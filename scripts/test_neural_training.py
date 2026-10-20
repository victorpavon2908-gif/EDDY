import base64
import unittest
import torch
from finetune_leo_neural_core import load_base, expand, tensors, encode, loss
from validate_leo_microgpt_assets import ASSETS, part_names

class TrainingContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        torch.set_num_threads(2)
        cls.payload = base64.b64decode(''.join((ASSETS / p).read_text() for p in part_names()))
    def test_corrupted_checkpoint_cannot_seed_training(self):
        changed = bytearray(self.payload); changed[-1] ^= 1
        with self.assertRaises(ValueError): load_base(bytes(changed))
    def test_expansion_preserves_every_existing_parameter_and_token_id(self):
        base, vocab = load_base(self.payload)
        newer, expanded = expand(base, vocab, {'greeting': {'prompts': ['palabranueva'], 'responses': ['respuestanueva']}})
        self.assertEqual(vocab, expanded[:len(vocab)])
        for name, expected in base.state_dict().items():
            actual = newer.state_dict()[name]
            if name in ('tok.weight', 'bias'): actual = actual[:len(vocab)]
            self.assertTrue(torch.equal(expected, actual), name)
    def test_oversized_example_is_rejected_not_silently_truncated(self):
        _, vocab = load_base(self.payload)
        with self.assertRaises(ValueError): encode(vocab, 'greeting', 'hola ' * 50, 'hola')
    def test_training_step_changes_real_weights_and_has_finite_gradient(self):
        model, vocab = load_base(self.payload)
        previous = model.tok.weight.detach().clone()
        optimizer = torch.optim.AdamW(model.parameters(), lr=0.0002)
        value = loss(model, [('greeting', 'hola', 'Aquí estoy.')], vocab)
        self.assertTrue(torch.isfinite(value))
        value.backward(); optimizer.step()
        self.assertFalse(torch.equal(previous, model.tok.weight))

if __name__ == '__main__': unittest.main()
