#!/usr/bin/env bash
# Run the core regression suite against the real Android source set.
# Requires JDK 17 and Android SDK 36; does not generate an APK.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
leo_test_filters=(
    --tests com.niko.assistant.cognitive.LeoEmotionClassifierTest
    --tests com.niko.assistant.cognitive.LeoNeuralCoreTest
    --tests com.niko.assistant.cognitive.LeoPersonalMemoryTest
    --tests com.niko.assistant.ai.ConversationCoordinatorTest
    --tests com.niko.assistant.ai.NikoIdentityTest
    --tests com.niko.assistant.ai.LeoDialoguePolicyTest
    --tests com.niko.assistant.ai.LeoExpressionTest
    --tests com.niko.assistant.voice.LeoWakeAcknowledgementTest
    --tests com.niko.assistant.ai.ConversationContextTest
    --tests com.niko.assistant.ai.LeoNativeWebSearchTest
    --tests com.niko.assistant.learning.AdaptiveLearningPolicyTest
    --tests com.niko.assistant.learning.InteractionCorrectionTest
    --tests com.niko.assistant.learning.LearnedActionCodecTest
    --tests com.niko.assistant.learning.LearnedActionStoreTest
    --tests com.niko.assistant.learning.LeoIntentTrainingCorpusTest
    --tests com.niko.assistant.learning.LeoAdaptiveTrainerTest
    --tests com.niko.assistant.learning.OnlineIntentNetworkTest
    --tests com.niko.assistant.localai.LeoDirectoryActivationTest
    --tests com.niko.assistant.localai.LeoDownloadIntegrityTest
)
exec ./gradlew :app:testDebugUnitTest "${leo_test_filters[@]}" "$@"
