package app.tek4tv.ipradio.core


import android.util.Log
import app.tek4tv.ipradio.model.DirectMessage
import app.tek4tv.ipradio.model.encoder_model.PingHubRequestEncoder
import app.tek4tv.ipradio.model.encoder_model.ResponseHubEncoder
import app.tek4tv.ipradio.repo.encoder_repo.DeviceRepository
import app.tek4tv.ipradio.utils.HubCommand
import app.tek4tv.ipradio.utils.Utils
import com.microsoft.signalr.HubConnection
import com.microsoft.signalr.HubConnectionBuilder
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean

class HubManagerEncoder : BaseHubManager() {
    var onMessageListener: (command: String?, message: String?) -> Unit = { _, _ -> }

    private val LOG_TAG = "HubManagerEncoder"

    private val URL_HUB = "https://devicehub-truyenthanh.tek4tv.vn/hubServer"

    private var pingHubJob: Job? = null

    private var openNewConnectionJob: Job? = null



    private val pingRequestAdapter = moshi.adapter(PingHubRequestEncoder::class.java)
    private val responseAdapter = moshi.adapter(ResponseHubEncoder::class.java)



    private val hubEventObserver = mutableListOf<HubEventObserver>()


    private val deviceRepo = DeviceRepository.getInstance()

    private val hubConnection: HubConnection

    var latestHubResponse = ResponseHubEncoder()



    //check ping to hub
    var pingToHub = false


    private var registerOnMessageListener = false


    private val notifyDisconnected = AtomicBoolean(false)

    init {
        versionName = try {
            appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName
        } catch (e: Exception) {
            ""
        }

        hubConnection = HubConnectionBuilder.create(URL_HUB).build()
    }

    fun connectToHub() {
        openNewConnectionJob?.cancel()
        openNewConnectionJob = coroutineScope.launch {
            var connectionId = openConnection()

            var i = 0

            while (connectionId == null) {
                i++
                delay(PING_INTERVAL)
                connectionId = openConnection()
                Log.e(LOG_TAG, "Trying to reconnect... $i")
            }
            Log.d(LOG_TAG, "$connectionId")
            registerHubMessageListener()
            startPing()
            notifyHubConnected()


            openNewConnectionJob = null
        }
    }

    private suspend fun openConnection(): String? {
        //open connection on another thread
        Log.e(LOG_TAG, "open connection")
        return withContext(Dispatchers.IO) {
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

    private fun registerHubMessageListener() {
        if (registerOnMessageListener) return

        registerOnMessageListener = true

        hubConnection.on("ReceiveMessage", { command: String?, message: String? ->
            try {
                mainHandler.post {
                    Log.d("command", command ?: "")
                    Log.d("message", message ?: "")

                    if (command != null && message != null) {
                        when (command) {
                            HubCommand.PONG -> {
                                //reset ping counter to specify that we are still connect to hub
                                pingCounter = 0
                            }

//                            HubCommand.UPDATE_STATUS -> {
//                                if (latestHubResponse.message != null) receivedConnectionId =
//                                    latestHubResponse.message
//                                        ?: ""
//                            }
                        }
                    }
                    parseHubResponse(message)
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

    /**
     * Serialize infos and send ping command to hub
     */
    private fun sendPing() {
        pingCounter++
        try {
            Log.d(LOG_TAG, "ping hub")

            val request = PingHubRequestEncoder().apply {
                imei = deviceImei
                status = "START"
                connectionId = (hubConnection.connectionId)
                version = versionName
                appName = "AppEncoder"
                provider = "Tek4tv"
            }
            sendMessage(pingRequestAdapter.toJson(request), Utils.ping)

        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
    }

    private fun startPing() {
        pingHubJob?.cancel()
        pingHubJob = coroutineScope.launch(Dispatchers.Default) {
            while (true) {
                sendPing()
                Log.d(LOG_TAG, "ping: $pingCounter")
                if (pingCounter == 1) {
                    pingToHub = true
                } else if (pingCounter >=5) {
                    pingToHub = false
                }
                delay(PING_INTERVAL)
                checkConnection()
            }
        }
    }

    /**
     * Check connection to hub base on pingCounter
     */
    private suspend fun checkConnection() {
        if (pingCounter >= 3) {
            Log.e(LOG_TAG, "Losed hub connection")

            if (notifyDisconnected.compareAndSet(false, true)) {
                notifyHubDisconnected()
            }

            //Try to reconnect to hub
            val cid = openConnection()
            if (cid != null) {
                notifyHubConnected()
                notifyDisconnected.compareAndSet(true, false)
            }
        }
    }

    @Synchronized
    private fun notifyHubConnected() {
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

    /**
     * Deserialize json response from hub
     * @param message : response Json string
     */
    private fun parseHubResponse(message: String?) {
        if (message.isNullOrEmpty()) return
        try {
            if (message.startsWith("{")) latestHubResponse = responseAdapter.fromJson(message)!!
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }


    companion object {
        var instance: HubManagerEncoder? = null

        fun getInst(): HubManagerEncoder {
            if (instance == null) instance = HubManagerEncoder()

            return instance!!
        }
    }
}
