package app.tek4tv.ipradio.core.webserver

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import app.tek4tv.ipradio.model.McuInfoData
import app.tek4tv.ipradio.model.MediaFile
import app.tek4tv.ipradio.model.MediaInfo
import app.tek4tv.ipradio.model.webconsole.ClassDUiModel
import app.tek4tv.ipradio.model.webconsole.ConnectionSettings
import app.tek4tv.ipradio.model.webconsole.DeviceBasicInfo
import app.tek4tv.ipradio.model.webconsole.StatusPageData
import app.tek4tv.ipradio.utils.formatDuration
import app.tek4tv.ipradio.utils.getModeMobileDataNoConvert
import app.tek4tv.ipradio.utils.getStateMobileData
import app.tek4tv.ipradio.utils.network.NetworkUtils

object MiraWebConsolePages {

    fun renderLoginPage(): String = """
        <!DOCTYPE html>
        <html lang="vi">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
            <title>MGW-v3 Login</title>
            <style>
                ${commonStyles()}
                body.login-body {
                    min-height: 100vh;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    background: #f4f6f8;
                    margin: 0;
                    padding: 20px;
                }
                .login-card {
                    background: #fff;
                    width: 100%;
                    max-width: 420px;
                    border-radius: 18px;
                    box-shadow: 0 10px 30px rgba(15, 23, 42, 0.08);
                    padding: 30px;
                }
                .login-title {
                    font-size: 28px;
                    font-weight: 700;
                    color: #2f8a31;
                    margin-bottom: 18px;
                }
                .login-note {
                    margin-top: 14px;
                    padding: 12px 14px;
                    border-radius: 10px;
                    background: #f8fafc;
                    border: 1px dashed #cbd5e1;
                    color: #475569;
                    font-size: 13px;
                    line-height: 1.5;
                }
            </style>
        </head>
        <body class="login-body">
            <div class="login-card">
                <div class="login-title">MGW-v3 Login</div>
                <form action="/login" method="post">
                    <table class="form-table">
                        <tr>
                            <td>Username</td>
                            <td><input type="text" name="username" value="admin" readonly></td>
                        </tr>
                        <tr>
                            <td>Password</td>
                            <td><input type="password" name="password" placeholder="Nhập mật khẩu" required autofocus></td>
                        </tr>
                    </table>
                    <div class="btn-row" style="border-top:0;padding-top:8px;">
                        <button class="btn primary" type="submit" style="width:100%;">Đăng nhập</button>
                    </div>
                </form>
            </div>
        </body>
        </html>
    """.trimIndent()

    fun renderConnectionPage(
        basicInfo: DeviceBasicInfo, data: ConnectionSettings = ConnectionSettings()
    ): String {
        val content = """
       <div class="section">
            <h2 class="section-title">Basic information</h2>
            <table class="form-table">
                <tr><td>MGW ID</td><td>${basicInfo.mgwId}</td></tr>
                <tr><td>Verify Code</td><td>${basicInfo.verifyCode}</td></tr>
                <tr><td>Firmware Version</td><td>${basicInfo.firmwareVersion}</td></tr>
                <tr><td>Local time</td><td id="local-time"></td></tr>
            </table>
        </div>

        <div class="section">
            <h2 class="section-title">LAN settings</h2>
            <table class="form-table">
                <tr><td>Local IP address</td><td><input type="text" name="local_ip" value="${data.localIp ?: "192.168.11.102"}"></td></tr>
                <tr><td>Subnet</td><td><input type="text" name="subnet" value="${data.subnet ?: "255.255.255.0"}"></td></tr>
                <tr><td>Gateway</td><td><input type="text" name="gateway" value="${data.gateway ?: "192.168.11.1"}"></td></tr>
            </table>
        </div>

        <div class="section">
            <h2 class="section-title">MQTT settings</h2>
            <table class="form-table">
                <tr>
                    <td>Security</td>
                    <td>
                        <select name="mqtt_security">
                            <option  value="0" ${if (data.mqttSecurity == "0") "selected" else ""}>None</option>
                            <option  value="1" ${if (data.mqttSecurity == "1" || data.mqttSecurity == null) "selected" else ""}>SSL/TLS</option>
                        </select>
                    </td>
                </tr>
                <tr><td>Server address</td><td><input type="text" name="mqtt_server" value="${data.mqttServer ?: "truyenthanhthongminh.vn"}"></td></tr>
                <tr><td>Server Port</td><td><input type="number" name="mqtt_port" value="${data.mqttPort ?: "1883"}"></td></tr>
            </table>
        </div>

        <div class="btn-row">
            <button class="btn primary" type="submit">Update</button>
            <button class="btn secondary" type="button" onclick="if(confirm('Khôi phục giao thức mạng về mặc định?')) { this.form.action='/default-connection'; this.form.submit(); }">Default</button>
        </div>

        <script>
            function pad(v) {
                return String(v).padStart(2, '0');
            }

            function updateLocalTime() {
                const now = new Date();
                const text =
                    now.getFullYear() + "-" +
                    pad(now.getMonth() + 1) + "-" +
                    pad(now.getDate()) + " " +
                    pad(now.getHours()) + ":" +
                    pad(now.getMinutes()) + ":" +
                    pad(now.getSeconds());

                const el = document.getElementById("local-time");
                if (el) {
                    el.textContent = text;
                }
            }

            updateLocalTime();
            setInterval(updateLocalTime, 1000);
        </script>
    """.trimIndent()

        return renderDashboardLayout(
            title = "Connection",
            activeMenu = "connection",
            content = "<form action='/update-connection' method='post'>$content</form>"
        )
    }

    fun renderSettingsPage(
        data: ConnectionSettings = ConnectionSettings(), wifiList: List<String> = emptyList()
    ): String {
        val fieldStyle =
            "width: 100%; height: 38px; padding: 8px; border: 1px solid #ccc; border-radius: 4px; box-sizing: border-box; font-size: 14px;"

        val content = """
    <div class="section">
        <h2 class="section-title">Network settings</h2>
        <table class="form-table">
            <tr>
                <td>Network Type</td>
                <td>
                    <select name="network_type" id="network_type_select" onchange="updateFieldVisibility()" style="$fieldStyle">                    
                        <option value="1" ${if (data.networkType == "1" || data.networkType == null) "selected" else ""}>Mobile Data</option>
                        <option value="0" ${if (data.networkType == "0") "selected" else ""}>Ethernet</option>
                        <option value="2" ${if (data.networkType == "2") "selected" else ""}>WiFi</option>
                    </select>
                </td>
            </tr>
            
            <tr id="wifi_ssid_row" style="display: none;">
                <td>WiFi Name (SSID)</td>
                <td>
                    <input list="wifi_ssids" name="wifi_ssid" value="${data.wifiSsid ?: ""}" autocomplete="off" style="$fieldStyle">
                    <datalist id="wifi_ssids">
                        ${wifiList.joinToString("\n") { "<option value=\"$it\">" }}
                    </datalist>
                </td>
            </tr>
            <tr id="wifi_password_row" style="display: none;">
                <td>WiFi Password</td>
                <td>
                    <div style="position: relative; display: flex; align-items: center;">
                        <input type="password" id="wifi_pwd_input" name="wifi_password" value="${data.wifiPassword ?: ""}" style="$fieldStyle padding-right: 40px;">
                        
                        <span id="toggle_pwd_icon" onclick="togglePasswordVisibility()" style="position: absolute; right: 10px; cursor: pointer; display: flex; align-items: center;">
                            <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#777" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                                <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"></path>
                                <circle cx="12" cy="12" r="3"></circle>
                            </svg>
                        </span>
                    </div>
                </td>
            </tr>

            <tr id="sim_selection_row">
                <td>SIM selection</td>
                <td>
                    <select name="sim_slot" style="$fieldStyle">
                        <option value="0" ${if (data.simSelection == "0" || data.simSelection == null) "selected" else ""}>Classic(Physical)</option>
                        <option value="1" ${if (data.simSelection == "1") "selected" else ""}>ESim</option>
                    </select>
                </td>
            </tr>
            <tr id="mobile_data_mode_row">
                <td>Mobile Data mode</td>
                <td>
                    <select name="mobile_data_mode" style="$fieldStyle">
                        <option value="0" ${if (data.mobileDataMode == "0") "selected" else ""}>Auto</option>
                        <option value="2" ${if (data.mobileDataMode == "2" || data.mobileDataMode == null) "selected" else ""}>2G</option>
                        <option value="3" ${if (data.mobileDataMode == "3" || data.mobileDataMode == null) "selected" else ""}>3G</option>
                        <option value="4" ${if (data.mobileDataMode == "4" || data.mobileDataMode == null) "selected" else ""}>4G</option>
                    </select>
                </td>
            </tr>           
        </table>
    </div>

    <div class="section">
        <h2 class="section-title">Other settings</h2>
        <table class="form-table">
            <tr>
                <td>Activation</td>
                <td>
                    <select name="activation" style="$fieldStyle">
                        <option value="0" ${if (data.activation == "0") "selected" else ""}>Disable</option>
                        <option value="1" ${if (data.activation == "1" || data.activation == null) "selected" else ""}>Enable</option>
                    </select>
                </td>
            </tr>
            <tr><td>Normal Volume (0 - 100%)</td><td><input type="number" name="normal_volume" min="0" max="100" value="${data.normalVolume ?: "80"}" style="$fieldStyle"></td></tr>
            <tr><td>FM Volume (0 - 100%)</td><td><input type="number" name="fm_volume" min="0" max="100" value="${data.fmVolume ?: "65"}" style="$fieldStyle"></td></tr>
            <tr>
                <td>FM Auto Stop for weak signal</td>
                <td>
                    <select name="fm_auto_stop" style="$fieldStyle">
                        <option value="0" ${if (data.fmAutoStop == "0" || data.fmAutoStop == null) "selected" else ""}>Disable</option>
                        <option value="1" ${if (data.fmAutoStop == "1") "selected" else ""}>Enable</option>
                    </select>
                </td>
            </tr>
            <tr><td>FM Auto RSSI Threshold (0 - 100dBuV)</td><td><input type="number" name="fm_rssi_threshold" value="${data.fmAutoThreshold ?: "70"}" style="$fieldStyle"></td></tr>
        </table>
    </div>

    <div class="btn-row">
        <button class="btn primary" type="submit">Update</button>
        <button class="btn secondary" type="button" onclick="if(confirm('Khôi phục cấu hình về mặc định?')) { this.form.action='/default-settings'; this.form.submit(); }">Default</button>
    </div>

    <script>
        // Chuỗi chứa mã vẽ SVG cho 2 trạng thái
        var svgEyeOpen = '<svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#777" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"></path><circle cx="12" cy="12" r="3"></circle></svg>';
        var svgEyeClosed = '<svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#777" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"></path><line x1="1" y1="1" x2="23" y2="23"></line></svg>';

        function updateFieldVisibility() {
            var el = document.getElementById("network_type_select");
            if (!el) return;
            var val = el.value;

            var isWifi = (val === '2');
            document.getElementById('wifi_ssid_row').style.display = isWifi ? '' : 'none';
            document.getElementById('wifi_password_row').style.display = isWifi ? '' : 'none';

            var isMobile = (val === '1');
            document.getElementById('sim_selection_row').style.display = isMobile ? '' : 'none';
            document.getElementById('mobile_data_mode_row').style.display = isMobile ? '' : 'none';
        }

        function togglePasswordVisibility() {
            var input = document.getElementById("wifi_pwd_input");
            var iconContainer = document.getElementById("toggle_pwd_icon");
            
            // Thay vì đổi class name, ta thay thế mã HTML bên trong thẻ span
            if (input.type === "password") {
                input.type = "text";
                iconContainer.innerHTML = svgEyeClosed; // Hiện mật khẩu -> Icon nhắm mắt
            } else {
                input.type = "password";
                iconContainer.innerHTML = svgEyeOpen;   // Ẩn mật khẩu -> Icon mở mắt
            }
        }

        function refreshWifiList() {
            var el = document.getElementById("network_type_select");
            if (el && el.value !== '2') return;

            fetch('/api/wifi-list')
                .then(function(r) { return r.json(); })
                .then(function(ssids) {
                    var dl = document.getElementById('wifi_ssids');
                    if (!dl) return;
                    dl.innerHTML = '';
                    ssids.forEach(function(ssid) {
                        var opt = document.createElement('option');
                        opt.value = ssid;
                        dl.appendChild(opt);
                    });
                })
                .catch(function(e) { console.warn('wifi-list fetch error', e); });
        }

        window.addEventListener('load', function() {
            updateFieldVisibility();
            refreshWifiList();
            setInterval(refreshWifiList, 10000);
        });
    </script>
    """.trimIndent()

        return renderDashboardLayout(
            title = "Settings",
            activeMenu = "settings",
            content = "<form action='/update-settings' method='post'>$content</form>"
        )
    }

    fun renderAdminPage(): String {
        val content = """
        <div class="section">
            <h2 class="section-title">Security</h2>
            <form id="pw-form" action='/admin/change-password' method='post'>
                <table class="form-table">
                    <tr><td>Current password</td><td><input type="password" name="current_password" required/></td></tr>
                    <tr><td>New password</td><td><input type="password" name="new_password" required minlength="1" /></td></tr>
                    <tr><td>Confirm</td><td><input type="password" name="confirm_password" required minlength="1" /></td></tr>
                    <tr><td>Current PC time</td><td id="pc-time"></td></tr>
                </table>
            </form>
        </div>

        <div class="btn-row" style="margin-top: 15px; display: flex; gap: 10px; border-top: 1px solid #d9dde3; padding-top: 15px;">
            <button class="btn" style="background-color: #5cb85c; color: white; border: none;" type="button" onclick="document.getElementById('pw-form').submit();">Change Password</button>
            <form action='/admin/reboot-mira' method='post' style="margin:0;"><button class="btn" style="background-color: #5cb85c; color: white; border: none;" type="submit" onclick="return confirm('Bạn có chắc muốn Reboot MIRA?');">Reboot MIRA</button></form>
            <form action='/admin/restart-app' method='post' style="margin:0;"><button class="btn" style="background-color: #5cb85c; color: white; border: none;" type="submit" onclick="return confirm('Bạn có chắc muốn Restart App?');">Restart App</button></form>
            <form action='/admin/factory-reset' method='post' style="margin:0;"><button class="btn" style="background-color: #5cb85c; color: white; border: none;" type="submit" onclick="return confirm('Bạn có chắc muốn Factory Reset (Xóa mọi cài đặt và Reboot)?');">Factory Reset</button></form>
        </div>
        
        <script>
            function pad(v) { return String(v).padStart(2, '0'); }
            function updatePcTime() {
                const now = new Date();
                const text = now.getDate() + "/" + (now.getMonth() + 1) + "/" + now.getFullYear() + " " +
                             pad(now.getHours()) + ":" + pad(now.getMinutes()) + ":" + pad(now.getSeconds());
                const el = document.getElementById("pc-time");
                if (el) el.textContent = text;
            }
            updatePcTime();
            setInterval(updatePcTime, 1000);
        </script>
        """.trimIndent()

        return renderDashboardLayout("Admin", "admin", content)
    }

    fun renderIoCtPage(): String {
        val content = """
            <form action="/update-ioct" method="post">
                <div class="page-head">
                    <div>
                        <h1 class="page-title">IO-CT</h1>
                        <div class="page-desc">Cấu hình thiết bị ngoại vi và điều khiển I/O.</div>
                    </div>
                </div>

                <div class="section">
                    <h2 class="section-title">Input settings</h2>
                    <table class="form-table">
                        <tr><td>Input 1</td><td><div class="inline"><select name="input_1_mode"><option selected>NO</option><option>NC</option></select><label class="switch"><input checked type="checkbox" name="input_1_enable"><span class="slider"></span></label><span>Kích hoạt</span></div></td></tr>
                        <tr><td>Input 2</td><td><div class="inline"><select name="input_2_mode"><option>NO</option><option selected>NC</option></select><label class="switch"><input type="checkbox" name="input_2_enable"><span class="slider"></span></label><span>Tắt</span></div></td></tr>
                        <tr><td>Debounce</td><td><input type="number" name="debounce" value="150"></td></tr>
                    </table>
                </div>

                <div class="section">
                    <h2 class="section-title">Output settings</h2>
                    <table class="form-table">
                        <tr><td>Relay 1</td><td><div class="inline"><label class="switch"><input checked type="checkbox" name="relay_1"><span class="slider"></span></label><span>Enable</span></div></td></tr>
                        <tr><td>Relay 2</td><td><div class="inline"><label class="switch"><input type="checkbox" name="relay_2"><span class="slider"></span></label><span>Disable</span></div></td></tr>
                        <tr><td>Pulse time (ms)</td><td><input type="number" name="pulse_time" value="500"></td></tr>
                    </table>
                </div>

                <div class="btn-row">
                    <button class="btn primary" type="submit">Update</button>
                    <button class="btn secondary" type="button">Default</button>
                </div>
            </form>
        """.trimIndent()

        return renderDashboardLayout("IO-CT", "ioct", content)
    }


    fun renderMediaPage(
        mediaList: List<MediaInfo> = emptyList(),
        selectedMid: Long? = null,
        statusPlaying: Int = -1
    ): String {
        val selectedMedia =
            mediaList.firstOrNull { it.mid == selectedMid } ?: mediaList.firstOrNull()

        fun MediaInfo.modeText(): String = when (mode) {
            1 -> "Schedule"
            2 -> "Live"
            else -> "-"
        }

        fun MediaInfo.repeatText(): String = when (repeat) {
            0 -> "No repeat"
            1 -> "Daily"
            2 -> "Weekly"
            else -> "-"
        }

        fun actionText(action: Int?): String = when (action) {
            1 -> "Create"
            2 -> "Update"
            4 -> "Delete"
            8 -> "Play"
            16 -> "Stop"
            else -> "-"
        }

        fun fileTypeText(type: Int?): String = when (type) {
            0 -> "Silence"
            1 -> "Audio"
            2 -> "FM"
            3 -> "Stream URL"
            else -> "-"
        }

        fun formatUnixTime(seconds: Long?): String {
            if (seconds == null || seconds <= 0) return "-"
            return try {
                val formatter =
                    java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                formatter.format(java.util.Date(seconds * 1000L))
            } catch (_: Exception) {
                "-"
            }
        }

        fun activeDaysText(days: Int?): String {
            if (days == null || days == 0) return "-"
            val map = listOf("CN", "T2", "T3", "T4", "T5", "T6", "T7")
            val result = mutableListOf<String>()
            for (i in 0..6) {
                if (((days shr i) and 1) == 1) result.add(map[i])
            }
            return if (result.isEmpty()) "-" else result.joinToString(", ")
        }

//        fun playTimeInDayText(ts: List<Long>?, ds: List<Long>?): String {
//            if (ts.isNullOrEmpty()) return "-"
//            return ts.mapIndexed { index, value ->
//                val d = ds?.getOrNull(index)
//                if (d != null) "$value ($d)" else value.toString()
//            }.joinToString(", ")
//        }

        fun audioInformationText(files: List<MediaFile>): String {
            if (files.isEmpty()) return "-"
            return files.joinToString(" | ") { file ->
                buildString {
                    append(fileTypeText(file.type))
                    append(": ")
                    append(file.url ?: "-")
                    file.duration?.let { append(" (${it}s)") }
                }
            }
        }

//        val headerIds = mediaList.mapNotNull { it.mid }.joinToString(", ").ifBlank { "0" }

        val selectedOptions = buildString {
            mediaList.forEach { item ->
                val mid = item.mid ?: return@forEach
                val selected = if (selectedMedia?.mid == mid) "selected" else ""
                append("""<option value="$mid" $selected>$mid - ${item.summary ?: "No title"}</option>""")
            }
        }.ifBlank { """<option value="">No media</option>""" }

        val detail = selectedMedia
        val selectedMidValue = detail?.mid?.toString().orEmpty()

        val statusDisplay = when (statusPlaying) {
            1 -> "Normal"
            0 -> "Lỗi"
            2 -> "Đang chờ phát..."
            else -> actionText(detail?.action) // Trạng thái gốc ban đầu khi chưa ấn Play
        }

        val content = """
        <div class="section">
            <h2 class="section-title">Media storage</h2>
            <table class="form-table">             
                <tr>
                    <td>Select Media by ID</td>
                    <td>
                        <form method="get" action="/media" style="margin:0;">
                            <select name="mid" onchange="this.form.submit()" style="width: 280px;">
                                $selectedOptions
                            </select>
                        </form>
                    </td>
                </tr>
            </table>
        </div>

        <div class="section">
            <h2 class="section-title">Media details</h2>
            <table class="form-table">
                <tr><td>Mode</td><td>${detail?.modeText() ?: "-"}</td></tr>
                <tr><td>Priority</td><td>${detail?.prio ?: "-"}</td></tr>
                <tr><td>Created</td><td>${formatUnixTime(detail?.created)}</td></tr>
                <tr><td>Expired</td><td>${formatUnixTime(detail?.expired)}</td></tr>
                <tr><td>Start</td><td>${formatUnixTime(detail?.start)}</td></tr>
                <tr><td>Repeat type</td><td>${detail?.repeatText() ?: "-"}</td></tr>
                <tr><td>Active Days</td><td>${activeDaysText(detail?.days)}</td></tr>
                <tr><td>Audio information</td><td>${audioInformationText(detail?.files ?: emptyList())}</td></tr>
                <tr><td>Schedule duration</td><td>${detail?.ds}</td></tr>
                 <tr><td>Status</td><td id="media-status-cell">$statusDisplay</td></tr>
            </table>
        </div>

        <div class="section" style="margin-top: 28px;">
            <div style="border-top: 3px solid #2a8de0; padding-top: 18px;">
                <div style="display:flex; gap:10px; flex-wrap:wrap;">

                    <form method="post" action="/media/play" style="margin:0;">
                        <input type="hidden" name="mid" value="$selectedMidValue">
                        <button class="btn primary" type="submit">Play</button>
                    </form>

                    <form method="post" action="/media/stop" style="margin:0;">
                        <input type="hidden" name="mid" value="$selectedMidValue">
                        <button class="btn primary" type="submit">Stop</button>
                    </form>

                    <form method="post" action="/media/delete" style="margin:0;" onsubmit="return confirm('Xóa media đang chọn?');">
                        <input type="hidden" name="mid" value="$selectedMidValue">
                        <button class="btn primary" type="submit">Delete</button>
                    </form>

                    <form method="post" action="/media/delete-all" style="margin:0;" onsubmit="return confirm('Xóa toàn bộ media?');">
                        <button class="btn primary" type="submit">Delete All</button>
                    </form>

                </div>
            </div>
        </div>
        
        <script>
            function refreshMediaStatus() {
                fetch('/api/media-status')
                    .then(function(r) { return r.json(); })
                    .then(function(d) {
                        var cell = document.getElementById('media-status-cell');
                        if (cell) {
                            if (d.status === 1) cell.textContent = 'Normal';
                            else if (d.status === 0) cell.textContent = 'Lỗi';
                            else if (d.status === 2) cell.textContent = 'Đang chờ phát...';
                        }
                    })
                    .catch(function(e) { console.warn('media-status error', e); });
            }
            setInterval(refreshMediaStatus, 1500);
        </script>
    """.trimIndent()

        return renderDashboardLayout("Media", "media", content)
    }

    fun renderPlayerPage(): String {
        val content = """
        <div class="section">
            <h2 class="section-title">Playlist</h2>
            <table class="form-table" id="playlist-table">
                <thead>
                    <tr>
                        <th style="text-align:left;color:#555;font-weight:600;">Media ID</th>
                        <th style="text-align:left;color:#555;font-weight:600;">Cycle</th>
                        <th style="text-align:left;color:#555;font-weight:600;">File chunk</th>
                        <th style="text-align:left;color:#555;font-weight:600;">Playing time</th>
                    </tr>
                </thead>
                <tbody>
                    <tr>
                        <td id="pl-mediaid">-</td>
                        <td id="pl-cycle">-</td>
                        <td id="pl-chunk">-</td>
                        <td>
                            <div style="display:flex;align-items:center;gap:10px;">
                                <input type="range" id="pl-slider" min="0" max="100" value="0"
                                    style="width:200px;accent-color:#2a8de0;" readonly>
                                <span id="pl-time" style="white-space:nowrap;font-size:13px;color:#444;">0m00s/0m00s (0%)</span>
                            </div>
                        </td>
                    </tr>
                </tbody>
            </table>
        </div>

        <div class="section">
            <h2 class="section-title">Players status</h2>
            <table class="form-table">
                <tr>
                    <td style="color:#2a8de0;font-weight:600;">MPD</td>
                    <td id="st-mpd">OFF</td>
                </tr>
                <tr>
                    <td style="color:#2a8de0;font-weight:600;">FM</td>
                    <td id="st-fm">OFF</td>
                </tr>
                <tr>
                    <td style="color:#2a8de0;font-weight:600;">SILENCE</td>
                    <td id="st-silence">OFF</td>
                </tr>
                <tr>
                    <td style="color:#2a8de0;font-weight:600;">LIVE MIC</td>
                    <td id="st-livemic">-</td>
                </tr>
            </table>
        </div>

           <div class="btn-row" style="border-top:0;gap:10px;">
            <form method="post" action="/media/next" style="margin:0;">
                <button class="btn primary" type="submit">Next</button>
            </form>
            <form method="post" action="/media/delete-all" style="margin:0;" onsubmit="return confirm('Stop all và xóa toàn bộ media?');">
                <input type="hidden" name="from" value="player">
                <button class="btn secondary" type="submit">Stop All</button>
            </form>
        </div>

        <script>
            function refreshPlayerStatus() {
                fetch('/api/player-status')
                    .then(function(r) { return r.json(); })
                    .then(function(d) {
                        var isIdle = (d.mediaId === null);
                        document.getElementById('pl-mediaid').textContent = isIdle ? '-' : d.mediaId;
                        document.getElementById('pl-cycle').textContent = isIdle ? '-' : (d.cycle || '-');
                        document.getElementById('pl-chunk').textContent = isIdle ? '-' : (d.fileChunk || '-');

                        var pct = isIdle ? 0 : (parseFloat(d.pct) || 0);
                        document.getElementById('pl-slider').value = pct;
                        document.getElementById('pl-time').textContent = isIdle ? '-' :
                            (d.posFmt || '0m00s') + '/' + (d.durFmt || '0m00s') + ' (' + pct.toFixed(1) + '%)';

                        // Players status
                        document.getElementById('st-mpd').textContent = d.isMpd ? 'ON' : 'OFF';
                        var fmText = d.isFm ? ('ON, RSSI=' + d.fmRssi) : 'OFF';
                        document.getElementById('st-fm').textContent = fmText;
                        document.getElementById('st-silence').textContent = d.isSilence ? 'ON' : 'OFF';
                        document.getElementById('st-livemic').textContent = d.isLiveMic ? 'ON' : '-';
                    })
                    .catch(function(e) { console.warn('player-status error', e); });
            }

            refreshPlayerStatus();
            setInterval(refreshPlayerStatus, 1000);
        </script>
        """.trimIndent()

        return renderDashboardLayout("Player", "player", content)
    }

    fun renderStatusPage(statusData: StatusPageData): String {
        // <tr><td>LOG</td><td>MEDIA (Last Sent ID/Not Send count) = 319616/915929, USERLOG = 5/0</td></tr>
        val uptimeText =
            "App=${formatDuration(statusData.appUptime)} (System=${formatDuration(statusData.systemUptime)})"
        val fmPlayingText =
            if (statusData.isFmPlaying) "ON, RSSI=${statusData.fmRssi}" else "OFF, RSSI=${statusData.fmRssi}"
        val streamBackText = if (statusData.isStreamBack) "ON" else "OFF"

        fun spkStr(run: Boolean, err: Boolean) = if (run) {
            if (err) "ON/ERR" else "ON/OK"
        } else {
            if (err) "OFF/ERR" else "OFF/OK"
        }

        val spk1 = spkStr(statusData.spk1Run, statusData.spk1Err)
        val spk2 = spkStr(statusData.spk2Run, statusData.spk2Err)
        val spk3 = spkStr(statusData.spk3Run, statusData.spk3Err)
        val spk4 = spkStr(statusData.spk4Run, statusData.spk4Err)
        val serverStatus = if (statusData.isServerConnected) "Connected" else "Disconnect"

        val content = """
        <div class="section">
            <h2 class="section-title">Status</h2>
            <table class="form-table">
                <tr><td>Uptime</td><td>${uptimeText}</td></tr>
                <tr><td>Storage</td><td>total=${
            String.format(
                "%.1f", statusData.storageTotalMiB
            )
        }MiB, free=${String.format("%.1f", statusData.storageFreeMiB)}MiB</td></tr>
               
                <tr><td>Internet Connection</td><td>connection_type=${statusData.connectionType}, disconnection_cnt=${statusData.disconnectionCnt}</td></tr>
                <tr><td>Server</td><td>$serverStatus</td></tr>
                <tr><td>FM</td><td>$fmPlayingText</td></tr>
                <tr><td>Speakers</td><td>S1=$spk1, S2=$spk2, S3=$spk3, S4=$spk4</td></tr>
                <tr><td>Stream Back</td><td>$streamBackText</td></tr>
            </table>
        </div>

        <div class="section" style="margin-top: 28px;">
            <div style="border-top: 3px solid #2a8de0; padding-top: 12px;">
                <label for="auto_refresh" style="font-size: 16px; margin-right: 10px;">Auto refresh</label>
                <input
                    id="auto_refresh"
                    type="number"
                    value="10"
                    min="0"
                    style="width: 64px; padding: 4px 8px; font-size: 16px;"
                >
            </div>
        </div>

        <script>
            let refreshTimer = null;
            const STORAGE_KEY = "status_auto_refresh";

            function scheduleRefresh() {
                if (refreshTimer) {
                    clearTimeout(refreshTimer);
                    refreshTimer = null;
                }

                const input = document.getElementById("auto_refresh");
                if (!input) return;

                const rawValue = input.value || "0";
                const seconds = parseInt(rawValue, 10);

                localStorage.setItem(STORAGE_KEY, rawValue);

                if (!isNaN(seconds) && seconds > 0) {
                    refreshTimer = setTimeout(function() {
                        location.reload();
                    }, seconds * 1000);
                }
            }

            window.addEventListener("load", function() {
                const input = document.getElementById("auto_refresh");
                if (!input) return;

                const savedValue = localStorage.getItem(STORAGE_KEY);
                if (savedValue !== null && savedValue !== "") {
                    input.value = savedValue;
                }

                scheduleRefresh();

                input.addEventListener("input", scheduleRefresh);
                input.addEventListener("change", scheduleRefresh);
            });
        </script>
    """.trimIndent()

        return renderDashboardLayout("Status", "status", content)

    }

    fun render3G4GPage(mcuInfo: McuInfoData): String {
        val content = """
            <form action="/update-3g4g" method="post">      
                     <div class="section">
                <h2 class="section-title">Basic Information</h2>
                <table class="form-table">
                    <tr><td>Imei</td><td>${mcuInfo.id}</td></tr>
                    <tr><td>Model</td><td>${mcuInfo.vcode}</td></tr>
                    <tr><td>Revision</td><td>${mcuInfo.fvers}</td></tr>
                    <tr><td>Imsi</td><td>${mcuInfo.gsmImei}</td></tr>
                    <tr><td>Phone</td><td>${mcuInfo.gsmPhone}</td></tr>
                    <tr><td>Gsm status</td><td>${getStateMobileData().takeUnless { it.isBlank() } ?: "Sim Not Available"}</td></tr>
                    <tr><td>Data status</td><td>${getModeMobileDataNoConvert().ifBlank { "No Sim" }}</td></tr>                     
                </table>
            </div>
                  
           </form>
        """.trimIndent()

        return renderDashboardLayout("3G/4G", "mobile", content)
    }


    //   <tr><td>IP Wan</td><td>${NetworkUtils.getMobileLocalIp(context)}</td></tr>
    fun renderAboutPage(mcuInfo: McuInfoData, context: Context): String {
        val content = """
     
            <div class="section">
                <h2 class="section-title">System information</h2>
                <table class="form-table">
                    <tr><td>Id</td><td>${mcuInfo.id}</td></tr>
                    <tr><td>vcode</td><td>${mcuInfo.vcode}</td></tr>
                    <tr><td>fvers</td><td>${mcuInfo.fvers}</td></tr>
                    <tr><td>os_version</td><td>${mcuInfo.osVersion}</td></tr>
                    <tr><td>os_builddate</td><td>${mcuInfo.osBuilddate}</td></tr>
                    <tr><td>HW version</td><td>${mcuInfo.hwVersion}</td></tr>
                    <tr><td>sd_cid</td><td>${mcuInfo.sdCid}</td></tr>
                    <tr><td>fm</td><td>${mcuInfo.fm}</td></tr>
                    <tr><td>classd_version</td><td>${mcuInfo.classdVersion}</td></tr>
                    <tr><td>gsm_imei</td><td>${mcuInfo.gsmImei}</td></tr>
                    <tr><td>gsm_model</td><td>${mcuInfo.gsmModel}</td></tr>
                    <tr><td>gsm_revision</td><td>${mcuInfo.gsmRevision}</td></tr>
                    <tr><td>gsm_imsi</td><td>${mcuInfo.gsmImsi}</td></tr>
                    <tr><td>phone</td><td>${mcuInfo.gsmPhone}</td></tr>             
                </table>
            </div>

      
                <div class="section" >
                    <h2 class="section-title">WAN information</h2>
                    <table class="form-table">
                        <tr><td>IP Lan</td><td>${NetworkUtils.getEthernetLanIp(context)}</td></tr>                    
                    </table>
                </div>
           
                 <div class="section">
                    <h2 class="section-title">FM module</h2>
                    <table class="form-table">
                        <tr><td>Module</td><td>${mcuInfo.fm}</td></tr>
                    </table>
                </div>
        """.trimIndent()

        return renderDashboardLayout("About", "about", content)
    }

    fun renderDashboardLayout(
        title: String,
        activeMenu: String,
        content: String,
    ): String {
        fun menuClass(menu: String) = if (menu == activeMenu) "menu-item active" else "menu-item"/*                 <a href="/io-ct" class="${menuClass("ioct")}">IO-CT</a>

                                          <a href="/admin" class="${menuClass("admin")}">Admin</a>
                                                    <a href="/logout" class="menu-item logout">Đăng xuất</a>
                                            */
        return """
            <!DOCTYPE html>
            <html lang="vi">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>$title</title>
                <style>
                    ${commonStyles()}
                </style>
            </head>
            <body>
                <div class="app">
                    <div class="topbar">IoT Gateway</div>
                    <div class="shell">
                        <aside class="sidebar">
          
                            <a href="/config-connection" class="${menuClass("connection")}">Connection</a>                
                            <a href="/config-settings" class="${menuClass("settings")}">Settings</a>
                            <a href="/class-d" class="${menuClass("classd")}">Class D</a>
                            <a href="/media" class="${menuClass("media")}">Media</a>
                            <a href="/player" class="${menuClass("player")}">Player</a>
                            <a href="/status" class="${menuClass("status")}">Status</a>
                            <a href="/3g-4g" class="${menuClass("mobile")}">3G/4G</a>                    
                            <a href="/admin" class="${menuClass("admin")}">Admin</a>
                            <a href="/about" class="${menuClass("about")}">About</a>
                  
                        </aside>
                        <main class="content">
                            <section class="page active">
                                $content
                            </section>
                        </main>
                    </div>
                </div>
            </body>
            </html>
        """.trimIndent()
    }

    private fun commonStyles(): String = """
        :root {
            --green: #46a548;
            --green-dark: #2f8a31;
            --line: #2a8de0;
            --border: #d9dde3;
            --bg: #f4f6f8;
            --text: #1f2937;
            --muted: #6b7280;
            --danger: #c62828;
            --card: #ffffff;
            --shadow: 0 10px 30px rgba(15, 23, 42, 0.08);
            --radius: 14px;
        }

        * { box-sizing: border-box; }
        body {
            margin: 0;
            font-family: Arial, Helvetica, sans-serif;
            color: var(--text);
            background: var(--bg);
        }
        .app {
            min-height: 100vh;
            display: flex;
            flex-direction: column;
        }
        .topbar {
            background: var(--green);
            color: #fff;
            padding: 14px 22px;
            font-size: 22px;
            font-weight: 700;
            letter-spacing: .2px;
            box-shadow: 0 2px 10px rgba(0,0,0,.08);
        }
        .shell {
            display: grid;
            grid-template-columns: 220px 1fr;
            min-height: calc(100vh - 54px);
        }
        .sidebar {
            background: #fff;
            border-right: 1px solid var(--border);
            padding: 12px 0;
        }
        .menu-title {
            padding: 0 18px 10px;
            font-size: 12px;
            text-transform: uppercase;
            color: var(--muted);
            letter-spacing: .08em;
        }
        .menu-item {
            display: block;
            width: 100%;
            border: 0;
            background: transparent;
            text-align: left;
            padding: 11px 18px;
            font-size: 14px;
            color: #374151;
            cursor: pointer;
            transition: .2s ease;
            border-left: 3px solid transparent;
            text-decoration: none;
        }
        .menu-item:hover {
            background: #f6f9fb;
            color: #111827;
        }
        .menu-item.active {
            background: #edf8ee;
            color: var(--green-dark);
            border-left-color: var(--green);
            font-weight: 700;
        }
        .menu-item.logout {
            color: var(--danger);
            margin-top: 12px;
        }
        .content {
            padding: 22px;
        }
        .page {
            background: var(--card);
            border-radius: var(--radius);
            box-shadow: var(--shadow);
            padding: 22px;
        }
        .page-head {
            display: flex;
            align-items: center;
            justify-content: space-between;
            gap: 16px;
            margin-bottom: 18px;
        }
        .page-title {
            margin: 0;
            font-size: 22px;
            font-weight: 700;
        }
        .page-desc {
            color: var(--muted);
            font-size: 14px;
            margin-top: 6px;
        }
        .status-pill {
            display: inline-flex;
            align-items: center;
            gap: 8px;
            background: #edf8ee;
            color: var(--green-dark);
            border: 1px solid #cce8ce;
            padding: 8px 12px;
            border-radius: 999px;
            font-size: 13px;
            font-weight: 700;
            white-space: nowrap;
        }
        .status-dot {
            width: 8px;
            height: 8px;
            border-radius: 50%;
            background: var(--green);
        }
        .section { margin-top: 18px; }
        .section-title {
            margin: 0 0 12px;
            padding-bottom: 8px;
            border-bottom: 2px solid var(--line);
            font-size: 16px;
            font-weight: 700;
            color: #111827;
        }
        .grid-2, .grid-3, .cards {
            display: grid;
            gap: 18px;
        }
        .grid-2 { grid-template-columns: repeat(2, minmax(0, 1fr)); }
        .grid-3 { grid-template-columns: repeat(3, minmax(0, 1fr)); }
        .cards { grid-template-columns: repeat(4, minmax(0, 1fr)); }
        .info-card {
            border: 1px solid var(--border);
            border-radius: 12px;
            padding: 16px;
            background: #fff;
        }
        .metric {
            font-size: 28px;
            font-weight: 700;
            margin: 4px 0 0;
        }
        .metric-label {
            font-size: 13px;
            color: var(--muted);
        }
        .form-table {
            width: 100%;
            border-collapse: collapse;
            font-size: 14px;
        }
        .form-table td, .form-table th {
            padding: 10px 8px;
            border-bottom: 1px solid #edf0f3;
            vertical-align: middle;
        }
        .form-table td:first-child {
            width: 32%;
            color: #4b5563;
            font-weight: 600;
        }
        input[type="text"],
        input[type="number"],
        input[type="password"],
        select,
        textarea {
            width: 100%;
            border: 1px solid #cfd6de;
            border-radius: 8px;
            padding: 9px 11px;
            font-size: 14px;
            outline: none;
            background: #fff;
            transition: .2s ease;
        }
        input:focus, select:focus, textarea:focus {
            border-color: var(--line);
            box-shadow: 0 0 0 3px rgba(42, 141, 224, .12);
        }
        .inline {
            display: flex;
            align-items: center;
            gap: 10px;
            flex-wrap: wrap;
        }
        .switch {
            position: relative;
            width: 48px;
            height: 28px;
            display: inline-block;
        }
        .switch input { display: none; }
        .slider {
            position: absolute;
            inset: 0;
            background: #cfd6de;
            border-radius: 999px;
            transition: .2s ease;
            cursor: pointer;
        }
        .slider::before {
            content: "";
            position: absolute;
            width: 22px;
            height: 22px;
            left: 3px;
            top: 3px;
            background: white;
            border-radius: 50%;
            transition: .2s ease;
            box-shadow: 0 1px 3px rgba(0,0,0,.18);
        }
        .switch input:checked + .slider { background: var(--green); }
        .switch input:checked + .slider::before { transform: translateX(20px); }
        .btn-row {
            display: flex;
            gap: 10px;
            flex-wrap: wrap;
            margin-top: 20px;
            padding-top: 18px;
            border-top: 1px solid #e8edf2;
        }
        .btn {
            border: 0;
            border-radius: 9px;
            padding: 10px 16px;
            font-size: 14px;
            font-weight: 700;
            cursor: pointer;
            transition: .2s ease;
        }
        .btn.primary { background: var(--green); color: #fff; }
        .btn.secondary { background: #eef3f8; color: #334155; }
        .btn.danger { background: #d93025; color: #fff; }
        .tag {
            display: inline-flex;
            align-items: center;
            border-radius: 999px;
            padding: 4px 10px;
            font-size: 12px;
            font-weight: 700;
        }
        .tag.ok { background: #edf8ee; color: var(--green-dark); }
        .tag.warn { background: #fff5e8; color: #b45309; }
        .tag.off { background: #f3f4f6; color: #6b7280; }
        .list-box {
            border: 1px solid var(--border);
            border-radius: 12px;
            overflow: hidden;
            background: #fff;
        }
        .list-row {
            display: grid;
            grid-template-columns: 1.2fr 100px 120px 1fr;
            gap: 12px;
            padding: 12px 14px;
            border-bottom: 1px solid #eef2f6;
            font-size: 14px;
            align-items: center;
        }
        .list-row.head {
            background: #f8fafc;
            font-weight: 700;
            color: #111827;
        }
        .list-row:last-child { border-bottom: 0; }
        .mono {
            font-family: "Courier New", monospace;
            font-size: 13px;
        }
        .split {
            display: grid;
            grid-template-columns: 1.2fr .8fr;
            gap: 18px;
        }
        .note {
            padding: 12px 14px;
            border-radius: 10px;
            background: #f8fafc;
            border: 1px dashed #cbd5e1;
            color: #475569;
            font-size: 13px;
            line-height: 1.5;
        }
        @media (max-width: 1100px) {
            .grid-3, .cards, .split { grid-template-columns: 1fr; }
            .grid-2 { grid-template-columns: 1fr; }
            .list-row { grid-template-columns: 1fr; }
        }
        @media (max-width: 800px) {
            .shell { grid-template-columns: 1fr; }
            .sidebar {
                border-right: 0;
                border-bottom: 1px solid var(--border);
                display: grid;
                grid-template-columns: repeat(3, minmax(0, 1fr));
                gap: 6px;
                padding: 12px;
            }
            .menu-title { display: none; }
            .menu-item {
                border-left: 0;
                border-radius: 8px;
                background: #f8fafc;
                padding: 10px 12px;
            }
            .menu-item.active { background: #edf8ee; }
            .content { padding: 14px; }
            .page { padding: 16px; }
            .page-head { flex-direction: column; align-items: flex-start; }
        }
    """.trimIndent()

    fun renderClassDPage(model: ClassDUiModel, errorMessage: String? = null): String {
        // Để cho các input đẹp và đồng bộ, bạn có thể tái sử dụng fieldStyle nhé,
        // ở đây tôi giữ nguyên cấu trúc cũ của bạn và cập nhật phần nút bấm.

        val errorHtml = if (errorMessage != null) {
            """
            <script>
                alert("${errorMessage.replace("<br>", "\\n")}");
            </script>
            <div style="background-color: #ffebee; border-left: 5px solid #f44336; padding: 15px; margin-bottom: 20px; color: #b71c1c; border-radius: 4px;">
                <strong>Lỗi thực thi lệnh Serial:</strong><br>$errorMessage
            </div>
            """.trimIndent()
        } else ""

        val content = """
    $errorHtml
    <div class="section">
        <h2 class="section-title">ClassD status</h2>
        <table class="form-table">
            <tr><td>Monitor Channel</td><td>0</td></tr>
            <tr><td>faultsz (left, right)</td><td>0,0</td></tr>
            <tr><td>szd (left, right)</td><td>(${model.szdLeft}, ${model.szdRight})</td></tr>
            <tr><td>mute (left, right)</td><td>${model.muteLeft},${model.muteRight}</td></tr>
            <tr><td>Threshold</td><td>${model.threshold}</td></tr>
            <tr><td>Speaker 1 (status, alarm, adc, offset)</td><td>0,0,0,${model.speaker1Offset}</td></tr>
            <tr><td>Speaker 2 (status, alarm, adc, offset)</td><td>0,0,0,${model.speaker2Offset}</td></tr>
            <tr><td>Speaker 3 (status, alarm, adc, offset)</td><td>0,0,0,${model.speaker3Offset}</td></tr>
            <tr><td>Speaker 4 (status, alarm, adc, offset)</td><td>0,0,0,${model.speaker4Offset}</td></tr>
        </table>
    </div>

    <div class="section">
        <h2 class="section-title">ClassD settings</h2>
        <form action="/update-classd" method="post">
            <table class="form-table">
                <tr>
                    <td>Monitor Channel</td>
                    <td>
                        <select name="monitor_channel" style="max-width: 320px;">
                            <option ${if (model.monitorChannel == "CH 1-2") "selected" else ""}>CH 1-2</option>
                            <option ${if (model.monitorChannel == "CH 2-4") "selected" else ""}>CH 2-4</option>                            
                        </select>
                    </td>
                </tr>

                <tr>
                    <td>szd (left, right)</td>
                    <td>
                        <div style="display:flex; gap:10px; max-width: 320px;">
                            <input type="number" name="szd_left" value="${model.szdLeft}" min="0" max="1">
                            <input type="number" name="szd_right" value="${model.szdRight}" min="0" max="1">
                        </div>
                    </td>
                </tr>

                <tr>
                    <td>mute (left, right)</td>
                    <td>
                        <div style="display:flex; gap:10px; max-width: 320px;">
                            <input type="number" name="mute_left" value="${model.muteLeft}" min="0" max="1">
                            <input type="number" name="mute_right" value="${model.muteRight}" min="0" max="1">
                        </div>
                    </td>
                </tr>

                <tr>
                    <td>Threshold</td>
                    <td><input type="number" name="threshold" value="${model.threshold}" min="0" style="max-width: 320px;"></td>
                </tr>

                <tr>
                    <td>Speaker 1 Offset</td>
                    <td><input type="number" name="speaker1_offset" value="${model.speaker1Offset}" min="0" style="max-width: 320px;"></td>
                </tr>

                <tr>
                    <td>Speaker 2 Offset</td>
                    <td><input type="number" name="speaker2_offset" value="${model.speaker2Offset}" min="0" style="max-width: 320px;"></td>
                </tr>

                <tr>
                    <td>Speaker 3 Offset</td>
                    <td><input type="number" name="speaker3_offset" value="${model.speaker3Offset}" min="0" style="max-width: 320px;"></td>
                </tr>

                <tr>
                    <td>Speaker 4 Offset</td>
                    <td><input type="number" name="speaker4_offset" value="${model.speaker4Offset}" min="0" style="max-width: 320px;"></td>
                </tr>
            </table>

            <div class="btn-row" style="border-top: 0; padding-top: 16px;">
                <button class="btn primary" type="submit">Update</button>
                <button class="btn secondary" type="button" onclick="if(confirm('Khôi phục cài đặt Class D về mặc định?')) { this.form.action='/default-classd'; this.form.submit(); }">Default</button>
            </div>
        </form>
    </div>
    """.trimIndent()

        return renderDashboardLayout("Class D", "classd", content)
    }

}
