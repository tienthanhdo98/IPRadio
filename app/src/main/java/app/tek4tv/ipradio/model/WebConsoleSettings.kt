package app.tek4tv.ipradio.model

data class ServerSettings(
    val bootstrapServer: String,
    val mqttServer: String,
    val mqttPort: Int,
    val mqttSecurity: Int
)

data class ConnectionSettings(
    val thingId: String,
    val thingKey: String,
    val channels: String
)