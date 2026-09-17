import React, { useState, useEffect, useRef } from "react";
import {
  Search,
  Globe,
  ExternalLink,
  Volume2,
  Square,
  Sparkles,
  ArrowRight,
  X,
  TrendingUp,
  Loader2,
  CheckCircle2,
  Calendar,
} from "lucide-react";
import { NikoWebSource, GoogleSearchResult } from "../types";

interface GoogleSearchModalProps {
  isOpen: boolean;
  onClose: () => void;
  initialQuery?: string;
  initialResult?: GoogleSearchResult | null;
  onSpeakText?: (text: string) => void;
  onStopSpeaking?: () => void;
  isSpeaking?: boolean;
}

const QUICK_TRENDS = [
  "Últimas noticias de hoy",
  "Clima y pronóstico",
  "Avances recientes en Inteligencia Artificial",
  "Exploración espacial y misiones",
  "Cotización y mercados",
  "Selección Argentina de fútbol",
];

export const GoogleSearchModal: React.FC<GoogleSearchModalProps> = ({
  isOpen,
  onClose,
  initialQuery = "",
  initialResult = null,
  onSpeakText,
  onStopSpeaking,
  isSpeaking = false,
}) => {
  const [query, setQuery] = useState(initialQuery);
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState<GoogleSearchResult | null>(initialResult);
  const [suggestions, setSuggestions] = useState<string[]>([]);
  const [showSuggestions, setShowSuggestions] = useState(false);
  const inputRef = useRef<HTMLInputElement>(null);
  const debounceTimerRef = useRef<any>(null);

  // Sync initial query and result when modal opens
  useEffect(() => {
    if (isOpen) {
      if (initialQuery) {
        setQuery(initialQuery);
      }
      if (initialResult) {
        setResult(initialResult);
      } else if (initialQuery) {
        handleSearch(initialQuery);
      }
      setTimeout(() => {
        inputRef.current?.focus();
      }, 100);
    }
  }, [isOpen, initialQuery, initialResult]);

  // Autocomplete fetch on query change
  useEffect(() => {
    if (!query.trim() || query.length < 2) {
      setSuggestions([]);
      return;
    }

    if (debounceTimerRef.current) {
      clearTimeout(debounceTimerRef.current);
    }

    debounceTimerRef.current = setTimeout(async () => {
      try {
        const resp = await fetch(`/api/search/suggestions?q=${encodeURIComponent(query.trim())}`);
        if (resp.ok) {
          const data = await resp.json();
          if (Array.isArray(data.suggestions)) {
            setSuggestions(data.suggestions);
          }
        }
      } catch (_e) {
        // ignore
      }
    }, 200);

    return () => {
      if (debounceTimerRef.current) {
        clearTimeout(debounceTimerRef.current);
      }
    };
  }, [query]);

  const handleSearch = async (searchQuery: string) => {
    const q = searchQuery.trim();
    if (!q) return;

    setShowSuggestions(false);
    setLoading(true);

    try {
      const resp = await fetch("/api/search", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ query: q }),
      });

      if (resp.ok) {
        const data = await resp.json();
        const searchResult: GoogleSearchResult = {
          query: data.query || q,
          provider: data.provider || "Google",
          summary: data.summary || "",
          sources: data.sources || [],
          suggestions: data.suggestions || [],
          groundedWithGoogle: data.groundedWithGoogle,
        };
        setResult(searchResult);

        // Read out synthesis if available
        if (searchResult.summary && onSpeakText) {
          // Clean citation brackets for smoother voice
          const speechSummary = searchResult.summary.replace(/\[\d+\]/g, "");
          onSpeakText(speechSummary);
        }
      }
    } catch (err) {
      console.error("Failed to execute search:", err);
    } finally {
      setLoading(false);
    }
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    handleSearch(query);
  };

  const handleSelectSuggestion = (s: string) => {
    setQuery(s);
    setShowSuggestions(false);
    handleSearch(s);
  };

  if (!isOpen) return null;

  return (
    <div
      id="google-search-modal-backdrop"
      className="fixed inset-0 z-50 flex items-center justify-center p-3 sm:p-4 bg-black/80 backdrop-blur-md animate-fadeIn select-none"
    >
      <div
        id="google-search-modal-card"
        className="w-full max-w-2xl max-h-[92vh] flex flex-col rounded-2xl bg-[#0B101B] border border-white/15 shadow-2xl overflow-hidden text-white font-sans"
        onClick={(e) => e.stopPropagation()}
      >
        {/* Header */}
        <div className="flex items-center justify-between px-4 sm:px-5 py-3.5 border-b border-white/10 bg-[#0E1524]">
          <div className="flex items-center gap-3">
            {/* Google Brand Dots */}
            <div className="flex items-center gap-1.5 p-1.5 rounded-lg bg-white/5 border border-white/10 shadow-inner">
              <span className="w-2.5 h-2.5 rounded-full bg-[#4285F4] shadow-[0_0_6px_#4285F4]" />
              <span className="w-2.5 h-2.5 rounded-full bg-[#EA4335] shadow-[0_0_6px_#EA4335]" />
              <span className="w-2.5 h-2.5 rounded-full bg-[#FBBC05] shadow-[0_0_6px_#FBBC05]" />
              <span className="w-2.5 h-2.5 rounded-full bg-[#34A853] shadow-[0_0_6px_#34A853]" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <h2 className="text-sm sm:text-base font-bold tracking-wide text-white">
                  Búsqueda Web con Google
                </h2>
                <span className="px-1.5 py-0.5 rounded text-[9px] font-bold tracking-wider bg-blue-500/20 text-blue-300 border border-blue-500/30">
                  EN VIVO
                </span>
              </div>
              <p className="text-[11px] text-white/50">
                Indexación en tiempo real por Google y síntesis con LEO
              </p>
            </div>
          </div>

          <button
            id="btn-close-google-search"
            onClick={onClose}
            className="p-2 rounded-xl bg-white/5 hover:bg-white/10 active:scale-95 text-white/70 hover:text-white transition-colors border border-white/10"
            title="Cerrar búsqueda"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        {/* Search Bar & Autocomplete Area */}
        <div className="p-4 sm:p-5 border-b border-white/10 bg-[#090D17]">
          <form onSubmit={handleSubmit} className="relative">
            <div className="flex items-center gap-2 rounded-xl bg-white/5 border border-white/15 focus-within:border-blue-400 focus-within:ring-2 focus-within:ring-blue-500/20 px-3 py-2 transition-all">
              <Search className="w-4 h-4 text-blue-400 shrink-0" />
              <input
                ref={inputRef}
                id="input-google-search"
                type="text"
                value={query}
                onChange={(e) => {
                  setQuery(e.target.value);
                  setShowSuggestions(true);
                }}
                onFocus={() => setShowSuggestions(true)}
                placeholder="Escribí lo que querés buscar en Google..."
                className="flex-1 bg-transparent text-sm text-white placeholder:text-white/35 focus:outline-none"
              />
              {query && (
                <button
                  type="button"
                  onClick={() => {
                    setQuery("");
                    setSuggestions([]);
                  }}
                  className="p-1 rounded-md text-white/40 hover:text-white hover:bg-white/10"
                  title="Borrar texto"
                >
                  <X className="w-3.5 h-3.5" />
                </button>
              )}
              <button
                id="btn-submit-google-search"
                type="submit"
                disabled={loading || !query.trim()}
                className="px-3 py-1.5 rounded-lg bg-blue-500 hover:bg-blue-600 active:scale-95 text-white text-xs font-semibold flex items-center gap-1.5 transition-all disabled:opacity-40 disabled:pointer-events-none shadow-md shadow-blue-500/20"
              >
                {loading ? (
                  <Loader2 className="w-3.5 h-3.5 animate-spin" />
                ) : (
                  <>
                    <span>Buscar</span>
                    <ArrowRight className="w-3.5 h-3.5" />
                  </>
                )}
              </button>
            </div>

            {/* Google Live Autocomplete Suggestions Dropdown */}
            {showSuggestions && suggestions.length > 0 && (
              <div className="absolute top-full left-0 right-0 mt-1.5 rounded-xl bg-[#0F1626] border border-white/15 shadow-2xl overflow-hidden z-20">
                <div className="px-3 py-1.5 text-[10px] font-semibold text-white/40 uppercase tracking-wider bg-white/5 border-b border-white/5 flex items-center gap-1.5">
                  <TrendingUp className="w-3 h-3 text-blue-400" />
                  <span>Sugerencias de búsqueda en Google</span>
                </div>
                <div className="py-1">
                  {suggestions.map((sug, idx) => (
                    <button
                      key={idx}
                      type="button"
                      onClick={() => handleSelectSuggestion(sug)}
                      className="w-full text-left px-3 py-2 text-xs text-white/90 hover:bg-blue-500/15 hover:text-blue-200 transition-colors flex items-center gap-2"
                    >
                      <Search className="w-3 h-3 text-white/30" />
                      <span className="truncate">{sug}</span>
                    </button>
                  ))}
                </div>
              </div>
            )}
          </form>

          {/* Quick Trending Chips */}
          <div className="mt-3 flex items-center gap-1.5 overflow-x-auto no-scrollbar py-0.5">
            <span className="text-[10px] text-white/40 font-semibold uppercase tracking-wider shrink-0 mr-1 flex items-center gap-1">
              <Sparkles className="w-3 h-3 text-amber-400" />
              Tendencias:
            </span>
            {QUICK_TRENDS.map((trend, i) => (
              <button
                key={i}
                onClick={() => {
                  setQuery(trend);
                  handleSearch(trend);
                }}
                className="px-2.5 py-1 rounded-full text-[11px] bg-white/5 hover:bg-white/10 border border-white/10 hover:border-blue-400/40 text-white/70 hover:text-white transition-all whitespace-nowrap shrink-0"
              >
                {trend}
              </button>
            ))}
          </div>
        </div>

        {/* Results Container (Scrollable) */}
        <div className="flex-1 overflow-y-auto p-4 sm:p-5 space-y-4 select-text">
          {loading && (
            <div className="py-12 flex flex-col items-center justify-center text-center space-y-3">
              <div className="relative flex items-center justify-center">
                <div className="w-12 h-12 rounded-full border-2 border-blue-500/20 border-t-blue-500 animate-spin" />
                <Globe className="w-5 h-5 text-blue-400 absolute" />
              </div>
              <div>
                <p className="text-sm font-semibold text-white">
                  Consultando índices de Google Search...
                </p>
                <p className="text-xs text-white/40 mt-0.5">
                  Extrayendo y contrastando fuentes en tiempo real para LEO
                </p>
              </div>
            </div>
          )}

          {!loading && !result && (
            <div className="py-12 flex flex-col items-center justify-center text-center space-y-2 text-white/40">
              <Globe className="w-8 h-8 text-blue-400/60" />
              <p className="text-xs">
                Ingresá cualquier consulta para obtener una síntesis en tiempo real impulsada por Google Search.
              </p>
            </div>
          )}

          {!loading && result && (
            <>
              {/* LEO Synthesis Card */}
              {result.summary && (
                <div className="rounded-xl bg-gradient-to-b from-blue-950/30 to-[#0A1220] border border-blue-500/30 p-4 shadow-lg">
                  <div className="flex items-center justify-between pb-2 mb-2 border-b border-blue-500/20">
                    <div className="flex items-center gap-2">
                      <Sparkles className="w-4 h-4 text-blue-400" />
                      <span className="text-xs font-bold text-blue-200 uppercase tracking-wide">
                        Síntesis de LEO (Google Grounding)
                      </span>
                    </div>

                    {/* Audio read button */}
                    {onSpeakText && (
                      <button
                        onClick={() => {
                          if (isSpeaking && onStopSpeaking) {
                            onStopSpeaking();
                          } else {
                            const speech = result.summary.replace(/\[\d+\]/g, "");
                            onSpeakText(speech);
                          }
                        }}
                        className={`px-2.5 py-1 rounded-lg text-xs font-medium flex items-center gap-1.5 transition-all ${
                          isSpeaking
                            ? "bg-rose-500/20 text-rose-300 border border-rose-500/40 animate-pulse"
                            : "bg-blue-500/20 text-blue-300 hover:bg-blue-500/30 border border-blue-500/30"
                        }`}
                        title={isSpeaking ? "Detener lectura" : "Escuchar síntesis con voz de LEO"}
                      >
                        {isSpeaking ? (
                          <>
                            <Square className="w-3 h-3 fill-rose-400" />
                            <span>Pausar</span>
                          </>
                        ) : (
                          <>
                            <Volume2 className="w-3 h-3" />
                            <span>Escuchar</span>
                          </>
                        )}
                      </button>
                    )}
                  </div>

                  <div className="text-xs sm:text-sm text-white/90 leading-relaxed space-y-2 whitespace-pre-wrap">
                    {result.summary}
                  </div>
                </div>
              )}

              {/* Related Google Searches Chips */}
              {result.suggestions && result.suggestions.length > 0 && (
                <div className="space-y-1.5">
                  <span className="text-[10px] font-semibold text-white/40 uppercase tracking-wider">
                    Búsquedas relacionadas en Google:
                  </span>
                  <div className="flex flex-wrap gap-1.5">
                    {result.suggestions.map((sug, idx) => (
                      <button
                        key={idx}
                        onClick={() => {
                          setQuery(sug);
                          handleSearch(sug);
                        }}
                        className="px-2.5 py-1 rounded-lg text-[11px] bg-white/5 hover:bg-blue-500/15 border border-white/10 hover:border-blue-400/40 text-white/80 hover:text-blue-200 transition-all flex items-center gap-1"
                      >
                        <Search className="w-2.5 h-2.5 text-blue-400" />
                        <span>{sug}</span>
                      </button>
                    ))}
                  </div>
                </div>
              )}

              {/* Indexed Google Sources Grid */}
              <div className="space-y-2 pt-1">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-1.5">
                    <CheckCircle2 className="w-3.5 h-3.5 text-emerald-400" />
                    <span className="text-xs font-bold text-white uppercase tracking-wide">
                      Fuentes Web Indexadas por Google ({result.sources.length})
                    </span>
                  </div>

                  <a
                    href={`https://www.google.com/search?q=${encodeURIComponent(result.query)}`}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="text-[11px] text-blue-400 hover:text-blue-300 inline-flex items-center gap-1 hover:underline"
                  >
                    <span>Ver en Google.com</span>
                    <ExternalLink className="w-3 h-3" />
                  </a>
                </div>

                <div className="grid grid-cols-1 sm:grid-cols-2 gap-2.5">
                  {result.sources.map((src, idx) => (
                    <a
                      key={idx}
                      href={src.url}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="group p-3 rounded-xl bg-white/[0.03] hover:bg-white/[0.07] border border-white/10 hover:border-blue-400/40 transition-all flex flex-col justify-between"
                    >
                      <div>
                        <div className="flex items-center justify-between gap-2 mb-1.5">
                          <span className="px-2 py-0.5 rounded text-[9px] font-bold uppercase tracking-wider bg-blue-500/20 text-blue-300 border border-blue-500/30 truncate max-w-[150px]">
                            {src.sourceName || src.domain}
                          </span>
                          {src.date && (
                            <span className="text-[10px] text-white/40 flex items-center gap-1">
                              <Calendar className="w-2.5 h-2.5" />
                              {src.date}
                            </span>
                          )}
                        </div>

                        <h4 className="text-xs font-semibold text-white group-hover:text-blue-200 transition-colors line-clamp-2 leading-snug">
                          {src.title}
                        </h4>

                        <p className="mt-1 text-[11px] text-white/50 line-clamp-2 leading-relaxed">
                          {src.snippet}
                        </p>
                      </div>

                      <div className="mt-2.5 pt-2 border-t border-white/5 flex items-center justify-between text-[10px] text-white/40 group-hover:text-blue-300 transition-colors">
                        <span className="truncate max-w-[160px] font-mono">{src.domain}</span>
                        <ExternalLink className="w-3 h-3 shrink-0" />
                      </div>
                    </a>
                  ))}
                </div>
              </div>
            </>
          )}
        </div>

        {/* Footer info */}
        <div className="px-4 py-2.5 border-t border-white/10 bg-[#080D17] flex items-center justify-between text-[11px] text-white/40">
          <div className="flex items-center gap-2">
            <span className="w-1.5 h-1.5 rounded-full bg-emerald-400" />
            <span>Motor Google conectado y calibrado</span>
          </div>
          <span>LEO v0.11</span>
        </div>
      </div>
    </div>
  );
};
