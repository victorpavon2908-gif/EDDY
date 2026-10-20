import unittest
from score_neural_candidate import score

class ScoringContractTest(unittest.TestCase):
    def test_empty_output_cannot_pass(self):
        self.assertFalse(score('')['passed'])
    def test_repeated_successful_case_cannot_replace_full_suite(self):
        result = score(('yes\tsí\tDale.\ttrue\n') * 43)
        self.assertFalse(result['passed'])
        self.assertFalse(result['complete'])
    def test_nonempty_gibberish_is_not_success(self):
        result = score('learning\tpregunta\tAprendo aprendo aprendo aprendo\ttrue')
        self.assertEqual(0, result['approved_wording'])
        self.assertFalse(result['passed'])
    def test_approved_answer_in_wrong_family_is_rejected(self):
        result = score('privacy\tpregunta\tDale.\ttrue')
        self.assertEqual(0, result['approved_wording'])

if __name__ == '__main__': unittest.main()
