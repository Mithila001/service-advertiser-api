# Quick start

Run commands from the repository root. **`0.1.0` below is an example, not an
approved first release.** Choose your version and substitute it everywhere.

Preconditions: a clean working tree (`git status --short` prints nothing), an
`origin` remote, access to `main`, permission to push tags/edit releases, and
GitHub Actions enabled. Local checks need Java 21, Maven or the wrapper, Python 3,
and Docker Linux containers with Compose 2.24.4+. GitHub CLI is optional locally.

1. Prepare the manually chosen stable **project** version:

   ```bash
   git switch main
   git pull --ff-only origin main
   git switch -c chore/prepare-v0.1.0
   # Edit the direct PROJECT <version> in pom.xml to 0.1.0, not the parent version.
   git diff -- pom.xml
   git add pom.xml
   git commit -m "chore: prepare release v0.1.0"
   git push -u origin chore/prepare-v0.1.0
   ```

   Merge through the usual review process. Feature branches need no independent
   version bump. Nothing increments a version automatically.

2. Open GitHub → Actions → **Verify**. Wait for a successful **main push** run
   for the exact merged commit you intend to release. Record its full SHA.
3. Update main and tag that verified commit. Replace `VERIFIED_MAIN_SHA` below
   with the recorded full SHA; do not paste it literally:

   ```bash
   git switch main
   git pull --ff-only origin main
   git status --short
   git log -1 --oneline
   git show VERIFIED_MAIN_SHA:pom.xml
   git tag -a v0.1.0 VERIFIED_MAIN_SHA -m "Release v0.1.0"
   git show --no-patch v0.1.0
   git push origin v0.1.0
   ```

   Stop if the tree is dirty or selected POM/commit is wrong. Main may have
   advanced; using the recorded SHA avoids accidentally tagging a newer commit.

4. Watch **Release** in Actions. On success, open the draft link in its job
   summary or GitHub → Releases. Review/edit the notes and version, then manually
   click **Publish release**. Automation creates only a draft.
5. When ready, use a normal branch to change the POM to your chosen next
   `X.Y.Z-SNAPSHOT`, review and merge. The next target is your decision.

Pushing `main` alone runs CI. Pushing a tag starts release verification.
Publishing a release does not deploy anything or publish an image.

## Full developer guide

### Vocabulary and source identity

| Term | Meaning here |
| --- | --- |
| Commit | An exact source revision, identified by its SHA. |
| Branch | A moving pointer for development; `main` integrates changes. |
| Tag | A named source revision, such as `v0.1.0`; use an annotated tag with a message. |
| Maven version | Direct project `<version>` in `pom.xml`, the application version source of truth; independent of the Spring Boot parent version. |
| SNAPSHOT | A development version that can continue changing before release. |
| GitHub Actions CI | Automated checks; the main-push workflow is `Verify`. |
| Release workflow | Tag-triggered `Release` verification and draft creation. |
| Draft / published Release | Editable unpublished notes / a human-published announcement for a tag. |
| JAR | The Java application archive built by Maven. |
| Docker image | The application/runtime package used to run containers. |
| GHCR | GitHub Container Registry, a future option currently unused. |
| Deployment | Running a chosen image in the target environment, separately and manually. |

A merge is an integration event, not a release. Example: feature commits share
`0.1.0-SNAPSHOT`; a preparation commit changes it to `0.1.0`; its merged main
commit passes Verify; `v0.1.0` selects that commit; Release verifies it and creates
a draft; you publish; a later commit starts the next SNAPSHOT. Latest main,
latest published release and deployed version can all differ.

Use canonical non-negative numbers without leading zeros. Patch changes fix
compatible behavior (`0.1.0` → `0.1.1`); minor changes add compatible capabilities
(`0.1.1` → `0.2.0`); major changes signal incompatible contracts (`1.4.2` → `2.0.0`).
Initial `0.x` versions communicate an evolving API; still explain breaking changes
in notes. Choose `1.0.0` when you intend a stable public contract. CI accepts stable
and SNAPSHOT forms; release tags accept stable only. No fixed next increment is
assumed: a patch target may be more suitable than the next minor.

### Feature development and local checks

Start a feature branch from updated main, implement/test, push, and merge through
your usual review process. Only main pushes trigger Verify; these workflows add
no automatic PR or feature-branch runs. No per-feature version change is needed.
A release-preparation branch is another short-lived branch.

The Git commands above work in Git Bash, Linux/macOS shells, and PowerShell.
Python/Maven commands differ:

```bash
# Git Bash / Linux / macOS
./mvnw -B test
python3 -m unittest discover -s infrastructure -p test_release.py
python3 infrastructure/check_version.py
python3 infrastructure/check.py
```

```powershell
# Windows PowerShell
.\mvnw.cmd -B test
py -3 -m unittest discover -s infrastructure -p test_release.py
py -3 infrastructure/check_version.py
py -3 infrastructure/check.py
```

Installed `mvn -B test` works on either platform; `python` can replace the launcher
when it selects Python 3. Maven integration tests require Docker for isolated
PostgreSQL. The plain infrastructure check needs Compose for configuration
rendering but starts no application environments. Successful checks exit zero.

For an existing local release tag, use
`python3 infrastructure/check_version.py --tag v0.1.0` (PowerShell: `py -3 ...`).
Fetch origin first. The tag must resolve to HEAD and belong to origin/main history.
Validators never change versions or tags. Do not use Python `-O`: infrastructure
assertions are part of the checks.

### Workflow responsibilities and storage

| Workflow | Trigger | Checks |
| --- | --- | --- |
| Verify (`verify.yaml`) | Push to main only | Java 21 Maven unit/PostgreSQL integration tests, helper tests, project version, plain Compose/topology checks. |
| Release (`release.yaml`) | Pushed `v*` tag only | Strict tag/POM/source checks, exact prior CI proof, release state, container-native validators, one temporary image build, isolated acceptance, then a separate draft job. |

`v*` is a trigger filter; malformed tags start the workflow and fail validation.
The tag must select a commit in origin/main history, which need not be its current
tip. The workflow-specific Actions API must prove a completed successful Verify
run for that exact SHA, main branch and push event, with the expected workflow
name/path. Missing proof or API errors stop the release. Maven tests are not
repeated after that proof.

Release sets up Java 21 because acceptance starts a host JVM. Existing fresh
`sa-check-*` environments and cleanup are preserved, including short 10-user
Locust acceptance. Extensive `infrastructure/benchmark.py` runs stay manual.
Moving heavy checks to deliberate releases reduces every-merge compute while
preserving standalone simulation, validation and benchmarking tools.

Verify uses read-only contents access. Release verification adds Actions-read;
only its dependent draft job gets contents-write. All use built-in `GITHUB_TOKEN`.
No PAT, registry credentials, deployment secrets or VM access are needed. If an
organization/repository policy blocks these permissions, resolve it with the
administrator rather than adding a personal token.

Available Surefire reports (Verify) and acceptance JSON/failure logs/short Locust
CSVs (Release) are uploaded even after failure with seven-day retention. Earlier
failures may produce no report; inspect job logs. Images disappear with the
runner. There are no JAR/image uploads, binary release attachments or GHCR pushes.
Git tags, releases and GitHub's source archives persist independently of short-lived
Actions reports. Publishing a Release never triggers deployment.

### Inspect, review, publish and resume

```bash
git fetch origin --tags
git tag --list 'v*'
git show --no-patch v0.1.0
git rev-parse 'v0.1.0^{commit}'
```

Use Actions to inspect Verify's SHA and conclusion, then Release's summaries.
Optional authenticated CLI checks:

```bash
gh run list --workflow verify.yaml --branch main --event push --commit VERIFIED_MAIN_SHA
gh run list --workflow release.yaml
gh release list
```

Generated draft notes are a starting point, not an authoritative change log.
Check the version, source SHA, verification result, changes since the previous
release, breaking changes and operational caveats. Edit misleading text and
publish manually after review. Existing draft notes survive reruns; published
releases are rejected. Choose and commit the next SNAPSHOT manually afterward.

### Failures and safe retries

| Failure | Response |
| --- | --- |
| Invalid POM, malformed tag, mismatch or SNAPSHOT | Inspect the direct project version and tag. Correct source through a branch and verify again. |
| Feature-only tag or wrong checked-out revision | Select a verified main commit. |
| Missing/failed prior Verify | Wait for or rerun the intended main run; a different SHA's success does not qualify. Fix actual failures first. |
| API/authentication failure | Resolve token policy/service availability; unavailable proof never counts as success. |
| Container check/build/acceptance failure | Inspect step logs and available reports, reproduce locally with existing tools, correct and reverify. No new draft is created. |
| Draft creation fails | Correct transient service/permission issues and rerun the failed job. Existing draft edits remain intact. |
| Published release exists | Stop; use a new version for further changes. |
| Remote tag moved/deleted | Identity recheck fails. Investigate before retrying. |

Use Actions → run → **Re-run failed jobs** for transient failures. Re-running all
jobs repeats expensive validation. A draft-only failure can be retried after
successful verification. Per-tag concurrency prevents simultaneous creation by
this workflow; avoid editing or publishing while it is still running.

Published tags are immutable operational identifiers: do not routinely force-push
or retag them. For defective source, prefer a corrective commit and new version.
For an unpublished failed candidate, inspect drafts and consumers, explicitly
agree on recovery, and prefer abandoning it for a new version. This tooling
does not delete remote tags or automate destructive recovery.

### Manual deployment and future extensions

To deploy a selected release, use a clean checkout of its tag and follow the
[deployment runbook](../../deployment/README.md) for building/transferring its
image, secrets, backups, updates and restores. Record image ID and source SHA.
The release workflow's temporary image cannot be downloaded for deployment.

See [environment operations](../../infrastructure/README.md) and
[verification history](../../infrastructure/verification.md) for standalone
commands and recorded results. First remote execution remains unverified until
actually run; local success does not establish runner behavior or permissions.

Future GHCR distribution needs explicit publishing permissions and retention
policy. Future CD needs separate deployment authorization and rollback design.
Neither capability is configured here.
