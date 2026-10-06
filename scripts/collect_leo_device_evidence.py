#!/usr/bin/env python3
"""Read-only Android evidence collector. Capturing data never implies a passed physical test."""
import argparse
import datetime as dt
import hashlib
import json
from pathlib import Path
import subprocess
import time

COMMANDS = {
    'identity': ['shell', 'getprop'],
    'package': ['shell', 'dumpsys', 'package', 'com.eddy.assistant'],
    'battery': ['shell', 'dumpsys', 'battery'],
    'cpu': ['shell', 'dumpsys', 'cpuinfo'],
    'memory': ['shell', 'dumpsys', 'meminfo', 'com.eddy.assistant'],
    'thermal': ['shell', 'dumpsys', 'thermalservice'],
    'audio': ['shell', 'dumpsys', 'audio'],
    'power': ['shell', 'dumpsys', 'power'],
    'batterystats': ['shell', 'dumpsys', 'batterystats', 'com.eddy.assistant'],
    'voice_diagnostics': ['exec-out', 'run-as', 'com.eddy.assistant', 'cat',
                          'shared_prefs/leo_voice_diagnostics_v1.xml'],
}
SCENARIOS = ('idle', 'conversation', 'wake', 'false-positive', 'asr', 'tts', 'barge-in',
             'screen-off', 'bluetooth', 'noise', 'distance', 'call', 'music')


def capture(adb, serial, args, destination, runner=subprocess.run):
    command = [adb, '-s', serial] + args
    started = time.monotonic()
    try:
        result = runner(command, capture_output=True, timeout=30, check=False)
        output = result.stdout + b'\n--- STDERR ---\n' + result.stderr
        code, problem = result.returncode, None
    except (subprocess.TimeoutExpired, OSError) as error:
        output = str(error).encode('utf-8')
        code, problem = None, type(error).__name__
    destination.write_bytes(output)
    return {'command': command, 'exit_code': code, 'problem': problem,
            'duration_seconds': time.monotonic() - started, 'file': destination.name,
            'sha256': hashlib.sha256(output).hexdigest()}


def collect(adb, serial, scenario, out, duration, interval, runner=subprocess.run):
    out.mkdir(parents=True, exist_ok=False)
    manifest = {'schema': 1, 'scenario': scenario, 'serial': serial,
                'started_utc': dt.datetime.now(dt.timezone.utc).isoformat(),
                'status': 'PENDIENTE DE VALIDACIÓN FÍSICA', 'capture_complete': False,
                'interpretation': 'Archivos crudos; requiere revisión humana. No calcula éxito, FPS, precisión ni consumo exclusivo de LEO.',
                'records': [], 'observations': []}
    start = time.monotonic()
    def save():
        manifest['elapsed_seconds'] = time.monotonic() - start
        (out / 'manifest.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding='utf-8')
    save()
    try:
        for name in ('identity', 'package'):
            manifest['records'].append(capture(adb, serial, COMMANDS[name], out / f'{name}.txt', runner))
            save()
        index = 0
        while True:
            for name in ('battery', 'cpu', 'memory', 'thermal', 'audio', 'power'):
                record = capture(adb, serial, COMMANDS[name], out / f'{index:04d}_{name}.txt', runner)
                record['sample_elapsed_seconds'] = time.monotonic() - start
                manifest['records'].append(record)
                save()
            if time.monotonic() - start >= duration:
                break
            time.sleep(min(interval, max(0, duration - (time.monotonic() - start))))
            index += 1
        for name in ('batterystats', 'voice_diagnostics'):
            manifest['records'].append(capture(adb, serial, COMMANDS[name], out / f'{name}.txt', runner))
        manifest['capture_complete'] = True
        manifest['all_commands_succeeded'] = all(r['exit_code'] == 0 for r in manifest['records'])
    except KeyboardInterrupt:
        manifest['interrupted'] = True
    finally:
        save()
    return manifest


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--adb', default='adb')
    parser.add_argument('--serial', required=True, help='Exact authorized adb device serial')
    parser.add_argument('--scenario', choices=SCENARIOS, required=True)
    parser.add_argument('--output', type=Path, required=True, help='New directory; existing evidence is never overwritten')
    parser.add_argument('--duration', type=float, default=60)
    parser.add_argument('--interval', type=float, default=15)
    args = parser.parse_args()
    if args.duration < 0 or args.interval < 1:
        parser.error('duration must be nonnegative; interval must be >= 1 second')
    result = collect(args.adb, args.serial, args.scenario, args.output, args.duration, args.interval)
    print(json.dumps({'status': result['status'], 'capture_complete': result['capture_complete'], 'output': str(args.output)}, ensure_ascii=False))
    return 0 if result['capture_complete'] else 1


if __name__ == '__main__':
    raise SystemExit(main())
