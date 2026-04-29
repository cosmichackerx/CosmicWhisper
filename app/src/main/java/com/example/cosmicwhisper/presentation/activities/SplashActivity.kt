package com.example.cosmicwhisper.presentation.activities

import android.app.ActivityOptions
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.AnimationSet
import android.view.animation.DecelerateInterpolator
import android.view.animation.TranslateAnimation
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.cosmicwhisper.R
import com.example.cosmicwhisper.databinding.ActivitySplashBinding
import com.example.cosmicwhisper.domain.extenstions.startActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 1. Enable Edge-to-Edge
        enableEdgeToEdge()
        
        // 2. Setup View Binding
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 3. Handle System Bars (Adaptive UI)
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // 4. Start Intro Animations
        startAnimations()

        // 5. Functional Pre-loading (Assets + Delay)
        lifecycleScope.launch(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            
            // Copy required Whisper models from assets to internal storage
            prepareAssets()
            
            // Minimum time for branding visibility (2.5 seconds total)
            val elapsedTime = System.currentTimeMillis() - startTime
            val remainingTime = 2500 - elapsedTime
            if (remainingTime > 0) {
                delay(remainingTime)
            }

            withContext(Dispatchers.Main) {
                navigateToMain()
            }
        }
    }

    private fun startAnimations() {
        // Fade in and Slide up animation for the identity section
        val animationSet = AnimationSet(true).apply {
            interpolator = DecelerateInterpolator()
            duration = 1000
            
            val fadeIn = AlphaAnimation(0f, 1f)
            val slideUp = TranslateAnimation(0f, 0f, 100f, 0f)
            
            addAnimation(fadeIn)
            addAnimation(slideUp)
        }

        binding.identitySection.startAnimation(animationSet)
        
        // Subtle pulse for the logo
        binding.appIcon.animate()
            .scaleX(1.1f)
            .scaleY(1.1f)
            .setDuration(1200)
            .setStartDelay(500)
            .withEndAction {
                binding.appIcon.animate().scaleX(1f).scaleY(1f).setDuration(800).start()
            }
            .start()
    }

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val options = ActivityOptions.makeCustomAnimation(this, android.R.anim.fade_in, android.R.anim.fade_out)
            startActivity(intent, options.toBundle())
        } else {
            startActivity(intent)
            @Suppress("DEPRECATION")
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
        
        finish()
    }

    private fun prepareAssets() {
        val assetsToCopy = listOf("whisper-tiny-en.tflite", "filters_vocab_en.bin")
        assetsToCopy.forEach { assetName ->
            val file = File(filesDir, assetName)
            if (!file.exists()) {
                try {
                    assets.open(assetName).use { input ->
                        file.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}