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
#   The major.minor base is the higher of the latest release tag reachable
#   from HEAD and the committed MARKETING_VERSION (kept at the latest release
#   by scripts/sync_upstream_tags.sh), so TestFlight builds always outrank the
#   last shipped version even when the clone has no tags. If the build number
#   does not exceed the release's patch number, the minor is bumped instead.
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
  # Branch build: <major.minor>.<build>, which must outrank the last release.
  # The floor is the HIGHER of (a) the newest release tag reachable from HEAD
  # and (b) the committed MARKETING_VERSION, which sync_upstream_tags.sh raises
  # to every mirrored release. (b) matters: Xcode Cloud clones often carry no
  # tags and the fetch below can fail silently — that once stamped 1.0.18
  # while 1.2.2 was live (ITMS-90062).
  git -C "$CI_PRIMARY_REPOSITORY_PATH" fetch --tags --quiet 2>/dev/null || true
  TAG_VERSION="$( { git -C "$CI_PRIMARY_REPOSITORY_PATH" tag --list 'v[0-9]*' --merged HEAD --sort=-v:refname 2>/dev/null || true; } | head -n 1 | sed 's/^v//' )"
  COMMITTED_VERSION="$(grep '^MARKETING_VERSION=' "$CONFIG" | cut -d= -f2 | tr -d '[:space:]')"
  FLOOR="$(printf '%s\n%s\n' "$TAG_VERSION" "$COMMITTED_VERSION" | grep -E '^[0-9]+(\.[0-9]+){0,2}$' | sort -V | tail -n 1)"
  if [ -z "$FLOOR" ]; then
    echo "ERROR: no usable version floor (tag '$TAG_VERSION', committed '$COMMITTED_VERSION')."
    exit 1
  fi
  MAJOR="$(printf '%s' "$FLOOR" | cut -d. -f1)"
  MINOR="$(printf '%s' "$FLOOR" | cut -d. -f2 -s)"; MINOR="${MINOR:-0}"
  PATCH="$(printf '%s' "$FLOOR" | cut -d. -f3 -s)"; PATCH="${PATCH:-0}"
  # <major.minor>.<build> only outranks the floor if build > patch; otherwise
  # move to the next minor so the upload can never land in a closed train.
  if [ "$CI_BUILD_NUMBER" -le "$PATCH" ]; then
    MINOR=$((MINOR + 1))
  fi
  MARKETING="${MAJOR}.${MINOR}.${CI_BUILD_NUMBER}"
  echo "Version floor: $FLOOR (tag: '${TAG_VERSION:-none}', committed: '$COMMITTED_VERSION')"
fi

sed -i '' "s/^MARKETING_VERSION=.*/MARKETING_VERSION=${MARKETING}/" "$CONFIG"

echo "Stamped versions:"
grep -E '^(CURRENT_PROJECT_VERSION|MARKETING_VERSION)=' "$CONFIG"
