package app.tek4tv.ipradio.core.players_helper

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.ImageView
import app.tek4tv.ipradio.model.MediaItem
import app.tek4tv.ipradio.model.MediaType
import app.tek4tv.ipradio.repo.MediaRepo
import app.tek4tv.ipradio.utils.TimeUtils.getDurationInSecond
import app.tek4tv.ipradio.utils.storage.getFileNameFromUrl
import app.tek4tv.ipradio.utils.storage.isFileExisted
import com.squareup.picasso.Picasso


class ImagePlayer(
    private val playerEventListener: IPlayerEventListener,
    private val imageLayout: ImageView,
    private val mediaRepo: MediaRepo
) : BaseMediaPlayer(playerEventListener) {
    private val delayTime: Long = 15000
    val TAG = "ImagePlayer"


    override fun playMedia(mediaItem: MediaItem, isForce: Boolean, timeStamp: Int) {
        Log.d(TAG, "ImageMediaDuration: ${getDurationInSecond(mediaItem.duration ?: "").toLong()}")
        currentMedia = mediaItem
        playerEventListener.onPlayMediaInit(mediaItem)
        if (mediaItem.body != "") {
            val fileName = getFileNameFromUrl(mediaItem.body!!)
            if (isFileExisted(mediaRepo.mediaStoragePath, fileName)) {
                val urlLocal = "file://${mediaRepo.mediaStoragePath}/$fileName"
                Picasso.get().load(urlLocal).into(imageLayout)
            } else {
                Picasso.get().load(mediaItem.body).into(imageLayout)
            }
            playerEventListener.onPlayMediaSuccess(mediaItem)
            handler?.removeCallbacksAndMessages(null)
            handler = Handler(Looper.getMainLooper()).apply {
                postDelayed(
                    {
                        if (currentMedia?.getMediaType() == MediaType.FILE) {
                            playerEventListener.onMediaEndReached(currentMedia)
                        } else {
                            playerEventListener.onMediaForceEndReached(currentMedia)

                        }
//                    }, getDurationInSecond(mediaItem.duration ?: "").toLong() * 1000
                    }, delayTime
                )
            }
        }
    }


    override fun resume() {
        handler?.removeCallbacksAndMessages(null)
        handler = Handler(Looper.getMainLooper()).apply {
            postDelayed(
                {
                    if (currentMedia?.getMediaType() == MediaType.FILE) {
                        playerEventListener.onMediaEndReached(currentMedia)
                    } else {
                        playerEventListener.onMediaForceEndReached(currentMedia)

                    }
                }, delayTime
            )
        }
    }


    override fun stop() {
        handler?.removeCallbacksAndMessages(null)
        Log.d(TAG, "stop")
    }


}
