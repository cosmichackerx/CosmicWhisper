package com.example.cosmicwhisper.presentation.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.app.audiototext.transcriber.RealtimeTranscriber
import com.example.cosmicwhisper.R
import com.example.cosmicwhisper.data.local.database.AppDatabase
import com.example.cosmicwhisper.data.local.entities.TranscriptionEntity
import com.example.cosmicwhisper.databinding.FragmentTranscribeBinding
import com.example.cosmicwhisper.domain.utils.PermissionHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.floor

/**
 * Real-time live transcription fragment using TensorFlow Lite
 * Provides immediate word-by-word transcription with auto-scrolling
 */
class TranscribeFragment : Fragment() {

    private var _binding: FragmentTranscribeBinding? = null
    private val binding get() = _binding!!

    private var transcriber: RealtimeTranscriber? = null
    private var isRecording = false
    private var timerJob: Job? = null
    private var recordingStartTime = 0L
    private var transcriptionJob: Job? = null

    companion object {
        const val AUDIO_PERMISSION_REQUEST_CODE = 1001
        const val UPDATE_INTERVAL_MS = 100L  // Update UI every 100ms
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTranscribeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initializeTranscriber()
        setupUI()
        setupRecordButton()
    }

    /**
     * Initialize the transcriber with callbacks
     */
    private fun initializeTranscriber() {
        transcriber = RealtimeTranscriber(
            context = requireContext(),
            onTranscriptionUpdate = { text ->
                updateTranscriptionUI(text)
            },
            onError = { error ->
                handleError(error)
            }
        )

        if (!transcriber!!.initialize()) {
            showError("Failed to initialize transcriber")
        }
    }

    /**
     * Setup UI elements
     */
    private fun setupUI() {
        binding.tvTranscription.text = ""
        binding.statusTitle.text = getString(R.string.start_transcription)
        binding.statusSubtitle.text = getString(R.string.tap_to_start)
        binding.tvTimer.text = getString(R.string.timer_default)
    }

    /**
     * Setup record button click listener
     */
    private fun setupRecordButton() {
        binding.fabRecordToggle.setOnClickListener {
            if (isRecording) {
                stopRecording()
            } else {
                startRecording()
            }
        }
    }

    /**
     * Start recording and transcription
     */
    private fun startRecording() {
        // Check for audio permission
        if (!PermissionHelper.hasAudioPermission(requireContext())) {
            PermissionHelper.requestAudioPermission(this, AUDIO_PERMISSION_REQUEST_CODE)
            return
        }

        isRecording = true
        recordingStartTime = System.currentTimeMillis()

        // Update UI
        binding.statusTitle.text = getString(R.string.recording)
        binding.statusSubtitle.text = getString(R.string.tap_to_stop)
        binding.fabRecordToggle.setImageResource(R.drawable.ic_mic)

        // Clear previous transcription
        transcriber?.clearTranscription()
        binding.tvTranscription.text = ""

        // Start timer
        startTimer()

        // Start transcription in coroutine
        transcriptionJob = viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Default) {
            transcriber?.startTranscription()
        }

        Toast.makeText(requireContext(), R.string.recording_started, Toast.LENGTH_SHORT).show()
    }

    /**
     * Stop recording and save transcription
     */
    private fun stopRecording() {
        isRecording = false

        // Stop timer
        timerJob?.cancel()

        // Stop transcription
        val finalTranscription = transcriber?.stopTranscription() ?: ""

        // Update UI
        binding.statusTitle.text = getString(R.string.start_transcription)
        binding.statusSubtitle.text = getString(R.string.tap_to_start)
        binding.fabRecordToggle.setImageResource(R.drawable.ic_mic)

        // Save transcription if not empty
        if (finalTranscription.isNotBlank()) {
            saveTranscription(finalTranscription)
            showSuccess("Transcription saved")
        } else {
            binding.tvTranscription.text = getString(R.string.no_transcription)
            showError("No speech detected")
        }

        binding.tvTimer.text = getString(R.string.timer_default)
        Toast.makeText(requireContext(), R.string.recording_stopped, Toast.LENGTH_SHORT).show()
    }

    /**
     * Update transcription display with real-time text
     */
    private fun updateTranscriptionUI(text: String) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
            binding.tvTranscription.text = text

            // Auto-scroll to latest transcription
            binding.tvTranscription.post {
                val scrollAmount = binding.tvTranscription.lineCount * binding.tvTranscription.lineHeight
                binding.tvTranscription.scrollTo(0, scrollAmount)
            }
        }
    }

    /**
     * Start recording timer
     */
    private fun startTimer() {
        timerJob = viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
            while (isRecording) {
                val elapsedTime = System.currentTimeMillis() - recordingStartTime
                val seconds = floor(elapsedTime / 1000.0).toInt()
                val minutes = seconds / 60
                val secondsInMinute = seconds % 60

                binding.tvTimer.text = String.format(
                    "%02d:%02d",
                    minutes,
                    secondsInMinute
                )

                delay(100)  // Update every 100ms
            }
        }
    }

    /**
     * Save transcription to database
     */
    private fun saveTranscription(text: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val database = AppDatabase.getDatabase(requireContext())
                val entity = TranscriptionEntity(
                    text = text,
                    timestamp = System.currentTimeMillis()
                )
                database.transcriptionDao().insert(entity)
            } catch (e: Exception) {
                e.printStackTrace()
                showError("Failed to save transcription: ${e.message}")
            }
        }
    }

    /**
     * Handle transcription errors
     */
    private fun handleError(error: String) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
            showError(error)
            if (isRecording) {
                stopRecording()
            }
        }
    }

    /**
     * Show error toast
     */
    private fun showError(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    /**
     * Show success toast
     */
    private fun showSuccess(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    /**
     * Handle permission result
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        PermissionHelper.handlePermissionResult(
            requestCode,
            permissions,
            grantResults,
            AUDIO_PERMISSION_REQUEST_CODE,
            onGranted = {
                startRecording()
            },
            onDenied = {
                showError("Audio permission is required for transcription")
            }
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        
        // Clean up resources
        if (isRecording) {
            stopRecording()
        }

        timerJob?.cancel()
        transcriptionJob?.cancel()
        transcriber?.release()
        transcriber = null

        _binding = null
    }
}
