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
import app.tek4tv.ipradio.utils.ConfigUtil
import app.tek4tv.ipradio.utils.GlobalProperties
import app.tek4tv.ipradio.utils.HubCommand
import app.tek4tv.ipradio.utils.Utils
import app.tek4tv.ipradio.utils.crypto.xorString
import app.tek4tv.ipradio.utils.network.CaCertHelper
import app.tek4tv.ipradio.utils.network.NetworkUtils
import com.microsoft.signalr.HubConnection
import com.microsoft.signalr.HubConnectionBuilder
import com.microsoft.signalr.HubConnectionState
import com.microsoft.signalr.TransportEnum
import io.reactivex.Single
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds

/**
 * SignalRHubManager: Kết nối song song tới SignalR Hub (theo cơ chế nhánh 4.2.2b)
 * chỉ phục vụ theo dõi trạng thái thiết bị (Heartbeat/Ping) và các lệnh monitor thông tin, log.
 */
class SignalRHubManager(
    private val appContext: Context,
    private val authService: AuthService,
    var onMessageListener: (command: String?, message: String?, responseHub: ResponseHub) -> Unit,
) {
    var onHubConnectedListener: (() -> Unit)? = null
    var destinationProvider: (() -> Pair<String, String>)? = null

    private val LOG_TAG = "SignalRHubManager"

    private val PING_DATA_INTERVAL = 30000L
    private val PING_HUB_INTERVAL = 30000L
    private val moshi = MoshiInst.moshi
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.US)

    private var pingHubJob: Job? = null
    private var pingDataJob: Job? = null
    private var openNewConnectionJob: Job? = null

    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    private val videoAdapter = moshi.adapter(Video::class.java)
    private val pingRequestAdapter = moshi.adapter(PingHubRequest::class.java)
    private val heartBeatAdapter = moshi.adapter(HeartbeatRequest::class.java)
    private val responseAdapter = moshi.adapter(ResponseHub::class.java)
    private val listAdapter = moshi.adapter(List::class.java)

    val deviceImei = Utils.getDeviceId(appContext)

    var pingCounter = 0

    @Volatile
    var isTokenReachable = false

    private var hubConnection: HubConnection

    var responseHub = ResponseHub()
    var receivedConnectionId = ""

    // player info
    var playlistIndex = 0
    var currentMediaItem: MediaItem? = null
    var curMediaName = ""
    var versionName = "M37.4.2c"

    private var registerOnMessageListener = false
    private val mainHandler = Handler(Looper.getMainLooper())

    private var hubToken: String? = null
    private var firstConnect = true

    private var serverTimeout = 0
    private var keepAliveInterval = 0

    var hubUrl = ""
        private set
    private var tokenUrl = ""
    var providerValue = ""
    var packageName = ""

    private var lastMessageKey: String = ""
    private var lastMessageTime: Long = 0L

    init {
        packageName = appContext.packageName
        providerValue = ConfigUtil.getString(
            appContext, ConfigUtil.PROVIDER, GlobalProperties.getInst().providerValue
        ) ?: GlobalProperties.getInst().providerValue

        hubUrl = ConfigUtil.getString(
            appContext,
            ConfigUtil.URL_SIGNALR_HUB,
            DEFAULT_SIGNALR_HUB_URL
        )?.takeIf { it.isNotBlank() } ?: DEFAULT_SIGNALR_HUB_URL

        tokenUrl = ConfigUtil.getString(
            appContext,
            ConfigUtil.URL_SIGNALR_TOKEN,
            DEFAULT_SIGNALR_TOKEN_URL
        )?.takeIf { it.isNotBlank() } ?: DEFAULT_SIGNALR_TOKEN_URL

        serverTimeout = ConfigUtil.getInt(appContext, ConfigUtil.ServerTimeout, 60000)
        keepAliveInterval = ConfigUtil.getInt(appContext, ConfigUtil.KeepAliveInterval, 30000)

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
                    } else {
                        Single.error(Exception("Cannot retrieve SignalR token"))
                    }
                }
            })
            .withTransport(TransportEnum.WEBSOCKETS)
            .build()

        hubConnection.serverTimeout = serverTimeout.toLong()
        hubConnection.keepAliveInterval = keepAliveInterval.toLong()
    }

    private fun getHubToken(): String? {
        return getHubToken(tokenUrl)
    }

    fun getHubToken(urlToken: String): String? {
        return try {
            val u = byteArrayOf(58, 26, 1, 19, 28, 17, 4, 75)
            val p = byteArrayOf(63, 8, 23, 18, 5, 31, 27, 74)

            val authInfo = Secrets().getAQNIjugs(packageName).split('-')
            if (authInfo.size < 2) return null

            val body = mapOf(
                String(xorString(u, packageName.reversed())) to authInfo[0],
                String(xorString(p, packageName.reversed())) to authInfo[1]
            )

            val call = authService.getSignalRToken(body, urlToken)
            val response = call.execute()
            if (response.isSuccessful) {
                isTokenReachable = true
                response.body()?.token
            } else {
                isTokenReachable = true
                Log.w(LOG_TAG, "getHubToken HTTP ${response.code()} from $urlToken")
                null
            }
        } catch (e: Exception) {
            isTokenReachable = false
            Log.e(LOG_TAG, "getHubToken error: ${e.message}", e)
            null
        }
    }


    fun isHubConnected(): Boolean {
        return hubConnection.connectionState == HubConnectionState.CONNECTED
    }

    fun connectToHub() {
        if (isHubConnected()) return
        if (openNewConnectionJob?.isActive == true) return

        openNewConnectionJob?.cancel()
        openNewConnectionJob = coroutineScope.launch {
            var connectionId = openConnection()
            var i = 0

            while (connectionId == null && isActive) {
                i++
                delay(PING_DATA_INTERVAL.milliseconds)
                connectionId = openConnection()
                Log.e(LOG_TAG, "Trying to reconnect SignalR... $i")
                pingCounter++
            }

            if (connectionId != null) {
                Log.d(LOG_TAG, "SignalR connected: $connectionId")
                pingCounter = 0
                onMessage()
                startPingTimer()
                startPingDataTimer()
                try {
                    onHubConnectedListener?.invoke()
                } catch (e: Exception) {
                    Log.e(LOG_TAG, "Error invoking onHubConnectedListener: ${e.message}")
                }
            }
            openNewConnectionJob = null
        }
    }

    private suspend fun openConnection(): String? {
        Log.i(LOG_TAG, "open connection to $hubUrl")
        return withContext(Dispatchers.IO) {
            try {
                if (hubConnection.connectionState != HubConnectionState.DISCONNECTED) {
                    try {
                        hubConnection.stop().blockingAwait()
                    } catch (_: Exception) {
                    }
                }
                hubConnection.start().blockingAwait()
                hubConnection.connectionId
            } catch (e: Exception) {
                hubToken = null
                Log.e(LOG_TAG, "openConnection failed: ${e.message}")
                null
            }
        }
    }

    private fun onMessage() {
        if (registerOnMessageListener) return
        registerOnMessageListener = true

        hubConnection.on("ReceiveMessage", { command: String?, message: String? ->
            try {
                mainHandler.post {
                    Log.d(LOG_TAG, "command receive: ${command ?: ""}")
                    Log.d(LOG_TAG, "message receive: ${message ?: ""}")

                    val currentCommand = command?.trim() ?: ""
                    val currentTime = System.currentTimeMillis()
                    if (currentCommand == lastMessageKey && (currentTime - lastMessageTime) < 1500) {
                        Log.d(LOG_TAG, "Bỏ qua bản tin trùng lặp: $currentCommand")
                        return@post
                    }
                    lastMessageKey = currentCommand
                    lastMessageTime = currentTime

                    if (command != null && message != null) {
                        when (command) {
                            HubCommand.PONG -> {
                                if (firstConnect) firstConnect = false
                                pingCounter = 0
                            }
                        }
                    }


                    parseHubResponse(message)

                    if (command == HubCommand.UPDATE_STATUS && responseHub.message != null) {
                        receivedConnectionId = responseHub.message ?: ""
                    }

                    onMessageListener.invoke(command, message, responseHub)
                }
            } catch (e: Exception) {
                Log.e(LOG_TAG, "onMessage error: ${e.message}", e)
            }
        }, String::class.java, String::class.java)
    }

    private fun sendMessage(mess: String, command: String) {
        try {
            if (!isHubConnected()) return
            hubConnection.invoke(command, deviceImei, mess)
        } catch (e: Exception) {
            Log.e(LOG_TAG, "sendMessage($command) error: ${e.message}")
        }
    }

    private fun sendDirectMessage(connectionId: String, command: String, message: String) {
        try {
            if (!isHubConnected()) return
            hubConnection.invoke(Utils.DIRECT_MESSAGE, connectionId, command, message)
        } catch (e: Exception) {
            Log.e(LOG_TAG, "sendDirectMessage($command) error: ${e.message}")
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


    private fun startPingTimer() {
        pingHubJob?.cancel()
        pingHubJob = coroutineScope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    sendPing()
                    Log.d(LOG_TAG, "ping: $pingCounter")
                    delay(PING_HUB_INTERVAL.milliseconds)
                    checkConnection()
                } catch (_: CancellationException) {
                    break
                } catch (e: Exception) {
                    Log.e(LOG_TAG, "startPingTimer error: ${e.message}")
                }
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
        } catch (e: Exception) {
            Log.e(LOG_TAG, "sendPing error: ${e.message}")
        }
    }

    private fun startPingDataTimer() {
        pingDataJob?.cancel()
        pingDataJob = coroutineScope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    sendPingData()
                    delay(PING_DATA_INTERVAL.milliseconds)
                } catch (_: CancellationException) {
                    break
                } catch (e: Exception) {
                    Log.e(LOG_TAG, "startPingDataTimer error: ${e.message}")
                }
            }
        }
    }

    private fun getMetadata(): String {
        val listMetadata: MutableList<MetadataModel> = mutableListOf()
        val modifyTimePlaylist =
            ConfigUtil.getString(appContext, ConfigUtil.KEY_MODIFY_TIME_PLAYLIST, "")
                ?.takeIf { it.isNotEmpty() }
                ?: ConfigUtil.getString(appContext, ConfigUtil.TIME_UPDATE_MONTHLY_SCENARIO, "")
                ?: ""
        val monthlyPlaylistId =
            ConfigUtil.getString(appContext, ConfigUtil.MONTHLY_SCENARIO_URL, "")
                ?.takeIf { it.isNotEmpty() }
                ?.split("/")?.last()
                ?: if (!ConfigUtil.getString(appContext, ConfigUtil.MONTHLY_SCENARIO_WSS_DATA, "")
                        .isNullOrEmpty()
                ) {
                    "MQTT_WSS"
                } else {
                    ""
                }
        listMetadata.add(MetadataModel("PLAYLISTINFO", "$monthlyPlaylistId,$modifyTimePlaylist"))
        return listAdapter.toJson(listMetadata)
    }

    private fun sendPingData() {
        try {
            if (!isHubConnected()) return
            val i = playlistIndex
            var mode = "-1"
            val item = currentMediaItem
            if (item != null) {
                val path = item.pathBackup
                mode = if (path.isNotEmpty() && !File(path).exists()) "1" else "0"
            }
            val video = Video("$i", mode).apply {
                if (item != null) {
                    mediaName = curMediaName.ifEmpty { item.title ?: "" }
                    path = item.pathBackup
                    level = item.level
                }
            }

            val globalProps = GlobalProperties.getInst()
            val fallbackDest = destinationProvider?.invoke()
            val destId = globalProps.destinationId.ifEmpty { fallbackDest?.first ?: "" }
            val destName = globalProps.destinationName.ifEmpty { fallbackDest?.second ?: "" }

            val currentTime = Calendar.getInstance().time
            val request = PingHubRequest().apply {
                imei = deviceImei
                status = "START"
                connectionId = hubConnection.connectionId
                this.video = videoAdapter.toJson(video)
                startTime = dateFormat.format(Date())
                networkInfo = NetworkUtils.getNetworkInfo(appContext)
                version = versionName
                destinationId = destId
                destinationName = destName
                appName = "Ipradio"
                logType = hubUrl
                provider = providerValue
                lat = globalProps.lat
                lng = globalProps.lng
                volume = SerialPortController.volume
                deviceName = globalProps.nameDevice
                createDate = (currentTime.time / 1000)
                userNameControl = globalProps.userNameController
                isRecord = "0"
                deviceInfo = SerialPortController.deviceInfo
                metadata = getMetadata()
            }
            val payloadJson = pingRequestAdapter.toJson(request)
            sendMessage(payloadJson, Utils.ping)
            Log.i(LOG_TAG, "SignalR ping data: $payloadJson")
        } catch (e: Exception) {
            Log.e(LOG_TAG, "sendPingData error: ${e.message}")
        }
    }

    private suspend fun checkConnection() {
        if (pingCounter >= 3 || hubConnection.connectionState == HubConnectionState.DISCONNECTED) {
            Log.e(LOG_TAG, "Lost SignalR hub connection (pingCounter=$pingCounter)")
            hubToken = null
            pingCounter = 0
            openConnection()
        }
    }

    private fun parseHubResponse(message: String?) {
        if (message.isNullOrEmpty()) return
        try {
            responseHub = ResponseHub()
            if (message.startsWith("{")) {
                responseHub = responseAdapter.fromJson(message) ?: ResponseHub()
            }
        } catch (e: Exception) {
            Log.d(LOG_TAG, "JsonEncodingException: ${e.message}")
        }
    }

    fun disconnect() {
        openNewConnectionJob?.cancel()
        pingHubJob?.cancel()
        pingDataJob?.cancel()
        try {
            if (hubConnection.connectionState != HubConnectionState.DISCONNECTED) {
                hubConnection.stop()
            }
        } catch (e: Exception) {
            Log.e(LOG_TAG, "disconnect error: ${e.message}")
        }
    }

    companion object {
        const val DEFAULT_SIGNALR_HUB_URL = "https://hubip-thongtinnguon.tek4tv.vn/hubServer"
        const val DEFAULT_SIGNALR_TOKEN_URL = "https://apigatewayhttt.tek4tv.vn/api/Auth/Login"
    }
}
