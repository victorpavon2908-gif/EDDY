#!/usr/bin/env python3
"""Execute repeatable software checks and retain raw logs and SHA-256 provenance."""
import datetime as dt
import hashlib
import json
from pathlib import Path
import platform
import subprocess
import time
root = Path(__file__).resolve().parents[1]
out = root / 'evidencias' / 'ejecucion_software'
out.mkdir(parents=True, exist_ok=True)
commands = {
    'tests_web': ['node', '--import', 'tsx', '--test', '--test-reporter=tap', 'tests/local-brain.test.ts', 'tests/metrics.test.mjs'],
    'tests_bateria': ['python3', '-m', 'unittest', 'discover', '-s', 'tests', '-p', 'test_*.py', '-v'],
    'typescript': ['npm', 'run', 'lint'],
    'build_web': ['npm', 'run', 'build'],
    'benchmark_texto': ['node', '--import', 'tsx', 'scripts/benchmark_local.ts'],
    'metricas_fisicas': ['node', 'scripts/calcular_metricas.mjs'],
    'microgpt_assets': ['python3', 'scripts/validate_leo_microgpt_assets.py'],
    'android_ai_core': ['bash', 'scripts/test_ai_core.sh'],
    'android_voice': ['bash', 'scripts/test_voice_fidelity.sh'],
}
report = {'fecha_utc': dt.datetime.now(dt.timezone.utc).isoformat(),
          'commit_base': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=root, text=True).strip(),
          'nota': 'Ejecución sobre árbol de trabajo; hashes identifican fuentes exactas. Pruebas automatizadas no certifican métricas físicas M01–M10.',
          'entorno': {'platform': platform.platform(), 'python': platform.python_version(), 'node': subprocess.check_output(['node','--version'],text=True).strip()}, 'comprobaciones': {}}
for name, command in commands.items():
    start = time.perf_counter()
    try:
        r = subprocess.run(command, cwd=root, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, timeout=180)
        code, log = r.returncode, r.stdout
    except subprocess.TimeoutExpired as exc:
        code, log = 124, str(exc)
    (out / f'{name}.log').write_text(log, encoding='utf-8')
    report['comprobaciones'][name] = {'comando': command, 'exit_code': code, 'segundos': time.perf_counter()-start, 'log': f'{name}.log'}
    print(name, code, flush=True)
paths = [p for base in ['src','scripts','tests'] for p in (root/base).rglob('*') if p.is_file() and '__pycache__' not in p.parts]
paths += [root/'package.json',root/'package-lock.json']
report['sha256_fuentes'] = {str(p.relative_to(root)):hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(paths)}
report['sha256_logs'] = {p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(out.glob('*.log'))}
(out/'resultados.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
