#!/usr/bin/env bash
# Runs production Kotlin inference against real trained weights on Linux, not a phone.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
: "${LEO_EMBEDDING_MODELS:?Run prepare_semantic_test.py and set LEO_EMBEDDING_MODELS}"
: "${LEO_JSON_JAR:?Set LEO_JSON_JAR to org.json 20250517 jar}"
leo_kotlin_lib="${LEO_KOTLIN_LIB_DIR:-${GRADLE_USER_HOME:-${HOME}}/niko-bootstrap/gradle-8.13/lib}"
leo_test_dir=$(mktemp -d)
trap 'rm -rf -- "$leo_test_dir"' EXIT
leo_classpath="$leo_kotlin_lib/kotlin-stdlib-2.0.21.jar:$leo_kotlin_lib/junit-4.13.2.jar:$leo_kotlin_lib/hamcrest-core-1.3.jar:$LEO_JSON_JAR"
java -cp "$leo_kotlin_lib/*" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
 -no-stdlib -no-reflect -jvm-target 17 -classpath "$leo_classpath" -d "$leo_test_dir/tests.jar" \
 app/src/main/java/com/niko/assistant/memory/embedding/LeoOrtBridge.kt \
 app/src/main/java/com/niko/assistant/memory/embedding/LeoEmbeddingModel.kt \
 app/src/main/java/com/niko/assistant/memory/embedding/LeoWordPiece.kt \
 app/src/main/java/com/niko/assistant/memory/embedding/LeoSemanticEncoder.kt \
 app/src/test/java/com/niko/assistant/memory/embedding/LeoWordPieceTest.kt \
 app/src/test/java/com/niko/assistant/memory/embedding/LeoSemanticEncoderTest.kt
leo_native_dir="${LEO_ORT_NATIVE_DIR:-$LEO_EMBEDDING_MODELS/native}"
LD_LIBRARY_PATH="$leo_native_dir${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}" java -Djava.library.path="$leo_native_dir" \
 -cp "$leo_test_dir/tests.jar:$leo_classpath" org.junit.runner.JUnitCore \
 com.niko.assistant.memory.embedding.LeoWordPieceTest com.niko.assistant.memory.embedding.LeoSemanticEncoderTest
