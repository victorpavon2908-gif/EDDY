package com.eddy.assistant.background

import android.Manifest
import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.eddy.assistant.EddyWakeActivity
import com.eddy.assistant.MainActivity
import com.eddy.assistant.R
import com.eddy.assistant.actions.ActionExecutor
import com.eddy.assistant.ai.EddyAiClient
import com.eddy.assistant.ai.EddyFallbackConversation
import com.eddy.assistant.brain.AssistantCommand
import com.eddy.assistant.brain.LocalBrain
import com.eddy.assistant.memory.EddyMemory
import com.eddy.assistant.proactive.EddyProactiveScheduler
import com.eddy.assistant.smarthome.LocalSmartHomeClient
import com.eddy.assistant.voice.EddySpeechRecognizer
import com.eddy.assistant.voice.EddyTextToSpeech
import com.eddy.assistant.voice.WakeResult
import com.eddy.assistant.voice.WakeWordGate
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class EddyAssistantService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var brain: LocalBrain
    private lateinit var executor: ActionExecutor
    private lateinit var smartHome: LocalSmartHomeClient
    private lateinit var memory: EddyMemory
    private lateinit var wakeGate: WakeWordGate
    private lateinit var aiClient: EddyAiClient
    private lateinit var fallbackConversation: EddyFallbackConversation
    private lateinit var proactiveScheduler: EddyProactiveScheduler
    private lateinit var recognizer: EddySpeechRecognizer
    private lateinit var tts: EddyTextToSpeech

    private var isListening = false
    private var isThinking = false
    private var isSpeaking = false
    private var screenReceiverRegistered = false
    private var lastPartialWakeAt = 0L

    private var windowManager: WindowManager? = null
    private var bubbleView: View? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var cpuWakeLock: PowerManager.WakeLock? = null

    private val bubblePrefs by lazy {
        getSharedPreferences(BUBBLE_PREFS, Context.MODE_PRIVATE)
    }

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    acquireCpuWakeLock()
                    rearmRecognitionForScreenChange(500L)
                }

                Intent.ACTION_SCREEN_ON -> rearmRecognitionForScreenChange(250L)
                Intent.ACTION_USER_PRESENT -> rearmRecognitionForScreenChange(150L)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        brain = LocalBrain()
        executor = ActionExecutor(applicationContext)
        smartHome = LocalSmartHomeClient(applicationContext)
        memory = EddyMemory(applicationContext)
        wakeGate = WakeWordGate()
        aiClient = EddyAiClient()
        fallbackConversation = EddyFallbackConversation()
        proactiveScheduler = EddyProactiveScheduler(applicationContext, memory)

        recognizer = EddySpeechRecognizer(
            context = applicationContext,
            onListeningChanged = { listening ->
                isListening = listening
                updateVisualState()
            },
            onPartialResult = { partial ->
                EddyRuntimeState.setHeard(applicationContext, partial)
                handlePartialWakeWord(partial)
            },
            onResult = { raw -> handleRecognition(raw) },
            onError = { error ->
                isThinking = false
                EddyRuntimeState.setResponse(applicationContext, error)
                updateVisualState()
            },
        )

        tts = EddyTextToSpeech(
            context = applicationContext,
            onReady = { ready -> EddyRuntimeState.setVoiceReady(applicationContext, ready) },
            onSpeakingChanged = { speaking ->
                isSpeaking = speaking
                updateVisualState()
                if (!speaking && hasMicrophonePermission()) {
                    recognizer.resume()
                }
            },
        )

        acquireCpuWakeLock()
        registerScreenStateReceiver()
        EddyRuntimeState.setRunning(applicationContext, true)
        EddyRuntimeState.setResponse(applicationContext, "Decí EDDY para activarme.")
        createNotificationChannels()
        startAsForeground()

        if (hasMicrophonePermission()) {
            recognizer.startContinuous()
        } else {
            EddyRuntimeState.setResponse(
                applicationContext,
                "Abrí EDDY y concedé el permiso de micrófono para mantenerme activo.",
            )
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_SHOW_BUBBLE -> showBubble()
            ACTION_HIDE_BUBBLE -> hideBubble()
            ACTION_REFRESH_BUBBLE -> {
                hideBubble()
                showBubble()
            }

            else -> Unit
        }

        if (hasMicrophonePermission() && !isSpeaking && !isThinking) {
            recognizer.resume()
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        bubbleParams?.let(::saveBubblePosition)
        hideBubble()
        unregisterScreenStateReceiver()
        recognizer.destroy()
        tts.shutdown()
        releaseCpuWakeLock()
        serviceScope.cancel()
        EddyRuntimeState.reset(applicationContext)
        super.onDestroy()
    }

    private fun handlePartialWakeWord(partial: String) {
        if (!wakeGate.hasWakeWord(partial)) return

        val now = System.currentTimeMillis()
        if (now - lastPartialWakeAt < PARTIAL_WAKE_DEBOUNCE_MS) return
        lastPartialWakeAt = now

        EddyRuntimeState.setHeard(applicationContext, "EDDY")
        EddyRuntimeState.setResponse(applicationContext, "Te escucho.")
        revealEddyOnLockScreen()
    }

    private fun handleRecognition(raw: String) {
        when (val wakeResult = wakeGate.consume(raw)) {
            WakeResult.Ignored -> {
                EddyRuntimeState.setHeard(applicationContext, "")
                EddyRuntimeState.setResponse(applicationContext, "Decí EDDY para activarme.")
                recognizer.resume()
            }

            WakeResult.Activated -> {
                EddyRuntimeState.setHeard(applicationContext, "EDDY")
                EddyRuntimeState.setResponse(applicationContext, "Te escucho. Decime.")
                revealEddyOnLockScreen()
                recognizer.resume()
            }

            is WakeResult.Command -> {
                EddyRuntimeState.setHeard(applicationContext, wakeResult.text)
                revealEddyOnLockScreen()
                recognizer.pause()
                isThinking = true
                updateVisualState()

                serviceScope.launch {
                    try {
                        delay(120)
                        handleCommand(wakeResult.text)
                    } finally {
                        isThinking = false
                        updateVisualState()
                    }
                }
            }
        }
    }

    private suspend fun handleCommand(text: String) {
        memory.rememberUserTurn(text)
        val command = brain.understand(text)

        if (command == AssistantCommand.ClearMemory) {
            memory.clearAll()
            speakResponse("De una. Borré mi memoria local y cancelé mis sugerencias programadas. Empezamos de nuevo.")
            return
        }

        memory.rememberCommand(command)
        proactiveScheduler.maybeSchedule(command)

        val response = when (command) {
            AssistantCommand.Greeting -> "De una, aquí estoy. Decime qué ocupás."

            AssistantCommand.TellTime -> {
                val time = SimpleDateFormat("h:mm a", Locale.forLanguageTag("es-NI")).format(Date())
                "Son las $time."
            }

            AssistantCommand.OpenCamera -> executor.openCamera().spokenMessage
            AssistantCommand.MemorySummary -> memory.describeLearnedPatterns()
            AssistantCommand.ClearMemory -> "Ya borré mi memoria local."
            is AssistantCommand.OpenApp -> executor.openApp(command.app).spokenMessage
            is AssistantCommand.Dial -> executor.dial(command.number).spokenMessage
            is AssistantCommand.ComposeMessage -> executor.composeMessage(command.number, command.message).spokenMessage
            is AssistantCommand.WhatsAppMessage -> executor.whatsappMessage(command.number, command.message).spokenMessage
            is AssistantCommand.PlaySpotify -> executor.playSpotify(command.query).spokenMessage
            is AssistantCommand.SetAlarm -> executor.setAlarm(command.hour, command.minute, command.label).spokenMessage
            is AssistantCommand.SetTimer -> executor.setTimer(command.seconds, command.label).spokenMessage
            is AssistantCommand.OpenMaps -> executor.openMaps(command.query).spokenMessage
            is AssistantCommand.SearchWeb -> executor.searchWeb(command.query).spokenMessage
            is AssistantCommand.ShareText -> executor.shareText(command.text).spokenMessage
            is AssistantCommand.SetTorch -> executor.setTorch(command.enabled).spokenMessage
            is AssistantCommand.SetVolume -> executor.setVolume(command.percent).spokenMessage
            is AssistantCommand.AdjustVolume -> executor.adjustVolume(command.direction).spokenMessage
            is AssistantCommand.SetBrightness -> executor.setBrightness(command.percent).spokenMessage
            is AssistantCommand.OpenSystemPanel -> executor.openSystemPanel(command.panel).spokenMessage
            AssistantCommand.BatteryStatus -> executor.batteryStatus().spokenMessage
            is AssistantCommand.Vibrate -> executor.vibrate(command.milliseconds).spokenMessage
            is AssistantCommand.SmartHomeControl -> smartHome.control(command.target, command.enabled).spokenMessage
            AssistantCommand.OpenSmartHomeSettings -> executor.openSmartHomeSettings().spokenMessage
            is AssistantCommand.Unknown -> {
                aiClient.reply(
                    message = command.originalText,
                    memoryContext = memory.contextForAi(),
                ) ?: fallbackConversation.reply(command.originalText, memory)
            }
        }

        speakResponse(response)
    }

    private fun speakResponse(text: String) {
        EddyRuntimeState.setResponse(applicationContext, text)
        memory.rememberAssistantTurn(text)
        recognizer.pause()
        val queued = tts.speak(text)
        if (!queued && hasMicrophonePermission()) {
            recognizer.resume()
        }
    }

    private fun updateVisualState() {
        val state = when {
            isSpeaking -> EddyRuntimeState.State.SPEAKING
            isThinking -> EddyRuntimeState.State.THINKING
            isListening -> EddyRuntimeState.State.LISTENING
            else -> EddyRuntimeState.State.IDLE
        }
        EddyRuntimeState.setState(applicationContext, state)
    }

    private fun rearmRecognitionForScreenChange(delayMs: Long) {
        if (!hasMicrophonePermission() || isSpeaking || isThinking) return
        serviceScope.launch {
            delay(delayMs)
            if (hasMicrophonePermission() && !isSpeaking && !isThinking) {
                recognizer.restart(0L)
            }
        }
    }

    private fun registerScreenStateReceiver() {
        if (screenReceiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(
            this,
            screenStateReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        screenReceiverRegistered = true
    }

    private fun unregisterScreenStateReceiver() {
        if (!screenReceiverRegistered) return
        runCatching { unregisterReceiver(screenStateReceiver) }
        screenReceiverRegistered = false
    }

    private fun hasMicrophonePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java) ?: return

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "EDDY activo",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Mantiene a EDDY disponible por voz en segundo plano."
                setShowBadge(false)
            },
        )

        manager.createNotificationChannel(
            NotificationChannel(
                WAKE_CHANNEL_ID,
                "Despertar EDDY",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Permite mostrar EDDY cuando lo llamás con el teléfono bloqueado."
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            },
        )
    }

    private fun startAsForeground() {
        val openIntent = Intent(this, MainActivity::class.java)
        val openPendingIntent = PendingIntent.getActivity(
            this,
            10,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val stopIntent = Intent(this, EddyAssistantService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            11,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_eddy_notification)
            .setContentTitle("EDDY está activo")
            .setContentText("Decí “EDDY” para activarlo, incluso con la pantalla bloqueada.")
            .setContentIntent(openPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(0, "Detener", stopPendingIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun revealEddyOnLockScreen() {
        val keyguardManager = getSystemService(KeyguardManager::class.java)
        val powerManager = getSystemService(PowerManager::class.java)
        val locked = keyguardManager?.isKeyguardLocked == true
        val screenOff = powerManager?.isInteractive == false

        if (!locked && !screenOff) return

        wakeScreenBriefly()

        val wakeIntent = Intent(this, EddyWakeActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP,
            )
        }
        val wakePendingIntent = PendingIntent.getActivity(
            this,
            12,
            wakeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val manager = getSystemService(NotificationManager::class.java) ?: return
        val notification = NotificationCompat.Builder(this, WAKE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_eddy_notification)
            .setContentTitle("EDDY")
            .setContentText("Te escucho.")
            .setContentIntent(wakePendingIntent)
            .setFullScreenIntent(wakePendingIntent, true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setTimeoutAfter(30_000L)
            .build()

        manager.notify(WAKE_NOTIFICATION_ID, notification)
    }

    @Suppress("DEPRECATION")
    private fun wakeScreenBriefly() {
        val powerManager = getSystemService(PowerManager::class.java) ?: return
        if (powerManager.isInteractive) return

        runCatching {
            val wakeLock = powerManager.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "$packageName:eddy_screen_wake",
            )
            wakeLock.acquire(6_000L)
        }
    }

    private fun acquireCpuWakeLock() {
        val powerManager = getSystemService(PowerManager::class.java) ?: return
        if (cpuWakeLock?.isHeld == true) return

        cpuWakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "$packageName:eddy_always_listening",
        ).apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun releaseCpuWakeLock() {
        val wakeLock = cpuWakeLock ?: return
        if (wakeLock.isHeld) {
            runCatching { wakeLock.release() }
        }
        cpuWakeLock = null
    }

    private fun showBubble() {
        if (!Settings.canDrawOverlays(this) || bubbleView != null) return

        val wm = getSystemService(WindowManager::class.java) ?: return
        windowManager = wm

        val bubbleSize = dp(68)
        val container = FrameLayout(this).apply {
            elevation = dp(12).toFloat()
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.WHITE)
                setStroke(dp(2), Color.BLACK)
            }
            setPadding(dp(9), dp(9), dp(9), dp(9))
            contentDescription = "Abrir EDDY"
        }

        val icon = ImageView(this).apply {
            setImageResource(R.drawable.ic_eddy)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            contentDescription = null
        }
        container.addView(
            icon,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )

        val dot = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.rgb(67, 221, 179))
                setStroke(dp(1), Color.WHITE)
            }
        }
        container.addView(
            dot,
            FrameLayout.LayoutParams(dp(14), dp(14), Gravity.END or Gravity.BOTTOM).apply {
                rightMargin = dp(1)
                bottomMargin = dp(1)
            },
        )

        val metrics = resources.displayMetrics
        val maxX = (metrics.widthPixels - bubbleSize).coerceAtLeast(0)
        val maxY = (metrics.heightPixels - bubbleSize - dp(24)).coerceAtLeast(0)
        val defaultX = (metrics.widthPixels - bubbleSize - dp(14)).coerceAtLeast(0)
        val defaultY = dp(180).coerceAtMost(maxY)

        val params = WindowManager.LayoutParams(
            bubbleSize,
            bubbleSize,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = bubblePrefs.getInt(KEY_BUBBLE_X, defaultX).coerceIn(0, maxX)
            y = bubblePrefs.getInt(KEY_BUBBLE_Y, defaultY).coerceIn(0, maxY)
        }

        container.setOnTouchListener(createBubbleTouchListener(params, container))

        runCatching {
            wm.addView(container, params)
            bubbleView = container
            bubbleParams = params
        }
    }

    private fun hideBubble() {
        bubbleParams?.let(::saveBubblePosition)
        val view = bubbleView ?: return
        runCatching { windowManager?.removeView(view) }
        bubbleView = null
        bubbleParams = null
    }

    private fun createBubbleTouchListener(
        params: WindowManager.LayoutParams,
        view: View,
    ): View.OnTouchListener {
        var startX = 0
        var startY = 0
        var downRawX = 0f
        var downRawY = 0f
        var moved = false

        return View.OnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x
                    startY = params.y
                    downRawX = event.rawX
                    downRawY = event.rawY
                    moved = false
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downRawX).toInt()
                    val dy = (event.rawY - downRawY).toInt()
                    if (kotlin.math.abs(dx) > dp(5) || kotlin.math.abs(dy) > dp(5)) moved = true

                    val metrics = resources.displayMetrics
                    params.x = (startX + dx).coerceIn(0, (metrics.widthPixels - view.width).coerceAtLeast(0))
                    params.y = (startY + dy).coerceIn(0, (metrics.heightPixels - view.height).coerceAtLeast(0))
                    runCatching { windowManager?.updateViewLayout(view, params) }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    if (moved) {
                        saveBubblePosition(params)
                    } else {
                        openMainActivity()
                    }
                    true
                }

                MotionEvent.ACTION_CANCEL -> {
                    if (moved) saveBubblePosition(params)
                    true
                }

                else -> false
            }
        }
    }

    private fun saveBubblePosition(params: WindowManager.LayoutParams) {
        bubblePrefs.edit()
            .putInt(KEY_BUBBLE_X, params.x)
            .putInt(KEY_BUBBLE_Y, params.y)
            .apply()
    }

    private fun openMainActivity() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(intent)
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    companion object {
        const val ACTION_SHOW_BUBBLE = "com.eddy.assistant.action.SHOW_BUBBLE"
        const val ACTION_HIDE_BUBBLE = "com.eddy.assistant.action.HIDE_BUBBLE"
        const val ACTION_REFRESH_BUBBLE = "com.eddy.assistant.action.REFRESH_BUBBLE"
        const val ACTION_STOP = "com.eddy.assistant.action.STOP"

        private const val CHANNEL_ID = "eddy_background_service"
        private const val WAKE_CHANNEL_ID = "eddy_wake_screen"
        private const val NOTIFICATION_ID = 4_310
        private const val WAKE_NOTIFICATION_ID = 4_311
        private const val PARTIAL_WAKE_DEBOUNCE_MS = 1_500L

        private const val BUBBLE_PREFS = "eddy_bubble"
        private const val KEY_BUBBLE_X = "bubble_x"
        private const val KEY_BUBBLE_Y = "bubble_y"
    }
}
