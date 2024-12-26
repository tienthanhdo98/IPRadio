package app.tek4tv.ipradio.utils.crypto

import android.os.Build
import android.util.Base64.DEFAULT
import android.util.Base64.encodeToString
import androidx.annotation.RequiresApi
import java.util.*
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import android.util.Base64
import android.util.Log
import javax.crypto.BadPaddingException

//NoPadding
//ZeroBytePadding
@RequiresApi(Build.VERSION_CODES.O)
fun encrypt(plainBytes: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
    val cipher = Cipher.getInstance("AES/CBC/NoPadding")
    val secretKeySpec = SecretKeySpec(key, "AES")
    val ivParameterSpec = IvParameterSpec(iv)
    cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, ivParameterSpec)
    return cipher.doFinal(plainBytes)
}

@RequiresApi(Build.VERSION_CODES.O)
fun decrypt(cipherBytes: ByteArray, key: ByteArray, iv: ByteArray): ByteArray? {
//    try {
        val cipher = Cipher.getInstance("AES/CBC/NoPadding")
        val secretKeySpec = SecretKeySpec(key, "AES")
        val ivParameterSpec = IvParameterSpec(iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, ivParameterSpec)
        return cipher.doFinal(cipherBytes)
//    }catch (e : Exception){
//        Log.d("decrypt", "decrypt: ${e.message}")
//        return null
//    }
}

//
// fun decrypt(dataToDecrypt: ByteArray,key: ByteArray,iv: ByteArray): ByteArray {
//    val cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING")
//
//    cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key,"AES"), IvParameterSpec(iv))
//    val cipherText = cipher.doFinal(dataToDecrypt)
//
//    return cipher.doFinal(cipherText)
//}