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
import app.tek4tv.ipradio.network.LogService
import app.tek4tv.ipradio.utils.*
import app.tek4tv.ipradio.utils.crypto.decryptData
import app.tek4tv.ipradio.utils.crypto.encryptData
import app.tek4tv.ipradio.utils.crypto.xorString
import app.tek4tv.ipradio.utils.network.NetworkUtils
import com.microsoft.signalr.*
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
    private val CONNECT_COUNTER_LIMIT = 100
    private val PING_INTERVAL = 15000L
    private val moshi = MoshiInst.moshi
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss")
    private var pingHubJob: Job? = null
    private var openNewConnectionJob: Job? = null

    private var coroutineScope = CoroutineScope(Dispatchers.Main)
    val appContext: Context = MyApp.instance!!.applicationContext

    private val videoAdapter = moshi.adapter(Video::class.java)
    private val pingRequestAdapter = moshi.adapter(PingHubRequest::class.java)
    private val deviceImei = Utils.getDeviceId(appContext)
    private val responseAdapter = moshi.adapter(ResponseHub::class.java)

    val hubEventObserver = mutableListOf<HubEventObserver>()

    var pingCounter = 69

    var hubConnection: HubConnection

    //current response Hub
    var responseHub = ResponseHub()
    var receivedConnectionId = ""

    //player info
    var playlistIndex = 0
    var currentMediaItem: MediaItem? = null
    var curMediaName = ""

    var versionName = ""

    var registerOnMessageListener = false

    private val mainHandler = Handler(Looper.getMainLooper())

    private val notifyDisconnected = AtomicBoolean(false)

    private val logUtils = LogUtils.getInst()

    private var hubToken: String? = null

    //"https://hub.gateway.httt.tek4tv.vn/hubServer"

    private val provider = "Tek4tv"

    private var firstConnect = true

    private val privateKeyStr: String
    var logService: LogService? = null

    init {
        versionName = try {
            appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName
        } catch (e: Exception) {
            ""
        }

        //Encrypted hub url: xor with key is reversed package name

        val hub = byteArrayOf(
            7, 29, 16, 17, 1, 74, 70, 1, 30, 1, 86, 69,
            2, 21, 90, 21, 7, 0, 22, 71, 12, 21, 6, 4, 71, 90, 19, 31, 0,
            31, 19, 90, 88, 30, 95, 9, 26, 11, 55, 4, 0, 6, 12, 92
        )

        //val hubUrl = String(xorString(hub, MyApp.instance!!.packageName.reversed()))
        //val hubUrl = "https://hub.gateway.httt.vtcplay.vn/hubServer"
        //val hubUrl = "https://hub-truyenthanh.tek4tv.vn/hubServer"
        val hubUrl = Secrets().gethubUrl(MyApp.instance!!.packageName)
        //vn.ip
        //val hubUrl = Secrets().gethubUrlvn(MyApp.instance!!.packageName)
        Log.e("urlbase", hubUrl)
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
            // val body = mapOf("Username" to "device", "Password" to "Device@123")

            //Auth Url encrypted in c++ lib
            //val url = "https://apigateway-truyenthanh.tek4tv.vn/api/Auth/Login"
            val url = Secrets().gettokenUrl(MyApp.instance!!.packageName)
            Log.e("urlbase", url)
            val call = authService.getToken(body, url)
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
                //Quá 100 lần không kết nối được lên hub thì hởi động lại app
                if (i >= CONNECT_COUNTER_LIMIT) hubComm.forceRestartApp()

                i++
                delay(PING_INTERVAL)
                connectionId = openConnection()
                Log.e(LOG_TAG, "Trying to reconnect... $i")
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
        Log.e(LOG_TAG, "open connection")
        return withContext(Dispatchers.IO) {
            try {
                hubConnection.start().blockingAwait()

                hubConnection.invoke(HubCommand.JOIN_GROUP, "Tek4tv", Utils.getDeviceId(appContext))
                val currentTime = Calendar.getInstance().time
                logUtils.logHubConnectState(
                    HubStateRecord(
                        imei = deviceImei,
                        provider = provider,
                        state = "CONNECTED",
                        networkType = NetworkUtils.getNetworkClass(appContext),
                        destinationID = logUtils.destinationId,
                        destinationName = logUtils.destinationName,
                        start = currentTime.time.toString()
                    )
                )
                Log.e(LOG_TAG, "Log connect to hub success ${currentTime.time}")
                if (ConfigUtil.checkSharef(
                        appContext,
                        ConfigUtil.DISCONNECT_START_TIME
                    ) && ConfigUtil.checkSharef(appContext, ConfigUtil.DISCONNECT_STATE)
                ) {
                    val keyStartDis = ConfigUtil.DISCONNECT_START_TIME
                    val keyState = ConfigUtil.DISCONNECT_STATE
                    val disconnectTime =
                        ConfigUtil.getString(appContext, keyStartDis, "0")
                            ?: "0"
                    val disconnectState =
                        ConfigUtil.getString(appContext, keyState, "0")
                            ?: "DISCONNECT"
                    logUtils.logHubConnectState(
                        HubStateRecord(
                            imei = deviceImei,
                            provider = provider,
                            state = disconnectState,
                            networkType = NetworkUtils.getNetworkClass(appContext),
                            destinationID = logUtils.destinationId,
                            destinationName = logUtils.destinationName,
                            start = disconnectTime
                        )
                    )
                    Log.e(LOG_TAG, "Log disconnect to hub success $disconnectTime")
                    ConfigUtil.removeSharef(appContext, keyStartDis)
                    ConfigUtil.removeSharef(appContext, keyState)

                }
                hubConnection.connectionId
            } catch (e: Exception) {
                hubToken = null
                e.printStackTrace()
                Log.e(LOG_TAG, "error connecting to hub: ${e.message ?: ""}")
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
                    Log.d("command", command ?: "")
                    Log.d("message", message ?: "")

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

                            HubCommand.UPDATE_STATUS -> {
                                if (responseHub.message != null) receivedConnectionId =
                                    responseHub.message ?: ""
                            }

                            HubCommand.SET_ENCRYPTED_VOLUME_DEVICE -> {
                                needDecrypt = true
                            }
                        }
                    }
                    parseHubResponse(message, needDecrypt)
                    //Handle command from server in MainActivity
                    onMessageListener.invoke(command, message)
                }
            } catch (e: Exception) {
                if (e != null)
                    e.printStackTrace()
            }
        }, String::class.java, String::class.java)
    }

    private fun sendMessage(mess: String, command: String) {
        try {
            hubConnection.invoke(command, Utils.getDeviceId(appContext), mess)
            Log.i("datadevice", "volume: $mess")
        } catch (e: Exception) {
            Log.e(LOG_TAG, e.message)
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

    private fun sendPing() {
        pingCounter++
        try {
            Log.d(LOG_TAG, "ping hub")
            val i = playlistIndex
            Log.d("player:", i.toString())

            var mode = "-1"

            if (currentMediaItem != null) {
                val path = currentMediaItem!!.body
                mode = if (path!!.isNotEmpty() && !File(path).exists()) "1"
                else "0"
            }

            val video = Video("" + i, mode).apply {

                if (currentMediaItem != null) {
                    mediaName = curMediaName
                    path = currentMediaItem!!.body!!
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
                logType = "report"
                provider = "Tek4tv"
                lat = GlobalProperties.getInst().lat
                lng = GlobalProperties.getInst().lng
                volume = SerialPortController.volume
                deviceName = GlobalProperties.getInst().nameDevice
                createDate = currentTime.time
                networkInfo = NetworkUtils.getNetworkClass(appContext)
            }
            sendMessage(pingRequestAdapter.toJson(request), Utils.ping)

        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
    }

    private fun startPingTimer() {
        pingHubJob?.cancel()
        pingHubJob = coroutineScope.launch(Dispatchers.Default) {
            while (true) {
                sendPing()
                Log.d(LOG_TAG, "ping: $pingCounter")
                delay(PING_INTERVAL)
                checkConnection()
            }
        }
    }

    private suspend fun checkConnection() {
        if (pingCounter >= CONNECT_COUNTER_LIMIT) {
            hubComm.forceRestartApp()
        }

        if (pingCounter >= 3) {
            Log.e(LOG_TAG, "Losed hub connection")
            if (notifyDisconnected.compareAndSet(false, true)) {
                hubToken = null
                notifyHubDisconnected()
                Log.e(LOG_TAG, "Hub Disconnected")
                val currentTime = Calendar.getInstance().time
                ConfigUtil.putString(
                    appContext,
                    ConfigUtil.DISCONNECT_START_TIME,
                    currentTime.time.toString()
                )
                ConfigUtil.putString(appContext, ConfigUtil.DISCONNECT_STATE, "DISCONNECTED")
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
    fun forceRestartApp()
    fun forceRestartDevice()
}

interface HubEventObserver {
    fun onLostHubConnection()
    fun onReconnectedToHub()
}

val publicKey =
    "MFwwDQYJKoZIhvcNAQEBBQADSwAwSAJBAIkOIaw3KkEJHxvfYZ9FZzC4zBryierYfjXocoi2zOY/HvfXK6bfjuu7ZwiaUX5C6tOl8T0Dd0AEw32NwhAzi60CAwEAAQ=="

/*
val privateKeyStr =
    "MIICWwIBAAKBgQCUN2A7D+wecFVoH9xQkg1o87hA/DFa8mMTfshyU4mrcvf3w0UkhmvxWJXA4hcvQWjpsXDm1ok7AhB9qeoEbxXYejyQLf9+znaBt2m4rgpSTPNb2ioOFd6d9JuiWJ1oPhPETjoyBdJ37yAZWkoX4nPXK4GcaaW5KPHcMGGRIzP/bQIDAQABAoGARlt7HM5UIablhAYLyVtrbjSzdHXC4A9LpMd8U3kAUxjCSSSyv8Hnn6uTevpYUyBTswt+MrYIcrBqL5zm76/jyCQ/i7wobqO+y089gyOVP6z6ZL2IASFoyEvMeI6Mp673p2EOWEyL/nbaiOmwhk16wsCg3hA9rABZVb3cHOCBzsECQQDKDDRCNYQjqHI9qHqDeq/7nOnQb59bJPb/8/EyxTA/2gFpjaRzyaHg/YUw0XsY9B0wzf+JJXFQ5OOPNusD/yNJAkEAu8tPCX5qSDYY+K/C+JvRob/keB7eXfQlNvDC6e2xs3rl7Qlt5f/bvhv/YdwmHEGQUDgXgsbIl7TW3WtnbebXBQJAen69rCpv64JziIg9hrr6OMzgQLt73LFjgrgt0n8UipGsa4cxXfWvzA1IE9ndDLrre6xYsZhexBGRGUHNaEXl6QJAV43rdJauwKoQLGntsfEb5JjN0T87F3zLmPjZNy1t6ysCZeg+h7wpXFI7WAnG7ueWfAAa3gsF3M0gXC1GFJRKbQJAO932dYpY+Ht5sJMHfQ7KwV8RsIN9vcWfdzoeI1a6/2LjYOoo23z98FCDKYdMV6QZr2JksPRfddMdvnquWxDMlg=="*/
