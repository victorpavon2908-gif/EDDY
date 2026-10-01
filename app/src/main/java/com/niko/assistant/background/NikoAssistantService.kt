package com.niko.assistant.background

import com.niko.assistant.compat.UpgradeIdentity

import android.Manifest
import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import com.niko.assistant.ui.robot.RobotMotion
import com.niko.assistant.ui.robot.RobotMotionBus
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
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.niko.assistant.NikoWakeActivity
import com.niko.assistant.MainActivity
import com.niko.assistant.R
import com.niko.assistant.media.CaptureGate
import kotlinx.coroutines.cancelAndJoin
import com.niko.assistant.actions.ActionExecutor
import com.niko.assistant.ai.NikoAiSettings
import com.niko.assistant.ai.AutonomousResearch
import com.niko.assistant.ai.ConversationCoordinator
import com.niko.assistant.ai.NikoAiClient
import com.niko.assistant.ai.NikoAiReply
import com.niko.assistant.ai.NikoWebSource
import com.niko.assistant.ai.NikoFallbackConversation
import com.niko.assistant.brain.AssistantCommand
import com.niko.assistant.brain.NikoMathEngine
import com.niko.assistant.brain.LocalBrain
import com.niko.assistant.brain.NikoSemanticActionResolver
import com.niko.assistant.brain.WebQueryRouter
import com.niko.assistant.devicecontrol.NikoUiAutomationAgent
import com.niko.assistant.devicecontrol.NikoUiTaskPolicy
import com.niko.assistant.learning.AdaptiveIntentStore
import com.niko.assistant.learning.InteractionCorrection
import com.niko.assistant.learning.LearnedIntent
import com.niko.assistant.learning.LearnedActionCodec
import com.niko.assistant.learning.LearnedActionStore
import com.niko.assistant.learning.LeoIntentTrainingCorpus
import com.niko.assistant.learning.OnlineIntentNetwork
import com.niko.assistant.localai.NikoDeviceProfile
import com.niko.assistant.localai.NikoLocalLlm
import com.niko.assistant.localai.NikoModelManager
import com.niko.assistant.localai.NikoModelProgress
import com.niko.assistant.localai.NikoVoiceProfile
import com.niko.assistant.memory.NikoMemory
import com.niko.assistant.programming.NikoCodeAgent
import com.niko.assistant.proactive.NikoProactiveScheduler
import com.niko.assistant.smarthome.LocalSmartHomeClient
import com.niko.assistant.voice.NikoLocalVoiceEngine
import com.niko.assistant.voice.NikoNeuralTextToSpeech
import com.niko.assistant.voice.NikoTextToSpeech
import com.niko.assistant.voice.VoiceRecoveryPolicy
import com.niko.assistant.voice.SpeechOutputPolicy
import com.niko.assistant.voice.ProgressiveSpeech
import com.niko.assistant.voice.SpeechProsody
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import com.niko.assistant.voice.VoiceControl
import com.niko.assistant.voice.LeoVoiceDiagnostics
import com.niko.assistant.voice.LeoRealtimeTurnBus
import com.niko.assistant.voice.LeoPlatformVoiceEngine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

open class NikoAssistantService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // El servicio de micrófono debe llegar a startForeground() antes de construir
    // componentes pesados. Todo lo demás se crea únicamente cuando una orden lo necesita.
    private val brain by lazy(LazyThreadSafetyMode.NONE) { LocalBrain() }
    private val executor by lazy(LazyThreadSafetyMode.NONE) { ActionExecutor(applicationContext) }
    private val smartHome by lazy(LazyThreadSafetyMode.NONE) { LocalSmartHomeClient(applicationContext) }
    private val memory by lazy(LazyThreadSafetyMode.NONE) { NikoMemory(applicationContext) }
    private val webClient by lazy(LazyThreadSafetyMode.NONE) { NikoAiClient(applicationContext) }
    private val fallbackConversation by lazy(LazyThreadSafetyMode.NONE) { NikoFallbackConversation() }
    private val proactiveScheduler by lazy(LazyThreadSafetyMode.NONE) { NikoProactiveScheduler(applicationContext, memory) }
    private val modelManager by lazy(LazyThreadSafetyMode.NONE) { NikoModelManager(applicationContext) }
    private val deviceProfile by lazy(LazyThreadSafetyMode.NONE) { NikoDeviceProfile.detect(applicationContext) }
    private val ownerVoice by lazy(LazyThreadSafetyMode.NONE) { NikoVoiceProfile(applicationContext) }

    private val localLlmDelegate = lazy(LazyThreadSafetyMode.NONE) {
        NikoLocalLlm(applicationContext, modelManager)
    }
    private val localLlm: NikoLocalLlm get() = localLlmDelegate.value

    private val uiAutomation by lazy(LazyThreadSafetyMode.NONE) { NikoUiAutomationAgent(localLlm) }
    private val semanticActions by lazy(LazyThreadSafetyMode.NONE) {
        NikoSemanticActionResolver(brain) { prompt -> localLlm.completeStructured(prompt) }
    }
    private val codeAgent by lazy(LazyThreadSafetyMode.NONE) { NikoCodeAgent(applicationContext) }
    private val adaptiveStore by lazy {
        AdaptiveIntentStore(File(filesDir, "adaptive_learning")) {
            runCatching { assets.open(LeoIntentTrainingCorpus.ASSET_NAME).use { it.readBytes() } }.getOrNull()
        }
    }
    private val learnedActionStore by lazy { LearnedActionStore(File(filesDir, "adaptive_learning")) }
    @Volatile private var adaptiveNetwork: OnlineIntentNetwork? = null
    @Volatile private var adaptiveUnavailable = false
    @Volatile private var learningEpoch = 0L
    private val adaptiveLoading = AtomicBoolean(false)
    private val learningMutex = Mutex()
    private var lastTrainableUtterance: String? = null
    private var replyProsody = SpeechProsody()
    private val speechOutput = SpeechOutputPolicy()
    private var localVoice: NikoLocalVoiceEngine? = null
    private var platformVoice: LeoPlatformVoiceEngine? = null
    private var localVoiceActive = false
    private val platformTtsDelegate = lazy(LazyThreadSafetyMode.NONE) {
        NikoTextToSpeech(
            context = applicationContext,
            onReady = { ready ->
                if (speechOutput.selected != SpeechOutputPolicy.Backend.NEURAL) {
                    NikoRuntimeState.setVoiceReady(applicationContext, ready)
                }
            },
            onVoiceSelected = { description ->
                if (speechOutput.selected != SpeechOutputPolicy.Backend.NEURAL) {
                    NikoRuntimeState.setVoiceStatus(applicationContext, description)
                }
            },
            onSpeakingChanged = ::onSpeakingChanged,
        )
    }
    private val platformTts: NikoTextToSpeech get() = platformTtsDelegate.value

    private val neuralTtsDelegate = lazy(LazyThreadSafetyMode.NONE) {
        NikoNeuralTextToSpeech(
            models = modelManager,
            profile = deviceProfile,
            onSpeakingChanged = ::onSpeakingChanged,
            canUseFallback = { platformTts.isReady },
            onFailure = { text, audioStarted -> serviceScope.launch {
                if (!destroyed) {
                    speechOutput.neuralFailed()
                    NikoRuntimeState.setVoiceStatus(applicationContext, "La voz local se interrumpió. ${platformTts.voiceDescription}")
                    if (audioStarted || !platformTts.speak(text, replyProsody)) onSpeakingChanged(false)
                    NikoRuntimeState.setVoiceReady(applicationContext, platformTts.isReady)
                }
            } },
        )
    }
    private val neuralTts: NikoNeuralTextToSpeech get() = neuralTtsDelegate.value

    private var destroyed = false
    private var foregroundReady = false
    @Volatile private var voiceRequested = false
    @Volatile private var platformVoiceDisabledForSession = false
    private var localVoiceStarting = false
    private var localVoiceEpoch = 0
    private var isTranscribing = false
    private var commandJob: Job? = null
    private var progressiveSpeech: ProgressiveSpeech? = null
    private var turnEpoch = 0L
    private val turnInterrupter: () -> Unit = { serviceScope.launch { interruptCurrentTurn() }; Unit }
    private var speechTimeout: Job? = null
    private var continueAfterSpeech = false
    private var recoveryJob: Job? = null
    private val voiceRecovery = VoiceRecoveryPolicy()
    private var initializationFailure: NikoLocalVoiceEngine.InitializationFailure? = null
    private var isListening = false
    private var isThinking = false
    private var isSpeaking = false
    private var screenReceiverRegistered = false
    private var windowManager: WindowManager? = null
    private var bubbleView: View? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var cpuWakeLock: PowerManager.WakeLock? = null

    private val bubblePrefs by lazy { getSharedPreferences(BUBBLE_PREFS, Context.MODE_PRIVATE) }

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> { acquireCpuWakeLock(); ensureVoiceListening() }
                Intent.ACTION_SCREEN_ON -> ensureVoiceListening()
                Intent.ACTION_USER_PRESENT -> ensureVoiceListening()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        LeoRealtimeTurnBus.registerTurnInterrupter(turnInterrupter)
        if (!hasMicrophonePermission()) {
            NikoRuntimeState.setResponse(applicationContext, "Abrí LEO y concedé el permiso de micrófono.")
            stopSelf()
            return
        }
        createNotificationChannels()
        try { startAsForeground() } catch (_: RuntimeException) {
            NikoRuntimeState.setResponse(applicationContext, "Abrí LEO para activar el micrófono.")
            stopSelf()
            return
        }
        foregroundReady = true

        // Inicializá la voz del sistema cuanto antes. Antes se construía por primera vez
        // al terminar una orden; si todavía no estaba lista, el código elegía la voz neural
        // ONNX y podía abortar el proceso justo después de mostrar "Procesando tu petición…".
        // Esto NO cambia la escucha: solo prepara la salida de voz segura de Android.
        runCatching { platformTts.isReady }

        CaptureGate.pauseVoice = {
            interruptCurrentTurn()
            LeoRealtimeTurnBus.interruptSpeech()
            ++localVoiceEpoch
            recoveryJob?.cancelAndJoin()
            localVoiceActive = false
            stopActiveVoiceAndAwait()
        }
        CaptureGate.resumeVoice = { ensureVoiceListening() }
        acquireCpuWakeLock()
        registerScreenStateReceiver()
        NikoRuntimeState.setRunning(applicationContext, true)
        NikoRuntimeState.setInput(applicationContext, NikoRuntimeState.InputState.PREPARING, "Preparando activación por voz…")
        NikoRuntimeState.setResponse(applicationContext, "LEO está listo para activar el micrófono.")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                voiceRequested = true
                NikoVoiceSettings.setEnabled(this, true)
            }
            ACTION_STOP -> {
                voiceRequested = false
                NikoVoiceSettings.setEnabled(this, false)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_SHOW_BUBBLE -> showBubble()
            ACTION_HIDE_BUBBLE -> hideBubble()
            ACTION_REFRESH_BUBBLE -> { hideBubble(); showBubble() }
            ACTION_RESET_BUBBLE -> resetBubblePosition()
        }
        if (!foregroundReady) { stopSelf(); return START_NOT_STICKY }
        if (!voiceRequested && intent?.action != ACTION_START) {
            stopSelf()
            return START_NOT_STICKY
        }
        ensureVoiceListening()
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        destroyed = true
        CaptureGate.pauseVoice = null
        CaptureGate.resumeVoice = null
        ++turnEpoch
        LeoRealtimeTurnBus.unregisterTurnInterrupter(turnInterrupter)
        LeoVoiceDiagnostics.cancelResponseTiming()
        progressiveSpeech?.cancel()
        progressiveSpeech = null
        commandJob?.cancel()
        speechTimeout?.cancel()
        recoveryJob?.cancel()
        bubbleParams?.let(::saveBubblePosition)
        hideBubble()
        unregisterScreenStateReceiver()
        stopActiveVoice()
        if (neuralTtsDelegate.isInitialized()) neuralTts.shutdown()
        if (platformTtsDelegate.isInitialized()) platformTts.shutdown()
        if (localLlmDelegate.isInitialized()) localLlm.release()
        releaseCpuWakeLock()
        serviceScope.cancel()
        NikoRuntimeState.reset(applicationContext)
        super.onDestroy()
    }

    /** One owner for preparation, native startup and recovery. No Android recognition sessions. */
    private fun ensureVoiceListening(initialDelay: Long = 0L) {
        if (CaptureGate.held || destroyed || !foregroundReady || localVoiceActive || recoveryJob?.isActive == true || !voiceRequested) return
        recoveryJob = serviceScope.launch {
            if (initialDelay > 0) delay(initialDelay)
            while (!CaptureGate.held && !destroyed && voiceRequested) {
                if (!hasMicrophonePermission()) {
                    inputUnavailable("Concedé permiso de micrófono en los ajustes de Android y volvé a abrir LEO.")
                    return@launch
                }
                while (isThinking || isSpeaking || commandJob?.isActive == true) delay(250L)
                localVoiceStarting = true
                try {
                    val released = stopActiveVoiceAndAwait()
                    if (!released) {
                        inputUnavailable("El micrófono anterior aún se está cerrando. Voy a reintentar.")
                    } else {
                        localVoice = null
                        platformVoice = null
                        NikoRuntimeState.setInput(applicationContext, NikoRuntimeState.InputState.PREPARING, "Iniciando micrófono…")

                        // Ruta primaria: el reconocedor de Android evita cargar JNI/ONNX solo para
                        // poder abrir el micrófono. Es especialmente útil en dispositivos donde
                        // el KWS nativo puede abortar el proceso.
                        if (!platformVoiceDisabledForSession && startPlatformVoiceIfAvailable()) return@launch

                        // Respaldo totalmente local: solo se usa si Android no ofrece un
                        // RecognitionService compatible.
                        val failure = initializationFailure
                        initializationFailure = null
                        val failedModel = failure?.model
                        NikoRuntimeState.setInputStatus(applicationContext, "Preparando activación local…")
                        val ready = withContext(Dispatchers.IO) {
                            if (failedModel != null && voiceRecovery.allowModelRepair(failedModel.id)) {
                                modelManager.repair(failedModel, ::onModelProgress)
                            }
                            modelManager.ensureRecommended(deviceProfile, ::onModelProgress)
                        }
                        if (ready && startLocalVoiceIfReady()) return@launch
                        if (!ready) inputUnavailable("No pude preparar el motor de voz. Revisá Internet, espacio y permiso de micrófono.")
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    inputUnavailable("No pude preparar la escucha local. Comprobá conexión, almacenamiento y permiso de micrófono.")
                } finally { localVoiceStarting = false }
                delay(voiceRecovery.nextDelayMillis(SystemClock.elapsedRealtime()))
            }
        }
    }

    private fun onModelProgress(progress: NikoModelProgress) {
        if (progress.state !in setOf(NikoModelProgress.State.INSTALLING, NikoModelProgress.State.DOWNLOADING)) return
        val amount = if (progress.totalBytes > 0) " · ${progress.downloadedBytes * 100 / progress.totalBytes}%" else ""
        serviceScope.launch {
            if (!destroyed && localVoiceStarting) NikoRuntimeState.setInputStatus(applicationContext, "Preparando voz local$amount")
        }
    }

    private fun inputUnavailable(message: String) {
        isListening = false
        isTranscribing = false
        NikoRuntimeState.setInput(applicationContext, NikoRuntimeState.InputState.ERROR, message)
        if (!isSpeaking && !isThinking) NikoRuntimeState.setResponse(applicationContext, message)
        updateVisualState()
    }

    private fun startPlatformVoiceIfAvailable(): Boolean {
        if (CaptureGate.held || !hasMicrophonePermission()) return false
        val epoch = ++localVoiceEpoch
        val engine = LeoPlatformVoiceEngine(
            context = applicationContext,
            onState = { state -> serviceScope.launch {
                if (destroyed || epoch != localVoiceEpoch) return@launch
                when (state) {
                    LeoPlatformVoiceEngine.State.PASSIVE -> {
                        isTranscribing = false
                        isListening = false
                        updateVisualState()
                    }
                    LeoPlatformVoiceEngine.State.ACTIVE -> {
                        isTranscribing = false
                        isListening = !isThinking && !isSpeaking
                        updateVisualState()
                    }
                    LeoPlatformVoiceEngine.State.PROCESSING -> {
                        isTranscribing = true
                        isListening = false
                        updateVisualState()
                    }
                    LeoPlatformVoiceEngine.State.SPEAKING -> {
                        isListening = false
                        updateVisualState()
                    }
                    LeoPlatformVoiceEngine.State.STOPPED -> {
                        isTranscribing = false
                        isListening = false
                        updateVisualState()
                    }
                }
            }},
            onWake = { serviceScope.launch {
                if (destroyed || epoch != localVoiceEpoch) return@launch
                NikoRuntimeState.setHeard(applicationContext, "LEO")
                NikoRuntimeState.setResponse(applicationContext, "Te escucho.")
                revealNikoOnLockScreen()
            }},
            onCommand = { text -> serviceScope.launch {
                if (!destroyed && epoch == localVoiceEpoch) {
                    isTranscribing = false
                    submitCommand(text)
                }
            }},
            onStatus = { status -> serviceScope.launch {
                if (!destroyed && epoch == localVoiceEpoch) {
                    when {
                        status.startsWith("Micrófono listo") -> {
                            NikoRuntimeState.setInput(applicationContext, NikoRuntimeState.InputState.READY, status)
                            if (!isSpeaking && !isThinking) {
                                NikoRuntimeState.setResponse(applicationContext, "Decí LEO para hablar conmigo.")
                            }
                        }
                        status.startsWith("Te escucho") || status.startsWith("LEO detectado") -> {
                            NikoRuntimeState.setInput(applicationContext, NikoRuntimeState.InputState.READY, status)
                            if (!isSpeaking && !isThinking) {
                                NikoRuntimeState.setResponse(applicationContext, "Te escucho.")
                            }
                        }
                        else -> NikoRuntimeState.setInputStatus(applicationContext, status)
                    }
                }
            }},
            onError = { error -> serviceScope.launch {
                if (!destroyed && epoch == localVoiceEpoch) {
                    NikoRuntimeState.setInput(applicationContext, NikoRuntimeState.InputState.ERROR, error)
                }
            }},
            onFatal = { error -> serviceScope.launch {
                if (!destroyed && epoch == localVoiceEpoch) {
                    platformVoiceDisabledForSession = true
                    ++localVoiceEpoch
                    localVoiceActive = false
                    platformVoice?.stop()
                    platformVoice = null
                    NikoRuntimeState.setInput(
                        applicationContext,
                        NikoRuntimeState.InputState.PREPARING,
                        "$error Probando el motor local de respaldo…",
                    )
                    ensureVoiceListening(250L)
                }
            }},
        )
        platformVoice = engine
        localVoice = null
        val started = engine.start()
        if (!started) {
            platformVoiceDisabledForSession = true
            platformVoice = null
            engine.stop()
            return false
        }

        localVoiceActive = true
        initializationFailure = null
        voiceRecovery.started(SystemClock.elapsedRealtime())
        isListening = false
        NikoRuntimeState.setInput(
            applicationContext,
            NikoRuntimeState.InputState.PREPARING,
            "Abriendo micrófono con el servicio de voz de Android…",
        )
        NikoRuntimeState.setResponse(applicationContext, "Preparando el micrófono de LEO…")
        updateVisualState()
        prewarmAdaptiveLearning()
        return true
    }

    private suspend fun stopActiveVoiceAndAwait(): Boolean {
        val platform = platformVoice
        val native = localVoice
        platformVoice = null
        localVoice = null
        val platformReleased = platform?.stopAndAwait() ?: true
        val nativeReleased = withContext(Dispatchers.IO) { native?.stopAndAwait() ?: true }
        return platformReleased && nativeReleased
    }

    private fun stopActiveVoice() {
        platformVoice?.stop()
        platformVoice = null
        localVoice?.stop()
        localVoice = null
    }

    private fun setActiveVoiceBusy(value: Boolean) {
        platformVoice?.setAssistantBusy(value)
        localVoice?.setAssistantBusy(value)
    }

    private fun cancelActiveVoiceConversation() {
        platformVoice?.cancelConversation()
        localVoice?.cancelConversation()
    }

    private fun finishActiveVoiceTurn() {
        platformVoice?.finishTurn()
        localVoice?.finishTurn()
    }

    private fun setActiveVoiceSpeaking(value: Boolean, continueCommand: Boolean = false) {
        platformVoice?.setAssistantSpeaking(value, continueCommand)
        localVoice?.setAssistantSpeaking(value, continueCommand)
    }

    private fun activeVoiceMicrophoneSilenced(): Boolean =
        platformVoice?.isMicrophoneSilenced == true || localVoice?.isMicrophoneSilenced == true

    private suspend fun startLocalVoiceIfReady(): Boolean {
        if (CaptureGate.held) return false
        if (localVoiceActive || !modelManager.coreReady() || !hasMicrophonePermission()) return localVoiceActive
        val epoch = ++localVoiceEpoch
        val engine = NikoLocalVoiceEngine(
            context = applicationContext,
            models = modelManager,
            profile = deviceProfile,
            ownerVoice = ownerVoice,
            onState = { voiceState -> serviceScope.launch {
                if (destroyed || epoch != localVoiceEpoch) return@launch
                when (voiceState) {
                    NikoLocalVoiceEngine.State.PASSIVE -> {
                        if (isListening && !isSpeaking && !isThinking) NikoRuntimeState.setResponse(applicationContext, "Decí LEO para hablar conmigo.")
                        isTranscribing = false
                        isListening = false
                        updateVisualState()
                    }
                    NikoLocalVoiceEngine.State.VERIFYING, NikoLocalVoiceEngine.State.PROCESSING -> { isTranscribing = true; isListening = false; updateVisualState() }
                    NikoLocalVoiceEngine.State.ACTIVE -> { isTranscribing = false; isListening = !isThinking && !isSpeaking; updateVisualState() }
                    NikoLocalVoiceEngine.State.SPEAKING -> { isListening = false; updateVisualState() }
                    NikoLocalVoiceEngine.State.STOPPED -> {
                        if (localVoiceActive) recoverLocalVoice("El motor local se detuvo. Voy a recuperar la escucha.")
                    }
                }
            }},
            onWake = { _, _ -> serviceScope.launch {
                if (destroyed || epoch != localVoiceEpoch) return@launch
                NikoRuntimeState.setHeard(applicationContext, "LEO")
                NikoRuntimeState.setResponse(applicationContext, "Te escucho.")
                revealNikoOnLockScreen()
                if (localLlm.isAvailable) serviceScope.launch { localLlm.prewarm() }
            }},
            onAwaitingCommand = { prompt, retry -> serviceScope.launch {
                if (destroyed || epoch != localVoiceEpoch) return@launch
                isTranscribing = false
                if (!isThinking && !isSpeaking) {
                    NikoRuntimeState.setResponse(applicationContext, prompt)
                    speakOnly(prompt, continueCommand = retry)
                }
            } },
            onCommand = { text -> serviceScope.launch {
                if (!destroyed && epoch == localVoiceEpoch) { isTranscribing = false; submitCommand(text) }
            } },
            onUnauthorizedVoice = { serviceScope.launch {
                if (!destroyed && epoch == localVoiceEpoch) {
                    isTranscribing = false
                    NikoRuntimeState.setResponse(applicationContext, "No distinguí tu voz con claridad. Repetí la frase cerca del teléfono.")
                    updateVisualState()
                }
            } },
            onMicrophoneSilenced = { silenced -> serviceScope.launch {
                if (!destroyed && epoch == localVoiceEpoch) {
                    if (silenced) inputUnavailable("Android silenció el micrófono. Revisá su interruptor de privacidad o cerrá la otra app que lo usa.")
                    else {
                        NikoRuntimeState.setInput(applicationContext, NikoRuntimeState.InputState.READY, "Activación local lista · decí LEO")
                        if (!isSpeaking && !isThinking) NikoRuntimeState.setResponse(applicationContext, "Decí LEO para hablar conmigo.")
                    }
                }
            } },
            onError = { error -> serviceScope.launch {
                if (!destroyed && epoch == localVoiceEpoch) {
                    NikoRuntimeState.setInputStatus(applicationContext, error)
                    if (localVoiceActive) recoverLocalVoice(error)
                }
            } },
        )

        NikoRuntimeState.setInputStatus(applicationContext, "Iniciando micrófono local…")
        localVoice = engine
        val started = withContext(Dispatchers.IO) { engine.start() }
        if (destroyed) { engine.stop(); return false }
        return if (started && engine.isRunning) {
            localVoiceActive = true
            platformVoice = null
            prewarmAdaptiveLearning()
            isListening = false
            initializationFailure = null
            voiceRecovery.started(SystemClock.elapsedRealtime())
            if (engine.isMicrophoneSilenced) inputUnavailable("Android silenció el micrófono. Revisá su interruptor de privacidad o cerrá la otra app que lo usa.")
            else {
                NikoRuntimeState.setInput(applicationContext, NikoRuntimeState.InputState.READY, "Activación local lista · decí LEO")
                NikoRuntimeState.setResponse(applicationContext, "Decí LEO para hablar conmigo.")
            }
            updateVisualState()
            true
        } else {
            ++localVoiceEpoch
            engine.stop()
            initializationFailure = engine.lastInitializationFailure
            val stage = initializationFailure?.stage ?: "micrófono"
            val detail = initializationFailure?.detail?.take(160).orEmpty()
            inputUnavailable("No inició $stage local. $detail. Voy a reintentar.")
            false
        }
    }

    private fun recoverLocalVoice(error: String) {
        if (destroyed) return
        ++localVoiceEpoch
        localVoiceActive = false
        stopActiveVoice()
        inputUnavailable(error)
        ensureVoiceListening(voiceRecovery.nextDelayMillis(SystemClock.elapsedRealtime()))
    }

    /** Main-thread turn ownership; canceled jobs must never reset a newer command window. */
    private fun interruptCurrentTurn() {
        if (destroyed) return
        ++turnEpoch
        RobotMotionBus.clear()
        LeoVoiceDiagnostics.cancelResponseTiming()
        progressiveSpeech?.cancel()
        progressiveSpeech = null
        commandJob?.cancel()
        commandJob = null
        speechTimeout?.cancel()
        speechTimeout = null
        continueAfterSpeech = false
        isThinking = false
        isSpeaking = false
        isTranscribing = false
        setActiveVoiceBusy(false)
        NikoRuntimeState.setSearching(applicationContext, false)
        updateVisualState()
    }

    private fun submitCommand(text: String) {
        if (destroyed) return
        VoiceControl.parse(text)?.let { control ->
            interruptCurrentTurn()
            LeoRealtimeTurnBus.interruptSpeech()
            cancelActiveVoiceConversation()
            isListening = false
            NikoRuntimeState.setHeard(applicationContext, text)
            if (control == VoiceControl.DEACTIVATE) {
                NikoVoiceSettings.setEnabled(this, false)
                ++localVoiceEpoch
                localVoiceActive = false
                stopActiveVoice()
                NikoRuntimeState.setResponse(applicationContext, "LEO desactivado. Podés activarme de nuevo desde la app.")
                stopSelf()
            } else {
                NikoRuntimeState.setResponse(applicationContext, "Detenido. Decí LEO cuando me necesités.")
            }
            updateVisualState()
            return
        }
        if (isSpeaking || isThinking || commandJob?.isActive == true) return
        val epoch = ++turnEpoch
        LeoVoiceDiagnostics.recordResponseStarted()
        isListening = false
        isThinking = true
        NikoRuntimeState.setResponse(applicationContext, "Procesando tu petición…")
        isTranscribing = false
        setActiveVoiceBusy(true)
        NikoRuntimeState.setHeard(applicationContext, text)
        revealNikoOnLockScreen()
        updateVisualState()
        commandJob = serviceScope.launch {
            try {
                val completed = withTimeoutOrNull(COMMAND_EXECUTION_TIMEOUT_MS) {
                    handleCommand(text)
                    true
                } == true
                if (!completed && epoch == turnEpoch && !destroyed) {
                    progressiveSpeech?.cancel()
                    progressiveSpeech = null
                    LeoRealtimeTurnBus.interruptSpeech()
                    speakResponse("La orden tardó demasiado. Ya liberé el procesamiento; decime de nuevo qué querés hacer.")
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (epoch == turnEpoch) {
                    progressiveSpeech?.cancel()
                    progressiveSpeech = null
                    LeoRealtimeTurnBus.interruptSpeech()
                    speakResponse("No pude completar eso. Volvé a llamarme y lo intentamos.")
                }
            } finally {
                if (epoch == turnEpoch && !destroyed) {
                    isThinking = false
                    if (!isSpeaking) finishTurn()
                    updateVisualState()
                }
            }
        }
    }

    private fun finishTurn() {
        finishActiveVoiceTurn()
        isListening = false
    }

    private suspend fun handleCommand(rawText: String) {
        val correctedText = InteractionCorrection.correctedText(rawText)
        val text = correctedText ?: rawText
        val correctionAlias = if (correctedText != null) lastTrainableUtterance else null
        lastTrainableUtterance = text
        replyProsody = SpeechProsody.forInput(text)

        // Ruta inmediata para acciones claras del teléfono. No toca el motor de escucha.
        // Antes estas órdenes esperaban memoria/IA/planificador aunque LocalBrain ya sabía
        // exactamente qué hacer, por eso la UI podía quedarse en "Procesando tu petición…".
        if (executeDeterministicLocalAction(text, correctionAlias)) return

        // Animaciones y cálculos también son 100% locales: deben responder antes de abrir
        // memoria, red o cualquier componente pesado.
        RobotMotion.parse(text)?.let { motion ->
            learnIntent(text, LearnedIntent.ACTION, correctionAlias)
            RobotMotionBus.perform(motion)
            speakResponse(when (motion) {
                RobotMotion.DANCE -> "Va, mirá cómo bailo."
                RobotMotion.JUMP -> "¡Ahí va un salto!"
                RobotMotion.SPIN -> "¡Doy una vuelta!"
                RobotMotion.WAVE -> "¡Hola! Aquí estoy con vos."
            })
            return
        }
        NikoMathEngine.solve(text)?.let {
            learnIntent(text, LearnedIntent.ACTION, correctionAlias)
            speakResponse("El resultado es $it.")
            return
        }

        // Memoria y conversación quedan después de todas las acciones inmediatas.
        withContext(Dispatchers.IO) { memory.rememberUserTurn(rawText) }
        // Tool transformations are local and must not become web research requests.
        val directTool = com.niko.assistant.brain.LocalBrain().understandMany(text).singleOrNull()
        if (directTool is AssistantCommand.OpenAppByName &&
            (directTool.name.startsWith("NIKO_TOOL") || directTool.name.startsWith("LEO_MUSIC_QUERY:"))) {
            speakResponse(executor.openAppByName(directTool.name).spokenMessage)
            return
        }
        WebQueryRouter.explicitQuery(text)?.takeIf { AutonomousResearch.allowedFor(text) }?.let { query ->
            rememberCorrectedAction(correctionAlias, listOf(AssistantCommand.SearchWeb(query)))
            learnIntent(text, LearnedIntent.SEARCH, correctionAlias)
            speakResearchResponse(query, researchReply(query))
            return
        }
        val learningEnabled = NikoAiSettings.adaptiveLearning(applicationContext)
        val identityReply = com.niko.assistant.ai.NikoIdentity.replyTo(
            text,
            adaptiveLearningEnabled = learningEnabled,
        )
        if (identityReply != null) {
            val response = if (com.niko.assistant.ai.NikoIdentity.isLearningQuestion(text)) {
                val stats = learningStats()
                com.niko.assistant.ai.NikoIdentity.replyTo(
                    text,
                    adaptiveLearningEnabled = learningEnabled,
                    trainingUpdates = stats.updates,
                    learnedCorrections = stats.corrections,
                ) ?: identityReply
            } else identityReply
            learnIntent(text, LearnedIntent.CONVERSATION, correctionAlias)
            speakResponse(response)
            return
        }
        withContext(Dispatchers.IO) { memory.learnExplicitly(text) }?.let {
            learnIntent(text, LearnedIntent.MEMORY, correctionAlias)
            speakResponse(it); return
        }
        val learnedCommands = if (learningEnabled) withContext(Dispatchers.IO) {
            runCatching { learnedActionStore.resolve(text) }.getOrNull()
        }?.let(semanticActions::parseDsl)?.takeIf { it.isNotEmpty() } else null
        val commands = learnedCommands ?: semanticActions.resolveMany(text)
        rememberCorrectedAction(correctionAlias, commands)
        if (commands.size > 1) {
            learnIntent(
                text,
                if (commands.all { it is AssistantCommand.SearchWeb }) LearnedIntent.SEARCH else LearnedIntent.ACTION,
                correctionAlias,
            )
            val responses = mutableListOf<String>()
            val sources = mutableListOf<NikoWebSource>()
            for (command in commands) {
                memory.rememberCommand(command); proactiveScheduler.maybeSchedule(command)
                if (command is AssistantCommand.SearchWeb) {
                    learnIntent(command.query, LearnedIntent.SEARCH)
                    val answer = researchReply(command.query, openBrowser = true)
                    responses.add(answer.text)
                    sources.addAll(answer.sources)
                    continue
                }
                val direct = executeDirectCommand(command)
                if (!direct.isNullOrBlank()) {
                    responses.add(direct)
                    withContext(Dispatchers.IO) { memory.rememberCompletedCommand(command, direct) }
                }
                delay(120L)
            }
            val answer = responses.joinToString(" ").ifBlank { "No entendí qué acciones querés que haga." }
            if (sources.isEmpty()) speakResponse(answer)
            else speakResearchResponse(text, NikoAiReply(answer, true, sources.distinctBy { it.url }.take(8)))
            return
        }
        val command = commands.firstOrNull() ?: AssistantCommand.Unknown(text)
        if (command == AssistantCommand.ClearMemory) { clearLocalMemory(); speakResponse("De una. Borré mi memoria local. Empezamos de nuevo."); return }
        memory.rememberCommand(command); proactiveScheduler.maybeSchedule(command)
        if (command is AssistantCommand.SearchWeb) {
            learnIntent(text, LearnedIntent.SEARCH, correctionAlias)
            speakResearchResponse(command.query, researchReply(command.query, openBrowser = true)); return
        }
        if (command is AssistantCommand.Unknown) {
            if (NikoUiTaskPolicy.looksLikeExplicitUiTask(text)) {
                learnIntent(text, LearnedIntent.ACTION, correctionAlias)
                speakResponse(uiAutomation.run(text).message)
                return
            }
            withContext(Dispatchers.IO) { memory.personalReply(text) }?.let {
                learnIntent(text, LearnedIntent.MEMORY, correctionAlias); speakResponse(it); return
            }
            val prediction = predictIntent(text)
            val remoteContext = withContext(Dispatchers.IO) { memory.contextForAi(false, text) }
            val history = memory.historyForAi(text)
            val streamedText = StringBuilder()
            var lastPreviewAt = 0L
            val answer = ConversationCoordinator.reply(
                message = text,
                localFirst = NikoAiSettings.localFirst(applicationContext),
                autoResearch = NikoAiSettings.autoResearch(applicationContext),
                learnedSearch = prediction?.let { it.reliable && it.intent == LearnedIntent.SEARCH } == true,
                local = {
                    val context = withContext(Dispatchers.IO) { memory.contextForAi(currentMessage = text) }
                    localLlm.reply(text, context)
                },
                cloud = { requireSources ->
                    if (requireSources) researchReply(text)
                    else if (webClient.isConfigured) webClient.reply(text, remoteContext, false, history) { delta ->
                        currentCoroutineContext().ensureActive()
                        if (!destroyed) {
                            val queue = progressiveSpeech ?: ProgressiveSpeech().also { progressiveSpeech = it }
                            if (delta.isNotBlank()) LeoVoiceDiagnostics.recordResponseText()
                            streamedText.append(delta)
                            val now = SystemClock.elapsedRealtime()
                            if (lastPreviewAt == 0L || now - lastPreviewAt >= 80L) {
                                NikoRuntimeState.previewResponse(streamedText.toString())
                                lastPreviewAt = now
                            }
                            queue.append(delta)
                            if (!isSpeaking) playNextProgressivePhrase()
                        }
                    }
                    else null
                },
                fallback = { withContext(Dispatchers.IO) {
                    val error = if (AutonomousResearch.offlineOnly(text)) localLlm.lastError else webClient.lastError ?: localLlm.lastError
                    fallbackConversation.reply(text, memory, error)
                } },
            )
            val learnedLabel = if (answer.webUsed || WebQueryRouter.needsCurrentInformation(text) && AutonomousResearch.allowedFor(text)) {
                LearnedIntent.SEARCH
            } else LearnedIntent.CONVERSATION
            learnIntent(text, learnedLabel, correctionAlias)
            if (looksLikeCapabilityRequest(text)) {
                val plan = codeAgent.analyze(text)
                codeAgent.registerNativeProposal(plan.capability, "${plan.strategy}: ${plan.explanation}", answer.text, com.niko.assistant.BuildConfig.VERSION_NAME)
            }
            if (progressiveSpeech != null) {
                progressiveSpeech?.finish()
                NikoRuntimeState.setAiResponse(applicationContext, answer.text, answer.webUsed, answer.sources)
                if (!isSpeaking) playNextProgressivePhrase()
                withContext(Dispatchers.IO) { memory.rememberAssistantTurn(answer.text) }
            } else speakResearchResponse(text, answer)
            return
        }
        learnIntent(
            text,
            when (command) {
                AssistantCommand.Greeting -> LearnedIntent.CONVERSATION
                AssistantCommand.MemorySummary -> LearnedIntent.MEMORY
                else -> LearnedIntent.ACTION
            },
            correctionAlias,
        )
        val direct = executeDirectCommand(command) ?: "Listo."
        speakResponse(direct)
        withContext(Dispatchers.IO) { memory.rememberCompletedCommand(command, direct) }
    }

    private suspend fun executeDeterministicLocalAction(
        text: String,
        correctionAlias: String?,
    ): Boolean {
        val commands = brain.understandMany(text)
        if (commands.isEmpty() || commands.any { it is AssistantCommand.Unknown || it is AssistantCommand.SearchWeb }) {
            return false
        }

        val responses = mutableListOf<String>()
        for (command in commands) {
            when (command) {
                AssistantCommand.ClearMemory -> {
                    clearLocalMemory()
                    responses += "Borré mi memoria local."
                }
                AssistantCommand.MemorySummary -> {
                    responses += withContext(Dispatchers.IO) { memory.describeLearnedPatterns() }
                }
                else -> {
                    val result = executeDirectCommand(command)
                    if (!result.isNullOrBlank()) responses += result
                }
            }
        }

        if (responses.isEmpty()) return false

        rememberCorrectedAction(correctionAlias, commands)
        learnIntent(
            text,
            if (commands.all { it == AssistantCommand.Greeting }) LearnedIntent.CONVERSATION else LearnedIntent.ACTION,
            correctionAlias,
        )

        // Persistencia en segundo plano: la acción ya ocurrió y no depende de la base.
        serviceScope.launch(Dispatchers.IO) {
            runCatching {
                commands.forEach { command ->
                    memory.rememberCommand(command)
                    memory.rememberCompletedCommand(command, responses.joinToString(" "))
                }
                memory.rememberUserTurn(text)
            }
        }

        speakResponse(responses.joinToString(" "))
        return true
    }

    private suspend fun predictIntent(text: String): OnlineIntentNetwork.Prediction? = withContext(Dispatchers.IO) {
        if (!NikoAiSettings.adaptiveLearning(applicationContext) || adaptiveUnavailable) return@withContext null
        val loaded = adaptiveNetwork ?: run {
            prewarmAdaptiveLearning()
            return@withContext null
        }
        learningMutex.withLock {
            try {
                loaded.predict(text)
            } catch (_: Exception) {
                adaptiveUnavailable = true
                NikoRuntimeState.setInputStatus(applicationContext, "Aprendizaje no disponible; conservé los datos para recuperación.")
                null
            }
        }
    }

    private fun prewarmAdaptiveLearning() {
        if (destroyed || adaptiveUnavailable || adaptiveNetwork != null ||
            !NikoAiSettings.adaptiveLearning(applicationContext) || !adaptiveLoading.compareAndSet(false, true)
        ) return
        serviceScope.launch(Dispatchers.IO) {
            try {
                learningMutex.withLock {
                    if (adaptiveNetwork == null && !adaptiveUnavailable) adaptiveNetwork = adaptiveStore.load()
                }
            } catch (_: Exception) {
                adaptiveUnavailable = true
                NikoRuntimeState.setInputStatus(applicationContext, "Aprendizaje no disponible; conservé los datos para recuperación.")
            } finally {
                adaptiveLoading.set(false)
            }
        }
    }

    /** Training and fsync must never delay command execution or the first spoken audio. */
    private fun learnIntent(text: String, intent: LearnedIntent, correctionAlias: String? = null) {
        if (!NikoAiSettings.adaptiveLearning(applicationContext) || adaptiveUnavailable) return
        val epoch = learningEpoch
        serviceScope.launch(Dispatchers.IO) {
            learningMutex.withLock {
                if (epoch != learningEpoch) return@withLock
                try {
                    val network = adaptiveNetwork ?: adaptiveStore.load().also { adaptiveNetwork = it }
                    network.learn(text, intent)
                    correctionAlias?.takeIf { it != text }?.let { network.learn(it, intent) }
                    adaptiveStore.save(network)
                } catch (_: Exception) {
                    adaptiveUnavailable = true
                    NikoRuntimeState.setInputStatus(applicationContext, "No pude guardar el aprendizaje. Las órdenes siguen disponibles.")
                }
            }
        }
    }

    private suspend fun clearLocalMemory() {
        ++learningEpoch
        withContext(Dispatchers.IO) {
            learningMutex.withLock {
                memory.clearAll()
                adaptiveStore.clear()
                learnedActionStore.clear()
                adaptiveNetwork = null
                adaptiveUnavailable = false
                lastTrainableUtterance = null
            }
        }
    }

    private fun rememberCorrectedAction(alias: String?, commands: List<AssistantCommand>) {
        alias ?: return
        if (!NikoAiSettings.adaptiveLearning(applicationContext)) return
        val dsl = LearnedActionCodec.encode(commands) ?: return
        val epoch = learningEpoch
        serviceScope.launch(Dispatchers.IO) {
            learningMutex.withLock {
                if (epoch != learningEpoch) return@withLock
                runCatching { learnedActionStore.remember(alias, dsl) }
                    .onFailure { NikoRuntimeState.setInputStatus(applicationContext, "No pude guardar esa corrección; el resto del aprendizaje sigue activo.") }
            }
        }
    }

    private suspend fun learningStats(): LearningStats = withContext(Dispatchers.IO) {
        learningMutex.withLock {
            val network = runCatching { adaptiveNetwork ?: adaptiveStore.load().also { adaptiveNetwork = it } }.getOrNull()
            val corrections = runCatching { learnedActionStore.count() }.getOrDefault(0)
            LearningStats(network?.observations ?: 0L, corrections)
        }
    }

    private data class LearningStats(val updates: Long, val corrections: Int)

    private fun looksLikeCapabilityRequest(text: String): Boolean {
        val value = text.lowercase(Locale.ROOT)
        return listOf(
            "aprende a", "aprendé a", "programate", "programáte", "prográmate", "mejorate", "mejoráte", "mejórate",
            "agrega una funcion", "agregá una función", "agrega una función", "crea una funcion", "creá una función",
            "quiero que puedas", "necesito que puedas", "haz que puedas", "hacé que puedas",
        ).any(value::contains)
    }

    private suspend fun executeDirectCommand(command: AssistantCommand): String? = when (command) {
        AssistantCommand.Greeting -> "Aquí estoy. Decime."
        AssistantCommand.TellTime -> "Son las ${SimpleDateFormat("h:mm a", Locale.forLanguageTag("es-NI")).format(Date())}."
        AssistantCommand.OpenCamera -> executor.openCamera().spokenMessage
        AssistantCommand.MemorySummary -> withContext(Dispatchers.IO) { memory.describeLearnedPatterns() }
        AssistantCommand.ClearMemory -> { clearLocalMemory(); "Borré mi memoria local." }
        is AssistantCommand.OpenApp -> executor.openApp(command.app).spokenMessage
        is AssistantCommand.OpenAppByName -> executor.openAppByName(command.name).spokenMessage
        is AssistantCommand.Dial -> executor.dial(command.number).spokenMessage
        is AssistantCommand.ComposeMessage -> executor.composeMessage(command.number, command.message).spokenMessage
        is AssistantCommand.WhatsAppMessage -> executor.whatsappMessage(command.number, command.message).spokenMessage
        is AssistantCommand.PlaySpotify -> executor.playSpotify(command.query).spokenMessage
        is AssistantCommand.SetAlarm -> executor.setAlarm(command.hour, command.minute, command.label).spokenMessage
        is AssistantCommand.SetTimer -> executor.setTimer(command.seconds, command.label).spokenMessage
        is AssistantCommand.OpenMaps -> executor.openMaps(command.query).spokenMessage
        is AssistantCommand.SearchWeb -> null
        is AssistantCommand.ShareText -> executor.shareText(command.text).spokenMessage
        is AssistantCommand.SetTorch -> executor.setTorch(command.enabled).spokenMessage
        is AssistantCommand.SetVolume -> executor.setVolume(command.percent).spokenMessage
        is AssistantCommand.AdjustVolume -> executor.adjustVolume(command.direction).spokenMessage
        is AssistantCommand.SetBrightness -> executor.setBrightness(command.percent).spokenMessage
        is AssistantCommand.OpenSystemPanel -> executor.openSystemPanel(command.panel).spokenMessage
        is AssistantCommand.NavigateDevice -> executor.navigateDevice(command.destination).spokenMessage
        is AssistantCommand.AutomateUi -> uiAutomation.run(command.task).message
        AssistantCommand.BatteryStatus -> executor.batteryStatus().spokenMessage
        is AssistantCommand.Vibrate -> executor.vibrate(command.milliseconds).spokenMessage
        is AssistantCommand.SmartHomeControl -> smartHome.control(command.target, command.enabled).spokenMessage
        AssistantCommand.OpenSmartHomeSettings -> executor.openSmartHomeSettings().spokenMessage
        AssistantCommand.OpenAiSettings -> executor.openAiSettings().spokenMessage
        is AssistantCommand.Unknown -> null
    }

    private suspend fun researchReply(query: String, forceWeb: Boolean = true, openBrowser: Boolean = false): NikoAiReply {
        fun unavailable(message: String) = NikoAiReply(message, false, emptyList())
        if (AutonomousResearch.offlineOnly(query)) return unavailable("Una búsqueda web necesita conexión. Puedo seguir con las funciones locales.")
        if (!webClient.isConfigured) {
            val browser = if (openBrowser) " ${executor.searchWeb(query).spokenMessage}" else ""
            return unavailable("Para verificar información web, configurá GroqCloud en Ajustes.$browser")
        }
        val researchEpoch = turnEpoch
        NikoRuntimeState.setSearching(applicationContext, true)
        NikoRuntimeState.setResponse(applicationContext, "Investigando en Internet…")
        return try {
            val current = NikoRuntimeState.read(applicationContext).heardText
            val context = withContext(Dispatchers.IO) { memory.contextForAi(false, query) }
            val reply = webClient.reply(query, context, forceWeb, memory.historyForAi(current))
            when {
                reply == null -> unavailable(webClient.lastError ?: "No pude consultar Internet. Volvé a intentarlo.")
                forceWeb && !reply.webUsed -> reply
                else -> reply
            }
        } finally { if (researchEpoch == turnEpoch) NikoRuntimeState.setSearching(applicationContext, false) }
    }

    private suspend fun speakResearchResponse(question: String, reply: NikoAiReply) {
        currentCoroutineContext().ensureActive()
        LeoVoiceDiagnostics.recordResponseText()
        val finalText = reply.text
        val evidenceNote = if (reply.webUsed) AutonomousResearch.evidenceNote(reply.sources.map { it.url }) else ""
        val displayed = if (evidenceNote.isBlank()) finalText else "$finalText\n\n$evidenceNote"
        NikoRuntimeState.setAiResponse(applicationContext, displayed, reply.webUsed, reply.sources)
        val spokenText = finalText
            .replace(Regex("\\s*\\[\\d+\\]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
        speakOnly(spokenText.ifBlank { finalText })
        withContext(Dispatchers.IO) { memory.rememberAssistantTurn(finalText) }
    }

    private suspend fun speakResponse(text: String) {
        currentCoroutineContext().ensureActive()
        LeoVoiceDiagnostics.recordResponseText()
        NikoRuntimeState.setResponse(applicationContext, text)
        speakOnly(text)
        withContext(Dispatchers.IO) { memory.rememberAssistantTurn(text) }
    }
    private fun speakOnly(text: String, continueCommand: Boolean = false) {
        if (text.isBlank() || destroyed) return
        continueAfterSpeech = continueCommand

        // Pausamos la escucha mientras LEO responde, pero no cargamos ningún runtime
        // neural nativo en esta ruta. La estabilidad del reconocimiento que ya funciona
        // queda intacta y la salida usa únicamente Android TTS.
        if (localVoiceActive) setActiveVoiceSpeaking(true, continueCommand)

        val systemReady = runCatching { platformTts.isReady }.getOrDefault(false)
        if (!systemReady) {
            isSpeaking = false
            NikoRuntimeState.setVoiceReady(applicationContext, false)
            NikoRuntimeState.setVoiceStatus(applicationContext, "Respuesta mostrada en pantalla · preparando voz del teléfono")
            if (localVoiceActive) setActiveVoiceSpeaking(false, continueCommand)
            if (continueCommand) {
                isListening = localVoiceActive && !activeVoiceMicrophoneSilenced()
            } else if (!isThinking) {
                finishTurn()
            }
            updateVisualState()
            return
        }

        isSpeaking = true
        updateVisualState()
        speechTimeout?.cancel()
        speechTimeout = serviceScope.launch {
            delay((text.length * 110L + 5_000L).coerceIn(12_000L, 360_000L))
            if (!destroyed && isSpeaking) {
                platformTts.stop()
                onSpeakingChanged(false)
            }
        }

        NikoRuntimeState.setVoiceStatus(applicationContext, platformTts.voiceDescription)
        val queued = runCatching { platformTts.speak(text, replyProsody) }.getOrDefault(false)
        if (queued) {
            NikoRuntimeState.setVoiceReady(applicationContext, true)
        } else {
            NikoRuntimeState.setVoiceReady(applicationContext, false)
            NikoRuntimeState.setVoiceStatus(applicationContext, "Respuesta mostrada en pantalla · voz del teléfono no disponible")
            onSpeakingChanged(false)
        }
    }

    private fun playNextProgressivePhrase(): Boolean {
        val queue = progressiveSpeech ?: return false
        val phrase = queue.poll()
        if (phrase != null) { speakOnly(phrase); return true }
        if (queue.isDrained) progressiveSpeech = null
        return false
    }

    private fun onSpeakingChanged(speaking: Boolean) {
        serviceScope.launch {
            if (destroyed || (!speaking && !isSpeaking)) return@launch
            isSpeaking = speaking
            if (!speaking && playNextProgressivePhrase()) return@launch
            if (localVoiceActive) setActiveVoiceSpeaking(speaking, continueAfterSpeech)
            if (!speaking) {
                speechTimeout?.cancel()
                speechTimeout = null
                if (continueAfterSpeech) {
                    isListening = localVoiceActive && !activeVoiceMicrophoneSilenced()
                } else if (!isThinking) finishTurn()
            }
            updateVisualState()
        }
    }
    private fun updateVisualState() {
        NikoRuntimeState.setState(applicationContext, when { isSpeaking -> NikoRuntimeState.State.SPEAKING; isThinking || isTranscribing -> NikoRuntimeState.State.THINKING; isListening -> NikoRuntimeState.State.LISTENING; else -> NikoRuntimeState.State.IDLE })
    }
    private fun registerScreenStateReceiver() {
        if (screenReceiverRegistered) return
        val filter = IntentFilter().apply { addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_USER_PRESENT) }
        ContextCompat.registerReceiver(this, screenStateReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED); screenReceiverRegistered = true
    }
    private fun unregisterScreenStateReceiver() { if (screenReceiverRegistered) { runCatching { unregisterReceiver(screenStateReceiver) }; screenReceiverRegistered = false } }
    private fun hasMicrophonePermission() = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "LEO local activo", NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) })
        manager.createNotificationChannel(NotificationChannel(WAKE_CHANNEL_ID, "Despertar LEO", NotificationManager.IMPORTANCE_HIGH).apply { setShowBadge(false); enableVibration(false); setSound(null, null); lockscreenVisibility = Notification.VISIBILITY_PUBLIC })
    }
    private fun startAsForeground() {
        val open = PendingIntent.getActivity(this, 10, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 11, UpgradeIdentity.assistantService(this).apply { action = ACTION_STOP }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, CHANNEL_ID).setSmallIcon(R.drawable.ic_niko_notification).setContentTitle("Activación por voz de LEO").setContentText("Escucha local habilitada. Abrí LEO para ver su estado.").setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true).setCategory(NotificationCompat.CATEGORY_SERVICE).addAction(0, "Detener", stop).build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE) else startForeground(NOTIFICATION_ID, notification)
    }
    private fun revealNikoOnLockScreen() {
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (keyguard?.isKeyguardLocked != true) return
        val show = PendingIntent.getActivity(this, WAKE_REQUEST_CODE, UpgradeIdentity.wakeActivity(this).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP) }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, WAKE_CHANNEL_ID).setSmallIcon(R.drawable.ic_niko_notification).setContentTitle("LEO").setContentText("Te escucho.").setContentIntent(show).setFullScreenIntent(show, true).setAutoCancel(true).setCategory(NotificationCompat.CATEGORY_CALL).setPriority(NotificationCompat.PRIORITY_MAX).setVisibility(NotificationCompat.VISIBILITY_PUBLIC).build()
        getSystemService(NotificationManager::class.java)?.notify(WAKE_NOTIFICATION_ID, notification)
    }

    private fun showBubble() {
        if (!Settings.canDrawOverlays(this) || bubbleView != null) return
        val wm = getSystemService(WindowManager::class.java); windowManager = wm
        val size = dp(66); val container = FrameLayout(this).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.argb(238, 255, 255, 255)); setStroke(dp(2), Color.rgb(0, 205, 160)) }
            elevation = dp(14).toFloat(); setPadding(dp(10), dp(10), dp(10), dp(10)); addView(ImageView(this@NikoAssistantService).apply { setImageResource(R.drawable.ic_niko_mark); scaleType = ImageView.ScaleType.CENTER_INSIDE }, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        }
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        val params = WindowManager.LayoutParams(size, size, type, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.START; x = bubblePrefs.getInt(KEY_BUBBLE_X, dp(DEFAULT_BUBBLE_X_DP)); y = bubblePrefs.getInt(KEY_BUBBLE_Y, dp(DEFAULT_BUBBLE_Y_DP)) }
        bubbleParams = params
        var downX = 0f; var downY = 0f; var originX = 0; var originY = 0; var dragging = false
        container.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { downX = event.rawX; downY = event.rawY; originX = params.x; originY = params.y; dragging = false; true }
                MotionEvent.ACTION_MOVE -> { val dx = event.rawX - downX; val dy = event.rawY - downY; if (!dragging && (kotlin.math.abs(dx) > dp(6) || kotlin.math.abs(dy) > dp(6))) dragging = true; if (dragging) { params.x = originX + dx.toInt(); params.y = originY + dy.toInt(); runCatching { wm.updateViewLayout(container, params) } }; true }
                MotionEvent.ACTION_UP -> { if (dragging) saveBubblePosition(params) else startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true }
                else -> false
            }
        }
        runCatching { wm.addView(container, params); bubbleView = container }
    }
    private fun hideBubble() { bubbleView?.let { view -> runCatching { windowManager?.removeView(view) } }; bubbleView = null }

    private fun resetBubblePosition() {
        bubblePrefs.edit().remove(KEY_BUBBLE_X).remove(KEY_BUBBLE_Y).apply()
        val params = bubbleParams ?: return
        params.x = dp(DEFAULT_BUBBLE_X_DP)
        params.y = dp(DEFAULT_BUBBLE_Y_DP)
        val view = bubbleView ?: return
        runCatching { windowManager?.updateViewLayout(view, params) }
    }

    private fun saveBubblePosition(params: WindowManager.LayoutParams) { bubblePrefs.edit().putInt(KEY_BUBBLE_X, params.x).putInt(KEY_BUBBLE_Y, params.y).apply() }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun acquireCpuWakeLock() { if (cpuWakeLock?.isHeld == true) return; cpuWakeLock = (getSystemService(PowerManager::class.java)?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "NIKO:AlwaysOnWake")?.apply { setReferenceCounted(false); acquire() }) }
    private fun releaseCpuWakeLock() { cpuWakeLock?.let { if (it.isHeld) runCatching { it.release() } }; cpuWakeLock = null }

    companion object {
        private const val COMMAND_EXECUTION_TIMEOUT_MS = 25_000L
        private const val CHANNEL_ID = UpgradeIdentity.assistantChannel
        private const val WAKE_CHANNEL_ID = UpgradeIdentity.wakeChannel
        private const val NOTIFICATION_ID = 2001
        private const val WAKE_NOTIFICATION_ID = 2002
        private const val WAKE_REQUEST_CODE = 2102
        private const val BUBBLE_PREFS = UpgradeIdentity.bubblePreferences
        private const val KEY_BUBBLE_X = "bubble_x"
        private const val KEY_BUBBLE_Y = "bubble_y"
        private const val DEFAULT_BUBBLE_X_DP = 18
        private const val DEFAULT_BUBBLE_Y_DP = 220
        const val ACTION_START = UpgradeIdentity.ACTION_START
        const val ACTION_STOP = UpgradeIdentity.ACTION_STOP
        const val ACTION_SHOW_BUBBLE = UpgradeIdentity.ACTION_SHOW_BUBBLE
        const val ACTION_HIDE_BUBBLE = UpgradeIdentity.ACTION_HIDE_BUBBLE
        const val ACTION_REFRESH_BUBBLE = UpgradeIdentity.ACTION_REFRESH_BUBBLE
        const val ACTION_RESET_BUBBLE = UpgradeIdentity.ACTION_RESET_BUBBLE
    }
}
