package app.tek4tv.ipradio.core

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import app.tek4tv.ipradio.MyApp
import app.tek4tv.ipradio.di.MoshiInst
import app.tek4tv.ipradio.model.*
import app.tek4tv.ipradio.utils.*
import com.microsoft.signalr.HubConnection
import com.microsoft.signalr.HubConnectionBuilder
import kotlinx.coroutines.*
import java.io.File
import java.security.KeyFactory
import java.security.NoSuchAlgorithmException
import java.security.PrivateKey
import java.security.spec.InvalidKeySpecException
import java.security.spec.PKCS8EncodedKeySpec
import java.text.SimpleDateFormat
import java.util.*
import javax.crypto.Cipher

class HubManager(
    private val hubComm: HubComm,
    var onMessageListener: (command: String?, message: String?) -> Unit
) {
    private val LOG_TAG = "HubConnectionTask"
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

    val hubConnection: HubConnection

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


    init {
        versionName = try {
            appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName
        } catch (e: Exception) {
            ""
        }

        hubConnection = HubConnectionBuilder.create(NetworkUtils.URL_HUB).build()
    }


    fun connectToHub() {
        openNewConnectionJob?.cancel()
        openNewConnectionJob = coroutineScope.launch {
            var connectionId = openConnection()

            var i = 0

            while (connectionId == null) {
                if (i >= CONNECT_COUNTER_LIMIT) hubComm.forceRestartApp()

                i++
                delay(PING_INTERVAL)
                connectionId = openConnection()
                Log.e(LOG_TAG, "Trying to reconnect... $i")
            }
            Log.d(LOG_TAG, "$connectionId")
            onMessage()
            startPingTimer()
            notifyHubConnected()

            openNewConnectionJob = null
        }
    }

    private suspend fun openConnection(): String? {
        //open connection on another thread
        Log.e(LOG_TAG, "open connection")
        return withContext(Dispatchers.Default) {
            try {
                hubConnection.start().blockingAwait()
                hubConnection.connectionId
            } catch (e: Exception) {
                Log.e(LOG_TAG, "error connecting to hub: ${e.message ?: ""} ")
                e.printStackTrace()
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
                    Log.d("command", command ?: "")
                    Log.d("message", message ?: "")

                    var needDecrypt = false

                    if (command != null && message != null) {
                        when (command) {
                            HubCommand.PONG -> {
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
            }
            catch (e : Exception)
            {
                e.printStackTrace()
            }
        }, String::class.java, String::class.java)
    }

    fun sendMessage(mess: String, command: String) {
        try {
            hubConnection.invoke(command, Utils.getDeviceId(appContext), mess)
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

    fun sendEncryptedMessage(connectionId: String, command: String, message: String)
    {
        Log.d(LOG_TAG,"be4 encrypted: $message")
        val encryptedMessage = encryptData(message, publicKey)
        val directMessage = DirectMessage(deviceImei, encryptedMessage?:"")

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
                val path = currentMediaItem!!.path
                mode = if (path.isNotEmpty() && !File(path).exists()) "1"
                else "0"
            }

            val video = Video("" + i, mode).apply {

                if (currentMediaItem != null) {
                    mediaName = curMediaName
                    path = currentMediaItem!!.path
                    level = currentMediaItem!!.level
                }
            }

            val request = PingHubRequest().apply {
                imei = deviceImei
                status = "START"
                connectionId = (hubConnection.connectionId)
                this.video = videoAdapter.toJson(video)
                startTine = dateFormat.format(Date())
                networkInfo = NetworkUtils.getNetworkInfo(appContext)
                version = versionName
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
        //Danger here
        if (pingCounter >= CONNECT_COUNTER_LIMIT) {
            hubComm.forceRestartApp()
        }

        if (pingCounter >= 3) {
            Log.e(LOG_TAG, "Losed hub connection")
            notifyHubDisconnected()

            //Try to reconnect to hub
            val cid = openConnection()
            if (cid != null) notifyHubConnected()
        }
    }

    @Synchronized
    private fun notifyHubConnected() {
        mainHandler.post {
            hubEventObserver.forEach { it.onConnectedToHub() }
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
    fun onConnectedToHub()
}

val publicKey = "MFwwDQYJKoZIhvcNAQEBBQADSwAwSAJBAIkOIaw3KkEJHxvfYZ9FZzC4zBryierYfjXocoi2zOY/HvfXK6bfjuu7ZwiaUX5C6tOl8T0Dd0AEw32NwhAzi60CAwEAAQ=="

val privateKeyStr =
    "MIICWwIBAAKBgQCUN2A7D+wecFVoH9xQkg1o87hA/DFa8mMTfshyU4mrcvf3w0UkhmvxWJXA4hcvQWjpsXDm1ok7AhB9qeoEbxXYejyQLf9+znaBt2m4rgpSTPNb2ioOFd6d9JuiWJ1oPhPETjoyBdJ37yAZWkoX4nPXK4GcaaW5KPHcMGGRIzP/bQIDAQABAoGARlt7HM5UIablhAYLyVtrbjSzdHXC4A9LpMd8U3kAUxjCSSSyv8Hnn6uTevpYUyBTswt+MrYIcrBqL5zm76/jyCQ/i7wobqO+y089gyOVP6z6ZL2IASFoyEvMeI6Mp673p2EOWEyL/nbaiOmwhk16wsCg3hA9rABZVb3cHOCBzsECQQDKDDRCNYQjqHI9qHqDeq/7nOnQb59bJPb/8/EyxTA/2gFpjaRzyaHg/YUw0XsY9B0wzf+JJXFQ5OOPNusD/yNJAkEAu8tPCX5qSDYY+K/C+JvRob/keB7eXfQlNvDC6e2xs3rl7Qlt5f/bvhv/YdwmHEGQUDgXgsbIl7TW3WtnbebXBQJAen69rCpv64JziIg9hrr6OMzgQLt73LFjgrgt0n8UipGsa4cxXfWvzA1IE9ndDLrre6xYsZhexBGRGUHNaEXl6QJAV43rdJauwKoQLGntsfEb5JjN0T87F3zLmPjZNy1t6ysCZeg+h7wpXFI7WAnG7ueWfAAa3gsF3M0gXC1GFJRKbQJAO932dYpY+Ht5sJMHfQ7KwV8RsIN9vcWfdzoeI1a6/2LjYOoo23z98FCDKYdMV6QZr2JksPRfddMdvnquWxDMlg=="