package app.tek4tv.ipradio.core

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import app.tek4tv.ipradio.MyApp
import app.tek4tv.ipradio.core.mqtt.MQTTManager
import app.tek4tv.ipradio.di.MoshiInst
import app.tek4tv.ipradio.model.*
import app.tek4tv.ipradio.network.AuthService
import app.tek4tv.ipradio.ui.MainActivity.Companion.jsonMapAdapter
import app.tek4tv.ipradio.utils.*
import app.tek4tv.ipradio.utils.Utils.Companion.convertAuthor
import app.tek4tv.ipradio.utils.Utils.Companion.getDeviceInfo
import app.tek4tv.ipradio.utils.Utils.Companion.getTypeOfMediaItem
import app.tek4tv.ipradio.utils.network.NetworkUtils
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Types
import kotlinx.coroutines.*
import java.lang.reflect.ParameterizedType
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean


class HubManager(
    private val hubComm: HubComm,
    private val authService: AuthService,
    var onMessageListener: (command: String?, message: String?) -> Unit,
) : IConnectionServer {
    private val TAG = "HubManager"

    private val CONNECT_COUNTER_LIMIT = 5
    private val PING_HUB_INTERVAL = 60000L
    private val moshi = MoshiInst.moshi

    private var pingHubJob: Job? = null
    private var getTokenJob: Job? = null

    private var coroutineScope = CoroutineScope(Dispatchers.Main)

    val appContext: Context = MyApp.instance!!.applicationContext

    private val deviceImei = Utils.getDeviceId(appContext)

    val hubEventObserver = mutableListOf<HubEventObserver>()

    private var typeMediaItem: ParameterizedType = Types.newParameterizedType(
        MutableList::class.java,
        MediaItem::class.java
    )
    private var jsonMediaItemAdapter: JsonAdapter<List<MediaItem>> = moshi.adapter(typeMediaItem)


    var pingCounter = 0


    var responseHub = ResponseHub()

    //player info
    var playlistIndex = 0
    var currentMediaItem: MediaItem? = null
    var curMediaName = ""

    var versionName = ""

    private val mainHandler = Handler(Looper.getMainLooper())

    private val notifyDisconnected = AtomicBoolean(false)


    private var hubToken: String? = null

    private val wssStatusAdapter: JsonAdapter<WssStatusModel> =
        moshi.adapter(WssStatusModel::class.java)
    private val wssLiveAdapter: JsonAdapter<ReceivedMessageModel> = moshi.adapter(ReceivedMessageModel::class.java)
    private val mediaItemMoshiAdapter: JsonAdapter<MediaItem> =
        MoshiInst.moshi.adapter(MediaItem::class.java)

    //wss
    private var wssManager: WssManager? = null

    //mqtt
    val isMQTT = true
    var mqttClient: MQTTManager? = null

    init {
        versionName = try {
            appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName
        } catch (e: Exception) {
            ""
        }
    }

    private fun getHubToken() {
        try {
            val body = mapOf(
                "Username" to "tek4tv_bacgiang",
                "Password" to "33e0650c-7bc9-4c45-87b4-3b36ea15a6a5",
            )
            val url = ConfigUtil.getStringEncrypt(
                context = appContext,
                ConfigUtil.URL_TOKEN,
                "https://gateway-staging.thongtinnguon.vn/api/v2/oauth/token"
            )
            val call = authService.getToken(body, url!!)
            val response = call.execute()
            if (response.isSuccessful) {
                if ((response.body()?.trangThaiGUI ?: 1) != 1) {
                    hubToken = response.body()!!.noiDung.token
                }
            } else {
                Log.i(TAG, "getHubToken fail")
                hubToken = null
                pingCounter ++
            }
        } catch (e: Exception) {
            Log.i(TAG, "getHubToken error: ${e.message}")
            hubToken = null
            pingCounter ++
        }
        Log.i(TAG, "getHubToken ping: $pingCounter")
    }

    fun connectToServer() {
        //reset sau 5 phút khi không lấy được token
        if (pingCounter >= 60) {
            val countForHardRestart = ConfigUtil.getInt(appContext, ConfigUtil.CountForHardReset, 0)

            hubComm.forceRestartApp(countForHardRestart)
        }
        getTokenJob?.cancel()
        getTokenJob = CoroutineScope(Dispatchers.Default).launch {
            while (hubToken == null) {
                getHubToken()
                delay(5000)
                connectToServer()
            }
        }
        if (hubToken == null) return
        getTokenJob?.cancel()

        if (isMQTT) {
            mqttClient =
                MQTTManager(hubToken, this, GlobalProperties.getInst().destinationId)
            mqttClient?.connectToMQTT(appContext)
        } else {
            wssManager = WssManager(hubToken, this@HubManager)
            wssManager?.connectToWss()
        }


    }

    override fun onConnectSuccess() {
        startPingTimer()
    }

    override fun onConnectFailure(message: String?) {
        startPingTimer()


    }
    override fun onReceiveMessageSuccess(message: String) {
        pingCounter = 0
        convertToHubCommand(message)

    }

    override fun onSendMessageSuccess(responseMessage: String) {

    }

    override fun onSendMessageFailure(responseMessage: String) {

    }

    override fun onDisconnectFailure(message: String?) {
    }


    fun closeConnect()
    {
        if (isMQTT) {
            mqttClient?.disconnectMqtt()
        } else {
            wssManager?.closeWebsocket()
        }
    }


    private fun sendMessage(message: String) {
        if (isMQTT) {
            mqttClient?.publishToMQTT(message)
        } else {
            wssManager?.sendMessage(message)
        }
    }


    private fun sendPing() {
        pingCounter++
        var pathPlaying = ""
        if (currentMediaItem != null) {
            pathPlaying = currentMediaItem!!.pathBackup
        }
        val request = WssStatusModel(
            id = "", topic = HubCommand.DV016, payload = PayloadStatus(
                dichID = GlobalProperties.getInst().dichId,
                tenDich = GlobalProperties.getInst().tenDich,
                nguonID = GlobalProperties.getInst().destinationId,
                tenNguon = GlobalProperties.getInst().destinationName,
                bangDienTuID = deviceImei,
                tenThietBI = GlobalProperties.getInst().nameDevice,
                tenLoaiThietBI = "Bảng điện tử công cộng",
                maNhaCungCap = GlobalProperties.getInst().providerValue,
                tenNhaCungCap = GlobalProperties.getInst().providerValue,
                trangThaiHoatDong = "",
                trangThaiKetNoi = NetworkUtils.getNetworkInfo(appContext),
                trangThai = "",
                amLuong = getDeviceInfo(
                    StringValue.deviceInfoVolume,
                    SerialPortController.deviceInfo
                ),
                viDo = GlobalProperties.getInst().lat,
                kinhDo = GlobalProperties.getInst().lng,
                thongTinThietBI = ThongTinThietBI(
                    congSuat = getDeviceInfo(
                        StringValue.deviceInfoPA,
                        SerialPortController.deviceInfo
                    ),
                    nhietDo = getDeviceInfo(
                        StringValue.deviceInfoTemperature,
                        SerialPortController.deviceInfo
                    ),
                    dungLuongSuDung = "1024000",
                    duLieuKhac = ""
                ),
                thoiDiemBatDau = (Calendar.getInstance().time.time / 1000),
                noiDungPhat = pathPlaying,
                phienBanUngDung = versionName,
            )
        )
        Log.d(TAG, "sendPing: ${request.payload}")
        Log.d("livelive", "handleFromCommandServer: ${Thread.currentThread()}")
        sendMessage(wssStatusAdapter.toJson(request))
    }


    fun convertToHubCommand(jsonMessage: String) {
        val wssData = wssLiveAdapter.fromJson(jsonMessage)
        var hubCommand = ""
        when (wssData!!.topic) {
            HubCommand.DV010 -> {
                val body = wssData.payload
                if (body != null) {
                    val mapMonthSchedule = convertWssMessToPlaylist(body)
                    val jsonMess = jsonMapAdapter.toJson(mapMonthSchedule)
                    hubCommand = HubCommand.UPDATE_MONTHLY_WSS
                    val typeScreen = wssData.payload.phanVungManHinh
                    ConfigUtil.putString(
                        context = appContext,
                        GlobalProperties.TYPE_SCREEN_KEY,
                        typeScreen
                    )
                    onMessageListener.invoke(hubCommand, jsonMess)
                    sendLogSchedule(HubCommand.DV014, body)

                }
            }
            //Phát trực tiếp
            HubCommand.DV011 -> {
                val body = wssData.payload
                hubCommand = HubCommand.LIVE
                try {
                    responseHub = ResponseHub().apply {
                        type = HubCommand.LIVE
                        level = body?.mucDoUuTien!!.toInt()
                        duration = body.thoiLuong.toString()
                        message = body.noiDung
                        userNameControl = ""
                        name = mediaItemMoshiAdapter.toJson(
                            MediaItem(
                                projectID = body.banTinCongCongID!!,
                                mediaID = body.banTinCongCongID,
                                id = 0,
                                sourceID = body.nguonID,
                                sourceName = body.tenNguon,
                                destinationID = body.danhSachDiaBanNhan!!.firstOrNull()?.dichID,
                                destinationName = body.danhSachDiaBanNhan.firstOrNull()?.tenDich,
                                title = body.tieuDe,
                                description = body.noiDungTomTat,
                                duration = body.thoiLuong,
                                body = body.noiDung,
                                sourceReference = body.nguonID,
                                startTime = "00:00:00",
                                fieldType = Category(
                                    id =  body.loaiLinhVuc?.toLong(),
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
                                author = Author(
                                    userName = body.tacGia?.butDanh,
                                    fullName = body.tacGia?.tenDayDu,
                                    email = body.tacGia?.email
                                ),
                            )
                        )
                    }
                    Log.d("wss", "convertWssToHubCommad: ${hubCommand}-${jsonMessage}")

                        onMessageListener.invoke(hubCommand, jsonMessage)

                }catch (e : Exception)
                {
                    Log.d("wss", "convertWssToHubCommad: fail ${e.message}")
                }



            }
            //Hủy lịch
            HubCommand. DV015 ->{
                val body = wssData.payload
                if (body != null) {
                    hubCommand = HubCommand.DELETE_MONTHLY_LIST_WSS

                    onMessageListener.invoke(hubCommand, "null")

                    sendLogSchedule(HubCommand.DV012, body)
                }
            }
            //dieu khien volume, pa
            HubCommand.DV017 -> {
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

                        }
                        //Reset - Tham số (số giây khởi động lại)
                        "3" -> {
                            hubCommand =    HubCommand.RESTART
                        }
                        //update thiet bi
                        "4" -> {
                            hubCommand = HubCommand.UPDATE_VERSION
                            if(giaTri != "")
                            {
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
            id = "", topic = HubCommand.DV013, payload = WssMediaLogModel(
                nguonID = GlobalProperties.getInst().destinationId,
                tenNguon = GlobalProperties.getInst().destinationName,
                dichID = GlobalProperties.getInst().dichId,
                tenDich = GlobalProperties.getInst().tenDich,
                bangDienTuID = deviceImei,
                maNhaCungCap = GlobalProperties.getInst().providerValue,
                tenNhaCungCap = GlobalProperties.getInst().providerValue,
                amLuong = getDeviceInfo(
                    StringValue.deviceInfoVolume,
                    SerialPortController.deviceInfo
                ),
                thoiDiemBatDau = (Calendar.getInstance().time.time / 1000),
                congSuat = paState,
                banTinCongCongID = mediaItem.projectID,
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
                loaiLinhVuc =  mediaItem.fieldType?.value ,
                vungPhat = mediaItem.broadcastRegion?.value
            )
        )
        sendMessage(wssStatusAdapter.toJson(request))
    }

    private fun sendLogSchedule(commanDV: String, body: ReceivedPayload) {
        val danhSachDiaBanNhan = listOf(
            DanhSachDiaBanNhan(
                dichID = GlobalProperties.getInst().destinationId,
                tenDich = GlobalProperties.getInst().destinationName,
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
                nguonID = GlobalProperties.getInst().destinationId,
                tenNguon = GlobalProperties.getInst().destinationName,
                dichID = GlobalProperties.getInst().dichId,
                tenDich = GlobalProperties.getInst().tenDich,
                bangDienTuID = deviceImei,
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
                danhSachThietBi = danhSachThietBi
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

    private fun convertWssMessToPlaylist(payload: ReceivedPayload): Map<String, String> {
        listDate.clear()
        val danhSachBanTin = payload.danhSachBanTin
        val mapData: MutableMap<String, String> = mutableMapOf()
        if (danhSachBanTin?.isNotEmpty() == true) {
            for (item in danhSachBanTin) {
                findDate(item.danhSachNgayPhat)
            }
        }

        listDate.toSet().forEach {

            val jsonSchedule = findScheduleByDate(
                payload,
                it
            )
            mapData[it] =
                jsonSchedule
        }

        mapData.forEach{
            Log.d("wssplaylist", "Convert Map: ${it.key}")
            Log.d("wssplaylist", "Convert Map: ${it.value}")
        }

        return mapData
    }

    private fun findScheduleByDate(payload: ReceivedPayload, ngayPhat: String): String {
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
                    type = getTypeOfMediaItem(item.danhSachBanTin.loaiBanTin.toInt()),
                    createDate = item.danhSachBanTin.thoiGianSANXuat.toString(),
                    metadata = mutableListOf(),
                    tag = mutableListOf(),
                    fixTime = tObjEndTime,
                    startTime = tObjStartTime,
                    start = tObjStartTime,
                    end = tObjEndTime,
                    broadcastRegion = BroadcastRegion(id = item.danhSachBanTin.vungPhat, value = item.danhSachBanTin.vungPhat!!)
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
            Log.d("wssTImeFrame", "convertToTimeFrame: end ${listItem[i].end}")
            Log.d("wssTImeFrame", "convertToTimeFrame: start ${listItem[i + 1].start}")
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
            Log.d(
                "wssTImeFrame",
                "convertToTimeFrame: startTimes ${jsonMediaItemAdapter.toJson(newList)}"
            )
            return jsonMediaItemAdapter.toJson(newList)
        }
        //Có nhiều khung
        else {
            Log.d("wssTImeFrame", "convertToTimeFrame: Nhiều khung")
            listTimeFrame.forEach {
                Log.d(
                    "wssTImeFrame",
                    "convertToTimeFrame: Nhiều khung ${listItem[it.keys.first()].start} - ${listItem[it.values.first()].end}"
                )
                Log.d(
                    "wssTImeFrame",
                    "convertToTimeFrame: Nhiều khung ${it.keys.first()} - ${it.values.first()}"
                )
                val list : MutableList<MediaItem> =  listItem.subList(it.keys.first(),it.values.first()+1).toMutableList()
                Log.d(
                    "wssTImeFrame",
                    "convertToTimeFrame: Nhiều khung $list"
                )
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
            while (true) {
                sendPing()
                Log.d(TAG, "ping: $pingCounter")
                delay(PING_HUB_INTERVAL)
                checkConnection()
            }
        }
    }

    private fun checkConnection() {
        if (pingCounter >= CONNECT_COUNTER_LIMIT) {
            val countForHardRestart = ConfigUtil.getInt(appContext, ConfigUtil.CountForHardReset, 0)

            hubComm.forceRestartApp(countForHardRestart)
        }
        if (pingCounter >= 3) {
            Log.e(TAG, "Losed hub connection")
            if (notifyDisconnected.compareAndSet(false, true)) {
                hubToken = null
                notifyHubDisconnected()
                Log.e(TAG, "Hub Disconnected")
            }
            //Try to reconnect to hub
            Log.e(TAG, "Try to reconnect Websocket")
            connectToServer()
        }
    }


    @Synchronized
    private fun notifyHubDisconnected() {
        mainHandler.post {
            hubEventObserver.forEach { it.onLostHubConnection() }
        }
    }


}


interface HubComm {
    fun forceRestartApp(countHardRestart : Int)
    fun forceRestartDevice()
}

interface HubEventObserver {
    fun onLostHubConnection()
    fun onReconnectedToHub()
}