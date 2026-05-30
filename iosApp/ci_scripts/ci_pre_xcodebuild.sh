#!/bin/sh
# Xcode Cloud pre-xcodebuild script.
#
# App Store Connect rejects any upload whose build number (CFBundleVersion)
# duplicates one already uploaded for the same marketing version. Config.xcconfig
# pins CURRENT_PROJECT_VERSION to a STATIC value, so every Xcode Cloud run would
# upload the same build number — only the first is accepted; every later archive
# passes locally but fails App Store Connect processing and never reaches
# TestFlight (no error in the Xcode Cloud log; Apple emails the account holder).
#
# Fix: stamp CFBundleVersion with CI_BUILD_NUMBER (Xcode Cloud's monotonically
# increasing run number) before xcodebuild reads the xcconfig. This script runs
# after ci_post_clone and before the archive action.

set -euxo pipefail

if [ -z "${CI_BUILD_NUMBER:-}" ]; then
  echo "CI_BUILD_NUMBER is not set; leaving CURRENT_PROJECT_VERSION unchanged."
  exit 0
fi

CONFIG="$CI_PRIMARY_REPOSITORY_PATH/iosApp/Configuration/Config.xcconfig"

# Replace the build-number line in place (BSD/macOS sed on Xcode Cloud runners).
sed -i '' "s/^CURRENT_PROJECT_VERSION=.*/CURRENT_PROJECT_VERSION=${CI_BUILD_NUMBER}/" "$CONFIG"

echo "Stamped build number:"
grep '^CURRENT_PROJECT_VERSION=' "$CONFIG"
