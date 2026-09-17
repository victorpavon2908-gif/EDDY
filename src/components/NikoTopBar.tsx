import React from "react";
import { Settings, Activity, Home, LayoutGrid } from "lucide-react";
import { NikoVisualState } from "../types";

interface NikoTopBarProps {
  state: NikoVisualState;
  autoListeningEnabled: boolean;
  onOpenSettings: () => void;
  onOpenDiagnostics: () => void;
  onOpenSmartHome: () => void;
  onOpenApps: () => void;
}

export const NikoTopBar: React.FC<NikoTopBarProps> = ({
  state,
  autoListeningEnabled,
  onOpenSettings,
  onOpenDiagnostics,
  onOpenSmartHome,
  onOpenApps,
}) => {
  const dotColor = !autoListeningEnabled
    ? "bg-slate-500"
    : state === "SPEAKING"
    ? "bg-emerald-400 shadow-[0_0_8px_rgba(52,211,153,0.8)]"
    : state === "LISTENING"
    ? "bg-teal-400 shadow-[0_0_8px_rgba(45,212,191,0.8)]"
    : state === "THINKING"
    ? "bg-cyan-400 shadow-[0_0_8px_rgba(34,211,238,0.8)]"
    : "bg-teal-400";

  return (
    <header className="w-full flex items-center justify-between py-2.5 px-3 z-30 select-none">
      <div className="flex flex-col">
        <div className="flex items-center gap-2">
          <h1 className="text-xl md:text-2xl font-black tracking-[0.16em] text-white font-mono">
            LEO
          </h1>
          <span className={`w-2 h-2 rounded-full transition-all duration-300 ${dotColor}`} />
        </div>
        <span className="text-[9px] font-semibold tracking-[0.18em] text-white/40 uppercase">
          Tu compañero personal
        </span>
      </div>

      <div className="flex items-center gap-2">
        <button
          id="btn-open-apps"
          onClick={onOpenApps}
          className="p-2 rounded-full bg-white/5 hover:bg-white/10 active:scale-95 transition-all text-white/75 hover:text-white border border-white/10"
          title="Herramientas y aplicaciones"
        >
          <LayoutGrid className="w-4 h-4" />
        </button>

        <button
          id="btn-open-smarthome"
          onClick={onOpenSmartHome}
          className="p-2 rounded-full bg-white/5 hover:bg-white/10 active:scale-95 transition-all text-white/75 hover:text-white border border-white/10"
          title="Casa inteligente"
        >
          <Home className="w-4 h-4" />
        </button>

        <button
          id="btn-open-diagnostics"
          onClick={onOpenDiagnostics}
          className="p-2 rounded-full bg-white/5 hover:bg-white/10 active:scale-95 transition-all text-white/75 hover:text-white border border-white/10"
          title="Diagnóstico de voz"
        >
          <Activity className="w-4 h-4" />
        </button>

        <button
          id="btn-open-settings"
          onClick={onOpenSettings}
          className="p-2 rounded-full bg-white/5 hover:bg-white/10 active:scale-95 transition-all text-white/75 hover:text-white border border-white/10"
          title="Configuración de IA y Voz"
        >
          <Settings className="w-4 h-4" />
        </button>
      </div>
    </header>
  );
};
