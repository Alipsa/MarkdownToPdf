#!/usr/bin/env bash
# Asserts that a jar's manifest Class-Path and its lib/ directory agree exactly.
#
# A flat lib/ directory can silently lose a jar: copy-dependencies names files
# artifactId-version.jar, so two dependencies from different groups sharing both
# will overwrite each other. prependGroupId is not an option because
# maven-jar-plugin generates Class-Path entries with the plain layout and has no
# matching setting, so renaming the files would desynchronise them from the
# manifest. Comparing the two directly is what catches it.
set -euo pipefail

JAR="${1:?usage: check-lib-classpath.sh <jar> <libdir>}"
LIBDIR="${2:?usage: check-lib-classpath.sh <jar> <libdir>}"

fail() { printf 'FAIL: %s\n' "$*" >&2; exit 1; }

# Manifest values wrap at 72 bytes with a leading space on continuation lines,
# so the header has to be unfolded before it can be split on spaces.
classpath="$(unzip -p "$JAR" META-INF/MANIFEST.MF | tr -d '\r' | awk '
  /^Class-Path: / { cp = substr($0, 13); inCp = 1; next }
  inCp && /^ /    { cp = cp substr($0, 2); next }
  inCp            { inCp = 0 }
  END             { print cp }
')"

[ -n "$classpath" ] || fail "no Class-Path in $JAR manifest"

entries=()
for e in $classpath; do entries+=("$e"); done
[ "${#entries[@]}" -gt 0 ] || fail "empty Class-Path in $JAR"

# 1. every entry is under lib/ and resolves to a file
for e in "${entries[@]}"; do
  case "$e" in
    lib/*) ;;
    *) fail "Class-Path entry not under lib/: $e" ;;
  esac
  [ -f "$LIBDIR/${e#lib/}" ] || fail "Class-Path entry has no file: $e"
done

# 2. entries are unique — a duplicate name is what a filename collision produces
uniq_count="$(printf '%s\n' "${entries[@]}" | sort -u | wc -l | tr -d ' ')"
[ "$uniq_count" -eq "${#entries[@]}" ] \
  || fail "duplicate Class-Path entries: ${#entries[@]} listed, $uniq_count unique (filename collision in lib/)"

# 3. lib/ holds exactly those files and nothing else
file_count="$(find "$LIBDIR" -maxdepth 1 -name '*.jar' | wc -l | tr -d ' ')"
[ "$file_count" -eq "${#entries[@]}" ] \
  || fail "$LIBDIR has $file_count jars but Class-Path lists ${#entries[@]}"

# 4. scope regressions: provided and test dependencies must not be here
for pattern in 'javafx-*' 'junit-*' 'opentest4j-*' 'apiguardian-*'; do
  found="$(find "$LIBDIR" -maxdepth 1 -name "$pattern" -print -quit)"
  [ -z "$found" ] \
    || fail "$found is in $LIBDIR — copy-dependencies is missing includeScope=runtime"
done

printf 'OK: %s jars, Class-Path consistent\n' "${#entries[@]}"
