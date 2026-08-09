#!/usr/bin/env bash
# Asserts the installer's failure paths fail loudly. Platform-independent logic, so it
# runs on Linux only.
#
#   test-install-failures.sh <path-to-unpacked-zip-root>
set -euo pipefail

SRC="${1:?usage: test-install-failures.sh <unpacked-zip-root>}"
fail() { printf 'FAIL: %s\n' "$*" >&2; exit 1; }
ok()   { printf 'ok: %s\n' "$*"; }

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

# 1. A broken createLauncher.sh must fail the install. This previously printed the error
#    and exited 0, reporting a successful install that had no launcher.
copy="$WORK/broken"
cp -R "$SRC" "$copy"
printf '#!/usr/bin/env bash\nexit 3\n' > "$copy/MarkdownToPdf/createLauncher.sh"
chmod +x "$copy/MarkdownToPdf/createLauncher.sh"
if (cd "$copy" && bash md2pdf-install.sh "$WORK/dest-broken" < /dev/null); then
  fail "installer exited 0 with a failing createLauncher.sh"
fi
ok "launcher failure exits non-zero"

# 2. A non-interactive re-install over an existing install must cancel, not clobber.
good="$WORK/good"
cp -R "$SRC" "$good"
dest="$WORK/dest-good"
(cd "$good" && bash md2pdf-install.sh "$dest" < /dev/null) || fail "first install failed"
marker="$dest/DO-NOT-DELETE"
touch "$marker"
if (cd "$good" && bash md2pdf-install.sh "$dest" < /dev/null); then
  fail "non-interactive re-install exited 0 instead of cancelling"
fi
[ -f "$marker" ] || fail "non-interactive re-install destroyed the existing installation"
ok "non-interactive re-install cancels without clobbering"

# 3. MD2PDF_REPLACE_EXISTING=1 is the explicit opt-in and must go through.
(cd "$good" && MD2PDF_REPLACE_EXISTING=1 bash md2pdf-install.sh "$dest" < /dev/null) \
  || fail "MD2PDF_REPLACE_EXISTING=1 re-install failed"
[ ! -f "$marker" ] || fail "MD2PDF_REPLACE_EXISTING=1 did not actually replace"
ok "MD2PDF_REPLACE_EXISTING=1 replaces"

# 4. Installing a directory onto itself must be refused rather than emptying it. This is
#    what happens when the archive is unzipped straight into the install parent, so the
#    source MarkdownToPdf/ and the destination are literally the same directory.
selfroot="$WORK/self"
mkdir -p "$selfroot"
cp -R "$SRC/." "$selfroot/"
if (cd "$selfroot" && bash md2pdf-install.sh "$selfroot/MarkdownToPdf" < /dev/null); then
  fail "installing a directory onto itself exited 0"
fi
[ -f "$selfroot/MarkdownToPdf/MarkdownToPdf.jar" ] \
  || fail "installing a directory onto itself destroyed it"
ok "self-install refused"

# 5. A destination containing the source must also be refused. Checking only for equal
#    paths is not enough: rm -rf would otherwise delete the source before the copy.
ancestor="$WORK/ancestor"
mkdir -p "$ancestor"
cp -R "$SRC/." "$ancestor/"
if (cd "$ancestor" && bash md2pdf-install.sh "$ancestor" < /dev/null); then
  fail "installing into an ancestor of the source exited 0"
fi
[ -f "$ancestor/MarkdownToPdf/MarkdownToPdf.jar" ] \
  || fail "installing into an ancestor of the source destroyed it"
ok "ancestor install refused"

# 6. The filesystem root is an ancestor of every source. Preserve the explicit replacement
#    opt-in here so this test proves the overlap guard runs before rm -rf, rather than merely
#    exercising the non-interactive existing-destination refusal.
root_output="$WORK/root-output"
if (cd "$good" && MD2PDF_REPLACE_EXISTING=1 bash md2pdf-install.sh / < /dev/null) >"$root_output" 2>&1; then
  fail "installing into / exited 0"
fi
grep -q "overlap" "$root_output" || fail "installing into / did not report an overlap"
ok "root install refused"

printf '\nPASS: installer failure paths\n'
