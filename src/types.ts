export type NikoVisualState = "IDLE" | "LISTENING" | "THINKING" | "SPEAKING";

export type InputState = "PREPARING" | "READY" | "ERROR";

export type NikoUiMode =
  | "ASSISTANT"
  | "VOICE_DIAGNOSTICS"
  | "CALCULATOR"
  | "STOPWATCH"
  | "TIMER"
  | "CLOCK"
  | "NOTES"
  | "CONVERTER"
  | "AI_SETTINGS"
  | "SMART_HOME_SETTINGS"
  | "GOOGLE_SEARCH";

export type RobotMotion = "WAVE" | "JUMP" | "DANCE" | "SPIN";

export interface NikoWebSource {
  title: string;
  url: string;
  snippet: string;
  domain: string;
  date?: string;
  score?: number;
  sourceName?: string;
  provider?: string;
}

export interface GoogleSearchResult {
  query: string;
  provider: string;
  summary: string;
  sources: NikoWebSource[];
  suggestions?: string[];
  groundedWithGoogle?: boolean;
}

export interface ConversationTurn {
  id: string;
  role: "user" | "assistant";
  text: string;
  timestamp: number;
  sources?: NikoWebSource[];
  motion?: RobotMotion;
}

export interface AssistantCommand {
  type:
    | "unknown"
    | "greeting"
    | "tell_time"
    | "search_web"
    | "open_app"
    | "open_tool"
    | "send_message"
    | "torch"
    | "battery_status"
    | "robot_motion"
    | "smart_home"
    | "math"
    | "memory_summary"
    | "clear_memory"
    | "stop";
  payload?: any;
}

export interface LeoVoiceSnapshot {
  wakeState: string;
  audioLevelDbfs: number;
  noiseFloorDbfs: number;
  snrDb: number;
  wakeLatencyMs: number;
  transcriptionEngine: string;
  transcriptionLatencyMs: number;
  speechEngine: string;
  speechStartLatencyMs: number;
  ownerProfileEnabled: boolean;
  ownerScore: number;
  ownerAccepted: boolean;
  lastTranscript: string;
  metrics: {
    completedCalls: number;
    targetCalls: number;
    truePositives: number;
    falseNegatives: number;
    falsePositives: number;
  };
}

export interface SmartHomeEntity {
  id: string;
  name: string;
  type: "light" | "switch" | "climate" | "lock" | "fan";
  state: boolean | string;
}
