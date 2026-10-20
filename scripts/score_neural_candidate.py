#!/usr/bin/env python3
"""Conservative generated-text gate for this small family-conditioned decoder.
Exact approved-family wording checks memorized response reliability, NOT broad intelligence.
"""
import argparse
import json
import re
import unicodedata
from pathlib import Path


def normalize(text):
    text = ''.join(c for c in unicodedata.normalize('NFD', text.lower()) if unicodedata.category(c) != 'Mn')
    return re.sub(r'[^\w]+', ' ', text).strip()


def score(log):
    corpus = json.loads(Path('scripts/leo_microgpt_corpus.json').read_text())
    approved = {k: {normalize(a) for a in v['responses']} for k, v in corpus.items()}
    for family, _, answer in json.loads(Path('scripts/leo_neural_foundations.json').read_text()):
        approved[family].add(normalize(answer))
    cases = []
    for row in log.splitlines():
        parts = row.split('\t')
        if len(parts) != 4:
            continue
        family, prompt, reply, valid = parts
        cases.append({'family': family, 'prompt': prompt, 'reply': reply,
                      'approved_wording': valid == 'true' and normalize(reply) in approved.get(family, set())})
    # 12 development prompts + one retention prompt per original family.
    expected = [(family, prompt) for family, prompt, _ in json.loads(Path('scripts/leo_neural_eval.json').read_text())]
    expected += [(key, value['prompts'][0]) for key, value in corpus.items()]
    complete = [(case['family'], case['prompt']) for case in cases] == expected
    correct = sum(case['approved_wording'] for case in cases)
    return {'cases': len(cases), 'approved_wording': correct,
            'passed': complete and correct == len(expected), 'complete': complete, 'results': cases,
            'scope': 'All development and retention outputs must match approved wording in their family. Not a truth/ethics benchmark.'}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('log'); parser.add_argument('output')
    args = parser.parse_args()
    result = score(Path(args.log).read_text())
    Path(args.output).write_text(json.dumps(result, ensure_ascii=False, indent=2))
    print(f"Approved wording: {result['approved_wording']}/{result['cases']}; passed={result['passed']}")
    if not result['passed']:
        raise SystemExit('Candidate rejected for activation')

if __name__ == '__main__': main()
