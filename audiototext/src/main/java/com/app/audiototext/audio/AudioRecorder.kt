package com.app.audiototext.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Real-time audio recorder that captures audio from device microphone
 * and provides continuous audio frames for speech recognition processing.
 */
class AudioRecorder(
    private val sampleRate: Int = 16000,
    private val channelConfig: Int = AudioFormat.CHANNEL_IN_MONO,
    private val audioFormat: Int = AudioFormat.ENCODING_PCM_16BIT
) {
    private var audioRecord: AudioRecord? = null
    private var isRecording = false

    companion object {
        // Frame size for processing (16ms @ 16kHz = 256 samples)
        const val FRAME_SIZE = 256
        const val BUFFER_SIZE_MULTIPLIER = 4
    }

    /**
     * Initialize audio recorder with proper buffer size calculations
     */
    fun initialize(): Boolean {
        return try {
            val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            val adjustedBufferSize = bufferSize * BUFFER_SIZE_MULTIPLIER

            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                adjustedBufferSize
            ).apply {
                if (state == AudioRecord.STATE_INITIALIZED) {
                    true
                } else {
                    this.release()
                    false
                }
            }

            audioRecord?.state == AudioRecord.STATE_INITIALIZED
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Start continuous audio recording and emit frames through channel
     */
    suspend fun startRecording(audioFrameChannel: Channel<FloatArray>) {
        withContext(Dispatchers.Default) {
            audioRecord?.let { recorder ->
                try {
                    recorder.startRecording()
                    isRecording = true

                    val buffer = ShortArray(FRAME_SIZE)
                    val audioBuffer = FloatArray(FRAME_SIZE)

                    while (isRecording) {
                        val bytesRead = recorder.read(buffer, 0, FRAME_SIZE, AudioRecord.READ_BLOCKING)

                        if (bytesRead == FRAME_SIZE) {
                            // Convert PCM 16-bit audio to normalized float [-1.0, 1.0]
                            for (i in buffer.indices) {
                                audioBuffer[i] = buffer[i].toFloat() / 32768.0f
                            }

                            // Send frame through channel for processing
                            try {
                                audioFrameChannel.send(audioBuffer.copyOf())
                            } catch (e: Exception) {
                                // Channel closed, stop recording
                                isRecording = false
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    isRecording = false
                }
            }
        }
    }

    /**
     * Stop recording and clean up resources
     */
    fun stopRecording() {
        isRecording = false
        audioRecord?.let { recorder ->
            if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                recorder.stop()
            }
        }
    }

    /**
     * Release audio recorder resources
     */
    fun release() {
        stopRecording()
        audioRecord?.release()
        audioRecord = null
    }

    fun isInitialized(): Boolean = audioRecord != null && audioRecord?.state == AudioRecord.STATE_INITIALIZED

    fun getAudioBytes(shortArray: ShortArray): ByteArray {
        val byteBuffer = ByteBuffer.allocate(shortArray.size * 2)
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN)
        byteBuffer.asShortBuffer().put(shortArray)
        return byteBuffer.array()
    }
}
