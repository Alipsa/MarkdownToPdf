#!/usr/bin/env bash
# Drives a MarkdownToPdf release. lib and gui release independently, on independent
# version numbers; gui releases never touch Maven Central.
#
#   ./release.sh lib [--skip-deploy]
#   ./release.sh gui
#
# Builds nothing. Every release asset comes from the CI run for HEAD, so what ships is
# byte-identical to what was tested. Runs on Linux or macOS.
set -euo pipefail

BASEDIR="$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" > /dev/null 2>&1 && pwd )"
cd "$BASEDIR"

die() { printf 'ERROR: %s\n' "$*" >&2; exit 1; }
step() { printf '\n=== %s\n' "$*"; }

MODULE="${1:-}"
case "$MODULE" in
  lib)
    SKIP_DEPLOY=0
    case "${2:-}" in
      "") ;;
      --skip-deploy) SKIP_DEPLOY=1 ;;
      *) die "unrecognized argument: ${2}. usage: ./release.sh lib [--skip-deploy]" ;;
    esac
    ;;
  gui)
    SKIP_DEPLOY=0
    case "${2:-}" in
      "") ;;
      --skip-deploy) die "gui has no Maven Central deploy step, so --skip-deploy does not apply. usage: ./release.sh gui" ;;
      *) die "unrecognized argument: ${2}. usage: ./release.sh gui" ;;
    esac
    ;;
  "")
    die "usage: ./release.sh lib [--skip-deploy] | ./release.sh gui"
    ;;
  *)
    die "unrecognized module: $MODULE. usage: ./release.sh lib [--skip-deploy] | ./release.sh gui"
    ;;
esac

# ── 1. preconditions ────────────────────────────────────────────────
step "Preconditions"

# sha256sum is GNU coreutils and absent on macOS, which ships shasum. Resolved here
# rather than at the point of use so an unusable host fails before anything is
# downloaded — and long before the irreversible step.
if command -v sha256sum > /dev/null; then
  sha256() { sha256sum "$@"; }
elif command -v shasum > /dev/null; then
  sha256() { shasum -a 256 "$@"; }
else
  die "need sha256sum or shasum"
fi

command -v gh  > /dev/null || die "gh is not installed"
command -v mvn > /dev/null || die "mvn is not installed"
gh auth status > /dev/null 2>&1 || die "gh is not authenticated"

[ -z "$(git status --porcelain)" ] || die "working tree is not clean"
[ "$(git rev-parse --abbrev-ref HEAD)" = "main" ] || die "not on main"

if [ "$MODULE" = "lib" ]; then
  VERSION="$(mvn -q org.apache.maven.plugins:maven-help-plugin:3.5.1:evaluate -Dexpression=revision -DforceStdout)"
  TAG="md2pdf-v$VERSION"
else
  VERSION="$(mvn -q -pl gui org.apache.maven.plugins:maven-help-plugin:3.5.1:evaluate -Dexpression=project.version -DforceStdout)"
  TAG="MarkdownToPdf-v$VERSION"
  # gui/MarkdownToPdf.xml duplicates gui's own version in a second, non-reactor file that
  # nothing else builds, tests, or touches (only CLAUDE.md references it) — a stale value
  # there is invisible until a developer runs `mvn -f gui/MarkdownToPdf.xml javafx:run`,
  # possibly releases later.
  LAUNCHER_VERSION="$(sed -n -e '/<artifactId>MarkdownToPdf<\/artifactId>/,/<\/dependency>/ s/.*<version>\(.*\)<\/version>.*/\1/p' gui/MarkdownToPdf.xml)"
  [ "$LAUNCHER_VERSION" = "$VERSION" ] \
    || die "gui/MarkdownToPdf.xml's dependency version ($LAUNCHER_VERSION) does not match gui/pom.xml's version ($VERSION) — bump both together before releasing"
fi
case "$VERSION" in *-SNAPSHOT) die "refusing to release a snapshot version: $VERSION" ;; esac
echo "Releasing $MODULE $VERSION"

git fetch --tags --quiet
git rev-parse -q --verify "refs/tags/$TAG" > /dev/null && die "tag $TAG already exists locally"
git ls-remote --exit-code --tags origin "$TAG" > /dev/null 2>&1 && die "tag $TAG already exists on the remote"

# Transitional guard: this repo's pre-existing releases were tagged bare "v<version>",
# before lib and gui split into their own tag schemes. It self-retires once every
# pre-split version has been superseded — no future version will ever collide with a
# legacy tag, only versions that were already released before this script existed.
git rev-parse -q --verify "refs/tags/v$VERSION" > /dev/null \
  && die "$VERSION was already released under the legacy tag v$VERSION — bump the version before releasing under the new $MODULE-specific tag scheme"
git ls-remote --exit-code --tags origin "v$VERSION" > /dev/null 2>&1 \
  && die "$VERSION was already released under the legacy tag v$VERSION — bump the version before releasing under the new $MODULE-specific tag scheme"

git push --dry-run --quiet origin HEAD || die "git push would fail"

if [ "$MODULE" = "lib" ]; then
  # The POM, not the directory: a directory listing can 200 on a partially-populated or
  # stale path, and only the POM's presence means the version is actually published.
  CENTRAL="https://repo1.maven.org/maven2/se/alipsa/md2pdf/$VERSION/md2pdf-$VERSION.pom"
  if curl -sfI "$CENTRAL" > /dev/null; then
    [ "$SKIP_DEPLOY" -eq 1 ] \
      || die "$VERSION is already on Maven Central. Maven Central cannot be overwritten; re-run with './release.sh lib --skip-deploy' to finish the rest of the release."
  fi
fi

# ── 1b. release notes ───────────────────────────────────────────────
# Composed here rather than at step 8: a module whose changelog still heads its section
# -SNAPSHOT has nothing to say about $VERSION, and that has to stop the release before the
# irreversible deploy — not surface as a published release page with a section missing.
step "Composing release notes"

# All three changelogs head every version section "## <version>", optionally followed by a
# date, so a section runs from that heading to the next "## " one. Only "## " closes a
# section: a deeper "### " subheading inside one is part of it and is kept. Leading and
# trailing blank lines are dropped so the composed file has no ragged gaps between sections.
extract_section() {
  awk -v ver="$2" '
    /^## / {
      if (started) { exit }
      heading = $0
      sub(/^##[[:space:]]+/, "", heading)
      if (heading == ver || index(heading, ver " ") == 1) { started = 1 }
      next
    }
    started {
      if (NF == 0) { if (printed) pending++; next }
      while (pending > 0) { print ""; pending-- }
      print; printed = 1
    }
  ' "$1"
}

# Outside $STAGING on purpose: step 3 empties that directory, and step 8 uploads every file
# in it as a release asset. .release-staging itself is gitignored.
NOTES="$BASEDIR/.release-staging/release-notes-$VERSION.md"
mkdir -p "$(dirname "$NOTES")"
: > "$NOTES"

add_section() {
  local title="$1" file="$2" body
  body="$(extract_section "$file" "$VERSION")"
  [ -n "$body" ] \
    || die "$file has no section for $VERSION — bump its heading from -SNAPSHOT before releasing"
  printf '## %s\n\n%s\n\n' "$title" "$body" >> "$NOTES"
}
if [ "$MODULE" = "lib" ]; then
  add_section "md2pdf-parent — build and release tooling" release.md
  add_section "md2pdf — library"                          lib/release.md
else
  add_section "MarkdownToPdf — desktop application"        gui/release.md
fi
cat "$NOTES"

# ── 2. wait for CI ──────────────────────────────────────────────────
step "Waiting for CI"
SHA="$(git rev-parse HEAD)"
RUN_ID="$(gh run list --commit "$SHA" --workflow ci.yml --limit 1 --json databaseId --jq '.[0].databaseId')"
[ -n "$RUN_ID" ] || die "no CI run for $SHA — push the commit and wait for CI to start"
# The whole run, not just the build jobs: verify, lint-scripts and install-failures are
# separate gates, and releasing past a failing test defeats the point of running it.
gh run watch "$RUN_ID" --exit-status || die "CI run $RUN_ID did not succeed"

# ── 3. download the assets ──────────────────────────────────────────
step "Downloading release assets"
# Keep staging outside target/: the release deploy includes `clean`, and -am brings the
# aggregator parent into the reactor, so Maven clean removes every module's target tree.
# This directory is ignored so a failed post-deploy recovery does not dirty the checkout.
STAGING="$BASEDIR/.release-staging/release-$VERSION"
# Emptied, not reused: the --skip-deploy recovery re-runs this step over a directory a
# previous attempt already populated, and a stale file here would ship unhashed under a
# SHA256SUMS that appears to account for it.
rm -rf "$STAGING"
mkdir -p "$STAGING"

if [ "$MODULE" = "lib" ]; then
  ASSETS=(
    "md2pdf-$VERSION-sources.jar"
    "md2pdf-$VERSION-javadoc.jar"
  )
else
  ASSETS=(
    "md2pdf-$VERSION-linux-x64.zip"
    "md2pdf-$VERSION-macos-aarch64.zip"
    "md2pdf-$VERSION-windows-x64.zip"
    "md2pdf-$VERSION-no-jdk.zip"
  )
fi

# One call per artifact. gh run download extracts multiple artifacts into separate
# subdirectories and only flattens when a single artifact is named — and the artifact
# name is the file's basename, so this loop is just the list above.
for asset in "${ASSETS[@]}"; do
  name="${asset%.*}"
  echo "  $asset"
  gh run download "$RUN_ID" -n "$name" -D "$STAGING" || die "could not download artifact $name"
done

# ── 4. sanity-check ─────────────────────────────────────────────────
step "Checking the staged assets"
if [ "$MODULE" = "lib" ]; then
  EXPECTED_COUNT=2
else
  EXPECTED_COUNT=4
fi
count="$(find "$STAGING" -maxdepth 1 -type f | wc -l | tr -d ' ')"
[ "$count" -eq "$EXPECTED_COUNT" ] || die "expected exactly $EXPECTED_COUNT files in $STAGING, found $count"
# Per-asset floors, because the assets differ by three orders of magnitude: a platform zip
# carries a ~100 MB runtime, the no-jdk zip is ~15 MB, and the javadoc/sources jars are each
# tens of KB. A single 1 MB floor would abort every release on the small jars.
asset_floor() {
  case "$1" in
    *-linux-x64.zip|*-macos-aarch64.zip|*-windows-x64.zip) echo 40000000 ;;  # 40 MB
    *-no-jdk.zip)                                          echo  5000000 ;;  #  5 MB
    *-javadoc.jar)                                         echo    10000 ;;  # 10 KB
    *-sources.jar)                                         echo     5000 ;;  #  5 KB
    *) echo 1 ;;
  esac
}
for asset in "${ASSETS[@]}"; do
  f="$STAGING/$asset"
  [ -f "$f" ] || die "missing: $asset"
  size="$(wc -c < "$f" | tr -d ' ')"
  floor="$(asset_floor "$asset")"
  [ "$size" -gt "$floor" ] || die "$asset is only $size bytes (expected more than $floor)"
done
if [ "$MODULE" = "gui" ]; then
  for label in linux-x64 macos-aarch64 windows-x64; do
    unzip -l "$STAGING/md2pdf-$VERSION-$label.zip" | grep -F 'MarkdownToPdf' > /dev/null \
      || die "md2pdf-$VERSION-$label.zip has no MarkdownToPdf entry"
  done
  unzip -l "$STAGING/md2pdf-$VERSION-no-jdk.zip" | grep -F 'MarkdownToPdf.jar' > /dev/null \
    || die "the no-jdk zip has no MarkdownToPdf.jar"
fi
echo "  $EXPECTED_COUNT assets OK"

# ── 5. checksums ────────────────────────────────────────────────────
step "Generating SHA256SUMS"
# Hashed by name from the staging directory so the file records bare basenames: a user
# who downloads the assets and SHA256SUMS into one directory can then run
# `sha256sum -c SHA256SUMS` with nothing else.
( cd "$STAGING" && sha256 "${ASSETS[@]}" > SHA256SUMS )
cat "$STAGING/SHA256SUMS"

# ── 6. Maven Central — the point of no return ───────────────────────
if [ "$MODULE" = "gui" ]; then
  step "No Maven Central deploy for gui — this is the entire point"
elif [ "$SKIP_DEPLOY" -eq 1 ]; then
  step "Skipping the Maven Central deploy (--skip-deploy)"
else
  step "Publishing lib to Maven Central"
  # -pl lib -am, never a bare deploy: the release profile is on the aggregator parent and
  # gui is a module, so an unqualified deploy would hand the GUI application to the
  # central-publishing plugin. -am is required because the parent POM is itself a
  # published artifact that lib resolves against.
  mvn -Prelease -pl lib -am clean site deploy
fi

# ── 7. tag ──────────────────────────────────────────────────────────
step "Tagging $TAG"
git tag -a "$TAG" -m "Release $VERSION"
git push origin "$TAG"

# ── 8. GitHub release ───────────────────────────────────────────────
step "Creating the GitHub release"
FINAL_COUNT=$((EXPECTED_COUNT + 1))
count="$(find "$STAGING" -maxdepth 1 -type f | wc -l | tr -d ' ')"
[ "$count" -eq "$FINAL_COUNT" ] || die "expected exactly $FINAL_COUNT files in $STAGING, found $count"
if [ "$MODULE" = "lib" ]; then
  TITLE="md2pdf $VERSION"
else
  TITLE="MarkdownToPdf $VERSION"
fi
# gh release create takes filenames or globs, never a directory.
gh release create "$TAG" "$STAGING"/* \
  --title "$TITLE" \
  --notes-file "$NOTES"

printf '\nReleased %s %s\n' "$MODULE" "$VERSION"
