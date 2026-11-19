#!/usr/bin/env python3
"""Verify the 5.20 kit offline; --stage pushes source/tag and creates a normal-release draft."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile

KIT = Path(__file__).resolve().parent
REPO = "JDewitz/HakuX-Advanced"
URL = f"https://github.com/{REPO}.git"
TAG = "advanced-1.3.0-5.20"
BASE = "5a04012cd8706f57f3e0574a491f0a1702880264"
SOURCE = "refs/heads/release/5.20-goin-commando"

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
    require(meta["repository"] == REPO and meta["tag"] == TAG and meta["base_commit"] == BASE,
            "Unexpected repository, tag or base.")
    require(meta["prerelease"] is False and meta["version_code"] == 42 and
            meta["version_name"] == "1.3.0-advanced5.20-goin-commando", "Unexpected release identity.")
    require(meta["apk"] == "HakuX-Advanced-1.3.0-5.20-goin-commando-arm64.apk", "Unexpected APK name.")
    lines = (KIT / "SHA256SUMS.txt").read_text().splitlines()
    pairs = [line.split("  ", 1) for line in lines]
    expected = {p.name for p in KIT.iterdir() if p.is_file() and p.name != "SHA256SUMS.txt"}
    require(len(pairs) == len(expected) and {p[1] for p in pairs} == expected, "Checksum inventory differs.")
    for digest, name in pairs:
        require(Path(name).name == name and hashlib.sha256((KIT / name).read_bytes()).hexdigest() == digest,
                f"Checksum mismatch: {name}")
    require(hashlib.sha256((KIT / meta["apk"]).read_bytes()).hexdigest() == meta["apk_sha256"], "APK differs.")
    require(hashlib.sha256((KIT / "source.bundle").read_bytes()).hexdigest() == meta["bundle_sha256"], "Bundle differs.")
    print(f"Verified {REPO} {TAG} at {meta['source_commit']} (normal release)", flush=True)
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

    def git(cwd, *args):
        return run(git_exe, "-c", "credential.helper=", "-c", "credential.helper=!gh auth git-credential",
                   *args, cwd=cwd, env=env)

    def releases():
        pages = json.loads(run(gh_exe, "api", "--paginate", "--slurp", f"repos/{REPO}/releases?per_page=100"))
        return [r for page in pages for r in page if r.get("tag_name") == TAG]

    repo = json.loads(run(gh_exe, "api", f"repos/{REPO}"))
    require(repo.get("full_name", "").lower() == REPO.lower() and repo.get("default_branch") == "main",
            "Unexpected target repository or default branch.")
    require(not releases(), "A release/draft already exists for this tag. Review it on GitHub before retrying.")
    head = meta["source_commit"]
    with tempfile.TemporaryDirectory(prefix="hakux-release-") as cwd:
        git(cwd, "init", "-q")
        git(cwd, "fetch", "--no-tags", URL, "refs/heads/main")
        current = git(cwd, "rev-parse", "FETCH_HEAD")
        require(current in (BASE, head), f"Remote main is {current}; review new commits before proceeding.")
        git(cwd, "bundle", "verify", str(KIT / "source.bundle"))
        git(cwd, "fetch", str(KIT / "source.bundle"), SOURCE)
        require(git(cwd, "rev-parse", "FETCH_HEAD") == head, "Bundle source differs.")
        git(cwd, "merge-base", "--is-ancestor", BASE, head)
        tag_ref = f"refs/tags/{TAG}"
        refs = dict(line.split()[::-1] for line in git(cwd, "ls-remote", URL, tag_ref, tag_ref + "^{}").splitlines())
        if tag_ref in refs:
            require(refs.get(tag_ref + "^{}", refs[tag_ref]) == head, "Existing tag points elsewhere.")
        else:
            git(cwd, "-c", "user.name=HakuX-Advanced release", "-c",
                "user.email=release@users.noreply.github.com", "tag", "-a", TAG, head,
                "-m", "HakuX-Advanced 5.20 Goin Commando")
        push = [f"{head}:refs/heads/main"] if current != head else []
        if tag_ref not in refs:
            push.append(f"{tag_ref}:{tag_ref}")
        if push:
            git(cwd, "push", "--atomic", URL, *push)
    assets = [str(p) for p in sorted(KIT.iterdir()) if p.is_file()]
    print(run(gh_exe, "release", "create", TAG, *assets, "--repo", REPO, "--verify-tag",
              "--title", "HakuX-Advanced 5.20 · Goin Commando", "--notes-file", str(KIT / "RELEASE-NOTES.md"),
              "--draft", "--prerelease=false", "--latest=false"), flush=True)
    result = releases()
    require(len(result) == 1 and result[0]["draft"] and not result[0]["prerelease"], "Review draft state.")
    apks = [a for a in result[0]["assets"] if a["name"] == meta["apk"]]
    require(len(apks) == 1 and apks[0]["state"] == "uploaded" and
            apks[0]["size"] == (KIT / meta["apk"]).stat().st_size and
            apks[0].get("digest") == "sha256:" + meta["apk_sha256"], "Review uploaded APK digest.")
    print("Normal-release draft and APK verified. Publish manually only when ready to offer the updater.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--stage", action="store_true", help="Push source/tag and create a normal-release draft.")
    args = parser.parse_args()
    meta = verify()
    if args.stage:
        stage(meta)
    else:
        print("Local verification complete. No network or publishing action performed.")
