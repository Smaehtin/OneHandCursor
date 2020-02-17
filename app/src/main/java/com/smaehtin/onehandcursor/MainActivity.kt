package com.smaehtin.onehandcursor

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)

        fab.setOnClickListener {
            toggleOneHandCursor()
        }
    }

    override fun onResume() {
        super.onResume()
        updateRunningIndicator()
    }

    private fun askToOpenAccessibilityServiceSettings() {
        AlertDialog.Builder(this)
            .setMessage(R.string.dialog_enable_accessibility_service_message)
            .setTitle(R.string.dialog_enable_accessibility_service_title)
            .setNegativeButton(R.string.dialog_cancel) { _, _ -> }
            .setPositiveButton(R.string.dialog_ok) { _, _ ->
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            .show()
    }


    private fun askToOpenDrawOverlaySettings() {
        AlertDialog.Builder(this)
            .setMessage(R.string.dialog_enable_draw_overlay_permission_message)
            .setTitle(R.string.dialog_enable_draw_overlay_permission_title)
            .setNegativeButton(R.string.dialog_cancel) { _, _ -> }
            .setPositiveButton(R.string.dialog_ok) { _, _ ->
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                )
            }
            .show()
    }

    private fun canDrawOverlays(context: Context): Boolean {
        return Settings.canDrawOverlays(context)
    }

    private fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val preferenceString =
            Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false

        return preferenceString.contains(
            "${context.packageName}/${CursorAccessibilityService::class.java.name}"
        )
    }

    private fun toggleOneHandCursor() {
        if (!canDrawOverlays(this)) {
            askToOpenDrawOverlaySettings()
            return
        }

        if (!isAccessibilityServiceEnabled(this)) {
            askToOpenAccessibilityServiceSettings()
            return
        }

        CursorAccessibilityService.sharedInstance?.disableSelf()
        updateRunningIndicator()
    }

    private fun updateRunningIndicator() {
        val isOneHandCursorEnabled = canDrawOverlays(this) && isAccessibilityServiceEnabled(this)

        fab.setImageResource(
            if (isOneHandCursorEnabled) {
                android.R.drawable.ic_media_pause
            } else {
                android.R.drawable.ic_media_play
            }
        )
    }
}
