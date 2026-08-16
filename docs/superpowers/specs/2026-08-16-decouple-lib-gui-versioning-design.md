# Decouple `lib` and `gui` versioning — Design

**Status:** Revised after two rounds of external review, plus one self-caught gap. Round 1 found 4
blocking gaps (`UpdateChecker`, gui's `<parent>` reference, `gui/MarkdownToPdf.xml`, the
unscoped-`revision` script sweep). Round 2 found that round 1's own fix for gui's `<parent>`
reference doesn't build — verified empirically — and replaced the core mechanism with a version
that does (gui declares a literal `<version>`, its `<parent>` reference is never touched); also
tightened the `UpdateChecker` fix (top-level JSON array splitting, pagination) and settled the
pre-fix-install transition question (accept and document, not a bridge release). While sweeping
for other places the rejected mechanism's `-Dexpression=revision -pl gui` pattern had leaked into
`release.sh`/`ci.yml`/`install.sh`/`buildAndRun.sh`, found that gui's own runtime self-reported
version (`gui/pom.xml:190` → `MarkdownToPdf.properties` → both the About dialog and
`UpdateChecker`'s comparison baseline) has the same `${revision}` problem and needs the same fix.
Round 3 found that removing the `md2pdf.version` lockstep guard had left a real one uncovered
(`gui/MarkdownToPdf.xml`'s duplicated dependency version, unguarded by any build or script — now
restored as a `release.sh gui` precondition), replaced a "GitHub returns releases newest-first"
assumption with a max-by-version comparison that needs no such assumption, and tightened several
citations and a doc-update note. Round 4 filled in two remaining documentation gaps: the
`docs/release-process.md` rewrite had kept the changelog-bump instruction but dropped the
version-bump instruction (the one a maintainer actually needs, and the one place the
`gui/pom.xml`/`gui/MarkdownToPdf.xml` two-file split needs to be written down); and the
`CLAUDE.md:84` fix was scoped to one sentence when all three (plus `CLAUDE.md:43`) are affected.
Also noted the three existing `UpdateChecker` test classes whose fixtures the tag-scheme fix
invalidates. Round 5 found the module argument silently displaces `--skip-deploy`'s existing
positional parse (`./release.sh lib --skip-deploy` would parse as deploy-anyway, caught loudly by
the idempotency check but at the worst possible moment — mid-recovery) and specified the new
grammar; also found two docs describing the old invocation outside any prior update list —
`docs/release-process.md`'s own opening paragraph and `gui/readme.md`'s separate "Recovery after a
partial release" section, the latter being the exact page an operator is sent to mid-incident.
Round 6 caught that round 5's fix for that recovery section had over-corrected: it retargeted the
section at `lib` alone, conflating "only `lib` has an irreversible step" with "only `lib` has a
recovery path" and thereby deleting gui's own (fully reversible) partial-failure documentation —
for the release flow the whole design exists to make the frequent one. Split into two blocks, one
per module. Also restored a dropped `git push --delete origin` step and fixed an off-by-one line
citation. Re-approval needed before implementation planning.

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

## Chosen mechanism: gui declares its own literal `<version>`

Keep the single aggregator/reactor build (no CI restructuring, no splitting into separate Maven
root projects). The reserved-property restriction above governs how a POM's *own* `<version>`
element is written — it says nothing about a child module being required to derive that version
from a property at all. `gui/pom.xml` simply declares a literal version and otherwise touches
nothing:

```xml
<!-- pom.xml (parent) -->
<properties>
  <revision>0.2.0</revision>   <!-- lib's version, unchanged meaning -->
</properties>
```

```xml
<!-- gui/pom.xml -->
<parent>
  <groupId>se.alipsa</groupId>
  <artifactId>md2pdf-parent</artifactId>
  <version>${revision}</version>   <!-- UNCHANGED — must stay exactly as today, see rejected note -->
</parent>
<artifactId>MarkdownToPdf</artifactId>
<version>0.2.1</version>              <!-- gui's own version — bumped here at gui release time -->

<properties>
  <md2pdf.version>${revision}</md2pdf.version>   <!-- UNCHANGED — already tracks lib automatically -->
</properties>
```

`lib/pom.xml` needs no change — it continues to inherit `revision` from the parent, which
becomes, by convention, "lib's version."

A single `mvn install` still reactor-builds both modules exactly as today. `md2pdf.version` needs
**no lockstep discipline at all**: because it stays `${revision}` untouched, it resolves against
the *parent's* `revision` at gui's own build time and always equals lib's actual current version
automatically. There is nothing for a human, or `release.sh`, to keep in sync here.

flatten-maven-plugin's `resolveCiFriendliesOnly` mode leaves gui's literal `<version>` alone (it
was never a CI-friendly property) and correctly resolves the parent's `${revision}` reference —
untouched on both sides — into gui's own flattened, installed POM.

**Verified empirically:** `mvn install` with parent `revision=0.2.0` and gui declaring
`<version>0.2.1</version>` (parent reference left as `${revision}`) succeeds; `mvn -pl gui
org.apache.maven.plugins:maven-help-plugin:3.5.1:evaluate -Dexpression=project.version
-DforceStdout` returns `0.2.1`; the flattened, installed gui POM correctly reads
`<parent><version>0.2.0</version></parent>` alongside its own `<version>0.2.1</version>` — a
repository consumer resolves both artifacts correctly, unlike either alternative below.

This is a genuine improvement over today's shared-version build, not just a contained trap: because
`./release.sh lib` deploys `md2pdf-parent` itself to Maven Central (`-pl lib -am`), and gui's
flattened POM correctly references `md2pdf-parent` at lib's actual deployed version, gui's
installed POM is fully resolvable from a repository, not merely reactor-only. Round 1's latent
trap (a broken `<parent>` reference, contained only by never publishing gui) doesn't just get
worked around here — it stops existing.

### Rejected: overriding the `revision` property in gui's own `<properties>`

An earlier version of this design had `gui/pom.xml` override the `revision` property itself
(rather than declaring a plain `<version>`), on the theory that flatten-maven-plugin would then
resolve it the same way it resolves the parent's. This fails for two compounding reasons, both
verified empirically:

1. **The override leaks into gui's own `<parent>` reference.** `gui/pom.xml`'s `<parent>` block
   reads `<version>${revision}</version>` — the *same* property gui's `<properties>` block would
   be overriding. Both resolve within the same POM's effective model, so overriding `revision` for
   gui's own version also flips what the parent reference resolves to. The flattened, installed gui
   POM ends up with `<parent><version>0.2.1</version>` — gui's *own* version — pointing at a parent
   artifact that was only ever installed/deployed as `0.2.0`. Resolving gui from a repository
   (rather than the reactor, where `relativePath` papers over it) fails: `test.rev:parent:pom:0.2.1
   (absent)`.
2. **Patching that with a literal `<parent><version>` breaks the build outright, for a different
   reason.** Maven's `relativePath` matching — how the reactor finds the parent POM without a
   repository round-trip — compares the *raw, uninterpolated* version text in the child's
   `<parent><version>` against the raw text of the candidate parent's own `<version>`. The root
   POM's raw version is the literal string `${revision}`; a child declaring the literal `0.2.0` is
   not textually equal to that, so `relativePath` matching is rejected and Maven falls through to
   a remote lookup of a coordinate that has only ever existed in the reactor. Verified: `mvn
   install` fails at model-read time — before any module compiles — with `Non-resolvable parent
   POM ... 'parent.relativePath' points at wrong local POM`.

The chosen mechanism above avoids both failure modes because the `<parent><version>` element is
never touched on either side — it stays the literal text `${revision}` in both the parent POM and
gui's reference to it, so `relativePath` matching succeeds — and gui's own version lives in the
one place Maven has always supported a child overriding independently: its own `<version>`
element.

## `release.sh` becomes two commands, not one flag

Drop the `--module` flag idea — two positional invocations instead:

```
./release.sh lib
./release.sh gui
```

Both share the existing preconditions (clean tree, on `main`, `gh`/`mvn` available and
authenticated). `md2pdf.version` needs no lockstep precondition: since it stays `${revision}`
untouched (chosen mechanism, above), it automatically tracks lib's actual current version at every
gui build.

One manual-sync point remains, and it does need a precondition: `gui/MarkdownToPdf.xml`'s
dependency `<version>` (see below) is a second, independent place gui's version is written, and
nothing builds, tests, or otherwise touches that file — grepped across `.github/`, every `*.sh`,
and `docs/`, its only reference anywhere is `CLAUDE.md`. A stale value there is invisible until a
developer happens to run `mvn -f gui/MarkdownToPdf.xml javafx:run`, possibly several releases
later. `./release.sh gui` must assert `MarkdownToPdf.xml`'s dependency `<version>` equals
`gui/pom.xml`'s own `<version>` and die with a clear message if not, for the same reason the old
`md2pdf.version` drift guard existed — to fail fast in `release.sh` rather than let it surface
later as a confusing, unrelated build error.

### `--skip-deploy` grammar, now that `$1` is the module

`release.sh:13-14` currently parses `--skip-deploy` positionally: `[ "${1:-}" = "--skip-deploy" ]
&& SKIP_DEPLOY=1`. Introducing a mandatory module argument displaces this — `$1` becomes `lib`/`gui`,
so that check silently stops matching. `./release.sh lib --skip-deploy` would parse as
`SKIP_DEPLOY=0` and attempt the Central deploy anyway, during what is presumably a recovery attempt
for a release Central already has. The existing "already on Central" idempotency check (currently
`release.sh:53-56`) does catch this and `die`s rather than double-publishing, so it fails loudly,
not silently — but it fails at exactly the moment an operator is mid-incident, which is the worst
time for a confusing "why is this rejecting a flag I just passed" error. The new grammar must be
explicit, not left to fall out of wherever `$2` happens to land:

- `./release.sh lib [--skip-deploy]` — flag read from `$2`.
- `./release.sh gui` — **reject** `--skip-deploy` explicitly (`die` with a clear message) rather
  than silently accept and ignore it. Gui has no deploy step, so tolerating the flag would imply it
  did something.
- No argument, or an unrecognized first argument, must `die` rather than fall through to either
  flow.

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
- Asset checks — two distinct, separately-parameterized mechanisms in `release.sh`, both needing
  a per-module value once the flows split:
  - The file-**count** checks (`[ "$count" -eq 5 ]` pre-deploy, `[ "$count" -eq 6 ]` post-tag once
    `SHA256SUMS` is added) currently guard the combined lib+gui staging directory. Lib's own count
    becomes 2 (sources jar + javadoc jar), 3 with `SHA256SUMS`.
  - `asset_floor()` (a *per-asset byte-size floor*, not a count — it maps each expected filename
    to a minimum size so a truncated upload is caught) needs a new case arm for
    `*-sources.jar` (comparable in size to the existing `*-javadoc.jar` arm, since both come from
    `lib/pom.xml`'s standard jar bindings).

### `./release.sh gui`

- `$VERSION` read via `mvn -q -pl gui org.apache.maven.plugins:maven-help-plugin:3.5.1:evaluate
  -Dexpression=project.version -DforceStdout` — **not** `-Dexpression=revision`: gui no longer
  overrides `revision` (chosen mechanism, above), so evaluating `revision` scoped to `gui` would
  return the *parent's* value (lib's version) via ordinary property inheritance. Gui's own version
  lives in its `<version>` element, so `project.version` is the only expression that resolves to it.
- Tag: `MarkdownToPdf-v$VERSION` (was: bare `v$VERSION`).
- Release notes: composed from `gui/release.md` only.
- Assets: the existing four — `md2pdf-$VERSION-linux-x64.zip`, `md2pdf-$VERSION-macos-aarch64.zip`,
  `md2pdf-$VERSION-windows-x64.zip`, `md2pdf-$VERSION-no-jdk.zip`. (Filenames keep their existing
  `md2pdf-` prefix — that's a separate, pre-existing artifact-naming convention, not something
  this change touches. Only the *version number* inside those filenames now comes from gui's own
  `<version>` — not `revision`; see the "not `-Dexpression=revision`" note above.)
- **No Maven Central deploy step at all** — this is the entire point.
- File-count check becomes 4 (5 once `SHA256SUMS` is added), gui's own value distinct from lib's
  (see above). `asset_floor()`'s existing case arms (`*-linux-x64.zip`, `*-macos-aarch64.zip`,
  `*-windows-x64.zip`, `*-no-jdk.zip`) already cover every gui asset and need no change.

## CI (`ci.yml`) changes

The `build` job's "Read the project version" step currently evaluates `revision` unscoped (at
the repo root), which today happens to equal both lib's and gui's version. Once they diverge,
this step must produce **two** values, evaluated with two different expressions (not just two
different scopes — gui's version is no longer the `revision` property at all under the chosen
mechanism):

- `APP_VERSION` — `mvn -q -pl gui ... -Dexpression=project.version -DforceStdout` — used to name
  the packaged app zips and the no-jdk archive, exactly as `APP_VERSION` is used today. Scoping
  `-Dexpression=revision` to `-pl gui` would be wrong here: gui no longer overrides `revision`, so
  that would resolve to the parent's (lib's) value via inheritance, not gui's own.
- A second value for lib's version — `-Dexpression=revision` at the repo root (unchanged) — used
  to name the javadoc jar (already does this today via the same variable, so this must be split
  out) and the new sources jar upload step.

Both the existing "Build the javadoc jar" step and the new sources-jar upload step name their
artifacts from lib's version, not gui's.

### Other scripts that evaluate the shared `revision` unscoped

`ci.yml`'s `-Dexpression=revision` isn't the only unscoped read. Every one of these currently
evaluates the repo-root `revision` (today harmlessly equal to both modules' version) and must be
re-pointed at gui's own version wherever it names or unpacks a gui artifact — and, per the note
above, that means switching the expression to `project.version`, not just adding `-pl gui`:

- `install.sh:27` — `VERSION="$(mvn ... -Dexpression=revision ...)"`, then
  `install.sh:30` unzips `gui/target/md2pdf-$VERSION-$LABEL.zip` using that value. Must become
  `-pl gui -Dexpression=project.version` (mirroring `ci.yml`'s `APP_VERSION`) — scoping the
  existing `revision` expression to `gui` is not enough, since gui no longer overrides `revision`.
- `gui/buildAndRun.sh:16` — same `-Dexpression=revision` pattern, same fix
  (`-Dexpression=project.version`), feeding `gui/buildAndRun.sh:19`'s unzip of
  `gui/target/md2pdf-$VERSION-linux-x64.zip`.
- `release.sh:40` — the root-scoped `-Dexpression=revision` evaluate is correct for
  `./release.sh lib` (documented above) but `./release.sh gui` must use the
  `-Dexpression=project.version`-scoped evaluate already specified in that section; this bullet
  exists so the sweep doesn't stop at `ci.yml` and miss `release.sh`'s own dual-path requirement.

`gui/createApp.sh` needs no change: `app_version()` (`createApp.sh:79-89`) already reads the
version from the built jar's own `Implementation-Version` manifest entry rather than evaluating
the POM, so it always tracks gui's own resolved version regardless of how lib and gui diverge.

## gui's self-reported runtime version must also stop reading `${revision}` (blocking)

`gui/pom.xml:190` (a `maven-antrun-plugin` execution, needed because "javafx plugin creates its
own jar with a new manifest, so we must create a separate properties file instead") generates
`MarkdownToPdf.properties` with `<entry key="Implementation-Version" value="${revision}"/>`. This
is not just cosmetic: `MarkdownToPdf.java:1544` (`readCurrentVersion()`) reads that same
`Implementation-Version` key back out of the bundled properties file, and it feeds **two**
consumers — the About dialog (`MarkdownToPdf.java:1361`) and, critically,
`checkForUpdates`'s `currentVersion` (`MarkdownToPdf.java:1470-1486`), the exact baseline
`UpdateChecker.checkForUpdate` compares the latest release against.

Left unfixed, this doesn't just mean the app *reports* the wrong version — it means the build
produces **two build-time sources of truth that are guaranteed to disagree** the moment gui's
version diverges from lib's. `maven-jar-plugin`'s own manifest entry (`gui/pom.xml:234`,
`addDefaultImplementationEntries`) already correctly stamps `Implementation-Version: ${pom.version}`
— gui's own version, `0.2.1` — and that's what `gui/createApp.sh`'s `app_version()`
(`createApp.sh:79-89`) reads when naming the shipped zip. But `MarkdownToPdf.properties` (fed by
the antrun entry at `gui/pom.xml:190`) still says `0.2.0`, and that's what the *running app*
reads via `readCurrentVersion()`. The result: the shipped archive is correctly named
`md2pdf-0.2.1-...`, but the application inside it reports itself as `0.2.0` in the About dialog and
compares updates against `0.2.0` as the baseline — not a cosmetic mismatch, an internally
inconsistent build artifact.

**Fix:** change `gui/pom.xml:190`'s entry to `value="${project.version}"`, matching what
`maven-jar-plugin`'s own manifest entry already does correctly via `addDefaultImplementationEntries`
(`gui/pom.xml:234`; documented in the comment immediately above it, `gui/pom.xml:228-233`, as
producing `Implementation-Version: ${pom.version}`) — the antrun-generated properties file exists
only because the javafx plugin's own jar step doesn't preserve that manifest entry, so it needs
the same expression, not `${revision}`.

## `UpdateChecker` must learn the new tag scheme (blocking)

Not a documentation gap — this breaks a shipped feature. `UpdateChecker.checkForUpdate` hits
`DEFAULT_API_URL = ".../releases/latest"` (`UpdateChecker.java:29`), a **repo-wide** endpoint, then
strips a leading `"v"` (`UpdateChecker.java:93`) before comparing versions.

Two independent problems once tags become `MarkdownToPdf-v0.2.1` / `md2pdf-v0.2.0`:

1. **Wrong endpoint.** `releases/latest` returns whichever release GitHub considers most recent
   *overall* — a `lib`-only release published after a `gui` release becomes "latest" and shadows
   the gui release entirely, and it ships no platform zips at all.
2. **Wrong prefix stripped.** `tagName.startsWith("v")` is false for `MarkdownToPdf-v0.2.1`, so the
   whole tag string is treated as the version. `VersionComparator.parse` (truncates at the first
   `-`) then reduces that to `"MarkdownToPdf"`, `Integer.parseInt` throws, is caught, and `parse`
   returns `null` — `isNewer` returns `false`. There is an `LOGGER.info` line, but nothing at
   WARN/ERROR and no user ever sees application logs.

The user-facing effect is worse than silence: an interactive Help-menu check
(`MarkdownToPdf.java:1489-1503`) shows `Alerts.info("You are running the latest version...")` —
an actively misleading message, not just a missing notification. A background/startup check just
shows nothing. Either way, every future update check permanently reports no update available,
for every installed user, with no error surfaced anywhere.

**Required fix, in scope for this change:**

- Replace the `releases/latest` call with a fetch of the releases list (`GET /releases?per_page=100`)
  filtered to tags starting with `MarkdownToPdf-v`. `lib` releases (`md2pdf-v...`) are correctly
  ignored by this filter. Don't rely on GitHub's response ordering to find "the latest" among the
  matches — `created_at` (what `/releases` sorts by) reflects the tagged commit's date, not
  publish time, so a gui release cut from an older commit (a backport, a hotfix branched early)
  could sort below a newer `lib` release, and taking "the first match" would silently pick the
  wrong one. `VersionComparator` already exists for exactly this comparison: collect every
  `MarkdownToPdf-v*` match in the page and take the maximum by version, not the first one seen.
  Same amount of code, no ordering assumption to rely on.
  `per_page` matters regardless: GitHub defaults to 30 releases per page, and if more than 30
  `lib` releases land after the last `gui` release, an unpaginated fetch finds nothing — the same
  silent-failure shape this fix exists to close. Log a WARN (not INFO) when no matching release is
  found in the fetched page, so this failure mode is at least visible in logs even though it still
  can't surface to the user without adding a second network round-trip for pagination.
- The parsing work is larger than just "strip a different prefix." `GitHubReleaseJson` is built
  around a single-release response: `extractScalarBeforeAssets` (`GitHubReleaseJson.java:92-96`)
  finds a field's *first* occurrence anywhere in the given JSON text, scoped only by the
  first `"assets"` key it sees. Handed a top-level **array** of releases (as `GET /releases`
  returns), it would find the first release's fields regardless of which array element actually
  matches the `MarkdownToPdf-v` prefix. Filtering by tag prefix therefore requires splitting the
  top-level array into per-release JSON objects *before* calling `extractTagName`/`extractHtmlUrl`
  on each one individually. The bounded good news: `extractBracketedRegion` and
  `splitTopLevelObjects` (`GitHubReleaseJson.java:121-195`) are already public static and already
  do exactly this kind of top-level splitting for the `assets` array — the same two methods apply
  directly to splitting the outer releases array, so this is a real but scoped addition, not a
  rewrite.
- Strip the `MarkdownToPdf-v` prefix (not `v`) before handing the matched release's tag to
  `VersionComparator`.
- `UpdateChecker.java:113`'s asset-name construction (`"md2pdf-" + latestVersion +
  platform.assetSuffix()`) already matches the gui asset-naming convention (`md2pdf-$VERSION-...`,
  unchanged by this design — see the "Out of scope" note on artifact naming), so no change needed
  there once `latestVersion` is correctly parsed.

Three existing test classes hard-code assumptions this fix invalidates and need matching updates:
`UpdateCheckerHttpTest` (stubs the path `/releases/latest`, becomes `/releases?per_page=100`),
`GitHubReleaseJsonTest` (its fixture is a single `{...}` release object with `tag_name: "v0.1.2"`;
the array-splitting change needs a `[{...}, {...}]` fixture — worth asserting specifically on a
mixed-prefix case, e.g. a newer `md2pdf-v*` entry interleaved with an older `MarkdownToPdf-v*` one,
to prove the filter picks the right one), and `UpdateCheckerTest` (asserts bare `"v0.1.2"` tag
handling throughout; needs `MarkdownToPdf-v*`-tagged fixtures instead).

### Transition: installs already running the old `UpdateChecker`

The fix above only helps clients that already have it. An install running *today's* `UpdateChecker`
still hits `releases/latest` and strips a leading `"v"`. The moment the first `MarkdownToPdf-v...`
tag lands, that client parses the tag down to `"MarkdownToPdf"`, `VersionComparator` returns
`false`, and it reports "you are running the latest version" — permanently, with no way to
discover the release that contains the fix. This is a one-way door: once the tag scheme changes,
there is no in-app path back for those installs.

**Decision: accept the stranding and document it**, rather than adding a bridge release or
dual-tagging period. `gui/release.md`'s entry for the first release under the new tag scheme must
say plainly that installs predating this fix will not detect this or any future update
automatically, and should be updated manually from the GitHub releases page. This is a one-time,
one-directional cost — every install that updates manually once is carried onto the fixed checker
and unaffected from then on.

## `gui/MarkdownToPdf.xml` and `CLAUDE.md` (blocking)

`gui/MarkdownToPdf.xml` is a standalone POM (not a reactor module) that lets a developer run
`mvn -f gui/MarkdownToPdf.xml javafx:run` without the reactor build. It has its own `<parent>`
reference (`<version>${revision}</version>`, `MarkdownToPdf.xml:22-27`) and its own dependency on
the built gui artifact (`<version>${project.version}</version>`, `MarkdownToPdf.xml:33-37`).

`${project.version}` here resolves via this file's own (untouched) `<parent>` inheritance, which —
under the chosen mechanism — is still just `${revision}`, i.e. **lib's** version (`0.2.0`), while
the gui artifact it depends on is now installed under gui's own literal version (`0.2.1`). The
dependency resolution fails.

**Fix:** point the dependency's `<version>` at gui's own literal version directly
(`MarkdownToPdf.xml:35`), rather than `${project.version}`. This needs no new property or override
mechanism — it's the same one place already bumped at gui release time (`gui/pom.xml`'s own
`<version>`), just duplicated into this second, non-reactor file.

### CLAUDE.md needs three sentences rewritten, not one

CLAUDE.md's full line 84 reads: "The `${revision}` property in the root POM controls the version
for all modules. Bump it in `pom.xml` only; flatten-maven-plugin propagates it.
`gui/MarkdownToPdf.xml` inherits the root parent, so its launcher dependency follows `${revision}`
as well." All three sentences need rewriting, not just the third:

- Sentence 1 ("controls the version for all modules") becomes false — `revision` controls the
  parent and lib; gui has its own independent `<version>`.
- Sentence 2 ("Bump it in `pom.xml` only") is an active instruction that, if followed for a gui
  release, produces a wrong release — it must instead describe the two-file split above
  (`gui/pom.xml`'s `<version>` and `gui/MarkdownToPdf.xml`'s dependency `<version>`, kept in sync).
- Sentence 3 (the one already identified) needs to describe the direct reference, not inheritance.

CLAUDE.md's other `revision`-related line, line 43 ("a Maven multi-module project with
`${revision}` CI-friendly versioning"), is now only half-true — the parent and lib still use it,
but gui deliberately opts out. One added clause closes it (e.g. "...for `lib`; `gui` has its own
independent version, see below").

## User-facing download docs

`README.md:54-61` and `gui/readme.md:39-47` already use `md2pdf-<version>-*` placeholders rather
than a hardcoded version or URL, so nothing there breaks. But GitHub's own repo-sidebar "Latest"
badge is repo-wide, same as the `UpdateChecker` endpoint above — whenever a `lib` release is newest,
that badge points visitors at a release page with two jars and no application. Add one sentence to
each readme's download section: download the newest `MarkdownToPdf-v*` release, not whatever the
sidebar highlights as "Latest."

## `docs/release-process.md` rewrite

`docs/release-process.md:11` currently opens with the instruction that matters most and must not
be dropped: "Bump `<revision>` in the root `pom.xml`, then give **all three** changelogs a
`## <version>` section." Both halves of that sentence need splitting by module, not just the
changelog half:

- **lib release:** bump `<revision>` in the root `pom.xml` (unchanged from today).
- **gui release:** bump `<version>` in `gui/pom.xml` **and** the dependency `<version>` in
  `gui/MarkdownToPdf.xml` — the same two-file bump `release.sh gui`'s new precondition (above)
  verifies. Documenting only one of the two files is exactly how the `MarkdownToPdf.xml` half gets
  forgotten in practice; the doc and the guard must describe the same two-file bump.

Replace the single unified process description with two independent flows (mirroring the two
`release.sh` subsections above), each opening with its own version-bump instruction as above, and
drop the "all three changelogs need a heading for this version" gate in favor of: bump only the
changelog(s) for the module you're releasing (`lib/release.md` for a lib release, `gui/release.md`
for a gui release; `release.md` alongside `lib/release.md` since it's gated on lib's version).

`docs/release-process.md:3-6`'s own opening paragraph references the bare invocations directly
("Run `./release.sh` from a clean `main` checkout"; "Use `./release.sh --skip-deploy` only
when...") — name the new two-flow invocations here explicitly
(`./release.sh lib [--skip-deploy]`, `./release.sh gui`) rather than relying on the rewrite
elsewhere in the file to cover it implicitly. Round 4's lesson applies again: the instruction that
matters most is the one that goes stale silently when it isn't named.

### `gui/readme.md`'s recovery section splits into two, not one

`gui/readme.md:119-132` ("Recovery after a partial release") isn't touched by this design's other
`gui/readme.md` edit (the download-section note, above) — it's a separate part of the same file.
It documents the old grammar directly: `git push --delete origin v<version>`, `git tag -d
v<version>`, `gh release delete v<version> --yes`, `./release.sh --skip-deploy`. It is also the
exact file `docs/release-process.md:6` sends an operator to for recovery, so a stale command here
is the one hit mid-incident, not in idle reading.

The fix is **not** to retarget this section at lib alone. "Only `lib` has an irreversible step" is
true; "only `lib` has a recovery path" is not — those are different claims, and conflating them
deletes documentation gui still needs. Gui has its own partial-failure mode: `release.sh` step 7
pushes the tag, step 8 creates the GitHub release; if `gh release create` fails partway (auth
expiry, a network drop, an asset error uploading one of four ~100 MB zips), the operator is left
with `MarkdownToPdf-v<version>` pushed to the remote and no release. Re-running `./release.sh gui`
then dies in its own preconditions (`release.sh:46-47` check both the local *and* the remote tag),
exactly the same shape of failure `lib` can hit — gui just has no Central step to worry about
undoing.

Given the motivation section's own framing (gui releases are meant to become the *frequent* kind),
losing gui's recovery documentation would remove coverage for the failure case that ends up
happening most often. Split the section into two short blocks instead:

- **lib** (unchanged in substance, tag format updated to `md2pdf-v<version>`): `git push --delete
  origin md2pdf-v<version>`, `git tag -d md2pdf-v<version>`, `gh release delete md2pdf-v<version>
  --yes` (only if a partial release was created), `./release.sh lib --skip-deploy` — keep the
  "cannot be undone or repeated" framing for the Central-deploy step specifically.
- **gui** (new, same four-line shape, no `--skip-deploy`): `git push --delete origin
  MarkdownToPdf-v<version>`, `git tag -d MarkdownToPdf-v<version>`, `gh release delete
  MarkdownToPdf-v<version> --yes` (only if a partial release was created), `./release.sh gui`.
  State the actual distinction plainly: gui has no irreversible step, so recovery is just
  delete-and-re-run, every time.

## Knock-on effect: `MarkdownToPdf-dist`

`build-mas.sh --tag` already accepts an arbitrary git ref string — no code change is needed
there. Going forward, callers pass gui's tag (`MarkdownToPdf-vX.Y.Z`), not the old bare
`vX.Y.Z`. Only the script's usage comment/examples need updating to reflect the new tag name.

Pre-existing tags (bare `vX.Y.Z`, from before this change) are left untouched — not renamed,
not backfilled with the new prefix. Anyone pinned to one of those tags continues to resolve it
exactly as before; only the usage examples change, not historical refs.

## Out of scope

- Splitting `lib`/`gui` into fully independent top-level Maven projects (no shared parent POM).
  Rejected: more Maven-native, but requires restructuring CI to build and locally install `lib`
  before building `gui`, for no benefit this project currently needs — the reactor-build
  approach above achieves independent versioning without that restructuring.
- Renaming the `md2pdf-*` artifact/zip filename prefix to something gui-specific. Unrelated,
  pre-existing convention; not touched by this change.
