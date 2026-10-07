"""Fetch pinned real model and Java JNI for tests; does not build or package an APK."""
import argparse
import hashlib
import os
from pathlib import Path
import re
import shutil
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def download(url, output):
    if output.exists():
        return
    part = output.with_suffix(output.suffix + '.part')
    subprocess.run(['curl', '--fail', '--location', '--silent', '--show-error', '--retry', '3',
                    '--max-time', '240', url, '-o', str(part)], check=True)
    part.replace(output)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--destination', type=Path, required=True)
    parser.add_argument('--github-env', type=Path)
    args = parser.parse_args()
    root = args.destination.resolve()
    root.mkdir(parents=True, exist_ok=True)
    source = (ROOT / 'app/src/main/java/com/niko/assistant/memory/embedding/LeoEmbeddingModel.kt').read_text()
    revision = re.search(r'REVISION = "([a-f0-9]+)"', source)[1]
    repository = re.search(r'REPOSITORY = "([^"]+)"', source)[1]
    artifacts = re.findall(r'Artifact\("([^"]+)", "([^"]+)", (\d+), "([a-f0-9]+)"\)', source)
    assert len(artifacts) == 3
    for remote, name, size, digest in artifacts:
        path = root / name
        download(f'https://huggingface.co/{repository}/resolve/{revision}/{remote}', path)
        assert path.stat().st_size == int(size), name
        assert hashlib.sha256(path.read_bytes()).hexdigest() == digest, name
        print(f'{name}: SHA-256 verified')
    jar = root / 'onnxruntime.jar'
    download('https://repo.maven.apache.org/maven2/com/microsoft/onnxruntime/onnxruntime/1.22.0/onnxruntime-1.22.0.jar', jar)
    native = Path(os.environ.get('NIKO_NATIVE_LIB_DIR', root / 'native'))
    native.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(jar) as archive:
        for name in ('libonnxruntime4j_jni.so', 'libonnxruntime.so'):
            # Preserve Sherpa's runtime when present, as production Android does.
            target = native / name
            if not target.exists():
                with archive.open('ai/onnxruntime/native/linux-x64/' + name) as src, target.open('wb') as dst:
                    shutil.copyfileobj(src, dst)
    settings = f'LEO_EMBEDDING_MODELS={root}\nLEO_ORT_NATIVE_DIR={native}\n'
    if args.github_env:
        with args.github_env.open('a') as out:
            out.write(settings)
    print(settings, end='')


if __name__ == '__main__':
    main()
