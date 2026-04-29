package com.example.cosmicwhisper.domain.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment

/**
 * Helper class for managing runtime permissions
 */
object PermissionHelper {

    /**
     * Check if audio recording permission is granted
     */
    fun hasAudioPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true  // Permission granted by default on older Android versions
        }
    }

    /**
     * Request audio permission from user
     */
    fun requestAudioPermission(fragment: Fragment, requestCode: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            fragment.requestPermissions(
                arrayOf(Manifest.permission.RECORD_AUDIO),
                requestCode
            )
        }
    }

    /**
     * Handle permission result
     */
    fun handlePermissionResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray,
        expectedRequestCode: Int,
        onGranted: () -> Unit,
        onDenied: () -> Unit
    ) {
        if (requestCode == expectedRequestCode) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                onGranted()
            } else {
                onDenied()
            }
        }
    }
}
