#!/usr/bin/env bash
# Offline JVM regression tests using the Kotlin/JUnit jars bundled with Gradle.
# This validates policy/audio code, not Android, JNI, microphone hardware or ASR WER.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."

leo_kotlin_lib="${LEO_KOTLIN_LIB_DIR:-${GRADLE_USER_HOME:-${HOME}}/niko-bootstrap/gradle-8.13/lib}"
for jar in kotlin-compiler-embeddable-2.0.21.jar kotlin-stdlib-2.0.21.jar junit-4.13.2.jar hamcrest-core-1.3.jar kotlinx-coroutines-core-jvm-1.6.4.jar; do
    if [[ ! -f "$leo_kotlin_lib/$jar" ]]; then
        printf 'Missing %s. Set LEO_KOTLIN_LIB_DIR to the Gradle 8.13 lib directory.\n' "$jar" >&2
        exit 1
    fi
done

leo_test_dir=$(mktemp -d)
trap 'rm -f -- "$leo_test_dir/tests.jar"; rmdir -- "$leo_test_dir"' EXIT
leo_classpath="$leo_kotlin_lib/kotlin-stdlib-2.0.21.jar:$leo_kotlin_lib/junit-4.13.2.jar:$leo_kotlin_lib/hamcrest-core-1.3.jar:$leo_kotlin_lib/kotlinx-coroutines-core-jvm-1.6.4.jar"
leo_units=(
    voice/TranscriptQuality
    voice/SpeechAudioHistory
    voice/SpeechAudioFidelity
    voice/FaithfulSpeechTranscriber
    voice/SpeechOutputPolicy
    voice/WakeCommandWindow
    voice/LeoRealtimeTurnBus
    voice/FarFieldAudioEnhancer
    voice/WakeWordGate
    voice/LeoPassiveWakeVerifier
    voice/PcmPreRoll
    voice/VoiceControl
    devicecontrol/NikoDirectUiAction
    devicecontrol/NikoUiTaskPolicy
)
leo_sources=()
leo_tests=()
leo_sources+=(
    app/src/main/java/com/niko/assistant/voice/LeoVoiceMetrics.kt
    scripts/testsupport/LeoVoiceJvmStubs.kt
)
for unit in "${leo_units[@]}"; do
    leo_sources+=("app/src/main/java/com/niko/assistant/$unit.kt" "app/src/test/java/com/niko/assistant/${unit}Test.kt")
    leo_tests+=("com.niko.assistant.${unit//\//.}Test")
done

java -cp "$leo_kotlin_lib/*" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
    -no-stdlib -no-reflect -jvm-target 17 -classpath "$leo_classpath" \
    -d "$leo_test_dir/tests.jar" "${leo_sources[@]}"
java -cp "$leo_test_dir/tests.jar:$leo_classpath" org.junit.runner.JUnitCore "${leo_tests[@]}"
