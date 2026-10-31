
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
    @Volatile private var isRunning = false
    private var audioThread: Thread? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    private lateinit var learningEngine: FinLearningEngine
    private lateinit var commandUnderstanding: FinCommandUnderstanding

    private val modelDir =
        "sherpa-onnx-streaming-zipformer-bn-vosk-2026-02-09"

    override fun onCreate() {
        super.onCreate()

        learningEngine = FinLearningEngine(this)
        commandUnderstanding = FinCommandUnderstanding(learningEngine)

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

        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            16000,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBuffer, 16000 * 2)
        )

        require(recorder.state == AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            "Microphone initialization failed"
        }

        audioRecord = recorder
        recorder.startRecording()
        isRunning = true

        audioThread = thread(name = "FIN-Offline-ASR") {
            processAudio(recorder)
        }
    }

    private fun processAudio(recorder: AudioRecord) {
        val engine = recognizer ?: return
        val stream = engine.createStream()

        val shortBuffer = ShortArray(1600)
        val floatBuffer = FloatArray(1600)

        try {
            while (
                isRunning &&
                !Thread.currentThread().isInterrupted
            ) {
                val count = recorder.read(
                    shortBuffer,
                    0,
                    shortBuffer.size
                )

                if (count <= 0) {
                    if (!isRunning) break
                    continue
                }

                for (i in 0 until count) {
                    floatBuffer[i] = shortBuffer[i] / 32768.0f
                }

                stream.acceptWaveform(
                    floatBuffer.copyOf(count),
                    16000
                )

                while (engine.isReady(stream)) {
                    engine.decode(stream)
                }

                if (engine.isEndpoint(stream)) {
                    val command =
                        engine.getResult(stream).text.trim()

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
            if (isRunning) {
                showMessage("Voice processing error: ${e.message}")
            }
        } finally {
            try {
                stream.release()
            } catch (_: Exception) {
            }

            if (!isRunning) {
                releaseRecognizerSafely(engine)
            }
        }
    }

    private fun releaseRecognizerSafely(
        engine: OnlineRecognizer
    ) {
        synchronized(this) {
            if (recognizer === engine) {
                recognizer = null
                try {
                    engine.release()
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun handleCommand(raw: String) {
        val text = raw.lowercase().trim()
        if (text.isBlank()) return

        val understood = commandUnderstanding.understand(raw)

        val apps = listOf(
            "facebook" to "com.facebook.katana",
            "ফেসবুক" to "com.facebook.katana",
            "instagram" to "com.instagram.android",
            "ইনস্টাগ্রাম" to "com.instagram.android",
            "whatsapp" to "com.whatsapp",
            "হোয়াটসঅ্যাপ" to "com.whatsapp",
            "messenger" to "com.facebook.orca",
            "মেসেঞ্জার" to "com.facebook.orca",
            "youtube" to "com.google.android.youtube",
            "ইউটিউব" to "com.google.android.youtube",
            "chrome" to "com.android.chrome",
            "ক্রোম" to "com.android.chrome",
            "twitter" to "com.twitter.android",
            "এক্স" to "com.twitter.android",
            "chatgpt" to "com.openai.chatgpt",
            "চ্যাটজিপিটি" to "com.openai.chatgpt",
            "google" to "com.google.android.googlequicksearchbox",
            "গুগল" to "com.google.android.googlequicksearchbox",
            "play store" to "com.android.vending",
            "প্লে স্টোর" to "com.android.vending"
        )

        val openVerb = containsAny(
            text,
            "open",
            "খোলো",
            "খুলে দাও",
            "চালু",
            "ওপেন"
        )

        val callVerb = containsAny(
            text,
            "call",
            "কল",
            "ফোন করো",
            "ফোন দাও"
        )

        val messageVerb = containsAny(
            text,
            "message",
            "মেসেজ",
            "বার্তা",
            "এসএমএস"
        )

        val app = apps.firstOrNull {
            containsPhrase(text, it.first)
        }

        var intentName = understood.intent
        var successful = false

        when (intentName) {
            FinCommandUnderstanding.OPEN_APP -> {
                if (!openVerb || app == null) {
                    recordOutcome(raw, "UNKNOWN", false)
                    showMessage("Please specify an app to open")
                    return
                }

                val launchIntent =
                    packageManager.getLaunchIntentForPackage(app.second)

                if (launchIntent == null) {
                    showMessage("App not installed")
                } else {
                    launchIntent.addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK
                    )

                    successful = try {
                        startActivity(launchIntent)
                        true
                    } catch (_: Exception) {
                        showMessage("Could not open app")
                        false
                    }
                }
            }

            FinCommandUnderstanding.CALL_CONTACT -> {
                if (!callVerb) {
                    recordOutcome(raw, "UNKNOWN", false)
                    showMessage("Please say who you want to call")
                    return
                }

                val name = removeCommandWords(
                    text,
                    "call",
                    "কল",
                    "ফোন করো",
                    "ফোন দাও",
                    "করো",
                    "কর"
                )

                successful = callContact(name)
            }

            FinCommandUnderstanding.MESSAGE_CONTACT -> {
                if (!messageVerb) {
                    recordOutcome(raw, "UNKNOWN", false)
                    showMessage("Please specify a contact")
                    return
                }

                val name = removeCommandWords(
                    text,
                    "message",
                    "মেসেজ",
                    "বার্তা",
                    "এসএমএস",
                    "পাঠাও",
                    "করো",
                    "কর"
                )

                successful = messageContact(name)
            }

            else -> {
                intentName = "UNKNOWN"
                showMessage("Command not understood")
            }
        }

        recordOutcome(raw, intentName, successful)
    }

    private fun containsPhrase(
        text: String,
        phrase: String
    ): Boolean {
        val escaped = Regex.escape(phrase)

        val pattern = if (phrase.any { it in 'a'..'z' || it in 'A'..'Z' }) {
            Regex(
                "(?<![a-z0-9])$escaped(?![a-z0-9])",
                RegexOption.IGNORE_CASE
            )
        } else {
            Regex("(^|\\s)$escaped($|\\s|[।,.!?])")
        }

        return pattern.containsMatchIn(text)
    }

    private fun removeCommandWords(
        text: String,
        vararg words: String
    ): String {
        var result = text

        words.sortedByDescending { it.length }.forEach { word ->
            result = result.replace(word, " ")
        }

        return result.trim()
    }

    private fun containsAny(
        text: String,
        vararg words: String
    ): Boolean {
        return words.any { text.contains(it) }
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
        } catch (_: Exception) {
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
        } catch (_: Exception) {
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
        } catch (_: Exception) {
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

        val recorder = audioRecord
        audioRecord = null

        try {
            recorder?.stop()
        } catch (_: Exception) {
        }

        audioThread?.interrupt()

        try {
            audioThread?.join(1500)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }

        try {
            recorder?.release()
        } catch (_: Exception) {
        }

        val worker = audioThread
        if (worker == null || !worker.isAlive) {
            recognizer?.let {
                releaseRecognizerSafely(it)
            }
        }

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
