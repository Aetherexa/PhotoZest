import unittest
from pathlib import Path
import sys
sys.path.insert(0, str(Path(__file__).resolve().parent))
import feature_flags as ff

class PipelineBoundaries(unittest.TestCase):
    def test_three_distinct_release_workflows(self):
        base = ff.ROOT / '.github/workflows'
        expected = {'android.yml','ios.yml','web.yml'}
        self.assertEqual({p.name for p in base.glob('*.yml')}, expected)
    def test_each_prod_release_has_explicit_main_and_environment_guard(self):
        for platform in ff.SCHEMA:
            workflow = (ff.ROOT / f'.github/workflows/{platform}.yml').read_text()
            self.assertIn(f'environment: {platform}-production', workflow) if platform != 'web' else self.assertIn('name: web-production', workflow)
            self.assertIn("inputs.publish == true && github.ref == 'refs/heads/main'", workflow)
            self.assertIn(f'--platform {platform} --profile production', workflow)
            self.assertIn('workflow_dispatch:', workflow)
    def test_other_platform_sources_cannot_trigger_this_pipeline(self):
        for platform in ff.SCHEMA:
            file = (ff.ROOT / f'.github/workflows/{platform}.yml').read_text()
            other = set(ff.SCHEMA) - {platform}
            trigger_section = file.split('workflow_dispatch:')[0]
            for name in other:
                self.assertNotIn(f"      - '{name}/**'", trigger_section)
                self.assertNotIn(f"      - 'config/features/{name}.*.json'", trigger_section)
    def test_no_automatic_publish(self):
        for platform in ff.SCHEMA:
            workflow = (ff.ROOT / f'.github/workflows/{platform}.yml').read_text()
            self.assertIn("if: github.event_name == 'workflow_dispatch'", workflow)
            self.assertIn('needs: verify', workflow)

if __name__ == '__main__':
    unittest.main()
