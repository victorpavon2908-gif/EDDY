"""Package only the offline voice core; no LLM, speaker biometrics or optional TTS.

Run before assembleDebug. Model IDs/URLs come from the Kotlin catalog, so a
catalog revision cannot silently ship stale assets. Large binaries stay out of git.
"""
from pathlib import Path
import hashlib
import re
import subprocess

ROOT = Path(__file__).resolve().parents[1]
CATALOG = ROOT / 'app/src/main/java/com/eddy/assistant/localai/EddyModelCatalog.kt'
OUTPUT = ROOT / 'app/src/main/assets/voice-core'


def main():
    source = CATALOG.read_text()
    OUTPUT.mkdir(parents=True, exist_ok=True)
    for name in ('keyword', 'vad', 'spanishAsr'):
        block = re.search(r'val ' + name + r' = EddyModelSpec\((.*?)\n    \)', source, re.S).group(1)
        model_id = re.search(r'id = "([^"]+)"', block).group(1)
        url = re.search(r'url = "([^"]+)"', block).group(1)
        minimum = int(re.search(r'minBytes = ([\d_]+)L', block).group(1).replace('_', ''))
        destination = OUTPUT / (model_id + '.bundle')
        if not destination.exists():
            temporary = destination.with_suffix('.part')
            subprocess.run(['curl', '--fail', '--location', '--retry', '3', '--connect-timeout', '20',
                            '--max-time', '600', '--output', str(temporary), url], check=True)
            if temporary.stat().st_size < minimum:
                raise RuntimeError(f'{name}: incomplete archive')
            temporary.replace(destination)
        if destination.stat().st_size < minimum:
            raise RuntimeError(f'{name}: incomplete cached archive')
        with destination.open('rb') as stream:
            digest = hashlib.file_digest(stream, 'sha256').hexdigest()
        destination.with_suffix('.sha256').write_text(digest)
        print(f'{name}: {destination.stat().st_size:,} bytes, sha256={digest}')


if __name__ == '__main__':
    main()
