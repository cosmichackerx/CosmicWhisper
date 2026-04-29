package com.app.engine

import android.util.Log
import com.app.asr.IWhisperListener

class WhisperEngineNative : IWhisperEngine {
    private val TAG = "WhisperEngineNative"
    private val nativePtr: Long // Native pointer to the TFLiteEngine instance

    override var isInitialized: Boolean = false
        private set
    private var mUpdateListener: IWhisperListener? = null

    override fun setUpdateListener(listener: IWhisperListener?) {
        mUpdateListener = listener
    }

    override fun initialize(
        modelPath: String?,
        vocabPath: String?,
        multilingual: Boolean
    ): Boolean {
        val ret = loadModel(modelPath, multilingual)
        Log.d(TAG, "Model is loaded...$modelPath")

        this.isInitialized = true
        return true
    }

    override fun transcribeBuffer(samples: FloatArray?): String? {
        return transcribeBuffer(nativePtr, samples)
    }

    override fun transcribeFile(waveFile: String?): String? {
        return transcribeFile(nativePtr, waveFile)
    }

    override fun interrupt() {
    }

    fun updateStatus(message: String?) {
        if (mUpdateListener != null) mUpdateListener!!.onUpdateReceived(message)
    }

    private fun loadModel(modelPath: String?, isMultilingual: Boolean): Int {
        return loadModel(nativePtr, modelPath, isMultilingual)
    }

    init {
        nativePtr = createTFLiteEngine()
    }

    // Native methods
    private external fun createTFLiteEngine(): Long
    private external fun loadModel(
        nativePtr: Long,
        modelPath: String?,
        isMultilingual: Boolean
    ): Int

    private external fun freeModel(nativePtr: Long = this.nativePtr)

    private external fun transcribeBuffer(nativePtr: Long, samples: FloatArray?): String?
    private external fun transcribeFile(nativePtr: Long, waveFile: String?): String?

    companion object {
        init {
            System.loadLibrary("audioEngine")
        }
    }

}
