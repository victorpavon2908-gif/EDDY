import fs from 'node:fs';
import path from 'node:path';
import { pathToFileURL } from 'node:url';

export function parseCsv(text) {
  const records = []; let row = [], field = '', quoted = false;
  text = text.replace(/^\uFEFF/, '');
  for (let i = 0; i < text.length; i++) {
    const c = text[i];
    if (c === '"') {
      if (quoted && text[i + 1] === '"') { field += '"'; i++; }
      else if (quoted || field === '') quoted = !quoted;
      else throw new Error('Comillas CSV inválidas');
    } else if (c === ',' && !quoted) { row.push(field); field = ''; }
    else if ((c === '\n' || c === '\r') && !quoted) {
      if (c === '\r' && text[i + 1] === '\n') i++;
      row.push(field); if (row.some(v => v !== '')) records.push(row);
      row = []; field = '';
    } else field += c;
  }
  if (quoted) throw new Error('Campo CSV sin cerrar');
  row.push(field); if (row.some(v => v !== '')) records.push(row);
  const headers = records.shift();
  if (!headers || new Set(headers).size !== headers.length) throw new Error('Cabecera inválida');
  for (const name of ['id', 'valor', 'estado', 'evidencia']) {
    if (!headers.includes(name)) throw new Error(`Falta columna ${name}`);
  }
  return records.map((values, i) => {
    if (values.length !== headers.length) throw new Error(`Fila ${i + 2}: número de columnas inválido`);
    return Object.fromEntries(headers.map((h, j) => [h, values[j].trim()]));
  });
}
const numeric = value => {
  if (!/^(?:\d+(?:[.,]\d+)?|[.,]\d+)$/.test(String(value))) return null;
  const n = Number(value.replace(',', '.')); return Number.isFinite(n) ? n : null;
};
const median = values => {
  if (!values.length) return null;
  const a = [...values].sort((x, y) => x - y), mid = Math.floor(a.length / 2);
  return a.length % 2 ? a[mid] : (a[mid - 1] + a[mid]) / 2;
};
export function summarize(rows, evidenceExists = () => false) {
  const accepted = [], rejected = [], seen = new Set();
  rows.forEach((r, i) => {
    if (r.estado === 'PENDIENTE' || r.estado === 'NO EJECUTADO') return;
    const fail = reason => rejected.push({ fila: i + 2, id: r.id, motivo: reason });
    if (!['MEDIDO', 'APROBADO', 'FALLÓ', 'FALLO'].includes(r.estado)) return fail('Estado no reconocido');
    if (!/^M(?:0[1-9]|10)$/.test(r.id)) return fail('Métrica desconocida');
    if (!r.evidencia || !evidenceExists(r.evidencia)) return fail('Evidencia local ausente');
    const key = [r.id, r.escenario, r.intento].join('|');
    if (seen.has(key)) return fail('Intento duplicado');
    let value;
    const enums = { M01: ['TP', 'FN'], M06: ['correcta', 'incorrecta'], M07: ['correcto', 'incorrecto'] };
    if (enums[r.id]) {
      value = r.id === 'M01' ? r.valor.toUpperCase() : r.valor.toLowerCase();
      if (!enums[r.id].includes(value)) return fail('Valor categórico inválido');
    } else {
      value = numeric(r.valor);
      if (value === null) return fail('Valor numérico inválido');
      if (['M02', 'M10'].includes(r.id) && !Number.isInteger(value)) return fail('El conteo debe ser entero');
      if (['M08', 'M09'].includes(r.id) && value > 100) return fail('Batería fuera de rango');
    }
    const hours = numeric(r.duracion_h ?? '');
    if (['M02', 'M08', 'M09'].includes(r.id) && !(hours > 0)) return fail('Falta duración positiva en duracion_h');
    seen.add(key); accepted.push({ ...r, value, hours });
  });
  const values = id => accepted.filter(r => r.id === id);
  const result = {};
  for (let i = 1; i <= 10; i++) {
    const id = `M${String(i).padStart(2, '0')}`, rr = values(id);
    let value = null, unit = '';
    if (['M01', 'M06', 'M07'].includes(id)) {
      const positive = { M01: 'TP', M06: 'correcta', M07: 'correcto' }[id];
      value = rr.length ? rr.filter(r => r.value === positive).length / rr.length * 100 : null; unit = '%';
    } else if (id === 'M02') {
      value = rr.length ? rr.reduce((s, r) => s + r.value, 0) / rr.reduce((s, r) => s + r.hours, 0) : null; unit = 'FP/h';
    } else if (['M03', 'M04', 'M05'].includes(id)) {
      value = median(rr.map(r => r.value)); unit = id === 'M05' ? 's' : 'ms';
    } else if (['M08', 'M09'].includes(id)) {
      value = rr.length ? rr.map(r => ({ puntos: r.value, horas: r.hours, puntos_por_hora: r.value / r.hours })) : null;
      unit = 'puntos porcentuales';
    } else { value = rr.length ? rr.reduce((s, r) => s + r.value, 0) : null; unit = 'incidentes'; }
    result[id] = { valor: value, unidad: unit, muestras: rr.length, estado: rr.length ? 'MEDIDO' : 'PENDIENTE', evidencias: rr.map(r => r.evidencia) };
  }
  return { metricas: result, rechazadas: rejected, advertencias: values('M01').length < 100 ? ['M01: faltan intentos para completar el protocolo de 100.'] : [] };
}
if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  try {
    const file = path.resolve(process.argv[2] || 'evidencias/mediciones.csv');
    const base = path.dirname(file);
    const report = summarize(parseCsv(fs.readFileSync(file, 'utf8')), name => {
      const target = path.resolve(base, name);
      return target.startsWith(base + path.sep) && fs.existsSync(target) && fs.statSync(target).isFile() && fs.statSync(target).size > 0;
    });
    console.log(JSON.stringify(report, null, 2));
    if (report.rechazadas.length) process.exitCode = 1;
  } catch (error) { console.error(error.message); process.exitCode = 1; }
}
