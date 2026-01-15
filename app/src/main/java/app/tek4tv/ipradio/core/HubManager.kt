package app.tek4tv.ipradio.core

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import app.tek4tv.ipradio.core.mqtt.MQTTManager
import app.tek4tv.ipradio.core.websocket.WssManager
import app.tek4tv.ipradio.di.JsonAdapterProvider.jsonMediaItemAdapter
import app.tek4tv.ipradio.di.JsonAdapterProvider.mediaItemMoshiAdapter
import app.tek4tv.ipradio.di.JsonAdapterProvider.wssLiveAdapter
import app.tek4tv.ipradio.di.JsonAdapterProvider.wssStatusAdapter
import app.tek4tv.ipradio.di.MoshiInst.jsonMapAdapter
import app.tek4tv.ipradio.model.Author
import app.tek4tv.ipradio.model.Category
import app.tek4tv.ipradio.model.ContentType
import app.tek4tv.ipradio.model.DanhSachDiaBanNhan
import app.tek4tv.ipradio.model.DanhSachNgayPhat
import app.tek4tv.ipradio.model.DanhSachThietBi
import app.tek4tv.ipradio.model.MQTTInfoModel
import app.tek4tv.ipradio.model.MediaItem
import app.tek4tv.ipradio.model.NoiDungMQTT
import app.tek4tv.ipradio.model.PayloadLive
import app.tek4tv.ipradio.model.PayloadStatus
import app.tek4tv.ipradio.model.Priority
import app.tek4tv.ipradio.model.ResponseHub
import app.tek4tv.ipradio.model.SourceMQTT
import app.tek4tv.ipradio.model.TacGia
import app.tek4tv.ipradio.model.ThongTinChiTietBanTin
import app.tek4tv.ipradio.model.ThongTinThietBI
import app.tek4tv.ipradio.model.WssMediaLogModel
import app.tek4tv.ipradio.model.WssStatusModel
import app.tek4tv.ipradio.model.itemMedia
import app.tek4tv.ipradio.network.AuthService
import app.tek4tv.ipradio.utils.ConfigUtil
import app.tek4tv.ipradio.utils.GlobalProperties
import app.tek4tv.ipradio.utils.HubCommand
import app.tek4tv.ipradio.utils.StringValue
import app.tek4tv.ipradio.utils.Utils
import app.tek4tv.ipradio.utils.Utils.Companion.convertAuthor
import app.tek4tv.ipradio.utils.Utils.Companion.getDeviceInfo
import app.tek4tv.ipradio.utils.Utils.Companion.getTypeOfMediaItem
import app.tek4tv.ipradio.utils.convertTimeStampToHHMMSS
import app.tek4tv.ipradio.utils.network.NetworkUtils
import app.tek4tv.ipradio.utils.network.NetworkUtils.Companion.getTotalDataNetworkUsed
import app.tek4tv.ipradio.utils.unixTimeStampToDateTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Calendar

@RequiresApi(Build.VERSION_CODES.N)
class HubManager(
    private val authService: AuthService,
    private val appContext: Context,
    private val coroutineScope: CoroutineScope,
    private var onMessageListener: (command: String?, message: String?) -> Unit,
) : IConnectionServer {
    private val TAG = "HubManager"

    private val CONNECT_COUNTER_LIMIT = 120
    private val PING_HUB_INTERVAL = 30000L
    private val API_CALL_INTERVAL = 30000L

    private var pingHubJob: Job? = null
    private var getTokenJob: Job? = null

    val deviceImei = Utils.getDeviceId(appContext)

    var pingCounter = 0

    var responseHub = ResponseHub()

    var currentMediaItem: MediaItem? = null

    var versionName = "M37.4.2c"

    private var hubToken: String? = null

    var isConnectSuccess = false

    private var wssManager: WssManager? = null

    var nguonId = "H41.126"

    var tenNguon = "Phường Hoàng Mai"
    var dichId = "H41"
    var tenDich = "Nghệ An"
    var listMQTTInfo: List<NoiDungMQTT> = listOf()
    val isMQTT = true
    private var getMQTTInfoJob: Job? = null

    var mqttClient: MQTTManager? = null


    private fun getHubToken() {
        try {
            val usernameKey = ConfigUtil.getStringEncrypt(appContext, ConfigUtil.USERNAME_KEY)
            val passwordKey = ConfigUtil.getStringEncrypt(appContext, ConfigUtil.PASSWORD_KEY)
            val username = ConfigUtil.getStringEncrypt(appContext, ConfigUtil.USERNAME)
            val password = ConfigUtil.getStringEncrypt(appContext, ConfigUtil.PASSWORD)
            val body = mapOf(
                usernameKey to username,
                passwordKey to password,
            )
            val url = ConfigUtil.getStringEncrypt(
                appContext,
                ConfigUtil.URL_TOKEN,
            )
            val call = authService.getToken(body, url)
            val response = call.execute()
            if (response.isSuccessful) {
                pingCounter = 0
                if ((response.body()?.trangThaiGUI ?: 1) != 1) {
                    hubToken = response.body()!!.noiDung!!.token
                    Log.i(TAG, "getHubToken $hubToken")
                }
            } else {
                Log.i(TAG, "getHubToken fail")
                hubToken = null
            }
        } catch (e: Exception) {
            Log.i(TAG, "getHubToken error: ${e.message}")
            hubToken = null
        }
    }

    private fun getMqttInfo() {
        try {
            val body = MQTTInfoModel(deviceImei)
            val url = ConfigUtil.getStringEncrypt(appContext, ConfigUtil.URL_MQTT_INFO)

            val call = authService.getMQTTInfo(body, "Bearer $hubToken", url)
            val response = call.execute()
            if (response.isSuccessful) {
                pingCounter = 0
                listMQTTInfo = response.body()!!.noiDung!!
                getDestinationInfo(response.body()!!.noiDung!!)
            }
        } catch (e: Exception) {
            listMQTTInfo = mutableListOf()
            Log.i(TAG, "MqttInfo fail: ${e.message}")
        }
    }

    private fun getDestinationInfo(listMQTTInfo: List<NoiDungMQTT>) {
        listMQTTInfo.forEach {
            if (it.topic?.contains("tinh") == true) {
                nguonId = it.topic.substringAfter("/").uppercase()
                dichId = it.topic.substringAfter("/").uppercase()
                getSourceMQTT(nguonId, false)
            }
            if (it.topic?.contains("huyen") == true) {
                nguonId = it.topic.substringAfter("/").uppercase()
            }
            if (it.topic?.contains("xa") == true) {
                nguonId = it.topic.substringAfter("/").uppercase()
            }
        }
        getSourceMQTT(nguonId, true)
    }

    private fun getSourceMQTT(id: String, timNguon: Boolean) {

        val url = ConfigUtil.getStringEncrypt(appContext, ConfigUtil.URL_MQTT_SOURCE)
        try {
            val call = authService.getSourceMQTT("${url}$id")
            val response = call.execute()
            if (response.isSuccessful) {
                val mqttSource: SourceMQTT = response.body()!!
                if (timNguon) {
                    tenNguon = mqttSource.name ?: ""
                    if (tenNguon == "") {
                        tenNguon = mqttSource.code?.uppercase() ?: ""
                    }
                } else {
                    tenDich = mqttSource.name ?: ""
                    if (tenDich == "") {
                        tenDich = mqttSource.code?.uppercase() ?: ""
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "getSourceMQTT: ${e.message}")
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    fun reconnectToServer() {
//        closeConnect()
//        connectToServer()
    }

    @RequiresApi(Build.VERSION_CODES.N)
    fun connectToServer() {
        getTokenJob?.cancel()
        getTokenJob = CoroutineScope(Dispatchers.Default).launch {
            while (hubToken == null) {
                getHubToken()
                if (hubToken == null) {
                    pingCounter++
                    delay(API_CALL_INTERVAL)
                    continue
                }
                // Cancel and start a new MQTT info retrieval job
                if (isMQTT) {
                    getMQTTInfoJob?.cancel()
                    getMQTTInfoJob = launch {
                        while (listMQTTInfo.isEmpty()) {
                            getMqttInfo()
                            if (listMQTTInfo.isEmpty()) {
                                pingCounter++
                                delay(API_CALL_INTERVAL)
                            }
                        }
                        mqttClient =
                            MQTTManager(
                                hubToken,
                                appContext,
                                this@HubManager,
                                listMQTTInfo,
                                destinationId = dichId
                            )
                        while (!isConnectSuccess) {
                            mqttClient?.connectToMQTT()
                            delay(5000)
                        }

                    }
                } else {
                    wssManager = WssManager(hubToken, appContext, this@HubManager)
                    while (!isConnectSuccess) {
                        wssManager!!.connectToWss()
                        delay(5000)
                    }

                }
            }
        }
    }


    override fun onConnectSuccess() {
        isConnectSuccess = true
        startPingTimer()
    }

    override fun onConnectFailure(message: String?) {
        isConnectSuccess = false
    }

    override fun onReceiveMessageSuccess(message: String) {
        pingCounter = 0
        convertToHubCommand(message)
    }

    private fun sendMessage(message: String) {
        if (isMQTT && mqttClient != null) {
            mqttClient?.publishToMQTT(message)
        } else {
            wssManager?.sendMessage(message)
        }
    }


    private fun sendPing() {
        pingCounter++
        var trangThaiHoatDong: String
        var noiDungPhat: String
        if (currentMediaItem != null) {
            noiDungPhat = currentMediaItem!!.pathBackup
            trangThaiHoatDong = "0"
        } else {
            noiDungPhat = ""
            trangThaiHoatDong = "2"
        }
        val request = WssStatusModel(
            id = "", topic = HubCommand.DV008, payload = PayloadStatus(
                nguonID = nguonId,
                tenNguon = tenNguon,
                dichID = dichId,
                tenDich = tenDich,
                cumLoaID = deviceImei,
                tenThietBI = GlobalProperties.getInst().nameDevice,
                tenLoaiThietBI = "Cụm loa truyền thanh",
                maNhaCungCap = GlobalProperties.getInst().providerValue,
                tenNhaCungCap = GlobalProperties.getInst().providerValue,
                amLuong = getDeviceInfo(
                    StringValue.deviceInfoVolume,
                    SerialPortController.deviceInfo
                ) ?: "0",
                trangThaiHoatDong = trangThaiHoatDong,
                trangThaiKetNoi = NetworkUtils.getNetworkInfo(appContext),
                viDo = GlobalProperties.getInst().lat,
                kinhDo = GlobalProperties.getInst().lng,
                thongTinThietBI = ThongTinThietBI(
                    congSuat = getDeviceInfo(
                        StringValue.deviceInfoPA,
                        SerialPortController.deviceInfo
                    ) ?: "0",
                    nhietDo = getDeviceInfo(
                        StringValue.deviceInfoTemperature,
                        SerialPortController.deviceInfo
                    ) ?: "0",
                    dungLuongSuDung = getTotalDataNetworkUsed(appContext),
                    duLieuKhac = SerialPortController.deviceInfo
                ),
                thoiDiemBatDau = (Calendar.getInstance().time.time / 1000),
                noiDungPhat = noiDungPhat,
                phienBanUngDung = versionName,
            )
        )
        Log.d(TAG, "sendPing: $request")
        sendMessage(wssStatusAdapter.toJson(request))
    }


    private fun convertToHubCommand(jsonMessage: String) {
        val wssData = wssLiveAdapter.fromJson(jsonMessage)
        var hubCommand = ""
        when (wssData!!.topic) {
            HubCommand.DV002 -> {
                val body = wssData.payload
                Log.d("TestLogrun", "run---")
                if (body != null) {
                    val mapMonthSchedule = convertWssMessToPlaylist(body)
                    Log.d("TestLog", "convertToHubCommand: body $mapMonthSchedule")
                    val jsonMess = jsonMapAdapter.toJson(mapMonthSchedule)
                    hubCommand = HubCommand.UPDATE_MONTHLY_WSS
                    coroutineScope.launch(Dispatchers.Main) {
                        onMessageListener.invoke(hubCommand, jsonMess)
                        sendLogSchedule(HubCommand.DV006, body)
                    }
                }
            }

            HubCommand.DV003 -> {
                val body = wssData.payload
                hubCommand = HubCommand.LIVE
                responseHub = ResponseHub().apply {
                    type = HubCommand.LIVE
                    level = body?.mucDoUuTien!!.toInt()
                    duration = body.thoiLuong.toString()
                    message = body.noiDung
                    userNameControl = ""
                    name = mediaItemMoshiAdapter.toJson(
                        MediaItem(
                            projectID = body.banTinID!!,
                            mediaID = body.banTinID,
                            id = 0,
                            sourceID = body.nguonID,
                            sourceName = body.tenNguon,
                            destinationID = body.danhSachDiaBanNhan!!.firstOrNull()?.dichID,
                            destinationName = body.danhSachDiaBanNhan.firstOrNull()?.tenDich,
                            title = body.tieuDe,
                            description = body.noiDungTomTat,
                            duration = body.thoiLuong,
                            body = body.noiDung,
                            sourceReference = body.nguonID!!.split(".").firstOrNull(),
                            startTime = "00:00:00",
                            fieldType = Category(
                                id = body.loaiLinhVuc?.toLong(),
                                value = body.loaiLinhVuc
                            ),
                            type = "LIVE",
                            fixTime = "00:00:00",
                            createDate = "",
                            contentType = ContentType(
                                id = body.loaiBanTin!!.toLong(),
                                value = body.loaiBanTin
                            ),
                            priority = Priority(
                                id = body.mucDoUuTien.toLong(),
                                value = body.mucDoUuTien
                            ),
                            author = Author(),
                        )
                    )
                }
                Log.d("wss", "convertWssToHubCommad: ${hubCommand}-${jsonMessage}")
                coroutineScope.launch(Dispatchers.Main) {
                    onMessageListener.invoke(hubCommand, jsonMessage)
                }
            }
            //Hủy lịch
            HubCommand.DV004 -> {
                val body = wssData.payload
                if (body != null) {
                    hubCommand = HubCommand.DELETE_MONTHLY_LIST_WSS
                    coroutineScope.launch(Dispatchers.Main) {
                        onMessageListener.invoke(hubCommand, "null")
                    }
                    sendLogSchedule(HubCommand.DV007, body)
                }
            }
            //dieu khien volume, pa
            HubCommand.DV009 -> {
                val body = wssData.payload
                if (body != null) {

                    val maLenh = body.dieuKhienThietBI?.maLenh
                    val giaTri = body.dieuKhienThietBI?.thamSo

                    when (maLenh) {
                        //0 - Điều khiển volume
                        "0" -> {
                            hubCommand = HubCommand.SET_VOLUME_DEVICE
                            responseHub = ResponseHub().apply {
                                message = giaTri
                            }
                        }
                        //1 - Điều khiển công suất
                        "1" -> {
                            hubCommand = HubCommand.TURN_ON_PA
                            //0 - bật
                            //1 - tắt
                            if (giaTri == "0") {
                                responseHub = ResponseHub().apply {
                                    message = "0"
                                }
                            } else {
                                responseHub = ResponseHub().apply {
                                    message = "1"
                                }
                            }
                        }
                        /*  Dừng phát nội dung
                        1 - Dừng phát bản tin, chuyển sang bản tin tiếp theo của lịch nếu có
                        2 - Dừng phát
                        3 - Phát tiếp nội dụng đã dừng*/
                        "2" -> {
                            hubCommand = HubCommand.CONTROL_MEDIA
                            if (giaTri == "0") {
                                responseHub = ResponseHub().apply {
                                    message = "0"
                                }
                            }
                            if (giaTri == "1") {
                                responseHub = ResponseHub().apply {
                                    message = "1"
                                }
                            }
                            if (giaTri == "2") {
                                responseHub = ResponseHub().apply {
                                    message = "2"
                                }
                            }
                            if (giaTri == "3") {
                                responseHub = ResponseHub().apply {
                                    message = "3"
                                }
                            }
                        }
                        //Reset - Tham số (số giây khởi động lại)
                        "3" -> {
                            hubCommand = HubCommand.RESTART
                        }
                        //update thiet bi
                        "4" -> {
                            hubCommand = HubCommand.UPDATE_VERSION
                            if (giaTri != "") {
                                responseHub = ResponseHub().apply {
                                    message = giaTri
                                }
                            }
                        }

                    }
                    onMessageListener.invoke(hubCommand, jsonMessage)

                }
            }

        }
    }


    fun sendLogMediaSuccess(mediaItem: MediaItem, paState: String) {

        val tacGia = TacGia(
            tenDayDu = mediaItem.author?.fullName ?: "",
            butDanh = mediaItem.author?.userName ?: "",
            email = mediaItem.author?.email ?: ""
        )

        val request = WssStatusModel(
            id = "", topic = HubCommand.DV005, payload = WssMediaLogModel(
                nguonID = nguonId,
                tenNguon = tenNguon,
                dichID = dichId,
                tenDich = tenDich,
                cumLoaID = deviceImei,
                maNhaCungCap = GlobalProperties.getInst().providerValue,
                tenNhaCungCap = GlobalProperties.getInst().providerValue,
                amLuong = getDeviceInfo(
                    StringValue.deviceInfoVolume,
                    SerialPortController.deviceInfo
                ),
                thoiDiemBatDau = (Calendar.getInstance().time.time / 1000),
                congSuat = paState,
                banTinID = mediaItem.projectID,
                loaiBanTin = mediaItem.contentType?.value,
                mucDoUuTien = mediaItem.priority?.id.toString(),
                noiDung = mediaItem.pathBackup,
                noiDungTomTat = mediaItem.description,
                tacGia = tacGia,
                tenThietBi = GlobalProperties.getInst().nameDevice,
                thoiLuong = mediaItem.duration,
                tieuDe = mediaItem.title,
                danhSachBanTin = listOf(),
                danhSachDiaBan = listOf(),
                danhSachThietBi = listOf(),
                loaiLinhVuc = mediaItem.fieldType?.value,
            )
        )
        Log.d(TAG, "sendLogMediaSuccess: dv005")
        sendMessage(wssStatusAdapter.toJson(request))
    }

    private fun sendLogSchedule(commanDV: String, body: PayloadLive) {
        val danhSachDiaBanNhan = listOf(
            DanhSachDiaBanNhan(
                dichID = dichId,
                tenDich = tenDich,
                cumLoaID = listOf(
                    deviceImei
                )
            )
        )
        val danhSachThietBi = listOf(
            DanhSachThietBi(
                cumLoaID = deviceImei,
                maNhaCungCap = GlobalProperties.getInst().providerValue,
                tenNhaCungCap = GlobalProperties.getInst().providerValue,
                tenThietBi = GlobalProperties.getInst().nameDevice,
            )
        )
        val request = WssStatusModel(
            id = "", topic = commanDV, payload = WssMediaLogModel(
                nguonID = nguonId,
                tenNguon = tenNguon,
                dichID = dichId,
                tenDich = tenDich,
                cumLoaID = deviceImei,
                maNhaCungCap = GlobalProperties.getInst().providerValue,
                tenNhaCungCap = GlobalProperties.getInst().providerValue,
                amLuong = getDeviceInfo(
                    StringValue.deviceInfoVolume,
                    SerialPortController.deviceInfo
                ),
                thoiDiemBatDau = (Calendar.getInstance().time.time / 1000),
                tenThietBi = GlobalProperties.getInst().nameDevice,
                lichPhatID = body.lichPhatID,
                tenLichPhat = body.tenLichPhat,
                tacGia = body.tacGia,
                danhSachBanTin = body.danhSachBanTin,
                danhSachDiaBan = danhSachDiaBanNhan,
                danhSachThietBi = danhSachThietBi,
            )
        )
        Log.d("WebsocketLog", "sendLogSchedule: $commanDV - $request")
        sendMessage(wssStatusAdapter.toJson(request))
    }

    private val listDate = mutableListOf<String>()
    private fun findDate(danhSachNgayPhat: List<DanhSachNgayPhat>) {
        danhSachNgayPhat.forEach {
            listDate.add(unixTimeStampToDateTime(it.ngayPhat))
        }
        listDate.sort()
    }

    private fun convertWssMessToPlaylist(payload: PayloadLive): Map<String, String> {
        listDate.clear()
        val danhSachBanTin = payload.danhSachBanTin
        val mapData: MutableMap<String, String> = mutableMapOf()
        if (danhSachBanTin?.isNotEmpty() == true) {
            for (item in danhSachBanTin) {
                findDate(item.danhSachNgayPhat)
            }
        }

        listDate.toSet().toMutableList().forEach {

            val jsonSchedule = findScheduleByDate(
                payload,
                it
            )
            mapData[it] =
                jsonSchedule
        }

        return mapData
    }


    private fun findScheduleByDate(payload: PayloadLive, ngayPhat: String): String {
        val danhSachBanTin = payload.danhSachBanTin
        val listBanTinPhatTheoNgay = mutableListOf<MediaItem>()
        val listItemMedia = mutableListOf<itemMedia>()
        //tim nhung ban tin co ngay la date
        for (item in danhSachBanTin!!) {
            item.danhSachNgayPhat.forEach {
                if (unixTimeStampToDateTime(it.ngayPhat) == ngayPhat) {
                    it.thoiDiemPhat.forEach { thoiDiemPhat ->
                        val time = ThongTinChiTietBanTin(
                            thoiDiemPhat.thoiGianBatDau,
                            thoiDiemPhat.thoiGianKetThuc
                        )
                        val itemMedia = itemMedia(time, item)
                        listItemMedia.add(itemMedia)
                    }
                }
            }
        }
        //chuyển list ra list media
        if (listItemMedia.isNotEmpty()) {
            listItemMedia.sortBy { itemMedia -> itemMedia.time.thoiGianBatDau }
            var count = 1
            var checkCountStart = 0
            for (item in listItemMedia) {
                val tObjStartTime: String = if (checkCountStart > 0) {
                    convertTimeStampToHHMMSS(item.time.thoiGianBatDau - 0)
                } else {
                    convertTimeStampToHHMMSS(item.time.thoiGianBatDau)
                }
                checkCountStart++
                val tObjEndTime: String = convertTimeStampToHHMMSS(
                    item.time.thoiGianKetThuc
                )
                val obj = MediaItem(
                    id = count,
                    mediaID = count.toString(),
                    projectID = item.danhSachBanTin.banTinID,
                    sourceID = payload.nguonID,
                    sourceName = payload.tenNguon,
                    destinationID = payload.dichID,
                    destinationName = payload.tenDich,
                    title = item.danhSachBanTin.tieuDe,
                    description = item.danhSachBanTin.noiDungTomTat,
                    duration = item.danhSachBanTin.thoiLuong,
                    contentType = Utils.convertContentType(item.danhSachBanTin.loaiBanTin),
                    fieldType = Utils.convertFiend(item.danhSachBanTin.loaiLinhVuc),
                    priority = Utils.convertPriority(item.danhSachBanTin.mucDoUuTien),
                    category = Utils.convertFiend(item.danhSachBanTin.loaiLinhVuc),
                    author = convertAuthor(item.danhSachBanTin.tacGia),
                    body = item.danhSachBanTin.noiDung,
                    sourceReference = item.danhSachBanTin.tenDiaBanTAO,
                    type = getTypeOfMediaItem(item.danhSachBanTin.loaiBanTin?.toInt()),
                    createDate = item.danhSachBanTin.thoiGianSANXuat.toString(),
                    metadata = mutableListOf(),
                    tag = mutableListOf(),
                    fixTime = tObjEndTime,
                    startTime = tObjStartTime,
                    start = tObjStartTime,
                    end = tObjEndTime,
                )
                count++
                listBanTinPhatTheoNgay.add(obj)

            }
        }
        return convertToTimeFrame(listBanTinPhatTheoNgay)
    }

    private fun convertToTimeFrame(listItem: List<MediaItem>): String {
        val newList = listItem.toMutableList()
        val res = mutableListOf<MediaItem>()
        val listTimeFrame: MutableList<Map<Int, Int>> = mutableListOf()
        val starts: MutableList<String> = mutableListOf()
        var startTime = 0
        var endTime: Int
        for (i in 0..listItem.size - 2) {
            if (i == 0) {
                startTime = 0
            }
            if (listItem[i].end != listItem[i + 1].start) {
                endTime = i
                starts.add(listItem[i].end!!)
                val map = mapOf(startTime to endTime)
                listTimeFrame.add(map)
                startTime = i + 1
            }
            if (i == listItem.size - 2) {
                endTime = i + 1
                val map = mapOf(startTime to endTime)
                listTimeFrame.add(map)

            }
        }
        //Chỉ có 1 khung
        if (starts.size == 0) {
            val firstId: Int = listItem.first().id?.minus(1) ?: 0
            Log.d("wssTImeFrame", "convertToTimeFrame: 1 khung")
            val tObjStart = MediaItem(
                id = firstId,
                mediaID = firstId.toString(),
                projectID = firstId.toString(),
                title = "Khung ${listItem.first().start}",
                body = "start",
                startTime = listItem.first().startTime,
                start = listItem.first().startTime,
                fixTime = listItem.first().startTime,
                end = listItem.first().end,
            )
            newList.add(0, tObjStart)
            val lastId: Int = listItem.last().id?.plus(1) ?: 0
            val tObEnd = MediaItem(
                id = lastId,
                mediaID = lastId.toString(),
                projectID = lastId.toString(),
                title = "End Khung ${listItem.first().start}",
                body = "end",
                startTime = listItem.last().startTime,
                fixTime = listItem.last().end,
                end = listItem.last().end,
                start = listItem.last().start
            )
            newList.add(tObEnd)
            return jsonMediaItemAdapter.toJson(newList)
        }
        //Có nhiều khung
        else {
            Log.d("wssTImeFrame", "convertToTimeFrame: Nhiều khung")
            listTimeFrame.forEach {
                val list: MutableList<MediaItem> =
                    listItem.subList(it.keys.first(), it.values.first() + 1).toMutableList()
                val firstId: Int = listItem[it.keys.first()].id?.minus(1) ?: 0
                val tObjStart = MediaItem(
                    id = firstId,
                    mediaID = firstId.toString(),
                    projectID = firstId.toString(),
                    title = "Khung ${listItem[it.keys.first()].start}",
                    body = "start",
                    startTime = listItem[it.keys.first()].startTime,
                    start = listItem[it.keys.first()].startTime,
                    fixTime = listItem[it.keys.first()].startTime,
                    end = listItem[it.keys.first()].end,
                )
                list.add(0, tObjStart)
                val lastId: Int = listItem[it.values.first()].id?.plus(1) ?: 0
                val tObEnd = MediaItem(
                    id = lastId,
                    mediaID = lastId.toString(),
                    projectID = lastId.toString(),
                    title = "End Khung ${listItem[it.values.first()].start}",
                    body = "end",
                    startTime = listItem[it.values.first()].startTime,
                    fixTime = listItem[it.values.first()].end,
                    end = listItem[it.values.first()].end,
                    start = listItem[it.values.first()].start
                )
                list.add(list.size, tObEnd)
                res.addAll(list)
            }
            return jsonMediaItemAdapter.toJson(res)

        }

    }


    private fun startPingTimer() {
        pingHubJob?.cancel()
        pingHubJob = coroutineScope.launch(Dispatchers.Default) {
            while (isActive) {
                try {
                    sendPing()
                    Log.d(TAG, "ping: $pingCounter")
                    delay(PING_HUB_INTERVAL)
                    checkConnection()
                } catch (e: CancellationException) {
                    break
                } catch (e: Exception) {
                    Log.d(TAG, "startPingTimer: ${e.message}")
                }
            }
        }
    }

    private fun checkConnection() {
        if (pingCounter >= 3) {
            //Try to reconnect to hub
            hubToken = null
            isConnectSuccess = false
            Log.e(TAG, "Losed hub connection. Try to reconnect Websocket")
            reconnectToServer()
        }
    }


}


interface HubComm {
    fun forceRestartApp(countHardRestart: Int)
    fun forceRestartDevice()
}

