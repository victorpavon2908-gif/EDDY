import express, { Request, Response } from "express";
import path from "path";
import { createServer as createViteServer } from "vite";
import { GoogleGenAI } from "@google/genai";

const app = express();
const PORT = 3000;

app.use(express.json());

// Lazy Gemini client helper
let genAiClient: GoogleGenAI | null = null;
function getGeminiClient(): GoogleGenAI | null {
  const apiKey = process.env.GEMINI_API_KEY;
  if (!apiKey) return null;
  if (!genAiClient) {
    genAiClient = new GoogleGenAI({ apiKey });
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
    groqConfigured: Boolean(process.env.GROQ_API_KEY),
  });
});

interface SearchSource {
  title: string;
  url: string;
  snippet: string;
  domain: string;
  date?: string;
  score: number;
}

// Web search API: DuckDuckGo + Wikipedia + Google News RSS
app.post("/api/search", async (req: Request, res: Response) => {
  const query = (req.body?.query || "").trim();
  if (!query) {
    return res.status(400).json({ error: "Missing query parameter" });
  }

  const results: SearchSource[] = [];

  try {
    // 1. Query Wikipedia (Spanish) API
    try {
      const wikiUrl = `https://es.wikipedia.org/w/api.php?action=query&list=search&srsearch=${encodeURIComponent(
        query
      )}&format=json&srlimit=3&origin=*`;
      const wikiResp = await fetch(wikiUrl, {
        headers: { "User-Agent": "LEO-Assistant/0.11.0" },
        signal: AbortSignal.timeout(4000),
      });
      if (wikiResp.ok) {
        const data = (await wikiResp.json()) as any;
        const searchItems = data?.query?.search || [];
        for (const item of searchItems) {
          const cleanSnippet = (item.snippet || "")
            .replace(/<[^>]*>/g, "")
            .replace(/&quot;/g, '"')
            .replace(/&amp;/g, "&");
          results.push({
            title: item.title || query,
            url: `https://es.wikipedia.org/wiki/${encodeURIComponent(item.title)}`,
            snippet: cleanSnippet,
            domain: "es.wikipedia.org",
            date: item.timestamp ? item.timestamp.split("T")[0] : undefined,
            score: 15,
          });
        }
      }
    } catch (_e) {
      // ignore wiki timeout
    }

    // 2. Query DuckDuckGo Instant Answer / HTML
    try {
      const ddgUrl = `https://api.duckduckgo.com/?q=${encodeURIComponent(
        query
      )}&format=json&no_html=1&skip_disambig=1`;
      const ddgResp = await fetch(ddgUrl, {
        headers: { "User-Agent": "LEO-Assistant/0.11.0" },
        signal: AbortSignal.timeout(4000),
      });
      if (ddgResp.ok) {
        const ddgData = (await ddgResp.json()) as any;
        if (ddgData.AbstractText && ddgData.AbstractURL) {
          results.push({
            title: ddgData.Heading || query,
            url: ddgData.AbstractURL,
            snippet: ddgData.AbstractText,
            domain: new URL(ddgData.AbstractURL).hostname.replace("www.", ""),
            score: 18,
          });
        }
        if (Array.isArray(ddgData.RelatedTopics)) {
          for (const topic of ddgData.RelatedTopics.slice(0, 3)) {
            if (topic.Text && topic.FirstURL) {
              results.push({
                title: topic.Text.split(" - ")[0] || query,
                url: topic.FirstURL,
                snippet: topic.Text,
                domain: new URL(topic.FirstURL).hostname.replace("www.", ""),
                score: 10,
              });
            }
          }
        }
      }
    } catch (_e) {
      // ignore ddg timeout
    }

    // 3. Fallback or general query if sparse: Google News RSS for Spanish
    if (results.length < 3) {
      try {
        const newsUrl = `https://news.google.com/rss/search?q=${encodeURIComponent(
          query
        )}&hl=es&gl=US&ceid=US:es`;
        const newsResp = await fetch(newsUrl, {
          signal: AbortSignal.timeout(4000),
        });
        if (newsResp.ok) {
          const xml = await newsResp.text();
          const items = xml.match(/<item>[\s\S]*?<\/item>/g) || [];
          for (const itemXml of items.slice(0, 4)) {
            const titleMatch = itemXml.match(/<title>([\s\S]*?)<\/title>/);
            const linkMatch = itemXml.match(/<link>([\s\S]*?)<\/link>/);
            const pubDateMatch = itemXml.match(/<pubDate>([\s\S]*?)<\/pubDate>/);
            if (titleMatch && linkMatch) {
              const rawTitle = titleMatch[1].replace(/<!\[CDATA\[(.*?)\]\]>/g, "$1").trim();
              const rawLink = linkMatch[1].trim();
              let domain = "news.google.com";
              try {
                domain = new URL(rawLink).hostname.replace("www.", "");
              } catch (_) {}

              results.push({
                title: rawTitle,
                url: rawLink,
                snippet: `Noticia relevante encontrada para "${query}". Publicado recientemente.`,
                domain,
                date: pubDateMatch ? new Date(pubDateMatch[1]).toLocaleDateString("es-ES") : undefined,
                score: 8,
              });
            }
          }
        }
      } catch (_e) {
        // ignore news timeout
      }
    }

    // Deduplicate by URL
    const seen = new Set<string>();
    const deduplicated: SearchSource[] = [];
    for (const r of results) {
      if (!seen.has(r.url)) {
        seen.add(r.url);
        deduplicated.push(r);
      }
    }

    // Fallback if still empty
    if (deduplicated.length === 0) {
      deduplicated.push({
        title: `Búsqueda sobre ${query}`,
        url: `https://duckduckgo.com/?q=${encodeURIComponent(query)}`,
        snippet: `Resultados generales recopilados para la consulta "${query}".`,
        domain: "duckduckgo.com",
        score: 5,
      });
    }

    res.json({
      query,
      sources: deduplicated.slice(0, 5),
    });
  } catch (error: any) {
    console.error("Search error:", error);
    res.status(500).json({ error: "Failed to execute web search" });
  }
});

// AI Chat / Synthesis endpoint (Gemini or Groq fallback)
app.post("/api/chat", async (req: Request, res: Response) => {
  const {
    message,
    history = [],
    sources = [],
    personality = "BALANCED",
    groqApiKey,
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
        model: "gemini-2.5-flash",
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
      });

      const text = response.text || "Aquí estoy para ayudarte.";
      return res.json({ reply: text, provider: "gemini" });
    } catch (err: any) {
      console.error("Gemini call failed, falling back:", err);
    }
  }

  // Try Groq if provided
  const activeGroqKey = groqApiKey || process.env.GROQ_API_KEY;
  if (activeGroqKey) {
    try {
      const groqResp = await fetch("https://api.groq.com/openai/v1/chat/completions", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${activeGroqKey}`,
        },
        body: JSON.stringify({
          model: "llama-3.3-70b-versatile",
          messages: [
            {
              role: "system",
              content: `${personalityInstruction} ${promptContext}`,
            },
            ...history.slice(-4).map((h: any) => ({
              role: h.role === "user" ? "user" : "assistant",
              content: h.text,
            })),
            { role: "user", content: message },
          ],
          temperature: 0.6,
          max_tokens: 500,
        }),
      });
      if (groqResp.ok) {
        const groqData = (await groqResp.json()) as any;
        const text = groqData.choices?.[0]?.message?.content;
        if (text) {
          return res.json({ reply: text, provider: "groq" });
        }
      }
    } catch (err) {
      console.error("Groq call failed:", err);
    }
  }

  // Smart local offline synthesis fallback
  let localReply = "";
  if (sources.length > 0) {
    const topSource = sources[0];
    localReply = `Según ${topSource.domain}, ${topSource.snippet.slice(0, 180)} [1]. Consulté ${sources.length} fuentes web.`;
  } else {
    localReply = `Entendido. Estoy procesando "${message}". Puedes configurar tu clave de Gemini o Groq en Ajustes para expandir mi razonamiento en la nube.`;
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
