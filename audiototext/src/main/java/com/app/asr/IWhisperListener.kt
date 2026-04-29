package com.app.asr

interface IWhisperListener {
    fun onUpdateReceived(message: String?)
    fun onResultReceived(result: String?)
}
