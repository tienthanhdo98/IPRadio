package app.tek4tv.ipradio.utils

import android.util.Log
import java.io.DataOutputStream

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