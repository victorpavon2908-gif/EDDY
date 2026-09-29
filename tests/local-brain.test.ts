import test from 'node:test';
import assert from 'node:assert/strict';
import { LocalBrain } from '../src/services/localBrain.ts';
for (const [input, expected] of [
  ['2 + 3','5'],['2.5 + 1.2','3.7'],['2,5 + 1,2','3.7'],['10 dividido por 2','5'],
  ['3 multiplicado por 4','12'],['20 por ciento de 150','30'],['20% de 150','30'],
  ['raíz cuadrada de 81','9'],['(2 + 3) * 4','20'],['2 elevado a 3','8'],
  ['cuánto es 7 menos 2','5'],['1 / 0',null],['hola 123',null],['raíz de 9 + 2',null],
] as const) test(`math: ${input}`, () => assert.equal(LocalBrain.evaluateMath(input), expected));
for (const [input, type] of [
  ['Leo, pará','stop'],['Leo, qué hora es','tell_time'],['abre calculadora','open_tool'],
  ['cómo está la batería','battery_status'],['apaga la linterna','torch'],
  ['hola','greeting'],['2 + 3','math'],['Leo, baila','robot_motion'],['no bailes','unknown'],
  ['Leonardo da Vinci','unknown'],
]) test(`routing: ${input}`, () => assert.equal(LocalBrain.understand(input).type,type));
test('wake word does not truncate Leonardo', () => assert.equal(LocalBrain.understand('Leonardo da Vinci').payload?.query,'Leonardo da Vinci'));
