package app.tek4tv.ipradio.repo

import android.util.Log
import app.tek4tv.ipradio.utils.storage.readFile
import app.tek4tv.ipradio.utils.storage.writeFile
import com.squareup.moshi.JsonAdapter
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException

abstract class BaseRepo<T>(
    protected val fileStoragePath: String
) {
    abstract val jsonAdapter: JsonAdapter<List<T>>

    private val mutex = Mutex()

    protected val coroutineScope = CoroutineScope(Dispatchers.IO)

    suspend fun saveItemsToFile(playlist: List<T>) {
        mutex.withLock {
            withContext(Dispatchers.IO) {
                try {
                    val json = jsonAdapter.toJson(playlist)
                    Log.d("writefile", fileStoragePath)
                    writeFile(fileStoragePath, json)
                } catch (e: IOException) {
                    Log.e("BaseRepo", "error writing playlist!!!")
                    e.printStackTrace()
                }
            }
        }
    }

    suspend fun readItemsFromFile(): List<T> {

        var result = listOf<T>()

        withContext(Dispatchers.IO) {
            try {
                val contentFromFile = readFile(fileStoragePath)
                result = jsonAdapter.fromJson(contentFromFile) ?: listOf()
            } catch (e: IOException) {
                Log.e("BaseRepo", "error reading playlist!!!")
            }

        }
        return result
    }

    fun cancelAllJob() {
        coroutineScope.cancel()
    }
}