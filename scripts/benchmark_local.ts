import { performance } from 'node:perf_hooks';
import { LocalBrain } from '../src/services/localBrain.ts';
const cases = [
  ['Leo, pará','stop'],['qué hora es','tell_time'],['abre calculadora','open_tool'],
  ['estado de batería','battery_status'],['apaga la linterna','torch'],['hola','greeting'],
  ['2 + 3','math'],['baila','robot_motion'],['no bailes','unknown'],['qué es fotosíntesis','search_web'],
] as const;
for (let i=0;i<1000;i++) LocalBrain.understand(cases[i%cases.length][0]);
const durations:number[] = []; let correct=0;
for (let i=0;i<10000;i++) {
  const [input,expected]=cases[i%cases.length]; const start=performance.now();
  const result=LocalBrain.understand(input); durations.push(performance.now()-start);
  if(result.type===expected) correct++;
}
durations.sort((a,b)=>a-b);
console.log(JSON.stringify({scope:'Enrutador TypeScript en Node; entradas textuales fijas, NO micrófono ni latencia Android',cases:cases.length,iterations:durations.length,correct,accuracy_percent:correct/durations.length*100,median_ms:(durations[4999]+durations[5000])/2,p95_ms:durations[Math.ceil(.95*durations.length)-1]},null,2));
