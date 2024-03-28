package app.tek4tv.ipradio.libs.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import com.pedro.encoder.Frame
import com.pedro.encoder.input.audio.AudioPostProcessEffect
import com.pedro.encoder.input.audio.CustomAudioEffect
import com.pedro.encoder.input.audio.GetMicrophoneData
import com.pedro.encoder.input.audio.NoAudioEffect


@SuppressLint("MissingPermission")
open class MicrophoneManagerTek(private val getMicrophoneData: GetMicrophoneData?) {
    private val TAG = "MicrophoneManager"
     var maxInputSize = 0

        private set
    var audioRecord: AudioRecord? = null
    private var pcmBuffer = ByteArray(maxInputSize)
    private var pcmBufferMuted = ByteArray(maxInputSize)
    var isRunning = false
        protected set
    var isCreated = false
        private set

    //default parameters for microphone
    var sampleRate = 32000 //hz
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    var channel = AudioFormat.CHANNEL_IN_STEREO
        private set
    var isMuted = false
        protected set
    private var audioPostProcessEffect: AudioPostProcessEffect? = null
    protected var handlerThread: HandlerThread? = null
    private var customAudioEffect: CustomAudioEffect = NoAudioEffect()

    init {
        pcmBufferSize
    }

    fun setCustomAudioEffect(customAudioEffect: CustomAudioEffect) {
        this.customAudioEffect = customAudioEffect
    }

    /**
     * Create audio record
     */
    fun createMicrophone() {
        createMicrophone(sampleRate, true, false, false)
        Log.i(TAG, "Microphone created, " + sampleRate + "hz, Stereo")
    }

    /**
     * Create audio record with params and default audio source
     */
    fun createMicrophone(
        sampleRate: Int, isStereo: Boolean, echoCanceler: Boolean,
        noiseSuppressor: Boolean
    ): Boolean {
        return createMicrophone(
            MediaRecorder.AudioSource.DEFAULT, sampleRate, isStereo, echoCanceler,
            noiseSuppressor
        )
    }

    /**
     * Create audio record with params and selected audio source
     *
     * @param audioSource - the recording source. See [MediaRecorder.AudioSource] for the
     * recording source definitions.
     */
    fun createMicrophone(
        audioSource: Int, sampleRate: Int, isStereo: Boolean,
        echoCanceler: Boolean, noiseSuppressor: Boolean
    ): Boolean {
        try {
            this.sampleRate = sampleRate
            channel = if (isStereo) AudioFormat.CHANNEL_IN_STEREO else AudioFormat.CHANNEL_IN_MONO
            audioRecord =
                AudioRecord(audioSource, sampleRate, channel, audioFormat, maxInputSize * 5)
            audioPostProcessEffect = AudioPostProcessEffect(audioRecord!!.audioSessionId)
            if (echoCanceler) audioPostProcessEffect!!.enableEchoCanceler()
            if (noiseSuppressor) audioPostProcessEffect!!.enableNoiseSuppressor()
            val chl = if (isStereo) "Stereo" else "Mono"
            require(audioRecord!!.state == AudioRecord.STATE_INITIALIZED) { "Some parameters specified is not valid" }
            Log.i(TAG, "Microphone created, " + sampleRate + "hz, " + chl +", MaxInputSize $maxInputSize, ${audioSource}")
            isCreated = true
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "create microphone error", e)
        }
        return isCreated
    }

    /**
     * Create audio record with params and AudioPlaybackCaptureConfig used for capturing internal
     * audio
     * Notice that you should granted [android.Manifest.permission.RECORD_AUDIO] before calling
     * this!
     *
     * @param config - AudioPlaybackCaptureConfiguration received from [ ]
     * @see AudioPlaybackCaptureConfiguration.Builder.Builder
     * @see "https://developer.android.com/guide/topics/media/playback-capture"
     *
     * @see "https://medium.com/@debuggingisfun/android-10-audio-capture-77dd8e9070f9"
     */
    @JvmOverloads
    fun createInternalMicrophone(
        config: AudioPlaybackCaptureConfiguration?, sampleRate: Int,
        isStereo: Boolean, echoCanceler: Boolean = false, noiseSuppressor: Boolean = false
    ): Boolean {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                this.sampleRate = sampleRate
                channel =
                    if (isStereo) AudioFormat.CHANNEL_IN_STEREO else AudioFormat.CHANNEL_IN_MONO
                audioRecord = AudioRecord.Builder().setAudioPlaybackCaptureConfig(config!!)
                    .setAudioFormat(
                        AudioFormat.Builder().setEncoding(audioFormat)
                            .setSampleRate(sampleRate)
                            .setChannelMask(channel)
                            .build()
                    )
                    .setBufferSizeInBytes(maxInputSize * 5)
                    .build()
                audioPostProcessEffect = AudioPostProcessEffect(audioRecord!!.getAudioSessionId())
                if (echoCanceler) audioPostProcessEffect!!.enableEchoCanceler()
                if (noiseSuppressor) audioPostProcessEffect!!.enableNoiseSuppressor()
                val chl = if (isStereo) "Stereo" else "Mono"
                require(audioRecord!!.state == AudioRecord.STATE_INITIALIZED) { "Some parameters specified is not valid" }
                Log.i(TAG, "Internal microphone created, " + sampleRate + "hz, " + chl )
                isCreated = true
            } else {
                return createMicrophone(sampleRate, isStereo, echoCanceler, noiseSuppressor)
            }
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "create microphone error", e)
        }
        return isCreated
    }

    /**
     * Start record and get data
     */
    @Synchronized
    open fun start() {
        init()
        handlerThread = HandlerThread(TAG)
        handlerThread!!.start()
        val handler = Handler(handlerThread!!.looper)
        handler.post {
            while (isRunning) {
                val frame = read()
                if (frame != null) {
                    getMicrophoneData?.inputPCMData(frame)
                }
            }
        }
    }

    private fun init() {
        audioRecord?.startRecording()
        isRunning = true
        Log.i(TAG, "Microphone started")
    }

    fun mute() {
        isMuted = true
    }

    fun unMute() {
        isMuted = false
    }

    /**
     * @return Object with size and PCM buffer data
     */
    protected fun read(): Frame? {
        val size = audioRecord!!.read(pcmBuffer, 0, pcmBuffer.size)
        return if (size < 0) null else Frame(
            if (isMuted) pcmBufferMuted else customAudioEffect.process(
                pcmBuffer
            ), 0, size
        )
    }



    /**
     * Stop and release microphone
     */
    @Synchronized
    open fun stop() {
        isRunning = false
        isCreated = false
        if (handlerThread != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                handlerThread!!.quitSafely()
            } else {
                handlerThread!!.quit()
            }
        }
        if(audioRecord != null) {
            audioRecord?.setRecordPositionUpdateListener(null)
            audioRecord?.stop()
            audioRecord?.release()
            if (audioPostProcessEffect != null) {
                audioPostProcessEffect!!.release()
            }
            Log.i(TAG, "Microphone stopped")
        }
    }

    /**
     * Get PCM buffer size
     */
    private val pcmBufferSize: Unit
        private get() {
            maxInputSize = AudioRecord.getMinBufferSize(sampleRate, channel, audioFormat)
            pcmBuffer = ByteArray(maxInputSize)
            pcmBufferMuted = ByteArray(maxInputSize)
        }
}
