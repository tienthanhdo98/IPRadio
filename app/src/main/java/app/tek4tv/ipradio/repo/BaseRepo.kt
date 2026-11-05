package app.tek4tv.ipradio.repo

import android.util.Log
import app.tek4tv.ipradio.utils.readFile
import app.tek4tv.ipradio.utils.writeFile
import com.squareup.moshi.JsonAdapter
import kotlinx.coroutines.*
import java.io.File

abstract class BaseRepo<T>(
    protected val fileStoragePath: String
) {
    abstract val jsonAdapter: JsonAdapter<List<T>>

    var saveFileJob: Job? = null

    protected val coroutineScope = CoroutineScope(Dispatchers.IO)

    suspend fun saveItemsToFile(playlist: List<T>, from: String = "") {
        saveFileJob?.join()
        saveFileJob = coroutineScope.launch(Dispatchers.IO) {
            try {
                val json = jsonAdapter.toJson(playlist)
                Log.d("writefile", "$from $fileStoragePath")
                writeFile(fileStoragePath, json)
            } catch (e: Exception) {
                Log.e("BaseRepo", "error writing playlist", e)
                e.printStackTrace()
            }
        }
        saveFileJob?.join()
    }

    suspend fun readItemsFromFile(): List<T> {
        var result = listOf<T>()

        withContext(Dispatchers.IO) {
            try {
                val contentFromFile = readFile(fileStoragePath)
                if (contentFromFile.isBlank() || contentFromFile.all { it == '\u0000' }) {
                    if (contentFromFile.isNotEmpty()) {
                        Log.e("BaseRepo", "File playlist ($fileStoragePath) bị hỏng hoặc chứa toàn ký tự null do sập nguồn. Xóa file hỏng để tự phục hồi.")
                        try { File(fileStoragePath).delete() } catch (_: Exception) {}
                    }
                    return@withContext
                }
                result = jsonAdapter.fromJson(contentFromFile) ?: listOf()
            } catch (e: Exception) {
                Log.e("BaseRepo", "error reading playlist: ${e.message}. Xóa file hỏng: $fileStoragePath")
                try { File(fileStoragePath).delete() } catch (_: Exception) {}
            }
        }
        return result
    }

    fun cancelAllJob() {
        coroutineScope.cancel()
    }
}
