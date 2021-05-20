package app.tek4tv.ipradio.utils

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
        val delayDuration = 30000L

        checkPlaylistJob = scope.launch(Dispatchers.Default) {
            while (true) {
                if (mediaRepo.broadcastList.isEmpty()) {
                    delay(delayDuration)
                    continue
                }
                Log.d(LOG_TAG, "begin check media")

                checkBrokenPath()

                checkDownload()
                Log.d(LOG_TAG, "end check media")

                delay(delayDuration)
            }
        }
    }

    private suspend fun checkBrokenPath()
    {
        try {
            var foundBrokenPath = false
            mediaRepo.broadcastList.forEach {
                if (it.path == "start" || it.path == "end" || it.getMediaType()!=MediaType.VOD) return@forEach

                val mediaPath = it.path
                if (mediaPath.isNotEmpty() && !File(mediaPath).exists()) {
                    if (it.pathBackup.startsWith("http")) it.path = it.pathBackup
                    foundBrokenPath = true
                    Log.d("aaa",it.name)
                }
            }

            if (foundBrokenPath)
                mediaRepo.saveItemsToFile(mediaRepo.broadcastList)
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
            val needDownload = mediaRepo.broadcastList.filter {
                it.getMediaType() == MediaType.VOD && it.path.startsWith("http")
            }

            if (needDownload.isNotEmpty()) {
                needDownload.forEach {
                    mediaRepo.downloadAMediaItem(it)
                }
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