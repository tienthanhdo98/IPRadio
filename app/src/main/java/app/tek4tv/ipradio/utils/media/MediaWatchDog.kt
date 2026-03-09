package app.tek4tv.ipradio.utils.media

import android.util.Log
import app.tek4tv.ipradio.model.MediaType
import app.tek4tv.ipradio.repo.MediaRepo
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
        val delayDuration = 180000L


        checkPlaylistJob = scope.launch(Dispatchers.Default) {
            while (true) {
                if (mediaRepo.broadcastList.isEmpty()) {
                    delay(delayDuration)
                    continue
                }
                Log.d(LOG_TAG, "begin check media")

                checkBrokenPath()

//                checkDownload()
                Log.d(LOG_TAG, "end check media")

                delay(delayDuration)
            }
        }
    }

    private suspend fun checkBrokenPath() {
//        try {
//            var foundBrokenPath = false
//            mediaRepo.broadcastList.forEach {
//                if (it.body == "start" || it.body == "end" || it.getMediaType()!=MediaType.FILE) return@forEach
//
//                val mediaPath = it.body
//                if (mediaPath!!.isNotEmpty() && !File(mediaPath).exists()) {
//                    if (it.contentType?.value != "4") it.body = it.pathBackup
//                    foundBrokenPath = true
//                }
//            }
//
//            if (foundBrokenPath)
//                mediaRepo.saveItemsToFile(mediaRepo.broadcastList)
//        }
//        catch (e: Exception)
//        {
//            Log.e(LOG_TAG, "error check path")
//            e.printStackTrace()
//        }
    }

    private fun checkDownload() {
//        try {
//            val needDownload = mediaRepo.broadcastList.filter {
//                 it.contentType?.value != "4"
//            }
//
//            if (needDownload.isNotEmpty()) {
//                needDownload.forEach {
//                    mediaRepo.downloadAMediaItem(it)
//                }
//            }
//        }
//        catch (e : Exception)
//        {
//            Log.e(LOG_TAG, "error check download")
//            e.printStackTrace()
//        }
    }

    fun cancelCheck() {
        checkPlaylistJob?.cancel()
        checkPlaylistJob = null
    }
}