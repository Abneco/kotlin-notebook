#!/bin/bash
set -euo pipefail

KTNB_DIR="$(cd "$(dirname "$0")" && pwd)"
ULTIMATE_DIR="${ULTIMATE_DIR:-$(cd "$KTNB_DIR/../ultimate" && pwd)}"
PREFIX="plugins/kotlin/jupyter"

usage() {
    echo "Usage: $0 --from <commit-hash>" >&2
    echo "       $0 --after <commit-hash>" >&2
    echo "       $0 --exact <hash1> [hash2 ...]" >&2
    echo "  --from <hash>:    cherry-picks from <hash> to HEAD (inclusive)." >&2
    echo "  --after <hash>:   cherry-picks commits strictly after <hash> to HEAD." >&2
    echo "  --exact <hash>...: cherry-picks the specified commit(s) only." >&2
    echo "  ULTIMATE_DIR defaults to $KTNB_DIR/../ultimate" >&2
    exit 1
}

mode=""
if [ "${1:-}" = "--after" ] || [ "${1:-}" = "--from" ] || [ "${1:-}" = "--exact" ]; then
    mode="${1#--}"
    shift
else
    usage
fi

if [ $# -eq 0 ]; then
    usage
fi

all_hashes=()

if [ "$mode" = "exact" ]; then
    for arg in "$@"; do
        all_hashes+=("$(git -C "$ULTIMATE_DIR" rev-parse "$arg")")
    done
else
    if [ $# -ne 1 ]; then
        usage
    fi
    start_hash=$(git -C "$ULTIMATE_DIR" rev-parse "$1")
    if [ "$mode" = "after" ]; then
        range="${start_hash}..HEAD"
    else
        range="${start_hash}^..HEAD"
    fi
    while IFS= read -r line; do
        all_hashes+=("$line")
    done < <(git -C "$ULTIMATE_DIR" log --reverse --first-parent --format="%H" "$range")
fi

if [ ${#all_hashes[@]} -eq 0 ]; then
    echo "No commits found." >&2
    exit 1
fi

echo "Found ${#all_hashes[@]} commit(s) to process."

for full_hash in "${all_hashes[@]}"; do
    echo "Processing $full_hash ..."

    # Collect files in plugins/kotlin/jupyter/, excluding .iml and .bazel files
    files_raw=()
    while IFS= read -r line; do
        files_raw+=("$line")
    done < <(
        git -C "$ULTIMATE_DIR" show --name-only --format= "$full_hash" -- "$PREFIX/" \
            | grep "^$PREFIX/" \
            | grep -v '\.iml$' \
            | grep -v '\.bazel$' \
            || true
    )

    if [ ${#files_raw[@]} -eq 0 ]; then
        echo "  No relevant files in $PREFIX/ — skipping."
        continue
    fi

    # Generate filtered patch, strip the plugins/kotlin/jupyter/ prefix, and apply
    git -C "$ULTIMATE_DIR" show "$full_hash" -- "${files_raw[@]}" \
        | sed "s|a/$PREFIX/|a/|g; s|b/$PREFIX/|b/|g" \
        | git -C "$KTNB_DIR" apply

    # Stage the files (prefix stripped)
    for f in "${files_raw[@]}"; do
        git -C "$KTNB_DIR" add "${f#"$PREFIX/"}"
    done

    # Commit with original author info
    author_name=$(git -C "$ULTIMATE_DIR" log -1 --format="%an" "$full_hash")
    author_email=$(git -C "$ULTIMATE_DIR" log -1 --format="%ae" "$full_hash")
    author_date=$(git -C "$ULTIMATE_DIR" log -1 --format="%ad" "$full_hash")
    commit_msg=$(git -C "$ULTIMATE_DIR" log -1 --format="%B" "$full_hash")

    GIT_AUTHOR_NAME="$author_name" \
    GIT_AUTHOR_EMAIL="$author_email" \
    GIT_AUTHOR_DATE="$author_date" \
    git -C "$KTNB_DIR" commit -m "$commit_msg" -m "Cherry-picked from ultimate@${full_hash:0:8}"

    echo "  Done."
done
