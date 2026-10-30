package com.fin.assistant

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.graphics.Color

class MainActivity : Activity() {
    private val permissions = mutableListOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.CALL_PHONE
    ).apply {
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
    }.toTypedArray()

    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        refreshStatus()
    }

    private fun buildUi() {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 36, 28, 28)
            setBackgroundColor(Color.WHITE)
        }
        val title = TextView(this).apply {
            text = "FIN"
            textSize = 36f
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
        }
        val subtitle = TextView(this).apply {
            text = "YOUR SILENT PERSONAL ASSISTANT"
            textSize = 11f
            setTextColor(Color.DKGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 4, 0, 32)
        }
        status = TextView(this).apply {
            textSize = 15f
            setTextColor(Color.BLACK)
            setPadding(0, 12, 0, 24)
        }
        val settings = Button(this).apply {
            text = "OPEN ANDROID PERMISSION SETTINGS"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:$packageName")))
            }
        }
        val activate = Button(this).apply {
            text = "ACTIVATE FIN"
            setOnClickListener { activateFin() }
        }
        val stop = Button(this).apply {
            text = "STOP FIN"
            setOnClickListener { stopService(Intent(this@MainActivity, FinVoiceService::class.java)); refreshStatus() }
        }
        val note = TextView(this).apply {
            text = "FIN uses your phone's on-device speech recognition when supported. Android may show a microphone privacy indicator and an ongoing service notification."
            textSize = 12f
            setTextColor(Color.DKGRAY)
            setPadding(0, 28, 0, 0)
        }
        page.addView(title)
        page.addView(subtitle)
        page.addView(status)
        page.addView(settings)
        page.addView(activate)
        page.addView(stop)
        page.addView(note)
        setContentView(page)
    }

    private fun activateFin() {
        val missing = permissions.filter {
            checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            requestPermissions(missing.toTypedArray(), 100)
            return
        }
        if (Build.VERSION.SDK_INT >= 31 &&
            !android.speech.SpeechRecognizer.isOnDeviceRecognitionAvailable(this)) {
            status.text = "On-device speech recognition is not available on this phone. FIN cannot start offline listening yet."
            return
        }
        val intent = Intent(this, FinVoiceService::class.java)
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent) else startService(intent)
        status.text = "FIN activation requested."
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refreshStatus()
        if (requestCode == 100 && grantResults.isNotEmpty() &&
            grantResults.all { it == PackageManager.PERMISSION_GRANTED }) activateFin()
    }

    private fun refreshStatus() {
        val names = listOf(
            "Microphone" to Manifest.permission.RECORD_AUDIO,
            "Contacts" to Manifest.permission.READ_CONTACTS,
            "Phone calls" to Manifest.permission.CALL_PHONE
        )
        status.text = names.joinToString("\n") { (label, permission) ->
            val ok = checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
            "${if (ok) "✓" else "✗"}  $label"
        }
    }
}
