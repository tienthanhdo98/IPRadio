package app.tek4tv.ipradio.core

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun Context.saveMessageToFile(message: String) {
    try {
        // Thư mục riêng của app
        val dir = File(filesDir, "messages")
        if (!dir.exists()) {
            dir.mkdirs()
        }

        // Lưu tất cả vào 1 file log
        val file = File(dir, "messages_log.txt")

        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            .format(Date())

        val content = "[$time] $message\n"

        file.appendText(content)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

fun Context.saveEachMessageAsFile(message: String) {
    try {
        val dir = File(filesDir, "messages")
        if (!dir.exists()) {
            dir.mkdirs()
        }

        val fileName = "msg_${System.currentTimeMillis()}.txt"
        val file = File(dir, fileName)
        file.writeText(message)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}