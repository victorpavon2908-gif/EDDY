import React, { useState } from "react";
import { Mic, MicOff, Send, Globe } from "lucide-react";
import { NikoVisualState, InputState } from "../types";

interface WakeDockProps {
  enabled: boolean;
  inputState: InputState;
  state: NikoVisualState;
  voiceReady: boolean;
  onToggleListening: () => void;
  onSubmitText: (text: string) => void;
  onOpenGoogleSearch?: (query?: string) => void;
}

export const WakeDock: React.FC<WakeDockProps> = ({
  enabled,
  inputState,
  state,
  onToggleListening,
  onSubmitText,
  onOpenGoogleSearch,
}) => {
  const [inputText, setInputText] = useState("");

  const ready = enabled && inputState === "READY";
  const isListening = state === "LISTENING";

  const title = !enabled
    ? "LEO EN PAUSA"
    : inputState === "PREPARING"
    ? "PREPARANDO ESCUCHA LOCAL"
    : inputState !== "READY"
    ? "ESCUCHA NO DISPONIBLE"
    : isListening
    ? "ESCUCHANDO TU VOZ..."
    : "ESCUCHA AMBIENTAL ACTIVA";

  const detail = ready
    ? "Decí “Leo” · no necesitás tocar la pantalla"
    : "Tocá el micrófono para iniciar la interacción";

  const handleSend = (e: React.FormEvent) => {
    e.preventDefault();
    if (inputText.trim()) {
      onSubmitText(inputText.trim());
      setInputText("");
    }
  };

  return (
    <footer
      id="wake-dock"
      className="w-full rounded-2xl bg-[#080D15]/90 border border-white/10 backdrop-blur-xl p-3 shadow-xl transition-all select-none"
    >
      <div className="flex items-center justify-between gap-3">
        {/* Microphone / Wake button */}
        <button
          id="btn-toggle-mic"
          onClick={onToggleListening}
          className={`w-11 h-11 rounded-full flex items-center justify-center shrink-0 transition-all duration-300 active:scale-90 ${
            isListening
              ? "bg-emerald-500 text-white shadow-[0_0_18px_rgba(16,185,129,0.7)] animate-pulse"
              : enabled
              ? "bg-teal-500/20 text-teal-300 border border-teal-500/40 hover:bg-teal-500/30"
              : "bg-slate-800 text-slate-400 border border-slate-700"
          }`}
          title={enabled ? "Pausar o activar escucha" : "Reanudar escucha de LEO"}
        >
          {enabled ? <Mic className="w-5 h-5" /> : <MicOff className="w-5 h-5" />}
        </button>

        {/* Status text */}
        <div className="flex-1 min-w-0">
          <h2 className="text-xs font-bold tracking-wider text-white/90 truncate uppercase font-mono">
            {title}
          </h2>
          <p className="text-[11px] text-white/45 truncate mt-0.5">{detail}</p>
        </div>
      </div>

      {/* Manual text input fallback */}
      <form onSubmit={handleSend} className="mt-2.5 flex items-center gap-2">
        <input
          id="input-command-text"
          type="text"
          value={inputText}
          onChange={(e) => setInputText(e.target.value)}
          placeholder="Escribí un comando o pregunta para Leo..."
          className="flex-1 bg-white/5 hover:bg-white/10 focus:bg-white/10 text-white text-xs rounded-xl px-3 py-2 border border-white/10 focus:outline-none focus:border-teal-400/60 placeholder:text-white/30 transition-all font-sans"
        />
        <button
          id="btn-wake-dock-google-search"
          type="button"
          onClick={() => onOpenGoogleSearch && onOpenGoogleSearch(inputText.trim())}
          className="p-2 rounded-xl bg-blue-500/15 text-blue-300 hover:bg-blue-500/25 border border-blue-500/30 active:scale-95 transition-all shrink-0"
          title="Búsqueda Web con Google"
        >
          <Globe className="w-4 h-4 text-blue-400" />
        </button>
        <button
          id="btn-send-command"
          type="submit"
          disabled={!inputText.trim()}
          className="p-2 rounded-xl bg-teal-500/20 text-teal-300 hover:bg-teal-500/30 border border-teal-500/30 disabled:opacity-30 disabled:pointer-events-none active:scale-95 transition-all shrink-0"
          title="Enviar mensaje"
        >
          <Send className="w-4 h-4" />
        </button>
      </form>
    </footer>
  );
};
