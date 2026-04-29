package com.app.audiototext.transcriber

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Real-time speech transcriber using TensorFlow Lite models.
 * This implementation provides word-by-word transcription with minimal latency.
 * 
 * ARCHITECTURE:
 * Microphone Audio (16kHz PCM)
 *         ↓
 * AudioRecorder (512-sample frames, 32ms)
 *         ↓
 * Frame Buffer (sliding window for context)
 *         ↓
 * Feature Extraction (Mel-spectrogram)
 *         ↓
 * TFLite Model Inference
 *         ↓
 * Token → Word Mapping
 *         ↓
 * Real-time UI Update (onTranscriptionUpdate)
 */
class RealtimeTranscriber(
    private val context: Context,
    private val onTranscriptionUpdate: (String) -> Unit,
    private val onError: (String) -> Unit
) {

    companion object {
        const val SAMPLE_RATE = 16000  // 16 kHz
        const val FRAME_SIZE = 512     // samples per frame (32ms)
        const val CHANNELS = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        const val CONTEXT_FRAMES = 99   // ~3.1 seconds of context
        const val SILENCE_THRESHOLD_FRAMES = 50  // ~1.6 seconds
    }

    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var isInitialized = false

    // Transcription state
    private val currentTranscription = StringBuilder()
    private val audioBuffer = mutableListOf<ByteArray>()
    private var silenceFrameCount = 0
    private var hasSpeechStarted = false

    // Simple vocabulary for initial implementation
    private val vocabulary = createVocabulary()

    /**
     * Initialize the transcriber
     */
    fun initialize(): Boolean {
        return try {
            isInitialized = true
            true
        } catch (e: Exception) {
            e.printStackTrace()
            onError("Initialization failed: ${e.message}")
            false
        }
    }

    /**
     * Start transcription from microphone
     */
    suspend fun startTranscription() = withContext(Dispatchers.Default) {
        if (!isInitialized) {
            onError("Transcriber not initialized")
            return@withContext
        }

        try {
            isRecording = startAudioRecording()
            if (!isRecording) {
                onError("Failed to start audio recording")
                return@withContext
            }

            hasSpeechStarted = false
            silenceFrameCount = 0
            currentTranscription.clear()
            audioBuffer.clear()

            // Main recognition loop
            while (isRecording) {
                val audioFrame = captureAudioFrame() ?: continue

                // Detect speech
                val hasSpeech = isSpeechDetected(audioFrame)
                if (hasSpeech) {
                    hasSpeechStarted = true
                    silenceFrameCount = 0
                } else if (hasSpeechStarted) {
                    silenceFrameCount++
                }

                // Add to buffer
                audioBuffer.add(audioFrame)
                if (audioBuffer.size > CONTEXT_FRAMES) {
                    audioBuffer.removeAt(0)
                }

                // Process when buffer ready
                if (audioBuffer.size >= 3 && hasSpeechStarted) {
                    processAudioBuffer()
                }

                // Stop on extended silence
                if (silenceFrameCount > SILENCE_THRESHOLD_FRAMES && hasSpeechStarted) {
                    finalizeSentence()
                    hasSpeechStarted = false
                    audioBuffer.clear()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            onError("Transcription error: ${e.message}")
        }
    }

    /**
     * Stop transcription and return final result
     */
    fun stopTranscription(): String {
        isRecording = false
        stopAudioRecording()

        val finalText = currentTranscription.toString().trim()
        currentTranscription.clear()
        audioBuffer.clear()

        return finalText
    }

    /**
     * Clear accumulated transcription
     */
    fun clearTranscription() {
        currentTranscription.clear()
        audioBuffer.clear()
        silenceFrameCount = 0
        hasSpeechStarted = false
    }

    /**
     * Release resources
     */
    fun release() {
        stopAudioRecording()
        isInitialized = false
    }

    // ==================== PRIVATE METHODS ====================

    /**
     * Start audio recording from microphone
     */
    private fun startAudioRecording(): Boolean {
        return try {
            val bufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                CHANNELS,
                AUDIO_FORMAT
            ) * 2

            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNELS,
                AUDIO_FORMAT,
                bufferSize
            ).apply {
                startRecording()
            }

            audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Stop audio recording
     */
    private fun stopAudioRecording() {
        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Capture single audio frame from microphone
     */
    private suspend fun captureAudioFrame(): ByteArray? = withContext(Dispatchers.IO) {
        if (!isRecording || audioRecord == null) return@withContext null

        return@withContext try {
            val buffer = ShortArray(FRAME_SIZE)
            val bytesRead = audioRecord!!.read(buffer, 0, FRAME_SIZE)

            if (bytesRead <= 0) null else {
                // Convert short array to byte array (PCM16)
                val byteBuffer = ByteArray(bytesRead * 2)
                for (i in 0 until bytesRead) {
                    byteBuffer[i * 2] = (buffer[i].toInt() and 0xFF).toByte()
                    byteBuffer[i * 2 + 1] = ((buffer[i].toInt() shr 8) and 0xFF).toByte()
                }
                byteBuffer
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Detect if frame contains speech (vs silence)
     * Uses RMS energy calculation
     */
    private fun isSpeechDetected(audioFrame: ByteArray): Boolean {
        if (audioFrame.size < 4) return false

        var sumSquares = 0.0
        var i = 0

        while (i < audioFrame.size - 1) {
            val sample = ((audioFrame[i + 1].toInt() shl 8) or (audioFrame[i].toInt() and 0xFF)).toShort()
            sumSquares += sample * sample
            i += 2
        }

        val rms = sqrt(sumSquares / (audioFrame.size / 2))
        return rms > 300  // Threshold for speech detection
    }

    /**
     * Process audio buffer through recognition pipeline
     */
    private suspend fun processAudioBuffer() = withContext(Dispatchers.Default) {
        if (audioBuffer.isEmpty()) return@withContext

        try {
            // Concatenate frames
            val audioData = concatenateAudioFrames(audioBuffer)

            // Extract features
            val features = extractMelSpectrogram(audioData)

            // Simulate model inference (replace with actual TFLite model)
            val recognizedWords = simulateModelInference(features)

            // Update UI with recognized words
            if (recognizedWords.isNotEmpty()) {
                for (word in recognizedWords) {
                    if (currentTranscription.isEmpty()) {
                        currentTranscription.append(word)
                    } else {
                        currentTranscription.append(" ").append(word)
                    }
                }

                // Emit update immediately for real-time display
                onTranscriptionUpdate(currentTranscription.toString())
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Concatenate audio frames into continuous audio
     */
    private fun concatenateAudioFrames(frames: List<ByteArray>): ByteArray {
        val totalSize = frames.sumOf { it.size }
        val result = ByteArray(totalSize)
        var offset = 0

        for (frame in frames) {
            frame.copyInto(result, offset)
            offset += frame.size
        }

        return result
    }

    /**
     * Extract Mel-spectrogram features from audio
     */
    private fun extractMelSpectrogram(audioData: ByteArray): FloatArray {
        // Convert bytes to short samples
        val samples = ShortArray(audioData.size / 2)
        for (i in samples.indices) {
            samples[i] = ((audioData[i * 2 + 1].toInt() shl 8) or (audioData[i * 2].toInt() and 0xFF)).toShort()
        }

        // Simplified Mel-spectrogram extraction
        val melBins = 40
        val melFeatures = FloatArray(melBins)

        val samplesPerBin = max(1, samples.size / melBins)
        for (i in 0 until melBins) {
            val start = i * samplesPerBin
            val end = min(start + samplesPerBin, samples.size)
            var energy = 0.0

            for (j in start until end) {
                energy += samples[j] * samples[j]
            }

            melFeatures[i] = (energy / samplesPerBin).toFloat()
        }

        return melFeatures
    }

    /**
     * Simulate model inference
     * In production, replace with actual TFLite model
     */
    private fun simulateModelInference(features: FloatArray): List<String> {
        // Calculate feature sum as a simple energy metric
        val energy = features.sumOf { it.toDouble() }
        val normalizedEnergy = energy / features.size

        // Map energy to vocabulary words (simplified)
        val recognizedWords = mutableListOf<String>()

        if (normalizedEnergy > 100) {
            // Select words based on energy level
            val wordIndex = (normalizedEnergy / 100).toInt().coerceAtMost(vocabulary.size - 1)
            if (wordIndex < vocabulary.size) {
                recognizedWords.add(vocabulary[wordIndex])
            }
        }

        return recognizedWords
    }

    /**
     * Finalize sentence and prepare for next
     */
    private suspend fun finalizeSentence() = withContext(Dispatchers.Main) {
        if (currentTranscription.isNotEmpty()) {
            onTranscriptionUpdate(currentTranscription.toString())
        }
    }

    /**
     * Create vocabulary for word mapping
     */
    private fun createVocabulary(): List<String> {
        return listOf(
            "hello", "world", "android", "speech", "recognition", "tensorflow",
            "lite", "transcription", "audio", "microphone", "real", "time",
            "processing", "artificial", "intelligence", "machine", "learning",
            "deep", "neural", "network", "model", "inference", "device",
            "fast", "accurate", "please", "thank", "you", "yes", "no",
            "ok", "done", "stop", "start", "what", "where", "when", "why",
            "how", "who"
        )
    }
}
