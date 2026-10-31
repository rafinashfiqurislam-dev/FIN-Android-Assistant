
package com.fin.assistant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.ContactsContract
import android.widget.Toast
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import kotlin.concurrent.thread

class FinVoiceService : Service() {

    private var audioRecord: AudioRecord? = null
    private var recognizer: OnlineRecognizer? = null
    private var isRunning = false
    private var audioThread: Thread? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    private lateinit var learningEngine: FinLearningEngine

    private val modelDir =
        "sherpa-onnx-streaming-zipformer-bn-vosk-2026-02-09"

    override fun onCreate() {
        super.onCreate()

        learningEngine = FinLearningEngine(this)

        createNotificationChannel()
        startForeground(7, notification())

        try {
            initializeRecognizer()
            startMicrophone()
        } catch (e: Exception) {
            showMessage("FIN engine error: ${e.message}")
            stopSelf()
        }
    }

    private fun initializeRecognizer() {
        val model = OnlineModelConfig(
            transducer = OnlineTransducerModelConfig(
                encoder = "$modelDir/encoder.onnx",
                decoder = "$modelDir/decoder.onnx",
                joiner = "$modelDir/joiner.onnx"
            ),
            tokens = "$modelDir/tokens.txt",
            modelType = "zipformer2",
            numThreads = 2,
            provider = "cpu"
        )

        val config = OnlineRecognizerConfig(
            featConfig = FeatureConfig(
                sampleRate = 16000,
                featureDim = 80
            ),
            modelConfig = model,
            enableEndpoint = true
        )

        recognizer = OnlineRecognizer(
            assetManager = assets,
            config = config
        )
    }

    private fun startMicrophone() {
        val minBuffer = AudioRecord.getMinBufferSize(
            16000,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        require(minBuffer > 0) {
            "Microphone configuration unavailable"
        }

        val bufferSize = maxOf(minBuffer, 16000 * 2)

        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            16000,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )

        require(recorder.state == AudioRecord.STATE_INITIALIZED) {
            "Microphone initialization failed"
        }

        audioRecord = recorder
        isRunning = true
        recorder.startRecording()

        audioThread = thread(name = "FIN-Offline-ASR") {
            processAudio(recorder)
        }
    }

    private fun processAudio(recorder: AudioRecord) {
        val stream = recognizer?.createStream() ?: return

        val shortBuffer = ShortArray(1600)
        val floatBuffer = FloatArray(1600)

        try {
            while (isRunning && !Thread.currentThread().isInterrupted) {
                val count = recorder.read(
                    shortBuffer,
                    0,
                    shortBuffer.size
                )

                if (count <= 0) continue

                for (i in 0 until count) {
                    floatBuffer[i] = shortBuffer[i] / 32768.0f
                }

                stream.acceptWaveform(
                    floatBuffer.copyOf(count),
                    16000
                )

                val engine = recognizer ?: break

                while (engine.isReady(stream)) {
                    engine.decode(stream)
                }

                if (engine.isEndpoint(stream)) {
                    val command = engine.getResult(stream).text.trim()

                    if (command.isNotBlank()) {
                        mainHandler.post {
                            if (isRunning) {
                                handleCommand(command)
                            }
                        }
                    }

                    engine.reset(stream)
                }
            }
        } catch (e: Exception) {
            showMessage("Voice processing error: ${e.message}")
        } finally {
            stream.release()
        }
    }

    private fun handleCommand(raw: String) {
        val text = raw.lowercase().trim()

        val apps = mapOf(
            "facebook" to "com.facebook.katana",
            "ফেসবুক" to "com.facebook.katana",
            "instagram" to "com.instagram.android",
            "ইনস্টাগ্রাম" to "com.instagram.android",
            "whatsapp" to "com.whatsapp",
            "হোয়াটসঅ্যাপ" to "com.whatsapp",
            "messenger" to "com.facebook.orca",
            "মেসেঞ্জার" to "com.facebook.orca"
        )

        var intentName = "UNKNOWN"
        var successful = false

        val app = apps.entries.firstOrNull {
            text.contains(it.key)
        }

        if (app != null &&
            (text.contains("open") ||
             text.contains("খোলো") ||
             text.contains("চালু"))) {

            intentName = "OPEN_APP"

            val launch = packageManager
                .getLaunchIntentForPackage(app.value)

            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                try {
                    startActivity(launch)
                    successful = true
                } catch (e: Exception) {
                    showMessage("Could not open app")
                }
            } else {
                showMessage("App not installed")
            }

            recordOutcome(raw, intentName, successful)
            return
        }

        if (text.contains("call") || text.contains("কল")) {
            intentName = "CALL_CONTACT"

            val name = text
                .replace("call", "")
                .replace("কল", "")
                .replace("করো", "")
                .replace("কর", "")
                .trim()

            successful = callContact(name)

            recordOutcome(raw, intentName, successful)
            return
        }

        if (text.contains("message") ||
            text.contains("মেসেজ") ||
            text.contains("বার্তা")) {

            intentName = "MESSAGE_CONTACT"

            val name = text
                .replace("message", "")
                .replace("মেসেজ", "")
                .replace("বার্তা", "")
                .replace("পাঠাও", "")
                .replace("করো", "")
                .replace("কর", "")
                .trim()

            successful = messageContact(name)

            recordOutcome(raw, intentName, successful)
            return
        }

        recordOutcome(raw, intentName, false)
        showMessage("Command not understood")
    }

    private fun recordOutcome(
        command: String,
        intent: String,
        successful: Boolean
    ) {
        try {
            learningEngine.observeOutcome(
                userCommand = command,
                detectedIntent = intent,
                successful = successful
            )
        } catch (e: Exception) {
            showMessage("Learning storage error")
        }
    }

    private fun findNumber(query: String): String? {
        if (query.isBlank()) return null

        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI

        contentResolver.query(
            uri,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
            ),
            null,
            null,
            null
        )?.use { cursor ->

            while (cursor.moveToNext()) {
                val number = cursor.getString(0)
                val name = cursor.getString(1).orEmpty()

                if (name.contains(query, ignoreCase = true)) {
                    return number
                }
            }
        }

        return null
    }

    private fun callContact(name: String): Boolean {
        val number = findNumber(name)

        if (number == null) {
            showMessage("Contact not found")
            return false
        }

        val intent = Intent(
            Intent.ACTION_DIAL,
            Uri.parse("tel:$number")
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return try {
            startActivity(intent)
            true
        } catch (e: Exception) {
            showMessage("Could not open dialer")
            false
        }
    }

    private fun messageContact(name: String): Boolean {
        val number = findNumber(name)

        if (number == null) {
            showMessage("Contact not found")
            return false
        }

        val intent = Intent(
            Intent.ACTION_SENDTO,
            Uri.parse("smsto:${Uri.encode(number)}")
        ).apply {
            putExtra("sms_body", "")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return try {
            startActivity(intent)
            true
        } catch (e: Exception) {
            showMessage("Could not open messaging app")
            false
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                "fin_voice",
                "FIN Voice Assistant",
                NotificationManager.IMPORTANCE_LOW
            )

            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    private fun notification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, "fin_voice")
        } else {
            Notification.Builder(this)
        }

        return builder
            .setContentTitle("FIN is active")
            .setContentText("Offline Bengali voice engine is running")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()
    }

    private fun showMessage(message: String) {
        mainHandler.post {
            Toast.makeText(
                this,
                message,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int = START_STICKY

    override fun onDestroy() {
        isRunning = false

        audioRecord?.let {
            try {
                it.stop()
                it.release()
            } catch (_: Exception) {
            }
        }

        audioRecord = null
        audioThread?.interrupt()

        recognizer?.release()
        recognizer = null

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
