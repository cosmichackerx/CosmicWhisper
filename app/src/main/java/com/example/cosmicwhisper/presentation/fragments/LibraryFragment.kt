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
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.cosmicwhisper.R
import com.example.cosmicwhisper.data.local.entities.TranscriptionEntity
import com.example.cosmicwhisper.databinding.FragmentLibraryBinding
import com.example.cosmicwhisper.presentation.adapters.HistoryAdapter
import java.io.File

class LibraryFragment : Fragment() {

    private var _binding: FragmentLibraryBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLibraryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = HistoryAdapter(
            onShareClick = { text -> shareText(text) },
            onDeleteClick = { entity -> deletePdf(entity) },
            onCopyClick = { text -> copyToClipboard(text) },
            onItemClick = { entity -> openPdf(entity) }
        )

        binding.rvLibrary.apply {
            this.adapter = adapter
            layoutManager = LinearLayoutManager(requireContext())
        }

        loadPdfFiles(adapter)
    }

    private fun loadPdfFiles(adapter: HistoryAdapter) {
        val context = context ?: return
        val directory = File(context.filesDir, "Library")
        val files = directory.listFiles { file -> file.extension == "pdf" }

        _binding?.apply {
            if (files.isNullOrEmpty()) {
                tvEmptyLibrary.visibility = View.VISIBLE
                rvLibrary.visibility = View.GONE
            } else {
                tvEmptyLibrary.visibility = View.GONE
                rvLibrary.visibility = View.VISIBLE
                
                // Map files to TranscriptionEntity for reuse of HistoryAdapter
                val entities = files.map { file ->
                    TranscriptionEntity(
                        id = 0,
                        text = file.name,
                        timestamp = file.lastModified()
                    )
                }.sortedByDescending { it.timestamp }
                
                adapter.submitList(entities)
            }
        }
    }

    private fun openPdf(entity: TranscriptionEntity) {
        val file = File(File(requireContext().filesDir, "Library"), entity.text)
        val uri: Uri = FileProvider.getUriForFile(
            requireContext(),
            "${requireContext().packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(intent)
    }

    private fun shareText(text: String) {
        val file = File(File(requireContext().filesDir, "Library"), text)
        val uri: Uri = FileProvider.getUriForFile(
            requireContext(),
            "${requireContext().packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.share_via)))
    }

    private fun copyToClipboard(text: String) {
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Filename", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(requireContext(), R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
    }

    private fun deletePdf(entity: TranscriptionEntity) {
        val context = context ?: return
        val file = File(File(context.filesDir, "Library"), entity.text)
        if (file.exists()) {
            file.delete()
            _binding?.let { loadPdfFiles(it.rvLibrary.adapter as HistoryAdapter) }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
