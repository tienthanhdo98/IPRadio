package app.tek4tv.ipradio.utils

import android.util.Log
import java.io.DataOutputStream
import java.io.IOException

fun execSu(command: String): Int {
    return try {
        val process = Runtime.getRuntime().exec("su")
        DataOutputStream(process.outputStream).use { os ->
            os.writeBytes(command + "\n")  // <-- quan trọng: thêm \n
            os.writeBytes("exit\n")
            os.flush()
        }
        process.waitFor()
    } catch (e: Exception) {
        Log.e("execSu", "Error: ${e.message}", e)
        -1
    }
}

//fun setAirplaneModeEnable(enableOrDisable: Boolean) {
//    val command: String = if (enableOrDisable) {
//        "settings put global airplane_mode_on 1"
//    } else {
//        "settings put global airplane_mode_on 0"
//    }
//    executeCommandViaSu(command)
//}
fun setAirplaneModeEnable(enableOrDisable: Boolean) {
    val command: String = if (enableOrDisable) {
        "cmd connectivity airplane-mode enable"
    } else {
        "cmd connectivity airplane-mode disable"
    }
    executeCommandViaSu(command)
}

@Throws(java.lang.Exception::class)
fun setMobileDataEnabled(enableOrDisable: Boolean) {
    val command: String = if (enableOrDisable) {
        "svc data enable"
    } else {
        "svc data disable"
    }
    executeCommandViaSu(command)
}

fun getStateMobileData(): String {
    val command = "getprop gsm.sim.state"
    val result = run(command)
    return result
}

fun getModeMobileData(): Int {
    try {
        // Chạy lệnh ADB shell
        val command = "getprop gsm.network.type"
        val result = run(command) // Hàm run() của bạn

        // Convert chuỗi kết quả sang số
        return convertMobileDataToInt(result)

    } catch (e: Exception) {
        Log.e("Network", "Lỗi lấy trạng thái mạng: ${e.message}")
        return -1 // Lỗi
    }
}

fun getModeMobileDataNoConvert(): String {
    return try {
        val result = run("getprop gsm.network.type")
        val type = result.split(",").firstOrNull().orEmpty().trim().uppercase()

        when {
            type.isBlank() -> ""
            type == "UNKNOWN" -> ""
            type == "NONE" -> ""
            else -> type
        }
    } catch (e: Exception) {
        Log.e("Network", "Lỗi lấy trạng thái mạng: ${e.message}")
        ""
    }
}

fun getEnableMobileData(): String {
    val command = "settings get global mobile_data"
    val result = run(command)
    return result
}

private fun detectEthernetInterface(): String? {
    val candidates = listOf("eth0", "eth1")
    for (iface in candidates) {
        try {
            val command = "ip link show $iface"
            val result = run(command)
            if (result.contains(iface)) {
                return iface
            }
        } catch (_: Exception) {
        }
    }
    return null
}


fun setEthernetEnabled(enableOrDisable: Boolean) {
    val eth = detectEthernetInterface() ?: return
    val command: String = if (enableOrDisable) {
        "ip link set $eth up"
    } else {
        "ip link set $eth down"
    }
    executeCommandViaSu(command)
}

private object RadioMode {
    const val AUTO = 0
    const val G2 = 2
    const val G3 = 3
    const val G4 = 4
}

private fun appModeToAndroidMode(appMode: Int): Int {
    return when (appMode) {
        RadioMode.AUTO -> 3    // GSM/WCDMA auto
        RadioMode.G2 -> 1    // GSM only
        RadioMode.G3 -> 2    // WCDMA only
        RadioMode.G4 -> 11   // LTE only
        else -> 3
    }
}

fun setPreferredMode(appMode: Int): Boolean {
    return try {
        val androidMode = appModeToAndroidMode(appMode)

        run("settings put global preferred_network_mode $androidMode")
        run("settings put global preferred_network_mode0 $androidMode")
        run("settings put global preferred_network_mode1 $androidMode")

        run("svc data disable")
        run("svc data enable")

        Log.d("NetworkSwitch", "setPreferredMode ok: appMode=$appMode androidMode=$androidMode")
        true
    } catch (e: Exception) {
        Log.e("NetworkSwitch", "setPreferredMode fail: ${e.message}", e)
        false
    }
}

@Throws(java.lang.Exception::class)
fun setWifiDataEnabled(enableOrDisable: Boolean) {
    val command: String = if (enableOrDisable) {
        "svc wifi enable"
    } else {
        "svc wifi disable"
    }
    executeCommandViaSu(command)
}

fun run(command: String): String {
    val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
    val stdout = process.inputStream.bufferedReader().use { it.readText() }
    val stderr = process.errorStream.bufferedReader().use { it.readText() }
    val exitCode = process.waitFor()

    if (exitCode != 0) {
        throw RuntimeException("Command failed: $command\n$stderr")
    }
    return stdout
}

private fun executeCommandViaSu(command: String): Boolean {
    val suCandidates = listOf(
        "su", "/system/xbin/su", "/system/bin/su"
    )

    for (su in suCandidates) {
        try {
            val process = Runtime.getRuntime().exec(arrayOf(su, "-c", command))

            val stdout = process.inputStream.bufferedReader().use { it.readText() }
            val stderr = process.errorStream.bufferedReader().use { it.readText() }
            val exitCode = process.waitFor()

            if (exitCode == 0) {
                Log.d("PingCountercheck", "run su ok: $su, out=$stdout")
                return true
            } else {
                Log.e("PingCountercheck", "run su fail: $su, exit=$exitCode, err=$stderr")
            }
        } catch (e: Exception) {
            Log.e("PingCountercheck", "run su exception: $su, ${e.message}", e)
        }
    }

    return false
}

//>