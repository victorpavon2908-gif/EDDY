import React, { useState } from "react";
import { Flashlight, Play, MessageCircle, Clock, Music } from "lucide-react";

interface QuickActionsRailProps {
  onOpenApp: (appName: string, url: string) => void;
  onOpenTool: (tool: string) => void;
  onShowToast: (msg: string) => void;
}

export const QuickActionsRail: React.FC<QuickActionsRailProps> = ({
  onOpenApp,
  onOpenTool,
  onShowToast,
}) => {
  const [torchActive, setTorchActive] = useState(false);

  const toggleTorch = async () => {
    try {
      if ("mediaDevices" in navigator && navigator.mediaDevices.getUserMedia) {
        const stream = await navigator.mediaDevices.getUserMedia({
          video: { facingMode: "environment" },
        });
        const track = stream.getVideoTracks()[0];
        const capabilities = (track.getCapabilities && (track.getCapabilities() as any)) || {};

        if (capabilities.torch) {
          const nextState = !torchActive;
          await (track as any).applyConstraints({ advanced: [{ torch: nextState }] });
          setTorchActive(nextState);
          onShowToast(nextState ? "Linterna encendida" : "Linterna apagada");
          return;
        }
      }
    } catch (_e) {
      // Browser constraint or permission rejection
    }

    // Fallback UI simulation of flashlight
    const nextState = !torchActive;
    setTorchActive(nextState);
    if (nextState) {
      document.body.classList.add("filter", "brightness-125");
      onShowToast("Linterna simulada activada");
    } else {
      document.body.classList.remove("filter", "brightness-125");
      onShowToast("Linterna apagada");
    }
  };

  const actions = [
    {
      id: "quick-torch",
      label: "Linterna",
      icon: Flashlight,
      color: "text-cyan-400 border-cyan-500/30 bg-cyan-950/20",
      activeColor: "text-cyan-200 border-cyan-400 bg-cyan-500/30 shadow-[0_0_12px_rgba(34,211,238,0.5)]",
      onClick: toggleTorch,
      isActive: torchActive,
    },
    {
      id: "quick-youtube",
      label: "YouTube",
      icon: Play,
      color: "text-rose-400 border-rose-500/30 bg-rose-950/20",
      onClick: () => onOpenApp("YouTube", "https://www.youtube.com"),
    },
    {
      id: "quick-whatsapp",
      label: "WhatsApp",
      icon: MessageCircle,
      color: "text-emerald-400 border-emerald-500/30 bg-emerald-950/20",
      onClick: () => onOpenApp("WhatsApp", "https://web.whatsapp.com"),
    },
    {
      id: "quick-spotify",
      label: "Spotify",
      icon: Music,
      color: "text-green-400 border-green-500/30 bg-green-950/20",
      onClick: () => onOpenApp("Spotify", "https://open.spotify.com"),
    },
    {
      id: "quick-clock",
      label: "Alarmas",
      icon: Clock,
      color: "text-purple-400 border-purple-500/30 bg-purple-950/20",
      onClick: () => onOpenTool("CLOCK"),
    },
  ];

  return (
    <nav
      id="quick-actions-rail"
      aria-label="Acciones rápidas"
      className="w-full flex items-center justify-between gap-2 py-1 select-none"
    >
      {actions.map((act) => {
        const IconComponent = act.icon;
        const currentStyle = act.isActive ? act.activeColor : act.color;
        return (
          <div key={act.id} className="flex flex-col items-center gap-1.5 flex-1 min-w-0">
            <button
              id={act.id}
              onClick={act.onClick}
              className={`w-12 h-12 rounded-full border flex items-center justify-center transition-all duration-200 active:scale-95 ${currentStyle}`}
              title={act.label}
            >
              <IconComponent className="w-5 h-5" />
            </button>
            <span className="text-[10px] font-medium text-white/55 truncate">
              {act.label}
            </span>
          </div>
        );
      })}
    </nav>
  );
};
