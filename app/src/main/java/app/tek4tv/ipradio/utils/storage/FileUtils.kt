package app.tek4tv.ipradio.utils.storage

import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import app.tek4tv.ipradio.utils.crypto.decrypt

import app.tek4tv.ipradio.utils.crypto.encrypt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.*
//
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption


const val PLAYLIST_FILE_NAME = "playlist.json"
const val AUDIO_LIST_FILE_NAME = "audio_list.json"
const val AUDIO_FOLDER_NAME = "audios"
const val RECORDED_AUDIO_FOLDER_NAME = "recorded"


fun isFileExisted(storagePath: String, fileName: String): Boolean {
    val file = File(storagePath, fileName)
    return file.exists() && file.isFile
}


@Throws(IOException::class)
suspend fun readFile(filePath: String): String {
    return withContext(Dispatchers.IO)
    {
        val br = BufferedReader(FileReader(filePath))
        val res = br.lineSequence().toList().joinToString(separator = "")
        br.close()
        res
    }
}

@Throws(IOException::class)
suspend fun writeFile(filePath: String, content: String) {
    withContext(Dispatchers.IO)
    {
        val bw = BufferedWriter(FileWriter(filePath))
        bw.write(content)
        bw.close()
    }
}

fun createFolder(storagePath: String): Boolean {
    val folder = File(storagePath)
    var success = false

    if (!folder.exists()) {
        success = folder.mkdir()
    }

    return success
}

fun isFolderExisted(storagePath: String): Boolean {
    val folder = File(storagePath)
    return folder.exists() && folder.isDirectory
}



fun getAllFileNameInFolder(storagePath: String): List<String> {
    return try {
        Log.d("filex",storagePath)
        val folder = File(storagePath)
        val audio = listOf("mp3")
        folder.listFiles().filter { file -> file.isFile && audio.any { file.name.endsWith(it) } }
            .map { it.name }
    } catch (e: Exception) {
        e.printStackTrace()
        listOf()
    }

}

fun getAllFilePathInFolder(storagePath: String): List<String> {
    val folder = File(storagePath)
    return folder.listFiles().filter { it.isFile }.map { it.path }
}

fun getFolderSize(path: String): Long {
    val folder = File(path)

    return try {
        var size = 0L

        for (f in folder.listFiles()) {
            if (f.isFile) size += f.length()
        }

        size
    } catch (e: Exception) {
        e.printStackTrace()
        -1L
    }
}

fun deleteAllFileInFolder(path: String) {
    val folder = File(path)
    try {
        for (f in folder.listFiles()) {
            if (f.isFile) f.delete()
        }

    } catch (e: Exception) {
        e.printStackTrace()
    }
}
fun deleteAFileInFolder(path: String,filename : String) {
    val folder = File(path)
    try {
        for (f in folder.listFiles()) {
            if (f.name == filename)
                f.delete()
        }

    } catch (e: Exception) {
        e.printStackTrace()
    }
}

fun getFileNameFromUrl(url: String): String {
    var fileName = File(Uri.parse(url).path).name
    val slash = fileName.lastIndexOf("\\")

    if (slash != -1) {
        fileName = fileName.substring(slash + 1)
    }
    return fileName
}

enum class SwapType {
    FrontToBack, BackToFront
}
enum class SwapEncryptDecryptMode {
    Encrypt, Decrypt, None
}
private val secret = "hklong-hklonghkl"
private val key = secret.toByteArray(StandardCharsets.UTF_8)
private val iv = ByteArray(16) { 0x00.toByte() }

//a.mp4
@RequiresApi(Build.VERSION_CODES.O)
fun doSwap(path: String?, swapType: SwapType,mode: SwapEncryptDecryptMode) {
    if (path == null) {
        throw IllegalArgumentException("Require path.")
    }
    if (!File(path).exists()) {
        throw FileNotFoundException("File not found.")
    }

    val tempPath = Files.createTempFile(null, null).toString()
    val buffer = ByteArray(4096)
    val swapBytes = ByteArray(100)

    FileInputStream(path).use { inputFs ->
        FileOutputStream(tempPath).use { outputFs ->
            if (swapType == SwapType.FrontToBack) {
                // Read first 100 bytes
                inputFs.read(swapBytes, 0, 100)
            } else {
                // Seek to 100 bytes before the end
                inputFs.channel.position(inputFs.channel.size() - 100)
                // Read last 100 bytes
                inputFs.read(swapBytes, 0, 100)

                // Write those 100 bytes at the beginning of output
                outputFs.write(swapBytes, 0, 100)

                // Reset position to the start
                inputFs.channel.position(0)
            }

            // Total bytes remaining to read
            var bytesRemaining = inputFs.channel.size() - 100

            while (bytesRemaining > 0) {
                val bytesRead = inputFs.read(buffer, 0, buffer.size)

                when (mode) {
                    SwapEncryptDecryptMode.Encrypt -> {
                        outputFs.write(
                            encrypt(buffer, key, iv),
                            0,
                            bytesRead.coerceAtMost(bytesRemaining.toInt())
                        )
                    }
                    SwapEncryptDecryptMode.Decrypt -> {
                        Log.d("Devoeas", "doSwap: ${decrypt(buffer, key, iv)?.size}")
                        Log.d("Devoeas", "doSwap: ${minOf(bytesRead.toLong(), bytesRemaining).toInt()}")
                        outputFs.write(
                            decrypt(buffer, key, iv),
                            0,
                            bytesRead.coerceAtMost(bytesRemaining.toInt())
                        )
                    //  outputFs.write(decrypt(buffer.copyOfRange(0, bytesRead), key, iv), 0, minOf(bytesRead.toLong(), bytesRemaining).toInt())
                    }
                    SwapEncryptDecryptMode.None -> {
                        outputFs.write(buffer, 0, minOf(bytesRead.toLong(), bytesRemaining).toInt())
                    }
                }

                bytesRemaining -= bytesRead
            }

            if (swapType == SwapType.FrontToBack) {
                // Write first 100 bytes at the end of output
                outputFs.write(swapBytes, 0, 100)
            }
        }
    }

//    Files.delete(file.toPath())
//    Files.move(tempPath, file.toPath(), StandardCopyOption.REPLACE_EXISTING)
    File(path).delete()
    Files.move(Paths.get(tempPath), Paths.get(path))
}


