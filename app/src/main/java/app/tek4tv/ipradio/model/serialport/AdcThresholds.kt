package app.tek4tv.ipradio.model.serialport

import app.tek4tv.ipradio.utils.GlobalProperties

data class AdcThresholds(
    val ch1: Int,
    val ch2: Int,
    val ch3: Int,
    val ch4: Int
)

fun parseAdcThresholds(data: String): AdcThresholds {
    val values = data.split("-").map { it.trim().toIntOrNull() }

    require(values.size == 4 && values.all { it != null }) {
        "Dữ liệu không hợp lệ. Ví dụ đúng: 16-14-14-18"
    }

    return AdcThresholds(
        ch1 = values[0]!!,
        ch2 = values[1]!!,
        ch3 = values[2]!!,
        ch4 = values[3]!!
    )
}

