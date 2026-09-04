# Leo MicroGPT v1

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
- Checkpoint SHA-256: `d94590643699181d550d51bb2a621bf7ac2e6d3b928811703e78fbb879c8017f`.

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

MicroGPT is not used to execute actions and is not treated as an authoritative factual model. The existing deterministic action planner remains responsible for phone commands. Learned local knowledge is checked before MicroGPT. Open-domain or current questions fall through to Groq/web according to the existing `ConversationCoordinator` policy.

The older native/JNI open-domain LLM runtime remains disabled. This avoids reintroducing the Android 12+ native crashes that motivated the safe local architecture.
