import json
import tempfile
import unittest
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parent))
import feature_flags as ff

class FeatureFlagTests(unittest.TestCase):
    def test_all_profiles_are_explicit_and_valid(self):
        for platform in ff.SCHEMA:
            for profile in ("preview", "production"):
                flags = ff.load(platform, profile)
                self.assertEqual(set(flags), set(ff.SCHEMA[platform]))
                self.assertTrue(all(type(v) is bool for v in flags.values()))
    def test_production_safety_defaults(self):
        self.assertFalse(ff.load("android", "production")["originalFolderMoves"])
        self.assertFalse(ff.load("android", "production")["screenshotOcr"])
        self.assertFalse(ff.load("ios", "production")["albumOrganization"])
        self.assertFalse(ff.load("web", "production")["screenshotOcr"])
    def test_generated_source_represents_each_toggle(self):
        for platform in ff.SCHEMA:
            flags = ff.load(platform, "production")
            generated = ff.render(platform, "production", flags)
            for name, enabled in flags.items():
                self.assertIn(name, generated)
                self.assertIn(str(enabled).lower(), generated)
    def test_no_universal_shared_production_profile(self):
        self.assertNotEqual(ff.load("android", "production"), ff.load("ios", "production"))
        self.assertNotEqual(ff.load("web", "production"), ff.load("ios", "production"))

if __name__ == "__main__":
    unittest.main()
