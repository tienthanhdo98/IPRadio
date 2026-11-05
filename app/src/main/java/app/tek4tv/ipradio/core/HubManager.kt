package app.tek4tv.ipradio.core

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import app.tek4tv.ipradio.MyApp
import app.tek4tv.ipradio.Secrets
import app.tek4tv.ipradio.core.constants.AppConstants.PROVIDER_DEFAULT_NAME
import app.tek4tv.ipradio.di.JsonAdapterProvider.adapterListString
import app.tek4tv.ipradio.di.MoshiInst
import app.tek4tv.ipradio.model.*
import app.tek4tv.ipradio.network.AuthService
import app.tek4tv.ipradio.network.IHubObserve
import app.tek4tv.ipradio.utils.*
import app.tek4tv.ipradio.utils.crypto.b64Decode
import app.tek4tv.ipradio.utils.crypto.xorString
import app.tek4tv.ipradio.utils.media.MediaCapture
import app.tek4tv.ipradio.utils.network.NetworkUtils
import app.tek4tv.ipradio.utils.network.CaCertHelper
import com.microsoft.signalr.HubConnection
import com.microsoft.signalr.HubConnectionBuilder
import com.microsoft.signalr.TransportEnum
import io.reactivex.Single
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import java.text.SimpleDateFormat
import java.util.*

/**
 * Manager hub connection: Connect to hub, ping,
 * receive command, ping
 * **/
class HubManager(
    private val appContext: Context,
    private val authService: AuthService
) {
    private val TAG = "HubManager"
    var mediaCapture: MediaCapture? = null
    lateinit var hubComm: HubComm
    lateinit var hubObverse: IHubObserve
    lateinit var logUtils: LogUtils
    private val CONNECT_COUNTER_LIMIT = 15
    private val PING_INTERVAL = 10000L
    private val moshi = MoshiInst.moshi
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss")
    private var pingHubJob: Job? = null
    private var openNewConnectionJob: Job? = null
    private var coroutineScope = CoroutineScope(Dispatchers.Main)

    private val videoAdapter = moshi.adapter(Video::class.java)

    private val pingRequestAdapter = moshi.adapter(PingHubRequest::class.java)

    val deviceImei = Utils.getDeviceId(appContext)

    private val responseAdapter = moshi.adapter(ResponseHub::class.java)

    var pingCounter = 69

    private var hubConnection: HubConnection

    //current response Hub
    var responseHub = ResponseHub()
    var receivedConnectionId = ""

    //player info
    var playlistIndex = 0
    var currentMediaItem: MediaItem? = null
    var curMediaName = ""

    private var versionName = "D62.4.2.2"

    private val mainHandler = Handler(Looper.getMainLooper())

    var isConnectSuccess = false

    private var hubToken: String? = null

    var providerValue = "TEK4TV"

    private var firstConnect = true

    var onHubConnectedListener: (() -> Unit)? = null

    fun isHubConnected(): Boolean {
        return hubConnection.connectionState == com.microsoft.signalr.HubConnectionState.CONNECTED
    }

    private var hubUrl = ""

    private var packageName = ""


        private var lastMessageKey = ""
    private var lastMessageTime = 0L
    private val COMMAND_DEDUP_INTERVAL_MS = 1500L

    private val _messageStateFlow = MutableSharedFlow<Pair<String?, String?>?>(0)
    val messageStateFlow: SharedFlow<Pair<String?, String?>?> = _messageStateFlow

    private suspend fun receiveMessage(command: String?, message: String?) {
        _messageStateFlow.emit(Pair(command, message)) // Update StateFlow
    }


    init {
        packageName = appContext.packageName
        providerValue = ConfigUtil.getString(
            appContext,
            ConfigUtil.PROVIDER, PROVIDER_DEFAULT_NAME
        ) ?: PROVIDER_DEFAULT_NAME
        hubUrl = ConfigUtil.getStringEncrypt(
            appContext,
            ConfigUtil.URL_HUB
        ).toString()

        hubConnection = HubConnectionBuilder.create(hubUrl)
            .setHttpClientBuilderCallback { builder ->
                CaCertHelper.buildConfiguredOkHttpClient(builder, appContext)
            }
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

    }

    //lấy token từ hub
    private fun getHubToken(): String? {
        return try {
            val usernameKey = ConfigUtil.getStringEncrypt(appContext, ConfigUtil.USERNAME_KEY)
            val passwordKey = ConfigUtil.getStringEncrypt(appContext, ConfigUtil.PASSWORD_KEY)
            val username = ConfigUtil.getStringEncrypt(appContext, ConfigUtil.USERNAME)
            val password = ConfigUtil.getStringEncrypt(appContext, ConfigUtil.PASSWORD)
            val body = mapOf(
                usernameKey!! to username!!,
                passwordKey!! to password!!,
            )
            val url = ConfigUtil.getStringEncrypt(
                appContext,
                ConfigUtil.URL_TOKEN,
            )

            val call = authService.getToken(body, url!!)
            val response = call.execute()

            if (response.isSuccessful) {
                val token = response.body()!!.token
                Log.i(TAG, "getHubToken: $token")
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
            //Encrypted json variable name
            val u = byteArrayOf(58, 26, 1, 19, 28, 17, 4, 75)
            val p = byteArrayOf(63, 8, 23, 18, 5, 31, 27, 74)

            //Thông tin xác thực lưu trong thư viện
            val authInfo = Secrets().getAQNIjugs(packageName).split('-')

            val body = mapOf(
                String(xorString(u, packageName.reversed())) to authInfo[0],
                String(xorString(p, packageName.reversed())) to authInfo[1]
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
        Log.i(TAG, "open connection")
        return withContext(Dispatchers.IO) {
            try {
                hubConnection.start().blockingAwait()

                hubConnection.connectionId
            } catch (e: Exception) {
                hubToken = null
                e.printStackTrace()
                Log.e(TAG, "error connecting to hub: ${e.message ?: ""}")
                null
            }
        }
    }

    //end check
    /**
     * bắt đầu kết nối dend hub
     * **/
    fun connectToHub() {
        if (isHubConnected()) {
            Log.d(TAG, "Hub is already connected")
            return
        }

        if (openNewConnectionJob?.isActive == true) {
            Log.d(TAG, "Connection attempt already in progress")
            return
        }

        openNewConnectionJob = coroutineScope.launch {
            var connectionId = openConnection()

            var i = 0

            while (connectionId == null) {
                if (i >= CONNECT_COUNTER_LIMIT) {
                    hubComm.forceRestartApp()
                }
                i++
                delay(PING_INTERVAL)
                connectionId = openConnection()
                Log.e(TAG, "Trying to reconnect... $i")
            }
            Log.d(TAG, "$connectionId")
            isConnectSuccess = true
            pingCounter = 0
            onMessage()
            startPingTimer()
            try {
                onHubConnectedListener?.invoke()
            } catch (e: Exception) {
                Log.e(TAG, "Error invoking onHubConnectedListener: ${e.message}")
            }
            openNewConnectionJob = null
        }
    }

    private suspend fun openConnection(): String? {
        //open connection on another thread
        Log.i(TAG, "open connection")
        return withContext(Dispatchers.IO) {
            try {
                hubConnection.start().blockingAwait()
                hubConnection.connectionId
            } catch (e: Exception) {
                hubToken = null
                e.printStackTrace()
                Log.e(TAG, "error connecting to hub: ${e.message ?: ""}")
                null
            }
        }
    }

    /**
     * Nhận lệnh từ hub
     * **/
    private fun onMessage() {
        try {
            hubConnection.remove("ReceiveMessage")
        } catch (e: Exception) {
            // Ignore
        }
        hubConnection.on("ReceiveMessage", { command: String?, message: String? ->
            try {
                mainHandler.post {
                    val currentCommand = command?.trim() ?: ""
                    val currentMessage = message?.trim() ?: ""
                    val currentTime = System.currentTimeMillis()
                    val messageKey = "$currentCommand:$currentMessage"

                    // Cháº·n báº£n tin trÃ¹ng láº·p dá»“n dáº­p trong 1.5s (trá»« PONG Ä‘á»ƒ duy trÃ¬ keep-alive)
                    if (currentCommand.isNotEmpty() && currentCommand != HubCommand.PONG) {
                        if (messageKey == lastMessageKey && (currentTime - lastMessageTime) < COMMAND_DEDUP_INTERVAL_MS) {
                            Log.w(TAG, "Bá» qua báº£n tin trÃ¹ng láº·p dá»“n dáº­p (< ${COMMAND_DEDUP_INTERVAL_MS}ms): $currentCommand")
                            return@post
                        }
                        lastMessageKey = messageKey
                        lastMessageTime = currentTime
                    }

                    Log.d("command", command ?: "")
                    Log.d("message", message ?: "")
                    var messDe = ""

                    if (command != null && message != null) {
                        when (command) {
                            HubCommand.PONG -> {
                                isConnectSuccess = true


                                //Vừa kết nối được lại hub
                                if (pingCounter >= 3 && !firstConnect) {
                                    Log.e(TAG, "Hub Reconnected")
                                }

                                if (firstConnect) {
                                    firstConnect = false
                                    hubObverse.onHubConnected()
                                }

                                pingCounter = 0
                            }

                            HubCommand.UPDATE_CONFIG, HubCommand.UPDATE_DESTINATION -> {
                                messDe = b64Decode(message)
                            }

                            HubCommand.UPDATE_STATUS -> {
                                if (responseHub.message != null) receivedConnectionId =
                                    responseHub.message ?: ""
                            }

                        }
                    }
                    //Handle command from server in MainActivity
                    coroutineScope.launch {
                        if (messDe != "") {
                            parseHubResponse(messDe)
                        } else {
                            parseHubResponse(message)
                        }
                        receiveMessage(command, message)
                    }

                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, String::class.java, String::class.java)
    }

    private fun sendMessage(mess: String) {
        try {
            if (hubConnection.connectionId != null) {
                hubConnection.invoke(Utils.ping, Utils.getDeviceId(appContext), mess)
                Log.i(TAG, "volume: $mess")
            }
        } catch (e: Exception) {
            Log.e(TAG, e.message!!)
            e.printStackTrace()
        }
    }

    private fun sendDirectMessage(connectionId: String, command: String, message: String) {
        try {
            if (hubConnection.connectionId != null) {
                hubConnection.invoke(Utils.DIRECT_MESSAGE, connectionId, command, message)
            }
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


    private fun sendPing() {
        pingCounter++
        try {
            Log.d(TAG, "ping hub")


            val mode = "-1"
            //Ban v1
            //
//            if (currentMediaItem != null) {
//                val path = currentMediaItem!!.pathBackup
//                mode = if (path.isNotEmpty() && !File(path).exists()) "1"
//                else "0"
//            }
//            val i = playlistIndex
//            val video = Video("" + i, mode).apply {
//                if (currentMediaItem != null) {
//                    mediaName = curMediaName
//                    path = currentMediaItem!!.pathBackup
//                    level = currentMediaItem!!.level
//                }
//            }


            //Ban v2
            //neu chay da man hinh path se gui la "path1,path2" path vùng 1,2,3
            val mediaPlay = Video("" + playlistIndex, mode).apply {
                if (currentMediaItem != null) {
                    val listPath = listOf(currentMediaItem!!.pathBackup)
                    val jsonString = adapterListString.toJson(listPath)
                    mediaName = curMediaName
                    path = if (jsonString == "") {
                        null
                    } else {
                        jsonString
                    }
                    level = currentMediaItem!!.level
                }
            }

            val currentTime = Calendar.getInstance().time

            val isRecording = if (mediaCapture?.isRecordingAudio == true) "1" else "0"

            val request = PingHubRequest().apply {
                imei = deviceImei
                status = "START"
                connectionId = (hubConnection.connectionId)
                this.video = videoAdapter.toJson(mediaPlay)
                startTime = dateFormat.format(Date())
                networkInfo = NetworkUtils.getNetworkInfo(appContext)
                version = versionName
                destinationId = GlobalProperties.getInst().destinationId
                destinationName = GlobalProperties.getInst().destinationName
                appName = "DigitalSignage"
                logType = hubUrl
                provider = providerValue
                lat = GlobalProperties.getInst().lat
                lng = GlobalProperties.getInst().lng
                volume = GlobalProperties.getInst().volume
                deviceName = GlobalProperties.getInst().nameDevice
                createDate = (currentTime.time / 1000)
                userNameControl = GlobalProperties.getInst().userNameController
                isRecord = isRecording
                deviceInfo = SerialPortController.deviceInfo
            }
            sendMessage(pingRequestAdapter.toJson(request))
            Log.i(TAG, "log hub device info: ${pingRequestAdapter.toJson(request)}")

        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
    }

    private fun startPingTimer() {
        pingHubJob?.cancel()
        pingHubJob = coroutineScope.launch(Dispatchers.Default) {
            while (true) {
                sendPing()
                Log.d(TAG, "ping: $pingCounter")
                delay(PING_INTERVAL)
                checkConnection()
            }
        }
    }

    private suspend fun checkConnection() {
//        if (pingCounter >= CONNECT_COUNTER_LIMIT) {
//            hubComm.forceRestartApp()
//        }
        if (pingCounter >= 4) {
            hubObverse.onHubDisConnected()
            isConnectSuccess = false
            firstConnect = true
            Log.e(TAG, "Lose hub connection")
            hubToken = null
            Log.e(TAG, "Hub Disconnected")
            //Try to reconnect to hub
            connectToHub()
        }
    }


    //parse hub command content
    private fun parseHubResponse(message: String?) {
        if (message.isNullOrEmpty()) return
        try {
            responseHub = ResponseHub()

            if (message.startsWith("{"))
                responseHub = responseAdapter.fromJson(message)!!

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

interface HubComm {
    fun forceRestartApp()
    fun forceRestartDevice()
}

