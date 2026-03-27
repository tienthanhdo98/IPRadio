package app.tek4tv.ipradio.model

import android.util.Log

data class DeviceStatusResponse(
    val status: Int,
    val functionId: Int,
    val fmAmMode: Int,
    val fmFrequency: Int,
    val amFrequency: Int,
    val volume: Int,
    val sourceAudio: Int,
    val paStatus: Int,
    val muteStatus: Int,
    val externalMic: Int,
    val temperature: Int,
    val piCode: Int,
    val psName: String,
    val adcRealtime: Int,
    val spa: Int,
    val audioStatus: List<Int>,
    val errorStatus: List<Int>,
    val acDetect: Int,
    val streamback: Int
) {
    fun fmFrequencyMHz(): Float = fmFrequency / 10f
    fun isFm(): Boolean = fmAmMode == 1
    fun isMute(): Boolean = muteStatus == 1
    fun isPaOn(): Boolean = paStatus == 1
}

fun findIndex(input: Int, list: List<Int>?): Int? {
    if (list == null) return null

    val candidates = when (input) {
        0 -> listOf(0, 2)
        1 -> listOf(1, 3)
        else -> return null
    }

    val found = candidates.firstOrNull { index ->
        list.getOrNull(index) == 1
    }

    return found?.plus(1) // trả về index đếm từ 1
}

fun parseDeviceStatusResponse(raw: String): DeviceStatusResponse? {
    return try {
        val clean = raw
            .removePrefix("$$,")
            .removeSuffix("\r\n")
            .trim()

        val parts = clean.split(",")

        if (parts.size < 19) return null
        if (parts[1] != "8") return null

        DeviceStatusResponse(
            status = parts[0].toIntOrNull() ?: return null,
            functionId = parts[1].toIntOrNull() ?: return null,
            fmAmMode = parts[2].toIntOrNull() ?: 0,
            fmFrequency = parts[3].toIntOrNull() ?: 0,
            amFrequency = parts[4].toIntOrNull() ?: 0,
            volume = parts[5].toIntOrNull() ?: 0,
            sourceAudio = parts[6].toIntOrNull() ?: 0,
            paStatus = parts[7].toIntOrNull() ?: 0,
            muteStatus = parts[8].toIntOrNull() ?: 0,
            externalMic = parts[9].toIntOrNull() ?: 0,
            temperature = parts[10].toIntOrNull() ?: 0,
            piCode = parts[11].toIntOrNull() ?: 0,
            psName = parts[12],
            adcRealtime = parts[13].toIntOrNull() ?: 0,
            spa = parts[14].toIntOrNull() ?: 0,
            audioStatus = parts[15].split("-").mapNotNull { it.toIntOrNull() },
            errorStatus = parts[16].split("-").mapNotNull { it.toIntOrNull() },
            acDetect = parts[17].toIntOrNull() ?: 0,
            streamback = parts[18].toIntOrNull() ?: 0
        )
    } catch (e: Exception) {
        Log.e("serrialinfo", "parseDeviceStatusResponse error: ${e.message}", e)
        null
    }
}