package app.tek4tv.ipradio.core

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import app.tek4tv.ipradio.core.mqtt.MQTTManager
import app.tek4tv.ipradio.di.JsonAdapterProvider.baseMqttMessageAdapter
import app.tek4tv.ipradio.di.JsonAdapterProvider.cancelPayloadAdapter
import app.tek4tv.ipradio.di.JsonAdapterProvider.controllerPayloadAdapter
import app.tek4tv.ipradio.di.JsonAdapterProvider.jsonMediaItemAdapter
import app.tek4tv.ipradio.di.JsonAdapterProvider.livePayloadAdapter
import app.tek4tv.ipradio.di.JsonAdapterProvider.mediaItemMoshiAdapter
import app.tek4tv.ipradio.di.JsonAdapterProvider.schedulePayloadAdapter
import app.tek4tv.ipradio.di.MoshiInst.jsonMapAdapter
import app.tek4tv.ipradio.model.Author
import app.tek4tv.ipradio.model.BaseMqttMessage
import app.tek4tv.ipradio.model.Category
import app.tek4tv.ipradio.model.ContentType
import app.tek4tv.ipradio.model.LogAddSchedulePayload
import app.tek4tv.ipradio.model.LogCancelSchedulePayload
import app.tek4tv.ipradio.model.MQTTInfoModel
import app.tek4tv.ipradio.model.MediaItem
import app.tek4tv.ipradio.model.NoiDungMQTT
import app.tek4tv.ipradio.model.ResponseHub
import app.tek4tv.ipradio.model.TacGia
import app.tek4tv.ipradio.model.NgayPhat
import app.tek4tv.ipradio.model.Priority
import app.tek4tv.ipradio.model.SchedulePayload
import app.tek4tv.ipradio.model.LogMediaSuccessPayload
import app.tek4tv.ipradio.model.StatusPayload
import app.tek4tv.ipradio.model.ThietBi
import app.tek4tv.ipradio.model.ThoiDiemPhat
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
    private val PING_HUB_INTERVAL = 60000L
    private val API_CALL_INTERVAL = 60000L

    private var pingHubJob: Job? = null
    private var getTokenJob: Job? = null

    //    val deviceImei = Utils.getDeviceId(appContext)
    val deviceImei = "54:78:C9:B8:10:0E"

    var pingCounter = 0

    var responseHub = ResponseHub()

    var currentMediaItem: MediaItem? = null

    var versionName = "M62.4.2c"

    private var hubToken: String? = null


    var isConnectSuccess = false

    var nguonId = ""
    var tenNguon = ""
    var dichId = ""
    var tenDich = ""
    var listMQTTInfo: List<NoiDungMQTT> = listOf()
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
            }
            if (it.topic?.contains("huyen") == true) {
                nguonId = it.topic.substringAfter("/").uppercase()
            }
            if (it.topic?.contains("xa") == true) {
                nguonId = it.topic.substringAfter("/").uppercase()
            }
        }
        tenNguon = nguonId
        tenDich = dichId
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
        if (mqttClient != null) {
            mqttClient?.publishToMQTT(message)
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
        val payload = StatusPayload(
            amLuong = SerialPortController.volume,
            cumLoaId = deviceImei,
            dichId = dichId,
            viDo = GlobalProperties.getInst().lat.toDouble(),
            kinhDo = GlobalProperties.getInst().lng.toDouble(),
            maNhaCungCap = GlobalProperties.getInst().providerValue,
            nguonId = nguonId,
            noiDungPhat = noiDungPhat,
            phienBanUngDung = versionName,
            tenDich = tenDich,
            tenLoaiThietBi = "Cụm loa truyền thanh",
            tenNguon = tenNguon,
            tenNhaCungCap = GlobalProperties.getInst().providerValue,
            tenThietBi = GlobalProperties.getInst().nameDevice,
            thoiDiemBatDau = (Calendar.getInstance().time.time / 1000),
            trangThaiHoatDong = trangThaiHoatDong,
            trangThaiKetNoi = NetworkUtils.getNetworkInfo(appContext),
        )
        val message = BaseMqttMessage(
            id = "",
            topic = HubCommand.DV008,
            payload = payload
        )
        sendMessage(baseMqttMessageAdapter.toJson(message))
    }

    private fun convertToHubCommand(jsonMessage: String) {
        try {
            val mqttMessage = baseMqttMessageAdapter.fromJson(jsonMessage)
            var hubCommand = ""
            when (mqttMessage!!.topic) {
                HubCommand.DV002 -> {
                    val body = mqttMessage.payload
                    if (body != null) {
                        try {
                            val payload = schedulePayloadAdapter.fromJsonValue(body)
                            if (payload == null) return
                            val mapMonthSchedule = convertWssMessToPlaylist(payload)
                            val jsonMess = jsonMapAdapter.toJson(mapMonthSchedule)
                            hubCommand = HubCommand.UPDATE_MONTHLY_WSS
                            onMessageListener.invoke(hubCommand, jsonMess)
                            coroutineScope.launch(Dispatchers.Main) {
                                onMessageListener.invoke(hubCommand, jsonMess)
                                sendLogSchedule(body)
                            }
                        } catch (e: Exception) {
                            Log.d(TAG, "dv002 fail: ${e.message}")
                        }

                    }
                }

                HubCommand.DV003 -> {
                    val body = mqttMessage.payload
                    if (body != null) {
                        try {
                            val payload = livePayloadAdapter.fromJsonValue(body)
                            hubCommand = HubCommand.LIVE
                            responseHub = ResponseHub().apply {
                                type = HubCommand.LIVE
                                level = payload?.mucDoUuTien!!.toInt()
                                duration = payload.thoiLuong
                                message = payload.noiDung
                                userNameControl = ""
                                name = mediaItemMoshiAdapter.toJson(
                                    MediaItem(
                                        projectID = payload.banTinID,
                                        mediaID = payload.banTinID,
                                        id = 0,
                                        sourceID = payload.nguonID,
                                        sourceName = payload.tenNguon,
                                        destinationID = payload.danhSachDiaBanNhan.firstOrNull()?.dichID,
                                        destinationName = payload.danhSachDiaBanNhan.firstOrNull()?.tenDich,
                                        title = payload.tieuDe,
                                        description = payload.noiDungTomTat,
                                        duration = payload.thoiLuong,
                                        body = payload.noiDung,
                                        sourceReference = payload.nguonID.split(".")
                                            .firstOrNull(),
                                        startTime = "00:00:00",
                                        fieldType = Category(
                                            id = payload.loaiLinhVuc.toLong(),
                                            value = payload.loaiLinhVuc
                                        ),
                                        type = "LIVE",
                                        fixTime = "00:00:00",
                                        createDate = payload.thoiGianSANXuat,
                                        contentType = ContentType(
                                            id = payload.loaiBanTin.toLong(),
                                            value = payload.loaiBanTin
                                        ),
                                        priority = Priority(
                                            id = payload.mucDoUuTien.toLong(),
                                            value = payload.mucDoUuTien
                                        ),
                                        author = Author(
                                            userName = payload.tacGia.butDanh,
                                            email = payload.tacGia.email,
                                            fullName = payload.tacGia.tenDayDu
                                        ),
                                    )
                                )
                            }
                            Log.d("wss", "convertWssToHubCommad: ${hubCommand}-${jsonMessage}")
                            coroutineScope.launch(Dispatchers.Main) {
                                onMessageListener.invoke(hubCommand, jsonMessage)
                            }
                        } catch (e: Exception) {
                            Log.d(TAG, "dv003 fail: ${e.message}")
                        }

                    }

                }
//            //Hủy lịch
                HubCommand.DV004 -> {
                    val body = mqttMessage.payload
                    if (body != null) {
                        hubCommand = HubCommand.DELETE_MONTHLY_LIST_WSS
                        coroutineScope.launch(Dispatchers.Main) {
                            onMessageListener.invoke(hubCommand, "null")
                        }
                        sendCancelScheduleLogSchedule(body)
                    }
                }
                //dieu khien volume, pa
                HubCommand.DV009 -> {
                    val body = mqttMessage.payload
                    if (body != null) {
                        val payload = controllerPayloadAdapter.fromJsonValue(body)
                        val maLenh = payload?.dieuKhienThietBi?.maLenh
                        val giaTri = payload?.dieuKhienThietBi?.thamSo

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
                                //update theo 2455, ngược vs bản cũa
                                //0 - tắt
                                //1 - bật
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
                                responseHub = ResponseHub().apply {
                                    message = (giaTri?.toIntOrNull() ?: 0).toString()
                                }
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
        } catch (e: Exception) {
            Log.d(TAG, "convertToHubCommand: ${e.message}")
        }

    }

    private fun sendCancelScheduleLogSchedule(body: Any) {
        try {
            val receivedPayload = cancelPayloadAdapter.fromJsonValue(body)
            val payload = LogCancelSchedulePayload(
                dichID = receivedPayload?.nguonID,
                tenDich = receivedPayload?.tenNguon,
                lichPhatID = receivedPayload?.lichPhatID,
                nguonID = receivedPayload?.nguonID,
                tenNguon = receivedPayload?.tenNguon,
                tenLichPhat = receivedPayload?.tenLichPhat,
                danhSachThietBi = listOf(
                    ThietBi(
                        cumLoaId = deviceImei,
                        maNhaCungCap = GlobalProperties.getInst().providerValue,
                        tenNhaCungCap = GlobalProperties.getInst().providerValue,
                        tenThietBi = GlobalProperties.getInst().nameDevice,
                    )
                ),


                )
            val baseMqttMessage = BaseMqttMessage(
                id = "", topic = HubCommand.DV007, payload = payload
            )
            sendMessage(baseMqttMessageAdapter.toJson(baseMqttMessage))

        } catch (e: Exception) {
            Log.d(TAG, "sendLogSchedule: ${e.message}")
        }


    }

    private fun sendLogSchedule(body: Any) {
        try {
            val receivedPayload = schedulePayloadAdapter.fromJsonValue(body)
            val payload = LogAddSchedulePayload(
                danhSachBanTin = receivedPayload?.danhSachBanTin,
                danhSachDiaBan = receivedPayload?.danhSachDiaBanNhan,
                danhSachThietBi = listOf(
                    ThietBi(
                        cumLoaId = deviceImei,
                        maNhaCungCap = GlobalProperties.getInst().providerValue,
                        tenNhaCungCap = GlobalProperties.getInst().providerValue,
                        tenThietBi = GlobalProperties.getInst().nameDevice,
                    )
                ),
                dichId = receivedPayload?.dichID,
                lichPhatId = receivedPayload?.lichPhatID,
                nguonId = receivedPayload?.nguonID,
                tenDich = receivedPayload?.tenDich,
                tenLichPhat = receivedPayload?.tenLichPhat,
                tenNguon = receivedPayload?.tenNguon
            )
            val baseMqttMessage = BaseMqttMessage(
                id = "", topic = HubCommand.DV006, payload = payload
            )
            sendMessage(baseMqttMessageAdapter.toJson(baseMqttMessage))
        } catch (e: Exception) {
            Log.d(TAG, "sendLogSchedule: ${e.message}")
        }
    }

    fun sendLogMediaSuccess(mediaItem: MediaItem, paState: String) {
        try {
            val tacGia = TacGia(
                tenDayDu = mediaItem.author?.fullName ?: "",
                butDanh = mediaItem.author?.userName ?: "",
                email = mediaItem.author?.email ?: ""
            )

            val payload = LogMediaSuccessPayload(
                amLuong = SerialPortController.volume,
                banTinId = mediaItem.projectID,
                congSuat = paState,
                cumLoaId = deviceImei,
                dichId = dichId,
                loaiBanTin = mediaItem.contentType?.value,
                loaiLinhVuc = mediaItem.fieldType?.value,
                maNhaCungCap = GlobalProperties.getInst().providerValue,
                mucDoUuTien = mediaItem.priority?.id.toString(),
                nguonId = nguonId,
                nguonTin = mediaItem.sourceName,
                tacGia = tacGia,
                tenDich = tenDich,
                tenNhaCungCap = GlobalProperties.getInst().providerValue,
                tenThietBi = GlobalProperties.getInst().nameDevice,
                thoiDiemBatDau = (Calendar.getInstance().time.time / 1000),
                thoiGianSanXuat = mediaItem.createDate,
                thoiLuong = mediaItem.duration,
                tiepAm = mediaItem.isExternalSource,
                tieuDe = mediaItem.title,
                noiDung = mediaItem.pathBackup,
                noiDungTomTat = mediaItem.description,
            )

            val baseMqttMessage = BaseMqttMessage(
                id = "", topic = HubCommand.DV005, payload = payload
            )
            sendMessage(baseMqttMessageAdapter.toJson(baseMqttMessage))
        } catch (e: Exception) {

            Log.d(TAG, "sendLogMediaSuccess: ${mediaItem.createDate}")
            Log.d(TAG, "sendLogMediaSuccess: ${e.message}")
        }

    }


    private val listDate = mutableListOf<String>()
    private fun findDate(danhSachNgayPhat: List<NgayPhat>?) {
        if (danhSachNgayPhat == null) return
        listDate.clear()
        danhSachNgayPhat.forEach {
            listDate.add(unixTimeStampToDateTime(it.ngayPhat))
        }
        listDate.sort()
    }

    private fun convertWssMessToPlaylist(payload: SchedulePayload): Map<String, String> {
        listDate.clear()
        val danhSachBanTin = payload.danhSachBanTin
        val mapData: MutableMap<String, String> = mutableMapOf()
        if (danhSachBanTin.isNotEmpty()) {
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


    private fun findScheduleByDate(payload: SchedulePayload, ngayPhat: String): String {
        val danhSachBanTin = payload.danhSachBanTin
        val listBanTinPhatTheoNgay = mutableListOf<MediaItem>()
        val listItemMedia = mutableListOf<itemMedia>()
        //tim nhung ban tin co ngay la date
        for (item in danhSachBanTin) {
            item.danhSachNgayPhat.forEach {
                if (unixTimeStampToDateTime(it.ngayPhat) == ngayPhat) {
                    it.thoiDiemPhat.forEach { thoiDiemPhat ->
                        val time = ThoiDiemPhat(
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
                    convertTimeStampToHHMMSS(item.time.thoiGianBatDau?.minus(0))
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
                    sourceReference = item.danhSachBanTin.tenDiaBanTao,
                    type = getTypeOfMediaItem(item.danhSachBanTin.loaiBanTin?.toInt()),
                    createDate = item.danhSachBanTin.thoiGianSanXuat,
                    metadata = mutableListOf(),
                    fixTime = tObjEndTime,
                    startTime = tObjStartTime,
                    start = tObjStartTime,
                    end = tObjEndTime,
                    isExternalSource = item.danhSachBanTin.tiepAm
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
        if (starts.isEmpty()) {
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
                } catch (_: CancellationException) {
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

