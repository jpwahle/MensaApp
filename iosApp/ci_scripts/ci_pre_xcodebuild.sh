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
# Fix: before xcodebuild reads the xcconfig, stamp BOTH values from
# CI_BUILD_NUMBER (Xcode Cloud's monotonically increasing run number):
#
#   CURRENT_PROJECT_VERSION (CFBundleVersion)      -> <CI_BUILD_NUMBER>                  e.g. 12
#   MARKETING_VERSION (CFBundleShortVersionString) -> <major.minor>.<CI_BUILD_NUMBER>    e.g. 1.0.12
#
# The committed MARKETING_VERSION (e.g. "1.0") is the human-controlled
# major.minor base: bump it for milestones (1.1, 2.0) and CI appends the build
# number as the patch, so every push to main yields a fresh, always-accepted
# version with no manual step. Runs after ci_post_clone, before the archive action.

set -euxo pipefail

if [ -z "${CI_BUILD_NUMBER:-}" ]; then
  echo "CI_BUILD_NUMBER is not set; leaving versions unchanged."
  exit 0
fi

CONFIG="$CI_PRIMARY_REPOSITORY_PATH/iosApp/Configuration/Config.xcconfig"

# CFBundleVersion: the build number is CI_BUILD_NUMBER verbatim.
sed -i '' "s/^CURRENT_PROJECT_VERSION=.*/CURRENT_PROJECT_VERSION=${CI_BUILD_NUMBER}/" "$CONFIG"

# CFBundleShortVersionString: keep the committed major.minor as the base and
# append the build number as the patch. Normalize to major.minor first, since
# CFBundleShortVersionString accepts at most three integer components.
MARKETING_BASE="$(grep '^MARKETING_VERSION=' "$CONFIG" | cut -d= -f2 | tr -d '[:space:]' | cut -d. -f1-2)"
sed -i '' "s/^MARKETING_VERSION=.*/MARKETING_VERSION=${MARKETING_BASE}.${CI_BUILD_NUMBER}/" "$CONFIG"

echo "Stamped versions:"
grep -E '^(CURRENT_PROJECT_VERSION|MARKETING_VERSION)=' "$CONFIG"
