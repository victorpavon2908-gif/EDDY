import fs from "node:fs";

const file = process.argv[2] || "evidencias/mediciones.csv";
const text = fs.readFileSync(file, "utf8").trim();
const lines = text.split(/\r?\n/);
const headers = lines.shift().split(",");
const rows = lines.map(line => {
  const values = line.split(",");
  return Object.fromEntries(headers.map((h, i) => [h, values[i] ?? ""]));
});

const real = rows.filter(r => r.estado && r.estado !== "PENDIENTE" && r.valor !== "");

function num(v) {
  const n = Number(String(v).replace(",", "."));
  return Number.isFinite(n) ? n : null;
}

function median(values) {
  const a = values.filter(v => v !== null).sort((x,y)=>x-y);
  if (!a.length) return null;
  const m = Math.floor(a.length/2);
  return a.length % 2 ? a[m] : (a[m-1] + a[m]) / 2;
}

const activation = real.filter(r => r.id === "M01");
const tp = activation.filter(r => r.valor.toUpperCase() === "TP").length;
const fn = activation.filter(r => r.valor.toUpperCase() === "FN").length;
const activationRate = tp + fn ? (tp/(tp+fn))*100 : null;

const latency = median(real.filter(r => r.id === "M03").map(r => num(r.valor)));
const fp = real.filter(r => r.id === "M02").reduce((s,r)=>s+(num(r.valor) ?? 0),0);
const interruption = median(real.filter(r => r.id === "M04").map(r => num(r.valor)));
const search = median(real.filter(r => r.id === "M05").map(r => num(r.valor)));
const sourceRows = real.filter(r => r.id === "M06");
const sourceOk = sourceRows.filter(r => /correcta/i.test(r.valor) && !/incorrecta/i.test(r.valor)).length;
const sourcePct = sourceRows.length ? sourceOk/sourceRows.length*100 : null;
const waRows = real.filter(r => r.id === "M07");
const waOk = waRows.filter(r => /^correcto$/i.test(r.valor)).length;
const waPct = waRows.length ? waOk/waRows.length*100 : null;
const batteryIdle = real.filter(r => r.id === "M08").map(r => num(r.valor)).filter(v=>v!==null);
const batteryMixed = real.filter(r => r.id === "M09").map(r => num(r.valor)).filter(v=>v!==null);
const incidents = real.filter(r => r.id === "M10").reduce((s,r)=>s+(num(r.valor) ?? 0),0);

console.log("# Resumen cuantitativo");
console.log(`M01 Activación: ${activationRate === null ? "PENDIENTE" : activationRate.toFixed(2)+"%"} (TP=${tp}, FN=${fn})`);
console.log(`M02 Falsos positivos registrados: ${real.some(r=>r.id==="M02") ? fp : "PENDIENTE"}`);
console.log(`M03 Latencia mediana: ${latency === null ? "PENDIENTE" : latency+" ms"}`);
console.log(`M04 Interrupción mediana: ${interruption === null ? "PENDIENTE" : interruption+" ms"}`);
console.log(`M05 Búsqueda mediana: ${search === null ? "PENDIENTE" : search+" s"}`);
console.log(`M06 Fuentes correctas: ${sourcePct === null ? "PENDIENTE" : sourcePct.toFixed(2)+"%"}`);
console.log(`M07 WhatsApp correcto: ${waPct === null ? "PENDIENTE" : waPct.toFixed(2)+"%"}`);
console.log(`M08 Consumo reposo: ${batteryIdle.length ? batteryIdle.join(", ")+" puntos" : "PENDIENTE"}`);
console.log(`M09 Consumo uso mixto: ${batteryMixed.length ? batteryMixed.join(", ")+" puntos" : "PENDIENTE"}`);
console.log(`M10 Incidentes: ${real.some(r=>r.id==="M10") ? incidents : "PENDIENTE"}`);
