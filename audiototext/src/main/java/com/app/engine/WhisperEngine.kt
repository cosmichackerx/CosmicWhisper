package com.app.engine

import android.util.Log
import com.app.asr.IWhisperListener
import com.app.utils.WaveUtil
import com.app.utils.WhisperUtil
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.min


class WhisperEngine : IWhisperEngine {

    private val TAG = "WhisperEngineLiteRT"
    private val mWhisperUtil = WhisperUtil()

    override var isInitialized: Boolean = false
        private set

    private var mUpdateListener: IWhisperListener? = null

    // LiteRT objects (pseudo / adjust based on API)
    private var model: Any? = null
    private var session: Any? = null

    override fun interrupt() {}

    override fun setUpdateListener(listener: IWhisperListener?) {
        mUpdateListener = listener
    }

    private fun updateStatus(msg: String?) {
        mUpdateListener?.onUpdateReceived(msg)
    }

    @Throws(IOException::class)
    override fun initialize(
        modelPath: String?,
        vocabPath: String?,
        multilingual: Boolean
    ): Boolean {

        // 🔥 Load model (LiteRT style - pseudo)
        val file = File(modelPath!!)
        if (!file.exists()) {
            Log.e(TAG, "Model not found")
            return false
        }

        try {

            Log.d(TAG, "Model loaded (LiteRT)")

            val ret = mWhisperUtil.loadFiltersAndVocab(multilingual, vocabPath)
            isInitialized = ret

        } catch (e: Exception) {
            Log.e(TAG, "Init error", e)
            isInitialized = false
        }

        return isInitialized
    }

    override fun transcribeFile(wavePath: String?): String {
        updateStatus("Processing...")
        val mel = getMelSpectrogram(wavePath)
        val result = runInference(mel)
        updateStatus("Done")
        return result
    }

    override fun transcribeBuffer(samples: FloatArray?): String? {
        return null
    }

    private fun getMelSpectrogram(wavePath: String?): FloatArray {
        val samples = WaveUtil.getSamples(wavePath)
        val fixedInputSize = WhisperUtil.WHISPER_SAMPLE_RATE * WhisperUtil.WHISPER_CHUNK_SIZE
        val inputSamples = FloatArray(fixedInputSize)
        val copyLength = min(samples.size, fixedInputSize)
        System.arraycopy(samples, 0, inputSamples, 0, copyLength)
        val cores = Runtime.getRuntime().availableProcessors()
        return mWhisperUtil.getMelSpectrogram(inputSamples, inputSamples.size, cores)
    }

    private fun runInference(inputData: FloatArray): String {

        // 🔥 Prepare input buffer
        val inputBuffer = ByteBuffer.allocateDirect(inputData.size * 4)
        inputBuffer.order(ByteOrder.nativeOrder())

        for (f in inputData) {
            inputBuffer.putFloat(f)
        }

        inputBuffer.rewind()

        // 🔥 Output buffer (adjust size based on model)
        val outputBuffer = ByteBuffer.allocateDirect(4 * 1000)
        outputBuffer.order(ByteOrder.nativeOrder())

        try {
            // ⚠️ Replace with real LiteRT call
            // session.run(inputBuffer, outputBuffer)

        } catch (e: Exception) {
            Log.e(TAG, "Inference error", e)
        }

        // 🔥 Decode output
        outputBuffer.rewind()

        val result = StringBuilder()

        while (outputBuffer.remaining() >= 4) {
            val token = outputBuffer.int

            if (token == mWhisperUtil.getTokenEOT()) break

            if (token < mWhisperUtil.getTokenEOT()) {
                val word = mWhisperUtil.getWordFromToken(token)
                result.append(word)
            }
        }

        return result.toString()
    }
}