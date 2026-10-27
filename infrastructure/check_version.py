"""Validate the project version; --tag also checks local release identity/history."""
import argparse
from pathlib import Path
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
VERSION = r'(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)'


def project_version(pom):
    value = ET.parse(pom).getroot().findtext('{http://maven.apache.org/POM/4.0.0}version')
    if value is None or not re.fullmatch(VERSION + r'(?:-SNAPSHOT)?', value):
        raise ValueError('Project version must be X.Y.Z or X.Y.Z-SNAPSHOT (canonical numbers)')
    return value


def git(*args, root=ROOT):
    return subprocess.check_output(['git', *args], cwd=root, text=True).strip()


def validate_tag(version, tag, root=ROOT):
    if not re.fullmatch('v' + VERSION, tag):
        raise ValueError('Release tag must be vX.Y.Z (canonical numbers, no SNAPSHOT)')
    if tag[1:] != version:
        raise ValueError(f'Tag {tag} does not match project version {version}')
    commit = git('rev-parse', '--verify', f'refs/tags/{tag}^{{commit}}', root=root)
    if commit != git('rev-parse', 'HEAD', root=root):
        raise ValueError('Tag does not resolve to the checked-out commit')
    subprocess.run(['git', 'merge-base', '--is-ancestor', commit, 'origin/main'],
                   cwd=root, check=True)
    return commit


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--tag')
    args = parser.parse_args()
    try:
        version = project_version(ROOT / 'pom.xml')
        if args.tag is not None:
            print(validate_tag(version, args.tag))
        else:
            print('Project version OK: ' + version)
    except (ValueError, ET.ParseError, OSError, subprocess.CalledProcessError) as error:
        sys.exit(f'Version/release validation failed: {error}')


if __name__ == '__main__':
    main()
