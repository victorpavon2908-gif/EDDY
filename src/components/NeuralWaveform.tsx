import React, { useEffect, useRef } from "react";
import { NikoVisualState } from "../types";

interface NeuralWaveformProps {
  state: NikoVisualState;
  audioLevel?: number; // dBFS (-60 to 0)
}

export const NeuralWaveform: React.FC<NeuralWaveformProps> = ({ state, audioLevel = -45 }) => {
  const canvasRef = useRef<HTMLCanvasElement>(null);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext("2d");
    if (!ctx) return;

    let animId: number;
    let phase = 0;

    const bars = 38;

    const speed =
      state === "SPEAKING"
        ? 0.08
        : state === "LISTENING"
        ? 0.05
        : state === "THINKING"
        ? 0.03
        : 0.015;

    const render = () => {
      phase += speed;
      const width = canvas.width;
      const height = canvas.height;
      ctx.clearRect(0, 0, width, height);

      const slot = width / bars;
      const centerY = height / 2;

      // Primary accent color based on state
      const baseR = state === "SPEAKING" ? 86 : state === "LISTENING" ? 79 : state === "THINKING" ? 128 : 100;
      const baseG = state === "SPEAKING" ? 219 : state === "LISTENING" ? 228 : state === "THINKING" ? 92 : 115;
      const baseB = state === "SPEAKING" ? 196 : state === "LISTENING" ? 248 : state === "THINKING" ? 255 : 130;

      for (let i = 0; i < bars; i++) {
        const harmonic = Math.abs(Math.sin(phase * Math.PI * 2 + i * 0.53));
        const envelope = 0.42 + 0.58 * Math.max(0, Math.sin((i * Math.PI) / (bars - 1)));

        let amount = 0.08;
        if (state === "LISTENING") {
          const liveBoost = Math.max(0, (audioLevel + 60) / 40);
          amount = 0.35 + liveBoost * 0.45;
        } else if (state === "THINKING") {
          amount = 0.32;
        } else if (state === "SPEAKING") {
          amount = 0.85;
        }

        const barHeight = Math.max(3, height * (0.08 + harmonic * envelope * amount));
        const t = i / (bars - 1);

        // Gradient mix to violet accent #7D73FF
        const r = Math.round(baseR * (1 - t) + 125 * t);
        const g = Math.round(baseG * (1 - t) + 115 * t);
        const b = Math.round(baseB * (1 - t) + 255 * t);
        const alpha = state === "IDLE" ? 0.28 : 0.82;

        ctx.fillStyle = `rgba(${r}, ${g}, ${b}, ${alpha})`;
        const barWidth = Math.max(2, slot * 0.3);
        const x = i * slot + (slot - barWidth) / 2;
        const y = centerY - barHeight / 2;

        // Rounded bar
        ctx.beginPath();
        const radius = barWidth / 2;
        ctx.roundRect(x, y, barWidth, barHeight, radius);
        ctx.fill();
      }

      animId = requestAnimationFrame(render);
    };

    render();

    return () => {
      cancelAnimationFrame(animId);
    };
  }, [state, audioLevel]);

  return (
    <div className="w-full h-7 flex items-center justify-center overflow-hidden">
      <canvas ref={canvasRef} width={340} height={28} className="w-full h-full" />
    </div>
  );
};
