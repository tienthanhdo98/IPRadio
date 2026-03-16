package app.tek4tv.ipradio.core.stream

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

class MicAacIcecastStreamer(
    private val context: Context,
    private val icecastClient: IcecastPutClient
) {
    @Volatile
    private var running = false

    suspend fun start() = withContext(Dispatchers.IO) {
        if (!hasRecordPermission()) {
            throw SecurityException("Missing RECORD_AUDIO permission")
        }

        running = true

        val sampleRate = 44100
        val channelCount = 1
        val bitRate = 64000

        val minBufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        require(minBufferSize > 0) { "AudioRecord.getMinBufferSize failed: $minBufferSize" }

        val recorder = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBufferSize * 2
            )
        } catch (e: SecurityException) {
            throw SecurityException("Cannot create AudioRecord", e)
        }

        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            error("AudioRecord init failed")
        }

        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        val format = MediaFormat.createAudioFormat(
            MediaFormat.MIMETYPE_AUDIO_AAC,
            sampleRate,
            channelCount
        ).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, minBufferSize * 2)
        }

        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()

        val pcmBuffer = ByteArray(minBufferSize)
        val bufferInfo = MediaCodec.BufferInfo()

        try {
            icecastClient.connect()

            recorder.startRecording()
            if (recorder.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                error("AudioRecord startRecording failed")
            }

            Log.d("MicAacStreamer", "Start streaming...")

            while (running && kotlinx.coroutines.currentCoroutineContext().isActive) {
                val read = recorder.read(pcmBuffer, 0, pcmBuffer.size)
                if (read <= 0) continue

                val inputIndex = codec.dequeueInputBuffer(10_000)
                if (inputIndex >= 0) {
                    codec.getInputBuffer(inputIndex)?.apply {
                        clear()
                        put(pcmBuffer, 0, read)
                    }

                    codec.queueInputBuffer(
                        inputIndex,
                        0,
                        read,
                        System.nanoTime() / 1000L,
                        0
                    )
                }

                while (true) {
                    val outputIndex = codec.dequeueOutputBuffer(bufferInfo, 0)
                    if (outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                        break
                    }

                    if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        Log.d("MicAacStreamer", "Output format changed: ${codec.outputFormat}")
                        continue
                    }

                    if (outputIndex >= 0) {
                        val encodedBuffer = codec.getOutputBuffer(outputIndex)
                        if (encodedBuffer != null && bufferInfo.size > 0) {
                            val packet = ByteArray(bufferInfo.size)
                            encodedBuffer.position(bufferInfo.offset)
                            encodedBuffer.limit(bufferInfo.offset + bufferInfo.size)
                            encodedBuffer.get(packet)

                            val adtsHeader = buildAdtsHeader(
                                aacFrameLength = packet.size + 7,
                                sampleRate = sampleRate,
                                channelCount = channelCount
                            )

                            icecastClient.write(adtsHeader)
                            icecastClient.write(packet)
                        }

                        codec.releaseOutputBuffer(outputIndex, false)
                    }
                }
            }

            Log.d("MicAacStreamer", "Streaming loop ended")
        } finally {
            try {
                recorder.stop()
            } catch (_: Exception) {
            }
            try {
                recorder.release()
            } catch (_: Exception) {
            }
            try {
                codec.stop()
            } catch (_: Exception) {
            }
            try {
                codec.release()
            } catch (_: Exception) {
            }
            icecastClient.close()
        }
    }

    fun stop() {
        running = false
    }

    private fun hasRecordPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun buildAdtsHeader(
        aacFrameLength: Int,
        sampleRate: Int,
        channelCount: Int
    ): ByteArray {
        val freqIdx = when (sampleRate) {
            96000 -> 0
            88200 -> 1
            64000 -> 2
            48000 -> 3
            44100 -> 4
            32000 -> 5
            24000 -> 6
            22050 -> 7
            16000 -> 8
            12000 -> 9
            11025 -> 10
            8000 -> 11
            7350 -> 12
            else -> 4
        }

        val profile = 2 // AAC LC

        return ByteArray(7).apply {
            this[0] = 0xFF.toByte()
            this[1] = 0xF1.toByte()
            this[2] = (((profile - 1) shl 6) or (freqIdx shl 2) or (channelCount shr 2)).toByte()
            this[3] = ((((channelCount and 3) shl 6) or (aacFrameLength shr 11))).toByte()
            this[4] = ((aacFrameLength and 0x7FF) shr 3).toByte()
            this[5] = ((((aacFrameLength and 7) shl 5) or 0x1F)).toByte()
            this[6] = 0xFC.toByte()
        }
    }
}