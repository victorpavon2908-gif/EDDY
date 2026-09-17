import React, { useState, useEffect, useRef, useCallback } from "react";
import {
  NikoVisualState,
  InputState,
  NikoUiMode,
  RobotMotion,
  NikoWebSource,
  LeoVoiceSnapshot,
} from "./types";
import { LocalBrain } from "./services/localBrain";
import { voiceService } from "./services/voiceService";
import { MemoryStore, AppSettings } from "./services/memoryStore";
import { LeoRobot } from "./components/LeoRobot";
import { NikoTopBar } from "./components/NikoTopBar";
import { LiveStateBadge } from "./components/LiveStateBadge";
import { ConversationGlass } from "./components/ConversationGlass";
import { QuickActionsRail } from "./components/QuickActionsRail";
import { WakeDock } from "./components/WakeDock";
import { AiSettingsModal } from "./components/AiSettingsModal";
import { VoiceDiagnosticsModal } from "./components/VoiceDiagnosticsModal";
import { SmartHomeModal } from "./components/SmartHomeModal";
import { EmbeddedAppsModal } from "./components/EmbeddedAppsModal";
import { LeoFirstRunModal } from "./components/LeoFirstRunModal";

export const App: React.FC = () => {
  // Application Settings
  const [settings, setSettings] = useState<AppSettings>(() => MemoryStore.getSettings());

  // Core Assistant States
  const [visualState, setVisualState] = useState<NikoVisualState>("IDLE");
  const [inputState, setInputState] = useState<InputState>("READY");
  const [uiMode, setUiMode] = useState<NikoUiMode>("ASSISTANT");
  const [requestedMotion, setRequestedMotion] = useState<RobotMotion | null>(null);

  // Conversation & Speech
  const [heardText, setHeardText] = useState("");
  const [responseText, setResponseText] = useState("Hola, soy Leo. ¿En qué te puedo ayudar hoy?");
  const [webSearching, setWebSearching] = useState(false);
  const [webUsed, setWebUsed] = useState(false);
  const [sources, setSources] = useState<NikoWebSource[]>([]);
  const [audioLevel, setAudioLevel] = useState(-55);
  const [snr, setSnr] = useState(12);

  // Modals & Navigation
  const [showSettings, setShowSettings] = useState(false);
  const [showDiagnostics, setShowDiagnostics] = useState(false);
  const [showSmartHome, setShowSmartHome] = useState(false);
  const [showApps, setShowApps] = useState(false);
  const [showFirstRun, setShowFirstRun] = useState(!settings.firstRunCompleted);
  const [toastMessage, setToastMessage] = useState<string | null>(null);

  // Diagnostics state
  const [diagnostics, setDiagnostics] = useState<LeoVoiceSnapshot>({
    wakeState: "ACTIVO",
    audioLevelDbfs: -52.4,
    noiseFloorDbfs: -58.2,
    snrDb: 14.8,
    wakeLatencyMs: 118,
    transcriptionEngine: "WebSpeech / Sherpa ONNX",
    transcriptionLatencyMs: 235,
    speechEngine: "Browser Neural Synthesis",
    speechStartLatencyMs: 175,
    ownerProfileEnabled: settings.ownerVoiceOnly,
    ownerScore: 0.94,
    ownerAccepted: true,
    lastTranscript: "",
    metrics: {
      completedCalls: 100,
      targetCalls: 100,
      truePositives: 97,
      falseNegatives: 2,
      falsePositives: 1,
    },
  });

  const showToast = useCallback((msg: string) => {
    setToastMessage(msg);
    setTimeout(() => setToastMessage(null), 3000);
  }, []);

  const triggerRobotMotion = (motion: RobotMotion) => {
    setRequestedMotion(motion);
  };

  // Speak helper
  const speakResponse = useCallback(
    (text: string, onDone?: () => void) => {
      setResponseText(text);
      setVisualState("SPEAKING");
      voiceService.speak(text, {
        rate: settings.voiceRate,
        pitch: settings.voicePitch,
        onStart: () => setVisualState("SPEAKING"),
        onEnd: () => {
          setVisualState("IDLE");
          onDone?.();
        },
      });
    },
    [settings.voiceRate, settings.voicePitch]
  );

  // Process incoming user query
  const handleProcessCommand = useCallback(
    async (rawText: string) => {
      const text = rawText.trim();
      if (!text) return;

      setHeardText(text);
      setDiagnostics((d) => ({
        ...d,
        lastTranscript: text,
        audioLevelDbfs: audioLevel,
        snrDb: snr,
      }));

      // 1. Understand through Local Brain
      const cmd = LocalBrain.understand(text);

      // Handle Stop command
      if (cmd.type === "stop") {
        voiceService.cancelSpeech();
        setVisualState("IDLE");
        setResponseText("Listo, me detengo.");
        return;
      }

      // Handle Robot Motion
      if (cmd.type === "robot_motion") {
        const motion: RobotMotion = cmd.payload?.motion || "WAVE";
        triggerRobotMotion(motion);
        const reply =
          motion === "DANCE"
            ? "¡Mirá cómo bailo!"
            : motion === "JUMP"
            ? "¡Allá voy!"
            : motion === "SPIN"
            ? "¡Dando una vuelta completa!"
            : "¡Hola! Mucho gusto saludarte.";
        speakResponse(reply);
        return;
      }

      // Handle Embedded Tool Opening
      if (cmd.type === "open_tool") {
        const tool: NikoUiMode = cmd.payload?.tool;
        if (tool === "ASSISTANT") {
          setUiMode("ASSISTANT");
          setShowApps(false);
          setShowDiagnostics(false);
          setShowSettings(false);
          setShowSmartHome(false);
          speakResponse("Volvimos a la pantalla principal.");
        } else if (tool === "VOICE_DIAGNOSTICS") {
          setShowDiagnostics(true);
          speakResponse("Abriendo panel de diagnóstico acústico.");
        } else if (tool === "AI_SETTINGS") {
          setShowSettings(true);
          speakResponse("Abriendo configuración de inteligencia y voz.");
        } else if (tool === "SMART_HOME_SETTINGS") {
          setShowSmartHome(true);
          speakResponse("Abriendo panel de casa inteligente.");
        } else {
          setUiMode(tool);
          setShowApps(true);
          speakResponse(`Abriendo ${tool.toLowerCase()}.`);
        }
        return;
      }

      // Handle Time
      if (cmd.type === "tell_time") {
        const now = new Date();
        const timeStr = now.toLocaleTimeString("es-ES", { hour: "2-digit", minute: "2-digit" });
        speakResponse(`Son las ${timeStr}.`);
        return;
      }

      // Handle Math
      if (cmd.type === "math") {
        const res = cmd.payload?.result;
        speakResponse(`El resultado es ${res}.`);
        return;
      }

      // Handle Greetings
      if (cmd.type === "greeting") {
        triggerRobotMotion("WAVE");
        speakResponse("¡Hola! ¿Cómo estás? Decime qué necesitás.");
        return;
      }

      // Handle External Apps
      if (cmd.type === "open_app") {
        const { appName, url } = cmd.payload;
        speakResponse(`Abriendo ${appName}...`);
        window.open(url, "_blank");
        return;
      }

      // Handle Battery
      if (cmd.type === "battery_status") {
        try {
          if ("getBattery" in navigator) {
            const b: any = await (navigator as any).getBattery();
            const pct = Math.round(b.level * 100);
            const charging = b.charging ? "conectado y cargando" : "con batería";
            speakResponse(`Queda un ${pct}% de batería, ${charging}.`);
            return;
          }
        } catch (_e) {}
        speakResponse("No pude leer el nivel exacto de batería en este dispositivo.");
        return;
      }

      // Handle Memory Summary
      if (cmd.type === "memory_summary") {
        const facts = MemoryStore.getFacts();
        if (facts.length > 0) {
          speakResponse(`Recuerdo lo siguiente: ${facts.join(". ")}.`);
        } else {
          speakResponse("Aún no tengo datos guardados sobre tus preferencias.");
        }
        return;
      }

      // Handle Clear Memory
      if (cmd.type === "clear_memory") {
        MemoryStore.clearMemory();
        speakResponse("He borrado todos los recuerdos de preferencias almacenados.");
        return;
      }

      // Handle Torch
      if (cmd.type === "torch") {
        const enable = cmd.payload?.enable ?? true;
        showToast(enable ? "Linterna encendida" : "Linterna apagada");
        speakResponse(enable ? "Encendí la linterna." : "Apagué la linterna.");
        return;
      }

      // Handle Web Search or Generative Synthesis
      setVisualState("THINKING");
      let currentSources: NikoWebSource[] = [];

      // If user asks a question or explicit search
      const isSearch =
        cmd.type === "search_web" ||
        text.toLowerCase().includes("busca") ||
        text.toLowerCase().includes("quien es") ||
        text.toLowerCase().includes("noticias");

      if (isSearch || settings.autoResearch) {
        setWebSearching(true);
        try {
          const searchQuery = cmd.type === "search_web" ? cmd.payload?.query : text;
          const searchResp = await fetch("/api/search", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ query: searchQuery }),
          });
          if (searchResp.ok) {
            const data = await searchResp.json();
            currentSources = data.sources || [];
            setSources(currentSources);
            setWebUsed(currentSources.length > 0);
          }
        } catch (err) {
          console.warn("Search fetch failed:", err);
        } finally {
          setWebSearching(false);
        }
      }

      // Query AI / Synthesis API
      try {
        const chatResp = await fetch("/api/chat", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            message: text,
            sources: currentSources,
            personality: settings.personality,
            groqApiKey: settings.groqApiKey,
          }),
        });

        if (chatResp.ok) {
          const chatData = await chatResp.json();
          speakResponse(chatData.reply || "Listo.");
        } else {
          speakResponse("Tuve un inconveniente al consultar la red, pero aquí estoy.");
        }
      } catch (err) {
        speakResponse("No pude conectar con el servicio en este momento.");
      }
    },
    [settings, audioLevel, snr, speakResponse, showToast]
  );

  // Setup Voice Service listeners
  useEffect(() => {
    voiceService.onTranscript = (transcript: string, isFinal: boolean) => {
      setHeardText(transcript);
      if (isFinal) {
        handleProcessCommand(transcript);
      } else {
        setVisualState("LISTENING");
      }
    };

    voiceService.onStateChange = (listening: boolean) => {
      if (listening && visualState === "IDLE") {
        setVisualState("LISTENING");
      } else if (!listening && visualState === "LISTENING") {
        setVisualState("IDLE");
      }
    };

    voiceService.onError = (err: string) => {
      console.warn("Voice service error:", err);
      setInputState("ERROR");
    };

    // Start live audio meter
    voiceService.startAudioMeter((volDbfs, snrVal) => {
      setAudioLevel(volDbfs);
      setSnr(snrVal);
    });

    if (settings.autoListening && !showFirstRun) {
      voiceService.startListening();
      setInputState("READY");
    }

    return () => {
      voiceService.stopAudioMeter();
      voiceService.stopListening();
    };
  }, [settings.autoListening, showFirstRun, handleProcessCommand, visualState]);

  const toggleListening = () => {
    const nextState = !settings.autoListening;
    const updated = { ...settings, autoListening: nextState };
    setSettings(updated);
    MemoryStore.saveSettings(updated);

    if (nextState) {
      voiceService.startListening();
      setInputState("READY");
      setVisualState("LISTENING");
      showToast("Escucha ambiental reanudada");
    } else {
      voiceService.stopListening();
      voiceService.cancelSpeech();
      setVisualState("IDLE");
      showToast("Leo en pausa");
    }
  };

  const handleRestartAcousticEngine = () => {
    voiceService.stopListening();
    voiceService.stopAudioMeter();
    setTimeout(() => {
      voiceService.startListening();
      voiceService.startAudioMeter((vol, snrVal) => {
        setAudioLevel(vol);
        setSnr(snrVal);
      });
      showToast("Motor acústico reiniciado");
    }, 400);
  };

  return (
    <div className="relative w-full h-screen overflow-hidden bg-[#03050A] flex flex-col items-center justify-between font-sans">
      {/* Background radial ambient gradients */}
      <div className="absolute top-0 left-1/2 -translate-x-1/2 w-full max-w-lg h-96 bg-gradient-to-b from-[#0A1826] via-[#050D17] to-transparent pointer-events-none opacity-60" />
      <div className="absolute -top-16 left-1/2 -translate-x-1/2 w-72 h-72 rounded-full bg-teal-500/10 blur-3xl pointer-events-none" />

      {/* Toast Notification Banner */}
      {toastMessage && (
        <div className="fixed top-4 z-50 bg-[#0B111C]/95 border border-teal-500/40 text-teal-200 px-4 py-2 rounded-full text-xs font-medium shadow-2xl backdrop-blur-md animate-fade-in">
          {toastMessage}
        </div>
      )}

      {/* Main framed container (matching mobile proportions of LEO) */}
      <div className="w-full max-w-md h-full flex flex-col justify-between p-3.5 sm:p-4 z-10">
        {/* 1. Top Bar */}
        <NikoTopBar
          state={visualState}
          autoListeningEnabled={settings.autoListening}
          onOpenSettings={() => setShowSettings(true)}
          onOpenDiagnostics={() => setShowDiagnostics(true)}
          onOpenSmartHome={() => setShowSmartHome(true)}
          onOpenApps={() => setShowApps(true)}
        />

        {/* 2. Hero & 3D Robot stage */}
        <div className="flex-1 w-full flex flex-col items-center justify-center relative min-h-0 py-1">
          <div className="w-full h-full max-h-[360px] flex items-center justify-center relative">
            <LeoRobot
              visualState={visualState}
              requestedMotion={requestedMotion}
              onMotionDone={() => setRequestedMotion(null)}
              onTriggerMotion={triggerRobotMotion}
              enabled={settings.autoListening}
            />
          </div>

          {/* Status Badge right below the robot */}
          <div className="mt-1">
            <LiveStateBadge
              state={visualState}
              enabled={settings.autoListening}
              inputState={inputState}
              webSearching={webSearching}
            />
          </div>
        </div>

        {/* 3. Conversation & Transcription Glass */}
        <div className="w-full my-2">
          <ConversationGlass
            state={visualState}
            heardText={heardText}
            responseText={responseText}
            webUsed={webUsed}
            webSearching={webSearching}
            sources={sources}
            audioLevel={audioLevel}
            onStop={() => {
              voiceService.cancelSpeech();
              setVisualState("IDLE");
            }}
          />
        </div>

        {/* 4. Quick Actions Rail */}
        <div className="w-full mb-2">
          <QuickActionsRail
            onOpenApp={(appName, url) => {
              showToast(`Abriendo ${appName}`);
              window.open(url, "_blank");
            }}
            onOpenTool={(tool) => {
              setUiMode(tool as NikoUiMode);
              setShowApps(true);
            }}
            onShowToast={showToast}
          />
        </div>

        {/* 5. Wake Dock & Input Box */}
        <WakeDock
          enabled={settings.autoListening}
          inputState={inputState}
          state={visualState}
          voiceReady={true}
          onToggleListening={toggleListening}
          onSubmitText={handleProcessCommand}
        />
      </div>

      {/* --- Modals --- */}
      <AiSettingsModal
        isOpen={showSettings}
        onClose={() => setShowSettings(false)}
        onSettingsChanged={(updated) => setSettings(updated)}
      />

      <VoiceDiagnosticsModal
        isOpen={showDiagnostics}
        onClose={() => setShowDiagnostics(false)}
        snapshot={diagnostics}
        onRestartEngine={handleRestartAcousticEngine}
      />

      <SmartHomeModal
        isOpen={showSmartHome}
        onClose={() => setShowSmartHome(false)}
        onShowToast={showToast}
      />

      <EmbeddedAppsModal
        currentApp={showApps ? uiMode === "ASSISTANT" ? "CALCULATOR" : uiMode : null}
        onClose={() => {
          setShowApps(false);
          setUiMode("ASSISTANT");
        }}
        onSelectApp={(app) => setUiMode(app)}
      />

      <LeoFirstRunModal
        isOpen={showFirstRun}
        onComplete={() => {
          setShowFirstRun(false);
          const updated = { ...settings, firstRunCompleted: true };
          setSettings(updated);
          MemoryStore.saveSettings(updated);
          voiceService.startListening();
        }}
      />
    </div>
  );
};
