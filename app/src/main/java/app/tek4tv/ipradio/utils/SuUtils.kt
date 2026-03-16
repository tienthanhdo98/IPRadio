package app.tek4tv.ipradio.utils

import android.util.Log
import java.io.DataOutputStream
import java.io.IOException

fun execSu(command: String) {
    try {
        val process = Runtime.getRuntime().exec("su")
        val os = DataOutputStream(process.outputStream)
        os.writeBytes(command)
        os.flush()
        os.writeBytes("exit\n")
        os.flush()
        process.waitFor()
        os.close()
    } catch (e: Exception) {
        Log.e("execSu", e.message ?: "")
        e.printStackTrace()
    }
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
        "p link set $eth up"
    } else {
        "p link set $eth down"
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