package app.tek4tv.ipradio.core.webserver

import android.content.Context
import app.tek4tv.ipradio.core.webserver.MiraWebConsolePages.renderAboutPage
import app.tek4tv.ipradio.core.webserver.MiraWebConsolePages.renderConnectionPage
import app.tek4tv.ipradio.core.webserver.MiraWebConsolePages.renderMediaPage
import app.tek4tv.ipradio.model.McuInfoData
import app.tek4tv.ipradio.model.MediaInfo
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

    fun updateBasicInfo(info: DeviceBasicInfo) {
        deviceBasicInfo = info
    }

    fun updateConnectionSettings(settings: ConnectionSettings) {
        currentConnectionSettings = settings
    }

    fun updateMcuInfoData(mcuInfo: McuInfoData?) {
        if (mcuInfo == null) return
        currentMcuInfoData = mcuInfo
    }

    fun updateMediaList(mediaList: List<MediaInfo>) {
        currentMediaList = mediaList.toSet().toList()
    }

    fun start(
        context: Context,
        globalProperties: GlobalProperties,
        onConnectionUpdate: (ConnectionSettings) -> Unit,
        onMediaAction: (MediaActionData) -> Unit,
        onGetStatusData: () -> StatusPageData,
        onResetConnection: () -> Unit,
        onResetSettings: () -> Unit,
        onRebootMira: () -> Unit,
        onRestartApp: () -> Unit,
        onFactoryReset: () -> Unit
    ) {
        if (server != null) return

        server = AppNanoHttpd(
            context = context,
            globalProperties = globalProperties,
            port = 8080,
            onConnectionUpdate = onConnectionUpdate,
            onMediaAction = onMediaAction,
            onGetStatusData = onGetStatusData,
            onResetConnection = onResetConnection,
            onResetSettings = onResetSettings,
            onRebootMira = onRebootMira,
            onRestartApp = onRestartApp,
            onFactoryReset = onFactoryReset
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
        private val onResetConnection: () -> Unit,
        private val onResetSettings: () -> Unit,
        private val onRebootMira: () -> Unit,
        private val onRestartApp: () -> Unit,
        private val onFactoryReset: () -> Unit,
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
                            html(
                                MiraWebConsolePages.renderSettingsPage(
                                    data = currentConnectionSettings
                                )
                            )
                        }
                    }

                    method == Method.GET && uri == "/io-ct" -> {
                        requireLogin(session) { html(MiraWebConsolePages.renderIoCtPage()) }
                    }

                    method == Method.GET && uri == "/class-d" -> {
                        requireLogin(session) { html(MiraWebConsolePages.renderClassDPage()) }
                    }

                    method == Method.GET && uri == "/media" -> {
                        requireLogin(session) {
                            val selectedMid =
                                session.parameters["mid"]?.firstOrNull()?.toLongOrNull()

                            html(
                                renderMediaPage(
                                    mediaList = currentMediaList, selectedMid = selectedMid
                                )
                            )
                        }
                    }

                    method == Method.GET && uri == "/player" -> {
                        requireLogin(session) { html(MiraWebConsolePages.renderPlayerPage()) }
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
                                ?: currentConnectionSettings.fmAutoThreshold
                        )

                        currentConnectionSettings = conn
                        onConnectionUpdate(conn)

                        if (uri == "/update-settings") {
                            html(MiraWebConsolePages.renderSettingsPage(conn))
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

                        onMediaAction(
                            MediaActionData(
                                action = "delete", mid = mid
                            )
                        )

                        redirect("/media")
                    }

                    method == Method.POST && uri == "/media/delete-all" -> {
                        if (!isLoggedIn(session)) return redirect("/login")

                        onMediaAction(
                            MediaActionData(
                                action = "delete_all"
                            )
                        )

                        redirect("/media")
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