package app.tek4tv.ipradio.utils.crypto

import kotlin.experimental.xor


fun xorString(byteArray: ByteArray, key: String): ByteArray {
    return try {
        byteArray.mapIndexed { index, byte -> byte.xor(key[index % key.length].code.toByte()) }
            .toByteArray()
    } catch (e: Exception) {
        e.printStackTrace()
        byteArrayOf()
    }
}