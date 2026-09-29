import importlib.util
from pathlib import Path
import unittest
spec = importlib.util.spec_from_file_location('battery', Path(__file__).parents[1] / 'scripts/medir_bateria.py')
battery = importlib.util.module_from_spec(spec)
spec.loader.exec_module(battery)
class BatteryTests(unittest.TestCase):
    def test_real_reading(self):
        result = battery.parse_battery('  AC powered: false\n  USB powered: false\n  level: 40\n  scale: 80\n  temperature: 310\n')
        self.assertEqual(result['percent'], 50)
        self.assertFalse(result['powered'])
    def test_charger(self):
        self.assertTrue(battery.parse_battery('USB powered: true\nlevel: 50\nscale: 100')['powered'])
    def test_simulation(self):
        self.assertTrue(battery.parse_battery('UPDATES STOPPED\nlevel: 50\nscale: 100')['simulated'])
    def test_invalid_scale(self):
        with self.assertRaises(ValueError): battery.parse_battery('level: 50\nscale: 0')
    def test_missing_level(self):
        with self.assertRaises(KeyError): battery.parse_battery('scale: 100')
if __name__ == '__main__': unittest.main()
