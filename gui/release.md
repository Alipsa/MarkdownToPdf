# MarkdownToPdf GUI Release docs
(Note, dates are in yyyy-MM-dd format)

## Release process

Run `./release.sh` from a clean `main` checkout. It downloads the artifacts from the green CI
run for `HEAD`, publishes the library to Maven Central, creates the version tag and opens the
GitHub release. Use `./release.sh --skip-deploy` only when Maven Central already received the
release and a later release step needs recovery; see the recovery instructions in
[`gui/readme.md`](readme.md).

# MarkdownToPdf GUI Release History
(Note, dates are in yyyy-MM-dd format)

## 0.1.1-SNAPSHOT
- Bundled-runtime packaging for Linux, macOS and Windows, plus a runtime-free `-no-jdk`
  archive.
- CI builds, installs and smoke-tests the platform archives and uploads release artifacts.

## v0.1.0 (2026-05-17)
- Initial release

