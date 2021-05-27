package app.tek4tv.ipradio.utils

import android.util.Base64
import java.security.*
import java.security.spec.InvalidKeySpecException
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher

fun encryptData(text: String, publicKeyStr: String): String? {
    return try {
        //val data = Base64.decode(text, Base64.DEFAULT)
        val publicKey = getPublicKey(Base64.decode(publicKeyStr.toByteArray(Charsets.US_ASCII), Base64.DEFAULT))
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, publicKey)
        //val restult = String(cipher.doFinal(data), Charsets.US_ASCII)
        String(Base64.encode(cipher.doFinal(text.toByteArray()), Base64.DEFAULT))
    } catch (e: java.lang.Exception) {
        e.printStackTrace()
        null
    }
}

fun decryptData(text: String?, pri_key: String): String? {
    return try {
        val data = Base64.decode(text, Base64.DEFAULT)
        val privateKey =
            getPrivateKey(Base64.decode(pri_key.toByteArray(Charsets.US_ASCII), Base64.DEFAULT))
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.DECRYPT_MODE, privateKey)
        String(cipher.doFinal(data), Charsets.US_ASCII)
    } catch (e: java.lang.Exception) {
        e.printStackTrace()
        null
    }
}
@Throws(NoSuchAlgorithmException::class, InvalidKeySpecException::class)
fun getPrivateKey(keyBytes: ByteArray?): PrivateKey? {
    val keySpec = PKCS8EncodedKeySpec(keyBytes)
    val keyFactory = KeyFactory.getInstance("RSA", "BC")
    return keyFactory.generatePrivate(keySpec)
}

@Throws(NoSuchAlgorithmException::class, InvalidKeySpecException::class)
fun getPublicKey(keyBytes: ByteArray?): PublicKey? {
    val keySpec = X509EncodedKeySpec(keyBytes)
    val keyFactory = KeyFactory.getInstance("RSA","BC")
    return keyFactory.generatePublic(keySpec)
}