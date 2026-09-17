import { ConversationTurn, SmartHomeEntity } from "../types";

const STORAGE_KEYS = {
  SETTINGS: "leo_settings_v1",
  FACTS: "leo_facts_v1",
  HISTORY: "leo_history_v1",
  SMART_HOME: "leo_smarthome_v1",
};

export interface AppSettings {
  autoListening: boolean;
  voicePitch: number;
  voiceRate: number;
  personality: "BALANCED" | "CONCISE" | "TECHNICAL";
  groqApiKey: string;
  localFirst: boolean;
  autoResearch: boolean;
  ownerVoiceOnly: boolean;
  ownerEnrolled: boolean;
  firstRunCompleted: boolean;
}

const DEFAULT_SETTINGS: AppSettings = {
  autoListening: true,
  voicePitch: 1.0,
  voiceRate: 1.05,
  personality: "BALANCED",
  groqApiKey: "",
  localFirst: true,
  autoResearch: true,
  ownerVoiceOnly: false,
  ownerEnrolled: true,
  firstRunCompleted: true,
};

const INITIAL_SMART_HOME: SmartHomeEntity[] = [
  { id: "light.living_room", name: "Luz de la Sala", type: "light", state: true },
  { id: "switch.kitchen_fan", name: "Ventilador de Cocina", type: "fan", state: false },
  { id: "climate.thermostat", name: "Termostato", type: "climate", state: "22°C" },
  { id: "lock.front_door", name: "Cerradura Principal", type: "lock", state: true },
];

export class MemoryStore {
  public static getSettings(): AppSettings {
    try {
      const raw = localStorage.getItem(STORAGE_KEYS.SETTINGS);
      return raw ? { ...DEFAULT_SETTINGS, ...JSON.parse(raw) } : DEFAULT_SETTINGS;
    } catch {
      return DEFAULT_SETTINGS;
    }
  }

  public static saveSettings(settings: Partial<AppSettings>) {
    try {
      const current = this.getSettings();
      const updated = { ...current, ...settings };
      localStorage.setItem(STORAGE_KEYS.SETTINGS, JSON.stringify(updated));
    } catch (e) {
      console.warn("Failed to save settings:", e);
    }
  }

  public static getFacts(): string[] {
    try {
      const raw = localStorage.getItem(STORAGE_KEYS.FACTS);
      return raw ? JSON.parse(raw) : [
        "Prefiere respuestas directas en español",
        "Utiliza la voz para tareas cotidianas y comandos rápidos"
      ];
    } catch {
      return [];
    }
  }

  public static addFact(fact: string) {
    const facts = this.getFacts();
    if (!facts.includes(fact)) {
      facts.push(fact);
      localStorage.setItem(STORAGE_KEYS.FACTS, JSON.stringify(facts));
    }
  }

  public static clearMemory() {
    localStorage.removeItem(STORAGE_KEYS.FACTS);
  }

  public static getHistory(): ConversationTurn[] {
    try {
      const raw = localStorage.getItem(STORAGE_KEYS.HISTORY);
      return raw ? JSON.parse(raw) : [];
    } catch {
      return [];
    }
  }

  public static saveHistory(turns: ConversationTurn[]) {
    try {
      // Keep last 30 turns
      localStorage.setItem(STORAGE_KEYS.HISTORY, JSON.stringify(turns.slice(-30)));
    } catch (e) {
      console.warn("Failed to save history:", e);
    }
  }

  public static getSmartHome(): SmartHomeEntity[] {
    try {
      const raw = localStorage.getItem(STORAGE_KEYS.SMART_HOME);
      return raw ? JSON.parse(raw) : INITIAL_SMART_HOME;
    } catch {
      return INITIAL_SMART_HOME;
    }
  }

  public static updateSmartHomeEntity(id: string, newState: boolean | string) {
    const entities = this.getSmartHome().map((e) =>
      e.id === id ? { ...e, state: newState } : e
    );
    localStorage.setItem(STORAGE_KEYS.SMART_HOME, JSON.stringify(entities));
    return entities;
  }
}
