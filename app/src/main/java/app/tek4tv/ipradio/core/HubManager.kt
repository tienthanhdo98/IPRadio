package app.tek4tv.ipradio.core

import android.content.Context
import android.util.Log
import app.tek4tv.ipradio.MyApp
import app.tek4tv.ipradio.di.RetrofitInstance
import app.tek4tv.ipradio.model.*
import app.tek4tv.ipradio.ui.MainActivity.Companion.jsonListMediaItemAdapter
import app.tek4tv.ipradio.ui.MainActivity.Companion.jsonMapAdapter
import app.tek4tv.ipradio.ui.MainActivity.Companion.retrofit
import app.tek4tv.ipradio.utils.*
import app.tek4tv.ipradio.utils.Utils.Companion.convertAuthor
import app.tek4tv.ipradio.utils.Utils.Companion.getDeviceInfo
import app.tek4tv.ipradio.utils.Utils.Companion.getTypeOfMediaItem
import app.tek4tv.ipradio.utils.network.NetworkUtils
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonEncodingException
import com.squareup.moshi.Moshi
import kotlinx.coroutines.*
import java.util.*


class HubManager(
    private val hubComm: HubComm,
    private val onMessageListener: (command: String?, message: String?) -> Unit,
) : IConnectionServer {
    private val TAG = "HubManager"

    private val CONNECT_COUNTER_LIMIT = 5
    private val PING_HUB_INTERVAL = 60000L
    private val moshi = RetrofitInstance.moshi

    private var pingHubJob: Job? = null
    private var getTokenJob: Job? = null
    private var getMQTTInfoJob: Job? = null

    private var coroutineScope = CoroutineScope(Dispatchers.Main)

    val appContext: Context = MyApp.instance!!.applicationContext

    val deviceImei = Utils.getDeviceId(appContext)

    var pingCounter = -1

    var responseHub = ResponseHub()

    //player info
    var currentMediaItem: MediaItemModel? = null

    var versionName = ""

    private var hubToken: String? = null

    private val wssStatusAdapter: JsonAdapter<WssStatusModel> =
        moshi.adapter(WssStatusModel::class.java)
    private val wssLiveAdapter: JsonAdapter<ReceivedMessageModel> =
        moshi.adapter(ReceivedMessageModel::class.java)
    private val wssPayloadLiveAdapter: JsonAdapter<ReceivedMessageLiveModel> =
        moshi.adapter(ReceivedMessageLiveModel::class.java)
    private val wssPayloadRemovePlaylistAdapter: JsonAdapter<ReceivedMessageRemovePlaylistModel> =
        moshi.adapter(ReceivedMessageRemovePlaylistModel::class.java)
    private val wssPayloadControlDeviceAdapter: JsonAdapter<ReceivedMessageControlDeviceModel> =
        moshi.adapter(ReceivedMessageControlDeviceModel::class.java)
    private val mediaItemMoshiAdapter: JsonAdapter<MediaItemModel> =
        moshi.adapter(MediaItemModel::class.java)

    //mqtt
    private var listMQTTInfo: List<NoiDungMQTT> = listOf()
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
                "Username" to "tek4tv_quangngai",
                "Password" to "4bfb2c4a-e049-4d21-86e0-4ffdf854b8a1",
            )
            val url = ConfigUtil.getString(
                context = appContext,
                ConfigUtil.URL_TOKEN,
                "https://gateway-thongtinnguon.tek4tv.vn/api/v2/oauth/token"
            )
            val call = retrofit.getToken(body, url!!)
            val response = call.execute()
            if (response.isSuccessful) {
                if ((response.body()?.trangThaiGUI ?: 1) != 1) {
                    hubToken = response.body()!!.noiDung.token
                    Log.i(TAG, "getHubToken $hubToken")
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
    private fun getMqttInfo()
    {
        Log.i(TAG, "get MqttInfo ")
        try {
            val body = MQTTInfoModel(deviceImei)
            val url = "https://gateway-thongtinnguon.tek4tv.vn/v2/mqtt-digital/digital/mqtt/dangkythietbi"
            val call = retrofit.getMQTTInfo(body,"Bearer $hubToken", url)
            val response = call.execute()
            Log.i(TAG, "get MqttInfo ${response.code()}")
            if (response.isSuccessful) {
                listMQTTInfo = response.body()!!.noiDung
                Log.i(TAG, "MqttInfo: ${listMQTTInfo.size}")
            } else {
                listMQTTInfo = listOf()
                Log.i(TAG, "MqttInfo null")
            }
        } catch (e: Exception) {
            Log.i(TAG, "MqttInfo fail: ${e.message}")
        }
    }

    fun connectToServer() {
        //reset sau 5 phút khi không lấy được token
//        if (pingCounter >= 60) {
//            val countForHardRestart = ConfigUtil.getInt(appContext, ConfigUtil.CountForHardReset, 0)
//            hubComm.forceRestartApp(countForHardRestart)
//        }
        getTokenJob?.cancel()
        getTokenJob = CoroutineScope(Dispatchers.Default).launch {
            while (isActive && hubToken == null) {
                getHubToken()
                if (hubToken == null) {
                    delay(5000)

                }
                if (hubToken == null) return@launch

                // Cancel and start a new MQTT info retrieval job
                getMQTTInfoJob?.cancel()
                getMQTTInfoJob = launch {
                    while (isActive && listMQTTInfo.isEmpty()) {
                        getMqttInfo()
                        if (listMQTTInfo.isEmpty()) {
                            delay(5000)
                        }
                    }
                    if (listMQTTInfo.isEmpty()) return@launch

                    mqttClient = MQTTManager(
                        hubToken,
                        this@HubManager,
                        GlobalProperties.getInst().destinationId,
                        listMQTTInfo
                    )
                    mqttClient?.connectToMQTT(appContext)

                }
                getMQTTInfoJob?.join()
            }

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
        Log.d(TAG, "onReceiveMessageSuccess: ${pingCounter}")
        convertToHubCommand(message)
    }

    override fun onSendMessageSuccess(responseMessage: String) {

    }

    override fun onSendMessageFailure(responseMessage: String) {

    }

    override fun onDisconnectFailure(message: String?) {
    }


    fun closeConnectMQTT() {
        mqttClient?.disconnectMqtt()
    }

    private fun sendMessage(message: String) {
        mqttClient?.publishToMQTT(message)
    }

    private fun sendPing() {
        pingCounter++
        val noiDungPhat: List<String> = if (currentMediaItem != null) {
            listOf(currentMediaItem!!.pathBackup)
         } else {
             listOf()
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
                trangThaiKetNoi = NetworkUtils.getNetworkInfo(appContext),
                trangThai = "1",
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
                 //conver sttring to list String
                 noiDungPhat = noiDungPhat,
                 phienBanUngDung = versionName,
             )
         )
        Log.d(TAG, "sendPing: ${request.payload}")
        sendMessage(wssStatusAdapter.toJson(request))
    }

    fun parseJson(jsonMessage: String, moshi: Moshi): MQTTReceivedScheduleModel? {
        val mqttReceivedScheduleModelAdapter: JsonAdapter<MQTTReceivedScheduleModel> =
            moshi.adapter(MQTTReceivedScheduleModel::class.java)
        return try {
            // Attempt to parse the JSON message
            mqttReceivedScheduleModelAdapter.fromJson(jsonMessage)
        } catch (e: JsonDataException) {
            // Handle errors related to data mismatch
            println("Error: Malformed JSON structure or unexpected data type - ${e.message}")
            null
        } catch (e: JsonEncodingException) {
            // Handle errors related to encoding issues (e.g., missing brackets, etc.)
            println("Error: Malformed JSON encoding - ${e.message}")
            null
        } catch (e: Exception) {
            // Handle any other unexpected exceptions
            println("Error: Unexpected error during JSON parsing - ${e.message}")
            null
        }
    }

    private fun convertToHubCommand(jsonMessage: String) {
        val wssData = wssLiveAdapter.fromJson(jsonMessage)
        var hubCommand = ""

        when (wssData!!.topic) {
            HubCommand.DV010 -> {
                Log.d(TAG, "Command: dv010")
                val body = parseJson(jsonMessage, moshi)
                if (body != null) {
                    val mapMonthSchedule = convertWssMessToPlaylist(body.payload!!)
                    val jsonMess = jsonMapAdapter.toJson(mapMonthSchedule)
                    hubCommand = HubCommand.UPDATE_MONTHLY_WSS
                    val typeScreen = body.payload?.phanVungManHinh
                    ConfigUtil.putString(
                        context = appContext,
                        GlobalProperties.TYPE_SCREEN_KEY,
                        typeScreen
                    )
                    onMessageListener.invoke(hubCommand, jsonMess)
                    sendLogUpdatePlaylist(body.payload!!)
                } else {
                    Log.d(TAG, "Command received error: dv010")
                }


            }
            //Phát trực tiếp
            HubCommand.DV011 -> {
                Log.d(TAG, "Command: dv011")
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
                                MediaItemModel(
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
                                        id = banTinPhat.mucDoUuTien?.toLong(),
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
                        onMessageListener.invoke(hubCommand, jsonMessage)
                    }

                }catch (e : Exception)
                {
                    Log.d(TAG, "Command received error: dv011 - ${e.message}")
                }


            }
//            //Hủy lịch
            HubCommand.DV012 -> {
                Log.d(TAG, "Command: dv012")
                val body = wssPayloadRemovePlaylistAdapter.fromJson(jsonMessage)
                if (body != null) {
                    hubCommand = HubCommand.DELETE_MONTHLY_LIST_WSS
                    onMessageListener.invoke(hubCommand, "null")
                    sendLogRemovePlaylist(body.payload!!)
                }
            }
            //dieu khien volume, pa
            HubCommand.DV017 -> {
                val body = wssPayloadControlDeviceAdapter.fromJson(jsonMessage)
                if (body != null) {
                    val maLenh = body.payload!!.dieuKhienThietBI?.maLenh
                    val giaTri = body.payload.dieuKhienThietBI?.thamSo
                    Log.d(TAG, "Command: dv017 - Tham So : $maLenh - Gia tri: $giaTri")
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
                    Log.d(TAG, "Command: dv017 - ${hubCommand}- ${responseHub}")

                }
            }

        }
    }

    fun sendLogMediaSuccess(mediaItem: MediaItemModel) {

        val tacGia = TacGia(
            tenDayDu = mediaItem.author?.fullName ?: "",
            butDanh = mediaItem.author?.userName ?: "",
            email = mediaItem.author?.email ?: "",
            tenDangNhap =   ""
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
                thoiDiemBatDau = (Calendar.getInstance().time.time / 1000),
                banTinCongCongID = mediaItem.projectID!!,
                loaiBanTin = mediaItem.contentType?.value!!,
                mucDoUuTien = mediaItem.priority?.id.toString(),
                noiDung = listOf(mediaItem.pathBackup),
                noiDungTomTat = mediaItem.description ?: "",
                tacGia = tacGia,
                tenThietBI = GlobalProperties.getInst().nameDevice,
                thoiLuong = mediaItem.duration!!,
                tieuDe = mediaItem.title ?: "",
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


    private fun sendLogUpdatePlaylist( body: ReceivedPayloadSchedule) {
        val request = WssStatusModel(
            id = "", topic = HubCommand.DV014A, payload = MQTTLogScheduleModel(
                NguonID = GlobalProperties.getInst().destinationId,
                TenNguon = GlobalProperties.getInst().destinationName,
                DichID = GlobalProperties.getInst().dichId,
                TenDich = GlobalProperties.getInst().tenDich,
                DanhSachDiaBanNhan = body.danhSachDiaBanNhan,
                PhanVungManHinh = body.phanVungManHinh,
                LichPhatID = body.lichPhatID,
                TenLichPhat = body.tenLichPhat,
                DanhSachNgayPhat = body.danhSachNgayPhat
            )
        )
        Log.d(TAG, "LogScheduleSucces: $request")
        sendMessage(wssStatusAdapter.toJson(request))
    }


    private fun sendLogRemovePlaylist(body: PayloadRemovePlaylist) {
        val request = WssStatusModel(
            id = "", topic = HubCommand.DV015, payload = PayloadSendLogRemovePlaylist(
                nguonID = body.nguonID,
                tenNguon = body.tenNguon,
                dichID = body.danhSachDiaBanNhan?.firstOrNull()?.dichID,
                tenDich =  body.danhSachDiaBanNhan?.firstOrNull()?.dichID,
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

    private fun convertWssMessToPlaylist(payload: ReceivedPayloadSchedule): Map<String, String> {

        val mapNgayPhat: MutableMap<String, String> = mutableMapOf()
        val danhSachNgayPhat = payload.danhSachNgayPhat

        //Lay lich tong cac ngay phat
        danhSachNgayPhat?.forEach {
            val jsonSchedule = findScheduleByDate(
                it,
                payload,

            )
            mapNgayPhat[unixTimeStampToDateTime(it.ngayPhat!!)] =
                jsonSchedule
        }

        mapNgayPhat.forEach {
            Log.d("wssplaylist", "Convert Map: ${it.key}")
            Log.d("wssplaylist", "Convert Map: ${it.value}")
        }
        return mapNgayPhat
    }
    private var countId = 0
    private fun findScheduleByDate(ngayPhat : app.tek4tv.ipradio.model.DanhSachNgayPhat,playload : ReceivedPayloadSchedule): String {
        val danhSachBanTinTrongNgay : MutableList<MediaItemModel> = mutableListOf()
        //tim cac khung phat trong ngay
        countId = 0
        ngayPhat.thoiDiemPhat.forEach {
            danhSachBanTinTrongNgay.addAll(convertToTimeFrame(it,playload))
        }
        return  jsonListMediaItemAdapter.toJson(danhSachBanTinTrongNgay)
    }

    private fun convertToTimeFrame(thoiDiemPhat: ThoiDiemPhat,playload : ReceivedPayloadSchedule): MutableList<MediaItemModel> {
        val startTimeFrame = convertTimeStampToHHMMSS(thoiDiemPhat.thoiGianBatDau!!)
        val endTimeFrame = convertTimeStampToHHMMSS(thoiDiemPhat.thoiGianKetThuc!!)
        val newList : MutableList<MediaItemModel> = mutableListOf()
        val tObjStart = MediaItemModel(
            id = countId,
            mediaID = "0",
            projectID = "0",
            title = "Khung $startTimeFrame - $endTimeFrame",
            body = "start",
            startTime =startTimeFrame,
            start = startTimeFrame,
            fixTime = startTimeFrame,
            end = endTimeFrame,
        )
        newList.add(tObjStart)
        countId ++
        thoiDiemPhat.danhSachBanTin?.forEach {
            val obj = MediaItemModel(
                id = countId,
                mediaID = countId.toString(),
                projectID = it.banTinCongCongID,
                sourceID = playload.nguonID,
                sourceName = playload.tenNguon,
                destinationID = playload.dichID,
                destinationName = playload.tenDich,
                title = it.tieuDe,
                description = it.noiDungTomTat,
                duration = it.thoiLuong,
                contentType = Utils.convertContentType(it.loaiBanTin!!),
                fieldType = Utils.convertFiend(it.loaiLinhVuc!!),
                priority = Utils.convertPriority(it.mucDoUuTien!!),
                category = Utils.convertFiend(it.loaiLinhVuc!!),
                author = convertAuthor(it.tacGia!!),
                body = it.noiDung,
                sourceReference = it.tenDiaBanTAO,
                type = getTypeOfMediaItem(it.loaiBanTin!!.toInt()),
                createDate = it.thoiGianSANXuat.toString(),
                metadata = mutableListOf(),
                tag = mutableListOf(),
                fixTime = "",
                startTime = "",
                start = "",
                end = "",
                broadcastRegion = BroadcastRegion(
                    id = it.vungPhat,
                    value = it.vungPhat!!
                ),

            )
            countId ++
            newList.add(obj)
        }
        val tObEnd = MediaItemModel(
            id = countId,
            mediaID = "0",
            projectID = "0",
            title = "Khung $startTimeFrame - $endTimeFrame",
            body = "end",
            startTime = startTimeFrame,
            fixTime = endTimeFrame,
            end = endTimeFrame,
            start = startTimeFrame
        )
        newList.add(tObEnd)
        countId ++
        return newList
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
//        if (pingCounter >= CONNECT_COUNTER_LIMIT) {
//            val countForHardRestart = ConfigUtil.getInt(appContext, ConfigUtil.CountForHardReset, 0)
//
//            hubComm.forceRestartApp(countForHardRestart)
//        }
        if (pingCounter >= 3) {
            Log.e(TAG, "Losed hub connection")
//            if (notifyDisconnected.compareAndSet(false, true)) {
//                hubToken = null
//                Log.e(TAG, "Hub Disconnected")
//            }
            hubToken = null
            //Try to reconnect to hub
            Log.e(TAG, "Try to reconnect Websocket")
            connectToServer()
        }
    }



}


interface HubComm {
    fun forceRestartApp(countHardRestart : Int)
    fun forceRestartDevice()
}


