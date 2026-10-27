"""Run: python -m unittest discover -s infrastructure -p test_release.py"""
import subprocess
import tempfile
from pathlib import Path
import unittest
from unittest.mock import patch

from check_version import project_version, validate_tag
import release


class ReleaseChecks(unittest.TestCase):
    def test_project_version(self):
        with tempfile.TemporaryDirectory() as folder:
            pom = Path(folder) / 'pom.xml'
            for value in ['0.0.1-SNAPSHOT', '0.1.0', '12.3.4', '', '01.2.3',
                          '1.2', '1.2.3-beta', '1.2.3\n', None]:
                with self.subTest(value=value):
                    child = '' if value is None else f'<version>{value}</version>'
                    pom.write_text('<project xmlns="http://maven.apache.org/POM/4.0.0">'
                                   '<parent><version>1.2.3</version></parent>' + child + '</project>')
                    if value in ['0.0.1-SNAPSHOT', '0.1.0', '12.3.4']:
                        self.assertEqual(project_version(pom), value)
                    else:
                        with self.assertRaises(ValueError):
                            project_version(pom)

    def test_tags_and_ancestry(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)

            def git(*args):
                return subprocess.check_output(['git', '-c', 'user.name=Test', '-c',
                    'user.email=test@example.invalid', *args], cwd=root, text=True,
                    stderr=subprocess.DEVNULL).strip()

            git('init')
            git('commit', '--allow-empty', '-m', 'main')
            sha = git('rev-parse', 'HEAD')
            git('update-ref', 'refs/remotes/origin/main', sha)
            git('tag', '-a', 'v1.2.3', '-m', 'test')
            self.assertEqual(validate_tag('1.2.3', 'v1.2.3', root), sha)
            for version, tag in [('1.2.4', 'v1.2.3'), ('1.2.3-SNAPSHOT', 'v1.2.3'),
                                 ('1.2.3', 'v01.2.3'), ('1.2.3', 'v1.2.3-SNAPSHOT'),
                                 ('1.2.3', 'vfoo'), ('1.2.3', 'v1.2.3\n')]:
                with self.subTest(tag=tag), self.assertRaises(ValueError):
                    validate_tag(version, tag, root)
            git('commit', '--allow-empty', '-m', 'feature')
            with self.assertRaises(ValueError):
                validate_tag('1.2.3', 'v1.2.3', root)
            git('tag', 'v1.2.4')
            with self.assertRaises(subprocess.CalledProcessError):
                validate_tag('1.2.4', 'v1.2.4', root)
            git('update-ref', 'refs/remotes/origin/main', git('rev-parse', 'HEAD'))
            git('checkout', '--detach', sha)
            self.assertEqual(validate_tag('1.2.3', 'v1.2.3', root), sha)

    def test_ci_proof(self):
        good = dict(head_sha='abc', head_branch='main', event='push', status='completed',
                    conclusion='success', name='Verify', path='.github/workflows/verify.yaml',
                    html_url='https://example.invalid/run')
        with patch.object(release, 'pages', return_value=[{'workflow_runs': []},
                                                         {'workflow_runs': [good]}]):
            self.assertEqual(release.successful_verify('owner/repo', 'abc'), good['html_url'])
        for field, value in [('head_sha', 'other'), ('head_branch', 'feature'),
                             ('event', 'pull_request'), ('status', 'in_progress'),
                             ('conclusion', 'failure'), ('name', 'Other'), ('path', 'other.yaml')]:
            with self.subTest(field=field), patch.object(release, 'pages', return_value=[
                    {'workflow_runs': [{**good, field: value}]}]), self.assertRaises(ValueError):
                release.successful_verify('owner/repo', 'abc')
        with patch.object(release, 'pages', return_value=[{'workflow_runs': []}]), self.assertRaises(ValueError):
            release.successful_verify('owner/repo', 'abc')

    def test_release_state_and_api_errors(self):
        draft = dict(tag_name='v1.2.3', draft=True, html_url='https://example.invalid/draft')
        for records, expected in [([], None), ([draft], draft['html_url'])]:
            with patch.object(release, 'pages', return_value=[records]):
                self.assertEqual(release.existing_draft('owner/repo', 'v1.2.3'), expected)
        for records in [[{**draft, 'draft': False}], [draft, draft]]:
            with patch.object(release, 'pages', return_value=[records]), self.assertRaises(ValueError):
                release.existing_draft('owner/repo', 'v1.2.3')
        with patch.object(release, 'gh', side_effect=subprocess.CalledProcessError(1, 'gh')):
            for check in [lambda: release.existing_draft('owner/repo', 'v1.2.3'),
                          lambda: release.successful_verify('owner/repo', 'abc')]:
                with self.assertRaises(subprocess.CalledProcessError):
                    check()

    def test_draft_rerun_preserves_notes(self):
        with patch('sys.argv', ['release.py', 'draft']), patch.dict('os.environ', {
                'GITHUB_REPOSITORY': 'owner/repo', 'RELEASE_TAG': 'v1.2.3'}), \
                patch.object(release, 'project_version', return_value='1.2.3'), \
                patch.object(release, 'validate_tag', return_value='abc'), \
                patch.object(release, 'existing_draft', return_value='draft-url'), \
                patch.object(release, 'summary'), patch.object(release, 'gh',
                    return_value='{"object":{"type":"commit","sha":"abc"}}') as gh:
            release.main()
            self.assertEqual(gh.call_count, 1)  # Only a read; no release edits/creation.

    def test_draft_creation_and_remote_identity(self):
        for remote_sha, existing in [('abc', None), ('changed', None), ('abc', 'published')]:
            with self.subTest(remote_sha=remote_sha, existing=existing), \
                    patch('sys.argv', ['release.py', 'draft']), patch.dict('os.environ', {
                        'GITHUB_REPOSITORY': 'owner/repo', 'RELEASE_TAG': 'v1.2.3'}), \
                    patch.object(release, 'project_version', return_value='1.2.3'), \
                    patch.object(release, 'validate_tag', return_value='abc'), \
                    patch.object(release, 'existing_draft', return_value=None,
                        side_effect=ValueError('published') if existing else None), \
                    patch.object(release, 'summary'), patch.object(release, 'gh', side_effect=[
                        '{"object":{"type":"tag","sha":"tag-object"}}',
                        '{"object":{"type":"commit","sha":"' + remote_sha + '"}}',
                        'draft-url']) as gh:
                if remote_sha != 'abc' or existing:
                    with self.assertRaises(ValueError):
                        release.main()
                    self.assertEqual(gh.call_count, 2)
                else:
                    release.main()
                    creation = gh.call_args.args
                    self.assertEqual(creation[:3], ('release', 'create', 'v1.2.3'))
                    self.assertIn('--verify-tag', creation)
                    self.assertIn('--draft', creation)


if __name__ == '__main__':
    unittest.main()
