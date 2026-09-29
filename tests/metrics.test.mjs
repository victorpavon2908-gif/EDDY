import test from 'node:test';
import assert from 'node:assert/strict';
import { parseCsv, summarize } from '../scripts/calcular_metricas.mjs';
const row = (id, valor, extra = {}) => ({ id, valor, estado: 'MEDIDO', evidencia: 'log.txt', escenario: 'caso', intento: '1', ...extra });
const measure = rows => summarize(rows, () => true);
test('CSV: quoted commas, escaped quotes, multiline, BOM and CRLF', () => {
  const [r] = parseCsv('\uFEFFid,valor,estado,evidencia,observacion\r\nM03,"12,5",MEDIDO,log.txt,"a, b\n""c"""\r\n');
  assert.equal(r.observacion, 'a, b\n"c"'); assert.equal(r.valor, '12,5');
});
test('CSV rejects malformed rows and unterminated quotes', () => {
  for (const data of ['id,valor,estado,evidencia\nM01,TP', 'id,valor,estado,evidencia\n"abc']) assert.throws(() => parseCsv(data));
});
test('M01 uses TP and FN, warns until 100 trials', () => {
  const r = measure([row('M01', 'TP'), row('M01', 'FN', { intento: '2' })]);
  assert.equal(r.metricas.M01.valor, 50); assert.equal(r.advertencias.length, 1);
});
test('M02 divides total false positives by total observation hours', () => {
  assert.equal(measure([row('M02', '3', { duracion_h: '0.5' }), row('M02', '1', { intento: '2', duracion_h: '1.5' })]).metricas.M02.valor, 2);
});
test('M03/M04/M05 report medians including zero and decimals', () => {
  for (const id of ['M03', 'M04', 'M05']) assert.equal(measure(['0','12,5','20','30'].map((v,i) => row(id,v,{intento:String(i)}))).metricas[id].valor, 16.25);
});
test('M06 and M07 use exact categorical values', () => {
  for (const [id, yes, no] of [['M06','correcta','incorrecta'],['M07','correcto','incorrecto']]) assert.equal(measure([row(id,yes),row(id,no,{intento:'2'})]).metricas[id].valor, 50);
});
test('M08/M09 retain duration and normalize battery drain', () => {
  for (const id of ['M08','M09']) assert.deepEqual(measure([row(id,'2',{duracion_h:'0.5'})]).metricas[id].valor, [{puntos:2,horas:0.5,puntos_por_hora:4}]);
});
test('M10 counts incidents, zero is a real observation', () => assert.equal(measure([row('M10','0')]).metricas.M10.valor,0));
test('Missing evidence cannot certify a measurement', () => {
  const r = summarize([row('M01','TP')]); assert.equal(r.metricas.M01.valor,null); assert.equal(r.rechazadas.length,1);
});
test('Reject duplicates, invalid values, durations and statuses', () => {
  const r = measure([row('M01','TP'),row('M01','TP'),row('M02','2'),row('M03',''),row('M04','-1'),row('M06','casi correcta'),row('M08','101',{duracion_h:'1'}),row('M10','0.5'),row('M07','correcto',{estado:'SIMULADO'})]);
  assert.equal(r.rechazadas.length,8);
});
test('Pending records do not become zero-valued results', () => {
  const r = measure([row('M10','',{estado:'PENDIENTE'})]); assert.equal(r.metricas.M10.valor,null);
});
