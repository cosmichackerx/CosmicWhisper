package com.app.audiototext

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

/**
 * Real-time audio recorder that captures microphone input continuously.
 * Optimized for speech recognition with proper PCM format configuration.
 */
class AudioRecorder {
    companion object {
        const val SAMPLE_RATE = 16000  // 16kHz - standard for speech recognition
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT  // 16-bit PCM
        const val CHANNELS = AudioFormat.CHANNEL_IN_MONO  // Mono input
        const val BUFFER_SIZE_FACTOR = 2  // Size multiplier for buffer allocation

        private const val SILENCE_THRESHOLD = 200  // RMS threshold for silence detection
    }

    private var audioRecord: AudioRecord? = null
    private var isRecording = false

    /**
     * Initializes and starts recording. Must be called before recording.
     * Requires RECORD_AUDIO permission.
     */
    fun startRecording(): Boolean {
        return try {
            val bufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                CHANNELS,
                AUDIO_FORMAT
            ) * BUFFER_SIZE_FACTOR

            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNELS,
                AUDIO_FORMAT,
                bufferSize
            ).apply {
                startRecording()
            }

            isRecording = audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING
            isRecording
        } catch (e: Exception) {
            e.printStackTrace()
            isRecording = false
            false
        }
    }

    /**
     * Stops recording and releases resources.
     */
    fun stopRecording() {
        isRecording = false
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        audioRecord = null
    }

    /**
     * Captures a single audio frame from the microphone.
     * Returns PCM16 audio data as ByteArray.
     * Frame size: 512 samples (32ms at 16kHz) for real-time responsiveness.
     */
    suspend fun captureAudioFrame(): ByteArray? = withContext(Dispatchers.Default) {
        if (!isRecording || audioRecord == null) return@withContext null

        return@withContext try {
            val frameSize = 512  // samples
            val buffer = ShortArray(frameSize)

            val bytesRead = audioRecord!!.read(buffer, 0, frameSize)
            if (bytesRead <= 0) return@withContext null

            // Convert ShortArray to ByteArray (PCM16)
            val byteBuffer = ByteArray(bytesRead * 2)
            for (i in buffer.indices.take(bytesRead)) {
                byteBuffer[i * 2] = (buffer[i].toInt() and 0xFF).toByte()
                byteBuffer[i * 2 + 1] = ((buffer[i].toInt() shr 8) and 0xFF).toByte()
            }

            byteBuffer
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Detects if audio frame contains speech (vs silence).
     * Uses RMS (Root Mean Square) energy calculation.
     */
    fun isSpeechDetected(audioFrame: ByteArray): Boolean {
        if (audioFrame.isEmpty()) return false

        var sumSquares = 0.0
        var i = 0
        while (i < audioFrame.size - 1) {
            // Convert bytes to 16-bit signed int (little-endian)
            val sample = (audioFrame[i + 1].toInt() shl 8) or (audioFrame[i].toInt() and 0xFF)
            sumSquares += sample * sample
            i += 2
        }

        val rms = sqrt(sumSquares / (audioFrame.size / 2))
        return rms > SILENCE_THRESHOLD
    }

    /**
     * Checks if recording is currently active.
     */
    fun isRecordingActive(): Boolean = isRecording
}
