import test from 'node:test';
import assert from 'node:assert/strict';
import { fetchText, groundedResult, parseRss, parseDuckDuckGo, searchWeb, uniqueSources, validateQuery } from '../server/webSearch.ts';
import type { GenerateContentResponse } from '@google/genai';

const rss = '<rss><channel><item><title><![CDATA[Panel &amp; solar]]></title><link>https://energy.example/solar</link><description>La energía solar genera electricidad.</description><pubDate>bad date</pubDate></item></channel></rss>';
const mockFetch = (job: (url: string, init?: RequestInit) => Promise<Response>) => job as typeof fetch;

test('validates untrusted query bodies without throwing', () => {
  for (const value of [null, {}, [], 4, '', ' ', 'x'.repeat(501)]) assert.equal(validateQuery(value), null);
  assert.equal(validateQuery('  energía   solar '), 'energía solar');
});
test('RSS decodes entities, keeps actual snippets and rejects unsafe links', () => {
  const [source] = parseRss(rss, 'Bing');
  assert.equal(source.title, 'Panel & solar');
  assert.equal(source.date, undefined);
  assert.equal(source.snippet, 'La energía solar genera electricidad.');
  assert.equal(parseRss(rss.replace('https://energy.example/solar', 'javascript:alert(1)'), 'Bing').length, 0);
});
test('deduplicates tracking URLs without merging different articles with the same title', () => {
  const source = { title: 'Solar', snippet: '', domain: 'energy.example', url: 'https://energy.example/solar' };
  assert.equal(uniqueSources([source, { ...source, url: source.url + '?utm_source=test#top' }, { ...source, url: source.url + '/other' }]).length, 2);
});
test('grounding maps duplicates and unsafe chunks to the displayed citation numbers', () => {
  const result = groundedResult({ candidates: [{ groundingMetadata: {
    groundingChunks: [{ web: { uri: 'javascript:alert(1)' } }, { web: { uri: 'https://a.example' } }, { web: { uri: 'https://a.example/?utm_source=x' } }, { web: { uri: 'https://b.example' } }],
    groundingSupports: [{ segment: { text: 'Dato [99]' }, groundingChunkIndices: [1, 2, 3] }, { segment: { text: 'Sin respaldo' }, groundingChunkIndices: [0, 99] }],
  } }] } as GenerateContentResponse);
  assert.equal(result.sources.length, 2);
  assert.equal(result.summary, 'Dato [1][2]');
});
test('model text alone is never presented as a grounded answer', () => {
  const result = groundedResult({ text: 'Invented answer', candidates: [{ groundingMetadata: {} }] } as GenerateContentResponse);
  assert.equal(result.summary, '');
});
test('providers start concurrently; one failure does not discard usable results', async () => {
  const started: string[] = [];
  let release!: () => void;
  const gate = new Promise<void>(resolve => { release = resolve; });
  const pending = searchWeb('panel solar', { transport: mockFetch(async url => {
    started.push(url);
    await gate;
    if (url.includes('bing.com')) return new Response(rss);
    if (url.includes('news.google')) return new Response('', { status: 429 });
    return Response.json(url.includes('wikipedia') ? { query: { search: [] } } : ['', ['solar', 123]]);
  }) });
  assert.equal(started.length, 5);
  release();
  const result = await pending;
  assert.equal(result.status, 'partial');
  assert.equal(result.sources.length, 1);
  assert.equal(result.groundedWithGoogle, false);
  assert.match(result.summary, /no se han leído ni verificado/);
  assert.deepEqual(result.suggestions, ['solar']);
});
test('all provider failures return zero sources, never a fabricated search result', async () => {
  const result = await searchWeb('test', { transport: mockFetch(async () => { throw new Error('offline'); }) });
  assert.deepEqual(result.sources, []);
  assert.equal(result.status, 'unavailable');
  assert.equal(result.groundedWithGoogle, false);
});
test('shared deadline aborts every outstanding provider', async () => {
  let aborted = 0;
  const result = await searchWeb('test', { timeoutMs: 20, transport: mockFetch(async (_url, init) => new Promise((_resolve, reject) => {
    init!.signal!.addEventListener('abort', () => { aborted++; reject(new Error('aborted')); }, { once: true });
  })) });
  assert.equal(aborted, 5);
  assert.equal(result.status, 'unavailable');
});
test('client cancellation propagates to all providers', async () => {
  const controller = new AbortController();
  let aborted = 0;
  const pending = searchWeb('test', { signal: controller.signal, transport: mockFetch(async (_url, init) => new Promise((_resolve, reject) => {
    init!.signal!.addEventListener('abort', () => { aborted++; reject(new Error('aborted')); }, { once: true });
  })) });
  controller.abort();
  await pending;
  assert.equal(aborted, 5);
});
test('response body size is bounded', async () => {
  await assert.rejects(fetchText('https://example.com', new AbortController().signal, mockFetch(async () => new Response('a'.repeat(1_000_001)))), /too large/);
});

test('DuckDuckGo redirect links and snippets are extracted without tracking wrappers', () => {
  const result = parseDuckDuckGo(`<div class="result results_links"><h2><a class="result__a" href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fenergy.example%2Fsolar&amp;rut=test">Panel solar</a></h2><a class="result__snippet">La energía <b>solar</b> genera electricidad.</a></div>`);
  assert.equal(result.length, 1);
  assert.equal(result[0].url, 'https://energy.example/solar');
  assert.equal(result[0].snippet, 'La energía solar genera electricidad.');
});
