package com.example.cosmicwhisper.presentation.activities

import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.example.cosmicwhisper.R
import com.example.cosmicwhisper.databinding.ActivityMainBinding
import com.example.cosmicwhisper.presentation.fragments.HistoryFragment
import com.example.cosmicwhisper.presentation.fragments.LibraryFragment
import com.example.cosmicwhisper.presentation.fragments.SettingsFragment
import com.example.cosmicwhisper.presentation.fragments.TranscribeFragment
import com.example.cosmicwhisper.data.PreferenceManager
import com.example.cosmicwhisper.domain.extenstions.loadFragment

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply theme before super.onCreate
        val preferenceManager = PreferenceManager(this)
        AppCompatDelegate.setDefaultNightMode(preferenceManager.themeMode)

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            
            // Apply top margin to the AppBarLayout instead of padding for better scroll behavior
            val lpAppBar = binding.appBarLayout.layoutParams as ViewGroup.MarginLayoutParams
            lpAppBar.topMargin = systemBars.top
            binding.appBarLayout.layoutParams = lpAppBar

            // Use padding instead of margin for BottomNavigationView so its background covers the bottom
            binding.bottomNavigation.setPadding(0, 0, 0, systemBars.bottom)

            WindowInsetsCompat.CONSUMED
        }

        setupNavigation()

        // Default fragment
        if (savedInstanceState == null) {
            loadFragment(R.id.fragment_container, TranscribeFragment())
            binding.toolbar.title = getString(R.string.transcribe)
        }
    }

    private fun setupNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            // Reset toolbar buttons visibility by default
            binding.btnSaveToolbar.visibility = View.GONE
            binding.btnCopyToolbar.visibility = View.GONE

            when (item.itemId) {
                R.id.nav_transcribe -> {
                    loadFragment(R.id.fragment_container, TranscribeFragment())
                    binding.toolbar.title = getString(R.string.transcribe)
                    true

                }
                R.id.nav_history -> {
                    loadFragment(R.id.fragment_container, HistoryFragment())
                    binding.toolbar.title = getString(R.string.recents)
                    true
                }
                R.id.nav_library -> {
                    loadFragment(R.id.fragment_container, LibraryFragment())
                    binding.toolbar.title = getString(R.string.library)
                    true
                }
                R.id.nav_settings -> {
                    loadFragment(R.id.fragment_container, SettingsFragment())
                    binding.toolbar.title = getString(R.string.settings)
                    true
                }
                else -> false
            }
        }
    }
}