# Markdown to PDF release history

## 0.1.1-SNAPSHOT
- Updated dependencies
  - spotless 3.5.1 -> 3.6.0
  - surefire 3.5.5 -> 3.5.6
- Improved documentation
- Renamed Md2PdfEngine.Job to Md2PdfEngine.Renderer
- Added platform archives with bundled Java 21 runtimes for Linux, macOS and Windows.
- Added a runtime-free `-no-jdk` archive for JavaFX-bundled JDK users.
- Release artifacts are built and tested by CI; `./release.sh` downloads those artifacts,
  publishes the library to Maven Central, tags the commit and creates the GitHub release.

## Release process

Run `./release.sh` from a clean `main` checkout after the CI run for the release commit is
green. If Maven Central publication succeeds but tagging or GitHub release creation fails,
follow the recovery procedure in `gui/readme.md`, then run `./release.sh --skip-deploy`.

## 0.1.0
- Initial release
