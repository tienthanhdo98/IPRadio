package app.tek4tv.ipradio.core.webserver

import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.SOCKET_READ_TIMEOUT
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class UserSession(val username: String)
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

object LocalWebConsole {
    private var server: AppNanoHttpd? = null

    fun start(
        onServerUpdate: (ServerSettings) -> Unit,
        onConnectionUpdate: (ConnectionSettings) -> Unit
    ) {
        if (server != null) return

        server = AppNanoHttpd(
            port = 8080,
            onServerUpdate = onServerUpdate,
            onConnectionUpdate = onConnectionUpdate
        ).apply {
            start(SOCKET_READ_TIMEOUT, false)
        }
    }

    fun stop() {
        server?.stop()
        server = null
    }

    private class AppNanoHttpd(
        port: Int,
        private val onServerUpdate: (ServerSettings) -> Unit,
        private val onConnectionUpdate: (ConnectionSettings) -> Unit
    ) : NanoHTTPD(port) {

        private val sessions = ConcurrentHashMap<String, UserSession>()

        override fun serve(session: IHTTPSession): Response {
            return try {
                val uri = session.uri ?: "/"
                val method = session.method

                when {
                    method == Method.GET && uri == "/login" -> {
                        html(renderLoginPage())
                    }

                    method == Method.POST && uri == "/login" -> {
                        val params = parsePostParams(session)
                        val password = params["password"].orEmpty()

                        if (password == "admin123") {
                            val sid = UUID.randomUUID().toString()
                            sessions[sid] = UserSession("admin")

                            redirect("/", cookies = listOf("USER_SESSION=$sid; Path=/; HttpOnly"))
                        } else {
                            html(
                                renderStatusPage(
                                    title = "Đăng nhập thất bại",
                                    message = "Mật khẩu không chính xác. Vui lòng kiểm tra lại.",
                                    isSuccess = false,
                                    backUrl = "/login"
                                )
                            )
                        }
                    }

                    method == Method.GET && uri == "/logout" -> {
                        val sid = getCookie(session, "USER_SESSION")
                        if (sid != null) sessions.remove(sid)
                        redirect(
                            "/login",
                            cookies = listOf("USER_SESSION=deleted; Path=/; Max-Age=0")
                        )
                    }

                    method == Method.GET && uri == "/" -> {
                        if (!isLoggedIn(session)) return redirect("/login")
                        html(renderMenuPage())
                    }

                    method == Method.GET && uri == "/config-server" -> {
                        if (!isLoggedIn(session)) return redirect("/login")
                        html(renderServerPage())
                    }

                    method == Method.GET && uri == "/config-connection" -> {
                        if (!isLoggedIn(session)) return redirect("/login")
                        html(renderConnectionPage())
                    }

                    method == Method.POST && uri == "/update-server" -> {
                        if (!isLoggedIn(session)) return redirect("/login")

                        val params = parsePostParams(session)
                        val settings = ServerSettings(
                            bootstrapServer = params["bootstrap_server"].orEmpty(),
                            mqttServer = params["mqtt_server"].orEmpty(),
                            mqttPort = params["mqtt_port"]?.toIntOrNull() ?: 1883,
                            mqttSecurity = params["mqtt_security"]?.toIntOrNull() ?: 0
                        )

                        onServerUpdate(settings)

                        html(
                            renderStatusPage(
                                title = "Thành công!",
                                message = "Cấu hình Server đã được lưu và đang đồng bộ.",
                                isSuccess = true,
                                backUrl = "/"
                            )
                        )
                    }

                    method == Method.POST && uri == "/update-connection" -> {
                        if (!isLoggedIn(session)) return redirect("/login")

                        val params = parsePostParams(session)
                        val conn = ConnectionSettings(
                            thingId = params["thing_id"].orEmpty(),
                            thingKey = params["thing_key"].orEmpty(),
                            channels = params["channels"].orEmpty()
                        )

                        onConnectionUpdate(conn)

                        html(
                            renderStatusPage(
                                title = "Thành công!",
                                message = "Cấu hình Kết nối đã được lưu và đang đồng bộ.",
                                isSuccess = true,
                                backUrl = "/"
                            )
                        )
                    }

                    else -> {
                        newFixedLengthResponse(
                            Response.Status.NOT_FOUND,
                            "text/plain; charset=UTF-8",
                            "404 Not Found"
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

        private fun getCookie(session: IHTTPSession, name: String): String? {
            val cookieHeader = session.headers["cookie"] ?: return null
            return cookieHeader.split(";")
                .map { it.trim() }
                .firstOrNull { it.startsWith("$name=") }
                ?.substringAfter("=")
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

        private fun html(content: String): Response {
            return newFixedLengthResponse(
                Response.Status.OK,
                "text/html; charset=UTF-8",
                content
            )
        }

        private fun redirect(location: String, cookies: List<String> = emptyList()): Response {
            val response = newFixedLengthResponse(Response.Status.REDIRECT, "text/plain", "")
            response.addHeader("Location", location)
            cookies.forEach { response.addHeader("Set-Cookie", it) }
            return response
        }
    }

    private fun renderLoginPage() = """
    <!DOCTYPE html>
    <html lang="vi">
    <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
        <title>MGW-v3 Login</title>
        <style>
            body { font-family: 'Segoe UI', Roboto, sans-serif; background: #f0f2f5; display: flex; justify-content: center; align-items: center; min-height: 100vh; margin: 0; padding: 20px; box-sizing: border-box; }
            .login-card { background: white; padding: 40px 30px; border-radius: 20px; box-shadow: 0 10px 25px rgba(0,0,0,0.05); width: 100%; max-width: 360px; text-align: center; }
            h2 { color: #1a73e8; margin: 0 0 25px 0; font-size: 24px; }
            input { width: 100%; padding: 14px; margin: 10px 0; border: 1px solid #ddd; border-radius: 10px; box-sizing: border-box; font-size: 16px; transition: border 0.3s; outline: none; }
            input:focus { border-color: #1a73e8; }
            button { width: 100%; padding: 14px; background: #1a73e8; color: white; border: none; border-radius: 10px; font-size: 16px; font-weight: bold; cursor: pointer; margin-top: 10px; }
            button:active { background: #1557b0; transform: scale(0.98); }
            p { font-size: 13px; color: #888; margin-top: 20px; }
        </style>
    </head>
    <body>
        <div class="login-card">
            <h2>MGW-v3 Login</h2>
            <form action='/login' method='post'>
                <input type='password' name='password' placeholder='Nhập mật khẩu' required autofocus>
                <button type='submit'>ĐĂNG NHẬP</button>
            </form>
            <p>Truy cập quản trị nội bộ thiết bị</p>
        </div>
    </body>
    </html>
    """.trimIndent()

    private fun renderMenuPage() = """
    <!DOCTYPE html>
    <html lang="vi">
    <head>
        <meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title>Menu Cấu hình MGW</title>
        <style>
            body { font-family: sans-serif; background: #f4f7f6; display: flex; justify-content: center; align-items: center; min-height: 100vh; margin: 0; padding: 20px; box-sizing: border-box; }
            .card { background: white; width: 100%; max-width: 400px; padding: 30px; border-radius: 20px; box-shadow: 0 8px 20px rgba(0,0,0,0.06); text-align: center; }
            h2 { margin-bottom: 10px; color: #333; }
            p { color: #666; margin-bottom: 25px; }
            .btn-menu { display: block; width: 100%; padding: 18px; margin: 12px 0; background: #1a73e8; color: white; text-decoration: none; border-radius: 12px; font-weight: bold; font-size: 16px; box-sizing: border-box; transition: 0.2s; }
            .btn-menu:active { background: #1557b0; transform: scale(0.97); }
            .logout { display: inline-block; margin-top: 20px; color: #d93025; text-decoration: none; font-size: 14px; font-weight: 500; }
        </style>
    </head>
    <body>
        <div class="card">
            <h2>MGW Dashboard</h2>
            <p>Vui lòng chọn mục thiết lập</p>
            <a href="/config-server" class="btn-menu">Cấu hình Server</a>
            <a href="/config-connection" class="btn-menu">Cấu hình Kết nối</a>
            <a href="/logout" class="logout">Đăng xuất tài khoản</a>
        </div>
    </body>
    </html>
    """.trimIndent()

    private fun renderServerPage() = """
    <!DOCTYPE html>
    <html lang="vi">
    <head>
        <meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title>Cấu hình Server</title>
        <style>
            body { font-family: sans-serif; background: #f4f7f6; display: flex; justify-content: center; align-items: center; min-height: 100vh; margin: 0; padding: 20px; box-sizing: border-box; }
            .container { width: 100%; max-width: 450px; background: white; padding: 30px; border-radius: 20px; box-shadow: 0 8px 20px rgba(0,0,0,0.06); }
            .back-link { display: inline-block; margin-bottom: 20px; text-decoration: none; color: #1a73e8; font-weight: 500; }
            h2 { margin: 0 0 20px 0; color: #333; border-left: 4px solid #1a73e8; padding-left: 15px; }
            label { display: block; margin-top: 15px; font-weight: 600; color: #555; font-size: 14px; }
            input, select { width: 100%; padding: 12px; margin-top: 6px; border: 1px solid #ddd; border-radius: 10px; box-sizing: border-box; font-size: 16px; outline: none; }
            input:focus { border-color: #1a73e8; }
            .btn-save { width: 100%; padding: 16px; background: #34a853; color: white; border: none; border-radius: 10px; font-weight: bold; font-size: 16px; margin-top: 30px; cursor: pointer; }
            .btn-save:active { background: #2d8a45; transform: scale(0.98); }
        </style>
    </head>
    <body>
        <div class="container">
            <a href="/" class="back-link">← Quay lại Menu</a>
            <h2>Server Settings</h2>
            <form action='/update-server' method='post'>
                <label>Bootstrap Server</label>
                <input type='text' name='bootstrap_server' value='http://bootstrap.aiot.vn'>
                <label>MQTT Server</label>
                <input type='text' name='mqtt_server' value='aiot.mobifone.vn'>
                <label>MQTT Port</label>
                <input type='number' name='mqtt_port' value='1883'>
                <label>MQTT Security</label>
                <select name='mqtt_security'>
                    <option value='0'>None (TCP)</option>
                    <option value='1'>SSL/TLS</option>
                </select>
                <button type='submit' class='btn-save'>LƯU THÔNG TIN</button>
            </form>
        </div>
    </body>
    </html>
    """.trimIndent()

    private fun renderConnectionPage() = """
    <!DOCTYPE html>
    <html lang="vi">
    <head>
        <meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title>Cấu hình Kết nối</title>
        <style>
            body { font-family: sans-serif; background: #f4f7f6; display: flex; justify-content: center; align-items: center; min-height: 100vh; margin: 0; padding: 20px; box-sizing: border-box; }
            .container { width: 100%; max-width: 450px; background: white; padding: 30px; border-radius: 20px; box-shadow: 0 8px 20px rgba(0,0,0,0.06); }
            .back-link { display: inline-block; margin-bottom: 20px; text-decoration: none; color: #1a73e8; font-weight: 500; }
            h2 { margin: 0 0 20px 0; color: #333; border-left: 4px solid #fbbc05; padding-left: 15px; }
            label { display: block; margin-top: 15px; font-weight: 600; color: #555; font-size: 14px; }
            input { width: 100%; padding: 12px; margin-top: 6px; border: 1px solid #ddd; border-radius: 10px; box-sizing: border-box; font-size: 16px; }
            .btn-save { width: 100%; padding: 16px; background: #fbbc05; color: #333; border: none; border-radius: 10px; font-weight: bold; font-size: 16px; margin-top: 30px; cursor: pointer; }
            .btn-save:active { background: #eab000; transform: scale(0.98); }
        </style>
    </head>
    <body>
        <div class="container">
            <a href="/" class="back-link">← Quay lại Menu</a>
            <h2>MGW Connection</h2>
            <form action='/update-connection' method='post'>
                <label>Thing ID</label>
                <input type='text' name='thing_id' placeholder='Nhập Thing ID'>
                <label>Thing Key</label>
                <input type='password' name='thing_key' placeholder='Nhập Thing Key'>
                <label>Channels (Topics)</label>
                <input type='text' name='channels' placeholder='Ví dụ: 1, 2, 3'>
                <button type='submit' class='btn-save'>CẬP NHẬT KẾT NỐI</button>
            </form>
        </div>
    </body>
    </html>
    """.trimIndent()

    private fun renderStatusPage(
        title: String,
        message: String,
        isSuccess: Boolean = true,
        backUrl: String = "/"
    ) = """
    <!DOCTYPE html>
    <html lang="vi">
    <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title>Thông báo MGW</title>
        <style>
            body { font-family: sans-serif; background: #f4f7f6; display: flex; justify-content: center; align-items: center; height: 100vh; margin: 0; }
            .card { background: white; padding: 40px; border-radius: 20px; box-shadow: 0 10px 25px rgba(0,0,0,0.1); text-align: center; width: 85%; max-width: 400px; border-top: 5px solid ${if (isSuccess) "#34a853" else "#d93025"}; }
            .icon { font-size: 60px; margin-bottom: 20px; }
            .success-icon { color: #34a853; }
            .error-icon { color: #d93025; }
            h2 { margin: 0 0 10px 0; color: #333; }
            p { color: #666; line-height: 1.5; margin-bottom: 25px; }
            .btn-back { display: inline-block; padding: 12px 25px; background: ${if (isSuccess) "#1a73e8" else "#555"}; color: white; text-decoration: none; border-radius: 8px; font-weight: bold; }
        </style>
    </head>
    <body>
        <div class="card">
            <div class="icon ${if (isSuccess) "success-icon" else "error-icon"}">
                ${if (isSuccess) "✔" else "⚠"}
            </div>
            <h2>$title</h2>
            <p>$message</p>
            <a href="$backUrl" class="btn-back">${if (isSuccess) "Quay lại Menu" else "Thử lại"}</a>
        </div>
    </body>
    </html>
    """.trimIndent()
}