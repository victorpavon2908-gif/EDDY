import React from "react";
import { X, RefreshCw, Activity, CheckCircle2 } from "lucide-react";
import { LeoVoiceSnapshot } from "../types";

interface VoiceDiagnosticsModalProps {
  isOpen: boolean;
  onClose: () => void;
  snapshot: LeoVoiceSnapshot;
  onRestartEngine: () => void;
}

export const VoiceDiagnosticsModal: React.FC<VoiceDiagnosticsModalProps> = ({
  isOpen,
  onClose,
  snapshot,
  onRestartEngine,
}) => {
  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/80 backdrop-blur-md">
      <div
        id="voice-diagnostics-dialog"
        className="w-full max-w-lg max-h-[90vh] flex flex-col rounded-2xl bg-[#0B111C] border border-white/10 shadow-2xl overflow-hidden"
      >
        {/* Header */}
        <div className="flex items-center justify-between p-4 border-b border-white/10 bg-[#080D15]">
          <div className="flex items-center gap-2">
            <Activity className="w-5 h-5 text-teal-400" />
            <div>
              <h2 className="text-base font-bold text-white tracking-wide">
                Diagnóstico de Voz
              </h2>
              <div className="text-[11px] text-white/50">
                Medición de telemetría acústica y latencia local
              </div>
            </div>
          </div>
          <button
            onClick={onClose}
            className="p-1.5 rounded-full hover:bg-white/10 text-white/60 hover:text-white transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Body */}
        <div className="flex-1 overflow-y-auto p-5 space-y-5 text-sm">
          {/* Real-time telemetry card */}
          <div className="p-4 rounded-xl bg-white/5 border border-white/5 space-y-2.5">
            <div className="flex items-center justify-between border-b border-white/5 pb-2">
              <span className="text-xs font-semibold uppercase tracking-wider text-teal-400">
                Escucha en Vivo
              </span>
              <span className="inline-flex items-center gap-1 text-[11px] text-emerald-400 bg-emerald-950/40 border border-emerald-500/30 px-2 py-0.5 rounded-full">
                <CheckCircle2 className="w-3 h-3" /> Conectado
              </span>
            </div>

            <div className="grid grid-cols-2 gap-y-2 text-xs">
              <span className="text-white/50">Palabra de activación (Wake):</span>
              <span className="text-white font-mono text-right">{snapshot.wakeState}</span>

              <span className="text-white/50">Nivel de audio:</span>
              <span className="text-white font-mono text-right">{snapshot.audioLevelDbfs.toFixed(1)} dBFS</span>

              <span className="text-white/50">Ruido ambiente estimado:</span>
              <span className="text-white font-mono text-right">{snapshot.noiseFloorDbfs.toFixed(1)} dBFS</span>

              <span className="text-white/50">Relación SNR instantánea:</span>
              <span className="text-white font-mono text-right">{snapshot.snrDb.toFixed(1)} dB</span>

              <span className="text-white/50">Latencia de detección:</span>
              <span className="text-white font-mono text-right">{snapshot.wakeLatencyMs} ms</span>

              <span className="text-white/50">Motor de transcripción (ASR):</span>
              <span className="text-white font-mono text-right">{snapshot.transcriptionEngine}</span>

              <span className="text-white/50">Motor de habla (TTS):</span>
              <span className="text-white font-mono text-right">{snapshot.speechEngine}</span>

              <span className="text-white/50">Respuesta → Primer sonido:</span>
              <span className="text-white font-mono text-right">{snapshot.speechStartLatencyMs} ms</span>

              <span className="text-white/50">Verificación de dueño (CAMPPlus):</span>
              <span className="text-teal-300 font-mono text-right">
                {snapshot.ownerProfileEnabled
                  ? `${Math.round(snapshot.ownerScore * 100)}% · Aceptado`
                  : "No exigido"}
              </span>
            </div>

            {/* Last transcript text */}
            <div className="mt-2 pt-2 border-t border-white/5">
              <div className="text-[10px] text-white/40 uppercase tracking-wide">Última transcripción:</div>
              <div className="mt-1 text-xs text-white/80 italic">
                "{snapshot.lastTranscript || "Aún no se ha registrado una transcripción completa."}"
              </div>
            </div>
          </div>

          {/* Benchmark 100-calls card */}
          <div className="p-4 rounded-xl bg-white/5 border border-white/5 space-y-3">
            <div className="flex items-center justify-between border-b border-white/5 pb-2">
              <span className="text-xs font-semibold uppercase tracking-wider text-teal-400">
                Banco de Aceptación (100 Llamadas)
              </span>
              <span className="text-xs font-mono text-white/60">
                {snapshot.metrics.completedCalls}/{snapshot.metrics.targetCalls}
              </span>
            </div>

            <div className="grid grid-cols-3 gap-2 text-center">
              <div className="p-2.5 rounded-lg bg-emerald-950/30 border border-emerald-500/20">
                <div className="text-lg font-bold text-emerald-400 font-mono">
                  {snapshot.metrics.truePositives}
                </div>
                <div className="text-[10px] text-white/50">TP (Correctas)</div>
              </div>

              <div className="p-2.5 rounded-lg bg-amber-950/30 border border-amber-500/20">
                <div className="text-lg font-bold text-amber-400 font-mono">
                  {snapshot.metrics.falseNegatives}
                </div>
                <div className="text-[10px] text-white/50">FN (No detectadas)</div>
              </div>

              <div className="p-2.5 rounded-lg bg-rose-950/30 border border-rose-500/20">
                <div className="text-lg font-bold text-rose-400 font-mono">
                  {snapshot.metrics.falsePositives}
                </div>
                <div className="text-[10px] text-white/50">FP (Falsas alarmas)</div>
              </div>
            </div>
          </div>
        </div>

        {/* Footer */}
        <div className="p-4 border-t border-white/10 bg-[#080D15] flex items-center justify-between">
          <button
            onClick={onRestartEngine}
            className="flex items-center gap-1.5 px-3 py-2 rounded-xl text-xs font-medium text-white/80 hover:text-white bg-white/5 hover:bg-white/10 border border-white/10 active:scale-95 transition-all"
          >
            <RefreshCw className="w-3.5 h-3.5" /> Reiniciar Motor Acústico
          </button>
          <button
            onClick={onClose}
            className="px-4 py-2 rounded-xl text-xs font-semibold bg-teal-500 hover:bg-teal-400 text-slate-950 shadow-md active:scale-95 transition-all"
          >
            Aceptar
          </button>
        </div>
      </div>
    </div>
  );
};
