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

> **Superseded (2026-08-07).** PR #1 landed `gui/src/main/assembly/install.sh`, a
> cross-platform end-user installer shipped as `md2pdf-install.sh` at the release-zip
> root, which covers the same need for macOS, Linux and Windows. `macInstall.sh` was
> therefore removed and everything below — quarantine removal, the Homebrew /
> checksum-verified BellSoft JDK install, and `MD2PDF_JAVA_HOME` — was folded into
> `md2pdf-install.sh`. Read §6–§7 as the rationale for those behaviours, not as a
> description of the files on disk.

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

## 7. JDK detection, auto-install, and a startup guard

The GUI requires a JavaFX-bundled JDK (README: Liberica Full JDK, Zulu+FX, or GraalVM), which standard OpenJDK distributions don't provide — but nothing today checks for this before trying to run `java`, so a missing/wrong JDK currently fails with a confusing module-not-found stack trace (or, from a double-clicked `.app`, fails silently with no visible terminal at all).

**Detection** (a small `javaIsSuitable`/`javaMajorVersion` zsh function pair, duplicated identically in both `macInstall.sh` and `markdownToPdf` rather than factored into a shared sourced file — the two scripts run from different locations after install, one at the zip root, one inside the app bundle, so cross-sourcing between them would be fragile): suitable means `java` is on `PATH`, `javaMajorVersion` (parsed the same way the existing root `install.sh` already does: `java -version 2>&1 | head -1 | cut -d'"' -f2 | sed '/^1\./s///' | cut -d'.' -f1`) is `>= 21`, and `java --list-modules` includes a line starting `javafx.controls`.

**`macInstall.sh` — ask permission, install on yes:**
1. Run the detection. If suitable, print the version found and skip straight to the app-copy steps (§6).
2. If not suitable, print why and prompt `Install Liberica Full JDK 21 now? (y/n)` via `read -q`.
3. On **y**:
   - If `brew` is on `PATH`: `brew tap bell-sw/liberica && brew install --cask liberica-jdk21-full`.
   - Else: map `uname -m` (`arm64`→`arm`, `x86_64`→`x86`; anything else prints a manual-install pointer and stops) to BellSoft's Product Discovery API (`https://api.bell-sw.com/v1/liberica/releases?version-feature=21&os=macos&arch=<arch>&bitness=64&package-type=pkg&bundle-type=jdk-full&version-modifier=latest`, verified live against the real endpoint — no hardcoded version-pinned URL, since BellSoft's build numbers change frequently), extract `downloadUrl` and `sha1` from the JSON with `grep -o`/`cut` (no `jq` dependency), download to a `mktemp -d` scratch dir, verify the SHA1 with `shasum -a 1` before doing anything else (abort on mismatch — this is the one step running downloaded code with `sudo`, so it gets checksum-verified first), then `sudo installer -pkg <file> -target /`.
   - Re-run the detection (`hash -r` first, to refresh zsh's command lookup cache) and report the result.
4. On **n**, or if the install path fails: print a manual-install pointer (`https://bell-sw.com/pages/downloads/?version=java`) and continue anyway — copying/de-quarantining the app doesn't depend on Java being present. The final summary line reports Java found/installed/still-missing either way.

**Startup guard, in `markdownToPdf`** (the actual entry point macOS invokes on double-click, before it currently does `source "$DIR/../../run.zsh"`): run the same detection. If it fails, show a blocking native dialog via `osascript -e 'display dialog "..." with icon stop'` naming `macInstall.sh` and the BellSoft downloads page, then `exit 1` **without** sourcing `run.zsh` / invoking `java` at all. This path never attempts to install anything — a double-clicked `.app` has no interactive terminal to run a `sudo` prompt in, so it only informs.

## 8. `MD2PDF_JAVA_HOME` — manual override for an existing JavaFX JDK

The startup guard's sdkman-candidate loop (§7 amendment) only helps when a suitable JDK happens to be sdkman-managed. A user whose JavaFX-bundled JDK is installed some other way (a `.pkg` install, a different version manager, a JDK sdkman knows about but that the loop's `sdk list java | grep installed` parsing doesn't surface correctly) has no way to point the app at it short of installing a fresh one via `macInstall.sh` — even though they already have exactly what's needed.

Add support for an optional `MD2PDF_JAVA_HOME` environment variable, checked in **both** `macInstall.sh` and `markdownToPdf`'s duplicated `javaMajorVersion`/`javaIsSuitable` pair, and in `run.zsh`'s actual launch command:

- A new `javaBin()` helper (added identically to both duplicated-function files) returns `"$MD2PDF_JAVA_HOME/bin/java"` when `MD2PDF_JAVA_HOME` is set and that path is executable, else plain `java`. `javaMajorVersion`/`javaIsSuitable` call `"$(javaBin)"` instead of bare `java` everywhere they invoke it.
- `run.zsh` (untouched by the prior amendment, but touched now) resolves the same `JAVA_BIN` at the top and uses it consistently for its own existing version check and for the final `java -Xmx8g ... -jar` launch line — so setting the override actually changes which JVM launches the app, not just which JVM the guard tests.
- Rationale for an app-specific variable rather than reusing bare `JAVA_HOME`: many users already have a system-wide `JAVA_HOME` pointing at their default JDK for Maven/other tooling/IDEs, which may be a different (non-FX) JDK than the one they want MarkdownToPdf to use specifically. An app-specific variable avoids that collision.
- Users set this in `~/.zshrc` (not just their interactive shell), because `markdownToPdf` already does `source "$HOME/.zshrc"` before checking Java — this is what makes an exported variable visible to a double-clicked `.app`, which otherwise wouldn't inherit shell-only environment customizations at all. Both the `osascript` failure dialog and `gui/readme.md` document this explicitly, with a realistic example (an sdkman candidate path).
- `macInstall.sh`'s JDK-detection (§7) picks this up for free through the same `javaIsSuitable`, so a user with a working override no longer gets offered an unnecessary JDK install.
