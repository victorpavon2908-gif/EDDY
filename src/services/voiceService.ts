export class VoiceService {
  private recognition: any = null;
  private isListening = false;
  private audioContext: AudioContext | null = null;
  private analyser: AnalyserNode | null = null;
  private mediaStream: MediaStream | null = null;
  private audioDataArray: any = null;
  private volumeCallback: ((volume: number, snr: number) => void) | null = null;
  private animFrameId: number | null = null;

  public onTranscript: ((text: string, isFinal: boolean) => void) | null = null;
  public onError: ((error: string) => void) | null = null;
  public onStateChange: ((listening: boolean) => void) | null = null;

  constructor() {
    this.initRecognition();
  }

  private initRecognition() {
    const SpeechRecognition =
      (window as any).SpeechRecognition || (window as any).webkitSpeechRecognition;

    if (!SpeechRecognition) {
      console.warn("SpeechRecognition not supported in this browser");
      return;
    }

    try {
      this.recognition = new SpeechRecognition();
      this.recognition.continuous = true;
      this.recognition.interimResults = true;
      this.recognition.lang = "es-ES";

      this.recognition.onstart = () => {
        this.isListening = true;
        this.onStateChange?.(true);
      };

      this.recognition.onresult = (event: any) => {
        let interim = "";
        let final = "";

        for (let i = event.resultIndex; i < event.results.length; ++i) {
          if (event.results[i].isFinal) {
            final += event.results[i][0].transcript;
          } else {
            interim += event.results[i][0].transcript;
          }
        }

        if (final.trim()) {
          this.onTranscript?.(final.trim(), true);
        } else if (interim.trim()) {
          this.onTranscript?.(interim.trim(), false);
        }
      };

      this.recognition.onerror = (event: any) => {
        if (event.error !== "no-speech") {
          console.warn("Speech recognition error:", event.error);
          this.onError?.(event.error);
        }
      };

      this.recognition.onend = () => {
        // Auto restart continuous listening if desired
        if (this.isListening) {
          try {
            this.recognition.start();
          } catch (_e) {
            this.isListening = false;
            this.onStateChange?.(false);
          }
        } else {
          this.onStateChange?.(false);
        }
      };
    } catch (e) {
      console.error("Failed to init SpeechRecognition:", e);
    }
  }

  public async startAudioMeter(onVolume: (vol: number, snr: number) => void) {
    this.volumeCallback = onVolume;
    try {
      if (!this.audioContext) {
        const AudioCtx = window.AudioContext || (window as any).webkitAudioContext;
        this.audioContext = new AudioCtx();
      }
      if (this.audioContext.state === "suspended") {
        await this.audioContext.resume();
      }

      if (!this.mediaStream) {
        this.mediaStream = await navigator.mediaDevices.getUserMedia({ audio: true });
      }

      const source = this.audioContext.createMediaStreamSource(this.mediaStream);
      this.analyser = this.audioContext.createAnalyser();
      this.analyser.fftSize = 64;
      source.connect(this.analyser);

      this.audioDataArray = new Uint8Array(this.analyser.frequencyBinCount);

      const loop = () => {
        if (!this.analyser || !this.audioDataArray) return;
        this.analyser.getByteFrequencyData(this.audioDataArray);

        let sum = 0;
        for (let i = 0; i < this.audioDataArray.length; i++) {
          sum += this.audioDataArray[i];
        }
        const avg = sum / this.audioDataArray.length; // 0 to 255
        const normalized = Math.min(1, avg / 90);
        const dbfs = -60 + normalized * 50;
        const noiseFloor = -48;
        const snr = Math.max(0, dbfs - noiseFloor);

        this.volumeCallback?.(dbfs, snr);
        this.animFrameId = requestAnimationFrame(loop);
      };
      loop();
    } catch (e) {
      console.warn("Could not start audio meter:", e);
    }
  }

  public stopAudioMeter() {
    if (this.animFrameId) {
      cancelAnimationFrame(this.animFrameId);
      this.animFrameId = null;
    }
    if (this.mediaStream) {
      this.mediaStream.getTracks().forEach((t) => t.stop());
      this.mediaStream = null;
    }
    if (this.audioContext && this.audioContext.state !== "closed") {
      this.audioContext.close().catch(() => {});
      this.audioContext = null;
    }
  }

  public startListening() {
    if (!this.recognition) return;
    this.isListening = true;
    try {
      this.recognition.start();
    } catch (_e) {
      // might already be started
    }
  }

  public stopListening() {
    this.isListening = false;
    if (this.recognition) {
      try {
        this.recognition.stop();
      } catch (_e) {}
    }
  }

  public speak(
    text: string,
    options?: { pitch?: number; rate?: number; onStart?: () => void; onEnd?: () => void }
  ): Promise<void> {
    return new Promise((resolve) => {
      if (!("speechSynthesis" in window)) {
        options?.onEnd?.();
        resolve();
        return;
      }

      window.speechSynthesis.cancel();

      const utterance = new SpeechSynthesisUtterance(text);
      utterance.lang = "es-ES";
      utterance.pitch = options?.pitch ?? 1.0;
      utterance.rate = options?.rate ?? 1.05;

      // Select best Spanish voice
      const voices = window.speechSynthesis.getVoices();
      const esVoice =
        voices.find((v) => v.lang.startsWith("es") && (v.name.includes("Natural") || v.name.includes("Google") || v.name.includes("Paulina") || v.name.includes("Monica"))) ||
        voices.find((v) => v.lang.startsWith("es")) ||
        null;

      if (esVoice) {
        utterance.voice = esVoice;
      }

      utterance.onstart = () => {
        options?.onStart?.();
      };

      utterance.onend = () => {
        options?.onEnd?.();
        resolve();
      };

      utterance.onerror = (e) => {
        console.warn("Speech synthesis error:", e);
        options?.onEnd?.();
        resolve();
      };

      window.speechSynthesis.speak(utterance);
    });
  }

  public cancelSpeech() {
    if ("speechSynthesis" in window) {
      window.speechSynthesis.cancel();
    }
  }
}

export const voiceService = new VoiceService();
