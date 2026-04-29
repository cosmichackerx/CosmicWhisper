package com.app.asr

interface IRecorderListener {
    fun onUpdateReceived(message: String?)
    fun onDataReceived(samples: FloatArray?)
}
