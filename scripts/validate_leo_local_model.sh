#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
leo_root=build/leo-local-validation
mkdir -p "$leo_root"
cmake -S app/src/main/cpp -B "$leo_root/native" -DCMAKE_BUILD_TYPE=Release
cmake --build "$leo_root/native" --target leo_llama_jni -j 2
leo_model="${1:-compact}"
python - "$leo_root" "$leo_model" <<'PY'
import hashlib, pathlib, sys, urllib.request
root=pathlib.Path(sys.argv[1]); path=root/'model.gguf'
assert sys.argv[2] in ('compact', 'large')
size=1834426016
expected='061b54daade076b5d3362dac252678d17da8c68f07560be70818cace6590cb1a'
url='https://huggingface.co/Qwen/Qwen3-1.7B-GGUF/resolve/90862c4b9d2787eaed51d12237eafdfe7c5f6077/Qwen3-1.7B-Q8_0.gguf'
if sys.argv[2] == 'large':
    url='https://huggingface.co/Qwen/Qwen3-4B-GGUF/resolve/bc640142c66e1fdd12af0bd68f40445458f3869b/Qwen3-4B-Q8_0.gguf'
    size=4280404704
    expected='8c2f07f26af9747e41988551106f149b03eb9b5cb6df636027b6bf6278473300'
if path.exists() and path.stat().st_size != size: path.unlink()
if not path.exists(): urllib.request.urlretrieve(url,path)
assert path.stat().st_size == size
with path.open('rb') as f: actual=hashlib.file_digest(f,'sha256').hexdigest()
assert actual == expected
print('VERIFIED MODEL SHA256',actual)
PY
./gradlew --version
leo_lib="${GRADLE_USER_HOME:-${HOME}}/niko-bootstrap/gradle-8.13/lib"
java -cp "$leo_lib/*" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -jvm-target 17 \
 -classpath "$leo_lib/kotlin-stdlib-2.0.21.jar" -d "$leo_root/probes.jar" \
 app/src/main/java/com/niko/assistant/inference/local/LeoLlamaBridge.kt \
 app/src/main/java/com/niko/assistant/inference/local/LeoLocalPrompt.kt scripts/ValidateLeoLocalModel.kt
java -Djava.library.path="$leo_root/native" -cp "$leo_root/probes.jar:$leo_lib/kotlin-stdlib-2.0.21.jar" ValidateLeoLocalModelKt "$leo_root/model.gguf" 2> "$leo_root/native.log" | tee "$leo_root/results.txt"
