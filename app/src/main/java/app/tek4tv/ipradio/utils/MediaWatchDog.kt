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
        val delayDuration = 180000L

        checkPlaylistJob = scope.launch(Dispatchers.Default) {
            while (true) {
                if (mediaRepo.broadcastList.isEmpty()) {
                    delay(delayDuration)
                    continue
                }
                Log.d(LOG_TAG, "begin check media")
                //if (playlist.isNotEmpty()) {
                var foundBrokenPath = false
                mediaRepo.broadcastList.forEach {
                    if (it.path == "start" || it.path == "end" || it.getMediaType()!=MediaType.VOD) return@forEach

                    val mediaPath = it.path
                    if (mediaPath.isNotEmpty() && !File(mediaPath).exists()) {
                        if (it.pathBackup.startsWith("http")) it.path = it.pathBackup
                        foundBrokenPath = true
                    }
                }

                if (foundBrokenPath)
                    mediaRepo.saveItemsToFile(mediaRepo.broadcastList)//savePlaylist(broadcastList, appContext.filesDir.path)

                val needDownload = mediaRepo.broadcastList.filter {
                    it.path.startsWith("http")
                }

                if (needDownload.isNotEmpty()) {
                    needDownload.forEach {
                        mediaRepo.downloadAMediaItem(it)
                    }
                } //downloadMedias(appContext)

                Log.d("checkplaylist", (needDownload.isNotEmpty()).toString())
                //}
                Log.d(LOG_TAG, "end check media")
                delay(delayDuration)
            }
        }
    }
}