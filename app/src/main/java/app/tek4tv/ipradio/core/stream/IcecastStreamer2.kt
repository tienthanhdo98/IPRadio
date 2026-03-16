package app.tek4tv.ipradio.core.stream

import android.util.Base64
import android.util.Log
import java.io.BufferedOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class IcecastConfig(
    val host: String,
    val port: Int,
    val mount: String,
    val username: String,
    val password: String,
    val tls: Boolean,
    val contentType: String = "audio/aac"
)


class IcecastPutClient(
    private val config: IcecastConfig
) {
    private var connection: HttpURLConnection? = null
    private var output: BufferedOutputStream? = null

    fun connect() {
        val scheme = if (config.tls) "https" else "http"
        val url = URL("$scheme://${config.host}:${config.port}${config.mount}")

        Log.d("Icecast", "Connecting to $url")

        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "PUT"
            doOutput = true
            doInput = true
            useCaches = false
            connectTimeout = 5000
            readTimeout = 5000

            setRequestProperty("Content-Type", config.contentType)
            setRequestProperty("Ice-Name", "Android Mic AAC")
            setRequestProperty("Ice-Description", "Live microphone from Android")
            setRequestProperty("Ice-Public", "0")

            val basic = Base64.encodeToString(
                "${config.username}:${config.password}".toByteArray(Charsets.UTF_8),
                Base64.NO_WRAP
            )
            setRequestProperty("Authorization", "Basic $basic")

            setChunkedStreamingMode(16 * 1024)
        }

        conn.connect()

        // outputStream sẽ thực sự mở request body
        val out = BufferedOutputStream(conn.outputStream, 64 * 1024)

        connection = conn
        output = out

        Log.d("Icecast", "HTTP connected")
    }

    fun write(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size) {
        val out = output ?: error("Icecast chưa connect")
        out.write(bytes, offset, length)
        out.flush()
    }

    fun close() {
        try {
            output?.flush()
        } catch (_: Exception) {
        }
        try {
            output?.close()
        } catch (_: Exception) {
        }
        try {
            connection?.disconnect()
        } catch (_: Exception) {
        }
        output = null
        connection = null
    }
}