# Markdown to PDF release

## Release process

Run `./release.sh` from a clean `main` checkout after the CI run for the release commit is
green. If Maven Central publication succeeds but tagging or GitHub release creation fails,
follow the recovery procedure in `gui/readme.md`, then run `./release.sh --skip-deploy`.

## Release history

### 0.1.1-SNAPSHOT (unreleased)
- Updated dependencies
  - spotless 3.5.1 -> 3.6.0
  - surefire 3.5.5 -> 3.5.6
- Improved documentation
- Added platform archives with bundled Java 25 runtimes for Linux, macOS and Windows.
- Added a runtime-free `-no-jdk` archive for JavaFX-bundled JDK 25+ users.
- Release artifacts are built and tested by CI; `./release.sh` downloads those artifacts,
  publishes the library to Maven Central, tags the commit and creates the GitHub release.

### 0.1.0 (2026-05-17)
- Initial release
