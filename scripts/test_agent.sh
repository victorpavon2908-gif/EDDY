#!/usr/bin/env bash
# Actual JVM behavior tests. No Android hardware claims; use Android CI for integration.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
leo_kotlin_lib="${LEO_KOTLIN_LIB_DIR:-${GRADLE_USER_HOME:-${HOME}}/niko-bootstrap/gradle-8.13/lib}"
leo_test_dir=$(mktemp -d)
trap 'rm -rf -- "$leo_test_dir"' EXIT
leo_classpath="$leo_kotlin_lib/kotlin-stdlib-2.0.21.jar:$leo_kotlin_lib/junit-4.13.2.jar:$leo_kotlin_lib/hamcrest-core-1.3.jar:$leo_kotlin_lib/kotlinx-coroutines-core-jvm-1.6.4.jar"
leo_sources=(
 app/src/main/java/com/niko/assistant/brain/AssistantCommand.kt
 app/src/main/java/com/niko/assistant/ai/NikoAiReply.kt
 app/src/main/java/com/niko/assistant/ai/ResearchCitationPolicy.kt
 app/src/main/java/com/niko/assistant/devicecontrol/LeoVisionContext.kt
 app/src/main/java/com/niko/assistant/memory/EddyTransformerEmbedder.kt
 app/src/main/java/com/niko/assistant/memory/MemoryLearning.kt
 app/src/main/java/com/niko/assistant/memory/MemoryRevision.kt
 app/src/main/java/com/niko/assistant/proactive/LeoInitiativeEngine.kt
 app/src/main/java/com/niko/assistant/skills/LeoSkill.kt
 app/src/main/java/com/niko/assistant/agent/*.kt
)
leo_tests=(agent/LeoAgentRuntime proactive/LeoInitiativeEngine memory/MemoryRevision skills/LeoSkillRegistry ai/ResearchCitationPolicy memory/EddyTransformerEmbedder devicecontrol/LeoVisionContext)
leo_classes=()
for unit in "${leo_tests[@]}"; do
 leo_sources+=("app/src/test/java/com/niko/assistant/${unit}Test.kt")
 leo_classes+=("com.niko.assistant.${unit//\//.}Test")
done
java -cp "$leo_kotlin_lib/*" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
 -no-stdlib -no-reflect -jvm-target 17 -classpath "$leo_classpath" \
 -d "$leo_test_dir/tests.jar" "${leo_sources[@]}"
java -cp "$leo_test_dir/tests.jar:$leo_classpath" org.junit.runner.JUnitCore "${leo_classes[@]}"
