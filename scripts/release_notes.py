#!/usr/bin/env python3
"""Generate customer-facing App Store "What's New" notes for a release tag.

Source priority for the raw material:
  1. The GitHub release body for the tag (if a release exists and has one).
  2. The annotated tag message body.
  3. Commit subjects between the previous release tag and this tag.

If ANTHROPIC_API_KEY is set (and the `anthropic` package is installed), the
raw material is rewritten by Claude into short customer-facing notes in
English and German. Without it, a deterministic path is used: human-written
text is lightly tidied, commit subjects are filtered to customer-visible
conventional-commit types (feat/fix/perf), and the same text is used for both
languages. If nothing customer-relevant remains, a generic fallback is used.

Output: JSON {"version", "source", "ai", "en", "de"} written to --output.

Environment:
  GITHUB_TOKEN / GITHUB_REPOSITORY  used to look up the GitHub release body
  ANTHROPIC_API_KEY                 optional, enables AI-written notes
  RELEASE_NOTES_MODEL               optional model override (default claude-opus-5)
  GITHUB_STEP_SUMMARY               if set, a markdown summary is appended

Usage: release_notes.py --tag v1.1.0 --output notes.json [--repo-dir .]
"""

from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import sys
import urllib.error
import urllib.parse
import urllib.request

# App Store rejects "What's New" text longer than 4000 characters.
WHATS_NEW_LIMIT = 4000
MAX_COMMITS = 200
MAX_MATERIAL_CHARS = 8000

FALLBACK_EN = "Bug fixes and performance improvements."
FALLBACK_DE = "Fehlerbehebungen und Leistungsverbesserungen."


def log(message: str) -> None:
    print(message, file=sys.stderr, flush=True)


def run_git(repo_dir: str, *args: str) -> str | None:
    result = subprocess.run(
        ["git", "-C", repo_dir, *args], capture_output=True, text=True
    )
    if result.returncode != 0:
        return None
    return result.stdout


def truncate(text: str) -> str:
    if len(text) <= WHATS_NEW_LIMIT:
        return text
    return text[: WHATS_NEW_LIMIT - 1].rstrip() + "…"


# --------------------------------------------------------------------------
# Raw material
# --------------------------------------------------------------------------

def github_release_body(tag: str) -> str | None:
    """Release body for the tag — from this repo, else the upstream repo.

    Releases are published on the upstream repository (which ships Android);
    this fork mirrors only the tags. UPSTREAM_REPOSITORY makes the deliberate,
    human-written release description available to the iOS notes too.
    """
    token = os.environ.get("GITHUB_TOKEN") or os.environ.get("GH_TOKEN")
    if not token:
        return None
    repos = [
        os.environ.get("GITHUB_REPOSITORY"),
        os.environ.get("UPSTREAM_REPOSITORY"),
    ]
    api = os.environ.get("GITHUB_API_URL", "https://api.github.com")
    for repo in repos:
        if not repo:
            continue
        url = f"{api}/repos/{repo}/releases/tags/{urllib.parse.quote(tag)}"
        request = urllib.request.Request(
            url,
            headers={
                "Authorization": f"Bearer {token}",
                "Accept": "application/vnd.github+json",
                "X-GitHub-Api-Version": "2022-11-28",
            },
        )
        try:
            with urllib.request.urlopen(request, timeout=15) as response:
                body = (json.load(response).get("body") or "").strip()
                if body:
                    log(f"Using the GitHub release description from {repo}.")
                    return body
        except urllib.error.HTTPError as error:
            if error.code != 404:
                log(f"GitHub release lookup on {repo} failed with HTTP {error.code}; ignoring.")
        except Exception as error:  # noqa: BLE001 - never fail notes on the lookup
            log(f"GitHub release lookup on {repo} failed ({error}); ignoring.")
    return None


def annotated_tag_message(tag: str, repo_dir: str) -> str | None:
    # Lightweight tags have object type "commit"; their %(contents) would just
    # be the last commit message, which is not deliberate release text.
    object_type = run_git(repo_dir, "cat-file", "-t", f"refs/tags/{tag}")
    if object_type is None or object_type.strip() != "tag":
        return None
    contents = run_git(repo_dir, "tag", "-l", "--format=%(contents)", tag) or ""
    contents = re.sub(
        r"-----BEGIN PGP SIGNATURE-----.*?-----END PGP SIGNATURE-----\s*",
        "",
        contents,
        flags=re.S,
    )
    lines = contents.strip().splitlines()
    # Drop a first line that merely repeats the tag ("v1.1", "Release 1.1", ...).
    version = tag.lstrip("v")
    boilerplate = {
        tag.lower(),
        version.lower(),
        f"version {version}".lower(),
        f"release {tag}".lower(),
        f"release {version}".lower(),
    }
    if lines and lines[0].strip().lower() in boilerplate:
        lines = lines[1:]
    text = "\n".join(lines).strip()
    return text or None


def commit_subjects(tag: str, repo_dir: str) -> list[str]:
    previous = run_git(
        repo_dir, "describe", "--tags", "--match", "v[0-9]*", "--abbrev=0", f"{tag}^"
    )
    range_spec = f"{previous.strip()}..{tag}" if previous else tag
    output = run_git(repo_dir, "log", "--no-merges", "--pretty=format:%s", range_spec)
    if output is None:
        return []
    subjects = [line.strip() for line in output.splitlines() if line.strip()]
    return subjects[:MAX_COMMITS]


# --------------------------------------------------------------------------
# Deterministic notes
# --------------------------------------------------------------------------

def tidy_human_text(text: str) -> str | None:
    """Light cleanup of a human-written release body or tag message."""
    text = text.replace("\r\n", "\n").replace("\r", "\n")
    lines = []
    for line in text.split("\n"):
        stripped = line.strip()
        if re.match(r"^\**full changelog\**\s*:", stripped, re.I):
            continue
        stripped = re.sub(r"^#+\s*", "", stripped)          # markdown headers
        stripped = re.sub(r"^[-*+]\s+", "• ", stripped)     # markdown bullets
        stripped = re.sub(r"\s+by @[\w.-]+ in \S+$", "", stripped)  # PR credits
        stripped = stripped.replace("**", "")
        lines.append(stripped)
    result = re.sub(r"\n{3,}", "\n\n", "\n".join(lines)).strip()
    return result or None


def bullets_from_commits(subjects: list[str]) -> str | None:
    """Keep customer-visible conventional commits (feat/fix/perf) as bullets."""
    seen: set[str] = set()
    bullets: list[str] = []
    for subject in subjects:
        match = re.match(r"^(feat|fix|perf)(\([^)]*\))?!?:\s*(.+)$", subject, re.I)
        if not match:
            continue
        text = match.group(3).strip().rstrip(".")
        if not text or text.lower() in seen:
            continue
        seen.add(text.lower())
        bullets.append("• " + text[0].upper() + text[1:])
        if len(bullets) >= 12:
            break
    return "\n".join(bullets) or None


# --------------------------------------------------------------------------
# AI-written notes (optional)
# --------------------------------------------------------------------------

SOURCE_DESCRIPTIONS = {
    "github-release": "the GitHub release description written by the developer",
    "tag-message": "the annotated git tag message written by the developer",
    "commits": "the raw git commit subjects since the previous release",
}


def ai_notes(version: str, source: str, material: str) -> dict[str, str] | None:
    api_key = (os.environ.get("ANTHROPIC_API_KEY") or "").strip()
    if not api_key:
        return None
    try:
        import anthropic
    except ImportError:
        log(
            "ANTHROPIC_API_KEY is set but the 'anthropic' package is not "
            "installed; falling back to deterministic notes."
        )
        return None

    schema = {
        "type": "object",
        "properties": {
            "en": {"type": "string", "description": "English release notes"},
            "de": {"type": "string", "description": "German release notes"},
        },
        "required": ["en", "de"],
        "additionalProperties": False,
    }
    prompt = f"""You write the App Store "What's New" release notes for "Mensa App Göttingen", \
an app that shows the daily canteen (Mensa) menus in Göttingen.

Version {version} is being released. The raw release material below comes from \
{SOURCE_DESCRIPTIONS.get(source, source)}.

<material>
{material[:MAX_MATERIAL_CHARS]}
</material>

Write the customer-facing release notes:
- Mention only changes users can see or feel. Ignore internal work (CI, build tooling, \
refactoring, dependency updates, tests, code cleanup, release automation).
- Plain, friendly language. No technical jargon, no marketing fluff, no emoji.
- One line per change, each starting with "• ". At most 8 lines; merge related minor items.
- If NOTHING in the material is relevant to users, use exactly "{FALLBACK_EN}" for "en" \
and "{FALLBACK_DE}" for "de".
- Produce an English version ("en") and a German version ("de") with the same meaning. \
The German version uses informal "du"."""

    try:
        client = anthropic.Anthropic(api_key=api_key)
        response = client.messages.create(
            model=os.environ.get("RELEASE_NOTES_MODEL", "claude-opus-5"),
            max_tokens=8000,
            output_config={
                "effort": "low",
                "format": {"type": "json_schema", "schema": schema},
            },
            messages=[{"role": "user", "content": prompt}],
        )
        if response.stop_reason == "refusal":
            log("Claude declined the request; falling back to deterministic notes.")
            return None
        text = next(
            block.text for block in response.content if block.type == "text"
        )
        data = json.loads(text)
        en = (data.get("en") or "").strip()
        de = (data.get("de") or "").strip()
        if en and de:
            return {"en": truncate(en), "de": truncate(de)}
        log("Claude returned empty notes; falling back to deterministic notes.")
    except Exception as error:  # noqa: BLE001 - AI polish must never break a release
        log(f"Claude release-notes generation failed ({error}); "
            "falling back to deterministic notes.")
    return None


# --------------------------------------------------------------------------
# Main
# --------------------------------------------------------------------------

def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--tag", required=True, help="Release tag, e.g. v1.1.0")
    parser.add_argument("--output", required=True, help="Path for the notes JSON")
    parser.add_argument("--repo-dir", default=".", help="Git repository directory")
    args = parser.parse_args()

    tag = args.tag
    version = tag.lstrip("v")

    human_text = None
    source = "commits"
    body = github_release_body(tag)
    if body:
        tidied = tidy_human_text(body)
        if tidied:
            human_text, source = tidied, "github-release"
    if not human_text:
        message = annotated_tag_message(tag, args.repo_dir)
        if message:
            tidied = tidy_human_text(message)
            if tidied:
                human_text, source = tidied, "tag-message"

    subjects = commit_subjects(tag, args.repo_dir) if not human_text else []
    material = human_text or "\n".join(f"- {subject}" for subject in subjects)

    notes = None
    ai_used = False
    if material:
        notes = ai_notes(version, source, material)
        ai_used = notes is not None
    if notes is None:
        deterministic = human_text or bullets_from_commits(subjects)
        if deterministic:
            # Without AI there is no translation; ship the same text for both
            # locales (matches the existing Play Store release flow).
            notes = {"en": truncate(deterministic), "de": truncate(deterministic)}
        else:
            notes, source = {"en": FALLBACK_EN, "de": FALLBACK_DE}, "fallback"

    result = {
        "version": version,
        "source": source,
        "ai": ai_used,
        "en": notes["en"],
        "de": notes["de"],
    }
    with open(args.output, "w", encoding="utf-8") as handle:
        json.dump(result, handle, ensure_ascii=False, indent=2)

    log(f"Release notes for {version} (source: {source}, ai: {ai_used}):")
    log("--- en ---\n" + result["en"])
    log("--- de ---\n" + result["de"])

    summary_path = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary_path:
        with open(summary_path, "a", encoding="utf-8") as summary:
            summary.write(
                f"## App Store release notes — {version}\n\n"
                f"Source: `{source}`, AI-written: `{ai_used}`\n\n"
                f"**English**\n\n{result['en']}\n\n"
                f"**German**\n\n{result['de']}\n"
            )
    return 0


if __name__ == "__main__":
    sys.exit(main())
