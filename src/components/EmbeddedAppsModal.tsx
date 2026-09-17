import React, { useState, useEffect } from "react";
import {
  X,
  Calculator,
  Timer as TimerIcon,
  Clock as ClockIcon,
  FileText,
  ArrowLeftRight,
  Play,
  Pause,
  RotateCcw,
  Plus,
  Trash2,
} from "lucide-react";
import { NikoUiMode } from "../types";

interface EmbeddedAppsModalProps {
  currentApp: NikoUiMode | null;
  onClose: () => void;
  onSelectApp: (app: NikoUiMode) => void;
}

export const EmbeddedAppsModal: React.FC<EmbeddedAppsModalProps> = ({
  currentApp,
  onClose,
  onSelectApp,
}) => {
  if (!currentApp || currentApp === "ASSISTANT" || currentApp === "AI_SETTINGS" || currentApp === "VOICE_DIAGNOSTICS" || currentApp === "SMART_HOME_SETTINGS") {
    return null;
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-3 sm:p-4 bg-black/85 backdrop-blur-md">
      <div
        id="embedded-app-dialog"
        className="w-full max-w-md max-h-[92vh] flex flex-col rounded-2xl bg-[#0B111C] border border-white/10 shadow-2xl overflow-hidden"
      >
        {/* App Bar */}
        <div className="flex items-center justify-between p-3.5 border-b border-white/10 bg-[#080D15]">
          <div className="flex items-center gap-2">
            <span className="text-xs font-mono font-bold text-teal-400 tracking-wider uppercase">
              LEO APPS
            </span>
            <span className="text-white/30 text-xs">/</span>
            <span className="text-xs font-semibold text-white">
              {currentApp === "CALCULATOR"
                ? "Calculadora"
                : currentApp === "STOPWATCH"
                ? "Cronómetro"
                : currentApp === "TIMER"
                ? "Temporizador"
                : currentApp === "CLOCK"
                ? "Reloj Mundial"
                : currentApp === "NOTES"
                ? "Bloc de Notas"
                : "Conversor"}
            </span>
          </div>

          <button
            onClick={onClose}
            className="p-1 rounded-full hover:bg-white/10 text-white/60 hover:text-white transition-colors"
            title="Volver a LEO"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Tab switch bar */}
        <div className="flex items-center gap-1 p-2 bg-black/40 border-b border-white/5 overflow-x-auto no-scrollbar text-xs">
          {[
            { id: "CALCULATOR", label: "Calculadora", icon: Calculator },
            { id: "STOPWATCH", label: "Cronómetro", icon: TimerIcon },
            { id: "TIMER", label: "Temporizador", icon: ClockIcon },
            { id: "CLOCK", label: "Reloj", icon: ClockIcon },
            { id: "NOTES", label: "Notas", icon: FileText },
            { id: "CONVERTER", label: "Conversor", icon: ArrowLeftRight },
          ].map((tab) => {
            const Icon = tab.icon;
            const active = currentApp === tab.id;
            return (
              <button
                key={tab.id}
                onClick={() => onSelectApp(tab.id as NikoUiMode)}
                className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg whitespace-nowrap transition-all ${
                  active
                    ? "bg-teal-500/20 text-teal-300 border border-teal-500/30 font-medium"
                    : "text-white/50 hover:text-white/80 hover:bg-white/5"
                }`}
              >
                <Icon className="w-3.5 h-3.5" />
                <span>{tab.label}</span>
              </button>
            );
          })}
        </div>

        {/* Active Tool Content */}
        <div className="flex-1 overflow-y-auto p-4 flex flex-col justify-center">
          {currentApp === "CALCULATOR" && <CalculatorView />}
          {currentApp === "STOPWATCH" && <StopwatchView />}
          {currentApp === "TIMER" && <TimerView />}
          {currentApp === "CLOCK" && <ClockView />}
          {currentApp === "NOTES" && <NotesView />}
          {currentApp === "CONVERTER" && <ConverterView />}
        </div>
      </div>
    </div>
  );
};

/* --- 1. Calculator View --- */
function CalculatorView() {
  const [display, setDisplay] = useState("0");
  const [equation, setEquation] = useState("");

  const handleDigit = (d: string) => {
    setDisplay((prev) => (prev === "0" ? d : prev + d));
  };

  const handleOp = (op: string) => {
    setEquation(`${display} ${op} `);
    setDisplay("0");
  };

  const handleClear = () => {
    setDisplay("0");
    setEquation("");
  };

  const handleEqual = () => {
    try {
      const full = `${equation}${display}`.replace(/×/g, "*").replace(/÷/g, "/");
      const res = new Function(`return (${full})`)();
      setDisplay(String(res));
      setEquation("");
    } catch {
      setDisplay("Error");
    }
  };

  const btns = [
    ["C", "±", "%", "÷"],
    ["7", "8", "9", "×"],
    ["4", "5", "6", "-"],
    ["1", "2", "3", "+"],
    ["0", ".", "="],
  ];

  return (
    <div className="w-full space-y-3 select-none">
      <div className="p-4 rounded-xl bg-black/50 border border-white/5 text-right font-mono">
        <div className="text-xs text-white/40 h-4">{equation}</div>
        <div className="text-3xl font-bold text-white tracking-wider truncate">{display}</div>
      </div>
      <div className="grid grid-cols-4 gap-2">
        {btns.flat().map((btn, idx) => {
          const isOp = ["÷", "×", "-", "+", "="].includes(btn);
          const isClear = btn === "C";
          const isZero = btn === "0";
          return (
            <button
              key={idx}
              onClick={() => {
                if (btn === "C") handleClear();
                else if (btn === "=") handleEqual();
                else if (["÷", "×", "-", "+"].includes(btn)) handleOp(btn);
                else if (btn === "±") setDisplay((p) => String(-parseFloat(p) || 0));
                else if (btn === "%") setDisplay((p) => String(parseFloat(p) / 100));
                else handleDigit(btn);
              }}
              className={`py-3.5 rounded-xl font-semibold text-sm transition-all active:scale-95 ${
                isZero ? "col-span-2 text-left px-5" : ""
              } ${
                isOp
                  ? "bg-teal-500/25 text-teal-300 border border-teal-500/30 hover:bg-teal-500/35"
                  : isClear
                  ? "bg-rose-950/30 text-rose-300 border border-rose-500/30"
                  : "bg-white/5 hover:bg-white/10 text-white border border-white/5"
              }`}
            >
              {btn}
            </button>
          );
        })}
      </div>
    </div>
  );
}

/* --- 2. Stopwatch View --- */
function StopwatchView() {
  const [time, setTime] = useState(0);
  const [running, setRunning] = useState(false);
  const [laps, setLaps] = useState<number[]>([]);

  useEffect(() => {
    let interval: any;
    if (running) {
      interval = setInterval(() => setTime((t) => t + 10), 10);
    }
    return () => clearInterval(interval);
  }, [running]);

  const formatTime = (ms: number) => {
    const mins = Math.floor(ms / 60000);
    const secs = Math.floor((ms % 60000) / 1000);
    const hundredths = Math.floor((ms % 1000) / 10);
    return `${String(mins).padStart(2, "0")}:${String(secs).padStart(2, "0")}.${String(
      hundredths
    ).padStart(2, "0")}`;
  };

  return (
    <div className="w-full flex flex-col items-center space-y-5 select-none">
      <div className="text-4xl font-black text-white font-mono tracking-wider py-4">
        {formatTime(time)}
      </div>
      <div className="flex items-center gap-3">
        <button
          onClick={() => setRunning(!running)}
          className={`px-5 py-2.5 rounded-xl font-bold text-xs flex items-center gap-1.5 active:scale-95 transition-all ${
            running ? "bg-amber-500 text-slate-950" : "bg-teal-500 text-slate-950"
          }`}
        >
          {running ? <Pause className="w-4 h-4" /> : <Play className="w-4 h-4" />}
          {running ? "Pausar" : "Iniciar"}
        </button>

        {running && (
          <button
            onClick={() => setLaps([time, ...laps])}
            className="px-4 py-2.5 rounded-xl bg-white/10 text-white font-semibold text-xs border border-white/10 active:scale-95"
          >
            Vuelta
          </button>
        )}

        <button
          onClick={() => {
            setRunning(false);
            setTime(0);
            setLaps([]);
          }}
          className="p-2.5 rounded-xl bg-white/5 hover:bg-white/10 text-white/60 border border-white/10 active:scale-95"
          title="Reiniciar"
        >
          <RotateCcw className="w-4 h-4" />
        </button>
      </div>

      {laps.length > 0 && (
        <div className="w-full max-h-36 overflow-y-auto space-y-1 text-xs font-mono border-t border-white/5 pt-2">
          {laps.map((lap, idx) => (
            <div key={idx} className="flex justify-between py-1 text-white/70 px-2">
              <span>Vuelta {laps.length - idx}</span>
              <span>{formatTime(lap)}</span>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

/* --- 3. Timer View --- */
function TimerView() {
  const [secondsLeft, setSecondsLeft] = useState(300);
  const [initialSeconds, setInitialSeconds] = useState(300);
  const [running, setRunning] = useState(false);

  useEffect(() => {
    let timer: any;
    if (running && secondsLeft > 0) {
      timer = setInterval(() => setSecondsLeft((s) => s - 1), 1000);
    } else if (secondsLeft === 0 && running) {
      setRunning(false);
      try {
        const audio = new Audio("data:audio/wav;base64,UklGRl9vT19tele");
        audio.play().catch(() => {});
      } catch (_e) {}
    }
    return () => clearInterval(timer);
  }, [running, secondsLeft]);

  const mins = Math.floor(secondsLeft / 60);
  const secs = secondsLeft % 60;

  return (
    <div className="w-full flex flex-col items-center space-y-5 select-none">
      <div className="text-5xl font-black text-white font-mono tracking-widest py-4">
        {String(mins).padStart(2, "0")}:{String(secs).padStart(2, "0")}
      </div>

      {/* Preset buttons */}
      <div className="flex gap-2">
        {[60, 300, 600, 900].map((s) => (
          <button
            key={s}
            onClick={() => {
              setRunning(false);
              setSecondsLeft(s);
              setInitialSeconds(s);
            }}
            className="px-2.5 py-1 rounded-lg bg-white/5 hover:bg-white/10 text-xs text-teal-300 border border-white/5"
          >
            {s / 60}m
          </button>
        ))}
      </div>

      <div className="flex items-center gap-3">
        <button
          onClick={() => setRunning(!running)}
          className="px-6 py-2.5 rounded-xl font-bold text-xs bg-teal-500 text-slate-950 flex items-center gap-1.5 active:scale-95"
        >
          {running ? <Pause className="w-4 h-4" /> : <Play className="w-4 h-4" />}
          {running ? "Pausar" : "Empezar"}
        </button>
        <button
          onClick={() => {
            setRunning(false);
            setSecondsLeft(initialSeconds);
          }}
          className="p-2.5 rounded-xl bg-white/5 hover:bg-white/10 text-white/60 border border-white/10"
        >
          <RotateCcw className="w-4 h-4" />
        </button>
      </div>
    </div>
  );
}

/* --- 4. Clock View --- */
function ClockView() {
  const [time, setTime] = useState(new Date());

  useEffect(() => {
    const id = setInterval(() => setTime(new Date()), 1000);
    return () => clearInterval(id);
  }, []);

  const cities = [
    { name: "Buenos Aires", tz: "America/Argentina/Buenos_Aires" },
    { name: "Madrid", tz: "Europe/Madrid" },
    { name: "Nueva York", tz: "America/New_York" },
    { name: "Tokio", tz: "Asia/Tokyo" },
  ];

  return (
    <div className="w-full space-y-4 select-none">
      <div className="text-center py-3 bg-black/40 rounded-xl border border-white/5">
        <div className="text-4xl font-mono font-bold text-white">
          {time.toLocaleTimeString("es-ES")}
        </div>
        <div className="text-xs text-white/50 capitalize mt-1">
          {time.toLocaleDateString("es-ES", { weekday: "long", day: "numeric", month: "long" })}
        </div>
      </div>

      <div className="space-y-2">
        <div className="text-xs font-semibold text-teal-400 uppercase tracking-wider">
          Otras Ciudades
        </div>
        <div className="grid grid-cols-2 gap-2">
          {cities.map((c) => (
            <div key={c.name} className="p-2.5 rounded-xl bg-white/5 border border-white/5">
              <div className="text-xs text-white/60">{c.name}</div>
              <div className="text-sm font-mono font-bold text-white mt-0.5">
                {time.toLocaleTimeString("es-ES", { timeZone: c.tz, hour: "2-digit", minute: "2-digit" })}
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}

/* --- 5. Notes View --- */
function NotesView() {
  const [notes, setNotes] = useState<string[]>(() => {
    try {
      const raw = localStorage.getItem("leo_app_notes");
      return raw ? JSON.parse(raw) : ["Comprar café", "Recordar reunión de las 18:00"];
    } catch {
      return [];
    }
  });
  const [newNote, setNewNote] = useState("");

  const addNote = () => {
    if (newNote.trim()) {
      const next = [newNote.trim(), ...notes];
      setNotes(next);
      localStorage.setItem("leo_app_notes", JSON.stringify(next));
      setNewNote("");
    }
  };

  const removeNote = (idx: number) => {
    const next = notes.filter((_, i) => i !== idx);
    setNotes(next);
    localStorage.setItem("leo_app_notes", JSON.stringify(next));
  };

  return (
    <div className="w-full space-y-3">
      <div className="flex gap-2">
        <input
          type="text"
          value={newNote}
          onChange={(e) => setNewNote(e.target.value)}
          onKeyDown={(e) => e.key === "Enter" && addNote()}
          placeholder="Escribí una nota rápida..."
          className="flex-1 bg-white/5 text-white text-xs rounded-xl px-3 py-2 border border-white/10 focus:outline-none focus:border-teal-400"
        />
        <button
          onClick={addNote}
          className="p-2 rounded-xl bg-teal-500 text-slate-950 font-bold active:scale-95"
        >
          <Plus className="w-4 h-4" />
        </button>
      </div>

      <div className="max-h-48 overflow-y-auto space-y-1.5">
        {notes.length === 0 ? (
          <div className="text-center text-xs text-white/40 py-6">No hay notas guardadas</div>
        ) : (
          notes.map((n, i) => (
            <div
              key={i}
              className="flex items-center justify-between p-2.5 rounded-xl bg-white/5 border border-white/5 text-xs text-white"
            >
              <span className="truncate pr-2">{n}</span>
              <button
                onClick={() => removeNote(i)}
                className="text-white/40 hover:text-rose-400 p-1"
              >
                <Trash2 className="w-3.5 h-3.5" />
              </button>
            </div>
          ))
        )}
      </div>
    </div>
  );
}

/* --- 6. Converter View --- */
function ConverterView() {
  const [val, setVal] = useState("10");
  const [mode, setMode] = useState<"km_mi" | "kg_lb" | "c_f">("km_mi");

  const num = parseFloat(val) || 0;
  let result = "";
  let unitFrom = "";
  let unitTo = "";

  if (mode === "km_mi") {
    result = (num * 0.621371).toFixed(2);
    unitFrom = "Kilómetros";
    unitTo = "Millas";
  } else if (mode === "kg_lb") {
    result = (num * 2.20462).toFixed(2);
    unitFrom = "Kilogramos";
    unitTo = "Libras";
  } else {
    result = ((num * 9) / 5 + 32).toFixed(1);
    unitFrom = "°Celsius";
    unitTo = "°Fahrenheit";
  }

  return (
    <div className="w-full space-y-4 select-none">
      <div className="grid grid-cols-3 gap-2">
        {(["km_mi", "kg_lb", "c_f"] as const).map((m) => (
          <button
            key={m}
            onClick={() => setMode(m)}
            className={`py-1.5 rounded-lg text-xs font-medium border transition-all ${
              mode === m
                ? "bg-teal-500/20 border-teal-400 text-white"
                : "bg-white/5 border-white/5 text-white/60"
            }`}
          >
            {m === "km_mi" ? "Distancia" : m === "kg_lb" ? "Peso" : "Temp."}
          </button>
        ))}
      </div>

      <div className="p-4 rounded-xl bg-black/40 border border-white/5 space-y-3">
        <div>
          <label className="text-[11px] text-white/50">{unitFrom}</label>
          <input
            type="number"
            value={val}
            onChange={(e) => setVal(e.target.value)}
            className="w-full bg-white/5 text-white text-lg font-mono rounded-lg px-3 py-1.5 border border-white/10 focus:outline-none focus:border-teal-400"
          />
        </div>
        <div className="border-t border-white/5 pt-2">
          <label className="text-[11px] text-white/50">{unitTo}</label>
          <div className="text-2xl font-bold font-mono text-teal-300">{result}</div>
        </div>
      </div>
    </div>
  );
}
