# Drag-and-drop Markdown loading — Design Spec

**Date:** 2026-08-07
**Status:** Approved

## Summary

Let users drop a `.md`/`.markdown` file onto the Markdown editor pane to open it, reusing the existing `MarkdownTab.loadFile(Path)` load path (already used by project loading). Also documents the previously-undocumented `install.sh` one-command Mac/Linux install script in the top-level and GUI READMEs.

---

## 1. Drop zone

`MarkdownTab`'s editor pane (`editorBox`, wrapping `markdownArea`) gets `setOnDragOver` / `setOnDragDropped` handlers, installed in the `MarkdownTab` constructor next to the existing preview-refresh wiring. Drops elsewhere in the window (Style tab, PDF tab) are not handled.

## 2. Accept condition (`onDragOver`)

Accept the drag with `TransferMode.COPY` only when the dragboard has files and it contains exactly one file whose name ends in `.md` or `.markdown` (case-insensitive) — the same extensions `promptAndLoad()` filters on. Otherwise the event is not accepted, so the OS gives no "drop allowed" affordance for multi-file or wrong-extension drags.

## 3. Drop handling (`onDragDropped`)

1. Re-validate the dragboard has exactly one file with a `.md`/`.markdown` extension. If not, set `success = false`, call `gui.setStatus("Drop a single .md file to open it")`, consume the event, and return.
2. If valid and `isChanged()` is `true`, show `Alerts.confirm("Unsaved changes", "The markdown file has unsaved changes.", "Discard changes and open the dropped file?")` — same pattern as `newDocument()`. If the user declines, set `success = false` and stop.
3. Otherwise:
   - Call `loadFile(path)` (existing method — reads the file, sets the tab title, clears the dirty flag, associates the file).
   - Call `gui.setProjectMarkdownFile(path)` (same call `promptAndLoad()` makes) so an active project stays in sync.
   - Call `gui.setStatus("Loaded " + path.getFileName())`.
   - Set `success = true`.
4. Set `event.setDropCompleted(success)` and consume the event.

## 4. Constraints

- No new Maven dependencies — uses JavaFX's built-in `Dragboard` / `TransferMode` API only.
- No unit test coverage: this project has no TestFX/JavaFX UI-testing dependency, so this is verified by manually running the GUI and dragging a file onto the editor.

---

## 5. Documentation fix (unrelated, bundled housekeeping)

`install.sh` (repo root) runs `mvn install`, builds the app bundle via `gui/createApp.sh`, unzips it into `~/Applications` (macOS) or `~/.local/share` (Linux), and on Linux also runs `createLauncher.sh` — a single-command build+install path that exists today but is mentioned in neither `README.md` nor `gui/readme.md`. Add a short section to `gui/readme.md`'s **Running** section (and a pointer from the root `README.md`) documenting `./install.sh [installDir]` as the one-command way to build and install on macOS/Linux.

## 6. `macInstall.sh` — end-user install script shipped inside the release zip

`install.sh` is a *developer* script: it builds from source. There is no equivalent for someone who just downloads the pre-built `md2pdf-gui.zip` release asset — today they must manually drag `MarkdownToPdf.app` into Applications and right-click → Open to bypass Gatekeeper quarantine. Add `macInstall.sh`, shipped at the root of the zip (a sibling of `MarkdownToPdf.app`, not inside the `.app` bundle), that:

1. Locates `MarkdownToPdf.app` next to itself (via `${0:A:h}`, zsh idiom for the script's own directory); aborts with an error if not found.
2. Targets `~/Applications` (matching `install.sh`'s existing macOS default — no `sudo` needed), creating it if missing.
3. If `~/Applications/MarkdownToPdf.app` already exists, prompts `y/n` to replace it; aborts cleanly on "n" without touching anything.
4. Copies (does not move) the `.app` bundle into `~/Applications`.
5. Removes the quarantine extended attribute recursively: `xattr -dr com.apple.quarantine <installed-app>` (ignoring failure if the attribute is already absent) — this is the "not quarantined" requirement.
6. Re-applies the executable bit to `Contents/MacOS/markdownToPdf` and any top-level `*.sh`/`*.zsh` files in the bundle (`run.sh`, `run.zsh`, `createLauncher.sh`) — belt-and-braces against zip/unzip tools that don't preserve the Unix executable bit.
7. Prints the installed path on success.

**Packaging:** `gui/createApp.sh` copies `gui/src/main/assembly/mac/macInstall.sh` into `gui/target/` (the zip root, alongside the `MarkdownToPdf.app` directory it already builds there) and adds it to the existing `zip -r md2pdf-gui.zip` invocation, so it ships at the top level of the release zip next to the `.app`.

**Shell:** zsh, matching the existing mac-specific assembly scripts (`run.zsh`, `mkicns.zsh`) rather than bash (used by the dev-facing root `install.sh`).
