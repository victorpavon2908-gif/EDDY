"""Bundle a licensed Spanish Piper voice with the APK. Fail the build if it is missing.

Source: sherpa-onnx prepackaged Piper davefx voice (CC0).
Files are downloaded only by the build machine; the installed app never downloads TTS.
"""
from pathlib import Path
import hashlib
import subprocess
import tarfile

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "app/src/main/assets/leo-tts"
URL = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-es_ES-davefx-medium.tar.bz2"
MODEL = "vits-piper-es_ES-davefx-medium"
FILES = (
    MODEL + "/es_ES-davefx-medium.onnx",
    MODEL + "/tokens.txt",
)

def main():
    ASSETS.mkdir(parents=True, exist_ok=True)
    archive = ASSETS / "source.tar.bz2"
    if not archive.exists():
        subprocess.run(
            ["curl", "--fail", "--location", "--retry", "3", "--connect-timeout", "20",
             "--max-time", "600", "--output", str(archive), URL], check=True,
        )
    with tarfile.open(archive, "r:bz2") as tar:
        members = tar.getmembers()
        for item in members:
            path = Path(item.name)
            if path.is_absolute() or ".." in path.parts or item.issym() or item.islnk():
                raise RuntimeError("Unsafe archive member")
        tar.extractall(ASSETS, filter="data")
    archive.unlink()
    for rel in FILES:
        dest = ASSETS / rel
        if not dest.is_file() or dest.stat().st_size < 100:
            raise RuntimeError("Missing bundled TTS file: " + rel)
        print(rel, dest.stat().st_size, hashlib.sha256(dest.read_bytes()).hexdigest())
    espeak = ASSETS / MODEL / "espeak-ng-data"
    if not espeak.is_dir() or not any(espeak.iterdir()):
        raise RuntimeError("Missing espeak-ng phonemizer assets")
    print("Bundled offline voice ready")

if __name__ == "__main__":
    main()
