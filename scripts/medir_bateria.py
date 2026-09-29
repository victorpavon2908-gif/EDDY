#!/usr/bin/env python3
"""Read-only Android battery capture. Never simulates battery or resets statistics."""
import argparse
import datetime as dt
import hashlib
import json
from pathlib import Path
import re
import subprocess
import time


def parse_battery(raw):
    fields = dict(re.findall(r'^\s*([\w ]+):\s*(.*?)\s*$', raw, re.M))
    level, scale = int(fields['level']), int(fields['scale'])
    if scale <= 0 or not 0 <= level <= scale:
        raise ValueError('Nivel de batería inválido')
    return {'percent': level / scale * 100,
            'powered': any(fields.get(k) == 'true' for k in ('AC powered', 'USB powered', 'Wireless powered', 'Dock powered')),
            'temperature_tenths_c': fields.get('temperature'),
            'simulated': 'UPDATES STOPPED' in raw.upper()}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial', required=True, help='Serial de adb devices; usar depuración inalámbrica para medir sin cargar')
    parser.add_argument('--modo', choices=['reposo', 'mixto'], required=True)
    parser.add_argument('--minutos', type=float, help='Por defecto: reposo 60, mixto 30')
    parser.add_argument('--salida', default='evidencias/dispositivo')
    args = parser.parse_args()
    minutes = args.minutos if args.minutos is not None else (60 if args.modo == 'reposo' else 30)
    if not 0 < minutes <= 1440:
        parser.error('Duración debe estar entre 0 y 1440 minutos')
    def adb(*command):
        return subprocess.run(['adb', '-s', args.serial, *command], check=True, capture_output=True, text=True, timeout=30).stdout
    adb('get-state')
    initial = adb('shell', 'dumpsys', 'battery')
    battery = parse_battery(initial)
    if battery['powered'] or battery['simulated']:
        raise RuntimeError('Desconecte el cargador y desactive simulación de batería antes de medir; use ADB inalámbrico.')
    stamp = dt.datetime.now(dt.timezone.utc).strftime('%Y%m%dT%H%M%SZ')
    folder = Path(args.salida) / f'{stamp}_{args.modo}'
    folder.mkdir(parents=True, exist_ok=False)
    metadata = {'inicio_utc': stamp, 'modo': args.modo, 'minutos_objetivo': minutes,
                'modelo': adb('shell', 'getprop', 'ro.product.model').strip(),
                'android': adb('shell', 'getprop', 'ro.build.version.release').strip(),
                'alcance': 'Descenso total del dispositivo; no consumo exclusivo de LEO.'}
    (folder / 'entorno.json').write_text(json.dumps(metadata, indent=2), encoding='utf-8')
    start = time.monotonic(); samples = []; reason = None
    try:
        while True:
            raw = initial if not samples else adb('shell', 'dumpsys', 'battery')
            current = parse_battery(raw)
            elapsed = time.monotonic() - start
            filename = f'bateria_{len(samples):04d}.txt'
            (folder / filename).write_text(raw, encoding='utf-8')
            samples.append({'segundos': elapsed, **current, 'evidencia': filename})
            (folder / 'muestras.json').write_text(json.dumps(samples, indent=2), encoding='utf-8')
            if current['powered'] or current['simulated']:
                reason = 'Se detectó carga o simulación durante la sesión'; break
            if current['percent'] > battery['percent']:
                reason = 'La batería aumentó durante la sesión'; break
            if elapsed >= minutes * 60:
                break
            time.sleep(min(30, minutes * 60 - elapsed))
    except (KeyboardInterrupt, Exception) as exc:
        reason = f'Sesión incompleta: {type(exc).__name__}: {exc}'
    hours = samples[-1]['segundos'] / 3600 if samples else 0
    delta = battery['percent'] - samples[-1]['percent'] if samples else None
    report = {'estado': 'INVALIDO' if reason else 'MEDIDO', 'motivo': reason,
              'id': 'M08' if args.modo == 'reposo' else 'M09', 'duracion_h': hours,
              'puntos_bateria': delta, 'puntos_por_hora': delta / hours if hours else None,
              'advertencia': 'Resolución limitada al nivel de batería reportado por Android. Cero descenso no significa cero consumo.'}
    (folder / 'resultado.json').write_text(json.dumps(report, indent=2), encoding='utf-8')
    hashes = {p.name: hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(folder.iterdir()) if p.is_file()}
    (folder / 'sha256.json').write_text(json.dumps(hashes, indent=2), encoding='utf-8')
    print(json.dumps(report, indent=2)); print(folder)
    return 1 if reason else 0


if __name__ == '__main__':
    raise SystemExit(main())
