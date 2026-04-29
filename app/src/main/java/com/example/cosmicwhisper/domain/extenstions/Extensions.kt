package com.example.cosmicwhisper.domain.extenstions

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction

/**
 * Fragment related extensions.
 */
fun AppCompatActivity.loadFragment(containerId: Int, fragment: Fragment) {
    supportFragmentManager.beginTransaction()
        .replace(containerId, fragment)
        .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
        .commit()
}

/**
 * Toast extension for UI feedback.
 */
fun Context.toast(message: String, duration: Int = Toast.LENGTH_SHORT) {
    Toast.makeText(this, message, duration).show()
}

/**
 * Start Activity extension.
 */
fun <T : Activity> Context.startActivity(it: Class<T>) {
    val intent = Intent(this, it)
    startActivity(intent)
}

/**
 * View visibility extensions.
 */
fun View.show() {
    visibility = View.VISIBLE
}

fun View.hide() {
    visibility = View.GONE
}

fun View.invisible() {
    visibility = View.INVISIBLE
}
