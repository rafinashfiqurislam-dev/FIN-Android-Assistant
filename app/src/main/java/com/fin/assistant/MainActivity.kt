
package com.fin.assistant

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private val bg = Color.rgb(10, 10, 10)
    private val panel = Color.rgb(25, 25, 25)
    private val white = Color.WHITE
    private val muted = Color.LTGRAY

    private lateinit var permissionStatus: TextView
    private lateinit var engineStatus: TextView

    private val requiredPermissions = arrayOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.READ_CONTACTS
    )

    companion object {
        private const val PERMISSION_REQUEST_CODE = 501
        private const val NOTIFICATION_REQUEST_CODE = 502
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildDashboard()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun text(
        value: String,
        size: Float,
        color: Int,
        bold: Boolean = false
    ) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        if (bold) setTypeface(null, android.graphics.Typeface.BOLD)
    }

    private fun card(): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(16))
            setBackgroundColor(panel)
        }

    private fun buildDashboard() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            setPadding(dp(20), dp(24), dp(20), dp(24))
        }

        val scroll = ScrollView(this)
        scroll.addView(root)

        root.addView(text("FIN", 38f, white, true))

        root.addView(
            text("YOUR PERSONAL OFFLINE ASSISTANT", 11f, muted).apply {
                letterSpacing = 0.12f
                setPadding(0, dp(4), 0, dp(28))
            }
        )

        root.addView(text("SYSTEM OVERVIEW", 12f, muted, true))
        root.addView(space(12))

        val engineCard = card()
        engineCard.addView(text("VOICE ENGINE", 12f, muted, true))

        engineStatus = text("BENGALI OFFLINE ENGINE", 15f, white, true)
        engineStatus.setPadding(0, dp(12), 0, dp(4))
        engineCard.addView(engineStatus)

        engineCard.addView(
            text(
                "Voice recognition is designed to run locally on this device.",
                12f,
                muted
            )
        )

        root.addView(engineCard)
        root.addView(space(12))

        val permissionCard = card()
        permissionCard.addView(text("PERMISSIONS", 12f, muted, true))

        permissionStatus = text("", 14f, white)
        permissionStatus.setPadding(0, dp(12), 0, 0)
        permissionCard.addView(permissionStatus)

        root.addView(permissionCard)
        root.addView(space(22))

        root.addView(text("CONTROLS", 12f, muted, true))
        root.addView(space(10))

        root.addView(actionButton("MANAGE PERMISSIONS") {
            val intent = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        })

        root.addView(space(10))

        root.addView(actionButton("ACTIVATE FIN") {
            activateFin()
        })

        root.addView(space(10))

        root.addView(actionButton("STOP FIN") {
            stopService(Intent(this, FinVoiceService::class.java))
            engineStatus.text = "VOICE ENGINE — STOPPED"
            Toast.makeText(
                this,
                "FIN service stopped.",
                Toast.LENGTH_SHORT
            ).show()
        })

        root.addView(space(28))

        root.addView(
            text(
                "PRIVACY FIRST\n" +
                    "Voice processing is designed to run on your device. " +
                    "Android displays microphone access indicators while the microphone is active.",
                12f,
                muted
            )
        )

        setContentView(scroll)
        refreshPermissions()
    }

    private fun activateFin() {
        val missingPermissions = requiredPermissions.filter {
            checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            requestPermissions(
                missingPermissions.toTypedArray(),
                PERMISSION_REQUEST_CODE
            )
            return
        }

        requestNotificationPermissionIfNeeded()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (
            Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                NOTIFICATION_REQUEST_CODE
            )
        } else {
            startFinService()
        }
    }

    private fun startFinService() {
        try {
            val intent = Intent(this, FinVoiceService::class.java)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }

            engineStatus.text = "VOICE ENGINE — STARTING"

            Toast.makeText(
                this,
                "FIN voice service is starting.",
                Toast.LENGTH_SHORT
            ).show()

        } catch (e: Exception) {
            engineStatus.text = "VOICE ENGINE — START FAILED"

            Toast.makeText(
                this,
                "Could not start FIN: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(
            requestCode,
            permissions,
            grantResults
        )

        when (requestCode) {
            PERMISSION_REQUEST_CODE -> {
                refreshPermissions()

                val allGranted = requiredPermissions.all {
                    checkSelfPermission(it) ==
                        PackageManager.PERMISSION_GRANTED
                }

                if (allGranted) {
                    requestNotificationPermissionIfNeeded()
                } else {
                    Toast.makeText(
                        this,
                        "Microphone and Contacts permissions are required for FIN.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

            NOTIFICATION_REQUEST_CODE -> {
                refreshPermissions()
                startFinService()
            }
        }
    }

    private fun space(height: Int): View =
        View(this).apply {
            layoutParams = LinearLayout.LayoutParams(1, dp(height))
        }

    private fun actionButton(
        label: String,
        action: () -> Unit
    ): Button = Button(this).apply {
        text = label
        isAllCaps = false
        setTextColor(white)
        setBackgroundColor(Color.rgb(45, 45, 45))
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(-1, dp(54))
    }

    private fun refreshPermissions() {
        if (!::permissionStatus.isInitialized) return

        val items = mutableListOf(
            "Microphone" to Manifest.permission.RECORD_AUDIO,
            "Contacts" to Manifest.permission.READ_CONTACTS
        )

        if (Build.VERSION.SDK_INT >= 33) {
            items.add(
                "Notifications" to Manifest.permission.POST_NOTIFICATIONS
            )
        }

        permissionStatus.text = items.joinToString("\n\n") {
            (name, permission) ->

            val granted = checkSelfPermission(permission) ==
                PackageManager.PERMISSION_GRANTED

            "${if (granted) "✓" else "○"}  $name: " +
                if (granted) "Allowed" else "Not allowed"
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPermissions()
    }
}
