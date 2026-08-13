#!/usr/bin/env bash
# Mirror release tags (v1.1, v1.2.0, ...) from the upstream repository into
# this fork and start the App Store release workflow for each new one.
#
# Why this exists: releases are tagged on the upstream repo (which ships
# Android), but GitHub never propagates tags to forks — "Sync fork" only
# moves branches. This fork ships iOS and carries iOS-only commits (Xcode
# Cloud ci_scripts, Info.plist keys, ...) that upstream does not have, so a
# mirrored tag must NOT point at the upstream commit: Xcode Cloud builds this
# fork, and the upstream tree would not even compile there. Instead, for each
# new upstream release tag this script:
#
#   1. merges the upstream release commit into the fork's main
#      (a no-op when the fork already contains it),
#   2. creates the same tag name on the fork's merged main — the iOS release
#      is "the upstream release plus this fork's iOS layer",
#   3. pushes main + tags, and
#   4. dispatches the App Store release workflow for each new tag.
#
# Step 4 is an explicit dispatch because pushes made with GITHUB_TOKEN never
# trigger `on: push` workflows (GitHub's Actions loop prevention). Xcode
# Cloud's GitHub App webhook is NOT affected by that rule, so the tag push
# alone is enough to start the build.
#
# Environment:
#   UPSTREAM_REPO      required, e.g. lkaesberg/MensaApp
#   MIN_VERSION        ignore upstream tags below this version (default 1.1.0)
#                      — pre-automation releases must never be re-released
#   RELEASE_WORKFLOW   workflow file to dispatch (default release-app-store.yml)
#   GH_TOKEN           token for `gh workflow run`
#   UPSTREAM_URL       override the upstream URL (used by tests)
#   SKIP_DISPATCH      set to skip `gh workflow run` (used by tests)

set -euo pipefail

UPSTREAM_REPO="${UPSTREAM_REPO:?set UPSTREAM_REPO, e.g. lkaesberg/MensaApp}"
UPSTREAM_URL="${UPSTREAM_URL:-https://github.com/${UPSTREAM_REPO}.git}"
MIN_VERSION="${MIN_VERSION:-1.1.0}"
RELEASE_WORKFLOW="${RELEASE_WORKFLOW:-release-app-store.yml}"

git config user.name >/dev/null 2>&1 || git config user.name "github-actions[bot]"
git config user.email >/dev/null 2>&1 || git config user.email "41898282+github-actions[bot]@users.noreply.github.com"

# Fetch upstream tags into a private namespace so they never collide with the
# fork's own tags of the same name. --no-tags is load-bearing: without it,
# git's tag auto-following would ALSO drop the upstream tags straight into
# refs/tags/, and the loop below would see them as already mirrored.
git fetch --quiet --no-tags "$UPSTREAM_URL" '+refs/tags/*:refs/upstream-tags/*'

version_lt() { # true if $1 < $2 (numeric dotted versions)
  [ "$1" != "$2" ] && [ "$(printf '%s\n%s\n' "$1" "$2" | sort -V | head -n 1)" = "$1" ]
}

new_tags=""
for tag in $(git for-each-ref --format='%(refname)' 'refs/upstream-tags/v[0-9]*' \
             | sed 's|^refs/upstream-tags/||' | sort -V); do
  if ! printf '%s' "$tag" | grep -Eq '^v[0-9]+(\.[0-9]+){0,2}$'; then
    echo "Skipping upstream tag '$tag' (not a release version tag)."
    continue
  fi
  if git rev-parse -q --verify "refs/tags/$tag" >/dev/null; then
    continue # already mirrored
  fi
  version="${tag#v}"
  if version_lt "$version" "$MIN_VERSION"; then
    echo "Skipping upstream tag '$tag' (below MIN_VERSION $MIN_VERSION — predates the iOS release automation)."
    continue
  fi

  commit="$(git rev-parse "refs/upstream-tags/$tag^{commit}")"
  if ! git merge-base --is-ancestor "$commit" HEAD; then
    echo "Merging upstream release commit $commit ($tag) into main…"
    if ! git merge --no-edit -m "Merge upstream release $tag" "$commit"; then
      git merge --abort || true
      echo "::error::Merging upstream release $tag into main has conflicts. Sync the fork manually (Sync fork button or 'git pull upstream main'), push, then re-run this workflow."
      exit 1
    fi
  fi

  # Preserve an annotated tag's message — it is a release-notes source.
  if [ "$(git cat-file -t "refs/upstream-tags/$tag")" = "tag" ]; then
    message="$(git for-each-ref --format='%(contents)' "refs/upstream-tags/$tag")"
    git tag -a "$tag" -m "${message:-$tag}" HEAD
  else
    git tag "$tag" HEAD
  fi
  echo "Mirrored $tag -> $(git rev-parse --short HEAD)"
  new_tags="$new_tags $tag"
done

if [ -z "$new_tags" ]; then
  echo "No new upstream release tags."
  exit 0
fi

# shellcheck disable=SC2046 — word splitting of the tag refs is intended
git push --quiet origin HEAD:main $(for t in $new_tags; do printf ' refs/tags/%s' "$t"; done)
echo "Pushed main and tags:$new_tags"

[ -n "${SKIP_DISPATCH:-}" ] && exit 0

dispatch_failed=0
for tag in $new_tags; do
  echo "Starting $RELEASE_WORKFLOW for $tag…"
  if ! gh workflow run "$RELEASE_WORKFLOW" --ref main -f "tag=$tag"; then
    echo "::error::Could not dispatch $RELEASE_WORKFLOW for $tag. Start it manually: Actions → Release to App Store → Run workflow → tag=$tag."
    dispatch_failed=1
  fi
done
exit "$dispatch_failed"
