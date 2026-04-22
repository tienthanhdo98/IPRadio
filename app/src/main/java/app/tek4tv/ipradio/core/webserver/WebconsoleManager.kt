package app.tek4tv.ipradio.core.webserver

import android.content.Context
import app.tek4tv.ipradio.core.webserver.MiraWebConsolePages.renderAboutPage
import app.tek4tv.ipradio.core.webserver.MiraWebConsolePages.renderClassDPage
import app.tek4tv.ipradio.core.webserver.MiraWebConsolePages.renderConnectionPage
import app.tek4tv.ipradio.core.webserver.MiraWebConsolePages.renderMediaPage
import app.tek4tv.ipradio.model.McuInfoData
import app.tek4tv.ipradio.model.MediaInfo
import app.tek4tv.ipradio.model.webconsole.ClassDUiModel
import app.tek4tv.ipradio.model.webconsole.ConnectionSettings
import app.tek4tv.ipradio.model.webconsole.DeviceBasicInfo
import app.tek4tv.ipradio.model.webconsole.MediaActionData
import app.tek4tv.ipradio.model.webconsole.StatusPageData
import app.tek4tv.ipradio.utils.GlobalProperties
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.SOCKET_READ_TIMEOUT
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class UserSession(val username: String)


object LocalWebConsole {
    private var server: AppNanoHttpd? = null

    private val sessions = ConcurrentHashMap<String, UserSession>()

    @Volatile
    private var deviceBasicInfo: DeviceBasicInfo = DeviceBasicInfo()

    @Volatile
    private var currentConnectionSettings: ConnectionSettings = ConnectionSettings()

    @Volatile
    private var currentMcuInfoData: McuInfoData = McuInfoData()

    @Volatile
    private var currentMediaList: List<MediaInfo> = listOf()

    @Volatile
    private var currentStatusMediaPlaying: Int = -1

    @Volatile
    private var currentMediaPlayingMid: Long? = 0

    @Volatile
    private var currentClassDData: ClassDUiModel = ClassDUiModel()

    fun updateBasicInfo(info: DeviceBasicInfo) {
        deviceBasicInfo = info
    }

    fun updateConnectionSettings(settings: ConnectionSettings) {
        currentConnectionSettings = settings
    }

    fun updateClassD(classd: ClassDUiModel) {
        currentClassDData = classd
    }

    fun updateMcuInfoData(mcuInfo: McuInfoData?) {
        if (mcuInfo == null) return
        currentMcuInfoData = mcuInfo
    }

    fun updateMediaList(mediaList: List<MediaInfo>) {
        currentMediaList = mediaList.toSet().toList()
    }

    fun updateMediaStatusPlaying(value: Int) {
        currentStatusMediaPlaying = value
    }

    fun start(
        context: Context,
        globalProperties: GlobalProperties,
        onConnectionUpdate: (ConnectionSettings) -> Unit,
        onMediaAction: (MediaActionData) -> Unit,
        onGetStatusData: () -> StatusPageData,
        onGetPlayerStatus: () -> app.tek4tv.ipradio.model.webconsole.PlayerStatusData,
        onResetConnection: () -> Unit,
        onResetSettings: () -> Unit,
        onRebootMira: () -> Unit,
        onRestartApp: () -> Unit,
        onFactoryReset: () -> Unit,
        onClassDUpdate: (ClassDUiModel) -> Unit,
    ) {
        if (server != null) return

        server = AppNanoHttpd(
            context = context,
            globalProperties = globalProperties,
            port = 8080,
            onConnectionUpdate = onConnectionUpdate,
            onMediaAction = onMediaAction,
            onGetStatusData = onGetStatusData,
            onGetPlayerStatus = onGetPlayerStatus,
            onResetConnection = onResetConnection,
            onResetSettings = onResetSettings,
            onRebootMira = onRebootMira,
            onRestartApp = onRestartApp,
            onFactoryReset = onFactoryReset,
            onClassDUpdate = onClassDUpdate
        ).apply {
            start(SOCKET_READ_TIMEOUT, false)
        }
    }

    fun stop() {
        server?.stop()
        server = null
    }

    private class AppNanoHttpd(
        private val context: Context,
        private val globalProperties: GlobalProperties,
        port: Int,
        private val onConnectionUpdate: (ConnectionSettings) -> Unit,
        private val onMediaAction: (MediaActionData) -> Unit,
        private val onGetStatusData: () -> StatusPageData,
        private val onGetPlayerStatus: () -> app.tek4tv.ipradio.model.webconsole.PlayerStatusData,
        private val onResetConnection: () -> Unit,
        private val onResetSettings: () -> Unit,
        private val onRebootMira: () -> Unit,
        private val onRestartApp: () -> Unit,
        private val onFactoryReset: () -> Unit,
        private val onClassDUpdate: (ClassDUiModel) -> Unit,
    ) : NanoHTTPD(port) {

        override fun serve(session: IHTTPSession): Response {
            return try {
                val uri = session.uri ?: "/"
                val method = session.method

                when {
                    method == Method.GET && uri == "/login" -> {
                        html(MiraWebConsolePages.renderLoginPage())
                    }

                    method == Method.GET && uri == "/" -> {
                        redirect("/config-connection")
                    }

//                    method == Method.GET && uri == "/config-connection" -> {
//                        requireLogin(session) { html(renderConnectionPage()) }
//                    }
                    method == Method.GET && uri == "/config-connection" -> {
                        requireLogin(session) {
                            html(
                                renderConnectionPage(
                                    basicInfo = deviceBasicInfo, data = currentConnectionSettings
                                )
                            )
                        }
                    }

                    method == Method.GET && uri == "/config-settings" -> {
                        requireLogin(session) {
                            val wifiList =
                                app.tek4tv.ipradio.utils.network.NetworkUtils.getAvailableWifiNetworks(
                                    context
                                )
                            html(
                                MiraWebConsolePages.renderSettingsPage(
                                    data = currentConnectionSettings, wifiList = wifiList
                                )
                            )
                        }
                    }

                    // API: trả danh sách WiFi dưới dạng JSON (không cần login để polling nhanh)
                    method == Method.GET && uri == "/api/wifi-list" -> {
                        if (!isLoggedIn(session)) return json("[]")
                        val list =
                            app.tek4tv.ipradio.utils.network.NetworkUtils.getAvailableWifiNetworks(
                                context
                            )
                        val jsonArr = list.joinToString(",") { "\"${it.replace("\"", "\\\"")}\"" }
                        json("[$jsonArr]")
                    }

                    method == Method.GET && uri == "/io-ct" -> {
                        requireLogin(session) { html(MiraWebConsolePages.renderIoCtPage()) }
                    }

                    method == Method.GET && uri == "/class-d" -> {
                        requireLogin(session) { html(renderClassDPage(currentClassDData)) }
                    }

                    method == Method.GET && uri == "/media" -> {
                        requireLogin(session) {
                            val selectedMid =
                                session.parameters["mid"]?.firstOrNull()?.toLongOrNull()

                            html(
                                renderMediaPage(
                                    mediaList = currentMediaList,
                                    selectedMid = selectedMid,
                                    statusPlaying = currentStatusMediaPlaying,
                                )
                            )
                        }
                    }

                    method == Method.GET && uri == "/player" -> {
                        requireLogin(session) { html(MiraWebConsolePages.renderPlayerPage()) }
                    }

                    method == Method.GET && uri == "/api/player-status" -> {
                        if (!isLoggedIn(session)) return json("{}")
                        val d = onGetPlayerStatus()
                        val posS = d.currentFilePositionMs / 1000
                        val durS = d.currentFileDurationMs / 1000
                        val pct =
                            if (durS > 0) String.format("%.1f", posS * 100.0 / durS) else "0.0"

                        fun Long.toMmSs(): String {
                            val m = this / 60;
                            val s = this % 60
                            return "${m}m${s.toString().padStart(2, '0')}s"
                        }

                        val j = buildString {
                            append("{")
                            append("\"mediaId\":${d.mediaId ?: "null"},")
                            append("\"cycle\":\"${d.cycleSec.toMmSs()}\",")
                            append("\"fileChunk\":\"${d.fileChunkCurrent}/${d.fileChunkTotal}\",")
                            append("\"posMs\":${d.currentFilePositionMs},")
                            append("\"durMs\":${d.currentFileDurationMs},")
                            append("\"posS\":${posS},")
                            append("\"durS\":${durS},")
                            append("\"pct\":${pct},")
                            append("\"posFmt\":\"${posS.toMmSs()}\",")
                            append("\"durFmt\":\"${durS.toMmSs()}\",")
                            append("\"isMpd\":${d.isMpdOn},")
                            append("\"isFm\":${d.isFmOn},")
                            append("\"fmRssi\":${d.fmRssi},")
                            append("\"isSilence\":${d.isSilenceOn},")
                            append("\"isLiveMic\":${d.isLiveMicOn},")
                            append("\"playMode\":\"${d.playMode}\"")
                            append("}")
                        }
                        json(j)
                    }

                    method == Method.GET && uri == "/api/media-status" -> {
                        if (!isLoggedIn(session)) return json("{}")
                        json("{\"status\":$currentStatusMediaPlaying}")
                    }

                    method == Method.GET && uri == "/status" -> {
                        requireLogin(session) {
                            html(
                                MiraWebConsolePages.renderStatusPage(
                                    onGetStatusData()
                                )
                            )
                        }
                    }

                    method == Method.GET && uri == "/3g-4g" -> {
                        requireLogin(session) {
                            html(
                                MiraWebConsolePages.render3G4GPage(
                                    currentMcuInfoData
                                )
                            )
                        }
                    }

                    method == Method.GET && uri == "/admin" -> {
                        requireLogin(session) { html(MiraWebConsolePages.renderAdminPage()) }
                    }

                    method == Method.GET && uri == "/about" -> {
                        requireLogin(session) { html(renderAboutPage(currentMcuInfoData, context)) }
                    }

                    method == Method.POST && uri == "/login" -> {
                        val params = parsePostParams(session)
                        val username = params["username"].orEmpty()
                        val password = params["password"].orEmpty()

                        if (username == "admin" && password == globalProperties.adminPassword) {
                            val sid = UUID.randomUUID().toString()
                            sessions[sid] = UserSession(username = "admin")
                            redirect(
                                "/config-connection",
                                cookies = listOf("USER_SESSION=$sid; Path=/; HttpOnly; SameSite=Lax")
                            )
                        } else {
                            html(MiraWebConsolePages.renderLoginPage())
                        }
                    }

                    method == Method.GET && uri == "/logout" -> {
                        val sid = getCookie(session, "USER_SESSION")
                        if (sid != null) sessions.remove(sid)
                        redirect(
                            "/login", cookies = listOf("USER_SESSION=deleted; Path=/; Max-Age=0")
                        )
                    }

                    //Post
                    method == Method.POST && (uri == "/update-connection" || uri == "/update-settings") -> {
                        if (!isLoggedIn(session)) return redirect("/login")

                        val params = parsePostParams(session)

                        val conn = ConnectionSettings(
                            localIp = params["local_ip"] ?: currentConnectionSettings.localIp,
                            subnet = params["subnet"] ?: currentConnectionSettings.subnet,
                            gateway = params["gateway"] ?: currentConnectionSettings.gateway,
                            mqttSecurity = params["mqtt_security"]
                                ?: currentConnectionSettings.mqttSecurity,
                            mqttServer = params["mqtt_server"]
                                ?: currentConnectionSettings.mqttServer,
                            mqttPort = params["mqtt_port"] ?: currentConnectionSettings.mqttPort,
                            networkType = params["network_type"]
                                ?: currentConnectionSettings.networkType,
                            simSelection = params["sim_slot"]
                                ?: currentConnectionSettings.simSelection,
                            mobileDataMode = params["mobile_data_mode"]
                                ?: currentConnectionSettings.mobileDataMode,
                            activation = params["activation"]
                                ?: currentConnectionSettings.activation,
                            normalVolume = params["normal_volume"]
                                ?: currentConnectionSettings.normalVolume,
                            fmVolume = params["fm_volume"] ?: currentConnectionSettings.fmVolume,
                            fmAutoStop = params["fm_auto_stop"]
                                ?: currentConnectionSettings.fmAutoStop,
                            fmAutoThreshold = params["fm_rssi_threshold"]
                                ?: currentConnectionSettings.fmAutoThreshold,
                            wifiSsid = params["wifi_ssid"] ?: currentConnectionSettings.wifiSsid,
                            wifiPassword = params["wifi_password"]
                                ?: currentConnectionSettings.wifiPassword
                        )

                        currentConnectionSettings = conn
                        onConnectionUpdate(conn)

                        if (uri == "/update-settings") {
                            val wifiList =
                                app.tek4tv.ipradio.utils.network.NetworkUtils.getAvailableWifiNetworks(
                                    context
                                )
                            html(MiraWebConsolePages.renderSettingsPage(conn, wifiList))
                        } else {
                            html(renderConnectionPage(deviceBasicInfo, conn))
                        }

                    }

                    method == Method.POST && uri == "/default-connection" -> {
                        if (!isLoggedIn(session)) return redirect("/login")

                        globalProperties.clearConnectionConfig()

                        val defaultConn = currentConnectionSettings.copy(
                            localIp = globalProperties.localIp,
                            subnet = globalProperties.subnet,
                            gateway = globalProperties.gateway,
                            mqttSecurity = globalProperties.mqttSecurity.toString(),
                            mqttServer = globalProperties.mqttServer,
                            mqttPort = globalProperties.mqttPort.toString()
                        )
                        currentConnectionSettings = defaultConn
                        onResetConnection()
                        redirect("/config-connection")
                    }

                    method == Method.POST && uri == "/default-settings" -> {
                        if (!isLoggedIn(session)) return redirect("/login")

                        globalProperties.clearSettingsConfig()

                        val defaultConn = currentConnectionSettings.copy(
                            networkType = globalProperties.settingsTxType.toString(),
                            simSelection = globalProperties.settingsSimType.toString(),
                            mobileDataMode = globalProperties.settingsSimMode.toString(),
                            activation = globalProperties.settingsDisable.toString(),
                            normalVolume = globalProperties.mainVolume,
                            fmVolume = globalProperties.fmVolume,
                            fmAutoStop = globalProperties.fmAutoStop.toString(),
                            fmAutoThreshold = globalProperties.fmThreshold
                        )
                        currentConnectionSettings = defaultConn
                        onResetSettings()
                        redirect("/config-settings")
                    }

                    method == Method.POST && uri == "/default-classd" -> {
                        if (!isLoggedIn(session)) return redirect("/login")

                        globalProperties.clearClassDConfig()

                        val defaultModel = ClassDUiModel(
                            threshold = globalProperties.classdThreshold,
                            szdLeft = globalProperties.classdSzdLeft,
                            szdRight = globalProperties.classdSzdRight,
                            muteLeft = globalProperties.muteSpeakerLeft,
                            muteRight = globalProperties.muteSpeakerRight,
                            speaker1Offset = globalProperties.adcThresholdCh1,
                            speaker2Offset = globalProperties.adcThresholdCh2,
                            speaker3Offset = globalProperties.adcThresholdCh3,
                            speaker4Offset = globalProperties.adcThresholdCh4
                        )

                        // Cập nhật biến lưu trữ hiện tại
                        currentClassDData = defaultModel

                        // Gọi callback để thông báo xuống thiết bị MCU/Phần cứng
                        onClassDUpdate(defaultModel)

                        // Tải lại trang ClassD để hiển thị các giá trị mới
                        redirect("/class-d")
                    }

                    // Admin Routes
                    method == Method.GET && uri == "/admin" -> {
                        requireLogin(session) {
                            html(MiraWebConsolePages.renderAdminPage())
                        }
                    }

                    method == Method.POST && uri == "/admin/change-password" -> {
                        if (!isLoggedIn(session)) return redirect("/login")
                        val params = parsePostParams(session)
                        val curr = params["current_password"].orEmpty()
                        val newp = params["new_password"].orEmpty()
                        val conf = params["confirm_password"].orEmpty()

                        if (curr == globalProperties.adminPassword && newp == conf && newp.isNotEmpty()) {
                            globalProperties.saveAdminPassword(newp)
                            redirect("/admin")
                        } else {
                            // Alternatively we can use an error param, but for simplicity redirect back
                            redirect("/admin")
                        }
                    }

                    method == Method.POST && uri == "/admin/reboot-mira" -> {
                        if (!isLoggedIn(session)) return redirect("/login")
                        onRebootMira()
                        redirect("/admin")
                    }

                    method == Method.POST && uri == "/admin/restart-app" -> {
                        if (!isLoggedIn(session)) return redirect("/login")
                        onRestartApp()
                        redirect("/admin")
                    }

                    method == Method.POST && uri == "/admin/factory-reset" -> {
                        if (!isLoggedIn(session)) return redirect("/login")
                        onFactoryReset()
                        redirect("/admin")
                    }

                    //Post Media
                    method == Method.POST && uri == "/media/play" -> {
                        if (!isLoggedIn(session)) return redirect("/login")

                        val params = parsePostParams(session)
                        val mid = params["mid"]?.toLongOrNull()
                        currentMediaPlayingMid = mid
                        currentStatusMediaPlaying = 2
                        onMediaAction(
                            MediaActionData(
                                action = "play", mid = mid
                            )
                        )

                        redirect("/media?mid=${mid ?: ""}")
                    }

                    method == Method.POST && uri == "/media/stop" -> {
                        if (!isLoggedIn(session)) return redirect("/login")

                        val params = parsePostParams(session)
                        val mid = params["mid"]?.toLongOrNull()
                        currentStatusMediaPlaying = -1

                        onMediaAction(
                            MediaActionData(
                                action = "stop", mid = mid
                            )
                        )

                        redirect("/media?mid=${mid ?: ""}")
                    }

                    method == Method.POST && uri == "/media/delete" -> {
                        if (!isLoggedIn(session)) return redirect("/login")

                        val params = parsePostParams(session)
                        val mid = params["mid"]?.toLongOrNull()
                        currentStatusMediaPlaying = -1

                        onMediaAction(
                            MediaActionData(
                                action = "delete", mid = mid
                            )
                        )

                        redirect("/media")
                    }

                    method == Method.POST && uri == "/media/delete-all" -> {
                        if (!isLoggedIn(session)) return redirect("/login")

                        val params = parsePostParams(session)
                        val from = params["from"]
                        currentStatusMediaPlaying = -1

                        onMediaAction(
                            MediaActionData(
                                action = "delete_all"
                            )
                        )

                        // Kiểm tra xem request đến từ đâu để chuyển hướng cho đúng
                        if (from == "player") {
                            redirect("/player")
                        } else {
                            redirect("/media")
                        }
                    }

                    method == Method.POST && uri == "/media/next" -> {
                        if (!isLoggedIn(session)) return redirect("/login")
                        onMediaAction(
                            MediaActionData(
                                action = "next"
                            )
                        )
                        redirect("/player")
                    }

                    method == Method.POST && uri == "/update-classd" -> {
                        if (!isLoggedIn(session)) return redirect("/login")

                        val params = parsePostParams(session)

                        val model = ClassDUiModel(
                            monitorChannel = params["monitor_channel"] ?: "CH 2-4",
                            szdLeft = params["szd_left"]?.toIntOrNull() ?: 0,
                            szdRight = params["szd_right"]?.toIntOrNull() ?: 0,
                            muteLeft = params["mute_left"]?.toIntOrNull() ?: 0,
                            muteRight = params["mute_right"]?.toIntOrNull() ?: 0,
                            threshold = params["threshold"]?.toIntOrNull() ?: 5,
                            speaker1Offset = params["speaker1_offset"]?.toIntOrNull() ?: 25,
                            speaker2Offset = params["speaker2_offset"]?.toIntOrNull() ?: 20,
                            speaker3Offset = params["speaker3_offset"]?.toIntOrNull() ?: 20,
                            speaker4Offset = params["speaker4_offset"]?.toIntOrNull() ?: 20,
                        )

                        onClassDUpdate(model)

                        html(renderClassDPage(model))
                    }

                    else -> {
                        newFixedLengthResponse(
                            Response.Status.NOT_FOUND, "text/plain; charset=UTF-8", "404 Not Found"
                        )
                    }


                }
            } catch (e: Exception) {
                newFixedLengthResponse(
                    Response.Status.INTERNAL_ERROR,
                    "text/plain; charset=UTF-8",
                    "500 Internal Server Error\n${e.message}"
                )
            }
        }

        private fun isLoggedIn(session: IHTTPSession): Boolean {
            val sid = getCookie(session, "USER_SESSION") ?: return false
            return sessions.containsKey(sid)
        }

        //        private fun getCookie(session: IHTTPSession, name: String): String? {
//            val cookieHeader = session.headers["cookie"] ?: return null
//            return cookieHeader.split(";").map { it.trim() }.firstOrNull { it.startsWith("$name=") }
//                ?.substringAfter("=")
//        }
        private fun getCookie(session: IHTTPSession, name: String): String? {
            val cookieHeader = session.headers["cookie"] ?: return null
            return cookieHeader.split(";").map { it.trim() }
                .firstOrNull { it.startsWith("$name=", ignoreCase = true) }?.substringAfter("=", "")
                ?.trim()
        }

        private fun parsePostParams(session: IHTTPSession): Map<String, String> {
            val files = HashMap<String, String>()
            session.parseBody(files)

            val result = mutableMapOf<String, String>()
            for ((key, value) in session.parameters) {
                result[key] = value.firstOrNull().orEmpty()
            }
            return result
        }

        //No Cache
        private fun html(content: String): Response {
            val r = newFixedLengthResponse(Response.Status.OK, "text/html; charset=UTF-8", content)
            r.addHeader("Cache-Control", "no-cache, no-store, must-revalidate")
            return r
        }

        private fun json(content: String): Response {
            val r = newFixedLengthResponse(
                Response.Status.OK, "application/json; charset=UTF-8", content
            )
            r.addHeader("Cache-Control", "no-cache, no-store, must-revalidate")
            return r
        }

        private fun redirect(location: String, cookies: List<String> = emptyList()): Response {
            val response = newFixedLengthResponse(
                Response.Status.REDIRECT_SEE_OTHER, // 303 - không bị cache
                "text/plain", ""
            )
            response.addHeader("Location", location)
            response.addHeader("Cache-Control", "no-cache, no-store, must-revalidate")
            cookies.forEach { response.addHeader("Set-Cookie", it) }
            return response
        }

        private fun requireLogin(session: IHTTPSession, onSuccess: () -> Response): Response {
            val sid = getCookie(session, "USER_SESSION")

            android.util.Log.d("WebConsole", "=== requireLogin ===")
            android.util.Log.d("WebConsole", "URI: ${session.uri}")
            android.util.Log.d("WebConsole", "Cookie raw: ${session.headers["cookie"]}")
            android.util.Log.d("WebConsole", "SID extracted: '$sid'")
            android.util.Log.d("WebConsole", "Active sessions: ${sessions.keys.toList()}")

            if (sid == null || !sessions.containsKey(sid)) {
                return redirect(
                    "/login", cookies = listOf("USER_SESSION=deleted; Path=/; Max-Age=0")
                )
            }
            return onSuccess()
        }
    }

}