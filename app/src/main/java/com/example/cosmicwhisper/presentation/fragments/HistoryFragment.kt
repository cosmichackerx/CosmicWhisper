package com.example.cosmicwhisper.presentation.fragments

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.appcompat.app.AlertDialog
import com.example.cosmicwhisper.R
import com.example.cosmicwhisper.data.local.database.AppDatabase
import com.example.cosmicwhisper.data.local.entities.TranscriptionEntity
import com.example.cosmicwhisper.databinding.FragmentHistoryBinding
import com.example.cosmicwhisper.presentation.adapters.HistoryAdapter
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.launch

class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupMenu()

        val adapter = HistoryAdapter(
            onShareClick = { text -> shareText(text) },
            onDeleteClick = { entity -> deleteEntry(entity) },
            onCopyClick = { text -> copyToClipboard(text) }
        )
        binding.rvHistory.apply {
            this.adapter = adapter
            layoutManager = LinearLayoutManager(requireContext())
        }

        loadHistory(adapter)
    }

    private fun loadHistory(adapter: HistoryAdapter) {
        val database = AppDatabase.getDatabase(requireContext())
        viewLifecycleOwner.lifecycleScope.launch {
            val history = database.transcriptionDao().getAllTranscriptions()
            _binding?.apply {
                if (history.isEmpty()) {
                    tvEmptyHistory.visibility = View.VISIBLE
                    rvHistory.visibility = View.GONE
                } else {
                    tvEmptyHistory.visibility = View.GONE
                    rvHistory.visibility = View.VISIBLE
                    adapter.submitList(history)
                }
            }
        }
    }

    private fun shareText(text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.share_via)))
    }

    private fun copyToClipboard(text: String) {
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Transcription", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(requireContext(), R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
    }

    private fun setupMenu() {
        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.menu_history, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    R.id.action_clear_all -> {
                        confirmClearAll()
                        true
                    }
                    R.id.action_export_all -> {
                        exportAllHistory()
                        true
                    }
                    else -> false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private fun exportAllHistory() {
        val database = AppDatabase.getDatabase(requireContext())
        viewLifecycleOwner.lifecycleScope.launch {
            val history = database.transcriptionDao().getAllTranscriptions()
            if (history.isNotEmpty()) {
                val exportText = history.joinToString("\n\n---\n\n") { entity ->
                    "Date: ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(entity.timestamp))}\n\n${entity.text}"
                }
                saveAndShareExport(exportText)
                context?.let { Toast.makeText(it, R.string.export_success, Toast.LENGTH_SHORT).show() }
            }
        }
    }

    private fun saveAndShareExport(content: String) {
        try {
            val exportFile = File(requireContext().cacheDir, getString(R.string.export_filename))
            FileOutputStream(exportFile).use { fos ->
                fos.write(content.toByteArray())
            }

            val uri: Uri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                exportFile
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, getString(R.string.share_via)))
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(requireContext(), R.string.export_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun confirmClearAll() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.clear_history_title)
            .setMessage(R.string.clear_history_message)
            .setPositiveButton(R.string.clear) { _, _ ->
                val database = AppDatabase.getDatabase(requireContext())
                viewLifecycleOwner.lifecycleScope.launch {
                    database.transcriptionDao().deleteAll()
                    _binding?.let { loadHistory(it.rvHistory.adapter as HistoryAdapter) }
                    context?.let { Toast.makeText(it, R.string.history_cleared, Toast.LENGTH_SHORT).show() }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun deleteEntry(entity: TranscriptionEntity) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.delete_transcription)
            .setMessage(R.string.delete_transcription_message)
            .setPositiveButton(R.string.delete) { _, _ ->
                val database = AppDatabase.getDatabase(requireContext())
                viewLifecycleOwner.lifecycleScope.launch {
                    database.transcriptionDao().deleteById(entity.id)
                    _binding?.let { loadHistory(it.rvHistory.adapter as HistoryAdapter) }
                    context?.let { Toast.makeText(it, R.string.entry_deleted, Toast.LENGTH_SHORT).show() }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}