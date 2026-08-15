# Markdown to PDF parent release history

This file covers the aggregator only — the shared build, CI and release tooling. Changes to
the published modules are recorded in their own changelogs:
[`lib/release.md`](lib/release.md) and [`gui/release.md`](gui/release.md).

How a release is cut is documented in [`docs/release-process.md`](docs/release-process.md).

## 0.2.0 (2026-08-15)
- No changes to the shared build, CI or release tooling.

## 0.1.1 (2026-08-11)
- Updated build tooling
  - spotless 3.5.1 -> 3.9.0 (google-java-format 1.22.0 -> 1.28.0)
  - spotbugs 4.9.8.3 -> 4.10.3.0
  - surefire 3.5.5 -> 3.5.6
- Restructured the README around the two-module layout and added a cross-platform
  installer script.
- Release artefacts are built and tested by CI; `./release.sh` downloads those artefacts,
  publishes the library to Maven Central, tags the commit and creates the GitHub release.

## 0.1.0 (2026-05-17)
- Initial release
