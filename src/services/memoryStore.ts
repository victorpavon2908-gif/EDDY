import { ConversationTurn, SmartHomeEntity } from "../types";

const STORAGE_KEYS = {
  SETTINGS: "leo_settings_v1",
  FACTS: "leo_facts_v1",
  PREFERENCES: "leo_preferences_v1",
  HISTORY: "leo_history_v1",
  SMART_HOME: "leo_smarthome_v1",
  RULES: "leo_autodidact_rules_v1",
  RAG_DOCS: "leo_rag_docs_v1",
  TIMELINE: "leo_timeline_v1",
};

export interface EpisodicEvent {
  kind: string;
  topic: string;
  summary: string;
  timestampMs: number;
}

export interface LearnedRule {
  trigger: string;
  response: string;
  usageCount: number;
}

export interface LocalRagDoc {
  id: string;
  title: string;
  modality: "PDF" | "PHOTO_OCR" | "NOTE";
  content: string;
}

export interface AppSettings {
  autoListening: boolean;
  voicePitch: number;
  voiceRate: number;
  personality: "BALANCED" | "CONCISE" | "TECHNICAL";
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
  localFirst: true,
  autoResearch: true,
  ownerVoiceOnly: false,
  ownerEnrolled: false,
  firstRunCompleted: true,
};

const INITIAL_SMART_HOME: SmartHomeEntity[] = [
  { id: "light.living_room", name: "Luz de la Sala", type: "light", state: true },
  { id: "switch.kitchen_fan", name: "Ventilador de Cocina", type: "fan", state: false },
  { id: "climate.thermostat", name: "Termostato", type: "climate", state: "22°C" },
  { id: "lock.front_door", name: "Cerradura Principal", type: "lock", state: true },
];

const PREFERENCE_SLOTS: Record<string, string[]> = {
  food: ["comida favorita", "comida preferida"],
  drink: ["bebida favorita", "bebida preferida"],
  music: ["musica favorita", "musica preferida"],
  place: ["lugar favorito", "lugar preferido"],
  hobby: ["pasatiempo favorito", "pasatiempo preferido"],
  sport: ["deporte favorito", "deporte preferido"],
  book: ["libro favorito", "libro preferido"],
  movie: ["pelicula favorita", "pelicula preferida"],
  game: ["juego favorito", "juego preferido"],
  programming_language: ["lenguaje de programacion favorito", "lenguaje de programacion preferido"],
  color: ["color favorito", "color preferido"],
  city: ["ciudad favorita", "ciudad preferida"],
  goal: ["meta principal", "objetivo principal"],
  profession: ["profesion", "ocupacion", "trabajo actual"],
  name: ["nombre", "apodo"],
  project: ["proyecto actual", "proyecto principal"],
  study: ["carrera", "area de estudio", "tema de estudio"],
  schedule: ["rutina favorita", "horario preferido"],
  team: ["equipo favorito", "equipo preferido"],
  pet: ["mascota", "mascota favorita"],
};

const PREFERENCE_LABELS: Record<string, string> = {
  food: "comida favorita",
  drink: "bebida favorita",
  music: "música favorita",
  place: "lugar favorito",
  hobby: "pasatiempo favorito",
  sport: "deporte favorito",
  book: "libro favorito",
  movie: "película favorita",
  game: "juego favorito",
  programming_language: "lenguaje de programación favorito",
  color: "color favorito",
  city: "ciudad favorita",
  goal: "meta principal",
  profession: "profesión",
  name: "nombre",
  project: "proyecto actual",
  study: "área de estudio",
  schedule: "horario preferido",
  team: "equipo favorito",
  pet: "mascota",
};

function normalizeKey(input: string): string {
  return input
    .toLowerCase()
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/[^a-z0-9ñ\s]/g, " ")
    .replace(/\s+/g, " ")
    .trim();
}

function normalizedMemoryInput(input: string): string {
  return normalizeKey(input)
    .replace(/^(?:leo|niko|eddy)\s+/, "")
    .replace(/^(?:recorda|recuerda|aprende|guarda) que (?=(?:ahora )?(?:mi |me llamo ))/, "")
    .replace(/^ahora (?=mi )/, "");
}

function canonicalValue(value: string): string {
  return normalizeKey(value).replace(/^(?:la|el|los|las)\s+/, "");
}

function isCertainSingleValue(value: string): boolean {
  const text = normalizeKey(value);
  if (/\b(?:quizas|tal vez|creo que|no se|posiblemente|probablemente|puede ser)\b/.test(text)) return false;
  if (/\b(?:pero|aunque|si fuera|si fuese)\b/.test(text)) return false;
  const allTerms = Object.values(PREFERENCE_SLOTS).flat();
  if (allTerms.some((term) => text.includes(`mi ${term}`))) return false;
  return true;
}

function safePreference(key: string, value: string): { key: string; value: string } | null {
  const normalizedValue = normalizeKey(value);
  if (
    !isCertainSingleValue(value) ||
    value.length < 1 ||
    value.length > 120 ||
    /\d/.test(value) ||
    ["clave", "contrasena", "token", "secreto", "@", "http"].some((bad) => normalizedValue.includes(bad))
  ) {
    return null;
  }
  return { key, value };
}

export class MemoryStore {
  private static memoryPreferencesFallback: Record<string, string> = {};
  private static memoryRulesFallback: Record<string, LearnedRule> = {};
  private static memoryRagFallback: LocalRagDoc[] = [];
  private static memoryTimelineFallback: EpisodicEvent[] = [];

  public static recordEpisode(kind: string, topic: string, summary: string) {
    const events = this.getTimeline();
    events.push({ kind, topic, summary, timestampMs: Date.now() });
    const trimmed = events.slice(-50);
    this.memoryTimelineFallback = trimmed;
    try {
      if (typeof localStorage !== "undefined") {
        localStorage.setItem(STORAGE_KEYS.TIMELINE, JSON.stringify(trimmed));
      }
    } catch {}
  }

  public static getTimeline(): EpisodicEvent[] {
    try {
      if (typeof localStorage !== "undefined") {
        const raw = localStorage.getItem(STORAGE_KEYS.TIMELINE);
        return raw ? JSON.parse(raw) : [];
      }
    } catch {}
    return [...this.memoryTimelineFallback];
  }

  public static getTimelineSummary(): string {
    const events = this.getTimeline();
    if (events.length === 0) {
      return "Todavía no tengo episodios registrados en esta sesión. A medida que ejecutemos protocolos o investiguemos temas, los guardaré en tu línea de tiempo.";
    }
    return (
      "Esto es lo más reciente en tu línea de tiempo:\n" +
      events
        .slice(-4)
        .reverse()
        .map((e, i) => `${i + 1}. [${e.kind}] ${e.topic}: ${e.summary}`)
        .join("\n")
    );
  }

  public static getSettings(): AppSettings {
    try {
      const raw = localStorage.getItem(STORAGE_KEYS.SETTINGS);
      const stored = raw ? JSON.parse(raw) : {};
      if (stored && typeof stored === "object" && "groqApiKey" in stored) {
        delete stored.groqApiKey;
        localStorage.setItem(STORAGE_KEYS.SETTINGS, JSON.stringify(stored));
      }
      const source = stored && typeof stored === "object" && !Array.isArray(stored) ? stored : {};
      const result = { ...DEFAULT_SETTINGS };
      for (const key of ["autoListening", "localFirst", "autoResearch", "firstRunCompleted"] as const) {
        if (typeof source[key] === "boolean") result[key] = source[key];
      }
      for (const [key, min, max] of [["voiceRate", 0.75, 1.5], ["voicePitch", 0.8, 1.3]] as const) {
        if (typeof source[key] === "number" && Number.isFinite(source[key])) result[key] = Math.min(max, Math.max(min, source[key]));
      }
      if (["BALANCED", "CONCISE", "TECHNICAL"].includes(source.personality)) result.personality = source.personality;
      // The web companion has no speaker verification; retire simulated enrollment flags.
      result.ownerEnrolled = false;
      result.ownerVoiceOnly = false;
      return result;
    } catch {
      return { ...DEFAULT_SETTINGS };
    }
  }

  public static saveSettings(settings: Partial<AppSettings>): boolean {
    try {
      const current = this.getSettings();
      const updated = { ...current, ...settings };
      localStorage.setItem(STORAGE_KEYS.SETTINGS, JSON.stringify(updated));
      return true;
    } catch {
      return false;
    }
  }

  public static getPreferences(): Record<string, string> {
    try {
      if (typeof localStorage !== "undefined") {
        const raw = localStorage.getItem(STORAGE_KEYS.PREFERENCES);
        return raw ? JSON.parse(raw) : {};
      }
    } catch {
      // fallback to in-memory store for Node test environments
    }
    return { ...this.memoryPreferencesFallback };
  }

  public static getPreference(key: string): string | null {
    const prefs = this.getPreferences();
    return prefs[key] ?? null;
  }

  public static setPreference(key: string, value: string) {
    const prefs = this.getPreferences();
    prefs[key] = value;
    this.memoryPreferencesFallback = prefs;
    try {
      if (typeof localStorage !== "undefined") {
        localStorage.setItem(STORAGE_KEYS.PREFERENCES, JSON.stringify(prefs));
      }
    } catch {
      // ignore storage errors
    }
  }

  public static deletePreference(key: string) {
    const prefs = this.getPreferences();
    delete prefs[key];
    this.memoryPreferencesFallback = prefs;
    try {
      if (typeof localStorage !== "undefined") {
        localStorage.setItem(STORAGE_KEYS.PREFERENCES, JSON.stringify(prefs));
      }
    } catch {
      // ignore storage errors
    }
  }

  public static getRules(): Record<string, LearnedRule> {
    try {
      if (typeof localStorage !== "undefined") {
        const raw = localStorage.getItem(STORAGE_KEYS.RULES);
        return raw ? JSON.parse(raw) : {};
      }
    } catch {
      // fallback
    }
    return { ...this.memoryRulesFallback };
  }

  public static learnRule(trigger: string, response: string): string {
    const key = normalizeKey(trigger);
    if (key.length < 2 || response.trim().length < 2) {
      return "Necesito una frase activadora y una respuesta claras para aprender la regla.";
    }
    if (["clave", "contrasena", "token", "pin", "secreto"].some((bad) => normalizeKey(response).includes(bad))) {
      return "Por seguridad no guardo contraseñas ni datos sensibles en reglas automáticas.";
    }
    const rules = this.getRules();
    rules[key] = { trigger: trigger.trim(), response: response.trim(), usageCount: 0 };
    this.memoryRulesFallback = rules;
    try {
      if (typeof localStorage !== "undefined") {
        localStorage.setItem(STORAGE_KEYS.RULES, JSON.stringify(rules));
      }
    } catch {}
    return `Aprendido: cuando me digás '${trigger.trim()}', te responderé '${response.trim()}'.`;
  }

  public static forgetRule(trigger: string): string {
    const key = normalizeKey(trigger);
    const rules = this.getRules();
    if (!(key in rules)) return `No tenía guardada una regla para '${trigger.trim()}'.`;
    delete rules[key];
    this.memoryRulesFallback = rules;
    try {
      if (typeof localStorage !== "undefined") {
        localStorage.setItem(STORAGE_KEYS.RULES, JSON.stringify(rules));
      }
    } catch {}
    return `Olvidé la regla personalizada para '${trigger.trim()}'.`;
  }

  public static matchRule(input: string): string | null {
    const key = normalizeKey(input).replace(/^(?:leo|niko|nico)\s+/, "");
    const rules = this.getRules();
    if (rules[key]) {
      rules[key].usageCount += 1;
      this.memoryRulesFallback = rules;
      return rules[key].response;
    }
    return null;
  }

  public static getDocuments(): LocalRagDoc[] {
    try {
      if (typeof localStorage !== "undefined") {
        const raw = localStorage.getItem(STORAGE_KEYS.RAG_DOCS);
        return raw ? JSON.parse(raw) : [];
      }
    } catch {}
    return [...this.memoryRagFallback];
  }

  public static saveDocument(title: string, content: string, modality: "PDF" | "PHOTO_OCR" | "NOTE" = "NOTE"): string {
    const cleanTitle = title.trim();
    const cleanContent = content.trim();
    if (!cleanTitle || cleanContent.length < 4) {
      return "El documento necesita un título y contenido válido para indexarlo.";
    }
    const docs = this.getDocuments().filter((d) => normalizeKey(d.title) !== normalizeKey(cleanTitle));
    docs.push({
      id: `doc_${Date.now()}`,
      title: cleanTitle,
      modality,
      content: cleanContent,
    });
    this.memoryRagFallback = docs;
    try {
      if (typeof localStorage !== "undefined") {
        localStorage.setItem(STORAGE_KEYS.RAG_DOCS, JSON.stringify(docs));
      }
    } catch {}
    return `Indexé '${cleanTitle}' (${modality}) en tu base documental local.`;
  }

  public static searchDocuments(query: string): string | null {
    const docs = this.getDocuments();
    if (docs.length === 0) return null;
    const qTokens = normalizeKey(query)
      .split(" ")
      .filter((t) => t.length >= 2 && !["que", "dice", "mis", "documentos", "notas", "sobre", "acerca", "del", "de", "la", "el", "en", "busca", "buscar"].includes(t));
    if (qTokens.length === 0) return null;

    let bestDoc: LocalRagDoc | null = null;
    let bestSentence = "";
    let bestScore = 0;

    for (const doc of docs) {
      const sentences = doc.content.split(/(?<=[.!?])\s+/);
      for (const sentence of sentences) {
        const sTokens = new Set(normalizeKey(`${doc.title} ${sentence}`).split(" "));
        const overlap = qTokens.filter((t) => sTokens.has(t)).length;
        if (overlap > bestScore) {
          bestScore = overlap;
          bestDoc = doc;
          bestSentence = sentence.trim();
        }
      }
    }

    if (!bestDoc || bestScore === 0) return null;
    return `Según tu documento '${bestDoc.title}' (${bestDoc.modality}): ${bestSentence}`;
  }

  public static getFacts(): string[] {
    const prefEntries = Object.entries(PREFERENCE_LABELS)
      .filter(([key]) => Boolean(this.getPreference(key)))
      .map(([key, label]) => `${label}: ${this.getPreference(key)}`);
    try {
      const raw = localStorage.getItem(STORAGE_KEYS.FACTS);
      const baseFacts: string[] = raw
        ? JSON.parse(raw)
        : [
            "Prefiere respuestas directas en español",
            "Utiliza la voz para tareas cotidianas y comandos rápidos",
          ];
      return [...prefEntries, ...baseFacts];
    } catch {
      return prefEntries;
    }
  }

  public static addFact(fact: string) {
    try {
      const raw = localStorage.getItem(STORAGE_KEYS.FACTS);
      const facts: string[] = raw ? JSON.parse(raw) : [];
      if (!facts.includes(fact)) {
        facts.push(fact);
        localStorage.setItem(STORAGE_KEYS.FACTS, JSON.stringify(facts));
      }
    } catch {
      // ignore storage errors
    }
  }

  public static clearMemory() {
    this.memoryPreferencesFallback = {};
    this.memoryRulesFallback = {};
    this.memoryRagFallback = [];
    this.memoryTimelineFallback = [];
    try {
      if (typeof localStorage !== "undefined") {
        localStorage.removeItem(STORAGE_KEYS.FACTS);
        localStorage.removeItem(STORAGE_KEYS.PREFERENCES);
        localStorage.removeItem(STORAGE_KEYS.RULES);
        localStorage.removeItem(STORAGE_KEYS.RAG_DOCS);
        localStorage.removeItem(STORAGE_KEYS.TIMELINE);
      }
    } catch {
      // ignore storage errors
    }
  }

  public static handlePersonalMemory(input: string): string | null {
    const text = normalizedMemoryInput(input);
    const hasQuestionMark = input.includes("?") || input.includes("¿");

    if (!hasQuestionMark) {
      const introMatch = text.match(/^(?:me llamo|mi nombre es|llamame|podes llamarme)\s+(.+)$/);
      if (introMatch && !text.startsWith("mi nombre es ")) {
        const rawName = input
          .trim()
          .replace(/^(?:(?:leo|niko|eddy)\s*[,.:;-]?\s*)?(?:record[aá]\s+que\s+|recuerda\s+que\s+)?(?:me\s+llamo|ll[aá]mame|pod[eé]s\s+llamarme)\s+/i, "")
          .trim()
          .replace(/[.!]+$/, "");
        const validated = safePreference("name", rawName || introMatch[1]);
        if (validated) {
          this.setPreference("name", validated.value);
          return `Mucho gusto, ${validated.value}. Guardé tu nombre en tu memoria personal.`;
        }
      }
      if (["no hagas bromas sobre mi", "no te burles de mi", "no quiero sarcasmo", "prefiero que no hagas bromas"].includes(text)) {
        this.setPreference("humor_policy", "false");
        return "Guardé que no querés bromas sobre vos ni sarcasmo. Voy a respetarlo.";
      }
      if (["podes hacer bromas sobre mi", "puedes hacer bromas sobre mi", "permito bromas sobre mi"].includes(text)) {
        this.setPreference("humor_policy", "true");
        return "Guardé que permitís bromas sobre vos. Podés cambiarlo cuando querás.";
      }
      if (text === "olvida mi preferencia de humor") {
        this.deletePreference("humor_policy");
        return "Olvidé tu preferencia de humor.";
      }
    }

    if (text === "cual es mi preferencia de humor") {
      const policy = this.getPreference("humor_policy");
      if (policy === "true") return "Permitís bromas sobre vos.";
      if (policy === "false") return "Preferís que no haga bromas sobre vos ni sarcasmo.";
      return "Todavía no guardaste una preferencia de humor.";
    }

    if (["mostra mis preferencias", "muestra mis preferencias", "cuales son mis preferencias", "que preferencias mias tenes guardadas"].includes(text)) {
      const prefs = this.getPreferences();
      const facts = Object.entries(PREFERENCE_LABELS)
        .filter(([key]) => Boolean(prefs[key]))
        .map(([key, label]) => `${label}: ${prefs[key]}`);
      return facts.length === 0
        ? "Todavía no guardaste preferencias personales. Podés enseñarme tu comida, música, pasatiempo o lenguaje de programación favorito."
        : facts.join("\n");
    }

    const recommendationMap: Record<string, string> = {
      "que me recomendas comer": "food",
      "que me recomiendas comer": "food",
      "que comemos hoy": "food",
      "que puedo comer": "food",
      "que me recomendas beber": "drink",
      "que me recomiendas beber": "drink",
      "que puedo beber": "drink",
      "que puedo tomar": "drink",
      "que me recomendas leer": "book",
      "que me recomiendas leer": "book",
      "que puedo leer": "book",
      "que me recomendas escuchar": "music",
      "que me recomiendas escuchar": "music",
    };
    const recSlot = recommendationMap[text];
    if (recSlot) {
      const saved = this.getPreference(recSlot);
      return saved
        ? `Podemos partir de ${saved}: me dijiste que es tu ${PREFERENCE_LABELS[recSlot]}. Si querés algo distinto, decime qué tenés disponible.`
        : `Todavía no conozco tu ${PREFERENCE_LABELS[recSlot]}. ¿Qué te gusta y qué tenés disponible?`;
    }

    if (!hasQuestionMark && !input.includes("@") && input.length <= 300) {
      const correctionMatch = text.match(/^ya no me gusta (.+) ahora prefiero (.+)$/);
      if (correctionMatch) {
        const previous = correctionMatch[1];
        const replacement = input.split(/\bahora\s+prefiero\s+/i)[1]?.trim().replace(/[.!]+$/, "") ?? correctionMatch[2];
        const matching = Object.keys(PREFERENCE_SLOTS).filter((key) => {
          const current = this.getPreference(key);
          return current !== null && canonicalValue(current) === canonicalValue(previous);
        });
        if (matching.length > 1) return "¿Cuál de tus preferencias querés cambiar? Decime su nombre.";
        if (matching.length === 0) return null;
        const key = matching[0];
        const validated = safePreference(key, replacement);
        if (validated) {
          this.setPreference(validated.key, validated.value);
          return `Actualicé tu ${PREFERENCE_LABELS[key]}: ${validated.value}. La anterior ya no figura como tu preferencia actual.`;
        }
        return "No cambié tu preferencia. Decime el nuevo valor sin información sensible ni suposiciones.";
      }

      const retractMatch = text.match(/^ya no me gusta (.+)$/);
      if (retractMatch) {
        const old = retractMatch[1];
        const matching = Object.keys(PREFERENCE_SLOTS).filter((key) => {
          const current = this.getPreference(key);
          return current !== null && canonicalValue(current) === canonicalValue(old);
        });
        if (matching.length > 1) return "¿Cuál de tus preferencias querés quitar? Decime su nombre.";
        if (matching.length === 1) {
          const key = matching[0];
          this.deletePreference(key);
          return `Dejé de considerar ${old} como tu ${PREFERENCE_LABELS[key]}.`;
        }
      }
    }

    for (const [key, terms] of Object.entries(PREFERENCE_SLOTS)) {
      for (const term of terms) {
        const match = text.match(new RegExp(`^mi ${term} es (.+)$`));
        if (match) {
          if (hasQuestionMark) return null;
          const rawValue = input.split(/\s+es\s+/i).slice(1).join(" es ").trim().replace(/[.!]+$/, "");
          if (!isCertainSingleValue(rawValue)) {
            return "No cambié tus preferencias. Decime una preferencia concreta, por ejemplo: mi comida favorita es pizza.";
          }
          const validated = safePreference(key, rawValue);
          if (!validated) return null;
          const existing = this.getPreference(key);
          if (existing && canonicalValue(existing) === canonicalValue(validated.value)) {
            return `Ya recuerdo que tu ${PREFERENCE_LABELS[key]} es ${existing}.`;
          }
          this.setPreference(validated.key, validated.value);
          return `Recordaré que tu ${PREFERENCE_LABELS[key]} es ${validated.value}. Podés corregirla o pedirme que la olvide.`;
        }
      }
    }

    if (!hasQuestionMark) {
      for (const [key, terms] of Object.entries(PREFERENCE_SLOTS)) {
        if (terms.some((term) => text === `olvida mi ${term}` || text === `olvida mi ${term} por favor`)) {
          this.deletePreference(key);
          return `Olvidé tu ${PREFERENCE_LABELS[key]}.`;
        }
      }
    }

    const slotMatch = Object.entries(PREFERENCE_SLOTS).find(([, terms]) => terms.some((t) => text.includes(t)))?.[0] ?? null;
    let queryKey: string | null = null;
    if (["que comida me gusta mas", "que comida prefiero", "que me gusta comer"].includes(text)) queryKey = "food";
    else if (["que bebida me gusta mas", "que bebida prefiero", "que me gusta beber", "que me gusta tomar"].includes(text)) queryKey = "drink";
    else if (["que musica me gusta mas", "que musica prefiero", "que musica escucho"].includes(text)) queryKey = "music";
    else if (["que lenguaje de programacion prefiero", "que lenguaje de programacion me gusta mas", "en que lenguaje programo"].includes(text)) queryKey = "programming_language";
    else if (["que deporte me gusta mas", "que deporte prefiero"].includes(text)) queryKey = "sport";
    else if (["que libro me gusta mas", "que libro prefiero"].includes(text)) queryKey = "book";
    else if (["que pelicula me gusta mas", "que pelicula prefiero"].includes(text)) queryKey = "movie";
    else if (["que juego me gusta mas", "que juego prefiero"].includes(text)) queryKey = "game";
    else if (["que color me gusta mas", "que color prefiero"].includes(text)) queryKey = "color";
    else if (["como me llamo", "cual es mi nombre"].includes(text)) queryKey = "name";
    else if (["cual es mi profesion", "de que trabajo", "a que me dedico"].includes(text)) queryKey = "profession";
    else if (["cual es mi proyecto actual", "en que proyecto estoy", "en que proyecto trabajo"].includes(text)) queryKey = "project";
    else if (["que estudio", "cual es mi carrera"].includes(text)) queryKey = "study";
    else if (["como se llama mi mascota", "cual es mi mascota"].includes(text)) queryKey = "pet";
    else if (text.startsWith("cual es mi ") || text.startsWith("recordas mi ") || text.startsWith("recuerdas mi ")) queryKey = slotMatch;

    if (!queryKey) return null;
    const value = this.getPreference(queryKey);
    return value
      ? `Tu ${PREFERENCE_LABELS[queryKey]} es ${value}.`
      : `Todavía no me dijiste tu ${PREFERENCE_LABELS[queryKey]}.`;
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
    try {
      if (typeof localStorage !== "undefined") {
        localStorage.setItem(STORAGE_KEYS.SMART_HOME, JSON.stringify(entities));
      }
    } catch {}
    return entities;
  }
}
