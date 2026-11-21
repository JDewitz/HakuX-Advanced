#!/usr/bin/env python3
"""Verify a prepared Advanced kit offline; --stage pushes source/tag and creates an unpublished release."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile

KIT = Path(__file__).resolve().parent
REPO = "JDewitz/HakuX-Advanced"
URL = f"https://github.com/{REPO}.git"

def require(condition, message):
    if not condition:
        raise SystemExit(message)


def find_tool(name, winget_id, candidates):
    found = shutil.which(name)
    if found:
        return Path(found).resolve()
    if os.name == "nt":
        for root, relative_path in candidates:
            directory = os.environ.get(root)
            if directory:
                candidate = Path(directory) / relative_path
                if candidate.is_file():
                    return candidate
        raise SystemExit(
            f"Cannot find {name}.exe. Install it with:\n"
            f"  winget install --id {winget_id} -e\n"
            "Then open a new PowerShell window and rerun this command."
        )
    raise SystemExit(f"Cannot find {name}. Install it and make sure it is on PATH.")


def run(*args, cwd=None, env=None):
    try:
        result = subprocess.run(args, cwd=cwd, env=env, encoding="utf-8",
                                errors="replace", capture_output=True)
    except FileNotFoundError as exc:
        raise SystemExit(f"Could not start {args[0]}: {exc.strerror}") from None
    if result.returncode:
        raise SystemExit(result.stderr.strip() or result.stdout.strip() or f"{args[0]} failed")
    return result.stdout.strip()


def verify():
    meta = json.loads((KIT / "RELEASE-METADATA.json").read_text())
    version = meta["release_version"]
    require(re.fullmatch(r"[0-9]+[.][0-9]+(?:[.][0-9]+)?", version), "Invalid release version.")
    require(meta["repository"] == REPO and meta["tag"] == f"advanced-1.3.0-{version}", "Unexpected release target.")
    require(meta["prerelease"] is False and meta["version_code"] > 0 and
            meta["version_name"] == f"1.3.0-advanced{version}", "Unexpected release identity.")
    require(meta["apk"] == f"HakuX-Advanced-1.3.0-{version}-arm64.apk", "Unexpected APK name.")
    require(all(re.fullmatch(r"[0-9a-f]{40}", meta[k]) for k in ("source_commit", "base_commit")), "Invalid source identity.")
    require(meta["source_ref"] == f"refs/heads/release/{version}", "Unexpected source branch.")
    pairs = [line.split("  ", 1) for line in (KIT / "SHA256SUMS.txt").read_text().splitlines()]
    expected = {meta["apk"], "source.bundle", "RELEASE-METADATA.json", "RELEASE-NOTES.md",
                "VALIDATION.json", "START-HERE.md", "prepare-release.py"}
    require(len(pairs) == len(expected) and {p[1] for p in pairs} == expected, "Unexpected checksum inventory.")
    for digest, name in pairs:
        require(hashlib.sha256((KIT / name).read_bytes()).hexdigest() == digest, f"Checksum mismatch: {name}")
    require(hashlib.sha256((KIT / meta["apk"]).read_bytes()).hexdigest() == meta["apk_sha256"], "APK differs.")
    require(hashlib.sha256((KIT / "source.bundle").read_bytes()).hexdigest() == meta["bundle_sha256"], "Bundle differs.")
    print(f"Verified {REPO} {meta['tag']} at {meta['source_commit']} (normal release)", flush=True)
    return meta


def stage(meta):
    git_exe = find_tool("git", "Git.Git", [
        ("ProgramW6432", "Git/cmd/git.exe"), ("ProgramFiles", "Git/cmd/git.exe"),
        ("ProgramFiles(x86)", "Git/cmd/git.exe"), ("LOCALAPPDATA", "Programs/Git/cmd/git.exe")])
    gh_exe = find_tool("gh", "GitHub.cli", [
        ("ProgramW6432", "GitHub CLI/gh.exe"), ("ProgramFiles", "GitHub CLI/gh.exe"),
        ("LOCALAPPDATA", "Programs/GitHub CLI/gh.exe")])
    env = os.environ.copy()
    env["PATH"] = str(gh_exe.parent) + os.pathsep + env.get("PATH", "")
    run(gh_exe, "auth", "status")
    tag, head, base = meta["tag"], meta["source_commit"], meta["base_commit"]

    def git(cwd, *args):
        return run(git_exe, "-c", "credential.helper=", "-c", "credential.helper=!gh auth git-credential",
                   *args, cwd=cwd, env=env)

    def releases():
        # --paginate can return concatenated JSON arrays. Avoid CLI-version-specific JSON fields/--slurp.
        raw = run(gh_exe, "api", "--paginate", f"repos/{REPO}/releases?per_page=100")
        decoder, result = json.JSONDecoder(), []
        while raw.strip():
            page, end = decoder.raw_decode(raw.lstrip())
            result.extend(r for r in page if r.get("tag_name") == tag)
            raw = raw.lstrip()[end:]
        return result

    remote = json.loads(run(gh_exe, "api", f"repos/{REPO}"))
    require(remote.get("full_name", "").lower() == REPO.lower() and remote.get("default_branch") == "main",
            "Unexpected repository/default branch.")
    require(not releases(), "A release/draft already exists. Review it on GitHub before retrying.")
    with tempfile.TemporaryDirectory(prefix="hakux-release-") as cwd:
        git(cwd, "init", "-q")
        git(cwd, "fetch", "--depth=1", "--no-tags", URL, "refs/heads/main")
        current = git(cwd, "rev-parse", "FETCH_HEAD")
        require(current in (base, head), f"Remote main is {current}; review new commits before staging.")
        if current != base:
            git(cwd, "fetch", "--depth=1", "--no-tags", URL, base)
        git(cwd, "bundle", "verify", str(KIT / "source.bundle"))
        git(cwd, "fetch", "--update-shallow", str(KIT / "source.bundle"), meta["source_ref"])
        require(git(cwd, "rev-parse", "FETCH_HEAD") == head, "Fetched source differs.")
        git(cwd, "merge-base", "--is-ancestor", base, head)
        ref = f"refs/tags/{tag}"
        refs = dict(line.split()[::-1] for line in git(cwd, "ls-remote", URL, ref, ref + "^{}").splitlines())
        if ref in refs:
            require(refs.get(ref + "^{}", refs[ref]) == head, "Existing tag points elsewhere.")
        else:
            git(cwd, "-c", "user.name=HakuX-Advanced release", "-c",
                "user.email=release@users.noreply.github.com", "tag", "-a", tag, head,
                "-m", meta["title"])
        push = [f"{head}:refs/heads/main"] if current != head else []
        if ref not in refs:
            push.append(f"{ref}:{ref}")
        if push:
            git(cwd, "push", "--atomic", URL, *push)
    assets = [str(KIT / name) for name in sorted({p[1] for p in
              (line.split("  ", 1) for line in (KIT / "SHA256SUMS.txt").read_text().splitlines())} | {"SHA256SUMS.txt"})]
    print(run(gh_exe, "release", "create", tag, *assets, "--repo", REPO, "--verify-tag",
              "--title", meta["title"], "--notes-file", str(KIT / "RELEASE-NOTES.md"),
              "--draft", "--prerelease=false", "--latest=false"), flush=True)
    result = releases()
    require(len(result) == 1 and result[0]["draft"] and not result[0]["prerelease"], "Review draft state.")
    apks = [a for a in result[0]["assets"] if a["name"] == meta["apk"]]
    require(len(apks) == 1 and apks[0]["state"] == "uploaded" and
            apks[0]["size"] == (KIT / meta["apk"]).stat().st_size and
            apks[0].get("digest") == "sha256:" + meta["apk_sha256"], "Review uploaded APK digest.")
    print("Verified unpublished normal-release draft. Review and publish when ready.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--stage", action="store_true", help="Push source/tag and upload an unpublished normal-release draft.")
    args = parser.parse_args()
    meta = verify()
    if args.stage:
        stage(meta)
    else:
        print("Local verification complete. No push or publication performed.")
