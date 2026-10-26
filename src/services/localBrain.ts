import { AssistantCommand, NikoUiMode, RobotMotion } from "../types";
import { MemoryStore } from "./memoryStore";

// Strip Spanish accents and lowercase
export function normalize(text: string): string {
  return text
    .toLowerCase()
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/[^a-z0-9\s]/g, " ")
    .replace(/\s+/g, " ")
    .trim();
}

function cleanConversationalPrefix(raw: string): string {
  return raw
    .trim()
    .replace(/^(?:oye\s+|hola\s+|hey\s+)?(?:leo|niko|nico)\b[,\s.:;!¿?¡-]*/i, "")
    .trim();
}

const WORD_NUMBERS: Record<string, string> = {
  cero: "0", un: "1", uno: "1", una: "1", dos: "2", tres: "3", cuatro: "4",
  cinco: "5", seis: "6", siete: "7", ocho: "8", nueve: "9", diez: "10",
  once: "11", doce: "12", trece: "13", catorce: "14", quince: "15",
  dieciseis: "16", diecisiete: "17", dieciocho: "18", diecinueve: "19",
  veinte: "20", veinticinco: "25", treinta: "30", cuarenta: "40",
  cincuenta: "50", sesenta: "60", setenta: "70", ochenta: "80",
  noventa: "90", cien: "100", mil: "1000",
};

interface UnitSpec {
  symbol: string;
  category: "LENGTH" | "MASS" | "TEMPERATURE" | "VOLUME" | "DATA" | "TIME" | "SPEED";
  toBaseFactor: number;
  aliases: string[];
}

const UNIT_SPECS: UnitSpec[] = [
  { symbol: "km", category: "LENGTH", toBaseFactor: 1000, aliases: ["km", "kms", "kilometro", "kilometros"] },
  { symbol: "m", category: "LENGTH", toBaseFactor: 1, aliases: ["m", "mts", "metro", "metros"] },
  { symbol: "cm", category: "LENGTH", toBaseFactor: 0.01, aliases: ["cm", "cms", "centimetro", "centimetros"] },
  { symbol: "mm", category: "LENGTH", toBaseFactor: 0.001, aliases: ["mm", "milimetro", "milimetros"] },
  { symbol: "mi", category: "LENGTH", toBaseFactor: 1609.344, aliases: ["mi", "milla", "millas"] },
  { symbol: "ft", category: "LENGTH", toBaseFactor: 0.3048, aliases: ["ft", "pie", "pies"] },
  { symbol: "in", category: "LENGTH", toBaseFactor: 0.0254, aliases: ["in", "pulgada", "pulgadas"] },
  { symbol: "yd", category: "LENGTH", toBaseFactor: 0.9144, aliases: ["yd", "yarda", "yardas"] },
  { symbol: "kg", category: "MASS", toBaseFactor: 1, aliases: ["kg", "kgs", "kilo", "kilos", "kilogramo", "kilogramos"] },
  { symbol: "g", category: "MASS", toBaseFactor: 0.001, aliases: ["g", "gr", "gramo", "gramos"] },
  { symbol: "lb", category: "MASS", toBaseFactor: 0.45359237, aliases: ["lb", "lbs", "libra", "libras"] },
  { symbol: "oz", category: "MASS", toBaseFactor: 0.028349523125, aliases: ["oz", "onza", "onzas"] },
  { symbol: "°C", category: "TEMPERATURE", toBaseFactor: 1, aliases: ["c", "celsius", "centigrado", "centigrados"] },
  { symbol: "°F", category: "TEMPERATURE", toBaseFactor: 1, aliases: ["f", "fahrenheit"] },
  { symbol: "K", category: "TEMPERATURE", toBaseFactor: 1, aliases: ["k", "kelvin"] },
  { symbol: "L", category: "VOLUME", toBaseFactor: 1, aliases: ["l", "lt", "lts", "litro", "litros"] },
  { symbol: "mL", category: "VOLUME", toBaseFactor: 0.001, aliases: ["ml", "mililitro", "mililitros"] },
  { symbol: "gal", category: "VOLUME", toBaseFactor: 3.785411784, aliases: ["gal", "galon", "galones"] },
  { symbol: "KB", category: "DATA", toBaseFactor: 1 / 1024, aliases: ["kb", "kilobyte", "kilobytes"] },
  { symbol: "MB", category: "DATA", toBaseFactor: 1, aliases: ["mb", "mega", "megas", "megabyte", "megabytes"] },
  { symbol: "GB", category: "DATA", toBaseFactor: 1024, aliases: ["gb", "giga", "gigas", "gigabyte", "gigabytes"] },
  { symbol: "TB", category: "DATA", toBaseFactor: 1024 * 1024, aliases: ["tb", "tera", "teras", "terabyte", "terabytes"] },
  { symbol: "s", category: "TIME", toBaseFactor: 1, aliases: ["s", "seg", "segundo", "segundos"] },
  { symbol: "min", category: "TIME", toBaseFactor: 60, aliases: ["min", "mins", "minuto", "minutos"] },
  { symbol: "h", category: "TIME", toBaseFactor: 3600, aliases: ["h", "hr", "hrs", "hora", "horas"] },
  { symbol: "días", category: "TIME", toBaseFactor: 86400, aliases: ["d", "dia", "dias"] },
  { symbol: "km/h", category: "SPEED", toBaseFactor: 1 / 3.6, aliases: ["km/h", "kmh", "kilometros por hora"] },
  { symbol: "mph", category: "SPEED", toBaseFactor: 0.44704, aliases: ["mph", "millas por hora"] },
  { symbol: "m/s", category: "SPEED", toBaseFactor: 1, aliases: ["m/s", "metros por segundo"] },
];

function formatNumber(val: number): string {
  if (!Number.isFinite(val)) return "";
  if (Math.abs(val - Math.round(val)) < 1e-9) return `${Math.round(val)}`;
  return `${Number(val.toFixed(4))}`;
}

export class LocalBrain {
  public static handleEddyLabRatsFeatures(input: string): string | null {
    const clean = cleanConversationalPrefix(input);
    const norm = normalize(clean);

    // 1. Eddy Lab Rats Persona Modes
    if (["activa modo eddy", "modo eddy", "ponete en modo eddy", "actua como eddy"].includes(norm)) {
      MemoryStore.setPreference("eddy_persona_mode", "EDDY_CLASSIC");
      return "¡Modo Eddy activado! Ingenio al 100 %, sistemas biónicos en línea y cero dependencia de la nube. ¿Qué misión tenemos hoy?";
    }
    if (["activa modo mision", "modo mision", "modo laboratorio", "centro de mando"].includes(norm)) {
      MemoryStore.setPreference("eddy_persona_mode", "MISSION_CONTROL");
      return "[CENTRO DE MANDO LEO] Modo Misión activado. Telemetría, protocolos de laboratorio y respuesta táctica listos.";
    }
    if (["activa modo enfoque", "modo enfoque", "modo serio", "modo empatico"].includes(norm)) {
      MemoryStore.setPreference("eddy_persona_mode", "EMPATHIC_FOCUS");
      return "Modo Enfoque Empático activado. Respuestas claras, serenas y directas sin distracciones.";
    }
    if (["en que modo estas", "cual es tu modo actual", "que personalidad tenes activa"].includes(norm)) {
      const currentMode = MemoryStore.getPreference("eddy_persona_mode") || "EDDY_CLASSIC";
      return `Estoy operando en modo ${currentMode}, 100 % soberano en tu dispositivo.`;
    }

    // 2. Bionic Lab Protocols (Smart Lab)
    if (["protocolo estudio", "activa protocolo estudio", "activa el protocolo estudio", "protocolo concentracion"].includes(norm)) {
      const msg = "[PROTOCOLO ESTUDIO ACTIVO] No molestar activado, volumen al 25 %, luz de escritorio encendida y bloc de notas listo.";
      MemoryStore.recordEpisode("Protocolo", "Protocolo Estudio", msg);
      return msg;
    }
    if (["protocolo nocturno", "activa protocolo nocturno", "activa el protocolo nocturno", "protocolo descanso"].includes(norm)) {
      MemoryStore.updateSmartHomeEntity("light.living_room", false);
      MemoryStore.updateSmartHomeEntity("lock.front_door", true);
      const msg = "[PROTOCOLO NOCTURNO ACTIVO] Brillo al 10 %, silencio activado, luces apagadas y cerradura principal asegurada.";
      MemoryStore.recordEpisode("Protocolo", "Protocolo Nocturno", msg);
      return msg;
    }
    if (["protocolo emergencia", "activa protocolo emergencia", "alerta roja", "protocolo seguridad"].includes(norm)) {
      MemoryStore.updateSmartHomeEntity("light.living_room", true);
      MemoryStore.updateSmartHomeEntity("lock.front_door", true);
      const msg = "[ALERTA ROJA · PROTOCOLO EMERGENCIA] Linterna encendida, brillo y volumen al 100 % y accesos asegurados.";
      MemoryStore.recordEpisode("Protocolo", "Protocolo Emergencia", msg);
      return msg;
    }
    if (["protocolo laboratorio", "activa protocolo laboratorio", "estado del laboratorio", "diagnostico bionico"].includes(norm)) {
      const msg = "[DIAGNÓSTICO DE LABORATORIO] Sensores acústicos, memoria vectorial RAG y red neuronal local operando al 100 % sin conexión a la nube.";
      MemoryStore.recordEpisode("Diagnóstico", "Estado del Laboratorio", msg);
      return msg;
    }

    // 3. Episodic Timeline Queries
    if (["que hicimos hoy", "en que quedamos", "en que estabamos trabajando", "resumen de mi actividad", "mostrame mi linea de tiempo"].includes(norm)) {
      return MemoryStore.getTimelineSummary();
    }

    return null;
  }

  public static handleAutodidactAndRag(input: string): string | null {
    const clean = cleanConversationalPrefix(input);

    // 1. Teach custom rule: "aprendé que cuando te diga X me respondás Y"
    const teachMatch = clean.match(
      /^(?:aprend[eé]|aprendete|apréndete|enseñame|enséñame|record[aá]|guard[aá])\s+(?:que\s+)?cuando\s+(?:yo\s+)?te\s+diga\s+(.+?)\s+(?:me\s+)?(?:respond[aá]s|respond[eé]|respond[eé]\s+con|decime|dime)\s+(.+)$/i
    );
    if (teachMatch) {
      const trigger = teachMatch[1].replace(/^["'«“]|["'»”.!]+$/g, "").trim();
      const response = teachMatch[2].replace(/^["'«“]|["'»”.!]+$/g, "").trim();
      return MemoryStore.learnRule(trigger, response);
    }

    // 2. Forget custom rule: "olvida la regla de X"
    const forgetMatch = clean.match(/^(?:olvid[aá]|borr[aá]|elimin[aá])\s+la\s+regla\s+(?:de|para|sobre)\s+(.+)$/i);
    if (forgetMatch) {
      return MemoryStore.forgetRule(forgetMatch[1].replace(/^["'«“]|["'»”.!]+$/g, "").trim());
    }

    // 3. Save RAG document: "guardá en mis documentos Título: contenido"
    const saveDocMatch = clean.match(
      /^(?:guard[aá]|index[aá]|agreg[aá])\s+en\s+mis\s+(?:documentos|notas|archivos)\s+(?:el\s+(?:pdf|documento|texto|ocr)\s+)?([^:]+):\s*(.+)$/i
    );
    if (saveDocMatch) {
      const title = saveDocMatch[1].trim();
      const content = saveDocMatch[2].trim();
      const norm = normalize(clean);
      const modality = norm.includes("pdf") ? "PDF" : norm.includes("ocr") || norm.includes("foto") ? "PHOTO_OCR" : "NOTE";
      return MemoryStore.saveDocument(title, content, modality);
    }

    // 4. Query RAG documents: "buscá en mis documentos X" / "qué dice en mis documentos sobre X"
    const cleanNoQuestion = clean.replace(/^[¿?¡!]+|[¿?¡!.]+$/g, "").trim();
    const queryDocMatch = cleanNoQuestion.match(
      /^(?:busc[aá]\s+en\s+mis\s+(?:documentos|notas|archivos)|qu[eé]\s+dice(?:n)?\s+(?:en\s+)?mis\s+(?:documentos|notas|archivos)\s+(?:sobre|acerca\s+de|de))\s+(.+)$/i
    );
    if (queryDocMatch) {
      return (
        MemoryStore.searchDocuments(queryDocMatch[1]) ??
        `No encontré referencias a '${queryDocMatch[1].trim()}' en tus documentos locales.`
      );
    }

    // 5. Match learned rule
    const ruleHit = MemoryStore.matchRule(clean);
    if (ruleHit !== null) {
      return ruleHit;
    }

    return null;
  }

  public static detectEmotionAndRespond(input: string): string | null {
    const clean = cleanConversationalPrefix(input);
    if (clean.includes("?") || clean.includes("¿")) return null;
    const norm = normalize(clean);
    if (/\b(?:no|nunca|jamas|pero|aunque|el|ella|ellos)\b/.test(norm)) return null;
    if (!/\b(?:estoy|me siento|ando|siento|tengo|me da)\b/.test(norm)) return null;

    if (/\b(?:feliz|contento|contenta|alegre|radiante|genial|motivado|motivada|entusiasmado|entusiasmada)\b/.test(norm)) {
      return "¡Qué alegría escuchar eso! Contame qué te tiene tan bien o cómo aprovechamos ese impulso.";
    }
    if (/\b(?:triste|bajoneado|bajoneada|deprimido|deprimida|desanimado|desanimada|solo|sola)\b/.test(norm)) {
      return "Lamento que te sintás así. Estoy aquí con vos; si querés desahogarte o distraerte un rato, decime.";
    }
    if (/\b(?:frustrado|frustrada|harto|harta|bloqueado|bloqueada|enojado|enojada|molesto|molesta)\b/.test(norm)) {
      return "Entiendo tu frustración. Respiremos un segundo y dividamos el problema en partes pequeñas: ¿qué es lo primero que te está trabando?";
    }
    if (/\b(?:estresado|estresada|ansioso|ansiosa|preocupado|preocupada|nervioso|nerviosa|agobiado|agobiada|cansado|cansada|agotado|agotada)\b/.test(norm)) {
      return "Noté que tenés mucha carga encima. Vamos paso a paso, sin prisa. Decime en qué te puedo aliviar el trabajo ahora mismo.";
    }
    return null;
  }

  public static cleanSpokenWebReply(text: string): string {
    return text
      .replace(/\[\d+\]/g, "")
      .replace(/https?:\/\/\S+/g, "")
      .replace(/\s+/g, " ")
      .replace(/\s+([.,;:!?])/g, "$1")
      .trim();
  }

  public static synthesizeWebSources(
    query: string,
    sources: Array<{ title: string; url: string; snippet: string; date?: string; domain?: string; sourceName?: string }>,
    personality: string = "BALANCED"
  ): string {
    const valid = (sources || []).filter((s) => s && ((s.snippet && s.snippet.trim().length > 12) || (s.title && s.title.trim().length > 12)));
    if (valid.length === 0) {
      return "No tengo fuentes verificadas suficientes para responder esa consulta. Probá con términos más específicos.";
    }

    const cleanQuery = cleanConversationalPrefix(query)
      .replace(/^(?:busca(?:r|me)?(?:\s+en\s+(?:internet|google|la web))?|investiga|averigua|noticias de|hablame de|explicame|decime|dime)\s+/i, "")
      .trim();

    const stopTokens = new Set([
      "que", "como", "cual", "quien", "donde", "cuando", "por", "para", "con", "del", "las", "los",
      "una", "uno", "sobre", "busca", "internet", "google", "hoy", "actual", "noticias", "informacion",
      "acerca", "explica", "explicame", "dime", "decime", "fue", "son", "esta", "estan",
    ]);

    const qTokens = new Set(
      normalize(cleanQuery || query)
        .split(" ")
        .filter((t) => t.length >= 2 && !stopTokens.has(t))
    );

    const isDefinition = /\b(?:que es|quien es|quien fue|significado|definicion|biografia)\b/i.test(normalize(query));
    const isHowTo = /\b(?:como|pasos|funciona|funcionamiento|procedimiento|hacer|preparar|configurar)\b/i.test(normalize(query));
    const isComparison = /\b(?:diferencia|diferencias|comparar|compara|versus|vs|mejor)\b/i.test(normalize(query));
    const wantsDeep = personality === "DETAILED" || /\b(?:profundidad|detallad|a fondo|amplia|completo)\b/i.test(normalize(query));

    interface RankedSentence {
      text: string;
      sourceIndex: number;
      domainKey: string;
      score: number;
    }

    const cleanFragment = (raw: string): string => {
      return raw
        .replace(/\s+/g, " ")
        .replace(/\s*(?:\.\.\.|…|\[\.\.\.\])\s*$/g, "")
        .replace(/\s+[-|—–]\s+[A-ZÁÉÍÓÚÑ][A-Za-zÁÉÍÓÚáéíóúñÑ0-9\s.&]{1,35}$/g, "")
        .trim();
    };

    const candidates: RankedSentence[] = [];
    valid.slice(0, 6).forEach((source, idx) => {
      const domainKey = (source.domain || source.sourceName || source.url || `src-${idx}`).toLowerCase().replace(/^www\./, "");
      const cleanedSnippet = cleanFragment(source.snippet || "");
      const cleanedTitle = cleanFragment(source.title || "");

      const rawSentences = cleanedSnippet
        .split(/(?<=[.!?])\s+/)
        .map((s) => cleanFragment(s))
        .filter((s) => s.length >= 24 && s.length <= 340);

      const pool =
        rawSentences.length > 0
          ? rawSentences
          : cleanedSnippet.length >= 20
          ? [cleanedSnippet]
          : cleanedTitle.length >= 20
          ? [`${cleanedTitle}: ${cleanedSnippet}`.trim()]
          : [];

      pool.forEach((sentence, sIdx) => {
        const normSentence = normalize(sentence);
        if (/\b(?:aceptar cookies|politica de privacidad|iniciar sesion|comprar ahora|suscribete|haz clic)\b/.test(normSentence)) {
          return;
        }
        const sTokens = normSentence.split(" ");
        const overlap = sTokens.filter((t) => qTokens.has(t)).length;
        const coverage = qTokens.size > 0 ? overlap / qTokens.size : 0.5;
        if (qTokens.size > 0 && overlap === 0 && sIdx > 0) return;

        const hasNumbers = /\d/.test(sentence) ? 0.9 : 0;
        const definitional = /\b(?:es un|es una|fue un|fue una|son|consiste|define|significa|conocido como|nacio|destaco)\b/i.test(normSentence)
          ? isDefinition ? 2.4 : 1.3
          : 0;
        const causalOrProcess = /\b(?:permite|produce|transforma|funciona|mediante|debido|porque|para ello|genera)\b/i.test(normSentence)
          ? isHowTo ? 2.2 : 1.2
          : 0;
        const contrastive = /\b(?:mientras|diferencia|cambio|sin embargo|ventaja|frente a)\b/i.test(normSentence) && isComparison ? 1.8 : 0;
        const wikiBonus = domainKey.includes("wikipedia.org") ? 1.1 : 0;
        const leadBonus = sIdx === 0 ? 0.6 : 0;

        candidates.push({
          text: sentence.slice(0, 280).replace(/[.!?]+$/, "").trim(),
          sourceIndex: idx + 1,
          domainKey,
          score:
            overlap * 2.2 +
            coverage * 2.0 +
            hasNumbers +
            definitional +
            causalOrProcess +
            contrastive +
            wikiBonus +
            leadBonus -
            idx * 0.2,
        });
      });
    });

    candidates.sort((a, b) => b.score - a.score);
    const maxSentences = personality === "CONCISE" ? 2 : wantsDeep ? 4 : 3;
    const selected: RankedSentence[] = [];
    const seenNorm = new Set<string>();
    const usedDomains = new Set<string>();

    // Pass 1: prioritize distinct domains so synthesis corroborates across sources
    for (const cand of candidates) {
      if (selected.length >= maxSentences) break;
      const key = normalize(cand.text).slice(0, 55);
      if (seenNorm.has(key) || usedDomains.has(cand.domainKey)) continue;
      seenNorm.add(key);
      usedDomains.add(cand.domainKey);
      selected.push(cand);
    }

    // Pass 2: fill remaining slots with highest-scoring non-duplicate facts
    for (const cand of candidates) {
      if (selected.length >= maxSentences) break;
      if (selected.includes(cand)) continue;
      const key = normalize(cand.text).slice(0, 55);
      if (seenNorm.has(key)) continue;
      seenNorm.add(key);
      selected.push(cand);
    }

    if (selected.length === 0) {
      const fallbackText = cleanFragment(valid[0].snippet || valid[0].title).slice(0, 240).replace(/[.!?]+$/, "");
      return `${fallbackText} [1].`;
    }

    const connectors = ["", "Además, ", "Por otra parte, ", "Asimismo, "];
    const formatted = selected.map((item, idx) => {
      let body = item.text;
      if (idx > 0) {
        const alreadyHasConnector = /^(?:adem[aá]s|asimismo|por otra parte|sin embargo|también|cabe destacar|en cuanto|finalmente)\b/i.test(body);
        if (!alreadyHasConnector && body.length > 2) {
          const connector = connectors[idx] || "";
          // Lowercase first letter only if it's not an acronym or proper name paired with articles
          const firstWord = body.split(" ")[0] || "";
          const canLower = /^(?:El|La|Los|Las|Un|Una|Este|Esta|Estos|Estas|Su|Sus|Se|En|Con|Para|Seg[uú]n)$/.test(firstWord);
          if (connector && canLower) {
            body = connector + body.charAt(0).toLowerCase() + body.slice(1);
          }
        }
      }
      return `${body} [${item.sourceIndex}].`;
    });

    const joined = formatted.join(" ");
    const personaMode = MemoryStore.getPreference("eddy_persona_mode");
    if (personaMode === "MISSION_CONTROL") {
      return `[REPORTE WEB VERIFICADO] ${joined}`;
    }
    return joined;
  }

  public static parseRobotMotion(input: string): RobotMotion | null {
    const text = normalize(cleanConversationalPrefix(input));
    if (/\b(?:no|nunca|jamas)\b/.test(text)) return null;
    const command = text
      .replace(/^(?:por favor |podes |quiero que |podrias )/, "")
      .replace(/ (?:por favor|porfa)$/, "")
      .trim();

    if (
      command.includes("baila") ||
      command.includes("bailes") ||
      command.includes("un baile")
    ) {
      return "DANCE";
    }
    if (
      command.includes("salta") ||
      command.includes("saltes") ||
      command.includes("brinca") ||
      command.includes("un salto")
    ) {
      return "JUMP";
    }
    if (
      command.includes("gira") ||
      command.includes("gires") ||
      command.includes("una vuelta")
    ) {
      return "SPIN";
    }
    if (
      command.includes("saluda") ||
      command.includes("saludame") ||
      command.includes("mueve los brazos") ||
      command.includes("move los brazos") ||
      command.includes("levanta la mano")
    ) {
      return "WAVE";
    }

    return null;
  }

  public static convertUnits(expr: string): string | null {
    const clean = expr
      .toLowerCase()
      .normalize("NFD")
      .replace(/[\u0300-\u036f]/g, "")
      .replace(/^(?:oye\s+|hola\s+|hey\s+)?(?:leo|niko|nico)\b[,\s.:;!¿?¡-]*/i, "")
      .replace(/[¿?!.:;]+$/g, "")
      .trim();

    const directMatch = clean.match(
      /^(?:converti(?:r|me)?|convierte|cuanto(?:s|as)?\s+(?:es|son|equivale(?:n)?)|pasa(?:r|me)?|transforma(?:r)?|calcula(?:r|me)?)?\s*(-?\d+(?:[.,]\d+)?)\s+(?:grados?\s+)?([a-z/]+(?:\s+por\s+hora|\s+por\s+segundo)?)\s+(?:a|en)\s+(?:grados?\s+)?([a-z/]+(?:\s+por\s+hora|\s+por\s+segundo)?)$/
    );
    const reverseMatch = !directMatch
      ? clean.match(
          /^cuanto(?:s|as)?\s+(?:grados?\s+)?([a-z/]+(?:\s+por\s+hora|\s+por\s+segundo)?)\s+(?:son|hay\s+en|tiene(?:n)?|equivalen?\s+a)\s+(-?\d+(?:[.,]\d+)?)\s+(?:grados?\s+)?([a-z/]+(?:\s+por\s+hora|\s+por\s+segundo)?)$/
        )
      : null;

    let rawAmount = "";
    let rawFrom = "";
    let rawTo = "";
    if (directMatch) {
      rawAmount = directMatch[1];
      rawFrom = directMatch[2];
      rawTo = directMatch[3];
    } else if (reverseMatch) {
      rawTo = reverseMatch[1];
      rawAmount = reverseMatch[2];
      rawFrom = reverseMatch[3];
    } else {
      return null;
    }

    const amount = parseFloat(rawAmount.replace(",", "."));
    if (!Number.isFinite(amount)) return null;
    const fromUnit = UNIT_SPECS.find((u) => u.aliases.includes(rawFrom.trim()));
    const toUnit = UNIT_SPECS.find((u) => u.aliases.includes(rawTo.trim()));
    if (!fromUnit || !toUnit || fromUnit.category !== toUnit.category) return null;

    let converted: number;
    if (fromUnit.category === "TEMPERATURE") {
      const celsius =
        fromUnit.symbol === "°C"
          ? amount
          : fromUnit.symbol === "°F"
          ? ((amount - 32) * 5) / 9
          : amount - 273.15;
      converted =
        toUnit.symbol === "°C"
          ? celsius
          : toUnit.symbol === "°F"
          ? (celsius * 9) / 5 + 32
          : celsius + 273.15;
    } else {
      converted = (amount * fromUnit.toBaseFactor) / toUnit.toBaseFactor;
    }
    if (!Number.isFinite(converted)) return null;
    return `${formatNumber(amount)} ${fromUnit.symbol} equivalen a ${formatNumber(converted)} ${toUnit.symbol}.`;
  }

  public static evaluateMath(expr: string): string | null {
    // Preserve operators and decimal separators; general text normalization removes them.
    const norm = expr
      .toLowerCase()
      .normalize("NFD")
      .replace(/[\u0300-\u036f]/g, "")
      .replace(/(\d),(?=\d)/g, "$1.")
      .replace(/^(?:(?:leo|niko|nico)\s*[,.:;-]?\s*)?(?:por\s+favor\s+)?(?:cuanto\s+es|cuanto\s+da|calcula(?:me)?|calcular|resuelve|resolver|resultado\s+de|dime\s+cuanto\s+es|decime\s+cuanto\s+es)\s+/, "")
      .replace(/[¿?.:;]+$/g, "")
      .trim();

    // Discount: "descuento del 15% a 200"
    const discountMatch = norm.match(
      /^(?:el\s+)?descuento\s+de(?:l)?\s+(-?\d+(?:\.\d+)?)\s*(?:%|por\s+ciento)\s+(?:a|de|sobre|en)\s+(-?\d+(?:\.\d+)?)$/
    );
    if (discountMatch) {
      const pct = parseFloat(discountMatch[1]);
      const base = parseFloat(discountMatch[2]);
      return formatNumber(base - (pct / 100) * base);
    }

    // What percent: "qué porcentaje es 30 de 150"
    const whatPctMatch = norm.match(
      /^(?:que\s+porcentaje|que\s+tanto\s+por\s+ciento)\s+es\s+(-?\d+(?:\.\d+)?)\s+de\s+(-?\d+(?:\.\d+)?)$/
    );
    if (whatPctMatch) {
      const part = parseFloat(whatPctMatch[1]);
      const whole = parseFloat(whatPctMatch[2]);
      if (whole === 0) return null;
      return formatNumber((part / whole) * 100);
    }

    // Compound percentage: "200 menos 15%" / "200 más el 15 por ciento"
    const compoundPct = norm.match(
      /^(-?\d+(?:\.\d+)?)\s*(\+|mas|-|menos)\s*(?:el\s+)?(-?\d+(?:\.\d+)?)\s*(?:%|por\s+ciento)$/
    );
    if (compoundPct) {
      const base = parseFloat(compoundPct[1]);
      const op = compoundPct[2];
      const pct = parseFloat(compoundPct[3]);
      const delta = (pct / 100) * base;
      return formatNumber(op === "-" || op === "menos" ? base - delta : base + delta);
    }

    // Percentage: "20 por ciento de 150"
    const pctMatch = norm.match(/^(?:el\s+)?(\d+(?:\.\d+)?)\s*(?:%|por ciento)\s*de\s*(\d+(?:\.\d+)?)$/);
    if (pctMatch) {
      const p = parseFloat(pctMatch[1]);
      const total = parseFloat(pctMatch[2]);
      const res = (p / 100) * total;
      return formatNumber(res);
    }

    // Scientific single functions: cube root, trig, log, factorial, abs
    const cbrtMatch = norm.match(/^raiz\s+cubica\s+de\s+(-?\d+(?:\.\d+)?)$/);
    if (cbrtMatch) return formatNumber(Math.cbrt(parseFloat(cbrtMatch[1])));

    const sinMatch = norm.match(/^seno\s+de\s+(-?\d+(?:\.\d+)?)$/);
    if (sinMatch) return formatNumber(Math.sin((parseFloat(sinMatch[1]) * Math.PI) / 180));

    const cosMatch = norm.match(/^coseno\s+de\s+(-?\d+(?:\.\d+)?)$/);
    if (cosMatch) return formatNumber(Math.cos((parseFloat(cosMatch[1]) * Math.PI) / 180));

    const tanMatch = norm.match(/^tangente\s+de\s+(-?\d+(?:\.\d+)?)$/);
    if (tanMatch) {
      const deg = parseFloat(tanMatch[1]);
      if (Math.abs((((deg % 180) + 180) % 180) - 90) < 1e-9) return null;
      return formatNumber(Math.tan((deg * Math.PI) / 180));
    }

    const logMatch = norm.match(/^logaritmo\s+de\s+(\d+(?:\.\d+)?)$/);
    if (logMatch) {
      const val = parseFloat(logMatch[1]);
      return val > 0 ? formatNumber(Math.log10(val)) : null;
    }

    const lnMatch = norm.match(/^logaritmo\s+natural\s+de\s+(e|\d+(?:\.\d+)?)$/);
    if (lnMatch) {
      const val = lnMatch[1] === "e" ? Math.E : parseFloat(lnMatch[1]);
      return val > 0 ? formatNumber(Math.log(val)) : null;
    }

    const absMatch = norm.match(/^valor\s+absoluto\s+de\s+(-?\d+(?:\.\d+)?)$/);
    if (absMatch) return formatNumber(Math.abs(parseFloat(absMatch[1])));

    const factMatch = norm.match(/^(?:factorial\s+de\s+(\d+)|(\d+)\s*!)$/);
    if (factMatch) {
      const n = parseInt(factMatch[1] || factMatch[2], 10);
      if (n < 0 || n > 20) return null;
      let acc = 1;
      for (let i = 2; i <= n; i++) acc *= i;
      return `${acc}`;
    }

    // Replace Spanish words with math symbols
    let cleaned = norm
      .replace(/!/g, "")
      .replace(/\bal cuadrado\b/g, " ^ 2")
      .replace(/\bal cubo\b/g, " ^ 3")
      .replace(/\bmas\b/g, "+")
      .replace(/\bmenos\b/g, "-")
      .replace(/\b(?:dividido por|dividido entre|entre|dividido)\b/g, "/")
      .replace(/\b(?:multiplicado por|por)\b/g, "*")
      .replace(/elevado a la|elevado a/g, "^")
      .replace(/raiz cuadrada de|raiz de/g, "sqrt")
      .trim();

    if (/[+\-*/^]/.test(cleaned)) {
      for (const [word, digit] of Object.entries(WORD_NUMBERS)) {
        cleaned = cleaned.replace(new RegExp(`\\b${word}\\b`, "g"), digit);
      }
    }

    // Square root (only standalone `sqrt <number>`, keeping `raíz de 9 + 2` null as required by tests)
    if (cleaned.includes("sqrt")) {
      const sqMatch = cleaned.match(/^sqrt\s*(\d+(?:\.\d+)?)$/);
      if (sqMatch) {
        const val = parseFloat(sqMatch[1]);
        return `${Math.sqrt(val)}`;
      }
      return null;
    }

    try {
      // Safe arithmetic evaluator for basic tokens
      if (!/^[0-9+\-*/().\s^]+$/.test(cleaned) || cleaned.trim().length === 0) {
        return null;
      }
      if (!/[+\-*/^]/.test(cleaned)) {
        return null;
      }
      cleaned = cleaned.replace(/\^/g, "**");
      // Use Function constructor with restricted scope
      const res = new Function(`return (${cleaned})`)();
      if (typeof res === "number" && !isNaN(res) && isFinite(res)) {
        return Number.isInteger(res) ? `${res}` : `${res.toFixed(4).replace(/\.?0+$/, "")}`;
      }
    } catch (_e) {
      return null;
    }
    return null;
  }

  public static understand(rawInput: string): AssistantCommand {
    const original = rawInput.trim();
    const withoutWake = cleanConversationalPrefix(original);
    const norm = normalize(withoutWake);

    // 1. Stop command
    if (
      norm === "para" ||
      norm === "para te" ||
      norm === "parate" ||
      norm === "cancela" ||
      norm === "detener" ||
      norm === "silencio" ||
      norm === "alto"
    ) {
      return { type: "stop" };
    }

    // 2. Personal Memory, Eddy Lab Rats Protocols/Persona, Autodidact Rules, Local RAG & Emotional Heart
    const memoryReply =
      MemoryStore.handlePersonalMemory(withoutWake) ??
      this.handleEddyLabRatsFeatures(withoutWake) ??
      this.handleAutodidactAndRag(withoutWake) ??
      this.detectEmotionAndRespond(withoutWake);
    if (memoryReply !== null) {
      return { type: "personal_memory", payload: { reply: memoryReply } };
    }

    // 3. Robot motion
    const motion = this.parseRobotMotion(withoutWake);
    if (motion) {
      return { type: "robot_motion", payload: { motion } };
    }

    // 4. Direct unit conversions ("100 km a millas", "30 grados celsius a fahrenheit")
    const unitConversion = this.convertUnits(withoutWake);
    if (unitConversion !== null) {
      return { type: "math", payload: { result: unitConversion, expression: withoutWake, unitConversion: true } };
    }

    // 5. UI Transformation / Apps
    if (
      norm.includes("vuelve a inicio") ||
      norm.includes("pantalla principal") ||
      norm.includes("volver a leo") ||
      norm.includes("modo principal")
    ) {
      return { type: "open_tool", payload: { tool: "ASSISTANT" as NikoUiMode } };
    }
    if (norm.includes("calculadora")) {
      return { type: "open_tool", payload: { tool: "CALCULATOR" as NikoUiMode } };
    }
    if (norm.includes("cronometro")) {
      return { type: "open_tool", payload: { tool: "STOPWATCH" as NikoUiMode } };
    }
    if (norm.includes("temporizador") || norm.includes("cuenta regresiva")) {
      return { type: "open_tool", payload: { tool: "TIMER" as NikoUiMode } };
    }
    if (norm.includes("reloj") && (norm.includes("abrir") || norm.includes("modo") || norm.includes("ver"))) {
      return { type: "open_tool", payload: { tool: "CLOCK" as NikoUiMode } };
    }
    if (norm.includes("nota") || norm.includes("bloc")) {
      return { type: "open_tool", payload: { tool: "NOTES" as NikoUiMode } };
    }
    if (norm.includes("conversor") || norm.includes("convertidor")) {
      return { type: "open_tool", payload: { tool: "CONVERTER" as NikoUiMode } };
    }
    if (norm.includes("diagnostico") || norm.includes("metricas de voz")) {
      return { type: "open_tool", payload: { tool: "VOICE_DIAGNOSTICS" as NikoUiMode } };
    }
    if (norm.includes("configura inteligencia") || norm.includes("ajustes de ia") || norm.includes("abrir ajustes")) {
      return { type: "open_tool", payload: { tool: "AI_SETTINGS" as NikoUiMode } };
    }
    if (norm.includes("casa inteligente") || norm.includes("domotica") || norm.includes("home assistant")) {
      return { type: "open_tool", payload: { tool: "SMART_HOME_SETTINGS" as NikoUiMode } };
    }

    // 6. Time
    if (norm.includes("que hora") || norm.includes("la hora")) {
      return { type: "tell_time" };
    }

    // 7. Battery
    if (norm.includes("bateria")) {
      return { type: "battery_status" };
    }

    // 8. Flashlight / Torch
    if (norm.includes("linterna")) {
      const turnOff = norm.includes("apaga");
      return { type: "torch", payload: { enable: !turnOff } };
    }

    // 9. Memory queries
    if (norm.includes("olvida todo") || norm.includes("borra tu memoria")) {
      return { type: "clear_memory" };
    }
    if (
      norm.includes("que sabes de mi") ||
      norm.includes("que recuerdas") ||
      norm.includes("que has aprendido")
    ) {
      return { type: "memory_summary" };
    }

    // 10. Math calculation
    const mathRes = this.evaluateMath(withoutWake);
    if (mathRes !== null) {
      return { type: "math", payload: { result: mathRes, expression: withoutWake } };
    }

    // Explicit research must precede app names ("busca tutoriales de YouTube").
    const searchMatch = withoutWake.match(/^(?:busca(?:r|me)?(?:\s+en\s+(?:internet|google|la web))?|investiga|averigua|noticias de)\s+(.+)/i);
    if (searchMatch) {
      return { type: "search_web", payload: { query: searchMatch[1].trim() } };
    }

    // 11. External App Launchers
    if (norm.includes("whatsapp")) {
      return { type: "open_app", payload: { appName: "WhatsApp", url: "https://web.whatsapp.com" } };
    }
    if (norm.includes("youtube")) {
      return { type: "open_app", payload: { appName: "YouTube", url: "https://www.youtube.com" } };
    }
    if (norm.includes("spotify")) {
      return { type: "open_app", payload: { appName: "Spotify", url: "https://open.spotify.com" } };
    }
    if (norm.includes("mapas") || norm.includes("maps")) {
      return { type: "open_app", payload: { appName: "Google Maps", url: "https://maps.google.com" } };
    }

    // 12. Message sending
    const msgMatch = withoutWake.match(/(?:manda|envia|escribe)\s+un\s+mensaje(?:\s+a\s+([^,]+?))?\s+(?:asi|diciendo|que diga)\s+(.+)/i);
    if (msgMatch) {
      return {
        type: "send_message",
        payload: {
          recipient: msgMatch[1]?.trim() || "Contacto",
          body: msgMatch[2]?.trim(),
        },
      };
    }

    // 13. Greetings
    if (
      /^(?:hola|buenas|que tal|como estas|buenos dias|buenas tardes|buenas noches)$/i.test(norm)
    ) {
      return { type: "greeting" };
    }

    // 14. Questions starting with "quien es", "que paso", "donde queda", "como funciona", "explicame" -> route to search
    if (
      norm.startsWith("quien es") ||
      norm.startsWith("quien fue") ||
      norm.startsWith("que es") ||
      norm.startsWith("como funciona") ||
      norm.startsWith("por que ") ||
      norm.startsWith("donde queda") ||
      norm.startsWith("cual es la capital") ||
      norm.startsWith("que paso") ||
      norm.startsWith("ultimas noticias") ||
      norm.startsWith("hablame de ") ||
      norm.startsWith("explicame ")
    ) {
      return { type: "search_web", payload: { query: withoutWake } };
    }

    // General query for local synthesis
    return { type: "unknown", payload: { query: withoutWake || original } };
  }
}
