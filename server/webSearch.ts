import type { GoogleGenAI, GenerateContentResponse } from '@google/genai';
import type { GoogleSearchResult, NikoWebSource } from '../src/types';

export function validateQuery(value: unknown): string | null {
  if (typeof value !== 'string') return null;
  const query = value.trim().replace(/\s+/g, ' ');
  return query.length > 0 && query.length <= 500 ? query : null;
}

export function safeUrl(value: string): string | null {
  try {
    const url = new URL(value);
    return ['https:', 'http:'].includes(url.protocol) && !url.username && !url.password ? url.href : null;
  } catch { return null; }
}

export function plainText(value: string): string {
  return value.replace(/<!\[CDATA\[([\s\S]*?)\]\]>/g, '$1').replace(/<[^>]*>/g, ' ')
    .replace(/&(#x[\da-f]+|#\d+|amp|quot|apos|lt|gt|nbsp);/gi, (entity, code: string) => {
      const named: Record<string, string> = { amp: '&', quot: '"', apos: "'", lt: '<', gt: '>', nbsp: ' ' };
      if (!code.startsWith('#')) return named[code.toLowerCase()] || entity;
      const n = code[1].toLowerCase() === 'x' ? parseInt(code.slice(2), 16) : Number(code.slice(1));
      return n > 0 && n <= 0x10ffff && !(n >= 0xd800 && n <= 0xdfff) ? String.fromCodePoint(n) : entity;
    }).replace(/\s+/g, ' ').trim();
}

export function parseRss(xml: string, provider: string): NikoWebSource[] {
  return (xml.match(/<item\b[^>]*>[\s\S]*?<\/item>/gi) || []).slice(0, 10).flatMap(item => {
    const field = (tag: string) => plainText(item.match(new RegExp(`<${tag}\\b[^>]*>([\\s\\S]*?)<\\/${tag}>`, 'i'))?.[1] || '');
    const url = safeUrl(field('link'));
    const title = field('title');
    if (!url || !title) return [];
    const date = new Date(field('pubDate'));
    return [{ title, url, snippet: field('description').slice(0, 700), domain: new URL(url).hostname,
      sourceName: field('source') || new URL(url).hostname, provider,
      date: Number.isFinite(date.getTime()) ? date.toISOString().slice(0, 10) : undefined }];
  });
}

export function parseDuckDuckGo(html: string): NikoWebSource[] {
  const blocks = html.split(/<div\b[^>]*class=["'][^"']*\bresult\b[^"']*["'][^>]*>/i);
  return blocks.flatMap(block => {
    const anchor = [...block.matchAll(/<a\b([^>]*)>([\s\S]*?)<\/a>/gi)]
      .find(match => /class=["'][^"']*\bresult__a\b/i.test(match[1]));
    const href = anchor?.[1].match(/href=["']([^"']+)["']/i)?.[1];
    if (!anchor || !href) return [];
    let url: URL;
    try { url = new URL(plainText(href), 'https://duckduckgo.com'); } catch { return []; }
    const destination = url.hostname === 'duckduckgo.com' || url.hostname.endsWith('.duckduckgo.com')
      ? url.searchParams.get('uddg') : url.href;
    const safe = destination && safeUrl(destination);
    if (!safe) return [];
    const snippet = block.match(/<(?:a|div)\b[^>]*class=["'][^"']*result__snippet[^"']*["'][^>]*>([\s\S]*?)<\/(?:a|div)>/i)?.[1] || '';
    return [{ title: plainText(anchor[2]), url: safe, snippet: plainText(snippet).slice(0, 700),
      domain: new URL(safe).hostname, provider: 'DuckDuckGo' }];
  }).slice(0, 8);
}

export function uniqueSources(sources: NikoWebSource[]): NikoWebSource[] {
  const seen = new Set<string>();
  return sources.filter(source => {
    const safe = safeUrl(source.url);
    if (!safe) return false;
    const url = new URL(safe);
    url.hash = '';
    for (const key of [...url.searchParams.keys()]) {
      if (/^(utm_|fbclid$|gclid$)/i.test(key)) url.searchParams.delete(key);
    }
    const key = url.href.replace(/\/$/, '');
    if (seen.has(key)) return false;
    seen.add(key);
    return true;
  });
}

// Only provider-supported passages can become cited claims. Map original chunk
// indices after deduplication; do not trust citation numbers invented in model text.
export function groundedResult(response: GenerateContentResponse): { sources: NikoWebSource[]; summary: string } {
  const metadata = response.candidates?.[0]?.groundingMetadata;
  const sources: NikoWebSource[] = [];
  const indices = new Map<number, number>();
  for (const [index, chunk] of (metadata?.groundingChunks || []).entries()) {
    const url = chunk.web?.uri && safeUrl(chunk.web.uri);
    if (!url) continue;
    let position = sources.findIndex(source => uniqueSources([source, { title: "", snippet: "", domain: "", url }]).length === 1);
    if (position < 0) {
      if (sources.length >= 8) continue;
      position = sources.length;
      sources.push({ title: chunk.web?.title || new URL(url).hostname, url,
        domain: new URL(url).hostname, snippet: '', provider: 'Google Search' });
    }
    indices.set(index, position + 1);
  }
  const passages = (metadata?.groundingSupports || []).flatMap(support => {
    const text = support.segment?.text?.trim();
    const refs = [...new Set((support.groundingChunkIndices || []).map(i => indices.get(i)).filter((n): n is number => n !== undefined))];
    if (!text || !refs.length) return [];
    return [`${text.replace(/\[\d+\]/g, '').trim()} ${refs.map(n => `[${n}]`).join('')}`];
  });
  return { sources, summary: [...new Set(passages)].join('\n\n') };
}

type Transport = typeof fetch;
export async function fetchText(url: string, signal: AbortSignal, transport: Transport = fetch): Promise<string> {
  const response = await transport(url, { signal, headers: { 'User-Agent': 'LEO-Assistant/0.11', 'Accept-Language': 'es,en;q=0.7' } });
  if (!response.ok) throw new Error(`HTTP ${response.status}`);
  const reader = response.body?.getReader();
  if (!reader) return '';
  const decoder = new TextDecoder();
  let text = '', bytes = 0;
  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      bytes += value.byteLength;
      if (bytes > 1_000_000) throw new Error('Response too large');
      text += decoder.decode(value, { stream: true });
    }
    return text + decoder.decode();
  } finally { await reader.cancel().catch(() => {}); reader.releaseLock(); }
}

export async function suggestions(query: string, signal: AbortSignal, transport: Transport = fetch): Promise<string[]> {
  try {
    const data = JSON.parse(await fetchText(`https://suggestqueries.google.com/complete/search?client=chrome&hl=es&q=${encodeURIComponent(query)}`, signal, transport));
    return Array.isArray(data?.[1]) ? data[1].filter((s: unknown): s is string => typeof s === 'string').slice(0, 7) : [];
  } catch { return []; }
}

export async function searchWeb(query: string, options: { gemini?: GoogleGenAI | null; transport?: Transport; signal?: AbortSignal; timeoutMs?: number } = {}): Promise<GoogleSearchResult> {
  const transport = options.transport || fetch;
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), options.timeoutMs ?? 10_000);
  const abort = () => controller.abort();
  options.signal?.addEventListener('abort', abort, { once: true });
  if (options.signal?.aborted) abort();
  const signal = controller.signal;
  const q = encodeURIComponent(query);
  const warnings: string[] = [];
  const provider = async (name: string, job: () => Promise<NikoWebSource[]>) => {
    try { return await job(); } catch { warnings.push(`${name} no estuvo disponible.`); return []; }
  };
  try {
    const [grounded, bing, duck, news, wiki, related] = await Promise.all([
      (async () => {
        if (!options.gemini) return { sources: [], summary: '' };
        try {
          const response = await options.gemini.models.generateContent({
            model: process.env.GEMINI_MODEL || 'gemini-3.8-flash',
            contents: `Respondé en español usando Google Search. Consulta: ${query}`,
            config: { tools: [{ googleSearch: {} }], abortSignal: signal },
          });
          return groundedResult(response);
        } catch { warnings.push('La síntesis con Google no estuvo disponible.'); return { sources: [], summary: '' }; }
      })(),
      provider('Bing', async () => parseRss(await fetchText(`https://www.bing.com/search?format=rss&setlang=es&q=${q}`, signal, transport), 'Bing')),
      provider('DuckDuckGo', async () => parseDuckDuckGo(await fetchText(`https://html.duckduckgo.com/html/?q=${q}`, signal, transport))),
      provider('Google News', async () => parseRss(await fetchText(`https://news.google.com/rss/search?q=${q}&hl=es-419&gl=NI&ceid=NI:es-419`, signal, transport), 'Google News')),
      provider('Wikipedia', async () => {
        const data = JSON.parse(await fetchText(`https://es.wikipedia.org/w/api.php?action=query&list=search&srsearch=${q}&format=json&srlimit=3`, signal, transport));
        return (Array.isArray(data?.query?.search) ? data.query.search : []).flatMap((item: { title?: unknown; snippet?: unknown }) =>
          typeof item.title === 'string' ? [{ title: item.title, url: `https://es.wikipedia.org/wiki/${encodeURIComponent(item.title)}`,
            snippet: typeof item.snippet === 'string' ? plainText(item.snippet) : '', domain: 'es.wikipedia.org', provider: 'Wikipedia' }] : []);
      }),
      suggestions(query, signal, transport),
    ]);
    const current = /\b(hoy|ayer|actual|actuales|noticias|recientes|precio|clima)\b/i.test(query);
    // Preserve grounded ordering: citation numbers refer to these exact positions.
    const sources = uniqueSources([...grounded.sources, ...(current ? [...news, ...bing, ...duck] : [...bing, ...duck, ...wiki, ...news]), ...(current ? wiki : [])]).slice(0, 8);
    const summary = grounded.summary || (sources.length
      ? `Extractos de resultados; no se han leído ni verificado las páginas completas:\n\n${sources.slice(0, 4).map((s, i) => `${s.title}${s.snippet ? ` — ${s.snippet}` : ''} [${i + 1}]`).join('\n\n')}`
      : 'No pude obtener resultados. Revisá la conexión, reintentá o abrí la búsqueda directamente en Google.');
    return { query, provider: [...new Set(sources.map(s => s.provider))].join(' · ') || 'Sin resultados', summary,
      sources, suggestions: related, groundedWithGoogle: Boolean(grounded.summary), warnings,
      status: sources.length ? (warnings.length ? 'partial' : 'ok') : 'unavailable' };
  } finally { clearTimeout(timeout); options.signal?.removeEventListener('abort', abort); }
}
