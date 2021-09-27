package app.tek4tv.ipradio.utils.crypto

import com.google.android.gms.common.util.Base64Utils


fun b64Encode(data: String): String {
    val byteArray = data.toByteArray()
    return Base64Utils.encode(byteArray)
}

fun b64Decode(data: String): String {
    return String(Base64Utils.decode(data))
}