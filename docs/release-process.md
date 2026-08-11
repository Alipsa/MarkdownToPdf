# MarkdownToPdf release process

Run `./release.sh` from a clean `main` checkout. It downloads the artifacts from the green CI
run for `HEAD`, publishes the library to Maven Central, creates the version tag and opens the
GitHub release. Use `./release.sh --skip-deploy` only when Maven Central already received the
release and a later release step needs recovery; see the recovery instructions in
[`gui/readme.md`](../gui/readme.md).

## Before releasing

Bump `<revision>` in the root `pom.xml`, then give **all three** changelogs a
`## <version>` section — [`release.md`](../release.md), [`lib/release.md`](../lib/release.md)
and [`gui/release.md`](../gui/release.md). Each file covers only its own artifact:
`md2pdf-parent` for the shared build, CI and release tooling, `md2pdf` for the library, and
`MarkdownToPdf` for the desktop application.

`release.sh` composes the GitHub release notes from those three sections, one per artifact,
and checks them in its preconditions — before anything is downloaded and long before the
irreversible Maven Central deploy. A module whose heading still reads `-SNAPSHOT` has no
section for the version being released, and the run aborts with:

    ERROR: gui/release.md has no section for 0.1.1 — bump its heading from -SNAPSHOT before releasing

Push the release commit and wait for its CI run to go green before running `./release.sh`;
the script releases the artifacts built by the run for `HEAD`, so a later commit means a
later CI run and a re-check.

