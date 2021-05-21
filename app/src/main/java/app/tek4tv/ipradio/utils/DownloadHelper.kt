package app.tek4tv.ipradio.utils

import android.util.Log
import com.downloader.Error
import com.downloader.OnDownloadListener
import com.downloader.PRDownloader
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.coroutines.resume

class DownloadHelper private constructor(
    private val scope: CoroutineScope
) {
    private val CHUNK_SIZE = 5

    private val addedItem = mutableMapOf<String, Boolean>()
    private val downloadQueue: Queue<DownloadItem> = PriorityQueue()
    private var isDownloading = false
    private var downloadJob: Job? = null

    private val mutex = Mutex()

    private fun startDownload() {

        scope.launch(Dispatchers.Default) {
            mutex.withLock {
                isDownloading = true
            }

            downloadJob?.join()
            downloadJob = launch {
                loopDownload()
            }
        }
    }

    private suspend fun loopDownload() {
        Log.d("downloadx", "begin")
        while (downloadQueue.isNotEmpty()) {
            val head = mutex.withLock {
                downloadQueue.poll()
            } ?: continue

            val isDownloadSuccess = downloadSingleItem(head)

            withContext(Dispatchers.Main) {
                if (isDownloadSuccess) head.downloadListener?.onDownloadComplete()
                else head.downloadListener?.onError(null)
            }

            mutex.withLock {
                addedItem[head.itDownloadUrl] = false
            }
        }

        mutex.withLock {
            isDownloading = false
        }

        Log.d("downloadx", "end")
        /*while (true) {
            downloadChunk()

            Log.d("downloadx", "finish chunked")

            if (downloadQueue.isEmpty()) break
        }
        isDownloading = false
        Log.d("downloadx", "finish all")*/
    }

    private suspend fun downloadChunk() {
        Log.d("downloadx", "chunked")
        val itemsToDownload = mutableListOf<DownloadItem>()

        while (downloadQueue.isNotEmpty() && itemsToDownload.size < CHUNK_SIZE) {
            val head = mutex.withLock {
                downloadQueue.poll()
            }
            if (head != null) itemsToDownload.add(head)
        }

        itemsToDownload.forEach {
            val isDownloadSuccess = downloadSingleItem(it)

            withContext(Dispatchers.Main) {
                if (isDownloadSuccess) it.downloadListener?.onDownloadComplete()
                else it.downloadListener?.onError(null)
            }

            mutex.withLock {
                addedItem[it.itDownloadUrl] = false
            }
        }
    }

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

    fun addToQueue(downloadItem: DownloadItem) {
        scope.launch {
            mutex.withLock {
                val added = addedItem[downloadItem.itDownloadUrl]
                if (added != null && added) return@launch
                addedItem[downloadItem.itDownloadUrl] = true

                downloadQueue.offer(downloadItem)

                if (!isDownloading) startDownload()
            }
        }
    }

    companion object {
        private var instance: DownloadHelper? = null
        fun getInstance(scope: CoroutineScope): DownloadHelper {
            if (instance == null) instance = DownloadHelper(scope)
            return instance!!
        }
    }
}

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
    private val counterChanel = Channel<Int>(3)

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