import test from 'node:test';
import assert from 'node:assert/strict';
import { LocalBrain } from '../src/services/localBrain.ts';
import { MemoryStore } from '../src/services/memoryStore.ts';

for (const [input, expected] of [
  ['2 + 3','5'],['2.5 + 1.2','3.7'],['2,5 + 1,2','3.7'],['10 dividido por 2','5'],
  ['3 multiplicado por 4','12'],['20 por ciento de 150','30'],['20% de 150','30'],
  ['raíz cuadrada de 81','9'],['(2 + 3) * 4','20'],['2 elevado a 3','8'],
  ['cuánto es 7 menos 2','5'],['1 / 0',null],['hola 123',null],['raíz de 9 + 2',null],
  ['200 menos 15%','170'],['200 más el 15 por ciento','230'],['descuento del 15% a 200','170'],
  ['qué porcentaje es 30 de 150','20'],['raíz cúbica de 27','3'],['seno de 30','0.5'],
  ['coseno de 60','0.5'],['tangente de 45','1'],['logaritmo de 1000','3'],
  ['factorial de 5','120'],['5!','120'],['valor absoluto de -42','42'],['doce por cinco','60'],
] as const) test(`math: ${input}`, () => assert.equal(LocalBrain.evaluateMath(input), expected));

for (const [input, expected] of [
  ['convertí 100 kilómetros a millas', '100 km equivalen a 62.1371 mi.'],
  ['cuánto es 100 grados celsius en fahrenheit', '100 °C equivalen a 212 °F.'],
  ['cuántos megabytes son 2 gigabytes', '2 GB equivalen a 2048 MB.'],
  ['10 kilos a libras', '10 kg equivalen a 22.0462 lb.'],
  ['10 kilos a kilómetros', null],
] as const) test(`unit conversion: ${input}`, () => assert.equal(LocalBrain.convertUnits(input), expected));

for (const [input, type] of [
  ['Leo, pará','stop'],['Leo, qué hora es','tell_time'],['abre calculadora','open_tool'],
  ['cómo está la batería','battery_status'],['apaga la linterna','torch'],
  ['hola','greeting'],['2 + 3','math'],['Leo, baila','robot_motion'],['no bailes','unknown'],
  ['Leonardo da Vinci','unknown'],['convertí 100 km a millas','math'],
]) test(`routing: ${input}`, () => assert.equal(LocalBrain.understand(input).type,type));

test('wake word does not truncate Leonardo', () => assert.equal(LocalBrain.understand('Leonardo da Vinci').payload?.query,'Leonardo da Vinci'));

for (const [input, query] of [
  ['busca en internet paneles solares', 'paneles solares'],
  ['buscar en Google noticias de Nicaragua', 'noticias de Nicaragua'],
  ['busca tutoriales de YouTube', 'tutoriales de YouTube'],
  ['investiga Spotify', 'Spotify'],
]) test(`explicit search query: ${input}`, () => {
  const command = LocalBrain.understand(input);
  assert.equal(command.type, 'search_web');
  assert.equal(command.payload.query, query);
});

test('personal memory stores, updates, recalls and forgets preferences safely', () => {
  MemoryStore.clearMemory();
  const learn = LocalBrain.understand('Leo, mi comida favorita es el gallo pinto');
  assert.equal(learn.type, 'personal_memory');
  assert.ok(learn.payload.reply.includes('gallo pinto'));

  const recall = LocalBrain.understand('¿Qué comida me gusta más?');
  assert.equal(recall.type, 'personal_memory');
  assert.ok(recall.payload.reply.includes('gallo pinto'));

  const update = LocalBrain.understand('ya no me gusta el gallo pinto ahora prefiero nacatamal');
  assert.equal(update.type, 'personal_memory');
  assert.ok(update.payload.reply.includes('nacatamal'));

  const uncertain = LocalBrain.understand('mi comida favorita es quizás lasaña');
  assert.equal(uncertain.type, 'personal_memory');
  assert.ok(uncertain.payload.reply.includes('No cambié'));
  assert.equal(MemoryStore.getPreference('food'), 'nacatamal');

  const forget = LocalBrain.understand('olvida mi comida favorita');
  assert.equal(forget.type, 'personal_memory');
  assert.equal(MemoryStore.getPreference('food'), null);

  const intro = LocalBrain.understand('Leo, me llamo Mario');
  assert.equal(intro.type, 'personal_memory');
  assert.equal(MemoryStore.getPreference('name'), 'Mario');

  const project = LocalBrain.understand('mi proyecto actual es LEO Soberano');
  assert.equal(project.type, 'personal_memory');
  assert.ok(LocalBrain.understand('¿En qué proyecto estoy?').payload.reply.includes('LEO Soberano'));
});

test('autodidact rules, local RAG documents, emotional heart and web synthesis work locally', () => {
  MemoryStore.clearMemory();

  const teach = LocalBrain.understand('Leo, aprendé que cuando te diga código alfa me respondás protocolo soberano activo');
  assert.equal(teach.type, 'personal_memory');
  assert.ok(teach.payload.reply.includes('Aprendido'));

  const triggered = LocalBrain.understand('Leo, código alfa');
  assert.equal(triggered.type, 'personal_memory');
  assert.equal(triggered.payload.reply, 'protocolo soberano activo');

  const ragSave = LocalBrain.understand('guardá en mis documentos el PDF Manual Solar: El inversor híbrido convierte corriente continua en alterna a 220 voltios.');
  assert.equal(ragSave.type, 'personal_memory');
  assert.ok(ragSave.payload.reply.includes('Manual Solar'));

  const ragQuery = LocalBrain.understand('¿Qué dice en mis documentos sobre el inversor híbrido?');
  assert.equal(ragQuery.type, 'personal_memory');
  assert.ok(ragQuery.payload.reply.includes('220 voltios'));

  const emotion = LocalBrain.understand('me siento frustrado con este problema');
  assert.equal(emotion.type, 'personal_memory');
  assert.ok(emotion.payload.reply.includes('frustración'));
  assert.equal(LocalBrain.detectEmotionAndRespond('no estoy triste'), null);

  const synthesis = LocalBrain.synthesizeWebSources('energía solar en Nicaragua', [
    {
      title: 'Energía solar en Nicaragua',
      url: 'https://energia.example/ni',
      snippet: 'La energía solar permite generar electricidad limpia en zonas rurales de Nicaragua con alta radiación.',
    },
    {
      title: 'Reporte técnico',
      url: 'https://reporte.example/solar',
      snippet: 'Los paneles fotovoltaicos reducen el costo eléctrico hasta un 40 por ciento.',
    },
  ]);
  assert.ok(synthesis.includes('[1]'));
  assert.ok(synthesis.includes('electricidad limpia'));

  const eddyMode = LocalBrain.understand('Leo, activa modo misión');
  assert.equal(eddyMode.type, 'personal_memory');
  assert.ok(eddyMode.payload.reply.includes('CENTRO DE MANDO LEO'));

  const nightProto = LocalBrain.understand('Leo, protocolo nocturno');
  assert.equal(nightProto.type, 'personal_memory');
  assert.ok(nightProto.payload.reply.includes('PROTOCOLO NOCTURNO ACTIVO'));

  const timeline = LocalBrain.understand('¿Qué hicimos hoy?');
  assert.equal(timeline.type, 'personal_memory');
  assert.ok(timeline.payload.reply.includes('Protocolo Nocturno'));

  const missionSynthesis = LocalBrain.synthesizeWebSources('energía solar en Nicaragua', [
    {
      title: 'Energía solar en Nicaragua - Diario Local',
      url: 'https://energia.example/ni',
      domain: 'energia.example',
      snippet: 'La energía solar permite generar electricidad limpia en zonas rurales de Nicaragua con alta radiación.',
    },
    {
      title: 'Reporte técnico | Portal Energético',
      url: 'https://reporte.example/solar',
      domain: 'reporte.example',
      snippet: 'Los paneles fotovoltaicos reducen el costo eléctrico hasta un 40 por ciento.',
    },
  ]);
  assert.ok(missionSynthesis.startsWith('[REPORTE WEB VERIFICADO]'));
  assert.ok(missionSynthesis.includes('Además, los paneles fotovoltaicos'));
  assert.ok(!LocalBrain.cleanSpokenWebReply(missionSynthesis).includes('[1]'));
});
