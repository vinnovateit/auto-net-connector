#!/usr/bin/env bash
# Opens a winget-pkgs pull request for one released version.
#
# Works entirely through the GitHub API rather than cloning: winget-pkgs holds
# roughly a million files and we are adding three.
set -euo pipefail

if [[ $# -ne 2 ]]; then
    echo "Usage: $0 <version> <manifest-dir>" >&2
    echo "  manifest-dir holds the three VinnovateIT.LatchCLI.*.yaml files" >&2
    exit 2
fi

version=$1
manifest_dir=$2

: "${GH_TOKEN:?a token with public_repo scope is required}"

upstream="microsoft/winget-pkgs"
fork="vinnovateit/winget-pkgs"
package_path="manifests/v/VinnovateIT/LatchCLI/$version"
branch="latch-cli-$version"

shopt -s nullglob
manifests=("$manifest_dir"/*.yaml)
shopt -u nullglob
if [[ ${#manifests[@]} -eq 0 ]]; then
    echo "No manifests found in $manifest_dir" >&2
    exit 2
fi

default_branch=$(gh api "repos/$upstream" --jq .default_branch)
echo "Upstream default branch: $default_branch"

# A stale fork would branch from old history; the PR would still apply, but
# syncing keeps the diff to just our three files. If the PAT lacks permission
# to sync (403), continue anyway — the PR will still be created.
if ! gh repo sync "$fork" --source "$upstream" --branch "$default_branch" --force; then
    echo "Warning: fork sync failed (token may lack repo scope); continuing anyway" >&2
fi

base_sha=$(gh api "repos/$fork/git/ref/heads/$default_branch" --jq .object.sha)

# Re-running a release should not fail on a leftover branch.
if gh api "repos/$fork/git/ref/heads/$branch" >/dev/null 2>&1; then
    echo "Branch $branch already exists on the fork; replacing it"
    gh api -X DELETE "repos/$fork/git/refs/heads/$branch"
fi

gh api -X POST "repos/$fork/git/refs" \
    -f "ref=refs/heads/$branch" \
    -f "sha=$base_sha" > /dev/null
echo "Created $fork:$branch from $base_sha"

for manifest in "${manifests[@]}"; do
    name=$(basename "$manifest")
    gh api -X PUT "repos/$fork/contents/$package_path/$name" \
        -f "message=Add VinnovateIT.LatchCLI $version ($name)" \
        -f "content=$(base64 -w0 < "$manifest")" \
        -f "branch=$branch" > /dev/null
    echo "Added $package_path/$name"
done

pr_url=$(gh pr create \
    --repo "$upstream" \
    --base "$default_branch" \
    --head "vinnovateit:$branch" \
    --title "New version: VinnovateIT.LatchCLI version $version" \
    --body "Automated submission from the [Latch release workflow](https://github.com/vinnovateit/latch/blob/main/.github/workflows/release.yml).

- Installer: https://github.com/vinnovateit/latch/releases/download/v$version/latch-cli-$version-windows-x64.zip
- Release: https://github.com/vinnovateit/latch/releases/tag/v$version

Checksums in the manifest are generated from the published release assets.")

echo "Opened $pr_url"
