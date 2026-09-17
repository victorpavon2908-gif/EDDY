import React, { useState } from "react";
import { X, Home, Lightbulb, Fan, Thermometer, Lock, Check } from "lucide-react";
import { MemoryStore } from "../services/memoryStore";
import { SmartHomeEntity } from "../types";

interface SmartHomeModalProps {
  isOpen: boolean;
  onClose: () => void;
  onShowToast: (msg: string) => void;
}

export const SmartHomeModal: React.FC<SmartHomeModalProps> = ({
  isOpen,
  onClose,
  onShowToast,
}) => {
  const [entities, setEntities] = useState<SmartHomeEntity[]>(MemoryStore.getSmartHome());
  const [haUrl, setHaUrl] = useState(localStorage.getItem("leo_ha_url") || "http://homeassistant.local:8123");
  const [haToken, setHaToken] = useState(localStorage.getItem("leo_ha_token") || "");
  const [saveNote, setSaveNote] = useState("");

  if (!isOpen) return null;

  const handleSaveConfig = () => {
    localStorage.setItem("leo_ha_url", haUrl.trim());
    localStorage.setItem("leo_ha_token", haToken.trim());
    setSaveNote("Configuración de Home Assistant guardada");
    setTimeout(() => setSaveNote(""), 2500);
  };

  const handleToggle = async (entity: SmartHomeEntity) => {
    let nextState: boolean | string = !entity.state;
    if (entity.type === "climate") {
      const current = parseInt(entity.state as string, 10) || 22;
      nextState = `${current >= 26 ? 20 : current + 1}°C`;
    }

    const updated = MemoryStore.updateSmartHomeEntity(entity.id, nextState);
    setEntities([...updated]);

    onShowToast(`${entity.name}: ${typeof nextState === "boolean" ? (nextState ? "Encendido" : "Apagado") : nextState}`);

    // Call backend endpoint
    try {
      await fetch("/api/smarthome", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          action: typeof nextState === "boolean" ? (nextState ? "turn_on" : "turn_off") : "set_temperature",
          entity: entity.id,
          baseUrl: haUrl,
          token: haToken,
        }),
      });
    } catch (_e) {}
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/80 backdrop-blur-md">
      <div
        id="smarthome-dialog"
        className="w-full max-w-lg max-h-[90vh] flex flex-col rounded-2xl bg-[#0B111C] border border-white/10 shadow-2xl overflow-hidden"
      >
        {/* Header */}
        <div className="flex items-center justify-between p-4 border-b border-white/10 bg-[#080D15]">
          <div className="flex items-center gap-2">
            <Home className="w-5 h-5 text-teal-400" />
            <h2 className="text-base font-bold text-white tracking-wide">
              Casa Inteligente (Home Assistant)
            </h2>
          </div>
          <button
            onClick={onClose}
            className="p-1.5 rounded-full hover:bg-white/10 text-white/60 hover:text-white transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Body */}
        <div className="flex-1 overflow-y-auto p-5 space-y-6 text-sm">
          {/* Connection settings */}
          <div className="p-3.5 rounded-xl bg-white/5 border border-white/5 space-y-3">
            <h3 className="text-xs font-semibold text-teal-400 uppercase tracking-wider">
              Conexión al Servidor
            </h3>
            <div>
              <label className="block text-xs text-white/70 mb-1">URL de Home Assistant</label>
              <input
                type="text"
                value={haUrl}
                onChange={(e) => setHaUrl(e.target.value)}
                placeholder="http://192.168.1.100:8123"
                className="w-full bg-white/5 text-white text-xs rounded-xl px-3 py-2 border border-white/10 focus:outline-none focus:border-teal-400 font-mono"
              />
            </div>
            <div>
              <label className="block text-xs text-white/70 mb-1">Token de Acceso de Larga Duración</label>
              <input
                type="password"
                value={haToken}
                onChange={(e) => setHaToken(e.target.value)}
                placeholder="eyJhbGciOiJIUzI1NiIsInR5cCI6..."
                className="w-full bg-white/5 text-white text-xs rounded-xl px-3 py-2 border border-white/10 focus:outline-none focus:border-teal-400 font-mono"
              />
            </div>
            <div className="flex justify-between items-center pt-1">
              <span className="text-xs text-emerald-400 font-medium">{saveNote}</span>
              <button
                type="button"
                onClick={handleSaveConfig}
                className="px-3 py-1.5 rounded-lg bg-teal-500/20 text-teal-300 hover:bg-teal-500/30 border border-teal-500/30 text-xs font-semibold"
              >
                Guardar credenciales
              </button>
            </div>
          </div>

          {/* Interactive Entities */}
          <div className="space-y-3">
            <h3 className="text-xs font-semibold text-teal-400 uppercase tracking-wider">
              Dispositivos Vinculados
            </h3>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-2.5">
              {entities.map((item) => {
                const isBool = typeof item.state === "boolean";
                const active = isBool ? (item.state as boolean) : true;

                return (
                  <div
                    key={item.id}
                    onClick={() => handleToggle(item)}
                    className={`p-3 rounded-xl border flex items-center justify-between cursor-pointer transition-all active:scale-98 select-none ${
                      active
                        ? "bg-teal-950/30 border-teal-500/40 shadow-sm"
                        : "bg-white/5 border-white/5 opacity-70"
                    }`}
                  >
                    <div className="flex items-center gap-3 min-w-0">
                      <div
                        className={`w-9 h-9 rounded-lg flex items-center justify-center shrink-0 ${
                          active ? "bg-teal-400/20 text-teal-300" : "bg-white/5 text-white/40"
                        }`}
                      >
                        {item.type === "light" ? (
                          <Lightbulb className="w-5 h-5" />
                        ) : item.type === "fan" ? (
                          <Fan className="w-5 h-5" />
                        ) : item.type === "climate" ? (
                          <Thermometer className="w-5 h-5" />
                        ) : (
                          <Lock className="w-5 h-5" />
                        )}
                      </div>
                      <div className="min-w-0">
                        <div className="font-semibold text-white text-xs truncate">
                          {item.name}
                        </div>
                        <div className="text-[11px] text-white/50 truncate">
                          {isBool ? (item.state ? "Encendido" : "Apagado") : item.state}
                        </div>
                      </div>
                    </div>

                    <span
                      className={`w-3 h-3 rounded-full shrink-0 ${
                        active ? "bg-teal-400 shadow-[0_0_8px_rgba(45,212,191,0.8)]" : "bg-slate-700"
                      }`}
                    />
                  </div>
                );
              })}
            </div>
          </div>
        </div>

        {/* Footer */}
        <div className="p-4 border-t border-white/10 bg-[#080D15] flex justify-end">
          <button
            onClick={onClose}
            className="px-4 py-2 rounded-xl text-xs font-semibold bg-teal-500 hover:bg-teal-400 text-slate-950 shadow-md active:scale-95 transition-all"
          >
            Listo
          </button>
        </div>
      </div>
    </div>
  );
};
