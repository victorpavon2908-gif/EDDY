# LEO Neural Core — decodificador local compatible v1

Leo MicroGPT is the bundled short-conversation model used by the Android assistant when a request can be answered locally without current or open-domain knowledge.

## Architecture

- Decoder-only causal Transformer, implemented in pure Kotlin at runtime.
- 104,797 trainable parameters.
- 2 Transformer blocks, 4 attention heads, hidden size 64, feed-forward size 128.
- Context window: 40 word/punctuation tokens.
- 541-token vocabulary, including 31 learned semantic-family control tokens.
- Tied token embedding / language-model output weights.
- INT8 symmetric per-tensor checkpoint with Float32 scales.
- Bundled checkpoint size: 109,722 bytes.
- Checkpoint SHA-256: `eb97f32cdde73e62c19ab0159de10503b687bbf0dce67a18d756e34fc075a851`.

## Training

The checked-in corpus contains 31 balanced conversational families: greetings, clarification, detail/shortness preferences, continuation, capabilities, offline behavior, verification boundaries, non-hallucination, learning, memory, privacy, Nicaraguan/voseo style, emotions, ideation, organization, model identity, corrections and similar short dialogue.

Training uses a response-only causal loss. The semantic-family control token is part of the learned sequence, so the tiny generator learns language generation while the conservative Android gate keeps open-domain facts, current information and executable phone actions outside its scope.

The final schedule is 480 main steps plus 220 and 320 lower-learning-rate consolidation steps. The final consolidation loss observed for the bundled checkpoint was approximately 0.07 over the last 50 batches.

Re-train with:

```bash
python scripts/train_leo_microgpt.py
```

PyTorch is a training-only dependency and is deliberately not added to the Android or backend runtime requirements.

## Safety and routing boundary

MicroGPT is not used to execute actions and is not treated as an authoritative factual model. The existing deterministic action planner remains responsible for phone commands. Learned local knowledge is checked before MicroGPT. Open-domain or current questions fall through to native web research according to the existing `ConversationCoordinator` policy.

The older native/JNI open-domain LLM runtime remains disabled. This avoids reintroducing the Android 12+ native crashes that motivated the safe local architecture.

## Ajuste adicional reproducible

Ver [entrenamiento y criterios del candidato](docs/LEO_NEURAL_TRAINING.md).
Los nombres MicroGPT de clases y formatos permanecen por compatibilidad técnica.
