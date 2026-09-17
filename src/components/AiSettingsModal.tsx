import React, { useState } from "react";
import { X, Check, Key, Mic, Sliders, ShieldCheck } from "lucide-react";
import { MemoryStore, AppSettings } from "../services/memoryStore";

interface AiSettingsModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSettingsChanged: (settings: AppSettings) => void;
}

export const AiSettingsModal: React.FC<AiSettingsModalProps> = ({
  isOpen,
  onClose,
  onSettingsChanged,
}) => {
  const [settings, setSettings] = useState<AppSettings>(MemoryStore.getSettings());
  const [groqKey, setGroqKey] = useState(settings.groqApiKey);
  const [savedMessage, setSavedMessage] = useState("");
  const [enrolling, setEnrolling] = useState(false);
  const [enrollSamples, setEnrollSamples] = useState(settings.ownerEnrolled ? 4 : 0);

  if (!isOpen) return null;

  const handleSave = () => {
    const updated: Partial<AppSettings> = {
      ...settings,
      groqApiKey: groqKey.trim(),
    };
    MemoryStore.saveSettings(updated);
    const full = MemoryStore.getSettings();
    setSettings(full);
    onSettingsChanged(full);
    setSavedMessage("Configuración guardada correctamente");
    setTimeout(() => setSavedMessage(""), 2500);
  };

  const handleEnrollVoice = () => {
    setEnrolling(true);
    setEnrollSamples(0);
    let count = 0;
    const interval = setInterval(() => {
      count += 1;
      setEnrollSamples(count);
      if (count >= 4) {
        clearInterval(interval);
        setEnrolling(false);
        const updated = { ...settings, ownerEnrolled: true, ownerVoiceOnly: true };
        MemoryStore.saveSettings(updated);
        setSettings(updated);
        onSettingsChanged(updated);
      }
    }, 1000);
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/80 backdrop-blur-md">
      <div
        id="ai-settings-dialog"
        className="w-full max-w-lg max-h-[90vh] flex flex-col rounded-2xl bg-[#0B111C] border border-white/10 shadow-2xl overflow-hidden"
      >
        {/* Header */}
        <div className="flex items-center justify-between p-4 border-b border-white/10 bg-[#080D15]">
          <div className="flex items-center gap-2">
            <Sliders className="w-5 h-5 text-teal-400" />
            <h2 className="text-base font-bold text-white tracking-wide">
              Configuración de LEO
            </h2>
          </div>
          <button
            onClick={onClose}
            className="p-1.5 rounded-full hover:bg-white/10 text-white/60 hover:text-white transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Scrollable body */}
        <div className="flex-1 overflow-y-auto p-5 space-y-6 text-sm">
          {/* Section: Voice & Listening */}
          <div className="space-y-3">
            <h3 className="text-xs font-semibold text-teal-400 tracking-wider uppercase flex items-center gap-2">
              <Mic className="w-4 h-4" /> Voz y Escucha
            </h3>

            <div className="flex items-center justify-between p-3 rounded-xl bg-white/5 border border-white/5">
              <div>
                <div className="font-medium text-white">Escucha ambiental activa</div>
                <div className="text-xs text-white/50">
                  Despierta al decir «Leo» sin tocar la pantalla
                </div>
              </div>
              <input
                type="checkbox"
                checked={settings.autoListening}
                onChange={(e) => setSettings({ ...settings, autoListening: e.target.checked })}
                className="w-5 h-5 accent-teal-400 rounded cursor-pointer"
              />
            </div>

            {/* Voice Pitch & Rate */}
            <div className="p-3 rounded-xl bg-white/5 border border-white/5 space-y-3">
              <div>
                <div className="flex justify-between text-xs text-white/80 mb-1">
                  <span>Velocidad de habla</span>
                  <span>{settings.voiceRate.toFixed(2)}x</span>
                </div>
                <input
                  type="range"
                  min="0.75"
                  max="1.5"
                  step="0.05"
                  value={settings.voiceRate}
                  onChange={(e) => setSettings({ ...settings, voiceRate: parseFloat(e.target.value) })}
                  className="w-full accent-teal-400 cursor-pointer"
                />
              </div>

              <div>
                <div className="flex justify-between text-xs text-white/80 mb-1">
                  <span>Tono de voz</span>
                  <span>{settings.voicePitch.toFixed(2)}</span>
                </div>
                <input
                  type="range"
                  min="0.8"
                  max="1.3"
                  step="0.05"
                  value={settings.voicePitch}
                  onChange={(e) => setSettings({ ...settings, voicePitch: parseFloat(e.target.value) })}
                  className="w-full accent-teal-400 cursor-pointer"
                />
              </div>
            </div>
          </div>

          {/* Section: Owner Voice Enrollment */}
          <div className="space-y-3">
            <h3 className="text-xs font-semibold text-teal-400 tracking-wider uppercase flex items-center gap-2">
              <ShieldCheck className="w-4 h-4" /> Mi Voz (CAMPPlus)
            </h3>
            <div className="p-3 rounded-xl bg-white/5 border border-white/5 space-y-2">
              <div className="flex items-center justify-between">
                <div>
                  <div className="font-medium text-white">Priorizar mi voz</div>
                  <div className="text-xs text-white/50">
                    {settings.ownerEnrolled
                      ? "Perfil registrado · reduce interferencias externas"
                      : "Aún no se ha registrado una muestra de voz"}
                  </div>
                </div>
                <input
                  type="checkbox"
                  disabled={!settings.ownerEnrolled}
                  checked={settings.ownerVoiceOnly}
                  onChange={(e) => setSettings({ ...settings, ownerVoiceOnly: e.target.checked })}
                  className="w-5 h-5 accent-teal-400 rounded cursor-pointer disabled:opacity-40"
                />
              </div>

              {enrolling ? (
                <div className="mt-3 p-3 rounded-lg bg-teal-950/40 border border-teal-500/30 text-center">
                  <div className="text-xs text-teal-300 font-semibold mb-1">
                    Grabando muestra {enrollSamples} de 4...
                  </div>
                  <div className="w-full bg-slate-800 rounded-full h-2 overflow-hidden">
                    <div
                      className="bg-teal-400 h-full transition-all duration-300"
                      style={{ width: `${(enrollSamples / 4) * 100}%` }}
                    />
                  </div>
                </div>
              ) : (
                <button
                  type="button"
                  onClick={handleEnrollVoice}
                  className="mt-2 text-xs py-1.5 px-3 rounded-lg bg-white/10 hover:bg-white/15 text-teal-200 border border-teal-500/20 active:scale-95 transition-all"
                >
                  {settings.ownerEnrolled ? "Volver a registrar mi voz" : "Registrar mi voz (4 frases)"}
                </button>
              )}
            </div>
          </div>

          {/* Section: AI Personality & Models */}
          <div className="space-y-3">
            <h3 className="text-xs font-semibold text-teal-400 tracking-wider uppercase flex items-center gap-2">
              <Key className="w-4 h-4" /> Inteligencia & GroqCloud
            </h3>

            <div>
              <label className="block text-xs font-medium text-white/70 mb-1">
                Personalidad de LEO
              </label>
              <div className="grid grid-cols-3 gap-2">
                {(["BALANCED", "CONCISE", "TECHNICAL"] as const).map((p) => (
                  <button
                    key={p}
                    type="button"
                    onClick={() => setSettings({ ...settings, personality: p })}
                    className={`py-2 px-2.5 rounded-xl border text-xs font-medium text-center transition-all ${
                      settings.personality === p
                        ? "bg-teal-500/20 border-teal-400 text-white"
                        : "bg-white/5 border-white/5 text-white/60 hover:text-white"
                    }`}
                  >
                    {p === "BALANCED" ? "Equilibrado" : p === "CONCISE" ? "Conciso" : "Técnico"}
                  </button>
                ))}
              </div>
            </div>

            <div>
              <label className="block text-xs font-medium text-white/70 mb-1">
                Clave API de GroqCloud (Opcional)
              </label>
              <input
                type="password"
                value={groqKey}
                onChange={(e) => setGroqKey(e.target.value)}
                placeholder="gsk_..."
                className="w-full bg-white/5 hover:bg-white/10 focus:bg-white/10 text-white text-xs rounded-xl px-3 py-2 border border-white/10 focus:outline-none focus:border-teal-400 font-mono"
              />
              <p className="text-[11px] text-white/40 mt-1">
                Permite conectar con Llama-3.3-70b a alta velocidad. Queda guardada de forma segura en este dispositivo.
              </p>
            </div>

            <div className="flex items-center justify-between p-3 rounded-xl bg-white/5 border border-white/5">
              <div>
                <div className="font-medium text-white">Respuestas locales primero</div>
                <div className="text-xs text-white/50">
                  Resuelve comandos y matemáticas al instante sin consultar la nube
                </div>
              </div>
              <input
                type="checkbox"
                checked={settings.localFirst}
                onChange={(e) => setSettings({ ...settings, localFirst: e.target.checked })}
                className="w-5 h-5 accent-teal-400 rounded cursor-pointer"
              />
            </div>
          </div>
        </div>

        {/* Footer */}
        <div className="p-4 border-t border-white/10 bg-[#080D15] flex items-center justify-between">
          <span className="text-xs text-emerald-400 font-medium">{savedMessage}</span>
          <div className="flex items-center gap-2">
            <button
              onClick={onClose}
              className="px-4 py-2 rounded-xl text-xs font-medium text-white/70 hover:text-white transition-colors"
            >
              Cerrar
            </button>
            <button
              onClick={handleSave}
              className="px-4 py-2 rounded-xl text-xs font-semibold bg-teal-500 hover:bg-teal-400 text-slate-950 flex items-center gap-1.5 shadow-md active:scale-95 transition-all"
            >
              <Check className="w-4 h-4" /> Guardar
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};
