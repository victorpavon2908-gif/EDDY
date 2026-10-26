import test from 'node:test';
import assert from 'node:assert/strict';
import { MemoryStore } from '../src/services/memoryStore.ts';

function storage(raw: string | null, fail = false) {
  Object.defineProperty(globalThis, 'localStorage', { configurable: true, value: {
    getItem: () => raw,
    setItem: (_key: string, value: string) => { if (fail) throw new Error('quota'); raw = value; },
  }});
}

test('settings tolerate corrupt, null and primitive persisted values', () => {
  for (const raw of ['{broken', 'null', '42', '[]', '"text"']) {
    storage(raw);
    const settings = MemoryStore.getSettings();
    assert.equal(settings.voiceRate, 1.05);
    assert.equal(settings.personality, 'BALANCED');
  }
});
test('settings validate types, clamp sliders and retire fake web voice enrollment', () => {
  storage(JSON.stringify({ autoListening: 'false', autoResearch: false, voiceRate: 99, voicePitch: -4,
    personality: 'invalid', ownerEnrolled: true, ownerVoiceOnly: true }));
  const settings = MemoryStore.getSettings();
  assert.equal(settings.autoListening, true);
  assert.equal(settings.autoResearch, false);
  assert.equal(settings.voiceRate, 1.5);
  assert.equal(settings.voicePitch, 0.8);
  assert.equal(settings.personality, 'BALANCED');
  assert.equal(settings.ownerEnrolled, false);
  assert.equal(settings.ownerVoiceOnly, false);
});
test('save reports storage failure instead of reporting success', () => {
  storage(null, true);
  assert.equal(MemoryStore.saveSettings({ autoResearch: false }), false);
  assert.equal(MemoryStore.getSettings().autoResearch, true);
});
test('saved research and voice preferences survive reopening', () => {
  storage(null);
  assert.equal(MemoryStore.saveSettings({ autoResearch: false, voiceRate: 1.25, personality: 'TECHNICAL' }), true);
  assert.equal(MemoryStore.getSettings().autoResearch, false);
  assert.equal(MemoryStore.getSettings().voiceRate, 1.25);
  assert.equal(MemoryStore.getSettings().personality, 'TECHNICAL');
});
