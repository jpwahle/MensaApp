#!/usr/bin/env bash
# Raise MARKETING_VERSION in iosApp/Configuration/Config.xcconfig to the given
# release version and commit the change — only ever upwards, a no-op when the
# committed value is already at or above it.
#
# The committed MARKETING_VERSION is the version floor for Xcode Cloud branch
# (TestFlight) builds: iosApp/ci_scripts/ci_pre_xcodebuild.sh stamps
# <major.minor>.<build> above it. Xcode Cloud often clones without tags, so
# without this floor a branch build can fall below the live App Store version
# (ITMS-90062). Called by:
#   - scripts/sync_upstream_tags.sh, for every mirrored upstream release, and
#   - .github/workflows/raise-version-floor.yml, for tags pushed by hand.
#
# Usage: raise_version_floor.sh <version|vVERSION>
# Prints "raised" when it committed, "unchanged" otherwise.

set -euo pipefail

version="${1:?usage: raise_version_floor.sh <version>}"
version="${version#v}"
if ! printf '%s' "$version" | grep -Eq '^[0-9]+(\.[0-9]+){0,2}$'; then
  echo "ERROR: '$1' is not a release version (expected <major>[.<minor>[.<patch>]])." >&2
  exit 1
fi

config="$(git rev-parse --show-toplevel)/iosApp/Configuration/Config.xcconfig"
current="$(grep '^MARKETING_VERSION=' "$config" | cut -d= -f2 | tr -d '[:space:]')"

if [ "$current" = "$version" ] || \
   [ "$(printf '%s\n%s\n' "$current" "$version" | sort -V | tail -n 1)" = "$current" ]; then
  echo "unchanged"
  exit 0
fi

sed -i.bak "s/^MARKETING_VERSION=.*/MARKETING_VERSION=${version}/" "$config"
rm -f "$config.bak"
git add "$config"
git commit --quiet -m "chore(ios): raise MARKETING_VERSION floor to $version"
echo "raised"
