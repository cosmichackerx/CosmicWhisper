package com.example.cosmicwhisper.presentation.fragments

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import com.example.cosmicwhisper.R
import com.example.cosmicwhisper.databinding.FragmentSettingsBinding

class SettingsFragment : Fragment() {
    
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val sharedPref = requireActivity().getSharedPreferences("settings", Context.MODE_PRIVATE)
        val currentTheme = sharedPref.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)

        // Initialize UI state
        binding.apply {
            when (currentTheme) {
                AppCompatDelegate.MODE_NIGHT_NO -> radioLight.isChecked = true
                AppCompatDelegate.MODE_NIGHT_YES -> radioDark.isChecked = true
                else -> radioSystem.isChecked = true
            }

            themeRadioGroup.setOnCheckedChangeListener { _, checkedId ->
                val mode = when (checkedId) {
                    R.id.radioLight -> AppCompatDelegate.MODE_NIGHT_NO
                    R.id.radioDark -> AppCompatDelegate.MODE_NIGHT_YES
                    else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                }

                // Use KTX edit extension for cleaner syntax
                sharedPref.edit {
                    putInt("theme_mode", mode)
                }
                
                // Real-time theme application
                AppCompatDelegate.setDefaultNightMode(mode)
                val themeName = when (checkedId) {
                    R.id.radioLight -> getString(R.string.light)
                    R.id.radioDark -> getString(R.string.dark)
                    else -> getString(R.string.system_default)
                }
                Toast.makeText(requireContext(), "Theme changed to $themeName", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}