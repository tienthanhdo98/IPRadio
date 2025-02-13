package app.tek4tv.ipradio.utils.media

import android.util.Log
import com.downloader.Error
import com.downloader.OnDownloadListener
import com.downloader.PRDownloader
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

class DownloadItem : Comparable<DownloadItem> {
    var itDownloadUrl = ""
    var itStoragePath = ""
    var itFileName = ""
    var downloadListener: OnDownloadListener? = null
    var priority = 5

    override fun compareTo(other: DownloadItem): Int {
        return priority - other.priority
    }
}

/**
 * A class for queueing download request
 * Can submit download request from many thread
 */
class DownloadHelper2 private constructor() {
    //for tracking added item to the queue
    private val addedItem = ConcurrentHashMap<String, Boolean>()

    //act like a places-holder for downloading item
    //we can only download 3 file at a time
    private val counterChanel = Channel<Int>(2)

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private suspend fun downloadSingleItem(it: DownloadItem): Boolean =
        suspendCancellableCoroutine { cont ->
            PRDownloader.download(it.itDownloadUrl, it.itStoragePath, it.itFileName).build()
                .start(object : OnDownloadListener {
                    override fun onDownloadComplete() {
                        cont.resume(true)
                    }

                    override fun onError(error: Error?) {
                        cont.resume(false)
                    }
                })
        }

    private fun downloadItem(downloadItem : DownloadItem)
    {
        scope.launch {
            //try to take a download slot
            //if the channel is full, the coroutine will be suspend until there are more slots
            counterChanel.send(0)
            Log.d(LOG_TAG,"Downloading : ${downloadItem.itDownloadUrl}")
            val success = downloadSingleItem(downloadItem)
            if(success) {
                downloadItem.downloadListener?.onDownloadComplete()
                Log.d(LOG_TAG,"Success: ${downloadItem.itDownloadUrl}")
            }
            else {
                downloadItem.downloadListener?.onError(Error())
                Log.e(LOG_TAG,"Error: ${downloadItem.itDownloadUrl}")
            }
            //when we done downloading an item, remove its slot
            counterChanel.receive()
            addedItem[downloadItem.itDownloadUrl] = false
        }
    }

    fun addToQueue(downloadItem: DownloadItem) {
        //prevent duplicate download from a url
        val added = addedItem[downloadItem.itDownloadUrl]
        if (added != null && added) {
            Log.d(LOG_TAG, "Duplicated: ${downloadItem.itDownloadUrl}")
            return
        }
        addedItem[downloadItem.itDownloadUrl] = true
        downloadItem(downloadItem)
    }

    companion object {
        private var instance: DownloadHelper2? = null
        const val LOG_TAG="DownloadHelper2"
        fun getInstance(): DownloadHelper2 {
            if (instance == null) instance = DownloadHelper2()
            return instance!!
        }
    }
}