import React from "react";
import { NikoVisualState, InputState } from "../types";

interface LiveStateBadgeProps {
  state: NikoVisualState;
  enabled: boolean;
  inputState: InputState;
  webSearching: boolean;
}

export const LiveStateBadge: React.FC<LiveStateBadgeProps> = ({
  state,
  enabled,
  inputState,
  webSearching,
}) => {
  let text = "LISTO PARA VOS";
  let dotBg = "bg-teal-400";
  let border = "border-teal-500/30";

  if (!enabled) {
    text = "EN PAUSA";
    dotBg = "bg-slate-400";
    border = "border-slate-600/30";
  } else if (inputState === "PREPARING") {
    text = "INICIANDO OÍDO LOCAL";
    dotBg = "bg-amber-400 animate-pulse";
    border = "border-amber-500/30";
  } else if (inputState === "ERROR") {
    text = "MICRÓFONO NO DISPONIBLE";
    dotBg = "bg-rose-400";
    border = "border-rose-500/30";
  } else if (webSearching) {
    text = "BUSCANDO EN INTERNET";
    dotBg = "bg-sky-400 animate-ping";
    border = "border-sky-500/30";
  } else if (state === "LISTENING") {
    text = "ESCUCHANDO";
    dotBg = "bg-emerald-400 animate-pulse";
    border = "border-emerald-500/40";
  } else if (state === "THINKING") {
    text = "PENSANDO";
    dotBg = "bg-cyan-400 animate-pulse";
    border = "border-cyan-500/40";
  } else if (state === "SPEAKING") {
    text = "HABLANDO";
    dotBg = "bg-emerald-400";
    border = "border-emerald-500/40";
  }

  return (
    <div
      id="live-state-badge"
      className={`inline-flex items-center gap-2 px-3 py-1 rounded-full bg-[#0B111C]/85 backdrop-blur-md border ${border} shadow-lg transition-all duration-300 select-none`}
    >
      <span className={`w-1.5 h-1.5 rounded-full ${dotBg}`} />
      <span className="text-[10px] font-bold tracking-wider text-white/80 uppercase">
        {text}
      </span>
    </div>
  );
};
