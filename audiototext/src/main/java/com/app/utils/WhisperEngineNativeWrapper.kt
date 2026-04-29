package com.app.utils


import android.content.Context
import com.app.asr.IWhisperListener
import com.app.engine.IWhisperEngine
import org.tensorflow.lite.Interpreter
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.min

class WhisperEngineNativeWrapper(private val context: Context) : IWhisperEngine {

    private var interpreter: Interpreter? = null
    private val whisperUtil = WhisperUtil()
    private var listener: IWhisperListener? = null

    override var isInitialized: Boolean = false
        private set

    override fun setUpdateListener(listener: IWhisperListener?) {
        this.listener = listener
    }

    override fun initialize(
        modelPath: String?,
        vocabPath: String?,
        multilingual: Boolean
    ): Boolean {
        return try {
            val buffer = File(modelPath!!).readBytes().let { bytes ->
                ByteBuffer.allocateDirect(bytes.size).apply {
                    order(ByteOrder.nativeOrder())
                    put(bytes)
                    rewind()
                }
            }

            val options = Interpreter.Options().apply {
                setNumThreads(4)
                setUseXNNPACK(true)
            }

            interpreter = Interpreter(buffer, options)

            // Load vocab & filters
            isInitialized = whisperUtil.loadFiltersAndVocab(multilingual, vocabPath!!)
            isInitialized
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    override fun transcribeFile(waveFile: String?): String {
        val pcm = readWavToFloat(File(waveFile!!))
        val mel = getMelSpectrogram(pcm)
        val tokens = runInference(mel)
        return decode(tokens)
    }

    override fun transcribeBuffer(samples: FloatArray?): String {
        val mel = getMelSpectrogram(samples!!)
        val tokens = runInference(mel)
        return decode(tokens)
    }

    override fun interrupt() {
        // Optional: implement if you want to cancel inference
    }

    private fun getMelSpectrogram(pcm: FloatArray): FloatArray {
        val fixedInputSize = WhisperUtil.WHISPER_SAMPLE_RATE * WhisperUtil.WHISPER_CHUNK_SIZE
        val inputSamples = FloatArray(fixedInputSize)
        val copyLength = min(pcm.size, fixedInputSize)
        System.arraycopy(pcm, 0, inputSamples, 0, copyLength)
        val cores = Runtime.getRuntime().availableProcessors()
        return whisperUtil.getMelSpectrogram(inputSamples, inputSamples.size, cores)
    }

    private fun readWavToFloat(file: File): FloatArray {
        val bytes = file.readBytes()
        val floatArr = FloatArray((bytes.size - 44) / 2)
        var i = 44
        var j = 0
        while (i < bytes.size) {
            val sample = ((bytes[i + 1].toInt() shl 8) or (bytes[i].toInt() and 0xff))
            floatArr[j++] = sample / 32768.0f
            i += 2
        }
        return floatArr
    }

    private fun runInference(mel: FloatArray): IntArray {
        val nMel = WhisperUtil.WHISPER_N_MEL      // usually 80
        val nLen = mel.size / nMel                // calculate length of each mel frame

        // Convert 1D mel -> 2D [nMel][nLen]
        val mel2D = Array(nMel) { i ->
            FloatArray(nLen) { j ->
                mel[i * nLen + j]
            }
        }

        // Add batch dimension -> 3D [1, nMel, nLen]
        val input = arrayOf(mel2D)

        // Output buffer (adjust output size to your model)
        val output = Array(1) { IntArray(448) }

        interpreter?.run(input, output)

        return output[0]
    }
    private fun decode(tokens: IntArray): String {
        val sb = StringBuilder()
        for (token in tokens) {
            if (token == whisperUtil.getTokenEOT()) break
            if (token >= 50257) continue
            val word = whisperUtil.getWordFromToken(token) ?: ""
            sb.append(word)
        }
        return sb.toString().replace("▁", " ").trim()
    }
}