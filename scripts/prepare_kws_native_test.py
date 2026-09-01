"""Prepare Linux JNI + the production KWS model for tests. Never builds/packages an APK."""
import argparse
from pathlib import Path
import re
import tarfile
import urllib.request

ROOT = Path(__file__).resolve().parents[1]


def unpack(url: str, destination: Path) -> None:
    archive = destination / url.rsplit("/", 1)[1]
    if not archive.is_file():
        partial = archive.with_suffix(".part")
        with urllib.request.urlopen(url, timeout=90) as response, partial.open("wb") as output:
            while chunk := response.read(1024 * 1024):
                output.write(chunk)
        partial.replace(archive)
    with tarfile.open(archive, "r:bz2") as source:
        source.extractall(destination, filter="data")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--destination", type=Path, required=True)
    parser.add_argument("--github-env", type=Path)
    args = parser.parse_args()
    catalog = (ROOT / "app/src/main/java/com/niko/assistant/localai/NikoModelCatalog.kt").read_text()
    version = re.search(r'SHERPA_VERSION = "([^"]+)"', catalog).group(1)
    keyword = re.search(r'val keyword = NikoModelSpec\((.*?)\n    \)', catalog, re.S).group(1)
    model_url = re.search(r'url = "([^"]+)"', keyword).group(1)
    destination = args.destination.resolve()
    destination.mkdir(parents=True, exist_ok=True)
    unpack(f"https://github.com/k2-fsa/sherpa-onnx/releases/download/v{version}/sherpa-onnx-v{version}-linux-x64-jni.tar.bz2", destination)
    unpack(model_url, destination)
    libraries = destination / f"sherpa-onnx-v{version}-linux-x64-jni/lib"
    if not (libraries / "libsherpa-onnx-jni.so").is_file():
        raise RuntimeError("JNI library missing")
    settings = f"NIKO_NATIVE_KWS_MODELS={destination}\nNIKO_NATIVE_LIB_DIR={libraries}\n"
    if args.github_env:
        with args.github_env.open("a") as output:
            output.write(settings)
    print(settings, end="")


if __name__ == "__main__":
    main()
