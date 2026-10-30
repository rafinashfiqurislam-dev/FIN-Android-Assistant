package com.fin.assistant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.ContactsContract
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.telephony.PhoneNumberUtils
import android.widget.Toast

class FinVoiceService : Service() {
    private var recognizer: SpeechRecognizer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var listening = false
    private var awaitingFollowUp = false
    private val channelId = "fin_background_voice"

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(7, buildNotification())
        if (Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(this)) {
            recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(this)
            recognizer?.setRecognitionListener(listener)
            startListening()
        } else {
            stopSelf()
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(channelId, "FIN voice service", NotificationManager.IMPORTANCE_LOW)
            channel.description = "Shows when FIN's microphone service is active."
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= 26)
            Notification.Builder(this, channelId) else Notification.Builder(this)
        return builder.setContentTitle("FIN is active")
            .setContentText("Voice assistant is listening. Tap FIN to manage it.")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) { listening = true }
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() { listening = false }
        override fun onError(error: Int) { listening = false; scheduleRestart() }
        override fun onResults(results: Bundle?) {
            listening = false
            val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
            if (text.isNotBlank()) handleCommand(text)
            scheduleRestart()
        }
        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun startListening() {
        if (recognizer == null || listening) return
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "bn-BD")
            }
            recognizer?.startListening(intent)
        } catch (_: Exception) { scheduleRestart() }
    }

    private fun scheduleRestart() {
        handler.postDelayed({ if (recognizer != null) startListening() }, 900)
    }

    private fun normalize(s: String) = s.lowercase()
        .replace("hey fin", "").replace("হেই ফিন", "").replace("হে ফিন", "")
        .replace("হে", "").trim()

    private fun handleCommand(raw: String) {
        val text = normalize(raw)
        if (text.isBlank()) return

        val appPackages = mapOf(
            "facebook" to "com.facebook.katana",
            "ফেসবুক" to "com.facebook.katana",
            "instagram" to "com.instagram.android",
            "ইনস্টাগ্রাম" to "com.instagram.android",
            "whatsapp" to "com.whatsapp",
            "হোয়াটসঅ্যাপ" to "com.whatsapp",
            "মেসেঞ্জার" to "com.facebook.orca",
            "messenger" to "com.facebook.orca"
        )
        val app = appPackages.entries.firstOrNull { text.contains(it.key) }
        if (app != null && (text.contains("open") || text.contains("খোলো") || text.contains("চালু"))) {
            val launch = packageManager.getLaunchIntentForPackage(app.value)
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(launch)
            }
            return
        }

        if (text.contains("call") || text.contains("কল")) {
            val name = text.replace("call", "").replace("কল", "")
                .replace("করো", "").replace("কর", "").trim()
            callContact(name)
            return
        }

        if (text.contains("message") || text.contains("মেসেজ") || text.contains("বার্তা")) {
            val lateText = if (text.contains("late") || text.contains("দেরি")) "আমি ১০ মিনিট দেরি করে পৌঁছাব।" else ""
            val name = text.replace("message", "").replace("মেসেজ", "").replace("বার্তা", "")
                .replace("করো", "").replace("কর", "").replace("আমার", "")
                .replace("কে", "").replace("পাঠাও", "").replace("লিখে দাও", "").trim()
            messageContact(name, lateText)
            return
        }
    }

    private fun findNumber(query: String): String? {
        if (query.isBlank()) return null
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        contentResolver.query(uri, arrayOf(
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
        ), null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val number = cursor.getString(0)
                val name = cursor.getString(1).orEmpty().lowercase()
                if (name.contains(query.lowercase()) ||
                    (query.contains("mother") && (name.contains("mom") || name.contains("mother") || name.contains("মা") || name.contains("আম্মু"))) ||
                    (query.contains("মা") && (name.contains("mom") || name.contains("mother") || name.contains("মা") || name.contains("আম্মু")))) {
                    return number
                }
            }
        }
        return null
    }

    private fun callContact(name: String) {
        val number = findNumber(name) ?: return
        val dial = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(dial)
    }

    private fun messageContact(name: String, body: String) {
        val number = findNumber(name) ?: return
        val sms = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${Uri.encode(number)}")).apply {
            putExtra("sms_body", body)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(sms)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        recognizer?.destroy()
        recognizer = null
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
