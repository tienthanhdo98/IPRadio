package app.tek4tv.ipradio.core

import android.content.Context
import android.util.Log
import app.tek4tv.ipradio.MyApp
import app.tek4tv.ipradio.di.RetrofitInstance
import app.tek4tv.ipradio.di.RetrofitInstance.authService
import app.tek4tv.ipradio.model.*
import app.tek4tv.ipradio.ui.MainActivity.Companion.appStorageManager
import app.tek4tv.ipradio.ui.MainActivity.Companion.retrofit
import app.tek4tv.ipradio.utils.ConfigUtil
import app.tek4tv.ipradio.utils.GlobalProperties
import app.tek4tv.ipradio.utils.HubCommand
import app.tek4tv.ipradio.utils.Utils
import app.tek4tv.ipradio.utils.Utils.Companion.getDeviceInfo
import app.tek4tv.ipradio.utils.Utils.Companion.parseJson
import app.tek4tv.ipradio.utils.network.NetworkUtils
import com.squareup.moshi.JsonAdapter
import kotlinx.coroutines.*
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.*


class HubManager(
    private val onMessageListener: (command: String?, message: String?) -> Unit,
) : IConnectionServer {
    private val TAG = "HubManager"
    private val PING_HUB_INTERVAL = 60000L
    private val API_CALL_INTERVAL = 30000L
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
    private var isConnectSuccess = false
    var nguonId = ""
    var tenNguon = ""
    var dichId = ""
    var tenDich = ""

    init {
        versionName = try {
            appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName
        } catch (e: Exception) {
            ""
        }
    }

    private fun getHubToken() {
        CoroutineScope(Dispatchers.IO).launch {
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

            call.enqueue(object : Callback<TokenDto> {
                override fun onResponse(
                    call: Call<TokenDto>,
                    response: Response<TokenDto>
                ) {
                    if (response.isSuccessful && response.body() != null) {
                        pingCounter = 0
                        if ((response.body()?.trangThaiGUI ?: 1) != 1) {
                            hubToken = response.body()!!.noiDung.token
                        }
                        Log.i(TAG, "getHubToken ok: $hubToken")
                    } else {
                        Log.i(TAG, "getHubToken fail")
                        hubToken = null
                    }
                }

                override fun onFailure(call: Call<TokenDto>, t: Throwable) {
                    Log.i(TAG, "getHubToken fail")
                    hubToken = null
                }
            })


        }
    }

    private fun getMqttInfo() {
        //Never use call.execute() in main thread-> make app crash when api timeout
        val body = MQTTInfoModel(deviceImei)
        val url =
            "https://gateway-thongtinnguon.tek4tv.vn/v2/mqtt-digital/digital/mqtt/dangkythietbi"
        val call = retrofit.getMQTTInfo(body, "Bearer $hubToken", url)

        call.enqueue(object : Callback<MQTTInfoValueModel> {
            override fun onResponse(
                call: Call<MQTTInfoValueModel>,
                response: Response<MQTTInfoValueModel>
            ) {
                if (response.isSuccessful && response.body() != null) {
                    listMQTTInfo = response.body()!!.noiDung
                    Log.i(TAG, "MqttInfo: ${listMQTTInfo.size}")
                    getDestinationInfo(response.body()!!.noiDung)
                } else {
                    listMQTTInfo = emptyList()
                    Log.i(TAG, "Response failed: ${response.code()}")
                }
            }

            override fun onFailure(call: Call<MQTTInfoValueModel>, t: Throwable) {
                listMQTTInfo = emptyList()
                Log.e(TAG, "MqttInfo fail: ${t.message}", t)
            }
        })

    }

    private fun getDestinationInfo(listMQTTInfo: List<NoiDungMQTT>) {
        listMQTTInfo.forEach {
            if (it.topic.contains("tinh")) {
                nguonId = it.topic.substringAfter("/").uppercase()
                dichId = it.topic.substringAfter("/").uppercase()
                getSourceMQTT(nguonId, false)
            }
            if (it.topic.contains("huyen")) {
                nguonId = it.topic.substringAfter("/").uppercase()
            }
            if (it.topic.contains("xa")) {
                nguonId = it.topic.substringAfter("/").uppercase()
            }
        }
        getSourceMQTT(nguonId, true)

    }

    private fun getSourceMQTT(id: String, timNguon: Boolean) {
        val url = "https://v2-provider-data-warehouse.tek4tv.vn/Source/$id"

        try {
            val call = authService.getSourceMQTT(url)

            call.enqueue(object : Callback<SourceMQTT> {
                override fun onResponse(call: Call<SourceMQTT>, response: Response<SourceMQTT>) {
                    if (response.isSuccessful && response.body() != null) {
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
                    } else {
                        Log.i(TAG, "Response failed: ${response.code()}")
                    }
                }

                override fun onFailure(call: Call<SourceMQTT>, t: Throwable) {
                    listMQTTInfo = emptyList()
                    Log.e(TAG, "MqttInfo fail: ${t.message}", t)
                }
            })
        } catch (e: Exception) {
            Log.d(TAG, "getSourceMQTT: ${e.message}")
        }

    }

    private fun closeConnect() {
        isConnectSuccess = false
        hubToken = null
        listMQTTInfo = listOf()
        mqttClient?.disconnectMqtt()

    }

    fun reconnectToServer() {
        closeConnect()
        connectToServer()
    }

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
                    mqttClient = MQTTManager(
                        hubToken,
                        this@HubManager,
                        listMQTTInfo
                    )
                    while (!isConnectSuccess) {
                        //mqttClient?.connectToMQTT(appContext)
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
        startPingTimer()
    }

    override fun onReceiveMessageSuccess(message: String) {
        pingCounter = 0
        Log.d(TAG, "onReceiveMessageSuccess: $pingCounter")
        convertToHubCommand(message)
    }


    private fun sendMessage(message: String) {
        if(isConnectSuccess) {
            mqttClient?.publishToMQTT(message)
        }
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
                amLuong = getDeviceInfo(
                    Utils.deviceInfoVolume,
                    SerialPortController.deviceInfo
                ),
                viDo = GlobalProperties.getInst().lat,
                kinhDo = GlobalProperties.getInst().lng,
                thongTinThietBI = ThongTinThietBI(
                    congSuat = getDeviceInfo(
                        Utils.deviceInfoPA,
                        SerialPortController.deviceInfo
                    ),
                    nhietDo = getDeviceInfo(
                        Utils.deviceInfoTemperature,
                        SerialPortController.deviceInfo
                    ),
                    dungLuongSuDung = "0",
                    duLieuKhac = Utils.getOtherInfo(SerialPortController.deviceInfo,appStorageManager)
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

    fun loadJSONFromAsset(context: Context, fileName: String): String? {
        return try {
            val inputStream = context.assets.open(fileName)
            val size = inputStream.available()
            val buffer = ByteArray(size)
            inputStream.read(buffer)
            inputStream.close()
            String(buffer, Charsets.UTF_8)
        } catch (ex: Exception) {
            ex.printStackTrace()
            null
        }
    }

    fun convertToHubCommand(jsonMessage: String) {
        val wssData = wssLiveAdapter.fromJson(jsonMessage)
        var hubCommand = ""

        when (wssData!!.topic) {
            HubCommand.DV010 -> {
                Log.d(TAG, "Command: dv010")
                val body = parseJson(jsonMessage, moshi)
                if (body != null) {
                    hubCommand = HubCommand.UPDATE_MONTHLY_WSS
                    onMessageListener.invoke(hubCommand, jsonMessage)
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
                    Log.d(TAG, "Command: dv017 - ${hubCommand}- $responseHub")

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
                DanhSachNgayPhat = body.DanhSachNgayPhat
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
                )
            )
        )
        Log.d("WebsocketLog", "sendLogRemovePlaylist: $request")
        sendMessage(wssStatusAdapter.toJson(request))
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
        if (pingCounter >= 3) {
            Log.e(TAG, "Losed hub connection")
            hubToken = null
            //Try to reconnect to hub
            Log.e(TAG, "Try to reconnect Websocket")
            reconnectToServer()
        }
    }


}


