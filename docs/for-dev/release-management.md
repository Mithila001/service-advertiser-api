# Release management

Versions, tags and publication remain manual. Normal Verify runs on pull requests
targeting `main`; pushes to `main` do not trigger Verify. Release runs on `v*` tag
pushes and tests the exact checked-out tagged source before creating a draft.
PR merge-test SHAs are not treated as proof that a later main/tag SHA passed.

## One-time repository settings

In GitHub Settings ? Rules ? Rulesets (or branch protection), target `main`:
require pull requests and the `verify` job from the **Verify** workflow to pass
before merging. Select the check after its first PR run. Require branches to be
up to date before merging, restrict direct pushes and limit bypass access according
to repository policy. Workflow YAML alone does not block merging. Configure these
settings separately; this change does not alter remote repository settings.

## Prepare and publish

1. Start a branch from updated main. Choose a stable `X.Y.Z` version and change
   the direct project version in `pom.xml`, not the Spring Boot parent version.
   Commit, push and open a PR into main. No automatic version bump exists.
2. Review the PR and its successful Verify check, then merge. Update local main
   and record the full main commit SHA to release. It must contain the intended
   stable version. Main may advance; tag the recorded commit explicitly.
3. With a clean working tree, create and push an annotated tag:

   ```bash
   git switch main
   git pull --ff-only origin main
   git status --short
   git show RELEASE_MAIN_SHA:pom.xml
   git tag -a v0.1.0 RELEASE_MAIN_SHA -m "Release v0.1.0"
   git push origin v0.1.0
   ```

   `0.1.0` and `RELEASE_MAIN_SHA` are placeholders: substitute the chosen version
   and full source SHA. The tag must match the stable POM version, identify HEAD
   during verification, and belong to `origin/main` history.
4. Watch **Release** in Actions. It validates source identity and release state,
   runs helper tests and Maven/PostgreSQL tests on the tag, checks container-native
   configuration, builds one temporary image, and runs disposable acceptance.
   A failed check prevents the dependent draft job.
5. Open the resulting draft, review/edit its generated notes and breaking changes,
   then publish manually. Existing drafts and edited notes survive retries;
   published releases are rejected. Use a normal PR for the next chosen SNAPSHOT.

Publishing does not deploy, publish images to GHCR, or attach application binaries.
Temporary images disappear with the runner. Deployment remains a separate manual
operation following the [deployment runbook](../../deployment/README.md).

## Local checks

Run from the repository root with Java 21, Python 3 and Docker Linux containers
with Compose 2.24.4+ available:

```powershell
.\mvnw.cmd -B test
py -3 -m unittest discover -s infrastructure -p test_release.py
py -3 infrastructure/check_version.py
py -3 infrastructure/check.py
```

On Linux/Git Bash use `./mvnw -B test` and `python3` instead of `py -3`.
Installed `mvn -B test` and Python 3 `python` are also supported.
For an existing tag, fetch origin and run `python3 infrastructure/check_version.py
--tag v0.1.0` from that checked-out tag. Validators never change versions or tags.
Do not use Python `-O`: assertions are part of infrastructure checks.

## Safeguards and retries

Release checks the remote tag identity both before verification and before draft
creation, including annotated tags. It retains ancestry/POM checks, rejects a
published release or multiple drafts, and serializes workflow runs per tag.
A moved/deleted tag fails verification. Prefer a corrected commit and new version
to moving tags; published tags are operationally immutable.

Verify and Release verification use contents-read. Only the dependent draft job
gets contents-write, using the built-in GitHub token. No PAT, deployment secrets,
registry credentials or VM access are required. Resolve token policy failures with
the repository administrator. GitHub API errors fail the gate.

Surefire reports and available acceptance evidence are uploaded even after failure
with seven-day retention. Earlier failures may produce no report; inspect logs.
Fix source failures in a new reviewed commit/version. For transient runner/API
failures, use **Re-run failed jobs**; a draft-only retry preserves successful
verification and edited draft notes. Do not publish while a retry is running.

The first PR/tag run must be observed on GitHub to confirm triggers and permissions;
local test success alone cannot establish remote runner behavior. Extensive
benchmarks and real-VM deployment verification remain separate manual work.
