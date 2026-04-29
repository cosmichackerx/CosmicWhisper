package com.app.audiototext.tflite

import android.content.Context
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.audio.TensorAudio
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.common.TensorProcessor
import org.tensorflow.lite.support.label.Category
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * TensorFlow Lite inference engine for real-time speech recognition
 * Processes audio frames and returns recognized text predictions
 */
class TFLiteInferenceEngine(
    private val context: Context,
    private val modelPath: String = "models/speech_commands.tflite"
) {
    private var interpreter: Interpreter? = null
    private var tensorAudio: TensorAudio? = null
    private var tensorProcessor: TensorProcessor? = null
    private var isInitialized = false

    private val recognitionLabels = mutableListOf<String>()
    private var inputBuffer: FloatArray? = null
    private var outputBuffer: FloatArray? = null

    companion object {
        // Standard audio configuration for speech recognition
        const val EXPECTED_SAMPLE_RATE = 16000
        const val RECORDING_LENGTH = 16000  // 1 second of audio at 16kHz
    }

    /**
     * Initialize TensorFlow Lite model and audio processing
     */
    fun initialize(): Boolean {
        return try {
            // Load model from assets
            val modelBuffer = loadModelFile(modelPath)
            interpreter = Interpreter(modelBuffer)
            isInitialized = true

            // Initialize audio tensor with standard speech recognition sample rate
            tensorAudio = TensorAudio.create(EXPECTED_SAMPLE_RATE, 1)

            // Setup tensor processor for normalization
            tensorProcessor = TensorProcessor.Builder()
                .add(NormalizeOp(127.5f, 127.5f))
                .build()

            // Get input/output info
            setupModelBuffers()

            // Load default speech command labels
            initializeDefaultLabels()

            true
        } catch (e: Exception) {
            e.printStackTrace()
            isInitialized = false
            false
        }
    }

    /**
     * Process audio frame and return recognized commands with confidence scores
     */
    fun processAudioFrame(audioFrame: FloatArray): List<RecognitionResult> {
        if (!isInitialized || interpreter == null) {
            return emptyList()
        }

        return try {
            // Prepare input tensor
            val inputTensor = interpreter!!.getInputTensor(0)
            val inputDataType = inputTensor.dataType()

            val inputData = when {
                inputDataType.toString().contains("FLOAT32") -> {
                    // Normalize audio to [-1, 1] range if needed
                    FloatArray(audioFrame.size) { i ->
                        audioFrame[i].coerceIn(-1f, 1f)
                    }
                }
                else -> {
                    // Convert to int16 quantized format
                    IntArray(audioFrame.size) { i ->
                        (audioFrame[i] * 32767).toInt().coerceIn(-32768, 32767)
                    }.map { it.toByte() }.toByteArray()
                }
            }

            // Fill input tensor
            inputTensor.setData(inputData)

            // Run inference
            interpreter!!.runForMultipleInputsOutputs(arrayOf(inputTensor), getOutputMap())

            // Extract and parse results
            extractResults()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Get continuous audio recognition from accumulated frames
     */
    fun processAccumulatedAudio(audioFrames: List<FloatArray>): String {
        if (audioFrames.isEmpty()) return ""

        // Concatenate all audio frames
        val accumulatedAudio = FloatArray(audioFrames.sumOf { it.size })
        var offset = 0
        for (frame in audioFrames) {
            frame.copyInto(accumulatedAudio, offset)
            offset += frame.size
        }

        // Process and get results
        val results = processAudioFrame(accumulatedAudio)

        // Return the top recognized command as text
        return if (results.isNotEmpty()) {
            results.first().label
        } else {
            ""
        }
    }

    /**
     * Setup model input/output buffers
     */
    private fun setupModelBuffers() {
        interpreter?.let { interp ->
            val inputTensor = interp.getInputTensor(0)
            val outputTensor = interp.getOutputTensor(0)

            // Allocate buffers based on model requirements
            inputBuffer = FloatArray(inputTensor.numElements())
            outputBuffer = FloatArray(outputTensor.numElements())
        }
    }

    /**
     * Extract and parse inference results
     */
    private fun extractResults(): List<RecognitionResult> {
        return try {
            val results = mutableListOf<RecognitionResult>()

            interpreter?.let { interp ->
                val outputTensor = interp.getOutputTensor(0)
                val outputData = outputTensor.dataAsFloatArray

                // Get top predictions
                outputData.withIndex()
                    .filter { it.index < recognitionLabels.size }
                    .map { (index, confidence) ->
                        RecognitionResult(
                            label = recognitionLabels.getOrNull(index) ?: "unknown",
                            confidence = confidence.coerceIn(0f, 1f)
                        )
                    }
                    .sortedByDescending { it.confidence }
                    .take(5)  // Top 5 results
                    .forEach { results.add(it) }
            }

            results
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Get output tensor map for model execution
     */
    private fun getOutputMap(): Map<Int, Any> {
        val outputMap = mutableMapOf<Int, Any>()
        interpreter?.let { interp ->
            for (i in 0 until interp.outputTensorCount) {
                val outputTensor = interp.getOutputTensor(i)
                val outputData = FloatArray(outputTensor.numElements())
                outputMap[i] = outputData
            }
        }
        return outputMap
    }

    /**
     * Initialize default speech command labels
     */
    private fun initializeDefaultLabels() {
        // Standard speech command vocabulary for general transcription
        recognitionLabels.clear()
        recognitionLabels.addAll(
            listOf(
                "hello", "world", "yes", "no", "okay", "stop",
                "start", "pause", "resume", "next", "previous",
                "volume", "mute", "unmute", "settings", "help",
                "save", "delete", "open", "close", "create",
                "new", "edit", "copy", "paste", "undo",
                "redo", "search", "find", "replace", "select",
                "cut", "clear", "submit", "cancel", "continue",
                "skip", "go", "back", "forward", "home",
                "end", "top", "bottom", "up", "down",
                "left", "right", "center", "full", "screen"
            )
        )
    }

    /**
     * Set custom labels for speech recognition
     */
    fun setLabels(labels: List<String>) {
        recognitionLabels.clear()
        recognitionLabels.addAll(labels)
    }

    /**
     * Load TFLite model from assets
     */
    private fun loadModelFile(modelPath: String): MappedByteBuffer {
        val assetFileDescriptor = context.assets.openFd(modelPath)
        val fileInputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
        val fileChannel = fileInputStream.channel
        val startOffset = assetFileDescriptor.startOffset
        val declaredLength = assetFileDescriptor.declaredLength

        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    /**
     * Release model resources
     */
    fun release() {
        interpreter?.close()
        interpreter = null
        tensorAudio = null
        tensorProcessor = null
        isInitialized = false
    }

    fun getIsInitialized(): Boolean = isInitialized

    /**
     * Data class for recognition results
     */
    data class RecognitionResult(
        val label: String,
        val confidence: Float
    )
}
