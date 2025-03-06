package app.tek4tv.ipradio.utils.media

import android.util.Log
import app.tek4tv.ipradio.model.MediaType
import app.tek4tv.ipradio.repo.MediaRepo
import app.tek4tv.ipradio.utils.*
import kotlinx.coroutines.*
import java.io.File

class MediaWatchDog(
    private val scope: CoroutineScope,
    private val mediaRepo: MediaRepo,
) {
    private var checkPlaylistJob: Job? = null
    val LOG_TAG = "mediawatchdog"

    fun checkPlaylist() {
        checkPlaylistJob?.cancel()
        checkPlaylistJob = scope.launch(Dispatchers.Default) {
            while (true) {
                if (mediaRepo.broadcastList.isNotEmpty()) {
                    Log.d(LOG_TAG, "Checking media...")
                    checkBrokenPath()
                    checkDownload()
                }
                delay(180000L) // 3 phút mới kiểm tra lại
            }
        }
    }

    private suspend fun checkBrokenPath()
    {
        try {
            val brokenItems = mediaRepo.broadcastList.filter {
                it.body != null && it.getMediaType() == MediaType.FILE && !File(it.body!!).exists()
            }

            if (brokenItems.isNotEmpty()) {
                brokenItems.forEach {
                    if (it.pathBackup.startsWith("http")) it.body = it.pathBackup
                    Log.d("foundBrokenPath", it.title)
                }
                mediaRepo.saveItemsToFile(mediaRepo.broadcastList,"checkBrokenPath")
            }
        }
        catch (e: Exception)
        {
            Log.e(LOG_TAG, "error check path")
            e.printStackTrace()
        }
    }

    private fun checkDownload()
    {
        try {
            val needDownload = mediaRepo.broadcastList
                .filter { it.getMediaType() == MediaType.FILE && it.body!!.startsWith("http") }
                .toList()

            if (needDownload.isNotEmpty()) {
                needDownload.forEach { mediaRepo.downloadMediaItem(it) }
            }
        }
        catch (e : Exception)
        {
            Log.e(LOG_TAG, "error check download")
            e.printStackTrace()
        }
    }

    fun cancelCheck()
    {
        checkPlaylistJob?.cancel()
        checkPlaylistJob = null
    }
}