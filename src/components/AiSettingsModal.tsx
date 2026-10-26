import React, { useEffect, useRef, useState } from "react";
import { X, Check, Mic, Sliders, ShieldCheck, Globe, BookOpen } from "lucide-react";
import { MemoryStore, AppSettings } from "../services/memoryStore";

interface AiSettingsModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSettingsChanged: (settings: AppSettings) => void;
}

export const AiSettingsModal: React.FC<AiSettingsModalProps> = ({ isOpen, onClose, onSettingsChanged }) => {
  const dialog = useRef<HTMLDialogElement>(null);
  const [settings, setSettings] = useState<AppSettings>(() => MemoryStore.getSettings());
  const [message, setMessage] = useState("");
  const [failed, setFailed] = useState(false);
  const [tab, setTab] = useState("Voz");

  useEffect(() => {
    if (isOpen) {
      setSettings(MemoryStore.getSettings());
      setMessage("");
      setFailed(false);
      dialog.current?.showModal();
    } else {
      dialog.current?.close();
    }
  }, [isOpen]);

  const update = <K extends keyof AppSettings>(key: K, value: AppSettings[K]) => {
    setSettings(current => ({ ...current, [key]: value }));
    setMessage("Cambios sin guardar");
    setFailed(false);
  };
  const handleSave = () => {
    if (!MemoryStore.saveSettings(settings)) {
      setFailed(true);
      setMessage("No se pudo guardar. Revisá el almacenamiento del navegador y reintentá.");
      return;
    }
    const full = MemoryStore.getSettings();
    setSettings(full);
    onSettingsChanged(full);
    setFailed(false);
    setMessage("Configuración guardada");
  };
  const toggle = (key: "autoListening" | "localFirst" | "autoResearch", title: string, description: string) => (
    <label className="flex items-center justify-between gap-4 p-4 rounded-xl bg-white/5 border border-white/10 cursor-pointer">
      <span><span className="block font-medium text-white">{title}</span><span className="block text-xs text-slate-300 mt-1">{description}</span></span>
      <input type="checkbox" checked={settings[key]} onChange={e => update(key, e.target.checked)} className="w-5 h-5 shrink-0 accent-teal-400" />
    </label>
  );

  return (
    <dialog ref={dialog} onCancel={event => { event.preventDefault(); onClose(); }} aria-labelledby="settings-title"
      className="m-auto w-[calc(100%-2rem)] max-w-xl max-h-[90dvh] p-0 rounded-3xl bg-[#0B111C] text-white border border-white/15 shadow-2xl backdrop:bg-black/80 backdrop:backdrop-blur-sm">
      <div className="flex flex-col max-h-[90dvh]">
        <header className="flex items-center justify-between gap-3 p-5 border-b border-white/10">
          <div><p className="text-xs tracking-widest text-teal-300 mb-1">EDDY / LEO · VERSIÓN WEB</p><h2 id="settings-title" className="text-xl font-semibold flex items-center gap-2"><Sliders size={20} /> Configuración</h2></div>
          <button onClick={onClose} aria-label="Cerrar configuración" className="p-3 rounded-full hover:bg-white/10"><X size={22} /></button>
        </header>
        <nav aria-label="Categorías de configuración" className="flex gap-2 p-4 overflow-x-auto border-b border-white/10">
          {["Voz", "Inteligencia", "Ayuda"].map(name => <button key={name} aria-pressed={tab === name} onClick={() => setTab(name)} className={`px-4 py-2 rounded-full text-sm whitespace-nowrap ${tab === name ? "bg-teal-300 text-slate-950 font-semibold" : "bg-white/5 text-slate-200"}`}>{name}</button>)}
        </nav>
        <div className="overflow-y-auto p-5 space-y-5 text-sm">
          <p className="text-slate-300 text-xs">Ajustá tus preferencias y pulsá Guardar para aplicarlas.</p>
          {tab === "Voz" && <>
            <h3 className="flex items-center gap-2 font-semibold text-teal-300"><Mic size={18} /> Voz y escucha</h3>
            {toggle("autoListening", "Escuchar «LEO»", "Requiere permiso de micrófono y un navegador compatible con reconocimiento de voz.")}
            <div className="p-4 rounded-xl bg-white/5 space-y-5">
              <label className="block">Velocidad de habla <span className="float-right text-teal-300">{settings.voiceRate.toFixed(2)}×</span><input aria-label="Velocidad de habla" type="range" min="0.75" max="1.5" step="0.05" value={settings.voiceRate} onChange={e => update("voiceRate", Number(e.target.value))} className="block w-full mt-3 accent-teal-400" /></label>
              <label className="block">Tono de voz <span className="float-right text-teal-300">{settings.voicePitch.toFixed(2)}</span><input aria-label="Tono de voz" type="range" min="0.8" max="1.3" step="0.05" value={settings.voicePitch} onChange={e => update("voicePitch", Number(e.target.value))} className="block w-full mt-3 accent-teal-400" /></label>
            </div>
            <div className="p-4 rounded-xl border border-amber-300/20 bg-amber-300/5 text-slate-200"><h3 className="flex items-center gap-2 font-semibold mb-2"><ShieldCheck size={18} /> Reconocimiento de mi voz</h3>Disponible en la aplicación Android con el modelo CAMPPlus instalado. Esta versión web no registra una huella vocal ni distingue al propietario.</div>
          </>}
          {tab === "Inteligencia" && <>
            <h3 className="flex items-center gap-2 font-semibold text-teal-300"><Globe size={18} /> Personalidad e Internet</h3>
            <fieldset><legend className="mb-3 text-slate-200">Estilo de respuesta</legend><div className="grid grid-cols-3 gap-2">
              {(["BALANCED", "CONCISE", "TECHNICAL"] as const).map(p => <label key={p} className={`text-center rounded-xl p-3 cursor-pointer border ${settings.personality === p ? "border-teal-300 bg-teal-300/10" : "border-white/10"}`}><input type="radio" name="personality" value={p} checked={settings.personality === p} onChange={() => update("personality", p)} className="sr-only peer" /><span className="peer-focus-visible:outline peer-focus-visible:outline-2">{p === "BALANCED" ? "Equilibrado" : p === "CONCISE" ? "Conciso" : "Técnico"}</span></label>)}
            </div></fieldset>
            {toggle("localFirst", "Respuestas locales primero", "Prioriza comandos y cálculos que el asistente puede resolver localmente.")}
            {toggle("autoResearch", "Investigar dudas en Internet", "Permite consultar fuentes públicas. Las consultas se envían a servicios externos.")}
            <p className="text-slate-300">Para pedir una búsqueda explícita: «LEO, buscá en internet…». Revisá los enlaces; un extracto no significa que se haya leído la página completa.</p>
          </>}
          {tab === "Ayuda" && <>
            <h3 className="flex items-center gap-2 font-semibold text-teal-300"><BookOpen size={18} /> Primeros pasos</h3>
            <ol className="list-decimal pl-5 space-y-3 text-slate-200"><li>Permití el micrófono cuando el navegador lo solicite.</li><li>Activá la escucha, guardá y decí «LEO» seguido de tu pregunta.</li><li>Abrí las herramientas para usar las funciones disponibles en la web.</li><li>Para noticias o datos actuales, pedí una búsqueda y revisá sus fuentes.</li></ol>
            <p className="text-slate-300">Si no escucha, revisá los permisos del sitio y el diagnóstico de voz. El navegador puede suspender la escucha en segundo plano.</p>
            <p className="text-slate-300">Los datos locales pertenecen a este navegador. Borrarlos o desinstalar la app puede eliminar recuerdos. La enciclopedia y los modelos locales descritos en el manual son funciones de Android.</p>
            <a href="https://github.com/victorpavon2908-gif/EDDY/blob/main/docs/MANUAL_USUARIO_LEO.md" target="_blank" rel="noreferrer" className="inline-block text-teal-300 underline py-2">Leer la guía completa de Android (requiere Internet)</a>
          </>}
        </div>
        <footer className="p-4 border-t border-white/10 flex flex-wrap items-center gap-3 justify-between">
          <span role="status" className={`text-xs flex-1 ${failed ? "text-rose-300" : "text-teal-300"}`}>{message}</span>
          <button onClick={handleSave} className="px-5 py-3 rounded-xl font-semibold bg-teal-300 text-slate-950 flex items-center gap-2"><Check size={18} /> Guardar</button>
        </footer>
      </div>
    </dialog>
  );
};
