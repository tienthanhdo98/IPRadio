package app.tek4tv.ipradio.core

import android.app.Activity
import android.util.Log
import app.tek4tv.ipradio.di.MoshiInst
import app.tek4tv.ipradio.model.*
import app.tek4tv.ipradio.ui.MainActivity
import app.tek4tv.ipradio.utils.NetworkUtils
import app.tek4tv.ipradio.utils.HubCommand
import app.tek4tv.ipradio.utils.Utils
import app.tek4tv.ipradio.utils.setSystemTime
import com.microsoft.signalr.HubConnection
import com.microsoft.signalr.HubConnectionBuilder
import com.squareup.moshi.Moshi
import kotlinx.coroutines.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

class HubManager(
    private var lifecycleScope: CoroutineScope,
    private var mainActivity: MainActivity,
    var onMessageListener: (command: String?, message: String?) -> Unit
) {
    private val LOG_TAG = "HubConnectionTask"
    private val moshi = MoshiInst.moshi
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss")
    private var pingHubJob: Job? = null
    private var openNewConnectionJob: Job? = null
    private var reconnectingJob: Job? = null
    private val videoAdapter = moshi.adapter(Video::class.java)
    private val pingRequestAdapter = moshi.adapter(PingHubRequest::class.java)
    private val deviceImei = Utils.getDeviceId(mainActivity.applicationContext)
    private val responseAdapter = moshi.adapter(ResponseHub::class.java)

    var pingCounter = 69

    //var lastPing = ""
    var hubConnection: HubConnection? = null

    //current response Hub
    var responseHub = ResponseHub()
    var receivedConnectionId = ""

    //player info
    var playlistIndex = 0
    var currentMediaItem: MediaItem? = null
    var curMediaName = ""

    var versionName = ""

    init {
        versionName = try {
            mainActivity.packageManager.getPackageInfo(mainActivity.packageName, 0).versionName
        } catch (e: Exception) {
            ""
        }
    }


    //this var used to check whether a connection to hub has ever been made
    var firstTimeConnected = false

    fun createNewHubConnection(forceReconnect: Boolean = false) {
        if (!forceReconnect && openNewConnectionJob != null && openNewConnectionJob!!.isActive) return

        try {
            hubConnection?.close()
        } catch (e: Exception) {
            //e.printStackTrace()
        }

        hubConnection = HubConnectionBuilder.create(NetworkUtils.URL_HUB).build()

        connectToHub()
        onMessage()
    }

    private fun connectToHub() {
        openNewConnectionJob?.cancel()
        openNewConnectionJob = lifecycleScope.launch {
            val connectionId = openConnection()

            if (connectionId != null) {
                Log.d("Connected", connectionId)
                firstTimeConnected = true
                startPingTimer()

                if (reconnectingJob != null) {
                    reconnectingJob?.cancel()
                    reconnectingJob = null
                }
            } else {
                /*
                 Avoid when we open the app for first time, have network but cannot connect to hub,
                 the ping hub timer hasn't started yet(cannot check for hub connection every 15s)
                */

                if (!firstTimeConnected && reconnectingJob == null)
                    tryReconnect()
            }
            openNewConnectionJob = null
        }
    }

    private fun tryReconnect() {
        reconnectingJob?.cancel()
        reconnectingJob = lifecycleScope.launch(Dispatchers.Default) {
            while (true) {
                delay(10000)
                createNewHubConnection(true)
            }
        }
    }

    private suspend fun openConnection(): String? {
        //open connection on another thread
        return withContext(Dispatchers.Default) {
            try {
                hubConnection!!.start().blockingAwait()
                hubConnection!!.connectionId
            } catch (e: Exception) {
                Log.e(LOG_TAG, "error connecting to hub: ${e.message ?: ""} ")
                //e.printStackTrace()
                null
            }
        }
    }

    private fun onMessage() {
        hubConnection?.on("ReceiveMessage", { command: String?, message: String? ->
            parseHubResponse(message)
            mainActivity.runOnUiThread {
                Log.d("command", command ?: "")
                Log.d("message", message ?: "")

                if (command != null && message != null) {
                    when (command) {
                        HubCommand.PONG -> {
                            pingCounter = 0
                            //syncTimeWithServer(message)
                        }

                        HubCommand.UPDATE_STATUS -> {
                            if (responseHub.message != null) receivedConnectionId =
                                responseHub.message ?: ""
                        }
                    }
                }

                //Handle command from server in MainActivity
                onMessageListener.invoke(command, message)
            }
        }, String::class.java, String::class.java)
    }

    fun sendMessage(mess: String, command: String) {
        try {
            hubConnection!!.invoke(
                command, Utils.getDeviceId(mainActivity.applicationContext),
                mess
            )
        } catch (e: Exception) {
            Log.e(LOG_TAG, e.message)
            e.printStackTrace()
        }
    }

    private fun sendDirectMessage(connectionId: String, command: String, message: String) {
        try {
            hubConnection!!.invoke(Utils.DIRECT_MESSAGE, connectionId, command, message)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun sendHubDirectMessage(connectionId: String, command: String, message: String) {
        val directMessage = DirectMessage(Utils.getDeviceId(mainActivity), message)

        sendDirectMessage(
            connectionId, command,
            Utils.toJsonString(moshi, DirectMessage::class.java, directMessage)
        )
    }

    private fun sendPing() {
        pingCounter++
        try {
            if (!mainActivity.isFinishing && hubConnection != null) {

                Log.d(LOG_TAG, "ping hub")
                val i = playlistIndex
                Log.d("player:", i.toString())

                var mode = "-1"

                val curMediaItem = currentMediaItem

                if (curMediaItem != null) {
                    val path = curMediaItem.path
                    mode = if (path.isNotEmpty() && !File(path).exists()) "1"
                    else "0"
                }

                val video = Video("" + i, mode).apply {
                    mediaName = curMediaName
                }

                val request = PingHubRequest().apply {
                    imei = deviceImei
                    status = "START"
                    connectionId = (hubConnection!!.connectionId)
                    this.video = videoAdapter.toJson(video)
                    startTine = dateFormat.format(Date())
                    networkInfo = NetworkUtils.getNetworkInfo(mainActivity.applicationContext)
                    version = versionName
                }

                sendMessage(pingRequestAdapter.toJson(request), Utils.ping)
            }
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
    }

    private fun startPingTimer() {
        pingHubJob?.cancel()
        pingHubJob = lifecycleScope.launch(Dispatchers.Default) {
            while (true) {
                sendPing()
                Log.d(LOG_TAG, "ping: $pingCounter")
                delay(15000)
                checkConnection()
            }
        }
    }

    private fun checkConnection() {
        if (pingCounter >= 3) {
            Log.e(LOG_TAG, "Losed hub connection")

            try {
                if (hubConnection != null)
                    hubConnection?.stop()
            } catch (e: Exception) {
                Log.e(LOG_TAG, "stop hub connection")
            }

            createNewHubConnection()
        }
    }

    private fun parseHubResponse(message: String?) {
        if (message.isNullOrEmpty()) return
        try {
            responseHub = ResponseHub()
            if (message.startsWith("{")) {
                responseHub = responseAdapter.fromJson(message)!!
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun syncTimeWithServer(timeFromServer: String) {
        try {
            val time = dateFormat.parse(timeFromServer)
            val now = Date()

            if (abs(time.time - now.time) >= 3000) {
                setSystemTime(timeFromServer)
                Log.d("timesync", "sync with server time: $timeFromServer")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}