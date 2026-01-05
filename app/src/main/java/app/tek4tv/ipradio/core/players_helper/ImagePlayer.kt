package app.tek4tv.ipradio.core.players_helper

import android.content.res.Resources
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.ImageView
import app.tek4tv.ipradio.model.MediaItem
import app.tek4tv.ipradio.model.MediaType
import app.tek4tv.ipradio.repo.MediaRepo
import app.tek4tv.ipradio.utils.getDurationInSecond
import app.tek4tv.ipradio.utils.getFileNameFromUrl
import app.tek4tv.ipradio.utils.isFileExisted
import com.squareup.picasso.Picasso
import java.io.File


class ImagePlayer(
    private val playerEventListener: IPlayerEventListener,
    private val imageLayout: ImageView,
    private val mediaRepo: MediaRepo
) : IMediaPlayer {
    val TAG = "ImagePlayer"
    private var delayTime: Long = 15000
    private var handler: Handler? = null
    private var currentMedia: MediaItem? = null


    override fun playMedia(mediaItem: MediaItem, timeStamp: Int) {
        delayTime =
            if (mediaItem.duration == "" || mediaItem.duration == "00:00:00" || mediaItem.duration == null) {
                15000
            } else {
                getDurationInSecond(mediaItem.duration).toLong() * 1000
            }
        currentMedia = mediaItem
        val dm = Resources.getSystem().displayMetrics
        val maxW = dm.widthPixels
        val maxH = dm.heightPixels
        if (mediaItem.body != "") {
            val fileName = getFileNameFromUrl(mediaItem.body!!)
            val file = File(mediaRepo.mediaStoragePath, fileName)
            if (isFileExisted(mediaRepo.mediaStoragePath, fileName)) {
                Picasso.get().load(file)
                    .resize(maxW, maxH)      // giới hạn theo kích thước màn hình
                    .onlyScaleDown() // chỉ thu nhỏ, không phóng to
                    .config(Bitmap.Config.RGB_565)// giảm bộ nhớ (từ 4B/px xuống 2B/px), chấp nhận giảm chất lượng
                    .into(imageLayout)
            } else {
                Picasso.get().load(mediaItem.body)
                    .resize(maxW, maxH)      // giới hạn theo kích thước màn hình
                    .onlyScaleDown() // chỉ thu nhỏ, không phóng to
                    .config(Bitmap.Config.RGB_565)// giảm bộ nhớ (từ 4B/px xuống 2B/px), chấp nhận giảm chất lượng
                    .into(imageLayout)
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
                    }, delayTime
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
