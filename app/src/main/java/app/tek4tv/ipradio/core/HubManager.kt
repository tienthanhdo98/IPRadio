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
import app.tek4tv.ipradio.ui.MainActivity.Companion.protocol_stream
import app.tek4tv.ipradio.ui.MainActivity.Companion.samplerate
import app.tek4tv.ipradio.ui.MainActivity.Companion.urlStream
import app.tek4tv.ipradio.utils.ConfigUtil
import app.tek4tv.ipradio.utils.GlobalProperties
import app.tek4tv.ipradio.utils.HubCommand
import app.tek4tv.ipradio.utils.Utils
import app.tek4tv.ipradio.utils.crypto.b64Decode
import app.tek4tv.ipradio.utils.crypto.decryptData
import app.tek4tv.ipradio.utils.crypto.encryptData
import app.tek4tv.ipradio.utils.crypto.xorString
import app.tek4tv.ipradio.utils.network.NetworkUtils
import com.microsoft.signalr.HubConnection
import com.microsoft.signalr.HubConnectionBuilder
import com.microsoft.signalr.TransportEnum
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
    val CONNECT_COUNTER_LIMIT = 20
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
    private val heartBeatAdapter = moshi.adapter(HeartbeatRequest::class.java)
    private val deviceImei = Utils.getDeviceId(appContext)
    private val responseAdapter = moshi.adapter(ResponseHub::class.java)
    private val listAdapter = moshi.adapter(List::class.java)

    val hubEventObserver = mutableListOf<HubEventObserver>()

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

    private var serverTimeout = 0
    private var keepAliveInterval = 0

    //check ping to hub
    private var hubUrl = ""


    init {
        versionName = try {
            appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName
        } catch (e: Exception) {
            ""
        }
        //https://hubip-alpha-binhdinh.tek4tv.vn/
        hubUrl = ConfigUtil.getStringEncrypt(
            appContext,
            ConfigUtil.URL_HUB,
            "https://hubip-binhduong.tek4tv.vn/hubServer"
        )!!
        serverTimeout = ConfigUtil.getInt(
            appContext,
            ConfigUtil.ServerTimeout, 60000
        )
        keepAliveInterval = ConfigUtil.getInt(
            appContext,
            ConfigUtil.KeepAliveInterval, 30000
        )
        hubConnection = HubConnectionBuilder.create(hubUrl)
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
            val u = byteArrayOf(58, 26, 1, 19, 28, 17, 4, 75)
            val p = byteArrayOf(63, 8, 23, 18, 5, 31, 27, 74)

            //Thông tin xác thực lưu trong thư viện
            val authInfo = Secrets().getAQNIjugs(MyApp.instance!!.packageName).split('-')

            val body = mapOf(
                String(xorString(u, MyApp.instance!!.packageName.reversed())) to authInfo[0],
                String(xorString(p, MyApp.instance!!.packageName.reversed())) to authInfo[1]
            )

            val url = ConfigUtil.getStringEncrypt(
                context = appContext,
                ConfigUtil.URL_TOKEN,
                "https://apigatewayhttt.tek4tv.vn/api/Auth/Login"
            )
            val call = authService.getToken(body, url!!)
            val response = call.execute()
            if (response.isSuccessful) {
                val token = response.body()!!.token
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
    fun getHubToken(urlToken : String): String? {
        return try {
            //Encrypted json variable name
            val u = byteArrayOf(58, 26, 1, 19, 28, 17, 4, 75)
            val p = byteArrayOf(63, 8, 23, 18, 5, 31, 27, 74)

            //Thông tin xác thực lưu trong thư viện
            val authInfo = Secrets().getAQNIjugs(MyApp.instance!!.packageName).split('-')

            val body = mapOf(
                String(xorString(u, MyApp.instance!!.packageName.reversed())) to authInfo[0],
                String(xorString(p, MyApp.instance!!.packageName.reversed())) to authInfo[1]
            )

            val call = authService.getToken(body, urlToken)
            val response = call.execute()

            if (response.isSuccessful) {
                val token = response.body()!!.token
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

//                hubConnection.invoke(HubCommand.JOIN_GROUP, "Tek4tv", Utils.getDeviceId(appContext))
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
                    val countForHardRestart = ConfigUtil.getInt(appContext, ConfigUtil.CountForHardReset, 0)
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
            startPingDataTimer()
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

//                hubConnection.invoke(HubCommand.JOIN_GROUP, "Tek4tv", Utils.getDeviceId(appContext))

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

                    if (command != null && message != null) {
                        when (command) {
                            HubCommand.PONG -> {

                                //Vừa kết nối được lại hub
                                if (pingCounter >= 3 && !firstConnect) {
                                    Log.e(LOG_TAG, "Hub Reconnected")
                                    notifyHubReconnected()
                                    notifyDisconnected.compareAndSet(true, false)
                                }
                                
                                if (firstConnect) firstConnect = false

                                pingCounter = 0

                            }
                            HubCommand.UPDATE_CONFIG , HubCommand.UPDATE_DESTINATION -> {
                                meessDe = b64Decode(message)
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
        pingDataJob = coroutineScope.launch(Dispatchers.Default) {
            while (true) {
                sendPingData()
                delay(PING_DATA_INTERVAL)
            }
        }
    }
    private fun getMetadata() : String
    {
        //thong tin lich phat
        val listMetadata : MutableList<MetadataModel> = mutableListOf()
        val modifyTimePlaylist = ConfigUtil.getString(appContext, ConfigUtil.KEY_MODIFY_TIME_PLAYLIST,"")
        val monthlyPlaylistId = ConfigUtil.getString(appContext, ConfigUtil.MONTHLY_SCENARIO_URL,"")?.split("/")?.last()
        val playlistInfo = MetadataModel("PLAYLISTINFO","$monthlyPlaylistId,$modifyTimePlaylist")
        listMetadata.add(playlistInfo)
        //thong tin stream encoder
        if(MainActivity.isEncoder)
        {
            val streamInfo = MetadataModel("STREAMINGINFO","${urlStream},${protocol_stream},${bitrate_stream},${samplerate}")
            listMetadata.add(streamInfo)
        }
        return  listAdapter.toJson(listMetadata)
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
                metadata =  getMetadata()
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
