import React from "react";
import { Mic, Globe, Square, Volume2 } from "lucide-react";
import { NikoVisualState, NikoWebSource } from "../types";
import { NeuralWaveform } from "./NeuralWaveform";

interface ConversationGlassProps {
  state: NikoVisualState;
  heardText: string;
  responseText: string;
  webUsed: boolean;
  webSearching: boolean;
  sources: NikoWebSource[];
  audioLevel?: number;
  onStop?: () => void;
}

export const ConversationGlass: React.FC<ConversationGlassProps> = ({
  state,
  heardText,
  responseText,
  webUsed,
  webSearching,
  sources,
  audioLevel = -45,
  onStop,
}) => {
  const accentColor =
    state === "SPEAKING"
      ? "text-emerald-400"
      : state === "LISTENING"
      ? "text-teal-400"
      : state === "THINKING"
      ? "text-cyan-400"
      : "text-teal-400";

  const label =
    state === "LISTENING"
      ? "TE ESCUCHO"
      : state === "THINKING"
      ? webSearching
        ? "BUSCANDO EN LA RED"
        : "PROCESANDO"
      : state === "SPEAKING"
      ? "LEO"
      : "LEO";

  const message =
    state === "LISTENING"
      ? heardText || "Decime qué necesitás..."
      : state === "THINKING"
      ? heardText || "Estoy trabajando en eso..."
      : state === "SPEAKING"
      ? responseText || "Aquí estoy."
      : responseText || "Decí “Leo” y hablame normal, o tocá el micrófono.";

  return (
    <section
      id="conversation-glass"
      className="w-full rounded-2xl bg-[#0A101A]/85 border border-white/10 backdrop-blur-xl p-4 shadow-2xl transition-all duration-300 relative overflow-hidden"
    >
      <div className="flex items-start justify-between gap-3">
        <div className="flex items-start gap-3 flex-1 min-w-0">
          {/* Avatar Icon */}
          <div className="w-9 h-9 rounded-full bg-teal-500/15 border border-teal-500/30 flex items-center justify-center shrink-0 mt-0.5">
            {webUsed || webSearching ? (
              <Globe className="w-4 h-4 text-cyan-400 animate-pulse" />
            ) : state === "SPEAKING" ? (
              <Volume2 className="w-4 h-4 text-emerald-400 animate-bounce" />
            ) : (
              <Mic className={`w-4 h-4 ${accentColor}`} />
            )}
          </div>

          {/* Text message */}
          <div className="flex-1 min-w-0">
            <div className="flex items-center gap-2">
              <span className={`text-[10px] font-bold tracking-widest uppercase ${accentColor}`}>
                {label}
              </span>
              {state === "SPEAKING" && onStop && (
                <button
                  id="btn-stop-speaking"
                  onClick={onStop}
                  className="inline-flex items-center gap-1 text-[10px] text-rose-300 hover:text-rose-200 bg-rose-950/40 border border-rose-500/30 px-2 py-0.5 rounded-full transition-colors active:scale-95"
                  title="Parar voz («Leo, pará»)"
                >
                  <Square className="w-2.5 h-2.5 fill-rose-400" />
                  <span>Pará</span>
                </button>
              )}
            </div>

            <p className="mt-1 text-sm md:text-base font-medium text-white/95 leading-relaxed break-words line-clamp-4">
              {message}
            </p>
          </div>
        </div>
      </div>

      {/* Waveform visualization */}
      <div className="mt-3">
        <NeuralWaveform state={state} audioLevel={audioLevel} />
      </div>

      {/* User previous speech transcript */}
      {heardText && state !== "LISTENING" && (
        <div className="mt-2 text-[11px] text-white/40 truncate font-mono">
          <span className="font-semibold text-white/50">VOS ·</span> {heardText}
        </div>
      )}

      {/* Web sources pills */}
      {sources && sources.length > 0 && (
        <div className="mt-3 pt-2 border-t border-white/5 flex items-center gap-1.5 overflow-x-auto no-scrollbar py-1">
          <span className="text-[10px] text-white/40 font-semibold uppercase tracking-wider shrink-0 mr-1">
            Fuentes:
          </span>
          {sources.slice(0, 4).map((source, index) => (
            <a
              key={index}
              href={source.url}
              target="_blank"
              rel="noopener noreferrer"
              className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full bg-white/5 hover:bg-white/10 border border-white/10 text-[10px] text-teal-200/80 hover:text-teal-100 transition-colors whitespace-nowrap shrink-0"
              title={source.title}
            >
              <span className="font-bold text-teal-400">{index + 1}</span>
              <span className="max-w-[140px] truncate">{source.title || source.domain}</span>
            </a>
          ))}
        </div>
      )}
    </section>
  );
};
