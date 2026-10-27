#!/usr/bin/env bash
#
# Installs a pre-push git hook that runs the headless review.
#
#   .claude/scripts/install-git-hooks.sh          # install
#   .claude/scripts/install-git-hooks.sh --remove # uninstall
#
# Deliberately opt-in rather than committed into the repo's hooks: a review that runs without
# being asked, costs tokens, and can block a push is something each person should choose.
# `git push --no-verify` always skips it.
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
HOOK="$PROJECT_DIR/.git/hooks/pre-push"

[ -d "$PROJECT_DIR/.git" ] || { echo "Not a git repository: $PROJECT_DIR" >&2; exit 1; }

if [ "${1:-}" = "--remove" ]; then
  rm -f "$HOOK"
  echo "Removed $HOOK"
  exit 0
fi

cat > "$HOOK" <<'HOOK_EOF'
#!/usr/bin/env bash
# Installed by .claude/scripts/install-git-hooks.sh — skip with `git push --no-verify`.
REPO="$(git rev-parse --show-toplevel)"

UPSTREAM="$(git rev-parse --abbrev-ref --symbolic-full-name '@{u}' 2>/dev/null || true)"
RANGE="${UPSTREAM:-HEAD~1}"

"$REPO/.claude/scripts/headless-review.sh" "$RANGE"
STATUS=$?

# Exit 2 means the review could not run (no CLI, no network). That must not block a push.
[ $STATUS -eq 2 ] && exit 0
exit $STATUS
HOOK_EOF

chmod +x "$HOOK"
echo "Installed $HOOK"
echo
echo "It reviews the commits you are about to push and blocks only on BLOCKER findings."
echo "Skip once with: git push --no-verify"
echo "Remove with:    .claude/scripts/install-git-hooks.sh --remove"
