#!/bin/sh
# Xcode Cloud pre-xcodebuild script.
#
# Two App Store Connect rules force every upload to carry unique, ever-increasing
# version numbers. Config.xcconfig commits only STATIC base values, so without
# this script every Xcode Cloud run would upload identical numbers and be
# rejected:
#
#   * CFBundleVersion (build number) must be unique within a marketing version,
#     else the upload is rejected as a duplicate build.
#   * CFBundleShortVersionString (marketing version) must be HIGHER than any
#     already-approved version. Once a version is approved its pre-release
#     "train" closes for new builds -> ITMS-90186 ("train 'X' is closed") and
#     ITMS-90062 ("must contain a higher version than ... [X]").
#
# Such archives pass locally but fail App Store Connect processing silently:
# nothing shows in the Xcode Cloud log; Apple emails the account holder instead.
#
# Fix: before xcodebuild reads the xcconfig, stamp both values. Two modes:
#
#   Release (tag) builds — CI_TAG is set (workflow started by a v* tag):
#     MARKETING_VERSION           -> the tag itself (v1.1 -> 1.1)
#     CURRENT_PROJECT_VERSION     -> CI_BUILD_NUMBER
#   The tag IS the public App Store version. The GitHub workflow
#   .github/workflows/release-app-store.yml waits for this exact version
#   string, so the two must never diverge.
#
#   Branch (main) builds — no CI_TAG:
#     MARKETING_VERSION           -> <major.minor>.<CI_BUILD_NUMBER>  e.g. 1.1.37
#     CURRENT_PROJECT_VERSION     -> CI_BUILD_NUMBER
#   The major.minor base is taken from the latest release tag reachable from
#   HEAD so TestFlight builds always outrank the last shipped version, falling
#   back to the committed MARKETING_VERSION base when no tag is visible.
#   (Prefer two-component release tags like v1.2 — the branch scheme appends
#   the build number as the third component.)
#
# Runs after ci_post_clone, before the archive action.

set -euxo pipefail

if [ -z "${CI_BUILD_NUMBER:-}" ]; then
  echo "CI_BUILD_NUMBER is not set; leaving versions unchanged."
  exit 0
fi

CONFIG="$CI_PRIMARY_REPOSITORY_PATH/iosApp/Configuration/Config.xcconfig"

# CFBundleVersion: the build number is CI_BUILD_NUMBER verbatim.
sed -i '' "s/^CURRENT_PROJECT_VERSION=.*/CURRENT_PROJECT_VERSION=${CI_BUILD_NUMBER}/" "$CONFIG"

if [ -n "${CI_TAG:-}" ]; then
  # Release build: the tag is the marketing version.
  MARKETING="${CI_TAG#v}"
  if ! printf '%s' "$MARKETING" | grep -Eq '^[0-9]+(\.[0-9]+){0,2}$'; then
    echo "ERROR: tag '$CI_TAG' is not a release version tag (expected v<major>[.<minor>[.<patch>]], e.g. v1.1.0)."
    exit 1
  fi
else
  # Branch build: <major.minor>.<build>, based on the highest release tag
  # reachable from HEAD so TestFlight uploads stay above the last approved
  # version. Fetching tags is best-effort — Xcode Cloud clones do not always
  # include them.
  git -C "$CI_PRIMARY_REPOSITORY_PATH" fetch --tags --quiet 2>/dev/null || true
  TAG_BASE="$( { git -C "$CI_PRIMARY_REPOSITORY_PATH" tag --list 'v[0-9]*' --merged HEAD --sort=-v:refname 2>/dev/null || true; } | head -n 1 | sed 's/^v//' | cut -d. -f1-2 )"
  if printf '%s' "$TAG_BASE" | grep -Eq '^[0-9]+(\.[0-9]+)?$'; then
    MARKETING_BASE="$TAG_BASE"
  else
    # Fallback: the committed base, normalized to major.minor since
    # CFBundleShortVersionString accepts at most three integer components.
    MARKETING_BASE="$(grep '^MARKETING_VERSION=' "$CONFIG" | cut -d= -f2 | tr -d '[:space:]' | cut -d. -f1-2)"
  fi
  MARKETING="${MARKETING_BASE}.${CI_BUILD_NUMBER}"
fi

sed -i '' "s/^MARKETING_VERSION=.*/MARKETING_VERSION=${MARKETING}/" "$CONFIG"

echo "Stamped versions:"
grep -E '^(CURRENT_PROJECT_VERSION|MARKETING_VERSION)=' "$CONFIG"
