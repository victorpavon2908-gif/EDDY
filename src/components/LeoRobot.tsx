import React, { useEffect, useRef, useState } from "react";
import * as THREE from "three";
import { GLTFLoader } from "three/examples/jsm/loaders/GLTFLoader.js";
import { NikoVisualState, RobotMotion } from "../types";

interface LeoRobotProps {
  visualState: NikoVisualState;
  requestedMotion: RobotMotion | null;
  onMotionDone?: () => void;
  onTriggerMotion?: (motion: RobotMotion) => void;
  enabled?: boolean;
}

export const LeoRobot: React.FC<LeoRobotProps> = ({
  visualState,
  requestedMotion,
  onMotionDone,
  onTriggerMotion,
  enabled = true,
}) => {
  const containerRef = useRef<HTMLDivElement>(null);
  const [modelLoaded, setModelLoaded] = useState(false);
  const [hasError, setHasError] = useState(false);

  // References for Three.js state
  const sceneRef = useRef<THREE.Scene | null>(null);
  const rendererRef = useRef<THREE.WebGLRenderer | null>(null);
  const mixerRef = useRef<THREE.AnimationMixer | null>(null);
  const actionsRef = useRef<Map<string, THREE.AnimationAction>>(new Map());
  const currentActionRef = useRef<THREE.AnimationAction | null>(null);
  const robotGroupRef = useRef<THREE.Group | null>(null);
  const headBoneRef = useRef<THREE.Object3D | null>(null);

  // Cycle motions on tap
  const cycleIndexRef = useRef(0);
  const handleTap = () => {
    if (!enabled) return;
    const motions: RobotMotion[] = ["WAVE", "JUMP", "DANCE", "SPIN"];
    const chosen = motions[cycleIndexRef.current % motions.length];
    cycleIndexRef.current += 1;
    onTriggerMotion?.(chosen);
  };

  useEffect(() => {
    const container = containerRef.current;
    if (!container) return;

    let isDisposed = false;
    const width = container.clientWidth || 320;
    const height = container.clientHeight || 320;

    // Scene setup
    const scene = new THREE.Scene();
    sceneRef.current = scene;

    const camera = new THREE.PerspectiveCamera(40, width / height, 0.1, 50);
    camera.position.set(0, 1.4, 3.4);

    let renderer: THREE.WebGLRenderer;
    try {
      renderer = new THREE.WebGLRenderer({
        antialias: true,
        alpha: true,
        powerPreference: "high-performance",
      });
      renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
      renderer.setSize(width, height);
      renderer.toneMapping = THREE.ACESFilmicToneMapping;
      renderer.toneMappingExposure = 1.1;
      container.appendChild(renderer.domElement);
      rendererRef.current = renderer;
    } catch (e) {
      console.warn("WebGL initialization failed, falling back to poster:", e);
      setHasError(true);
      return;
    }

    // Lighting (matching pearl finish & turquoise accents from LEO spec)
    const ambientLight = new THREE.AmbientLight(0xffffff, 0.85);
    scene.add(ambientLight);

    const keyLight = new THREE.DirectionalLight(0xe8f4ff, 1.4);
    keyLight.position.set(2, 4, 3);
    scene.add(keyLight);

    const rimLight = new THREE.DirectionalLight(0x56dbc4, 1.8);
    rimLight.position.set(-2.5, 2, -2);
    scene.add(rimLight);

    const fillLight = new THREE.DirectionalLight(0x805cff, 0.7);
    fillLight.position.set(0, -1, 2);
    scene.add(fillLight);

    // Subtle floor shadow plane
    const shadowGeo = new THREE.PlaneGeometry(2.4, 2.4);
    const shadowMat = new THREE.MeshBasicMaterial({
      color: 0x010306,
      transparent: true,
      opacity: 0.35,
    });
    const shadowMesh = new THREE.Mesh(shadowGeo, shadowMat);
    shadowMesh.rotation.x = -Math.PI / 2;
    shadowMesh.position.y = 0;
    scene.add(shadowMesh);

    // GLTF Loading
    const loader = new GLTFLoader();
    const modelUrl = "/models/leo_robot.glb";

    loader.load(
      modelUrl,
      (gltf) => {
        if (isDisposed) return;
        const root = gltf.scene;
        robotGroupRef.current = root;
        root.position.set(0, 0, 0);
        root.scale.set(0.88, 0.88, 0.88);
        scene.add(root);

        // Find Head bone for tracking cursor
        root.traverse((child) => {
          if (child.name.toLowerCase().includes("head")) {
            headBoneRef.current = child;
          }
          if ((child as THREE.Mesh).isMesh) {
            const mesh = child as THREE.Mesh;
            mesh.castShadow = true;
            mesh.receiveShadow = true;
          }
        });

        // Animation mixer
        const mixer = new THREE.AnimationMixer(root);
        mixerRef.current = mixer;

        // Map clips
        gltf.animations.forEach((clip) => {
          const action = mixer.clipAction(clip);
          actionsRef.current.set(clip.name.toLowerCase(), action);
        });

        // Start with Idle
        const idleAction =
          actionsRef.current.get("idle") ||
          actionsRef.current.get(Array.from(actionsRef.current.keys())[0] || "");

        if (idleAction) {
          idleAction.play();
          currentActionRef.current = idleAction;
        }

        mixer.addEventListener("finished", () => {
          onMotionDone?.();
        });

        setModelLoaded(true);
      },
      undefined,
      (err) => {
        console.warn("Failed to load 3D GLB model, falling back:", err);
        setHasError(true);
      }
    );

    // Resize observer
    const resizeObserver = new ResizeObserver((entries) => {
      for (const entry of entries) {
        const w = entry.contentRect.width;
        const h = entry.contentRect.height;
        if (w > 0 && h > 0 && rendererRef.current) {
          camera.aspect = w / h;
          camera.updateProjectionMatrix();
          rendererRef.current.setSize(w, h);
        }
      }
    });
    resizeObserver.observe(container);

    // Cursor tracking
    let targetRotY = 0;
    let targetRotX = 0;
    const handlePointerMove = (e: PointerEvent) => {
      const rect = container.getBoundingClientRect();
      const x = (e.clientX - rect.left) / rect.width - 0.5;
      const y = (e.clientY - rect.top) / rect.height - 0.5;
      targetRotY = x * 0.45;
      targetRotX = y * 0.25;
    };
    window.addEventListener("pointermove", handlePointerMove);

    // Render loop
    const clock = new THREE.Clock();
    let animId: number;

    const animate = () => {
      animId = requestAnimationFrame(animate);
      const delta = clock.getDelta();

      if (mixerRef.current) {
        mixerRef.current.update(delta);
      }

      // Smooth head / body turning towards cursor
      if (robotGroupRef.current) {
        robotGroupRef.current.rotation.y +=
          (targetRotY - robotGroupRef.current.rotation.y) * 0.05;
        robotGroupRef.current.rotation.x +=
          (targetRotX - robotGroupRef.current.rotation.x) * 0.05;
      }

      renderer.render(scene, camera);
    };
    animate();

    return () => {
      isDisposed = true;
      cancelAnimationFrame(animId);
      window.removeEventListener("pointermove", handlePointerMove);
      resizeObserver.disconnect();
      if (renderer.domElement && container.contains(renderer.domElement)) {
        container.removeChild(renderer.domElement);
      }
      renderer.dispose();
    };
  }, []);

  // Handle animation transitions (Idle, Listen, Think, Talk, Wave, Jump, Dance, Spin)
  useEffect(() => {
    if (!mixerRef.current || actionsRef.current.size === 0) return;

    // Determine requested clip
    let clipKey = "idle";
    let loopOnce = false;

    if (requestedMotion) {
      clipKey = requestedMotion.toLowerCase();
      loopOnce = true;
    } else {
      switch (visualState) {
        case "LISTENING":
          clipKey = "listen";
          break;
        case "THINKING":
          clipKey = "think";
          break;
        case "SPEAKING":
          clipKey = "talk";
          break;
        case "IDLE":
        default:
          clipKey = "idle";
          break;
      }
    }

    // Find best matching action
    let nextAction =
      actionsRef.current.get(clipKey) ||
      actionsRef.current.get(clipKey.replace(/^([a-z])/, (_, c) => c.toUpperCase()));

    // Fallbacks if specific clip is named differently in the GLB
    if (!nextAction) {
      if (clipKey === "think") nextAction = actionsRef.current.get("idle");
      else if (clipKey === "listen") nextAction = actionsRef.current.get("idle");
      else if (clipKey === "talk") nextAction = actionsRef.current.get("wave") || actionsRef.current.get("idle");
    }

    if (nextAction && nextAction !== currentActionRef.current) {
      const prevAction = currentActionRef.current;
      currentActionRef.current = nextAction;

      if (loopOnce) {
        nextAction.setLoop(THREE.LoopOnce, 1);
        nextAction.clampWhenFinished = true;
      } else {
        nextAction.setLoop(THREE.LoopRepeat, Infinity);
        nextAction.clampWhenFinished = false;
      }

      nextAction.reset();
      // 280ms crossfade blend as specified in LEO_ROBOT.md!
      if (prevAction) {
        prevAction.crossFadeTo(nextAction, 0.28, true);
      }
      nextAction.play();
    }
  }, [visualState, requestedMotion, modelLoaded]);

  return (
    <div
      id="leo-robot-stage"
      onClick={handleTap}
      className="relative w-full h-full flex items-center justify-center cursor-pointer select-none group"
      title={enabled ? "Tocame o decí «Leo, bailá»" : "Leo en pausa"}
    >
      {/* Ambient radial turquoise glow under the robot */}
      <div
        className={`absolute bottom-6 w-56 h-12 rounded-full blur-2xl transition-opacity duration-700 pointer-events-none ${
          visualState === "SPEAKING"
            ? "bg-emerald-400/30 opacity-90 scale-110"
            : visualState === "LISTENING"
            ? "bg-teal-400/25 opacity-80 scale-105"
            : visualState === "THINKING"
            ? "bg-cyan-400/20 opacity-70"
            : "bg-teal-500/15 opacity-50"
        }`}
      />

      {/* 3D WebGL Canvas Container */}
      <div
        ref={containerRef}
        className={`w-full h-full max-w-sm max-h-96 relative z-10 transition-opacity duration-500 ${
          modelLoaded && !hasError ? "opacity-100" : "opacity-0 pointer-events-none"
        }`}
      />

      {/* High-res Poster Fallback while loading or if WebGL not supported */}
      {(!modelLoaded || hasError) && (
        <div className="absolute inset-0 flex flex-col items-center justify-center z-0 p-4">
          <img
            src="/leo_robot_poster.png"
            alt="LEO Robot"
            className="w-48 h-56 object-contain drop-shadow-[0_12px_24px_rgba(0,0,0,0.6)] animate-pulse"
          />
        </div>
      )}

      {/* Quick interactive hint badge */}
      {visualState === "IDLE" && enabled && (
        <span className="absolute top-2 text-[11px] font-medium tracking-wide text-cyan-200/50 bg-slate-900/40 px-3 py-1 rounded-full border border-white/5 backdrop-blur-xs transition-opacity group-hover:text-cyan-200/90">
          Tocame o decí «Leo, bailá»
        </span>
      )}
    </div>
  );
};
