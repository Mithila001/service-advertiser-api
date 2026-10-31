"""GitHub release gates and draft creation. Uses gh and the built-in Actions token."""
import argparse
import json
import os
import subprocess
import sys

from check_version import ROOT, validate_tag, project_version


def gh(*args):
    return subprocess.check_output(['gh', *args], text=True).strip()


def pages(endpoint):
    # gh emits one JSON array containing every page; API/authentication failures propagate.
    return json.loads(gh('api', '--paginate', '--slurp', endpoint))


def existing_draft(repo, tag):
    matches = [release for page in pages(f'repos/{repo}/releases?per_page=100')
               for release in page if release['tag_name'] == tag]
    if any(not release['draft'] for release in matches):
        raise ValueError(f'A published release already exists for {tag}')
    if len(matches) > 1:
        raise ValueError('Multiple drafts found; resolve manually before retrying')
    return matches[0]['html_url'] if matches else None


def summary(message):
    print(message)
    with open(os.environ['GITHUB_STEP_SUMMARY'], 'a', encoding='utf-8') as output:
        output.write(message + '\n')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('mode', choices=['verify', 'draft'])
    args = parser.parse_args()
    repo, tag = os.environ['GITHUB_REPOSITORY'], os.environ['RELEASE_TAG']
    sha = validate_tag(project_version(ROOT / 'pom.xml'), tag)
    # Fetch the remote tag explicitly, including on a retry after someone moved/deleted it.
    gh_ref = json.loads(gh('api', f'repos/{repo}/git/ref/tags/{tag}'))['object']
    while gh_ref['type'] == 'tag':
        gh_ref = json.loads(gh('api', f'repos/{repo}/git/tags/{gh_ref["sha"]}'))['object']
    if gh_ref['type'] != 'commit' or gh_ref['sha'] != sha:
        raise ValueError('Remote tag no longer identifies the verified source')
    draft = existing_draft(repo, tag)
    if args.mode == 'draft':
        if not draft:
            draft = gh('release', 'create', tag, '--repo', repo, '--verify-tag', '--draft',
                       '--title', f'Service Advertiser {tag}', '--generate-notes')
        summary('Draft (review/edit and publish manually): ' + draft)
    elif draft:
        summary('Existing draft will be preserved: ' + draft)


if __name__ == '__main__':
    try:
        main()
    except (ValueError, KeyError, OSError, subprocess.CalledProcessError) as error:
        sys.exit(f'Release gate failed: {error}')
