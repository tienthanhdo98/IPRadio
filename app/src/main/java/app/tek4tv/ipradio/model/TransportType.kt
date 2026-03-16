package app.tek4tv.ipradio.model

object TransportType {
    const val ETH = 0
    const val MOBILE = 1
    const val WIFI = 2
}

object SimType {
    const val PHYSICAL = 0
    const val ESIM = 1
}

object RadioMode {
    const val AUTO = 0
    const val G2 = 2
    const val G3 = 3
    const val G4 = 4
}

data class NetworkConfig(
    val transport: Int,   // 0 ETH, 1 Mobile, 2 WiFi
    val simType: Int? = null,   // chỉ dùng khi transport = MOBILE
    val radioMode: Int? = null  // chỉ dùng khi transport = MOBILE
)