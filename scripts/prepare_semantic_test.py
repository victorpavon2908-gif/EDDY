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
    parser.add_argument('--jni-include', type=Path, help='JDK include directory when javac is not on PATH')
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
    # Only a standalone Linux run needs a core; CI reuses Sherpa's actual core.
    if not (native / 'libonnxruntime.so').exists():
        with zipfile.ZipFile(jar) as archive:
            with archive.open('ai/onnxruntime/native/linux-x64/libonnxruntime.so') as src, (native / 'libonnxruntime.so').open('wb') as dst:
                shutil.copyfileobj(src, dst)
    java_home = Path(os.environ['JAVA_HOME']) if os.environ.get('JAVA_HOME') else (Path(shutil.which('javac')).resolve().parents[1] if shutil.which('javac') else None)
    jni_include = args.jni_include or (java_home / 'include' if java_home else None)
    if not jni_include or not (jni_include / 'jni.h').is_file():
        raise RuntimeError('JDK JNI headers required: install JDK 17 or supply --jni-include')
    subprocess.run([
        'c++', '-std=c++17', '-shared', '-fPIC', '-O2', '-Wl,-z,defs',
        '-I' + str(jni_include), '-I' + str(jni_include / 'linux'),
        '-I' + str(ROOT / 'app/src/main/cpp/include'),
        str(ROOT / 'app/src/main/cpp/leo_semantic_jni.cpp'), '-ldl', '-pthread',
        '-o', str(native / 'libleo_semantic_jni.so'),
    ], check=True)
    settings = f'LEO_EMBEDDING_MODELS={root}\nLEO_ORT_NATIVE_DIR={native}\n'
    if args.github_env:
        with args.github_env.open('a') as out:
            out.write(settings)
    print(settings, end='')


if __name__ == '__main__':
    main()
