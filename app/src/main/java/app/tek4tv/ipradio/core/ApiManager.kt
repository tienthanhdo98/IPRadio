package app.tek4tv.ipradio.core

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import app.tek4tv.ipradio.di.MoshiInst.moshi
import app.tek4tv.ipradio.model.ResponseHub
import app.tek4tv.ipradio.utils.HubCommand
import app.tek4tv.ipradio.utils.Utils
import app.tek4tv.ipradio.utils.crypto.b64Decode
import com.microsoft.signalr.HubConnection
import com.microsoft.signalr.HubConnectionBuilder
import com.microsoft.signalr.TransportEnum
import io.reactivex.Single
import kotlinx.coroutines.*

class ApiManager(
    private val context: Context,
    private var hubToken: String?,
    private val connectEventListener: IConnectionServer,
) {
    val TAG = "APILOG"
    private var hubUrl = "https://hubdi-ninhbinh.tek4tv.vn/hubServer"
    lateinit var hubConnection: HubConnection
    private var coroutineScope = CoroutineScope(Dispatchers.Main)
    private val mainHandler = Handler(Looper.getMainLooper())
    val responseAdapter = moshi.adapter(ResponseHub::class.java)
    var responseHub = ResponseHub()
    var receivedConnectionId = ""
    private var openNewConnectionJob: Job? = null

    fun init() {
//        hubUrl = ConfigUtil.getStringEncrypt(
//            context, ConfigUtil.URL_HUB
//        )
        hubConnection = HubConnectionBuilder.create(hubUrl).withAccessTokenProvider(Single.defer {
            if (hubToken != null) {
                Single.just(hubToken)
            } else {
                Single.error(Exception())
            }
        }).withTransport(TransportEnum.WEBSOCKETS).build()

    }

    suspend fun openConnection(): String? {
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

    fun connect() {
        init()
        openNewConnectionJob?.cancel()
        openNewConnectionJob = coroutineScope.launch {
            val connectionId = openConnection()
            if (connectionId != null) {
                connectEventListener.onConnectSuccess()
                onHandleReceiveMessage()
            } else {
                connectEventListener.onConnectFailure("")
            }
        }

    }

    private fun onHandleReceiveMessage() {
        hubConnection.on("ReceiveMessage", { command: String?, message: String? ->
            try {
                mainHandler.post {
                    Log.d("command", command ?: "")
                    Log.d("message", message ?: "")
                    var messDe = ""

                    if (command != null && message != null) {
                        when (command) {
                            HubCommand.PONG -> {

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
//                    if (command == HubCommand.PONG) return@post
                    if (message == null) return@post
                    receiveMessage(command, message)


                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, String::class.java, String::class.java)
    }

    private fun receiveMessage(command: String?, message: String) {
        connectEventListener.onReceiveMessageSuccess(
            command = command, message = message
        )
    }


    private fun parseHubResponse(message: String?, needDecrypt: Boolean) {
        if (message.isNullOrEmpty()) return
        try {
            responseHub = ResponseHub()

//            if (message.startsWith("{"))
            responseHub = responseAdapter.fromJson(message)!!

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }


    fun sendMessage(mess: String, command: String) {
        try {
            if (hubConnection.connectionId != null) {
                hubConnection.invoke(command, Utils.getDeviceId(context), mess)
                Log.i("datadevice", "volume: $mess")
            }
        } catch (e: Exception) {
            Log.e(TAG, e.message!!)
            e.printStackTrace()
        }
    }


}