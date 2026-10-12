import React, { useEffect, useRef } from "react";
import { NikoVisualState, RobotMotion } from "../types";

interface LeoEmblemProps {
  visualState: NikoVisualState;
  requestedMotion: RobotMotion | null;
  onMotionDone?: () => void;
  onTriggerMotion?: (motion: RobotMotion) => void;
  enabled?: boolean;
}

export const LeoEmblem: React.FC<LeoEmblemProps> = ({
  visualState, requestedMotion, onMotionDone, onTriggerMotion, enabled = true,
}) => {
  const emblem = useRef<HTMLImageElement>(null);
  const done = useRef(onMotionDone);
  done.current = onMotionDone;
  useEffect(() => {
    const element = emblem.current;
    if (!element || !requestedMotion) return;
    if (!enabled || visualState === "LISTENING" || visualState === "THINKING" ||
        document.hidden || window.matchMedia("(prefers-reduced-motion: reduce)").matches || !element.animate) {
      done.current?.();
      return;
    }
    const frames: Record<RobotMotion, Keyframe[]> = {
      WAVE: [0, -8, 8, -8, 0].map(angle => ({ transform: `rotate(${angle}deg)` })),
      JUMP: [0, -24, 0].map(y => ({ transform: `translateY(${y}px)` })),
      DANCE: [0, -10, 10, -10, 10, 0].map(angle => ({ transform: `rotate(${angle}deg)` })),
      SPIN: [{ transform: "rotate(0deg)" }, { transform: "rotate(360deg)" }],
    };
    const animation = element.animate(frames[requestedMotion], {
      duration: { WAVE: 800, JUMP: 700, DANCE: 1600, SPIN: 1100 }[requestedMotion],
      easing: "ease-in-out", iterations: 1,
    });
    animation.onfinish = () => done.current?.();
    const onVisibility = () => { if (document.hidden) { animation.cancel(); done.current?.(); } };
    document.addEventListener("visibilitychange", onVisibility);
    return () => { animation.onfinish = null; animation.cancel(); document.removeEventListener("visibilitychange", onVisibility); };
  }, [requestedMotion, enabled, visualState]);
  const description = !enabled ? "LEO está en pausa" : {
    IDLE: "LEO está listo", LISTENING: "LEO te escucha", THINKING: "LEO está pensando", SPEAKING: "LEO está hablando",
  }[visualState];
  return (
    <button type="button" disabled={!enabled} onClick={() => onTriggerMotion?.("WAVE")}
      aria-label={description} title={enabled ? "Saludar con LEO" : description}
      className="relative flex h-full w-full items-center justify-center rounded-3xl p-8 focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-teal-300">
      <img ref={emblem} src="/leo_emblem.webp" alt="" width="360" height="341"
        draggable={false} className="max-h-full max-w-full object-contain rounded-2xl" />
    </button>
  );
};
