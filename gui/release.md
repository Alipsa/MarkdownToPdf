# MarkdownToPdf GUI Release History
(Note, dates are in yyyy-MM-dd format)

## 0.1.1 (2026-08-11)
- Added an update check: **Help > Check for Updates…** asks GitHub Releases whether a newer
  version ships an archive for this platform, and offers to open the release page. It also
  runs automatically at startup — switch that off with **Help > Automatically check for
  updates**, or dismiss a single version with **Skip this version**.
- Added a splash screen shown while the application starts.
- Added drag-and-drop: dropping a Markdown file onto the editor opens it.
- Bundled-runtime packaging for Linux, macOS and Windows, plus a runtime-free `-no-jdk`
  archive.
- GUI builds and bundled runtimes use Java 25 with JavaFX 25; the `-no-jdk` archive requires
  a JavaFX-bundled JDK 25 or later.
- CI builds, installs and smoke-tests the platform archives and uploads release artifacts.

## 0.1.0 (2026-05-17)
- Initial release
