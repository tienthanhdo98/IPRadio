package app.tek4tv.ipradio.model

data class IcecastConfig(
    val host: String,
    val port: Int,
    val mount: String,
    val username: String,
    val password: String,
    val tls: Boolean,
    val contentType: String = "audio/aac"
)