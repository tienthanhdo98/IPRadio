package app.tek4tv.ipradio.core

import JsonAdapterProvider.mediaItemMoshiAdapter
import JsonAdapterProvider.wssLiveAdapter
import JsonAdapterProvider.wssPayloadControlDeviceAdapter
import JsonAdapterProvider.wssPayloadLiveAdapter
import JsonAdapterProvider.wssPayloadRemovePlaylistAdapter
import JsonAdapterProvider.wssPayloadScheduleAdapter
import JsonAdapterProvider.wssStatusAdapter
import android.content.Context
import android.os.Environment
import android.os.StatFs
import android.util.Log
import app.tek4tv.ipradio.di.MoshiInst
import app.tek4tv.ipradio.di.MoshiInst.adapterTacGia
import app.tek4tv.ipradio.model.*
import app.tek4tv.ipradio.model.local.MediaLogModel
import app.tek4tv.ipradio.network.AuthService
import app.tek4tv.ipradio.network.IHubObserve
import app.tek4tv.ipradio.ui.MainActivity.Companion.jsonMapAdapter
import app.tek4tv.ipradio.utils.*
import app.tek4tv.ipradio.utils.Utils.Companion.convertAuthor
import app.tek4tv.ipradio.utils.Utils.Companion.getDeviceInfo
import app.tek4tv.ipradio.utils.Utils.Companion.getTypeOfMediaItem
import app.tek4tv.ipradio.utils.network.NetworkUtils
import app.tek4tv.ipradio.utils.storage.AppStorageManager
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Types
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import java.lang.reflect.ParameterizedType
import java.util.*


class HubManager(
    private val appContext: Context,
    private val authService: AuthService
) : IConnectionServer {
    private val TAG = "HubManager"
    private val CONNECT_COUNTER_LIMIT = 60
    private val PING_HUB_INTERVAL = 30000L
    private val API_CALL_INTERVAL = 10000L
    private val moshi = MoshiInst.moshi
    lateinit var hubComm: HubComm
    lateinit var appStorageManager: AppStorageManager
    private var pingHubJob: Job? = null
    private var getTokenJob: Job? = null
    private var getMQTTInfoJob: Job? = null

    private var coroutineScope = CoroutineScope(Dispatchers.Main)

    private val deviceImei = Utils.getDeviceId(appContext)

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


    private var connectionListener: IHubObserve? = null

    private var hubToken: String? = null

    private val _messageStateFlow = MutableSharedFlow<Pair<String?, String?>?>(0)
    val messageStateFlow: SharedFlow<Pair<String?, String?>?> = _messageStateFlow

    private suspend fun receiveMessage(command: String?, message: String?) {
        _messageStateFlow.emit(Pair(command, message)) // Update StateFlow
    }

    var versionName = "D76W.4.2.1d"
    var nguonId = "H48.52.04"
    var tenNguon = "Xã Ba Cung"
    var dichId = "H48"
    var tenDich = "Quảng Ngãi"
    var isConnectSuccess = false

    //wss
    private var wssManager: WssManager? = null

    //mqtt
    private val isMQTT = true
    private var mqttClient: MQTTManager? = null
    private var listMQTTInfo: List<NoiDungMQTT> = listOf()


    fun setConnectionListener(listener: IHubObserve) {
        connectionListener = listener
    }

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
                    hubToken = response.body()!!.noiDung.token
                }
                Log.i(TAG, "getHubToken ok: $hubToken")
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
            Log.i(TAG, "MqttInfo fail: $pingCounter")
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
                        tenNguon = mqttSource.code ?: ""
                    }
                } else {
                    tenDich = mqttSource.name ?: ""
                    if (tenDich == "") {
                        tenDich = mqttSource.code ?: ""
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "getSourceMQTT: ${e.message}")
        }
    }

    private fun closeConnect() {
        isConnectSuccess = false
        hubToken = null
        listMQTTInfo = listOf()
        if (isMQTT) {
            mqttClient?.disconnectMqtt()
        } else {
            wssManager?.closeWebsocket()
        }
    }

    fun reconnectToServer() {
        closeConnect()
        connectToServer()
    }

    fun connectToServer() {
        //reset sau 5 phút khi không lấy được token
//        if (pingCounter >= 60) {
//            val countForHardRestart = ConfigUtil.getInt(appContext, ConfigUtil.CountForHardReset, 0)
//            hubComm.forceRestartApp(countForHardRestart)
//        }
        getTokenJob?.cancel()
        getTokenJob = CoroutineScope(Dispatchers.Default).launch {
            while (hubToken == null) {
                getHubToken()
                if (hubToken == null) {
                    pingCounter++
                    checkPingToAppReset()
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
                                checkPingToAppReset()
                                delay(API_CALL_INTERVAL)
                            }
                        }
                        mqttClient = MQTTManager(
                            hubToken,
                            this@HubManager,
                            listMQTTInfo
                        )
                        while (!isConnectSuccess) {
                            mqttClient?.connectToMQTT(appContext)
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
        connectionListener?.onHubConnected()
        startPingTimer()
    }

    override fun onConnectFailure(message: String?) {
        isConnectSuccess = false
        connectionListener?.onHubDisConnected()
        pingCounter++
        checkPingToAppReset()
    }

    override fun onReceiveMessageSuccess(message: String) {
        pingCounter = 0
        convertToHubCommand(message)
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
        val noiDungPhat: List<String> = if (currentMediaItem != null) {
            listOf(currentMediaItem!!.pathBackup)
        } else {
            emptyList()
        }

        val request = WssStatusModel(
            id = "", topic = HubCommand.DV016, payload = PayloadStatus(
                dichID = dichId,
                tenDich = tenDich,
                nguonID = nguonId,
                tenNguon = tenNguon,
                bangDienTuID = deviceImei,
                tenThietBI = GlobalProperties.getInst().nameDevice,
                tenLoaiThietBI = "Bảng điện tử công cộng",
                maNhaCungCap = GlobalProperties.getInst().providerValue,
                tenNhaCungCap = GlobalProperties.getInst().providerValue,
                trangThaiKetNoi = NetworkUtils.getNetworkInfo(appContext),
                trangThai = "1",
                amLuong = GlobalProperties.getInst().volume,
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
                    dungLuongSuDung = NetworkUtils.getTotalNetworkDataUsage(appContext),
                    duLieuKhac = SerialPortController.deviceInfo
                ),
                thoiDiemBatDau = (Calendar.getInstance().time.time / 1000),
                //conver sttring to list String
                noiDungPhat = noiDungPhat,
                phienBanUngDung = versionName,
            )
        )
        Log.d(TAG, "sendPing: ${request.payload}")
        Log.d(TAG, "getStorageInfo: ${getStorageInfo()}")
        Log.d(TAG, "getMemoryInfo: ${getMemoryInfo()}")
        sendMessage(wssStatusAdapter.toJson(request))
    }

    fun getTotalInternalStorageUsingStatFs(): Long {
        // Get the internal data directory.
        val path = Environment.getDataDirectory()
        val stat = StatFs(path.path)
        // Obtain block count and block size.
        val blockCount = stat.blockCountLong
        val blockSize = stat.blockSizeLong
        // Total storage (in bytes) is numberOfBlocks * sizeOfEachBlock.
        return blockCount * blockSize
    }

    fun getStorageInfo(): String {
        val total = Utils.bytesToBinaryGB(getTotalInternalStorageUsingStatFs())
        val used = Utils.bytesToBinaryGB(appStorageManager.getUsedRomStorage())
        return "Storage: ${used}/${total}"
    }

    fun getMemoryInfo(): String {
        val total = Utils.bytesToBinaryGB(appStorageManager.getTotalRam())
        val used = Utils.bytesToBinaryGB(appStorageManager.getUsedRam())
        return "Ram: ${used}/${total}"
    }


    private fun convertToHubCommand(jsonMessage: String) {
        val wssData = wssLiveAdapter.fromJson(jsonMessage)
        var hubCommand = ""

        when (wssData!!.topic) {
            HubCommand.DV010 -> {
                val body = wssPayloadScheduleAdapter.fromJson(jsonMessage)
                if (body != null) {
                    val mapMonthSchedule = convertWssMessToPlaylist(body.payload!!)
                    val jsonMess = jsonMapAdapter.toJson(mapMonthSchedule)
                    hubCommand = HubCommand.UPDATE_MONTHLY_WSS
                    ConfigUtil.putString(
                        context = appContext,
                        GlobalProperties.TYPE_SCREEN_KEY,
                        body.payload.phanVungManHinh
                    )
                    coroutineScope.launch {
                        receiveMessage(hubCommand, jsonMess)
                    }
                    sendLogUpdatePlaylist(body.payload)

                }
            }
            //Phát trực tiếp
            HubCommand.DV011 -> {
                val body = wssPayloadLiveAdapter.fromJson(jsonMessage)
                hubCommand = HubCommand.LIVE
                try {
                    val bodyLive = body!!.payload
                    val banTinPhat = bodyLive!!.danhSachBanTin!!.firstOrNull()
                    if (banTinPhat != null) {
                        responseHub = ResponseHub().apply {
                            type = HubCommand.LIVE
                            level = banTinPhat.mucDoUuTien!!.toInt()
                            duration = banTinPhat.thoiLuong!!
                            message = banTinPhat.noiDung
                            userNameControl = ""
                            name = mediaItemMoshiAdapter.toJson(
                                MediaItem(
                                    projectID = banTinPhat.banTinCongCongID,
                                    mediaID = banTinPhat.banTinCongCongID,
                                    id = 0,
                                    sourceID = bodyLive.nguonID,
                                    sourceName = bodyLive.tenNguon,
                                    destinationID = bodyLive.danhSachDiaBanNhan!!.firstOrNull()?.dichID,
                                    destinationName = bodyLive.danhSachDiaBanNhan.firstOrNull()?.tenDich,
                                    title = banTinPhat.tieuDe,
                                    description = banTinPhat.noiDungTomTat,
                                    duration = bodyLive.danhSachBanTin!!.firstOrNull()?.thoiLuong,
                                    body = bodyLive.danhSachBanTin.firstOrNull()?.noiDung,
                                    sourceReference = bodyLive.nguonID,
                                    startTime = "00:00:00",
                                    fieldType = Category(
                                        id = banTinPhat.loaiLinhVuc?.toLong(),
                                        value = banTinPhat.loaiLinhVuc
                                    ),
                                    type = "LIVE",
                                    fixTime = "00:00:00",
                                    createDate = banTinPhat.thoiGianSANXuat.toString(),
                                    contentType = ContentType(
                                        id = banTinPhat.loaiBanTin?.toLong(),
                                        value = banTinPhat.loaiBanTin
                                    ),
                                    priority = Priority(
                                        id = banTinPhat.mucDoUuTien.toLong(),
                                        value = banTinPhat.mucDoUuTien
                                    ),
                                    author = Author(
                                        userName = banTinPhat.tacGia?.butDanh,
                                        fullName = banTinPhat.tacGia?.tenDayDu,
                                        email = banTinPhat.tacGia?.email
                                    ),
                                    broadcastRegion = BroadcastRegion(
                                        banTinPhat.vungPhat,
                                        banTinPhat.vungPhat!!
                                    )
                                )
                            )
                        }
                        Log.d("wssLive", "convertWssToHubCommad: ${hubCommand}-${jsonMessage}")
                        Log.d("wssLive", "convertWssToHubCommad: $responseHub")

//                        onMessageListener.invoke(hubCommand, jsonMessage)
                        coroutineScope.launch {
                            receiveMessage(hubCommand, jsonMessage)
                        }
                    }

                } catch (e: Exception) {
                    Log.d("wss", "convertWssToHubCommad: fail ${e.message}")
                }


            }
//            //Hủy lịch
            HubCommand.DV012 -> {
                val body = wssPayloadRemovePlaylistAdapter.fromJson(jsonMessage)
                if (body != null) {
                    hubCommand = HubCommand.DELETE_MONTHLY_LIST_WSS

//                    onMessageListener.invoke(hubCommand, "null")
                    coroutineScope.launch {
                        receiveMessage(hubCommand, "null")
                    }

                    sendLogRemovePlaylist(body.payload!!)
                }
            }
            //dieu khien volume, pa
            HubCommand.DV017 -> {
                val body = wssPayloadControlDeviceAdapter.fromJson(jsonMessage)
                if (body != null) {

                    val maLenh = body.payload!!.dieuKhienThietBI?.maLenh
                    val giaTri = body.payload.dieuKhienThietBI?.thamSo

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
                        0 - Dung phat tat ca
                        1 - Dừng phát bản tin, chuyển sang bản tin tiếp theo của lịch nếu có
                        2 - Tam dung
                        3 - Phát tiếp nội dụng đã dừng*/
                        "2" -> {
//                            hubCommand =  HubCommand.DELETE_MONTHLY_LIST_WSS
                            hubCommand = HubCommand.CONTROL_MEDIA
                            if (giaTri == "0" || giaTri == "2") {
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

//                    onMessageListener.invoke(hubCommand, jsonMessage)
                    coroutineScope.launch {
                        receiveMessage(hubCommand, jsonMessage)
                    }
                    Log.d("wss", "Control ${hubCommand}- $responseHub")

                }
            }

        }
    }


    fun sendUnsentLogMediaSuccess(mediaItem: MediaLogModel) {
        val request = WssStatusModel(
            id = "", topic = HubCommand.DV013, payload = WssMediaLogModel(
                nguonID = nguonId,
                tenNguon = tenNguon,
                dichID = dichId,
                tenDich = tenDich,
                bangDienTuID = deviceImei,
                maNhaCungCap = GlobalProperties.getInst().providerValue,
                tenNhaCungCap = GlobalProperties.getInst().providerValue,
                thoiDiemBatDau = mediaItem.thoiDiemBatDau!!,
                loaiBanTin = mediaItem.loaiBanTin!!,
                mucDoUuTien = mediaItem.mucDoUuTien!!,
                noiDung = listOf(mediaItem.noiDung!!),
                noiDungTomTat = mediaItem.noiDungTomTat!!,
                tacGia = adapterTacGia.fromJson(mediaItem.tacGia!!)!!,
                tenThietBI = GlobalProperties.getInst().nameDevice,
                thoiLuong = mediaItem.thoiLuong!!,
                tieuDe = mediaItem.tieuDe!!,
                loaiLinhVuc = mediaItem.loaiLinhVuc!!,
                //
                banTinCongCongID = mediaItem.banTinID!!,
                vungPhat = mediaItem.vungPhat!!,
                thongTinChiTietBanTin = mutableListOf(
                    ThongTinChiTietBanTinTa("", "")
                ),
                nguonTin = mediaItem.nguonTin!!,
                thoiGianSANXuat = mediaItem.thoiGianSanXuat!!
            )
        )
        Log.d(TAG, "sendLogMediaSuccess: DV013")
        sendMessage(wssStatusAdapter.toJson(request))
    }

    fun sendLogMediaSuccess(mediaItem: MediaItem) {
        if (isConnectSuccess) {
            val tacGia = TacGia(
                tenDayDu = mediaItem.author?.fullName ?: "",
                butDanh = mediaItem.author?.userName ?: "",
                email = mediaItem.author?.email ?: "",
                tenDangNhap = ""
            )
            Log.d(TAG, "sendLogMediaSuccess: ${mediaItem.createDate}")

            val request = WssStatusModel(
                id = "", topic = HubCommand.DV013, payload = WssMediaLogModel(
                    nguonID = nguonId,
                    tenNguon = tenNguon,
                    dichID = dichId,
                    tenDich = tenDich,
                    bangDienTuID = deviceImei,
                    maNhaCungCap = GlobalProperties.getInst().providerValue,
                    tenNhaCungCap = GlobalProperties.getInst().providerValue,
                    thoiDiemBatDau = (Calendar.getInstance().time.time / 1000),
                    banTinCongCongID = mediaItem.projectID!!,
                    loaiBanTin = mediaItem.contentType?.value!!,
                    mucDoUuTien = mediaItem.priority?.id.toString(),
                    //conver string to list String(chi dung cho digital)
                    noiDung = listOf(mediaItem.pathBackup),
                    noiDungTomTat = mediaItem.description!!,
                    tacGia = tacGia,
                    tenThietBI = GlobalProperties.getInst().nameDevice,
                    thoiLuong = mediaItem.duration!!,
                    tieuDe = mediaItem.title!!,
                    loaiLinhVuc = mediaItem.fieldType!!.value!!,
                    vungPhat = mediaItem.broadcastRegion!!.value,
                    thongTinChiTietBanTin = mutableListOf(
                        ThongTinChiTietBanTinTa("", "")
                    ),
                    nguonTin = mediaItem.sourceName!!,
                    thoiGianSANXuat = mediaItem.createDate?.toLong() ?: 0
                )
            )
            sendMessage(wssStatusAdapter.toJson(request))
        }
    }

    private fun sendLogUpdatePlaylist(body: ReceivedPayloadSchedule) {
        val request = WssStatusModel(
            id = "", topic = HubCommand.DV014, payload = PayloadUpdatePlaylist(
                nguonID = nguonId,
                tenNguon = tenNguon,
                dichID = dichId,
                tenDich = tenDich,
                danhSachDiaBan = body.danhSachDiaBanNhan,
                danhSachThietBI = mutableListOf(
                    DanhSachThietBi(
                        bangDienTuID = deviceImei,
                        tenThietBI = GlobalProperties.getInst().nameDevice,
                        maNhaCungCap = GlobalProperties.getInst().providerValue,
                        tenNhaCungCap = GlobalProperties.getInst().providerValue,
                    )
                ),
                phanVungManHinh = body.phanVungManHinh,
                lichPhatID = body.lichPhatID,
                tenLichPhat = body.tenLichPhat,
                danhSachBanTin = body.danhSachBanTin,


                )
        )
        Log.d("WebsocketLog", "sendLogSchedule: $request")
        sendMessage(wssStatusAdapter.toJson(request))
    }


    private fun sendLogRemovePlaylist(body: PayloadRemovePlaylist) {
        val request = WssStatusModel(
            id = "", topic = HubCommand.DV015, payload = PayloadSendLogRemovePlaylist(
                nguonID = body.nguonID,
                tenNguon = body.tenNguon,
                dichID = body.danhSachDiaBanNhan?.firstOrNull()?.dichID,
                tenDich = body.danhSachDiaBanNhan?.firstOrNull()?.dichID,
                lichPhatID = body.lichPhatID,
                tenLichPhat = body.tenLichPhat,
                danhSachThietBI = mutableListOf(
                    DanhSachThietBi(
                        bangDienTuID = deviceImei,
                        tenThietBI = GlobalProperties.getInst().nameDevice,
                        maNhaCungCap = GlobalProperties.getInst().providerValue,
                        tenNhaCungCap = GlobalProperties.getInst().providerValue,
                    )
                ),


                )
        )
        Log.d("WebsocketLog", "sendLogRemovePlaylist: $request")
        sendMessage(wssStatusAdapter.toJson(request))
    }

    private val listDate = mutableListOf<String>()
    private fun findDate(danhSachNgayPhat: List<DanhSachNgayPhat>) {
        danhSachNgayPhat.forEach {
            listDate.add(unixTimeStampToDateTime(it.ngayPhat))
        }
        listDate.sort()
    }

    private fun convertWssMessToPlaylist(payload: ReceivedPayloadSchedule): Map<String, String> {
        listDate.clear()
        val danhSachBanTin = payload.danhSachBanTin
        val mapData: MutableMap<String, String> = mutableMapOf()
        if (danhSachBanTin.isNotEmpty()) {
            for (item in danhSachBanTin) {
                findDate(item.danhSachNgayPhat!!)
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

        return mapData
    }

    private fun findScheduleByDate(payload: ReceivedPayloadSchedule, ngayPhat: String): String {
        val danhSachBanTin = payload.danhSachBanTin
        val listBanTinPhatTheoNgay = mutableListOf<MediaItem>()
        val listItemMedia = mutableListOf<itemMedia>()
        //tim nhung ban tin co ngay la date
        for (item in danhSachBanTin) {
            item.danhSachNgayPhat!!.forEach {
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
            for ((checkCountStart, item) in listItemMedia.withIndex()) {
                val tObjStartTime: String = if (checkCountStart > 0) {
                    convertTimeStampToHHMMSS(item.time.thoiGianBatDau - 0)
                } else {
                    convertTimeStampToHHMMSS(item.time.thoiGianBatDau)
                }
                val tObjEndTime: String = convertTimeStampToHHMMSS(
                    item.time.thoiGianKetThuc
                )
                val obj = MediaItem(
                    id = count,
                    mediaID = count.toString(),
                    projectID = item.danhSachBanTin.banTinCongCongID,
                    sourceID = payload.nguonID,
                    sourceName = payload.tenNguon,
                    destinationID = payload.danhSachDiaBanNhan.firstOrNull()!!.dichID,
                    destinationName = payload.danhSachDiaBanNhan.firstOrNull()!!.tenDich,
                    title = item.danhSachBanTin.tieuDe,
                    description = item.danhSachBanTin.noiDungTomTat,
                    duration = item.danhSachBanTin.thoiLuong,
                    contentType = Utils.convertContentType(item.danhSachBanTin.loaiBanTin!!),
                    fieldType = Utils.convertFiend(item.danhSachBanTin.loaiLinhVuc!!),
                    priority = Utils.convertPriority(item.danhSachBanTin.mucDoUuTien!!),
                    category = Utils.convertFiend(item.danhSachBanTin.loaiLinhVuc!!),
                    author = convertAuthor(item.danhSachBanTin.tacGia!!),
                    body = item.danhSachBanTin.noiDung,
                    sourceReference = item.danhSachBanTin.tenDiaBanTAO,
                    type = getTypeOfMediaItem(item.danhSachBanTin.loaiBanTin!!.toInt()),
                    createDate = item.danhSachBanTin.thoiGianSANXuat.toString(),
                    metadata = mutableListOf(),
                    tag = mutableListOf(),
                    fixTime = tObjEndTime,
                    startTime = tObjStartTime,
                    start = tObjStartTime,
                    end = tObjEndTime,
                    broadcastRegion = BroadcastRegion(
                        id = item.danhSachBanTin.vungPhat,
                        value = item.danhSachBanTin.vungPhat!!
                    )
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
                val list: MutableList<MediaItem> =
                    listItem.subList(it.keys.first(), it.values.first() + 1).toMutableList()
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
//        if (pingCounter >= CONNECT_COUNTER_LIMIT) {
//            val countForHardRestart = ConfigUtil.getInt(appContext, ConfigUtil.CountForHardReset, 0)
//
//            hubComm.forceRestartApp(countForHardRestart)
//        }
        if (pingCounter >= 3) {
            //Try to reconnect to hub
            hubToken = null
            Log.e(TAG, "Try to reconnect Websocket")
            reconnectToServer()
        }
    }


    private fun checkPingToAppReset() {
        Log.d(TAG, "checkPingToReset: $pingCounter")
        //sau 5s se reset lai app neu api call that bai 60*5000
        if (pingCounter >= CONNECT_COUNTER_LIMIT) {
//            val countForHardRestart = ConfigUtil.getInt(appContext, ConfigUtil.CountForHardReset, 0)
//            hubComm.forceRestartApp(countForHardRestart)
            reconnectToServer()
        }
    }


}


interface HubComm {
    fun forceRestartDevice()
}



