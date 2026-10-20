#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
leo_bundle=${1:?Pass the candidate bundle path}
leo_lib=${LEO_KOTLIN_LIB_DIR:-${HOME}/niko-bootstrap/gradle-8.13/lib}
leo_temp=$(mktemp -d)
trap 'rm -rf -- "$leo_temp"' EXIT
python - "$leo_temp/cases.tsv" <<'PY'
import json,sys
from pathlib import Path
cases=json.loads(Path('scripts/leo_neural_eval.json').read_text())
old=json.loads(Path('scripts/leo_microgpt_corpus.json').read_text())
cases += [(key, value['prompts'][0], '') for key,value in old.items()]
Path(sys.argv[1]).write_text(''.join(f'{family}\t{prompt}\n' for family,prompt,_ in cases))
PY
java -cp "$leo_lib/*" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
  -no-stdlib -no-reflect -jvm-target 17 -classpath "$leo_lib/kotlin-stdlib-2.0.21.jar" \
  -d "$leo_temp/eval.jar" \
  app/src/main/java/com/niko/assistant/localai/LeoMicroGptCore.kt \
  app/src/main/java/com/niko/assistant/localai/LeoMicroGptGate.kt scripts/EvaluateLeoCandidate.kt
java -cp "$leo_temp/eval.jar:$leo_lib/kotlin-stdlib-2.0.21.jar" EvaluateLeoCandidateKt "$leo_bundle" "$leo_temp/cases.tsv"
