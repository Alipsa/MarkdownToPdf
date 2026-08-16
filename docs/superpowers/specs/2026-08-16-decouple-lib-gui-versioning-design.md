# Decouple `lib` and `gui` versioning — Design

**Status:** Approved by user, ready for implementation planning.

## Motivation

The parent aggregator, `md2pdf` (lib), and `MarkdownToPdf` (gui) currently share one version
number via the `revision` property, and `release.sh` releases all three together in one pass:
it deploys `md2pdf` to Maven Central, tags the single shared version, and creates one GitHub
release combining lib's javadoc jar with gui's platform zips.

This forces every gui-only change (e.g. a packaging fix, an icon asset, a `build-mas.sh`
compatibility fix with no `lib` changes at all) through a Maven Central deploy of an unchanged
library — Central deploys are irreversible and this is unnecessary churn. `gui/pom.xml` already
has an indirection (`<md2pdf.version>${revision}</md2pdf.version>`) suggesting decoupling was
anticipated but never finished.

**Goal:** `lib` and `gui` release independently, on independent version numbers, with `gui`
releases never touching Maven Central.

## Constraint: Maven's `<version>` element

Maven only resolves a small reserved set of property names — `revision`, `sha1`, `changelist` —
when used inside a `<version>` element; this is a Maven core restriction for "CI-friendly
versions," not just a `flatten-maven-plugin` convention. An arbitrary property name like
`${gui.version}` placed directly in a `<version>` tag will NOT resolve — Maven leaves it as a
literal unresolved string in the effective model.

This rules out simply introducing `<lib.version>`/`<gui.version>` properties and using them
inside `<version>` elements directly.

## Chosen mechanism: per-module `revision` override

Keep the single aggregator/reactor build (no CI restructuring, no splitting into separate Maven
root projects). `gui/pom.xml` overrides the reserved `revision` property in its own
`<properties>` block, shadowing the parent's value only for gui's own `<version>` resolution:

```xml
<!-- pom.xml (parent) -->
<properties>
  <revision>0.2.0</revision>   <!-- lib's version, unchanged meaning -->
</properties>
```

```xml
<!-- gui/pom.xml -->
<properties>
  <revision>0.2.1</revision>              <!-- overrides the parent's, just for gui -->
  <md2pdf.version>0.2.0</md2pdf.version>  <!-- gui's dependency on lib: explicit, since the
                                                parent's revision no longer equals lib's version
                                                from gui's point of view once they diverge -->
</properties>
```

`lib/pom.xml` needs no change — it continues to inherit `revision` from the parent, which
becomes, by convention, "lib's version."

A single `mvn install` still reactor-builds both modules exactly as today; Maven resolves the
inter-module dependency locally because `md2pdf.version` is kept in lockstep with lib's actual
`<version>` by whoever bumps versions at release time (see `release.sh` below) — it is never
allowed to drift from lib's real version, since that would break local reactor resolution and
force a remote (Central) lookup instead.

flatten-maven-plugin's `resolveCiFriendliesOnly` mode operates per-module on the effective model
being built, so it correctly flattens gui's overridden `revision` into gui's own
installed/deployed POM, independent of lib's.

## `release.sh` becomes two commands, not one flag

Drop the `--module` flag idea — two positional invocations instead:

```
./release.sh lib
./release.sh gui
```

Both share the existing preconditions (clean tree, on `main`, `gh`/`mvn` available and
authenticated), plus one new one that applies regardless of which module is being released:
`gui/pom.xml`'s `md2pdf.version` must equal lib's actual current `revision` exactly — a bump to
lib's version with no matching update to `gui/pom.xml` breaks local reactor resolution (Maven
would try to resolve the stale coordinate remotely and fail, since an unreleased lib bump isn't
on Central yet). `release.sh` checks this and dies with a clear message before doing anything
else, rather than let it surface as an opaque Maven resolution error mid-build.

From there the two flows diverge:

### `./release.sh lib`

- `$VERSION` read via the existing root-level `mvn ... evaluate -Dexpression=revision`
  (unchanged — this already reads lib's version, since lib inherits `revision` unmodified).
- Tag: `md2pdf-v$VERSION` (was: bare `v$VERSION`).
- Release notes: composed from `release.md` + `lib/release.md` only. The parent's version now
  always equals lib's, so gating `release.md`'s heading on lib releases keeps the "shared
  build/tooling" changelog meaningfully aligned with the version it's attached to.
- Assets: `md2pdf-$VERSION-sources.jar` and `md2pdf-$VERSION-javadoc.jar`. Both are already
  produced by `lib/pom.xml`'s unconditional `maven-source-plugin`/`maven-javadoc-plugin`
  bindings (`attach-sources`/`attach-javadocs`) — CI currently builds and uploads only the
  javadoc jar as a distinct artifact; add an equivalent upload step for the sources jar. Central
  itself already requires both for a valid deploy, so nothing changes about what gets published
  there — this only adds the sources jar as a downloadable GitHub release asset, matching what
  Central already requires and lib already builds.
- Maven Central deploy: unchanged (`mvn -Prelease -pl lib -am clean site deploy`), including the
  existing "already on Central" idempotency check and `--skip-deploy` recovery path.

### `./release.sh gui`

- `$VERSION` read via `mvn -q -pl gui org.apache.maven.plugins:maven-help-plugin:3.5.1:evaluate
  -Dexpression=revision -DforceStdout` — scoped to the `gui` module so it picks up gui's
  overridden `revision`, not the parent's.
- Tag: `MarkdownToPdf-v$VERSION` (was: bare `v$VERSION`).
- Release notes: composed from `gui/release.md` only.
- Assets: the existing four — `md2pdf-$VERSION-linux-x64.zip`, `md2pdf-$VERSION-macos-aarch64.zip`,
  `md2pdf-$VERSION-windows-x64.zip`, `md2pdf-$VERSION-no-jdk.zip`. (Filenames keep their existing
  `md2pdf-` prefix — that's a separate, pre-existing artifact-naming convention, not something
  this change touches. Only the *version number* inside those filenames now comes from gui's own
  `revision`.)
- **No Maven Central deploy step at all** — this is the entire point.

## CI (`ci.yml`) changes

The `build` job's "Read the project version" step currently evaluates `revision` unscoped (at
the repo root), which today happens to equal both lib's and gui's version. Once they diverge,
this step must produce **two** values:

- `APP_VERSION` — evaluated scoped to `gui` (`-pl gui`) — used to name the packaged app zips and
  the no-jdk archive, exactly as `APP_VERSION` is used today, just correctly scoped now.
- A second value for lib's version — used to name the javadoc jar (already does this today via
  the same variable, so this must be split out) and the new sources jar upload step.

Both the existing "Build the javadoc jar" step and the new sources-jar upload step name their
artifacts from lib's version, not gui's.

## `docs/release-process.md` rewrite

Replace the single unified process description with two independent flows (mirroring the two
subsections above), and drop the "all three changelogs need a heading for this version" gate in
favor of: bump only the changelog(s) for the module you're releasing (`lib/release.md` for a lib
release, `gui/release.md` for a gui release; `release.md` alongside `lib/release.md` since it's
gated on lib's version).

## Knock-on effect: `MarkdownToPdf-dist`

`build-mas.sh --tag` already accepts an arbitrary git ref string — no code change is needed
there. Going forward, callers pass gui's tag (`MarkdownToPdf-vX.Y.Z`), not the old bare
`vX.Y.Z`. Only the script's usage comment/examples need updating to reflect the new tag name.

## Out of scope

- Splitting `lib`/`gui` into fully independent top-level Maven projects (no shared parent POM).
  Rejected: more Maven-native, but requires restructuring CI to build and locally install `lib`
  before building `gui`, for no benefit this project currently needs — the reactor-build
  approach above achieves independent versioning without that restructuring.
- Renaming the `md2pdf-*` artifact/zip filename prefix to something gui-specific. Unrelated,
  pre-existing convention; not touched by this change.
