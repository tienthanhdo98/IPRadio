package app.tek4tv.ipradio.core.players_helper

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.ImageView
import app.tek4tv.ipradio.model.MediaItemModel
import app.tek4tv.ipradio.model.MediaType
import app.tek4tv.ipradio.repo.MediaRepo
import app.tek4tv.ipradio.utils.getDurationInSecond
import app.tek4tv.ipradio.utils.storage.getFileNameFromUrl
import app.tek4tv.ipradio.utils.storage.isFileExisted
import com.squareup.picasso.Picasso


class ImagePlayer(
    private val playerEventListener: IPlayerEventListener,
    private val imageLayout: ImageView,
    private val mediaRepo: MediaRepo
) : IMediaPlayer {
    val TAG = "ImagePlayer"
    private val delayTime: Long = 15000
    private var handler: Handler? = null
    private var currentMedia: MediaItemModel? = null


    override fun playMedia(mediaItemModel: MediaItemModel, timeStamp: Int) {
        Log.d(
            TAG,
            "ImageMediaDuration: ${getDurationInSecond(mediaItemModel.duration ?: "").toLong()}"
        )
        currentMedia = mediaItemModel
        if (mediaItemModel.body != "") {
            val fileName = getFileNameFromUrl(mediaItemModel.body!!)
            if (isFileExisted(mediaRepo.mediaStoragePath, fileName)) {
                val urlLocal = "file://${mediaRepo.mediaStoragePath}/$fileName"
                Picasso.get().load(urlLocal).into(imageLayout)
            } else {
                Picasso.get().load(mediaItemModel.body).into(imageLayout)
            }
            playerEventListener.onPlayMediaSuccess(mediaItemModel)
            handler?.removeCallbacksAndMessages(null)
            handler = Handler(Looper.getMainLooper()).apply {
                postDelayed(
                    {
                        if (currentMedia?.getMediaType() == MediaType.FILE) {
                            playerEventListener.onMediaEndReached(currentMedia)
                        } else {
                            playerEventListener.onMediaForceEndReached(currentMedia)

                        }
                    }, getDurationInSecond(mediaItemModel.duration ?: "00:00:15").toLong() * 1000
//                    }, delayTime
                )
            }
        }
    }

    override fun resume() {

    }

    override fun release() {

    }


    override fun reset() {

    }


    override fun stop() {
        handler?.removeCallbacksAndMessages(null)
    }

    override fun pause() {

    }


}
