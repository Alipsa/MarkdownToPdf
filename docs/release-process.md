# MarkdownToPdf release process

`lib` and `gui` release independently, on independent version numbers. `gui` releases never
touch Maven Central.

```
./release.sh lib [--skip-deploy]
./release.sh gui
```

Run either from a clean `main` checkout. Both download the artifacts from the green CI run for
`HEAD`, create the version tag, and open the GitHub release; `./release.sh lib` additionally
publishes the library to Maven Central. Use `./release.sh lib --skip-deploy` only when Maven
Central already received the release and a later release step needs recovery; see
[`gui/readme.md`](../gui/readme.md) for lib's and gui's recovery instructions.

## Before releasing a lib version

Bump `<revision>` in the root `pom.xml`, then give **both** relevant changelogs a
`## <version>` section — [`release.md`](../release.md) and [`lib/release.md`](../lib/release.md).
`release.md` covers the shared build, CI and release tooling; `lib/release.md` covers the
library itself. (`0.2.0` was already released under the legacy bare `v0.2.0` tag — the first
lib release under the new `md2pdf-v<version>` scheme must be a version bumped past `0.2.0`.)

## Before releasing a gui version

Bump **two** files in lockstep:

- `gui/pom.xml`'s own `<version>`.
- The dependency `<version>` in `gui/MarkdownToPdf.xml` — `release.sh gui` checks these match
  and refuses to proceed otherwise, but the values still have to be written by hand in both
  places.

Then give [`gui/release.md`](../gui/release.md) a `## <version>` section. On the first gui release
under the new `MarkdownToPdf-v<version>` tag scheme, `release.sh` also creates a bare
`v<version>` compatibility release with the same assets. This lets installed 0.2.0-and-earlier
clients, which only understand the old tag scheme, update into the prefix-aware checker. (`0.2.0`
was already released under the legacy bare `v0.2.0` tag — the first gui release under the new
scheme must be a version bumped past `0.2.0`.)

The legacy updater only looks at the repository-wide `releases/latest` endpoint, so this migration
window closes as soon as either module publishes another release. `release.sh` verifies that the
compatibility release initially wins GitHub's latest-release selection; after that, leave both lib
and gui releases quiet for at least seven days to give normally active legacy installs time to run
their daily update check. Run the first new-style gui release in an exclusive release window: a
concurrent release can displace the compatibility release before the script verifies it, leaving
both tags and releases to clean up manually.

## What `release.sh` checks

`release.sh` composes the GitHub release notes from the relevant changelog section(s) above and
checks them in its preconditions — before anything is downloaded and long before the irreversible
Maven Central deploy (lib only). A module whose heading still reads `-SNAPSHOT` has no section for
the version being released, and the run aborts with:

    ERROR: gui/release.md has no section for 0.1.1 — bump its heading from -SNAPSHOT before releasing

For a gui release, it also checks that `gui/MarkdownToPdf.xml`'s dependency version matches
`gui/pom.xml`'s version, and dies with a clear message before doing anything else if they've
drifted.

Push the release commit and wait for its CI run to go green before running `./release.sh lib [--skip-deploy]`
or `./release.sh gui`, as appropriate; the script releases the artifacts built by the run for
`HEAD`, so a later commit means a later CI run and a re-check.
