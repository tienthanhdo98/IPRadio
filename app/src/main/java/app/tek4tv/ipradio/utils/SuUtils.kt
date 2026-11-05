package app.tek4tv.ipradio.utils

import android.util.Log
import java.io.DataOutputStream
import java.io.IOException

fun execSu(command: String): Int {
    return try {
        val process = Runtime.getRuntime().exec("su")
        DataOutputStream(process.outputStream).use { os ->
            os.writeBytes(command + "\n")
            os.writeBytes("exit\n")
            os.flush()
        }
        process.waitFor()
    } catch (e: Exception) {
        Log.e("execSu", "Error: ${e.message}", e)
        -1
    }
}

fun reboot() {
    try {
        val proc = Runtime.getRuntime().exec(arrayOf("su", "0", "reboot"))
        proc.waitFor()
    } catch (e: Exception) {
        Log.e("reboot", "reboot error: ${e.message}")
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

@Throws(java.lang.Exception::class)
fun setWifeDataEnabled(enableOrDisable: Boolean) {
    val command: String = if (enableOrDisable) {
        "svc wifi enable"
    } else {
        "svc wifi disable"
    }
    executeCommandViaSu(command)
}


private fun executeCommandViaSu(command: String) {
    var success = false
    var su = "su"
    for (i in 0..2) {
        // Default "su" command executed successfully, then quit.
        if (success) {
            break
        }
        // Else, execute other "su" commands.
        if (i == 1) {
            su = "/system/xbin/su"
        } else if (i == 2) {
            su = "/system/bin/su"
        }
        try {
            // Execute command as "su".
            Runtime.getRuntime().exec(arrayOf(su, "-c", command))
            Log.d("PingCountercheck", "run adb: ok")
        } catch (e: IOException) {
            success = false
            // Oops! Cannot execute `su` for some reason.
            // Log error here.
            Log.d("PingCountercheck", "run adb: fail ${e.message}")
        } finally {
            success = true
        }
    }
}
