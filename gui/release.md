# MarkdownToPdf GUI Release History
(Note, dates are in yyyy-MM-dd format)

## 0.2.0 (2026-08-15)
- Added a pluggable file-access layer for sandboxed distributions. Stored projects are retained
  when their files are temporarily inaccessible, and the application can ask to locate an
  inaccessible project Markdown file and remember the new location.
- App-store distributions can disable GitHub update checks with
  `-Dmd2pdf.update.enabled=false`; this also hides the related **Help** menu items.
- **View external** now lets sandboxed distributions choose where to save the rendered PDF before
  opening it in the system viewer. PDF export and file dialogs also handle inaccessible or stale
  initial directories more safely.
- CSS style parsing and syntax-highlight injection now behave consistently in every system locale.

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
