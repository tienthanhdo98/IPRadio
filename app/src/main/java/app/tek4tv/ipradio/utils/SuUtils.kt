package app.tek4tv.ipradio.utils

import android.content.Context
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
    var command: String? = null
    command = if (enableOrDisable) {
        "svc data enable"
    } else {
        "svc data disable"
    }
    executeCommandViaSu("-c", command)
}
private fun executeCommandViaSu( option: String, command: String) {
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
            Runtime.getRuntime().exec(arrayOf(su, option, command))
        } catch (e: IOException) {
            success = false
            // Oops! Cannot execute `su` for some reason.
            // Log error here.
        } finally {
            success = true
        }
    }
}