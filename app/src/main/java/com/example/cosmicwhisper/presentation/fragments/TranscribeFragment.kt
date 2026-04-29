package com.example.cosmicwhisper.presentation.fragments

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.app.audiototext.RecognitionListener
import com.app.audiototext.SpeechRecognitionEngine
import com.example.cosmicwhisper.R
import com.example.cosmicwhisper.data.local.database.AppDatabase
import com.example.cosmicwhisper.data.local.entities.TranscriptionEntity
import com.example.cosmicwhisper.databinding.FragmentTranscribeBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Real-time speech transcription fragment using TensorFlow Lite.
 * Features:
 * - Continuous audio capture and transcription
 * - Word-by-word display with auto-scrolling to latest text
 * - Recording timer
 * - Save to database
 * - Copy & Share functionality (via toolbar)
 */
class TranscribeFragment : Fragment(), RecognitionListener {

    private var _binding: FragmentTranscribeBinding? = null
    private val binding get() = _binding!!

    private var speechEngine: SpeechRecognitionEngine? = null
    private var isRecording = false
    private var recordingStartTime: Long = 0
    private val timerJob = mutableListOf<kotlinx.coroutines.Job>()

    // State management for transcription
    private val currentTranscriptionBuilder = StringBuilder()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Request permissions if needed
        requestPermissionsIfNeeded()
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

        setupUI()
    }

    private fun setupUI() {
        // Record button click listener
        binding.fabRecordToggle.setOnClickListener {
            if (isRecording) {
                stopRecording()
            } else {
                startRecording()
            }
        }

        // Initialize empty state
        updateUIState(isRecording = false)
    }

    private fun startRecording() {
        // Check permission
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(
                requireContext(),
                R.string.permission_required,
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        isRecording = true
        recordingStartTime = System.currentTimeMillis()
        currentTranscriptionBuilder.clear()
        binding.tvTranscription.text = ""

        // Initialize speech recognition engine
        speechEngine = SpeechRecognitionEngine(this)
        speechEngine?.startRecognition(viewLifecycleOwner.lifecycleScope)

        // Start timer update
        startTimerUpdate()
        updateUIState(isRecording = true)

        Toast.makeText(
            requireContext(),
            R.string.recording_started,
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun stopRecording() {
        isRecording = false
        speechEngine?.stopRecognition()

        // Cancel timer updates
        timerJob.forEach { it.cancel() }
        timerJob.clear()

        updateUIState(isRecording = false)

        Toast.makeText(
            requireContext(),
            R.string.recording_stopped,
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun startTimerUpdate() {
        val timerCoroutine = viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
            while (isRecording) {
                val elapsedMillis = System.currentTimeMillis() - recordingStartTime
                val seconds = elapsedMillis / 1000
                val minutes = seconds / 60
                val displaySeconds = seconds % 60

                binding.tvTimer.text = String.format(
                    Locale.getDefault(),
                    "%02d:%02d",
                    minutes,
                    displaySeconds
                )

                // Update every 100ms for smooth timer
                kotlinx.coroutines.delay(100)
            }
        }
        timerJob.add(timerCoroutine)
    }

    private fun updateUIState(isRecording: Boolean) {
        binding.apply {
            if (isRecording) {
                statusTitle.text = getString(R.string.recording_in_progress)
                statusSubtitle.text = getString(R.string.tap_to_stop)
                fabRecordToggle.setImageResource(R.drawable.ic_stop)
                tvTimer.visibility = View.VISIBLE
                tvTranscription.visibility = View.VISIBLE
            } else {
                statusTitle.text = getString(R.string.start_transcription)
                statusSubtitle.text = getString(R.string.tap_to_start)
                fabRecordToggle.setImageResource(R.drawable.ic_mic)
                tvTimer.visibility = View.GONE
                tvTimer.text = getString(R.string.timer_default)
            }
        }
    }

    /**
     * Real-time transcription callback - updates UI with partial results.
     * This is called frequently as new words are recognized.
     */
    override fun onPartialResult(text: String) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
            currentTranscriptionBuilder.clear()
            currentTranscriptionBuilder.append(text)
            binding.tvTranscription.text = text

            // Auto-scroll to bottom (latest transcription)
            binding.cardTranscription.post {
                binding.cardTranscription.scrollTo(
                    0,
                    binding.cardTranscription.bottom
                )
            }
        }
    }

    /**
     * Final result callback - when recognizer determines segment is complete.
     */
    override fun onFinalResult(text: String) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
            currentTranscriptionBuilder.clear()
            currentTranscriptionBuilder.append(text)
            binding.tvTranscription.text = text

            // Save to database asynchronously
            saveTranscription(text)
        }
    }

    override fun onRecognitionStarted() {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
            binding.statusSubtitle.text = getString(R.string.listening)
        }
    }

    override fun onSpeechDetected() {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
            binding.statusSubtitle.text = getString(R.string.processing)
        }
    }

    override fun onRecognitionStopped() {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
            binding.statusSubtitle.text = getString(R.string.recording_stopped)
        }
    }

    override fun onError(errorCode: Int, errorMessage: String) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
            isRecording = false
            updateUIState(isRecording = false)
            Toast.makeText(
                requireContext(),
                "Error: $errorMessage",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun saveTranscription(text: String) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val database = AppDatabase.getDatabase(requireContext())
                val entity = TranscriptionEntity(
                    text = text,
                    timestamp = System.currentTimeMillis()
                )
                database.transcriptionDao().insert(entity)

                // Notify on main thread
                launch(Dispatchers.Main) {
                    Toast.makeText(
                        requireContext(),
                        R.string.transcription_saved,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun requestPermissionsIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.RECORD_AUDIO
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(
                    arrayOf(Manifest.permission.RECORD_AUDIO),
                    PERMISSION_REQUEST_CODE
                )
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(
                    requireContext(),
                    R.string.permission_denied,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (isRecording) {
            stopRecording()
        }
        timerJob.forEach { it.cancel() }
        timerJob.clear()
        _binding = null
    }

    companion object {
        private const val PERMISSION_REQUEST_CODE = 1001
    }
}
