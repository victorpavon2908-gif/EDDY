#!/usr/bin/env bash
# Rebuild or verify LEO's deterministic on-device intent checkpoint.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."

leo_kotlin_lib="${LEO_KOTLIN_LIB_DIR:-${GRADLE_USER_HOME:-${HOME}}/niko-bootstrap/gradle-8.13/lib}"
leo_asset="app/src/main/assets/leo-intent-network-v1.bin"
leo_mode="${1:---write}"
if [[ "$leo_mode" != "--write" && "$leo_mode" != "--check" ]]; then
    printf 'Uso: %s [--write|--check]\n' "$0" >&2
    exit 2
fi

leo_training_dir=$(mktemp -d)
trap 'rm -f -- "$leo_training_dir/trainer.jar" "$leo_training_dir/leo-intent-network.bin"; rmdir -- "$leo_training_dir"' EXIT
leo_classpath="$leo_kotlin_lib/kotlin-stdlib-2.0.21.jar"
java -cp "$leo_kotlin_lib/*" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
    -no-stdlib -no-reflect -jvm-target 17 -classpath "$leo_classpath" \
    -d "$leo_training_dir/trainer.jar" \
    app/src/main/java/com/niko/assistant/memory/MemoryLearning.kt \
    app/src/main/java/com/niko/assistant/learning/AdaptiveLearningPolicy.kt \
    app/src/main/java/com/niko/assistant/learning/LeoIntentTrainingCorpus.kt \
    app/src/main/java/com/niko/assistant/learning/OnlineIntentNetwork.kt \
    scripts/TrainLeoIntents.kt
java -cp "$leo_training_dir/trainer.jar:$leo_classpath" \
    com.niko.assistant.training.TrainLeoIntentsKt "$leo_training_dir/leo-intent-network.bin"

if [[ "$leo_mode" == "--check" ]]; then
    cmp --silent "$leo_training_dir/leo-intent-network.bin" "$leo_asset" || {
        printf '%s no coincide con el corpus actual. Ejecutá %s --write.\n' "$leo_asset" "$0" >&2
        exit 1
    }
    printf 'Modelo de intención reproducible verificado.\n'
else
    install -D -m 0644 "$leo_training_dir/leo-intent-network.bin" "$leo_asset"
    printf 'Modelo de intención actualizado: %s\n' "$leo_asset"
fi
