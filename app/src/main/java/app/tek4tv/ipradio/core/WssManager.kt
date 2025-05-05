package app.tek4tv.ipradio.core

import android.content.Context
import android.util.Log
import app.tek4tv.ipradio.core.IConnectionServer
import app.tek4tv.ipradio.utils.ConfigUtil
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.util.concurrent.TimeUnit

class WssManager(private var hubToken : String?, private val context: Context, private val connectEventListener : IConnectionServer) {
    val TAG = "WssManager"
    private var webSocket: WebSocket? = null
    private var client: OkHttpClient? = null

    inner class EchoWebSocketListener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
            Log.d("WSS2", "onOpen: connnected")
            connectEventListener.onConnectSuccess()
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            Log.d("Websocket receive", text )
            //Vừa kết nối được lại hub
            connectEventListener.onReceiveMessageSuccess(message = text)
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            Log.d("WSS2", "Receiving bytes: ${bytes.hex()}")
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(1000, null)

            Log.d("WSS2", "losing: $code / $reason")
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
            Log.d("WSS2", "onFailure: ${t.message} - ${t.cause}")
            connectEventListener.onConnectFailure(t.message)
        }
    }
    fun sendMessage(message : String)
    {
        webSocket?.send(message)
    }

    fun connectToWss()
    {
        client = OkHttpClient.Builder(
        ).connectTimeout(60, TimeUnit.SECONDS)
            .pingInterval(10, TimeUnit.SECONDS)
            .build()
        //wss://gateway-qltt.tek4tv.vn/ws/ipradio
        val url = ConfigUtil.getStringEncrypt(context, ConfigUtil.URL_WSS)
        val request =
            Request.Builder().url(url)
                .addHeader("Authorization", "Bearer $hubToken").build()
        val listener = EchoWebSocketListener()

        webSocket = client?.newWebSocket(request, listener)

    }

    fun closeWebsocket() {
        webSocket?.close(1000, "Client disconnected")
//        client?.dispatcher?.executorService?.shutdown()
        Log.d(TAG, "closeWebsocket")
    }
}