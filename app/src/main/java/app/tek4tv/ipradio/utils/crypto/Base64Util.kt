package app.tek4tv.ipradio.utils.crypto

import com.google.android.gms.common.util.Base64Utils

class Base64Util {
    fun encode(data: String ) : String {
        val byteArray = data.toByteArray()
        return Base64Utils.encode(byteArray)
    }

    fun decode(data: String ) : String {
        return String(Base64Utils.decode(data))
    }
}