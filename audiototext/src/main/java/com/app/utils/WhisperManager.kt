package com.app.utils

import android.content.Context
import com.app.asr.IWhisperListener
import com.app.asr.Whisper
import java.io.File

class WhisperManager(private val context: Context) {

    private var whisper: Whisper? = null
    private var isInitialized = false

    fun init(
        modelName: String = "whisper-tiny-en.tflite",
        vocabName: String = "filters_vocab_en.bin",
        onReady: (() -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        try {
            val modelPath = getFilePath(modelName)
            val vocabPath = getFilePath(vocabName)

            val modelFile = File(modelPath)
            if (!modelFile.exists()) {
                onError?.invoke("Model not found")
                return
            }

            whisper = Whisper(context)
            whisper?.loadModel(modelPath, vocabPath, false)

            isInitialized = true
            onReady?.invoke()

        } catch (e: Exception) {
            onError?.invoke(e.message ?: "Init error")
        }
    }

    fun transcribe(
        wavPath: String,
        onResult: (String?) -> Unit,
        onUpdate: ((String?) -> Unit)? = null
    ) {
        if (!isInitialized || whisper == null) {
            onUpdate?.invoke("Whisper not initialized")
            return
        }

        if (whisper!!.isInProgress) {
            onUpdate?.invoke("Already processing")
            return
        }

        whisper?.setListener(object : IWhisperListener {
            override fun onUpdateReceived(message: String?) {
                onUpdate?.invoke(message)
            }

            override fun onResultReceived(result: String?) {
                onResult(result)
            }
        })

        whisper?.setFilePath(wavPath)
        whisper?.setAction(Whisper.ACTION_TRANSCRIBE)
        whisper?.start()
    }

    fun stop() {
        whisper?.stop()
    }

    private fun getFilePath(assetName: String): String {
        val file = File(context.filesDir, assetName)
        if (!file.exists()) {
            context.assets.open(assetName).use { input ->
                file.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
        return file.absolutePath
    }
}