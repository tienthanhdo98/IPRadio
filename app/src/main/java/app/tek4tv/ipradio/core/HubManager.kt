package app.tek4tv.ipradio.core

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import app.tek4tv.ipradio.MyApp
import app.tek4tv.ipradio.Secrets
import app.tek4tv.ipradio.di.MoshiInst
import app.tek4tv.ipradio.model.*
import app.tek4tv.ipradio.network.AuthService
import app.tek4tv.ipradio.ui.MainActivity
import app.tek4tv.ipradio.ui.MainActivity.Companion.bitrate_stream

import app.tek4tv.ipradio.ui.MainActivity.Companion.jsonMapAdapter
import app.tek4tv.ipradio.ui.MainActivity.Companion.protocol_stream
import app.tek4tv.ipradio.ui.MainActivity.Companion.samplerate
import app.tek4tv.ipradio.ui.MainActivity.Companion.urlStream
import app.tek4tv.ipradio.utils.*
import app.tek4tv.ipradio.utils.HubCommand.DV002
import app.tek4tv.ipradio.utils.HubCommand.DV003
import app.tek4tv.ipradio.utils.HubCommand.DV004
import app.tek4tv.ipradio.utils.HubCommand.DV005
import app.tek4tv.ipradio.utils.HubCommand.DV006
import app.tek4tv.ipradio.utils.HubCommand.DV007
import app.tek4tv.ipradio.utils.HubCommand.DV008
import app.tek4tv.ipradio.utils.HubCommand.DV009
import app.tek4tv.ipradio.utils.Utils.Companion.convertAuthor
import app.tek4tv.ipradio.utils.Utils.Companion.getTypeOfMediaItem
import app.tek4tv.ipradio.utils.crypto.decryptData
import app.tek4tv.ipradio.utils.crypto.encryptData
import app.tek4tv.ipradio.utils.network.NetworkUtils
import com.microsoft.signalr.HubConnection
import com.microsoft.signalr.HubConnectionBuilder
import com.microsoft.signalr.TransportEnum
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Types
import io.reactivex.Single
import kotlinx.coroutines.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean


/**
 * Manager hub connection: Connect to hub, ping,
 * receive command, ping
 * **/
class HubManager(
    private val hubComm: HubComm,
    private val authService: AuthService,
    var onMessageListener: (command: String?, message: String?) -> Unit,
    ) {
    private val LOG_TAG = "HubManager"
    private val CONNECT_COUNTER_LIMIT = 10
    private val PING_DATA_INTERVAL = 15000L
    private val PING_HUB_INTERVAL = 10000L
    private val moshi = MoshiInst.moshi
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss")
    private var pingHubJob: Job? = null
    private var pingDataJob: Job? = null
    private var openNewConnectionJob: Job? = null

    private var coroutineScope = CoroutineScope(Dispatchers.Main)
    val appContext: Context = MyApp.instance!!.applicationContext

    private val videoAdapter = moshi.adapter(Video::class.java)
    private val pingRequestAdapter = moshi.adapter(PingHubRequest::class.java)
    private val deviceImei = Utils.getDeviceId(appContext)
    private val responseAdapter = moshi.adapter(ResponseHub::class.java)
    private val listAdapter = moshi.adapter(List::class.java)

    val hubEventObserver = mutableListOf<HubEventObserver>()

    var typeMediaItem = Types.newParameterizedType(
        MutableList::class.java,
        MediaItem::class.java
    )
    var jsonMediaItemAdapter = moshi.adapter<List<MediaItem>>(typeMediaItem)

    var pingCounter = 0

    private var hubConnection: HubConnection

    //current response Hub
    var responseHub = ResponseHub()
    var receivedConnectionId = ""

    //player info
    var playlistIndex = 0
    var currentMediaItem: MediaItem? = null
    var curMediaName = ""

    var versionName = ""

    private var registerOnMessageListener = false


    private val mainHandler = Handler(Looper.getMainLooper())

    private val notifyDisconnected = AtomicBoolean(false)


    private var hubToken: String? = null

    private var firstConnect = true

    private var privateKeyStr: String

    private var serverTimeout = 60000
    private var keepAliveInterval = 30000

    //check ping to hub
    private var hubUrl = ""


    init {
        versionName = try {
            appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName
        } catch (e: Exception) {
            ""
        }
        hubUrl = ConfigUtil.getStringEncrypt(
            appContext,
            ConfigUtil.URL_HUB,
            "https://gateway-qltt.tek4tv.vn/ws/ipradio"
        )!!
        serverTimeout = ConfigUtil.getInt(
            appContext,
            ConfigUtil.ServerTimeout, 60000
        )
        keepAliveInterval = ConfigUtil.getInt(
            appContext,
            ConfigUtil.KeepAliveInterval, 30000
        )
        hubConnection = HubConnectionBuilder.create(hubUrl).shouldSkipNegotiate(false)
            .withAccessTokenProvider(Single.defer {
                if (hubToken != null) {
                    Single.just(hubToken)
                } else {
                    val token = getHubToken()
                    if (token != null) {
                        hubToken = token
                        Single.just(token)
                    } else
                        Single.error(Exception())
                }
            })
            .withTransport(TransportEnum.WEBSOCKETS)
            .build()
        hubConnection.serverTimeout = serverTimeout.toLong()
        hubConnection.keepAliveInterval = keepAliveInterval.toLong()

        privateKeyStr = Secrets().getHubPrivateKey(MyApp.instance!!.packageName)
    }

    //lấy token từ hub
    private fun getHubToken(): String? {
        return try {
            //Encrypted json variable name
            val body = mapOf(
                "Username" to "provider_tek4tv",
                "Password" to "733929b7-33e3-44fe-b03f-1e7430ad64d6",
            )

            val url = ConfigUtil.getStringEncrypt(
                context = appContext,
                ConfigUtil.URL_TOKEN,
                "https://gateway-qltt.tek4tv.vn/api/v2/oauth/token"
            )

            Log.i("urlbase", url!!)
            val call = authService.getToken(body, url)
            val response = call.execute()

            if (response.isSuccessful) {
                val token = response.body()!!.noiDung.token
                Log.i("tntntn", "getHubToken: $token")
                token
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    //check
    fun getHubToken(urlToken: String): String? {
        return try {
            val body = mapOf(
                "Username" to "provider_tek4tv",
                "Password" to "733929b7-33e3-44fe-b03f-1e7430ad64d6",
            )

            val url = ConfigUtil.getStringEncrypt(
                context = appContext,
                ConfigUtil.URL_TOKEN,
                "https://gateway-qltt.tek4tv.vn/api/v2/oauth/token"
            )

            Log.i("urlbase", url!!)
            val call = authService.getToken(body, url)
            val response = call.execute()

            if (response.isSuccessful) {
                val token = response.body()!!.noiDung.token
                Log.i("tntntn", "getHubToken: $token")
                token
            } else {
                null
            }

        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun openConnection2(hubConnection: HubConnection): String? {
        //open connection on another thread
        Log.i(LOG_TAG, "open connection")
        return withContext(Dispatchers.IO) {
            try {
                hubConnection.start().blockingAwait()
                hubConnection.connectionId
            } catch (e: Exception) {
                hubToken = null
                e.printStackTrace()
                Log.e(LOG_TAG, "error connecting to hub: ${e.message ?: ""}")
                null
            }
        }
    }

    //end check
    /**
     * bắt đầu kết nối dend hub
     * **/
    fun connectToHub() {
        if (!firstConnect) return

        openNewConnectionJob?.cancel()
        openNewConnectionJob = coroutineScope.launch {
            var connectionId = openConnection()

            var i = 0

            while (connectionId == null) {
                if (ConfigUtil.getString(appContext, "checkhub", "0") != "0") {
                    ConfigUtil.putString(appContext, "checkhub", "0")
                    Log.d("alarmx", "Change 0")
                }
                //Quá 100 lần không kết nối được lên hub thì hởi động lại app
                if (i >= CONNECT_COUNTER_LIMIT) {
                    val countForHardRestart =
                        ConfigUtil.getInt(appContext, ConfigUtil.CountForHardReset, 0)
                    hubComm.forceRestartApp(countForHardRestart)
                }
                i++
                delay(PING_DATA_INTERVAL)
                connectionId = openConnection()
                Log.e(LOG_TAG, "Trying to reconnect... $i")
                pingCounter++
            }
            Log.d(LOG_TAG, "$connectionId")
            onMessage()
            startPingTimer()
            //notifyHubConnected()
            openNewConnectionJob = null
        }
    }

    private suspend fun openConnection(): String? {
        //open connection on another thread
        Log.i(LOG_TAG, "open connection")
        return withContext(Dispatchers.IO) {
            try {
                hubConnection.start().blockingAwait()

                hubConnection.connectionId
            } catch (e: Exception) {
                hubToken = null
                e.printStackTrace()
                null
            }
        }
    }


    //wss
    val wssStatusAdapter: JsonAdapter<WssStatusModel> = moshi.adapter(WssStatusModel::class.java)
    val wssLogMediaAdapter: JsonAdapter<WssMediaLogModel> =
        moshi.adapter(WssMediaLogModel::class.java)
    val wssPlaylistAdapter: JsonAdapter<WssPlaylist> = moshi.adapter(WssPlaylist::class.java)
    val wssAdapter: JsonAdapter<WssModel> = moshi.adapter(WssModel::class.java)
    val wssLiveAdapter: JsonAdapter<WssLiveModel> = moshi.adapter(WssLiveModel::class.java)
    val mediaItemMoshiAdapter: JsonAdapter<MediaItem> =
        MoshiInst.moshi.adapter(MediaItem::class.java)


    /**
     * Nhận lệnh từ hub
     * **/
    private fun onMessage() {
        if (registerOnMessageListener) return

        registerOnMessageListener = true

        hubConnection.on("ReceiveMessage", { message: String? ->
            try {
                Log.d("Websocket receive", message ?: "")
                //Vừa kết nối được lại hub
                if (pingCounter >= 3 && !firstConnect) {
                    Log.e(LOG_TAG, "Hub Reconnected")
                    notifyHubReconnected()
                    notifyDisconnected.compareAndSet(true, false)
                }
                if (firstConnect) firstConnect = false
                pingCounter = 0
                // Handle command from server in MainActivity
                convertWssToHubCommad(message!!)
            } catch (e: Exception) {
                Log.d("Websocket err", e.message.toString())
            }
        }, String::class.java)

    }

    private fun sendMessageWSS(mess: String) {
        try {
            hubConnection.invoke("sendMessage", mess)
            Log.d("Websocket mess", "${wssStatusAdapter.fromJson(mess)?.id}")
            // Log.i("wssLiveSS", "volume: $mess")
        } catch (e: Exception) {
            Log.e("Websocket", e.message!!)
            e.printStackTrace()
        }
    }

    private fun sendPingWss() {
        pingCounter++

        var pathPlaying = ""
        if (currentMediaItem != null) {
            pathPlaying = currentMediaItem!!.pathBackup
        }
        SerialPortController.deviceInfo
        val request = WssStatusModel(
            id = hubConnection.connectionId, topic = DV008, payload = PayloadStatus(
                dichID = "H02",
                tenDich = "Bắc Giang",
                nguonID = GlobalProperties.getInst().destinationId,
                tenNguon = GlobalProperties.getInst().destinationName,
                cumLoaID = deviceImei,
                tenThietBI = GlobalProperties.getInst().nameDevice,
                tenLoaiThietBI = "Cụm loa truyền thanh",
                maNhaCungCap = GlobalProperties.getInst().providerValue,
                tenNhaCungCap = GlobalProperties.getInst().providerValue,
                amLuong = SerialPortController.volume,
                trangThaiHoatDong = "",
                trangThaiKetNoi = NetworkUtils.getNetworkInfo(appContext),
                viDo = GlobalProperties.getInst().lat,
                kinhDo = GlobalProperties.getInst().lng,
                thongTinThietBI = ThongTinThietBI(
                    congSuat = "1",
                    nhietDo = "10",
                    dungLuongSuDung = "1024000"
                ),
                thoiDiemBatDau = (Calendar.getInstance().time.time / 1000),
                noiDungPhat = pathPlaying,
                phienBanUngDung = versionName,
            )
        )
        Log.i("Websocket", "log hub: ${wssStatusAdapter.toJson(request)}")

        sendMessageWSS(wssStatusAdapter.toJson(request))
    }

    private fun sendPingData() {
        try {
            val i = playlistIndex
            var mode = "-1"
            if (currentMediaItem != null) {
                val path = currentMediaItem!!.pathBackup
                mode = if (path.isNotEmpty() && !File(path).exists()) "1"
                else "0"
            }
            val video = Video("" + i, mode).apply {

                if (currentMediaItem != null) {
                    mediaName = curMediaName
                    path = currentMediaItem!!.pathBackup
                    level = currentMediaItem!!.level
                }
            }

            val currentTime = Calendar.getInstance().time
            val request = PingHubRequest().apply {
                imei = deviceImei
                status = "START"
                connectionId = (hubConnection.connectionId)
                this.video = videoAdapter.toJson(video)
                startTime = dateFormat.format(Date())
                networkInfo = NetworkUtils.getNetworkInfo(appContext)
                version = versionName
                destinationId = GlobalProperties.getInst().destinationId
                destinationName = GlobalProperties.getInst().destinationName
                appName = "Ipradio"
                logType = hubUrl
                provider = GlobalProperties.getInst().providerValue
                lat = GlobalProperties.getInst().lat
                lng = GlobalProperties.getInst().lng
                volume = SerialPortController.volume
                deviceName = GlobalProperties.getInst().nameDevice
                createDate = (currentTime.time / 1000)
                userNameControl = GlobalProperties.getInst().userNameController
                isRecord = "0"
                deviceInfo = SerialPortController.deviceInfo
                metadata = getMetadata()
            }
            sendMessage(pingRequestAdapter.toJson(request), Utils.ping)
//            Log.i("loghub", "log hub: ${request.video}")
//            Log.i("loghub", "log hub: ${request.deviceInfo}")
            Log.i("loghub", "log hub: ${request.networkInfo}")
            Log.i("loghub", "log hub: ${request.metadata}")

        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
    }



    fun convertWssToHubCommad(jsonMessage: String) {
        val wssData = wssAdapter.fromJson(jsonMessage)
        var hubCommand = ""
        when (wssData!!.topic) {
            DV002 -> {
                val res = wssLiveAdapter.fromJson(jsonMessage)
                val body = res!!.payload
                if (body != null) {
                    val mapMonthSchedule = convertWssMessToPlaylist(body)
                    val jsonMess = jsonMapAdapter.toJson(mapMonthSchedule)
                    hubCommand = HubCommand.UPDATE_MONTHLY_WSS
                    onMessageListener.invoke(hubCommand, jsonMess)
                    sendLogSchedule(DV006, body)
                }
            }
            DV003 -> {
                val res = wssLiveAdapter.fromJson(jsonMessage)
                val body = res!!.payload
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
                            mediaID = body.banTinID.toInt(),
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
                            author = Author()
                        )
                    )
                }
                Log.d("wss", "convertWssToHubCommad: ${hubCommand}-${jsonMessage}")
                onMessageListener.invoke(hubCommand, jsonMessage)
            }
            //Hủy lịch
            DV004 ->{
                val res = wssLiveAdapter.fromJson(jsonMessage)
                val body = res!!.payload
                if (body != null) {
                    hubCommand = HubCommand.DELETE_MONTHLY_LIST_WSS
                    onMessageListener.invoke(hubCommand, "null")
                    sendLogSchedule(DV007, body)
                }
            }

            DV009 -> {
                val res = wssLiveAdapter.fromJson(jsonMessage)
                val body = res!!.payload
                if (body != null) {

                    val maLenh = body.dieuKhienThietBI?.maLenh
                    val giaTri = body.dieuKhienThietBI?.thamSo

                    Log.d("wss", "convertWssToHubCommad: Mã ${maLenh}- giá trị: ${giaTri}")
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

                    }
                    onMessageListener.invoke(hubCommand, jsonMessage)

                }
            }


        }
    }

    fun sendLogMediaSuccess(mediaItem: MediaItem, paState: String) {
        var sourceId = ""
        var sourceName = ""
        val tacGia = TacGia(
            tenDayDu = mediaItem.author?.fullName ?: "",
            butDanh = mediaItem.author?.userName ?: "",
            email = mediaItem.author?.email ?: ""
        )
        if (mediaItem.sourceID != null && mediaItem.sourceName != null) {
            sourceId = mediaItem.sourceID
            sourceName = mediaItem.sourceName
        } else {
            sourceId = GlobalProperties.getInst().destinationId
            sourceName = GlobalProperties.getInst().destinationName
        }
        val request = WssStatusModel(
            id = hubConnection.connectionId, topic = DV005, payload = WssMediaLogModel(
                nguonID = sourceId,
                tenNguon = sourceName,
                dichID = "H02",
                tenDich = "Bắc Giang",
                cumLoaID = deviceImei,
                maNhaCungCap = GlobalProperties.getInst().providerValue,
                tenNhaCungCap = GlobalProperties.getInst().providerValue,
                amLuong = SerialPortController.volume,
                thoiDiemBatDau = (Calendar.getInstance().time.time / 1000),
                congSuat = paState,
                banTinID = mediaItem.projectID,
                loaiBanTin = mediaItem.contentType?.value,
                mucDoUuTien = mediaItem.priority?.value,
                noiDung = mediaItem.pathBackup,
                noiDungTomTat = mediaItem.description,
                tacGia = tacGia,
                tenThietBi = GlobalProperties.getInst().nameDevice,
                thoiLuong = mediaItem.duration,
                tieuDe = mediaItem.title,
                danhSachBanTin = listOf(),
                danhSachDiaBan = listOf(),
                danhSachThietBi = listOf()
            )
        )

//        loaiLinhVuc =  ,
//        tiepAm = ,
//        nguonTiepAm = ,
//        nguonTin = ,
//        thongTinChiTietBanTin = ,
//        thoiGianSanXuat = ,

        sendMessageWSS(wssStatusAdapter.toJson(request))
    }

    private fun sendLogSchedule(commanDV: String, body: PayloadLive) {
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
            id = hubConnection.connectionId, topic = commanDV, payload = WssMediaLogModel(
                nguonID = GlobalProperties.getInst().destinationId,
                tenNguon = GlobalProperties.getInst().destinationName,
                dichID = "H02",
                tenDich = "Bắc Giang",
                cumLoaID = deviceImei,
                maNhaCungCap = GlobalProperties.getInst().providerValue,
                tenNhaCungCap = GlobalProperties.getInst().providerValue,
                amLuong = SerialPortController.volume,
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
        sendMessageWSS(wssStatusAdapter.toJson(request))
    }


    private fun convertWssMessToPlaylist(payload: PayloadLive): Map<String, String> {
        val listMedia = payload.danhSachBanTin
        val mapData: MutableMap<String, String> = mutableMapOf()
        if (listMedia?.isNotEmpty() == true) {
            for (item in listMedia) {
                val ngayPhat =
                    unixTimeStampToDateTime(item.danhSachNgayPhat.first().ngayPhat)
                Log.d(
                    "wssplaylist",
                    "convertWssMessToPlaylist: ${ngayPhat}"
                )
                val jsonSchedule =
                    findScheduleByDate2(
                        payload,
                        unixTimeStampToDateTime(item.danhSachNgayPhat.first().ngayPhat)
                    )
                mapData[unixTimeStampToDateTime(item.danhSachNgayPhat.first().ngayPhat)] =
                    jsonSchedule
            }
        }
        Log.d("wssplaylist", "Convert Map: ${mapData.size}")
        Log.d("wssplaylist", "Convert Map: $mapData")
        return mapData

    }

    private fun findScheduleByDate2(payload: PayloadLive, ngayPhat: String): String {
        val listMedia = payload.danhSachBanTin
        val listPhatTheoNgay = mutableListOf<DanhSachBanTin>()
        val listBanTinPhatTheoNgay = mutableListOf<MediaItem>()
        // Tìm khung giờ
        if (listMedia != null) {
            for (item in listMedia) {
                if (unixTimeStampToDateTime(item.danhSachNgayPhat.first().ngayPhat) == ngayPhat) {
                    listPhatTheoNgay.add(item)
                }
            }
        }
        if (listPhatTheoNgay.isNotEmpty()) {
            var count = 0
            for ((checkCountStart, item) in listPhatTheoNgay.withIndex()) {
                var tObjStartTime = ""
                tObjStartTime = if (checkCountStart > 0) {
                    convertTimeStampToHHMMSS(item.danhSachNgayPhat.first().thoiDiemPhat.first().thoiGianBatDau - 0)
                } else {
                    convertTimeStampToHHMMSS(item.danhSachNgayPhat.first().thoiDiemPhat.first().thoiGianBatDau)
                }
                val tObjEndTime: String = convertTimeStampToHHMMSS(
                    item.danhSachNgayPhat.first().thoiDiemPhat.first().thoiGianKetThuc
                )
                val tObjStart = MediaItem(
                    id = count,
                    mediaID = count,
                    projectID = item.banTinID,
                    title = item.tieuDe,
                    body = "start",
                    startTime = tObjStartTime,
                    fixTime = tObjStartTime,
                    end = tObjEndTime,
                )
                count++
                listBanTinPhatTheoNgay.add(tObjStart)
                val obj = MediaItem(
                    id = count,
                    mediaID = count,
                    projectID = item.banTinID,
                    sourceID = payload.nguonID,
                    sourceName = payload.tenNguon,
                    destinationID = payload.dichID,
                    destinationName = payload.tenDich,
                    title = item.tieuDe,
                    description = item.noiDungTomTat,
                    duration = item.thoiLuong,
                    contentType = Utils.convertContentType(item.loaiBanTin),
                    priority = Utils.convertPriority(item.mucDoUuTien),
                    category = Utils.convertFiend(item.loaiLinhVuc),
                    author = convertAuthor(item.tacGia),
                    body = item.noiDung,
                    sourceReference = item.tenDiaBanTAO,
                    type = getTypeOfMediaItem(item.loaiBanTin.toInt()),
                    createDate = item.thoiGianSANXuat.toString(),
                    metadata = mutableListOf(),
                    tag = mutableListOf(),
                    fixTime = tObjEndTime,
                    startTime = tObjStartTime,
                    start = tObjStartTime,
                    end = tObjEndTime,
                )
                count++
                listBanTinPhatTheoNgay.add(obj)

                val tObEnd = MediaItem(
                    id = count,
                    mediaID = count,
                    projectID = item.banTinID,
                    title = item.tieuDe,
                    body = "end",
                    startTime = tObjStartTime,
                    fixTime = tObjEndTime,
                    end = tObjEndTime,
                )
                count++;
                listBanTinPhatTheoNgay.add(tObEnd)
            }
        }
        Log.d(
            "wssplaylist",
            "findScheduleByDate: ${jsonMediaItemAdapter.toJson(listBanTinPhatTheoNgay)}"
        )
        return jsonMediaItemAdapter.toJson(listBanTinPhatTheoNgay)

    }


    private fun findScheduleByDate(payload: PayloadLive, ngayPhat: Long): String {
        val listMedia = payload.danhSachBanTin
        val listPhatTheoNgay = mutableListOf<DanhSachBanTin>()
        val listBanTinPhatTheoNgay = mutableListOf<MediaItem>()
        // Tìm khung giờ
        if (listMedia != null) {
            for (item in listMedia) {
                for (listTime in item.danhSachNgayPhat) {
                    if (listTime.ngayPhat == ngayPhat) {
                        listPhatTheoNgay.add(item)
                    }
                }
            }
        }
        if (listPhatTheoNgay.isNotEmpty()) {
            var count = 0
            for ((checkCountStart, item) in listPhatTheoNgay.withIndex()) {
                var tObjStartTime = ""
                tObjStartTime = if (checkCountStart > 0) {
                    convertTimeStampToHHMMSS(item.danhSachNgayPhat.first().thoiDiemPhat.first().thoiGianBatDau - 0)
                } else {
                    convertTimeStampToHHMMSS(item.danhSachNgayPhat.first().thoiDiemPhat.first().thoiGianBatDau)
                }
                val tObjEndTime: String = convertTimeStampToHHMMSS(
                    item.danhSachNgayPhat.first().thoiDiemPhat.first().thoiGianKetThuc
                )
                val tObjStart = MediaItem(
                    id = count,
                    mediaID = count,
                    projectID = item.banTinID,
                    title = item.tieuDe,
                    body = "start",
                    startTime = tObjStartTime,
                    fixTime = tObjStartTime,
                    end = tObjEndTime,
                )
                count++
                listBanTinPhatTheoNgay.add(tObjStart)
                val obj = MediaItem(
                    id = count,
                    mediaID = count,
                    projectID = item.banTinID,
                    sourceID = payload.nguonID,
                    sourceName = payload.tenNguon,
                    destinationID = payload.dichID,
                    destinationName = payload.tenDich,
                    title = item.tieuDe,
                    description = item.noiDungTomTat,
                    duration = item.thoiLuong,
                    contentType = Utils.convertContentType(item.loaiBanTin),
                    priority = Utils.convertPriority(item.mucDoUuTien),
                    category = Utils.convertFiend(item.loaiLinhVuc),
                    author = convertAuthor(item.tacGia),
                    body = item.noiDung,
                    sourceReference = item.tenDiaBanTAO,
                    type = getTypeOfMediaItem(item.loaiBanTin.toInt()),
                    createDate = item.thoiGianSANXuat.toString(),
                    metadata = mutableListOf(),
                    tag = mutableListOf(),
                    fixTime = tObjEndTime,
                    startTime = tObjStartTime,
                    start = tObjStartTime,
                    end = tObjEndTime,
                )
                count++
                listBanTinPhatTheoNgay.add(obj)

                val tObEnd = MediaItem(
                    id = count,
                    mediaID = count,
                    projectID = item.banTinID,
                    title = item.tieuDe,
                    body = "end",
                    startTime = tObjStartTime,
                    fixTime = tObjEndTime,
                    end = tObjEndTime,
                )
                count++;
                listBanTinPhatTheoNgay.add(tObEnd)
            }
        }
        Log.d(
            "wssplaylist",
            "findScheduleByDate: ${jsonMediaItemAdapter.toJson(listBanTinPhatTheoNgay)}"
        )
        return jsonMediaItemAdapter.toJson(listBanTinPhatTheoNgay)

    }


    private fun sendMessage(mess: String, command: String) {
        try {
            hubConnection.invoke(command, Utils.getDeviceId(appContext), mess)
            Log.i("datadevice", "volume: $mess")
        } catch (e: Exception) {
            Log.e(LOG_TAG, e.message!!)
            e.printStackTrace()
        }
    }

    private fun sendDirectMessage(connectionId: String, command: String, message: String) {
        try {
            hubConnection.invoke(Utils.DIRECT_MESSAGE, connectionId, command, message)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun sendHubDirectMessage(connectionId: String, command: String, message: String) {
        val directMessage = DirectMessage(deviceImei, message)
        sendDirectMessage(
            connectionId, command,
            Utils.toJsonString(moshi, DirectMessage::class.java, directMessage)
        )
    }

    fun sendEncryptedMessage(connectionId: String, command: String, message: String) {
        Log.d(LOG_TAG, "be4 encrypted: $message")
        val encryptedMessage = encryptData(message, publicKey)
        val directMessage = DirectMessage(deviceImei, encryptedMessage ?: "")

        sendDirectMessage(
            connectionId, command,
            Utils.toJsonString(moshi, DirectMessage::class.java, directMessage)
        )
    }

    private fun startPingTimer() {
        pingHubJob?.cancel()
        pingHubJob = coroutineScope.launch(Dispatchers.Default) {
            while (true) {
                sendPingWss()
                Log.d(LOG_TAG, "ping: $pingCounter")
                delay(PING_HUB_INTERVAL)
                checkConnection()
            }
        }
    }


    private fun getMetadata(): String {
        //thong tin lich phat
        val listMetadata: MutableList<MetadataModel> = mutableListOf()
        val modifyTimePlaylist =
            ConfigUtil.getString(appContext, ConfigUtil.KEY_MODIFY_TIME_PLAYLIST, "")
        val monthlyPlaylistId =
            ConfigUtil.getString(appContext, ConfigUtil.MONTHLY_SCENARIO_URL, "")?.split("/")
                ?.last()
        val playlistInfo = MetadataModel("PLAYLISTINFO", "$monthlyPlaylistId,$modifyTimePlaylist")
        listMetadata.add(playlistInfo)
        //thong tin stream encoder
        if (MainActivity.isEncoder) {
            val streamInfo = MetadataModel(
                "STREAMINGINFO",
                "${urlStream},${protocol_stream},${bitrate_stream},${samplerate}"
            )
            listMetadata.add(streamInfo)
        }
        return listAdapter.toJson(listMetadata)
    }


    private suspend fun checkConnection() {
        if (pingCounter >= CONNECT_COUNTER_LIMIT) {
            val countForHardRestart = ConfigUtil.getInt(appContext, ConfigUtil.CountForHardReset, 0)

            hubComm.forceRestartApp(countForHardRestart)
        }
        if (pingCounter >= 3) {
            Log.e(LOG_TAG, "Losed hub connection")
            if (notifyDisconnected.compareAndSet(false, true)) {
                hubToken = null
                notifyHubDisconnected()
                Log.e(LOG_TAG, "Hub Disconnected")
            }
            //Try to reconnect to hub
            openConnection()
        }
    }

    @Synchronized
    private fun notifyHubReconnected() {
        mainHandler.post {
            hubEventObserver.forEach { it.onReconnectedToHub() }
        }
    }

    @Synchronized
    private fun notifyHubDisconnected() {
        mainHandler.post {
            hubEventObserver.forEach { it.onLostHubConnection() }
        }
    }

    //parse hub command content
    private fun parseHubResponse(message: String?, needDecrypt: Boolean) {
        if (message.isNullOrEmpty()) return
        try {
            responseHub = ResponseHub()
            if (needDecrypt) {
                val decryptedStr = decryptData(message, privateKeyStr)
                if (decryptedStr != null && decryptedStr.startsWith("{"))
                    responseHub = responseAdapter.fromJson(decryptedStr)!!
            } else {
                if (message.startsWith("{"))
                    responseHub = responseAdapter.fromJson(message)!!
            }
        } catch (e: Exception) {
            e.printStackTrace()
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

val publicKey =
    "MFwwDQYJKoZIhvcNAQEBBQADSwAwSAJBAIkOIaw3KkEJHxvfYZ9FZzC4zBryierYfjXocoi2zOY/HvfXK6bfjuu7ZwiaUX5C6tOl8T0Dd0AEw32NwhAzi60CAwEAAQ=="
