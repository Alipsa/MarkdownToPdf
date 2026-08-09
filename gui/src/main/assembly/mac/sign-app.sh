#!/usr/bin/env bash
# Signs a MarkdownToPdf.app, inside-out.
#
#   sign-app.sh <path-to-.app>
#
# Apple requires nested code to be signed deepest-first, and "nested code" in a jlink
# image is more than the dylibs: bin/java, bin/keytool, bin/jrunscript and
# lib/jspawnhelper are all Mach-O. jspawnhelper is not in bin/ and is the one most often
# missed. Enumerating rather than listing is deliberate — a hardcoded list stops being
# correct the next time the module set changes, and surfaces as a rejected notarization
# months later.
set -euo pipefail

APP="${1:?usage: sign-app.sh <app-bundle>}"
[ -d "$APP" ] || { echo "no such bundle: $APP" >&2; exit 1; }

IDENTITY="$(security find-identity -v -p codesigning 2>/dev/null \
  | grep -m1 'Developer ID Application' \
  | sed -n 's/.*"\(.*\)".*/\1/p' || true)"

if [ -n "$IDENTITY" ]; then
  echo "Signing with: $IDENTITY"
  SIGN=(codesign --force --timestamp --sign "$IDENTITY")
else
  # Not an error: GitHub does not expose secrets to forked-PR runs, so fork builds take
  # this path correctly. Ad-hoc signatures satisfy the Apple Silicon kernel; they do not
  # satisfy Gatekeeper, which is why the installer still strips the quarantine flag.
  echo "No Developer ID found — ad-hoc signing"
  SIGN=(codesign --force --sign -)
fi

# Deepest first. -r on sort reverses the depth-sorted list so children precede parents.
while IFS= read -r f; do
  case "$(file -b "$f")" in
    *Mach-O*) "${SIGN[@]}" "$f" ;;
  esac
done < <(find "$APP" -type f \( -perm -u+x -o -name '*.dylib' \) \
         | awk -F/ '{print NF"\t"$0}' | sort -rn | cut -f2-)

"${SIGN[@]}" "$APP"
codesign --verify --deep --strict --verbose=2 "$APP"
echo "Signed and verified: $APP"
