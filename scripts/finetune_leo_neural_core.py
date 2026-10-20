#!/usr/bin/env python3
"""Fine-tune the shipped INT8 decoder; export a candidate, never overwrite Android assets."""
import argparse
import base64
import hashlib
import json
import math
import random
import struct
import time
from pathlib import Path

import torch
import torch.nn.functional as F
from train_leo_microgpt import MicroGPT, CONTEXT, DIM, HEADS, FF, LAYERS, build_vocab, prompt_tok, response_tok, export_int8
from validate_leo_microgpt_assets import ASSETS, EXPECTED_SHA256, part_names


def tensors(model):
    result = [model.tok.weight, model.pos.weight]
    for b in model.blocks:
        result += [b.ln1.weight, b.ln1.bias, b.q.weight, b.q.bias, b.k.weight, b.k.bias,
                   b.v.weight, b.v.bias, b.o.weight, b.o.bias, b.ln2.weight, b.ln2.bias,
                   b.fc1.weight, b.fc1.bias, b.fc2.weight, b.fc2.bias]
    return result + [model.lnf.weight, model.lnf.bias, model.bias]


def load_base(data):
    if hashlib.sha256(data).hexdigest() != EXPECTED_SHA256:
        raise ValueError('Base checkpoint integrity failed')
    if data[:8] != b'LEOMGQ81':
        raise ValueError('Unsupported checkpoint')
    version, count, context, dim, heads, ff, layers = struct.unpack_from('<7I', data, 8)
    if (version, context, dim, heads, ff, layers) != (1, CONTEXT, DIM, HEADS, FF, LAYERS):
        raise ValueError('Incompatible architecture')
    offset = 36
    vocab = []
    for _ in range(count):
        length, = struct.unpack_from('<H', data, offset); offset += 2
        vocab.append(data[offset:offset + length].decode('utf-8')); offset += length
    model = MicroGPT(count)
    with torch.no_grad():
        for tensor in tensors(model):
            scale, = struct.unpack_from('<f', data, offset); offset += 4
            if not math.isfinite(scale) or scale <= 0:
                raise ValueError('Invalid tensor scale')
            size = tensor.numel()
            value = torch.frombuffer(bytearray(data[offset:offset + size]), dtype=torch.int8)
            tensor.copy_(value.reshape(tensor.shape).float() * scale); offset += size
    if offset != len(data):
        raise ValueError('Invalid checkpoint size')
    return model, vocab


def expand(model, old_vocab, corpus):
    vocab = old_vocab + [word for word in build_vocab(corpus) if word not in old_vocab]
    if len(vocab) > 4096:
        raise ValueError('Vocabulary exceeds Android contract')
    enlarged = MicroGPT(len(vocab))
    with torch.no_grad():
        old = model.state_dict()
        for name, value in enlarged.state_dict().items():
            if name in ('tok.weight', 'bias'):
                value[:len(old_vocab)].copy_(old[name])
            else:
                value.copy_(old[name])
    return enlarged, vocab


def encode(vocab, family, prompt, answer):
    ids = {word: i for i, word in enumerate(vocab)}
    sequence = [ids['<bos>'], ids[f'<f_{family}>']] + [ids.get(w, ids['<unk>']) for w in prompt_tok(prompt)]
    sequence += [ids['<sep>']] + [ids.get(w, ids['<unk>']) for w in response_tok(answer)] + [ids['<eos>']]
    if len(sequence) > CONTEXT:
        raise ValueError(f'Example exceeds {CONTEXT} tokens: {family}: {prompt}')
    return sequence, sequence.index(ids['<sep>'])


def loss(model, samples, vocab):
    sequences = [encode(vocab, *sample) for sample in samples]
    x, y, masks = [], [], []
    for ids, sep in sequences:
        n = CONTEXT - len(ids)
        x.append(ids[:-1] + [0] * n)
        y.append(ids[1:] + [0] * n)
        masks.append([float(i >= sep) for i in range(len(ids) - 1)] + [0.] * n)
    x, y, mask = torch.tensor(x), torch.tensor(y), torch.tensor(masks)
    logits = model(x)
    per_token = F.cross_entropy(logits.reshape(-1, len(vocab)), y.reshape(-1), reduction='none').view_as(y)
    return (per_token * mask).sum() / mask.sum()


def evaluate(model, cases, vocab):
    model.eval()
    with torch.no_grad():
        values = [float(loss(model, cases[i:i+16], vocab)) for i in range(0, len(cases), 16)]
    return sum(values) / len(values)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--steps', type=int, default=400)
    parser.add_argument('--output', default='build/leo-neural-candidate')
    args = parser.parse_args()
    if not 1 <= args.steps <= 10000:
        raise ValueError('steps must be in 1..10000')
    torch.set_num_threads(4); torch.manual_seed(2908); random.seed(2908)
    start = time.monotonic()
    original = json.loads(Path('scripts/leo_microgpt_corpus.json').read_text())
    curriculum_path = Path('scripts/leo_neural_foundations.json')
    extra = json.loads(curriculum_path.read_text())
    heldout = json.loads(Path('scripts/leo_neural_eval.json').read_text())
    training_prompts = {p for family in original.values() for p in family['prompts']}
    training_prompts.update(case[1] for case in extra)
    normalized_training = {tuple(prompt_tok(prompt)) for prompt in training_prompts}
    if any(tuple(prompt_tok(case[1])) in normalized_training for case in heldout):
        raise ValueError('Evaluation prompt overlaps training')
    corpus = json.loads(json.dumps(original))
    for family, prompt, answer in extra:
        corpus[family]['prompts'].append(prompt); corpus[family]['responses'].append(answer)
    payload = base64.b64decode(''.join((ASSETS / p).read_text() for p in part_names()), validate=True)
    base, old_vocab = load_base(payload)
    model, vocab = expand(base, old_vocab, corpus)
    # Fixed retention probes are training-family examples, NOT an independent benchmark.
    retention = [(key, value['prompts'][0], value['responses'][0]) for key, value in original.items()]
    baseline = {'development_response_nll': evaluate(model, heldout, vocab), 'retention_nll': evaluate(model, retention, vocab)}
    optimizer = torch.optim.AdamW(model.parameters(), lr=0.0002, weight_decay=0.003)
    model.train()
    losses = []
    for step in range(args.steps):
        samples = []
        for _ in range(32):
            if random.random() < 0.5:
                samples.append(random.choice(extra))
            else:
                key = random.choice(list(original)); value = original[key]
                samples.append((key, random.choice(value['prompts']), random.choice(value['responses'])))
        current = loss(model, samples, vocab)
        if not torch.isfinite(current):
            raise ValueError('Non-finite loss')
        optimizer.zero_grad(set_to_none=True); current.backward()
        torch.nn.utils.clip_grad_norm_(model.parameters(), 1.0); optimizer.step()
        losses.append(float(current.detach()))
        if (step + 1) % 50 == 0:
            print(f'step={step+1} loss={losses[-1]:.6f}', flush=True)
    after = {'development_response_nll': evaluate(model, heldout, vocab), 'retention_nll': evaluate(model, retention, vocab)}
    output = Path(args.output); output.mkdir(parents=True, exist_ok=True)
    candidate = output / 'leo-neural-core-candidate.bundle'
    sha = export_int8(model.eval(), vocab, candidate)
    # Export is evaluated again by the independent Kotlin runtime before release.
    passed = after['development_response_nll'] < baseline['development_response_nll'] and after['retention_nll'] <= baseline['retention_nll'] + 0.35
    report = {'base_sha256': EXPECTED_SHA256, 'candidate_sha256': sha,
              'curriculum_sha256': hashlib.sha256(curriculum_path.read_bytes()).hexdigest(),
              'torch_version': torch.__version__, 'seed': 2908, 'steps': args.steps,
              'parameters': sum(p.numel() for p in model.parameters()), 'vocabulary': len(vocab),
              'bytes': candidate.stat().st_size, 'elapsed_seconds_host': time.monotonic() - start,
              'development_cases': len(heldout), 'retention_cases': len(retention), 'before': baseline,
              'after': after, 'last_50_training_loss': sum(losses[-50:]) / len(losses[-50:]),
              'numerical_gate_passed': passed, 'android_activation': False,
              'limitations': 'Development prompts excluded from training but used for model selection. Small conversational decoder. NLL is not truth, ethics, reasoning accuracy or device performance.'}
    (output / 'training-report.json').write_text(json.dumps(report, ensure_ascii=False, indent=2))
    print(json.dumps(report, ensure_ascii=False, indent=2))
    if not passed:
        raise SystemExit('Candidate failed numerical gate; do not activate')

if __name__ == '__main__':
    main()
