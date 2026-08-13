#!/usr/bin/env python3
"""Create, populate, and submit an App Store version for a tagged release.

Expects the binary itself to be built and uploaded by Xcode Cloud (its
tag-triggered workflow stamps MARKETING_VERSION with the tag). This script
then drives the App Store Connect API:

  1. Preflight: fail fast if the tag version is not higher than every
     already-submitted/live version (Apple would otherwise reject the upload
     silently via email).
  2. Wait until the build with CFBundleShortVersionString == <version> is
     uploaded and finishes processing (state VALID).
  3. Set export compliance on the build if missing (uses-non-exempt = false;
     the app declares ITSAppUsesNonExemptEncryption=false anyway).
  4. Create the App Store version (or retarget the existing editable one)
     with releaseType AFTER_APPROVAL, i.e. auto-release once approved.
  5. Attach the build and write the "What's New" text for every enabled
     localization (German locales get the German notes, everything else the
     English notes).
  6. Submit for review. A stale not-yet-in-review submission from a previous
     release is cancelled and superseded; an actively IN_REVIEW submission is
     never touched automatically.

Re-running is safe: every step is get-or-create/patch, and an already
submitted/live target version exits successfully as a no-op.

Environment (required):
  ASC_ISSUER_ID     App Store Connect API issuer ID
  ASC_KEY_ID        App Store Connect API key ID
  ASC_PRIVATE_KEY   Contents of the .p8 private key (App Manager role)

Usage: appstore_release.py --version 1.1.0 --notes notes.json
"""

from __future__ import annotations

import argparse
import json
import os
import sys
import time

import jwt  # PyJWT with the [crypto] extra
import requests

BASE_URL = "https://api.appstoreconnect.apple.com"
DEFAULT_BUNDLE_ID = "com.lkaesberg.mensaapp.ComposeApp"
TOKEN_LIFETIME_SECONDS = 15 * 60  # Apple allows at most 20 minutes

# appStoreVersions in these states can be edited/retargeted; anything else is
# submitted, live, or historical and blocks lower-or-equal version numbers.
EDITABLE_VERSION_STATES = {
    "PREPARE_FOR_SUBMISSION",
    "DEVELOPER_REJECTED",
    "REJECTED",
    "METADATA_REJECTED",
    "INVALID_BINARY",
}

# reviewSubmissions states that are still open (not COMPLETE/CANCELING).
OPEN_SUBMISSION_STATES = (
    "READY_FOR_REVIEW",
    "WAITING_FOR_REVIEW",
    "IN_REVIEW",
    "UNRESOLVED_ISSUES",
)


def log(message: str) -> None:
    print(time.strftime("[%H:%M:%S] ") + message, flush=True)


def fail(message: str) -> "NoReturn":  # noqa: F821
    print(f"::error::{message}", flush=True)
    sys.exit(1)


class AppStoreConnect:
    def __init__(self, issuer_id: str, key_id: str, private_key: str) -> None:
        self.issuer_id = issuer_id
        self.key_id = key_id
        self.private_key = private_key
        self._token: str | None = None
        self._token_expiry = 0.0
        self.session = requests.Session()

    def _bearer(self) -> str:
        now = time.time()
        if self._token is None or now > self._token_expiry - 60:
            expiry = now + TOKEN_LIFETIME_SECONDS
            self._token = jwt.encode(
                {
                    "iss": self.issuer_id,
                    "iat": int(now) - 10,
                    "exp": int(expiry),
                    "aud": "appstoreconnect-v1",
                },
                self.private_key,
                algorithm="ES256",
                headers={"kid": self.key_id, "typ": "JWT"},
            )
            self._token_expiry = expiry
        return self._token

    def request(
        self,
        method: str,
        path: str,
        *,
        params: dict | None = None,
        body: dict | None = None,
        ok_statuses: tuple[int, ...] = (200, 201, 204),
    ) -> dict | None:
        response = self.session.request(
            method,
            f"{BASE_URL}{path}",
            params=params,
            json=body,
            headers={"Authorization": f"Bearer {self._bearer()}"},
            timeout=60,
        )
        if response.status_code not in ok_statuses:
            detail = ""
            try:
                errors = response.json().get("errors", [])
                detail = "; ".join(
                    f"{e.get('code', '?')}: {e.get('title', '')} — {e.get('detail', '')}"
                    for e in errors
                )
            except Exception:  # noqa: BLE001
                detail = response.text[:2000]
            hint = ""
            if response.status_code == 401:
                hint = (
                    " (check ASC_ISSUER_ID / ASC_KEY_ID / ASC_PRIVATE_KEY — "
                    "the key may be revoked or the secret malformed)"
                )
            elif response.status_code == 403:
                hint = " (the API key needs the App Manager role)"
            fail(
                f"App Store Connect API {method} {path} failed with "
                f"HTTP {response.status_code}{hint}: {detail}"
            )
        if response.status_code == 204 or not response.content:
            return None
        return response.json()

    def get(self, path: str, **params) -> dict:
        return self.request("GET", path, params=params)

    def paged_data(self, path: str, **params) -> list[dict]:
        result = self.get(path, **params)
        data = list(result.get("data", []))
        while result.get("links", {}).get("next"):
            next_url = result["links"]["next"].removeprefix(BASE_URL)
            result = self.request("GET", next_url)
            data.extend(result.get("data", []))
        return data


# --------------------------------------------------------------------------
# Helpers
# --------------------------------------------------------------------------

def version_key(version_string: str) -> tuple[int, int, int] | None:
    parts = version_string.split(".")
    if not all(part.isdigit() for part in parts) or len(parts) > 3:
        return None
    numbers = tuple(int(part) for part in parts)
    return numbers + (0,) * (3 - len(numbers))  # type: ignore[return-value]


def version_state(version: dict) -> str:
    attributes = version.get("attributes", {})
    return attributes.get("appVersionState") or attributes.get("appStoreState") or ""


def find_app(api: AppStoreConnect, bundle_id: str) -> str:
    result = api.get("/v1/apps", **{"filter[bundleId]": bundle_id, "limit": "1"})
    data = result.get("data", [])
    if not data:
        fail(
            f"No app with bundle ID '{bundle_id}' is visible to this API key. "
            "Check the bundle ID and the key's app access."
        )
    return data[0]["id"]


def list_versions(api: AppStoreConnect, app_id: str) -> list[dict]:
    return api.paged_data(
        f"/v1/apps/{app_id}/appStoreVersions",
        **{"filter[platform]": "IOS", "limit": "200"},
    )


def preflight(versions: list[dict], target: str) -> None:
    """Fail early when the tag cannot become a new App Store version."""
    target_key = version_key(target)
    if target_key is None:
        fail(f"'{target}' is not a valid App Store version (need 1–3 numeric parts).")
    for version in versions:
        existing = version["attributes"].get("versionString", "")
        state = version_state(version)
        if state in EDITABLE_VERSION_STATES:
            continue
        existing_key = version_key(existing)
        if existing_key is None:
            continue
        if existing == target:
            log(
                f"Version {target} already exists in state {state}; "
                "nothing to do. ✅"
            )
            sys.exit(0)
        if existing_key >= target_key:
            fail(
                f"Tag version {target} is not higher than the existing version "
                f"{existing} (state {state}). Apple only accepts strictly "
                "increasing version numbers — delete the tag and re-tag with a "
                f"version above {existing}."
            )


def wait_for_build(
    api: AppStoreConnect, app_id: str, version: str, wait_minutes: int
) -> str:
    """Poll until the Xcode Cloud build for this version finishes processing."""
    deadline = time.time() + wait_minutes * 60
    base_filters = {
        "filter[app]": app_id,
        "filter[preReleaseVersion.version]": version,
        "filter[expired]": "false",
        "sort": "-uploadedDate",
        "limit": "1",
    }
    log(
        f"Waiting for a processed build with version {version} "
        f"(up to {wait_minutes} min — Xcode Cloud build + App Store processing)…"
    )
    while True:
        valid = api.get(
            "/v1/builds", **base_filters, **{"filter[processingState]": "VALID"}
        ).get("data", [])
        if valid:
            build_id = valid[0]["id"]
            log(f"Build ready: {build_id} (version {version}). ✅")
            return build_id

        broken = api.get(
            "/v1/builds",
            **base_filters,
            **{"filter[processingState]": "FAILED,INVALID"},
        ).get("data", [])
        if broken:
            state = broken[0]["attributes"].get("processingState")
            fail(
                f"The uploaded build for version {version} failed App Store "
                f"processing (state {state}). Apple emails the account holder "
                "with the reason; fix it and push a new tag."
            )

        if time.time() > deadline:
            fail(
                f"No processed build with version {version} appeared within "
                f"{wait_minutes} minutes. Check that (a) the Xcode Cloud "
                "workflow has a Tag start condition matching this tag and an "
                "Archive action with 'TestFlight and App Store' distribution, "
                "(b) the Xcode Cloud build succeeded, and (c) Apple did not "
                "email the account holder about a rejected upload. Re-run this "
                "workflow (workflow_dispatch) once the build exists."
            )
        time.sleep(60)


def ensure_export_compliance(api: AppStoreConnect, build_id: str) -> None:
    build = api.get(f"/v1/builds/{build_id}", **{"fields[builds]": "usesNonExemptEncryption"})
    uses = build.get("data", {}).get("attributes", {}).get("usesNonExemptEncryption")
    if uses is None:
        log("Build has no export-compliance answer; setting usesNonExemptEncryption=false.")
        api.request(
            "PATCH",
            f"/v1/builds/{build_id}",
            body={
                "data": {
                    "type": "builds",
                    "id": build_id,
                    "attributes": {"usesNonExemptEncryption": False},
                }
            },
        )


def ensure_version(
    api: AppStoreConnect, app_id: str, versions: list[dict], target: str
) -> str:
    """Reuse/retarget the editable App Store version, or create a new one."""
    editable = [v for v in versions if version_state(v) in EDITABLE_VERSION_STATES]

    exact = next(
        (v for v in editable if v["attributes"].get("versionString") == target), None
    )
    if exact is not None:
        version_id = exact["id"]
        log(f"Reusing editable App Store version {target} ({version_id}).")
        if exact["attributes"].get("releaseType") != "AFTER_APPROVAL":
            api.request(
                "PATCH",
                f"/v1/appStoreVersions/{version_id}",
                body={
                    "data": {
                        "type": "appStoreVersions",
                        "id": version_id,
                        "attributes": {"releaseType": "AFTER_APPROVAL"},
                    }
                },
            )
        return version_id

    if editable:
        # Only one editable version can exist at a time — retarget it.
        stale = editable[0]
        version_id = stale["id"]
        old = stale["attributes"].get("versionString")
        log(f"Retargeting existing editable version {old} -> {target} ({version_id}).")
        api.request(
            "PATCH",
            f"/v1/appStoreVersions/{version_id}",
            body={
                "data": {
                    "type": "appStoreVersions",
                    "id": version_id,
                    "attributes": {
                        "versionString": target,
                        "releaseType": "AFTER_APPROVAL",
                    },
                }
            },
        )
        return version_id

    log(f"Creating App Store version {target}.")
    created = api.request(
        "POST",
        "/v1/appStoreVersions",
        body={
            "data": {
                "type": "appStoreVersions",
                "attributes": {
                    "platform": "IOS",
                    "versionString": target,
                    "releaseType": "AFTER_APPROVAL",
                },
                "relationships": {
                    "app": {"data": {"type": "apps", "id": app_id}}
                },
            }
        },
    )
    return created["data"]["id"]


def attach_build(api: AppStoreConnect, version_id: str, build_id: str) -> None:
    api.request(
        "PATCH",
        f"/v1/appStoreVersions/{version_id}/relationships/build",
        body={"data": {"type": "builds", "id": build_id}},
    )
    log("Build attached to the version.")


def set_whats_new(api: AppStoreConnect, version_id: str, notes: dict) -> None:
    localizations = api.paged_data(
        f"/v1/appStoreVersions/{version_id}/appStoreVersionLocalizations",
        limit="50",
    )
    if not localizations:
        log(
            "WARNING: the version has no localizations; App Store Connect "
            "usually copies them from the live version. Submission may fail "
            "until metadata exists."
        )
        return
    for localization in localizations:
        locale = localization["attributes"].get("locale", "")
        text = notes["de"] if locale.lower().startswith("de") else notes["en"]
        api.request(
            "PATCH",
            f"/v1/appStoreVersionLocalizations/{localization['id']}",
            body={
                "data": {
                    "type": "appStoreVersionLocalizations",
                    "id": localization["id"],
                    "attributes": {"whatsNew": text},
                }
            },
        )
        log(f"Set What's New for locale {locale}.")


def submission_contains_version(
    api: AppStoreConnect, submission_id: str, version_id: str
) -> bool:
    items = api.paged_data(
        f"/v1/reviewSubmissions/{submission_id}/items",
        include="appStoreVersion",
        limit="50",
    )
    for item in items:
        relationship = (
            item.get("relationships", {}).get("appStoreVersion", {}).get("data") or {}
        )
        if relationship.get("id") == version_id:
            return True
    return False


def submit_for_review(
    api: AppStoreConnect, app_id: str, version_id: str, target: str
) -> None:
    open_submissions = api.get(
        "/v1/reviewSubmissions",
        **{
            "filter[app]": app_id,
            "filter[state]": ",".join(OPEN_SUBMISSION_STATES),
            "limit": "20",
        },
    ).get("data", [])

    submission_id = None
    cancelled_any = False
    for submission in open_submissions:
        state = submission["attributes"].get("state")
        if submission_contains_version(api, submission["id"], version_id):
            if state == "READY_FOR_REVIEW":
                submission_id = submission["id"]
                log(f"Reusing draft review submission {submission_id}.")
                break
            log(f"Version {target} is already submitted (state {state}). ✅")
            return
        if state == "IN_REVIEW":
            fail(
                f"Another submission is actively IN_REVIEW. Not cancelling it "
                f"automatically — resolve it in App Store Connect, then re-run "
                f"this workflow to submit {target}."
            )
        log(f"Cancelling stale review submission {submission['id']} (state {state}) "
            f"so {target} can supersede it.")
        api.request(
            "PATCH",
            f"/v1/reviewSubmissions/{submission['id']}",
            body={
                "data": {
                    "type": "reviewSubmissions",
                    "id": submission["id"],
                    "attributes": {"canceled": True},
                }
            },
        )
        cancelled_any = True
    if cancelled_any:
        time.sleep(10)  # give App Store Connect a moment before creating anew

    if submission_id is None:
        created = api.request(
            "POST",
            "/v1/reviewSubmissions",
            body={
                "data": {
                    "type": "reviewSubmissions",
                    "attributes": {"platform": "IOS"},
                    "relationships": {
                        "app": {"data": {"type": "apps", "id": app_id}}
                    },
                }
            },
        )
        submission_id = created["data"]["id"]

    if not submission_contains_version(api, submission_id, version_id):
        api.request(
            "POST",
            "/v1/reviewSubmissionItems",
            body={
                "data": {
                    "type": "reviewSubmissionItems",
                    "relationships": {
                        "reviewSubmission": {
                            "data": {
                                "type": "reviewSubmissions",
                                "id": submission_id,
                            }
                        },
                        "appStoreVersion": {
                            "data": {
                                "type": "appStoreVersions",
                                "id": version_id,
                            }
                        },
                    },
                }
            },
        )

    api.request(
        "PATCH",
        f"/v1/reviewSubmissions/{submission_id}",
        body={
            "data": {
                "type": "reviewSubmissions",
                "id": submission_id,
                "attributes": {"submitted": True},
            }
        },
    )


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--version", required=True, help="Version string, e.g. 1.1.0")
    parser.add_argument("--notes", required=True, help="Notes JSON from release_notes.py")
    parser.add_argument("--bundle-id", default=DEFAULT_BUNDLE_ID)
    parser.add_argument("--wait-minutes", type=int, default=90)
    args = parser.parse_args()

    issuer_id = (os.environ.get("ASC_ISSUER_ID") or "").strip()
    key_id = (os.environ.get("ASC_KEY_ID") or "").strip()
    private_key = (os.environ.get("ASC_PRIVATE_KEY") or "").strip()
    if not issuer_id or not key_id or not private_key:
        fail(
            "ASC_ISSUER_ID, ASC_KEY_ID, and ASC_PRIVATE_KEY must be set as "
            "repository secrets — see .github/RELEASING.md for the setup."
        )
    # Tolerate a key pasted as a single line with literal \n sequences.
    if "\\n" in private_key and "\n" not in private_key:
        private_key = private_key.replace("\\n", "\n")

    with open(args.notes, encoding="utf-8") as handle:
        notes = json.load(handle)

    api = AppStoreConnect(issuer_id, key_id, private_key)

    app_id = find_app(api, args.bundle_id)
    log(f"App: {args.bundle_id} ({app_id})")

    versions = list_versions(api, app_id)
    preflight(versions, args.version)

    build_id = wait_for_build(api, app_id, args.version, args.wait_minutes)
    ensure_export_compliance(api, build_id)

    version_id = ensure_version(api, app_id, versions, args.version)
    attach_build(api, version_id, build_id)
    set_whats_new(api, version_id, notes)

    submit_for_review(api, app_id, version_id, args.version)
    log(
        f"Version {args.version} submitted for review — it will be released "
        "automatically after approval. 🚀"
    )
    log(f"Track it: https://appstoreconnect.apple.com/apps/{app_id}/appstore")
    return 0


if __name__ == "__main__":
    sys.exit(main())
