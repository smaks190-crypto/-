#!/usr/bin/env python3
"""
Argon4 Helper: Fetch and verify the structured CI Error Report artifact from GitHub Actions.
Implements the 'CI Error Report First' diagnostic protocol:
1. Locates the latest workflow run for a branch (or specific --run-id).
2. Verifies the commit SHA against the expected branch HEAD SHA.
3. Downloads ONLY the 'ci-error-report' artifact (ci-error-report.md + ci-error-report.json)
   without downloading full CI build logs.
"""

import argparse
import io
import json
import os
import sys
import urllib.request
import zipfile
from pathlib import Path
from typing import Any, Dict, Optional


def github_api_get(url: str, token: str, accept: str = "application/vnd.github+json") -> bytes:
    req = urllib.request.Request(
        url,
        headers={
            "Authorization": f"Bearer {token}",
            "Accept": accept,
            "X-GitHub-Api-Version": "2022-11-28",
            "User-Agent": "Argon4-CI-Error-Report-Fetcher",
        },
    )
    with urllib.request.urlopen(req, timeout=30) as resp:
        return resp.read()


def find_target_run(
    repo: str,
    token: str,
    branch: Optional[str],
    commit_sha: Optional[str],
    run_id: Optional[str],
) -> Dict[str, Any]:
    if run_id:
        url = f"https://api.github.com/repos/{repo}/actions/runs/{run_id}"
        return json.loads(github_api_get(url, token).decode("utf-8"))

    url = f"https://api.github.com/repos/{repo}/actions/runs?per_page=20"
    if branch:
        url += f"&branch={branch}"
    data = json.loads(github_api_get(url, token).decode("utf-8"))
    runs = data.get("workflow_runs") or []
    if not runs:
        raise RuntimeError(f"No workflow runs found for branch={branch!r}")

    if commit_sha:
        for r in runs:
            if str(r.get("head_sha", "")).startswith(commit_sha) or commit_sha.startswith(str(r.get("head_sha", ""))):
                return r
        raise RuntimeError(
            f"No workflow run found matching commit SHA {commit_sha} on branch {branch!r}. "
            f"Latest run head_sha is {runs[0].get('head_sha')}."
        )
    return runs[0]


def main() -> int:
    parser = argparse.ArgumentParser(description="Fetch structured CI Error Report artifact from GitHub Actions.")
    parser.add_argument("--repo", default="smaks190-crypto/-", help="GitHub repository (owner/name)")
    parser.add_argument("--branch", default="", help="Branch name to query")
    parser.add_argument("--commit-sha", default="", help="Expected commit SHA to verify against")
    parser.add_argument("--run-id", default="", help="Explicit GitHub Actions workflow run ID")
    parser.add_argument("--artifact-name", default="ci-error-report", help="Artifact name (default: ci-error-report)")
    parser.add_argument("--output-dir", default="/tmp/ci-error-report-fetched", help="Directory to extract report files")
    parser.add_argument("--print-md", action="store_true", help="Print ci-error-report.md to stdout")
    parser.add_argument("--print-json", action="store_true", help="Print ci-error-report.json to stdout")
    args = parser.parse_args()

    token = os.environ.get("GITHUB_TOKEN", "").strip()
    if not token:
        print("ERROR: GITHUB_TOKEN environment variable is required.", file=sys.stderr)
        return 2

    run = find_target_run(
        repo=args.repo,
        token=token,
        branch=args.branch or None,
        commit_sha=args.commit_sha or None,
        run_id=args.run_id or None,
    )
    actual_run_id = run["id"]
    head_sha = run.get("head_sha", "")
    status = run.get("status", "")
    conclusion = run.get("conclusion", "")

    if args.commit_sha and not head_sha.startswith(args.commit_sha):
        print(
            f"ERROR: Commit SHA mismatch! Expected {args.commit_sha}, but run {actual_run_id} has head_sha {head_sha}.",
            file=sys.stderr,
        )
        return 3

    artifacts_url = f"https://api.github.com/repos/{args.repo}/actions/runs/{actual_run_id}/artifacts"
    artifacts_data = json.loads(github_api_get(artifacts_url, token).decode("utf-8"))
    artifacts = artifacts_data.get("artifacts") or []

    target_artifact = None
    for art in artifacts:
        if art.get("name") == args.artifact_name and not art.get("expired", False):
            target_artifact = art
            break

    if not target_artifact:
        avail = [a.get("name") for a in artifacts]
        print(
            f"ERROR: Artifact '{args.artifact_name}' not found on run {actual_run_id} "
            f"(status={status}, conclusion={conclusion}, head_sha={head_sha}). Available artifacts: {avail}",
            file=sys.stderr,
        )
        return 4

    download_url = target_artifact["archive_download_url"]
    zip_bytes = github_api_get(download_url, token)

    out_dir = Path(args.output_dir)
    out_dir.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(io.BytesIO(zip_bytes)) as zf:
        zf.extractall(out_dir)

    md_path = out_dir / "ci-error-report.md"
    json_path = out_dir / "ci-error-report.json"

    if json_path.is_file():
        report_obj = json.loads(json_path.read_text(encoding="utf-8"))
        report_sha = report_obj.get("workflow", {}).get("commitSha", "")
        if args.commit_sha and report_sha and not report_sha.startswith(args.commit_sha):
            print(
                f"ERROR: Report internal commitSha ({report_sha}) does not match expected {args.commit_sha}!",
                file=sys.stderr,
            )
            return 5

    print(
        f"Fetched '{args.artifact_name}' from run {actual_run_id} "
        f"(branch={run.get('head_branch')}, head_sha={head_sha}, conclusion={conclusion}) -> {out_dir}"
    )

    if args.print_md and md_path.is_file():
        print("\n--- ci-error-report.md ---")
        print(md_path.read_text(encoding="utf-8"))
    if args.print_json and json_path.is_file():
        print("\n--- ci-error-report.json ---")
        print(json_path.read_text(encoding="utf-8"))

    return 0


if __name__ == "__main__":
    sys.exit(main())
