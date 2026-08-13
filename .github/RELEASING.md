# Automated store releases

Releases are split across two repositories:

- **Upstream (`lkaesberg/MensaApp`)** ships **Android**. Publishing a GitHub
  Release there (`gh release create v1.1.0 --notes "…"`) creates the version
  tag and triggers `release-play-store.yml`.
- **This fork (`jpwahle/MensaApp`)** ships **iOS**. GitHub never propagates
  tags to forks ("Sync fork" only moves branches), so
  `sync-upstream-tags.yml` polls upstream twice an hour: each new upstream
  release tag is merged into this fork's `main` (the fork carries iOS-only
  commits — Xcode Cloud ci_scripts etc. — that upstream lacks, so the iOS
  tag points at the *merged* main, not the upstream commit), pushed, and the
  App Store workflow is dispatched. Xcode Cloud reacts to the mirrored tag
  on its own. Run the sync workflow manually (Actions → *Sync release tags
  from upstream*) to release immediately instead of waiting for the tick.

So the normal flow is: **release upstream, and iOS follows automatically
within ~30 minutes.** Tagging directly on this fork
(`git tag v1.1.0 && git push origin v1.1.0`) ships **iOS only** — useful for
fork-only fixes.

Tags must be `v<major>[.<minor>[.<patch>]]` (e.g. `v1.1`, `v1.1.0`) and end
up on `main`. Apple and Google both require strictly increasing versions, so
only ever tag upwards — and note that upstream tags below `v1.1.0` are never
mirrored (`MIN_VERSION` in `sync-upstream-tags.yml`): `v1.0.0`/`v1.0.1`
predate this automation, and old iOS builds already shipped as `1.0.<build>`,
so the next coordinated release must be **`v1.1.0` or higher**.

---

## Apple App Store (iOS)

`.github/workflows/release-app-store.yml` submits a new App Store version for
review — with automatic release after approval — for every version tag that
lands on this fork (mirrored from upstream by `sync-upstream-tags.yml`, or
pushed here by hand). The pieces:

1. **Xcode Cloud builds the tag.** Its tag-triggered workflow runs
   `iosApp/ci_scripts/ci_pre_xcodebuild.sh`, which stamps `MARKETING_VERSION`
   with the tag (`v1.1` → `1.1`) and uploads the archive to App Store Connect.
2. **The GitHub workflow generates the "What's New" text.** Source priority:
   GitHub release body → annotated tag message → commit subjects since the
   previous tag (filtered to customer-visible `feat:`/`fix:`/`perf:` commits).
   With an `ANTHROPIC_API_KEY` secret, Claude rewrites the material into
   short customer-facing notes in **English and German**; without it, the
   same (untranslated) text is used for both locales, and if nothing is
   customer-relevant a generic "bug fixes and improvements" line is used.
3. **It waits for the processed build** (up to 90 minutes for the Xcode Cloud
   build + Apple's processing), creates the App Store version with the tag's
   version string, attaches the build, writes the notes into every enabled
   localization, and **submits for review**. `releaseType` is
   `AFTER_APPROVAL`, so the update goes live automatically once Apple
   approves it.

Branch (main) builds are unaffected: they keep uploading to TestFlight as
`<major.minor>.<build>` (now based on the latest release tag), and are never
submitted to review.

### One-time setup

#### 1. App Store Connect API key

1. App Store Connect → **Users and Access** → **Integrations** →
   **App Store Connect API** → **Team Keys** → **Generate API Key**.
2. Name it e.g. `github-app-store-release`, role **App Manager**.
3. Download the `.p8` file (only possible once) and note the **Key ID** and
   the **Issuer ID** shown above the key list.

#### 2. Add the GitHub secrets

Repo → Settings → Secrets and variables → Actions → **New repository secret**:

| Secret | Value |
| --- | --- |
| `ASC_ISSUER_ID` | the Issuer ID |
| `ASC_KEY_ID` | the Key ID |
| `ASC_PRIVATE_KEY` | full contents of the downloaded `.p8` file |
| `ANTHROPIC_API_KEY` | *(optional)* enables AI-written EN+DE release notes |

#### 3. Xcode Cloud tag workflow

The GitHub workflow does not build anything — Xcode Cloud must archive tag
pushes. In App Store Connect (or Xcode → Report navigator → Cloud):

1. Open the app's Xcode Cloud workflows and **duplicate** the existing
   archive workflow (or create one), name it e.g. `Release (tags)`.
2. **Start Conditions**: remove the branch condition, add **Tag Changes**
   with a custom pattern: tags **beginning with** `v`.
3. **Archive** action for iOS with deployment preparation
   **TestFlight and App Store** (this is what uploads the build).
4. Keep the existing branch workflow for TestFlight builds of `main`.

#### 4. The first tag must beat the live version

Apple only accepts versions **higher** than anything already approved. The
old auto-bump scheme shipped versions like `1.0.<build>`, so start tagging at
least one minor above the currently live version — e.g. if `1.0.12` is live,
the first tag is `v1.1` (or `v1.1.0`). The workflow checks this up front and
fails with a clear message instead of letting Apple reject the upload
silently.

### Behavior details

- **Upstream sync**: mirrored tags are created by `sync-upstream-tags.yml`
  with the repo-scoped `GITHUB_TOKEN`. Such pushes can't trigger `on: push`
  workflows (GitHub's loop prevention), so the sync dispatches the App Store
  workflow explicitly; Xcode Cloud's webhook is unaffected and builds the tag
  either way. If the merge of an upstream release conflicts, the sync run
  fails with instructions — sync the fork manually, push, and re-run it. If
  only the final dispatch fails, the tag already exists: start *Release to
  App Store* manually with that tag.
- **Release notes across repos**: the notes generator looks for the GitHub
  release description on this fork first, then on upstream
  (`UPSTREAM_REPOSITORY`), so the release text written for the Play Store is
  reused for iOS.
- **Idempotent re-runs**: `workflow_dispatch` with a tag re-runs the whole
  flow safely — an already-submitted or live version exits as a no-op. Use
  this if the Xcode Cloud build finished after the 90-minute wait timed out.
- **Superseding**: tagging a new version while an older one is still
  `WAITING_FOR_REVIEW` cancels that submission and submits the new version.
  A submission that is actively `IN_REVIEW` is never cancelled automatically
  — the run fails and asks you to resolve it in App Store Connect.
- **Export compliance** is answered automatically (the app declares
  `ITSAppUsesNonExemptEncryption=false`).
- **Version scheme**: prefer two-component tags (`v1.2`, `v2.0`); the branch
  builds append the Xcode Cloud build number as the third component. When you
  tag a new major/minor, also raise the fallback `MARKETING_VERSION` base in
  `iosApp/Configuration/Config.xcconfig` at some point (it only matters if
  Xcode Cloud ever clones without tags).

---

## Google Play (Android)

`.github/workflows/release-play-store.yml` builds a signed AAB and uploads it
to the Play **production** track every time a GitHub Release is published.
In the two-repo split this runs on the **upstream** repo, where the releases
are published and the Play secrets live; the copy of the workflow in this
fork stays dormant because no GitHub Releases are published here.

- `versionName` comes from the release tag (`v2.8` → `2.8`)
- `versionCode` is `git rev-list --count HEAD` (the total commit count), so it always increases
- Release notes come from the GitHub release body (truncated to Play's 500-character limit)

Local builds are unaffected: `composeApp/build.gradle.kts` falls back to the hardcoded
`versionCode`/`versionName` and skips the signing config when `MENSA_KEYSTORE_FILE` is unset.

### One-time setup

#### 1. Google Cloud service account

1. Open <https://console.cloud.google.com/> and create a project (or reuse one).
2. Enable the **Google Play Android Developer API**:
   APIs & Services → Library → search for it → Enable.
3. IAM & Admin → Service Accounts → **Create service account**
   (name e.g. `github-play-publisher`, no roles needed at the GCP level).
4. Open the new account → **Keys** → Add key → Create new key → **JSON**. A file downloads.
   Keep it safe; it cannot be re-downloaded.

#### 2. Grant it access in Play Console

1. Open <https://play.google.com/console/> → **Users and permissions** → **Invite new users**.
2. Enter the service account's email (`…@….iam.gserviceaccount.com`).
3. Under **App permissions**, add the Mensa App.
4. Under **Account permissions**, grant at least:
   - View app information and download bulk reports
   - Create, edit, and delete draft apps
   - Release apps to testing tracks
   - Release to production, exclude devices, and use app signing
5. Invite the user. Permission propagation can take a few minutes to a few hours.

#### 3. Base64-encode the upload keystore

This is the same keystore you already use to sign the AAB you upload by hand:

```bash
base64 -i /path/to/upload-keystore.jks | pbcopy
```

#### 4. Add the GitHub secrets

Repo → Settings → Secrets and variables → Actions → **New repository secret**:

| Secret | Value |
| --- | --- |
| `PLAY_KEYSTORE_BASE64` | output of the `base64` command above |
| `PLAY_KEYSTORE_PASSWORD` | keystore password |
| `PLAY_KEY_ALIAS` | key alias inside the keystore |
| `PLAY_KEY_PASSWORD` | password for that key |
| `PLAY_SERVICE_ACCOUNT_JSON` | full contents of the downloaded JSON key file |

### Notes

- `workflow_dispatch` is wired up if you need to re-run a publish manually.
- The very first upload for an app must be done manually in Play Console; the API cannot
  create the initial release. That is already done for this app.
- Play rejects a `versionCode` that is not higher than the last published one. The commit
  count is currently ahead of the old hardcoded value (15), so the first automated release
  jumps to ~47. That is fine — it only has to increase.
