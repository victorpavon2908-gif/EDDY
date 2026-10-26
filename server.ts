import express, { Request, Response } from "express";
import path from "path";
import { createServer as createViteServer } from "vite";
import { GoogleGenAI } from "@google/genai";

import { searchWeb, suggestions, validateQuery } from "./server/webSearch";

const app = express();
const PORT = 3000;

app.use(express.json());

// Lazy Gemini client helper
let genAiClient: GoogleGenAI | null = null;
function getGeminiClient(): GoogleGenAI | null {
  const apiKey = process.env.GEMINI_API_KEY;
  if (!apiKey) return null;
  if (!genAiClient) {
    genAiClient = new GoogleGenAI({
      apiKey,
      httpOptions: {
        headers: {
          "User-Agent": "aistudio-build",
        },
      },
    });
  }
  return genAiClient;
}

// Health check endpoint
app.get("/api/health", (_req: Request, res: Response) => {
  res.json({
    status: "ok",
    app: "LEO Assistant",
    version: "0.11.0",
    geminiConfigured: Boolean(process.env.GEMINI_API_KEY),
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
    const result = await searchWeb(query, { gemini: getGeminiClient(), signal: controller.signal });
    if (!controller.signal.aborted) res.json(result);
  } catch {
    if (!controller.signal.aborted) res.status(503).json({ error: "La búsqueda no está disponible. Volvé a intentar." });
  } finally { res.off("close", cancel); }
});

// Web companion synthesis (existing optional Gemini, no Groq provider)
app.post("/api/chat", async (req: Request, res: Response) => {
  const {
    message,
    history = [],
    sources = [],
    personality = "BALANCED",
  } = req.body || {};

  if (!message) {
    return res.status(400).json({ error: "Message is required" });
  }

  let personalityInstruction = "Sos LEO, un compañero asistente inteligente, empático, conciso y directo en español.";
  if (personality === "CONCISE") {
    personalityInstruction += " Respondés con extrema concisión, máximo 1 o 2 oraciones, directo al grano.";
  } else if (personality === "TECHNICAL") {
    personalityInstruction += " Respondés con precisión técnica, estructurando datos y explicando fundamentos.";
  }

  let promptContext = "";
  if (Array.isArray(sources) && sources.length > 0) {
    promptContext += `\n\nFuentes web encontradas para la consulta:\n` +
      sources
        .map(
          (s: SearchSource, idx: number) =>
            `[${idx + 1}] ${s.title} (${s.url}): ${s.snippet} (Fecha: ${s.date || "N/D"})`
        )
        .join("\n") +
      `\n\nInstrucción de síntesis: Basá tu respuesta en estas fuentes. Citá los hechos con [1], [2], etc. Si algo no está en las fuentes, aclaralo brevemente.`;
  }

  // Try Gemini API first if configured
  const gemini = getGeminiClient();
  if (gemini) {
    try {
      const response = await gemini.models.generateContent({
        model: process.env.GEMINI_MODEL || "gemini-3.8-flash",
        contents: [
          {
            role: "user",
            parts: [
              {
                text: `${personalityInstruction}\n\n${promptContext}\n\nMensaje del usuario: ${message}`,
              },
            ],
          },
        ],
        config: {
          tools: [{ googleSearch: {} }],
        },
      });

      const text = response.text || "Aquí estoy para ayudarte.";
      const candidate = response.candidates?.[0];
      const metadata = candidate?.groundingMetadata;
      const returnedSources: SearchSource[] = [...sources];

      if (metadata?.groundingChunks) {
        for (const chunk of metadata.groundingChunks) {
          if (chunk.web?.uri) {
            const uri = chunk.web.uri;
            let domain = "google.com";
            try {
              domain = new URL(uri).hostname.replace("www.", "");
            } catch (_) {}
            if (!returnedSources.some((s) => s.url === uri)) {
              returnedSources.push({
                title: chunk.web.title || domain,
                url: uri,
                snippet: `Fuente citada por Google Search.`,
                domain,
                sourceName: domain,
                provider: "Google Search",
                score: 25,
              });
            }
          }
        }
      }

      return res.json({
        reply: text,
        provider: "gemini",
        sources: returnedSources,
        groundedWithGoogle: Boolean(metadata?.webSearchQueries?.length || metadata?.groundingChunks?.length),
        webSearchQueries: metadata?.webSearchQueries || [],
      });
    } catch (err: any) {
      console.warn("Gemini chat call fallback:", err?.message || err);
    }
  }

  // Smart local offline synthesis fallback
  let localReply = "";
  if (sources.length > 0) {
    const topSource = sources[0];
    localReply = `Según ${topSource.domain}, ${topSource.snippet.slice(0, 180)} [1]. Consulté ${sources.length} fuentes web.`;
  } else {
    localReply = `No tengo fuentes suficientes para responder esa pregunta. Probá una búsqueda más específica.`;
  }

  return res.json({ reply: localReply, provider: "local" });
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
