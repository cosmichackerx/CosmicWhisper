package com.app.engine

import com.app.asr.IWhisperListener
import java.io.IOException

interface IWhisperEngine {
    val isInitialized: Boolean
    fun interrupt()
    fun setUpdateListener(listener: IWhisperListener?)

    @Throws(IOException::class)
    fun initialize(modelPath: String?, vocabPath: String?, multilingual: Boolean): Boolean
    fun transcribeFile(wavePath: String?): String?
    fun transcribeBuffer(samples: FloatArray?): String? //String getTranslation(String wavePath);
}
