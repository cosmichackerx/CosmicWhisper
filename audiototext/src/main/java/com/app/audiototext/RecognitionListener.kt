package com.app.audiototext

/**
 * Interface for real-time speech recognition events.
 * Callbacks are invoked on the background thread where recognition is happening.
 */
interface RecognitionListener {
    /**
     * Called when recognition starts.
     */
    fun onRecognitionStarted()

    /**
     * Called when speech is detected.
     */
    fun onSpeechDetected()

    /**
     * Called when a partial result is available.
     * This is called frequently with intermediate transcription text.
     */
    fun onPartialResult(text: String)

    /**
     * Called when a final result is available.
     * This means the recognizer has determined this segment is complete.
     */
    fun onFinalResult(text: String)

    /**
     * Called when recognition stops.
     */
    fun onRecognitionStopped()

    /**
     * Called when an error occurs during recognition.
     */
    fun onError(errorCode: Int, errorMessage: String)
}
