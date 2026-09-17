import { AssistantCommand, NikoUiMode, RobotMotion } from "../types";

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
    .replace(/^(?:oye\s+|hola\s+|hey\s+)?(?:leo|niko|nico)[,\s.:;!¿?¡-]*/i, "")
    .trim();
}

export class LocalBrain {
  public static parseRobotMotion(input: string): RobotMotion | null {
    const text = normalize(cleanConversationalPrefix(input));
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

  public static evaluateMath(expr: string): string | null {
    const norm = normalize(expr);

    // Percentage: "20 por ciento de 150"
    const pctMatch = norm.match(/(\d+(?:\.\d+)?)\s*(?:%|por ciento)\s*de\s*(\d+(?:\.\d+)?)/);
    if (pctMatch) {
      const p = parseFloat(pctMatch[1]);
      const total = parseFloat(pctMatch[2]);
      const res = (p / 100) * total;
      return `${res}`;
    }

    // Replace Spanish words with math symbols
    let cleaned = norm
      .replace(/cuanto es/g, "")
      .replace(/calcula/g, "")
      .replace(/mas/g, "+")
      .replace(/menos/g, "-")
      .replace(/por|multiplicado por/g, "*")
      .replace(/entre|dividido por|dividido/g, "/")
      .replace(/elevado a la|elevado a/g, "^")
      .replace(/raiz de|raiz cuadrada de/g, "sqrt")
      .replace(/[^0-9+\-*/().^sqrt\s]/g, "");

    // Square root
    if (cleaned.includes("sqrt")) {
      const sqMatch = cleaned.match(/sqrt\s*(\d+(?:\.\d+)?)/);
      if (sqMatch) {
        const val = parseFloat(sqMatch[1]);
        return `${Math.sqrt(val)}`;
      }
    }

    try {
      // Safe arithmetic evaluator for basic tokens
      if (!/^[0-9+\-*/().\s^]+$/.test(cleaned) || cleaned.trim().length === 0) {
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

    // 2. Robot motion
    const motion = this.parseRobotMotion(withoutWake);
    if (motion) {
      return { type: "robot_motion", payload: { motion } };
    }

    // 3. UI Transformation / Apps
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

    // 4. Time
    if (norm.includes("que hora") || norm.includes("la hora")) {
      return { type: "tell_time" };
    }

    // 5. Battery
    if (norm.includes("bateria")) {
      return { type: "battery_status" };
    }

    // 6. Flashlight / Torch
    if (norm.includes("linterna")) {
      const turnOff = norm.includes("apaga");
      return { type: "torch", payload: { enable: !turnOff } };
    }

    // 7. Memory queries
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

    // 8. Math calculation
    if (
      norm.startsWith("cuanto es") ||
      norm.startsWith("calcula") ||
      norm.includes("por ciento de") ||
      /^\d+\s*[\+\-\*\/]\s*\d+/.test(norm)
    ) {
      const res = this.evaluateMath(withoutWake);
      if (res !== null) {
        return { type: "math", payload: { result: res, expression: withoutWake } };
      }
    }

    // 9. External App Launchers
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

    // 10. Message sending
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

    // 11. Explicit Web Search
    const searchMatch = withoutWake.match(/^(?:busca(?:me)?|investiga|averigua|busca en internet|noticias de)\s+(.+)/i);
    if (searchMatch) {
      return { type: "search_web", payload: { query: searchMatch[1].trim() } };
    }

    // 12. Greetings
    if (
      /^(?:hola|buenas|que tal|como estas|buenos dias|buenas tardes|buenas noches)$/i.test(norm)
    ) {
      return { type: "greeting" };
    }

    // 13. Questions starting with "quien es", "que paso", "donde queda" -> route to search
    if (
      norm.startsWith("quien es") ||
      norm.startsWith("que es") ||
      norm.startsWith("donde queda") ||
      norm.startsWith("cual es la capital")
    ) {
      return { type: "search_web", payload: { query: withoutWake } };
    }

    // General query for LLM/Synthesis
    return { type: "unknown", payload: { query: withoutWake || original } };
  }
}
