import express, { Request, Response } from "express";
import path from "path";
import { createServer as createViteServer } from "vite";

import { searchWeb, suggestions, validateQuery } from "./server/webSearch";
import { LocalBrain } from "./src/services/localBrain";
import { MemoryStore } from "./src/services/memoryStore";

const app = express();
const PORT = 3000;

app.use(express.json());

// Health check endpoint — 100% Sovereign Local Engine (Zero External LLM Dependency)
app.get("/api/health", (_req: Request, res: Response) => {
  res.json({
    status: "ok",
    app: "LEO Assistant",
    version: "0.15.0",
    sovereignLocal: true,
    externalModelsUsed: false,
  });
});

interface SearchSource {
  title: string;
  url: string;
  snippet: string;
  domain: string;
  date?: string;
  sourceName?: string;
  provider?: string;
  score: number;
}

// Validate before contacting providers; all searches have a shared deadline.
app.get("/api/search/suggestions", async (req: Request, res: Response) => {
  const query = validateQuery(req.query.q);
  res.json({ suggestions: query ? await suggestions(query, AbortSignal.timeout(3000)) : [] });
});

app.post("/api/search", async (req: Request, res: Response) => {
  const query = validateQuery(req.body?.query);
  if (!query) return res.status(400).json({ error: "Ingresá una consulta de 1 a 500 caracteres." });
  const controller = new AbortController();
  const cancel = () => { if (!res.writableEnded) controller.abort(); };
  res.on("close", cancel);
  try {
    const result = await searchWeb(query, { signal: controller.signal });
    if (!controller.signal.aborted) res.json(result);
  } catch {
    if (!controller.signal.aborted) res.status(503).json({ error: "La búsqueda no está disponible. Volvé a intentar." });
  } finally { res.off("close", cancel); }
});

// 100% Sovereign Web Companion Synthesis (no GPT, no Gemini, no external LLMs)
app.post("/api/chat", async (req: Request, res: Response) => {
  const {
    message,
    sources = [],
    personality = "BALANCED",
  } = req.body || {};

  if (!message) {
    return res.status(400).json({ error: "Message is required" });
  }

  const cleanMessage = String(message).trim();

  // 1. Deterministic math & unit conversion short-circuit
  const unitResult = LocalBrain.convertUnits(cleanMessage);
  if (unitResult !== null) {
    return res.json({ reply: unitResult, provider: "local-math", sources: [] });
  }
  const mathResult = LocalBrain.evaluateMath(cleanMessage);
  if (mathResult !== null) {
    return res.json({ reply: `El resultado es ${mathResult}.`, provider: "local-math", sources: [] });
  }

  // 2. Personal memory, Eddy Lab Rats protocols/persona, autodidactic rules, local RAG & Emotional Heart
  const personalOrEmotion =
    MemoryStore.handlePersonalMemory(cleanMessage) ??
    LocalBrain.handleEddyLabRatsFeatures(cleanMessage) ??
    LocalBrain.handleAutodidactAndRag(cleanMessage) ??
    LocalBrain.detectEmotionAndRespond(cleanMessage);
  if (personalOrEmotion !== null) {
    return res.json({ reply: personalOrEmotion, provider: "local-sovereign", sources: [] });
  }

  // 3. Multi-source fluent extractive synthesis when web sources are available
  if (Array.isArray(sources) && sources.length > 0) {
    const reply = LocalBrain.synthesizeWebSources(cleanMessage, sources as SearchSource[], String(personality));
    return res.json({
      reply,
      provider: "local-sovereign",
      sources,
      groundedWithGoogle: false,
    });
  }

  return res.json({
    reply: "No tengo fuentes suficientes para responder esa pregunta. Probá una búsqueda más específica o enseñame una regla personalizada.",
    provider: "local-sovereign",
    sources: [],
  });
});

// Smart Home simulation / proxy endpoint
app.post("/api/smarthome", async (req: Request, res: Response) => {
  const { action, entity, baseUrl, token } = req.body || {};

  // If real Home Assistant URL and token provided, proxy the request
  if (baseUrl && token && baseUrl.startsWith("http")) {
    try {
      const haUrl = `${baseUrl.replace(/\/$/, "")}/api/services/${action.replace(".", "/")}`;
      const haResp = await fetch(haUrl, {
        method: "POST",
        headers: {
          Authorization: `Bearer ${token}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify({ entity_id: entity }),
        signal: AbortSignal.timeout(3000),
      });
      if (haResp.ok) {
        return res.json({ success: true, mode: "live" });
      }
    } catch (e) {
      console.warn("Home Assistant live call failed, falling back to simulated state:", e);
    }
  }

  // Simulated smart home response
  res.json({
    success: true,
    mode: "simulated",
    message: `Acción ejecutada: ${action} en ${entity}`,
  });
});

async function start() {
  if (process.env.NODE_ENV !== "production") {
    const vite = await createViteServer({
      server: { middlewareMode: true },
      appType: "spa",
    });
    app.use(vite.middlewares);
  } else {
    const distPath = path.join(process.cwd(), "dist");
    app.use(express.static(distPath));
    app.get("*", (_req: Request, res: Response) => {
      res.sendFile(path.join(distPath, "index.html"));
    });
  }

  app.listen(PORT, "0.0.0.0", () => {
    console.log(`LEO Assistant server running on http://0.0.0.0:${PORT}`);
  });
}

start();
