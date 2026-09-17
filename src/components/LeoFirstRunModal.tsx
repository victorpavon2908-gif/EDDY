import React from "react";
import { Mic, ShieldCheck, Sparkles } from "lucide-react";

interface LeoFirstRunModalProps {
  isOpen: boolean;
  onComplete: () => void;
}

export const LeoFirstRunModal: React.FC<LeoFirstRunModalProps> = ({ isOpen, onComplete }) => {
  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/85 backdrop-blur-md">
      <div
        id="first-run-dialog"
        className="w-full max-w-sm rounded-3xl bg-[#0B111C] border border-white/10 shadow-2xl p-6 flex flex-col items-center text-center space-y-4"
      >
        <div className="w-16 h-16 rounded-full bg-teal-500/20 border border-teal-500/30 flex items-center justify-center mb-1">
          <img src="/leo_robot_portrait.png" alt="LEO" className="w-12 h-12 object-contain" />
        </div>

        <h2 className="text-xl font-black tracking-wide text-white font-mono">
          HOLA, SOY LEO
        </h2>

        <p className="text-xs text-white/70 leading-relaxed">
          Tu compañero personal inteligente con control por voz en español, resolución matemática, búsqueda web y visualización 3D interactiva.
        </p>

        <div className="w-full space-y-2 text-left text-xs bg-white/5 p-3 rounded-2xl border border-white/5">
          <div className="flex items-center gap-2 text-teal-300">
            <Mic className="w-4 h-4 shrink-0" />
            <span>Activación por voz con «Leo»</span>
          </div>
          <div className="flex items-center gap-2 text-teal-300">
            <ShieldCheck className="w-4 h-4 shrink-0" />
            <span>Procesamiento local prioritario y seguro</span>
          </div>
          <div className="flex items-center gap-2 text-teal-300">
            <Sparkles className="w-4 h-4 shrink-0" />
            <span>Robot 3D animado con bailes y giros</span>
          </div>
        </div>

        <button
          onClick={onComplete}
          className="w-full py-3 rounded-2xl bg-teal-500 hover:bg-teal-400 active:scale-98 text-slate-950 font-bold text-xs tracking-wider uppercase transition-all shadow-lg shadow-teal-500/20"
        >
          Comenzar Experiencia
        </button>
      </div>
    </div>
  );
};
