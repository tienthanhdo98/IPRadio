package app.tek4tv.ipradio.core


import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import app.tek4tv.ipradio.Secrets
import app.tek4tv.ipradio.di.MoshiInst
import app.tek4tv.ipradio.model.DirectMessage
import app.tek4tv.ipradio.model.HeartbeatRequest
import app.tek4tv.ipradio.model.MediaItem
import app.tek4tv.ipradio.model.MetadataModel
import app.tek4tv.ipradio.model.PingHubRequest
import app.tek4tv.ipradio.model.ResponseHub
import app.tek4tv.ipradio.model.Video
import app.tek4tv.ipradio.network.AuthService
import app.tek4tv.ipradio.ui.MainActivity.Companion.isEncoder
import app.tek4tv.ipradio.ui.MainActivity.Companion.streamingModel
import app.tek4tv.ipradio.utils.ConfigUtil
import app.tek4tv.ipradio.utils.GlobalProperties
import app.tek4tv.ipradio.utils.HubCommand
import app.tek4tv.ipradio.utils.Utils
import app.tek4tv.ipradio.utils.crypto.b64Decode
import app.tek4tv.ipradio.utils.crypto.decryptData
import app.tek4tv.ipradio.utils.crypto.encryptData
import app.tek4tv.ipradio.utils.crypto.xorString
import app.tek4tv.ipradio.utils.network.CaCertHelper
import app.tek4tv.ipradio.utils.network.NetworkUtils
import com.microsoft.signalr.HubConnection
import com.microsoft.signalr.HubConnectionBuilder
import com.microsoft.signalr.TransportEnum
import io.reactivex.Single
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import kotlin.time.Duration.Companion.milliseconds

/**
 * Manager hub connection: Connect to hub, ping,
 * receive command, ping
 * **/
class HubManager(
    private val appContext: Context,
    private val hubComm: HubComm,
    private val authService: AuthService,
    var onMessageListener: (command: String?, message: String?) -> Unit,
) {
    var onHubConnectedListener: (() -> Unit)? = null
    private val LOG_TAG = "HubManager"

    //10p
    val CONNECT_COUNTER_LIMIT = 20
    private val PING_DATA_INTERVAL = 30000L
    private val PING_HUB_INTERVAL = 30000L
    private val moshi = MoshiInst.moshi
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss")
    private var pingHubJob: Job? = null
    private var pingDataJob: Job? = null
    private var openNewConnectionJob: Job? = null

    private var coroutineScope = CoroutineScope(Dispatchers.IO)

    private val videoAdapter = moshi.adapter(Video::class.java)
    private val pingRequestAdapter = moshi.adapter(PingHubRequest::class.java)
    private val heartBeatAdapter = moshi.adapter(HeartbeatRequest::class.java)

    val deviceImei = Utils.getDeviceId(appContext)

    private val responseAdapter = moshi.adapter(ResponseHub::class.java)
    private val listAdapter = moshi.adapter(List::class.java)

    var pingCounter = 0

    // Cờ đánh dấu server Token API tek4tv có gọi được không
    // true = Internet thực sự có (gọi được API tek4tv)
    // false = Không gọi được API → mất Internet thực tế (SIM hết cước, mất mạng...)
    @Volatile
    var isTokenReachable = false

    private var hubConnection: HubConnection

    //current response Hub
    var responseHub = ResponseHub()
    var receivedConnectionId = ""

    //player info
    var playlistIndex = 0
    var currentMediaItem: MediaItem? = null
    var curMediaName = ""

    var versionName = GlobalProperties.Defaults.VERSION_NAME

    private var registerOnMessageListener = false

    private val mainHandler = Handler(Looper.getMainLooper())

    private var hubToken: String? = null

    private var firstConnect = true

    private var privateKeyStr: String

    private var serverTimeout = 0

    private var keepAliveInterval = 0

    //check ping to hub
    private var hubUrl = ""
    private var tokenUrl = ""
    var providerValue = ""
    var packageName = ""

    private var lastMessageKey: String = ""
    private var lastMessageTime: Long = 0L


    init {
        packageName = appContext.packageName
        providerValue = ConfigUtil.getString(
            appContext, ConfigUtil.PROVIDER, GlobalProperties.Defaults.PROVIDER_VALUE
        )!!
        hubUrl = ConfigUtil.getString(
            appContext,
            ConfigUtil.URL_HUB,
            "https://hubip-angiang.tek4tv.vn/hubServer"
        ) ?: "https://hubip-angiang.tek4tv.vn/hubServer"
        tokenUrl = ConfigUtil.getString(
            appContext, ConfigUtil.URL_TOKEN, Secrets().getgetTokenApi(packageName)
        )!!
        serverTimeout = ConfigUtil.getInt(
            appContext, ConfigUtil.ServerTimeout, 60000
        )
        keepAliveInterval = ConfigUtil.getInt(
            appContext, ConfigUtil.KeepAliveInterval, 30000
        )
        hubConnection = HubConnectionBuilder.create(hubUrl)
            .setHttpClientBuilderCallback { builder ->
                CaCertHelper.buildConfiguredOkHttpClient(builder, appContext)
            }
            .withAccessTokenProvider(Single.defer {
                val currentToken = hubToken
                if (currentToken != null) {
                    Single.just(currentToken)
                } else {
                    val token = getHubToken()
                    if (token != null) {
                        hubToken = token
                        Single.just(token)
                    } else Single.error(Exception())
                }
            }).withTransport(TransportEnum.WEBSOCKETS).build()
        hubConnection.serverTimeout = serverTimeout.toLong()
        hubConnection.keepAliveInterval = keepAliveInterval.toLong()

        privateKeyStr = Secrets().getHubPrivateKey(packageName)
    }

    //lấy token từ hub
    private fun getHubToken(): String? {
        return getHubToken(tokenUrl)
    }

    //check
    fun getHubToken(urlToken: String): String? {
        return try {
            val u = byteArrayOf(58, 26, 1, 19, 28, 17, 4, 75)
            val p = byteArrayOf(63, 8, 23, 18, 5, 31, 27, 74)

            // Thông tin xác thực lưu trong thư viện
//            val authInfo = Secrets().getAQNIjugs(packageName).split('-')
            val authInfo = Secrets().getAQNIjugs(packageName).split('-')

            val body = mapOf(
                String(xorString(u, packageName.reversed())) to authInfo[0],
                String(xorString(p, packageName.reversed())) to authInfo[1]
            )

            val call = authService.getToken(body, urlToken)
            val response = call.execute()
            if (response.isSuccessful) {
                isTokenReachable = true
                val token = response.body()!!.token
                token
            } else {
                isTokenReachable = true // Server trả response (dù lỗi 4xx/5xx) = Internet vẫn có
                null
            }
        } catch (e: Exception) {
            isTokenReachable = false // Exception (timeout, DNS fail, SSL fail) = mất Internet
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
    fun isHubConnected(): Boolean {
        return hubConnection.connectionState == com.microsoft.signalr.HubConnectionState.CONNECTED
    }

    /**
     * bắt đầu kết nối dend hub
     * **/
    fun connectToHub() {
        if (isHubConnected()) return
        if (openNewConnectionJob?.isActive == true) return

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
            Log.d(LOG_TAG, connectionId)
            pingCounter = 0
            onMessage()
            startPingTimer()
            startPingDataTimer()
            try {
                onHubConnectedListener?.invoke()
            } catch (e: Exception) {
                Log.e(LOG_TAG, "Error invoking onHubConnectedListener: ${e.message}")
            }
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

    /**
     * Nhận lệnh từ hub
     * **/
    private fun onMessage() {
        if (registerOnMessageListener) return

        registerOnMessageListener = true

        hubConnection.on("ReceiveMessage", { command: String?, message: String? ->
            try {
                mainHandler.post {
                    Log.d("command receive", command ?: "")
                    Log.d("message receive", message ?: "")
                    var meessDe = ""
                    var needDecrypt = false

                    val currentCommand = command?.trim() ?: ""
                    val currentTime = System.currentTimeMillis()
                    val messageKey = currentCommand
                    if (messageKey == lastMessageKey && (currentTime - lastMessageTime) < 1500) {
                        Log.d(LOG_TAG, "Bỏ qua bản tin trùng lặp: $currentCommand")
                        return@post
                    }
                    lastMessageKey = messageKey
                    lastMessageTime = currentTime

                    if (command != null && message != null) {
                        when (command) {
                            HubCommand.PONG -> {

                                if (firstConnect) firstConnect = false

                                pingCounter = 0

                            }

                            HubCommand.UPDATE_CONFIG, HubCommand.UPDATE_DESTINATION -> {
                                meessDe = b64Decode(message)
                                Log.d("meessDe", "onMessage: $meessDe")
                            }

                            HubCommand.UPDATE_STATUS -> {
                                if (responseHub.message != null) receivedConnectionId =
                                    responseHub.message ?: ""
                            }

                            HubCommand.SET_ENCRYPTED_VOLUME_DEVICE -> {
                                needDecrypt = true
                            }
                        }
                    }
                    if (meessDe != "") {
                        parseHubResponse(meessDe, needDecrypt)
                    } else {
                        parseHubResponse(message, needDecrypt)
                    }
                    //Handle command from server in MainActivity

                    onMessageListener.invoke(command, message)

                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, String::class.java, String::class.java)
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
            connectionId,
            command,
            Utils.toJsonString(moshi, DirectMessage::class.java, directMessage)
        )
    }

    fun sendEncryptedMessage(connectionId: String, command: String, message: String) {
        Log.d(LOG_TAG, "be4 encrypted: $message")
        val encryptedMessage = encryptData(message, publicKey)
        val directMessage = DirectMessage(deviceImei, encryptedMessage ?: "")

        sendDirectMessage(
            connectionId,
            command,
            Utils.toJsonString(moshi, DirectMessage::class.java, directMessage)
        )
    }

    private fun startPingTimer() {
        pingHubJob?.cancel()
        pingHubJob = coroutineScope.launch(Dispatchers.IO) {
            while (true) {
                sendPing()
                Log.d(LOG_TAG, "ping: $pingCounter")
                delay(PING_HUB_INTERVAL)
                checkConnection()
            }
        }
    }

    private fun sendPing() {
        pingCounter++
        try {
            val request = HeartbeatRequest().apply {
                key = deviceImei
                mess = ""
            }
            sendMessage(heartBeatAdapter.toJson(request), Utils.heartbeat)
            Log.d(LOG_TAG, "ping hub ${heartBeatAdapter.toJson(request)}")

        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
    }


    private fun startPingDataTimer() {
        pingDataJob?.cancel()
        pingDataJob = coroutineScope.launch(Dispatchers.IO) {
            while (true) {
                sendPingData()
                delay(PING_DATA_INTERVAL.milliseconds)
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
        if (isEncoder && streamingModel != null) {
            val streamInfo = MetadataModel(
                "STREAMINGINFO",
                "${streamingModel!!.streamUrl},${streamingModel!!.protocolStream},${streamingModel!!.bitrateStream},${streamingModel!!.samplerate}"
            )
            listMetadata.add(streamInfo)
        }
        return listAdapter.toJson(listMetadata)
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
                provider = providerValue
                lat = GlobalProperties.getInst().lat
                lng = GlobalProperties.getInst().lng
//                volume = GlobalProperties.getInst().volume
                volume = SerialPortController.volume
                deviceName = GlobalProperties.getInst().nameDevice
                createDate = (currentTime.time / 1000)
                userNameControl = GlobalProperties.getInst().userNameController
                isRecord = "0"
                deviceInfo = SerialPortController.deviceInfo
                metadata = getMetadata()
            }
            sendMessage(pingRequestAdapter.toJson(request), Utils.ping)
            Log.i("loghub", "log hub: ${pingRequestAdapter.toJson(request)}")

        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun checkConnection() {
//        if (pingCounter >= CONNECT_COUNTER_LIMIT) {
//            val countForHardRestart = ConfigUtil.getInt(appContext, ConfigUtil.CountForHardReset, 0)
//
//            hubComm.forceRestartApp(countForHardRestart)
//        }
        if (pingCounter >= 3) {
            Log.e(LOG_TAG, "Losed hub connection")
            hubToken = null
            pingCounter = 0
            openConnection()
        }
    }


    //parse hub command content
    private fun parseHubResponse(message: String?, needDecrypt: Boolean) {
        if (message.isNullOrEmpty()) return
        try {
            responseHub = ResponseHub()
            if (needDecrypt) {
                val decryptedStr = decryptData(message, privateKeyStr)
                if (decryptedStr != null && decryptedStr.startsWith("{")) responseHub =
                    responseAdapter.fromJson(decryptedStr)!!
            } else {
                if (message.startsWith("{")) responseHub = responseAdapter.fromJson(message)!!
            }
        } catch (e: Exception) {
            Log.d("alarmx", "JsonEncodingException: ${e.message}")
        }
    }
}

interface HubComm {
    fun forceRestartApp(countHardRestart: Int)
    fun forceRestartDevice()
}


val publicKey =
    "MFwwDQYJKoZIhvcNAQEBBQADSwAwSAJBAIkOIaw3KkEJHxvfYZ9FZzC4zBryierYfjXocoi2zOY/HvfXK6bfjuu7ZwiaUX5C6tOl8T0Dd0AEw32NwhAzi60CAwEAAQ=="
