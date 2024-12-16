import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import javax.crypto.spec.IvParameterSpec

// AES Encryption
fun encrypt(plainBytes: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
    val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding") // Use PKCS5Padding
    val secretKey = SecretKeySpec(key, "AES")
    val ivSpec = IvParameterSpec(iv)
    cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec)
    return cipher.doFinal(plainBytes)
}

// AES Decryption
fun decrypt(cipherBytes: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
    val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding") // Use PKCS5Padding
    val secretKey = SecretKeySpec(key, "AES")
    val ivSpec = IvParameterSpec(iv)
    cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec)
    return cipher.doFinal(cipherBytes)
}