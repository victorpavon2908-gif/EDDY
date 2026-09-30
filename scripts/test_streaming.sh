#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
leo_kotlin_lib="${LEO_KOTLIN_LIB_DIR:-${GRADLE_USER_HOME:-${HOME}}/niko-bootstrap/gradle-8.13/lib}"
leo_json_jar="${LEO_JSON_JAR:?Set LEO_JSON_JAR to org.json:json:20250517 jar}"
leo_test_dir=$(mktemp -d)
trap 'rm -rf -- "$leo_test_dir"' EXIT
leo_classpath="$leo_json_jar:$leo_kotlin_lib/kotlin-stdlib-2.0.21.jar:$leo_kotlin_lib/junit-4.13.2.jar:$leo_kotlin_lib/hamcrest-core-1.3.jar:$leo_kotlin_lib/kotlinx-coroutines-core-jvm-1.6.4.jar"
leo_units=(GroqSseReader GroqStreaming GroqHttpStreaming GroqGateway GroqProtocol GroqConversation)
leo_sources=(app/src/main/java/com/niko/assistant/memory/MemoryLearning.kt)
leo_tests=()
for unit in "${leo_units[@]}"; do
    if [[ "$unit" != GroqStreaming && "$unit" != GroqHttpStreaming ]]; then leo_sources+=("app/src/main/java/com/niko/assistant/ai/$unit.kt"); fi
    leo_sources+=("app/src/test/java/com/niko/assistant/ai/${unit}Test.kt")
    leo_tests+=("com.niko.assistant.ai.${unit}Test")
done
for unit in GroqStreamTransport GroqHttpClient NikoAiReply ConversationContext NikoPersonality NikoIdentity LeoBrand; do
    leo_sources+=("app/src/main/java/com/niko/assistant/ai/$unit.kt")
done
java -cp "$leo_kotlin_lib/*" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
    -no-stdlib -no-reflect -jvm-target 17 -classpath "$leo_classpath" \
    -d "$leo_test_dir/tests.jar" "${leo_sources[@]}"
java -cp "$leo_test_dir/tests.jar:$leo_classpath" org.junit.runner.JUnitCore "${leo_tests[@]}"
