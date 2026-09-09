#!/usr/bin/env bash
# Prints the CHANGELOG.md section for one version, so the release body is
# maintained in the repository instead of being retyped in the workflow.
set -euo pipefail

if [[ $# -lt 1 || $# -gt 2 ]]; then
    echo "Usage: $0 <version> [output-file]" >&2
    exit 2
fi

version=$1
output=${2:-}
changelog="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/CHANGELOG.md"

if [[ ! -f $changelog ]]; then
    echo "Missing $changelog" >&2
    exit 2
fi

# Everything between "## <version>" and the next "## " heading.
notes=$(awk -v want="## $version" '
    $0 == want { capture = 1; next }
    capture && /^## / { exit }
    capture { print }
' "$changelog")

# Drop leading and trailing blank lines so the published body starts at the
# first heading.
notes=$(printf '%s\n' "$notes" | sed -e '/./,$!d' | tac | sed -e '/./,$!d' | tac)

if [[ -z $notes ]]; then
    echo "CHANGELOG.md has no '## $version' section; add one before releasing" >&2
    exit 2
fi

if [[ -n $output ]]; then
    printf '%s\n' "$notes" > "$output"
else
    printf '%s\n' "$notes"
fi
