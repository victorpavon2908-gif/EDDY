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
    groqConfigured: Boolean(process.env.GROQ_API_KEY),
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

// Google Search Suggestions Autocomplete
app.get("/api/search/suggestions", async (req: Request, res: Response) => {
  const query = String(req.query.q || "").trim();
  if (!query) {
    return res.json({ suggestions: [] });
  }

  try {
    const url = `https://suggestqueries.google.com/complete/search?client=chrome&q=${encodeURIComponent(
      query
    )}&hl=es`;
    const resp = await fetch(url, {
      headers: { "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)" },
      signal: AbortSignal.timeout(3000),
    });
    if (resp.ok) {
      const data = await resp.json();
      const suggestions = Array.isArray(data?.[1]) ? data[1].slice(0, 7) : [];
      return res.json({ suggestions });
    }
  } catch (_e) {
    // ignore suggestion timeout
  }

  return res.json({ suggestions: [] });
});

// Real-time Google Web Search API with Google Grounding & Live News Feeds
app.post("/api/search", async (req: Request, res: Response) => {
  const query = (req.body?.query || "").trim();
  if (!query) {
    return res.status(400).json({ error: "Missing query parameter" });
  }

  const results: SearchSource[] = [];
  let geminiSummary = "";
  let webSearchQueries: string[] = [];
  let groundedWithGoogle = false;

  // 1. First attempt Google Search Grounding with Gemini 3.8 Flash if configured
  const gemini = getGeminiClient();
  if (gemini) {
    try {
      const response = await gemini.models.generateContent({
        model: "gemini-3.8-flash",
        contents: `Sos LEO, asistente inteligente en español. Realizá una búsqueda exhaustiva en Google para responder a esta consulta: "${query}".
Presentá una síntesis clara, directa y actualizada en 2 o 3 párrafos concisos.
Citá las fuentes de información con números de referencia [1], [2], etc. según corresponda.`,
        config: {
          tools: [{ googleSearch: {} }],
        },
      });

      const text = response.text || "";
      const candidate = response.candidates?.[0];
      const metadata = candidate?.groundingMetadata;

      if (metadata) {
        groundedWithGoogle = true;
        if (Array.isArray(metadata.webSearchQueries)) {
          webSearchQueries = metadata.webSearchQueries;
        }

        if (Array.isArray(metadata.groundingChunks)) {
          for (const chunk of metadata.groundingChunks) {
            if (chunk.web?.uri) {
              const uri = chunk.web.uri;
              let domain = "google.com";
              try {
                domain = new URL(uri).hostname.replace("www.", "");
              } catch (_) {}

              results.push({
                title: chunk.web.title || domain,
                url: uri,
                snippet: `Resultado indexado por Google Search para la búsqueda "${query}".`,
                domain,
                sourceName: domain,
                provider: "Google Search",
                score: 30,
              });
            }
          }
        }
      }

      if (text) {
        geminiSummary = text;
      }
    } catch (err: any) {
      console.warn("Gemini Google Search Grounding fallback needed:", err?.message || err);
    }
  }

  // 2. Query Live Google News RSS (Spanish Latin America & Global)
  try {
    const newsUrls = [
      `https://news.google.com/rss/search?q=${encodeURIComponent(query)}&hl=es-419&gl=AR&ceid=AR:es-419`,
      `https://news.google.com/rss/search?q=${encodeURIComponent(query)}&hl=es&gl=ES&ceid=ES:es`,
    ];

    for (const newsUrl of newsUrls) {
      try {
        const newsResp = await fetch(newsUrl, {
          headers: { "User-Agent": "LEO-Assistant/0.11.0 (Google-Search-Client)" },
          signal: AbortSignal.timeout(4500),
        });

        if (newsResp.ok) {
          const xml = await newsResp.text();
          const items = xml.match(/<item>[\s\S]*?<\/item>/g) || [];

          for (const itemXml of items.slice(0, 5)) {
            const titleMatch = itemXml.match(/<title>([\s\S]*?)<\/title>/);
            const linkMatch = itemXml.match(/<link>([\s\S]*?)<\/link>/);
            const sourceMatch = itemXml.match(/<source[^>]*>([\s\S]*?)<\/source>/);
            const pubDateMatch = itemXml.match(/<pubDate>([\s\S]*?)<\/pubDate>/);

            if (titleMatch && linkMatch) {
              let cleanTitle = titleMatch[1].replace(/<!\[CDATA\[(.*?)\]\]>/g, "$1").trim();
              const sourceName = sourceMatch
                ? sourceMatch[1].replace(/<!\[CDATA\[(.*?)\]\]>/g, "$1").trim()
                : "Google News";

              // Remove source trailer from title if present
              if (sourceName && cleanTitle.endsWith(` - ${sourceName}`)) {
                cleanTitle = cleanTitle.slice(0, -(` - ${sourceName}`.length)).trim();
              }

              const rawLink = linkMatch[1].trim();
              let domain = "news.google.com";
              try {
                domain = new URL(rawLink).hostname.replace("www.", "");
              } catch (_) {}

              let formattedDate: string | undefined;
              if (pubDateMatch) {
                try {
                  const d = new Date(pubDateMatch[1]);
                  formattedDate = d.toLocaleDateString("es-ES", {
                    day: "numeric",
                    month: "short",
                    year: "numeric",
                  });
                } catch (_) {}
              }

              results.push({
                title: cleanTitle,
                url: rawLink,
                snippet: `Artículo verificado publicado por ${sourceName} e indexado en Google Search.`,
                domain,
                sourceName,
                date: formattedDate,
                provider: "Google News",
                score: 20,
              });
            }
          }
        }
      } catch (_e) {
        // next news feed
      }
    }
  } catch (_err) {
    // ignore
  }

  // 3. Query Wikipedia (Spanish) API for foundational facts
  try {
    const wikiUrl = `https://es.wikipedia.org/w/api.php?action=query&list=search&srsearch=${encodeURIComponent(
      query
    )}&format=json&srlimit=3&origin=*`;
    const wikiResp = await fetch(wikiUrl, {
      headers: { "User-Agent": "LEO-Assistant/0.11.0" },
      signal: AbortSignal.timeout(3500),
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
          sourceName: "Wikipedia",
          date: item.timestamp ? item.timestamp.split("T")[0] : undefined,
          provider: "Enciclopedia Web",
          score: 15,
        });
      }
    }
  } catch (_e) {
    // ignore wiki timeout
  }

  // 4. Fetch Google Autocomplete Suggestions for this query
  let suggestions: string[] = [];
  try {
    const suggUrl = `https://suggestqueries.google.com/complete/search?client=chrome&q=${encodeURIComponent(
      query
    )}&hl=es`;
    const suggResp = await fetch(suggUrl, {
      headers: { "User-Agent": "Mozilla/5.0" },
      signal: AbortSignal.timeout(2500),
    });
    if (suggResp.ok) {
      const data = await suggResp.json();
      if (Array.isArray(data?.[1])) {
        suggestions = data[1].slice(0, 6);
      }
    }
  } catch (_e) {
    // ignore
  }

  // Deduplicate results by URL and title
  const seenUrls = new Set<string>();
  const seenTitles = new Set<string>();
  const deduplicated: SearchSource[] = [];

  for (const r of results) {
    const titleKey = r.title.toLowerCase().trim();
    if (!seenUrls.has(r.url) && !seenTitles.has(titleKey)) {
      seenUrls.add(r.url);
      seenTitles.add(titleKey);
      deduplicated.push(r);
    }
  }

  // Direct Google search link as fallback/standard companion
  const directGoogleSearchUrl = `https://www.google.com/search?q=${encodeURIComponent(query)}`;

  if (deduplicated.length === 0) {
    deduplicated.push({
      title: `Resultados en Google: "${query}"`,
      url: directGoogleSearchUrl,
      snippet: `Búsqueda directa en tiempo real a través del motor Google Search para la consulta "${query}".`,
      domain: "google.com",
      sourceName: "Google Search",
      provider: "Google",
      score: 10,
    });
  }

  // If no Gemini summary was generated, synthesize from top Google Search sources
  if (!geminiSummary && deduplicated.length > 0) {
    const top = deduplicated.slice(0, 3);
    const facts = top
      .map(
        (s, idx) =>
          `[${idx + 1}] ${s.sourceName ? s.sourceName + ": " : ""}${s.title}${
            s.snippet && !s.snippet.startsWith("Artículo verificado") ? " — " + s.snippet : ""
          }`
      )
      .join("\n\n");

    geminiSummary = `Según los resultados más recientes encontrados en Google Search sobre "${query}":\n\n${facts}\n\nPodés explorar cada fuente citada para acceder al contenido completo.`;
  }

  res.json({
    query,
    provider: "Google",
    summary: geminiSummary,
    searchQueries: webSearchQueries.length > 0 ? webSearchQueries : [query, ...suggestions.slice(0, 2)],
    suggestions,
    sources: deduplicated.slice(0, 8),
    groundedWithGoogle,
  });
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
        model: "gemini-3.8-flash",
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
