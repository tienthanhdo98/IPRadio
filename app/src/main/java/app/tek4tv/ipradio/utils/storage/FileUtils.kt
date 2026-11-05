package app.tek4tv.ipradio.utils

import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.*

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
    return withContext(Dispatchers.IO) {
        val file = File(filePath)
        if (!file.exists()) return@withContext ""
        file.readText(Charsets.UTF_8)
    }
}

@Throws(IOException::class)
suspend fun writeFile(filePath: String, content: String) {
    withContext(Dispatchers.IO) {
        val targetFile = File(filePath)
        val tempFile = File("${filePath}.tmp")
        try {
            FileOutputStream(tempFile).use { fos ->
                fos.write(content.toByteArray(Charsets.UTF_8))
                fos.flush()
                fos.fd.sync() // Ép dữ liệu từ Page Cache xuống chip nhớ vật lý (flash eMMC)
            }
            // Atomic rename: Tráo đổi file nguyên tử để ngăn ngừa file bị trống/hỏng khi sập nguồn
            if (!tempFile.renameTo(targetFile)) {
                if (targetFile.exists()) {
                    targetFile.delete()
                }
                if (!tempFile.renameTo(targetFile)) {
                    tempFile.copyTo(targetFile, overwrite = true)
                    tempFile.delete()
                }
            }
        } catch (e: Exception) {
            if (tempFile.exists()) {
                tempFile.delete()
            }
            throw e
        }
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
        Log.d("filex", storagePath)
        val folder = File(storagePath)
        val audio = listOf("mp3")
        folder.listFiles()?.filter { file -> file.isFile && audio.any { file.name.endsWith(it) } }
            ?.map { it.name } ?: listOf()
    } catch (e: Exception) {
        e.printStackTrace()
        listOf()
    }

}

fun getAllFilePathInFolder(storagePath: String): List<String> {
    val folder = File(storagePath)
    return folder.listFiles()?.filter { it.isFile }?.map { it.path } ?: listOf()
}

fun getFolderSize(path: String): Long {
    val folder = File(path)

    return try {
        var size = 0L
        val files = folder.listFiles()
        if (files != null) {
            for (f in files) {
                if (f.isFile) size += f.length()
            }
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
        val files = folder.listFiles()
        if (files != null) {
            for (f in files) {
                if (f.isFile) f.delete()
            }
        }

    } catch (e: Exception) {
        e.printStackTrace()
    }
}

fun deleteAFileInFolder(path: String, filename: String) {
    val folder = File(path)
    try {
        val files = folder.listFiles()
        if (files != null) {
            for (f in files) {
                if (f.name == filename)
                    f.delete()
            }
        }

    } catch (e: Exception) {
        e.printStackTrace()
    }
}

fun getFileNameFromUrl(url: String): String {
    if (url.isBlank()) return ""
    return try {
        val parsedPath = Uri.parse(url).path ?: url
        var fileName = File(parsedPath).name
        val slash = fileName.lastIndexOf("\\")
        if (slash != -1) {
            fileName = fileName.substring(slash + 1)
        }
        fileName
    } catch (e: Exception) {
        url.substringAfterLast('/')
    }
}
