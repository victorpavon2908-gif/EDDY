import os
import unittest
from unittest.mock import patch

from .compat import env


class ConfigurationCompatibilityTest(unittest.TestCase):
    def test_new_configuration_wins_over_old_configuration(self):
        with patch.dict(os.environ, {"NIKO_AI_MODEL": "current", "EDDY_AI_MODEL": "previous"}, clear=True):
            self.assertEqual("current", env("NIKO_AI_MODEL"))

    def test_existing_deployment_and_unrelated_environment_still_work(self):
        with patch.dict(os.environ, {"EDDY_AI_MODEL": "previous", "PORT": "10000"}, clear=True):
            self.assertEqual("previous", env("NIKO_AI_MODEL"))
            self.assertEqual("10000", env("PORT"))
            self.assertEqual("fallback", env("NIKO_AI_TIMEOUT", "fallback"))
