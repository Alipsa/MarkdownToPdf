# Drag-and-drop Markdown loading — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let users drop a `.md`/`.markdown` file onto the Markdown editor pane to open it, document the existing (but undocumented) `install.sh` one-command Mac/Linux install script, add a `macInstall.sh` end-user install script shipped inside the release zip, and make both the installer and the app's own startup path aware of whether a suitable JDK is present.

**Architecture:** `MarkdownTab`'s editor `VBox` gets `setOnDragOver`/`setOnDragDropped` handlers wired in the constructor. Drop handling reuses the existing `loadFile(Path)` method (already used by project loading) after an unsaved-changes confirmation identical to the one `newDocument()` uses. Separately, `gui/readme.md` and `README.md` get a short addition documenting `install.sh`, and a new `macInstall.sh` (packaged by `gui/createApp.sh` at the root of `md2pdf-gui.zip`) copies the app to `~/Applications`, de-quarantines it, fixes executable bits, and — after asking permission — installs a JavaFX-bundled JDK via Homebrew or BellSoft's official release API if none is found. `markdownToPdf` (the actual macOS entry point) runs the same detection at startup and shows a native dialog instead of a cryptic crash if Java is missing, without attempting any install itself.

**Tech Stack:** Java 21, JavaFX 23 (built-in `Dragboard`/`TransferMode` API — no new dependency), Maven.

> **Amendment (post-Task-4 review):** `javaIsSuitable`'s numeric guard was changed from
> `[[ -n "$v" && "$v" -ge 21 ]]` to a `case`-based digit check before the `-ge` comparison.
> macOS ships a root-owned `/usr/bin/java` stub even with no JDK installed, so
> `command -v java` succeeds but `java -version`'s first line is prose (e.g. "The operation
> couldn't be completed. Unable to locate a Java Runtime.") rather than a version string —
> feeding that non-numeric text into zsh's `-ge` operator is a fatal `bad math expression`
> error, not a false comparison, which killed the entire installer (including the app-copy
> steps) on precisely the JDK-less Macs it exists to serve. Both occurrences below
> (Task 4 and Task 5) already reflect the fix.

## Global Constraints

- Zero new Maven dependencies (see project CLAUDE.md "Key constraints"). Extended for this plan's shell scripts: no new CLI-tool dependencies either — JSON fields are extracted with `grep -o`/`cut`, not `jq`, since `jq` isn't guaranteed to be present on a fresh macOS install.
- All Java formatted with Google Java Format; run `mvn spotless:apply` before verify, never in the same command as `mvn verify`.
- No automated test coverage for the drag-and-drop behavior itself — this project has no JavaFX/TestFX UI-testing dependency. Verification is by manual run of the GUI (requires a JavaFX-bundled JDK, e.g. Liberica Full JDK).

---

## File Map

| File | Change |
|---|---|
| `gui/src/main/java/se/alipsa/md2pdf/gui/MarkdownTab.java` | +imports, +`wireDragAndDrop`, +`isSingleMarkdownFile`, +`handleDragDropped`, constructor wires the editor `VBox` to the new handlers |
| `gui/readme.md` | +documentation of `install.sh` under a new subsection in **Running**; macOS subsection updated to mention `macInstall.sh` |
| `README.md` | +one-line pointer to the install script from the GUI section |
| `gui/src/main/assembly/mac/macInstall.sh` | new script — installs the app bundle from an unzipped release into `~/Applications`, de-quarantines it, fixes executable bits, detects/installs a JDK |
| `gui/createApp.sh` | copies `macInstall.sh` to the zip root and adds it to the `zip -r` invocation |
| `gui/src/main/assembly/mac/markdownToPdf` | +Java detection, shows a native dialog and exits if no suitable JDK is found, before sourcing `run.zsh` |

---

## Task 1: Wire drag-and-drop onto the Markdown editor

**Files:**
- Modify: `gui/src/main/java/se/alipsa/md2pdf/gui/MarkdownTab.java`

**Interfaces:**
- Consumes: `BaseTab.isChanged()` (returns `boolean`), `MarkdownTab.loadFile(Path)` (existing, reads file + sets title + clears dirty flag + associates file), `MarkdownToPdf.setProjectMarkdownFile(Path)` (existing), `BaseTab.setStatus(String)` (existing, protected — accessible from within `MarkdownTab`), `Alerts.confirm(String title, String headerText, String contentText)` (returns `boolean`, `true` = user chose Yes).
- Produces: nothing consumed by later tasks — this is a leaf UI change.

- [ ] **Step 1: Add the new imports**

In `gui/src/main/java/se/alipsa/md2pdf/gui/MarkdownTab.java`, replace the import block (lines 1–18) with:

```java
package se.alipsa.md2pdf.gui;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.SplitPane;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebView;
import javafx.stage.FileChooser;
import se.alipsa.md2pdf.Md2PdfEngine;
import se.alipsa.md2pdf.Md2PdfException;
import se.alipsa.md2pdf.gui.widgets.Alerts;
import se.alipsa.md2pdf.gui.widgets.ExceptionAlert;
```

- [ ] **Step 2: Wire the editor `VBox` to the new handlers in the constructor**

In the same file, find the constructor body:

```java
    VBox editorBox = new VBox(markdownArea);
    VBox.setVgrow(editorBox, Priority.ALWAYS);

    SplitPane splitPane = new SplitPane(editorBox, webView);
```

Replace it with:

```java
    VBox editorBox = new VBox(markdownArea);
    VBox.setVgrow(editorBox, Priority.ALWAYS);
    wireDragAndDrop(editorBox);

    SplitPane splitPane = new SplitPane(editorBox, webView);
```

- [ ] **Step 3: Add the three new private methods**

Add these methods after the constructor (immediately before `refreshPreview()`):

```java
  private void wireDragAndDrop(VBox editorBox) {
    editorBox.setOnDragOver(
        event -> {
          if (isSingleMarkdownFile(event.getDragboard())) {
            event.acceptTransferModes(TransferMode.COPY);
          }
          event.consume();
        });
    editorBox.setOnDragDropped(this::handleDragDropped);
  }

  private boolean isSingleMarkdownFile(Dragboard dragboard) {
    if (!dragboard.hasFiles()) {
      return false;
    }
    List<File> files = dragboard.getFiles();
    if (files.size() != 1) {
      return false;
    }
    String name = files.get(0).getName().toLowerCase(Locale.ROOT);
    return name.endsWith(".md") || name.endsWith(".markdown");
  }

  private void handleDragDropped(DragEvent event) {
    Dragboard dragboard = event.getDragboard();
    boolean success = false;
    if (isSingleMarkdownFile(dragboard)) {
      File droppedFile = dragboard.getFiles().get(0);
      boolean canProceed =
          !isChanged()
              || Alerts.confirm(
                  "Unsaved changes",
                  "The markdown file has unsaved changes.",
                  "Discard changes and open the dropped file?");
      if (canProceed) {
        Path path = droppedFile.toPath();
        loadFile(path);
        gui.setProjectMarkdownFile(path);
        setStatus("Loaded " + path.getFileName());
        success = true;
      }
    } else {
      setStatus("Drop a single .md file to open it");
    }
    event.setDropCompleted(success);
    event.consume();
  }
```

- [ ] **Step 4: Compile to catch mistakes**

```bash
mvn compile -pl gui
```

Expected: `BUILD SUCCESS`, no compilation errors.

- [ ] **Step 5: Format and verify**

```bash
mvn spotless:apply -pl gui
mvn verify -pl gui
```

Expected: both succeed. `spotless:apply` may reformat the new code — that's fine, re-check the diff still matches the intent above.

- [ ] **Step 6: Manual verification (requires a JavaFX-bundled JDK, e.g. Liberica Full JDK)**

```bash
mvn install && mvn package -P fatjar -pl gui
java -jar gui/target/MarkdownToPdf-*-jar-with-dependencies.jar
```

With the app running, check each of these against the Markdown tab's editor pane:

1. Drag a `.md` file from the OS file manager onto the editor. Expect: file content loads, tab title becomes the filename, status bar shows "Loaded `<filename>`".
2. Type a change into the editor (tab title gains a trailing `*`), then drag a different `.md` file onto the editor. Expect: a confirmation dialog titled "Unsaved changes" appears. Choosing **No** leaves the original (edited) content in place. Choosing **Yes** loads the new file.
3. Drag a non-Markdown file (e.g. a `.txt` or `.pdf`) onto the editor. Expect: no drop indicator appears while dragging, and dropping does nothing to the editor content; status bar shows "Drop a single .md file to open it" if the drop is released over the pane.
4. Select two `.md` files in the OS file manager and drag both onto the editor at once. Expect: same rejection as step 3.

- [ ] **Step 7: Commit**

```bash
git add gui/src/main/java/se/alipsa/md2pdf/gui/MarkdownTab.java
git commit -m "feat: support drag-and-drop of a markdown file onto the editor"
```

---

## Task 2: Document `install.sh`

**Files:**
- Modify: `gui/readme.md`
- Modify: `README.md`

**Interfaces:** None — documentation only.

- [ ] **Step 1: Add an install-script subsection to `gui/readme.md`**

In `gui/readme.md`, find the **Running** section, specifically right after the `### macOS` subsection ends (after the paragraph starting "The first time you open the app you may need to right-click…") and before `### Windows`. Insert a new subsection:

```markdown
### One-command build + install (macOS / Linux)

From the repository root, `install.sh` builds the project, packages the app bundle, and
installs it in one step:

```bash
./install.sh [installDir]
```

This runs `mvn install`, builds `MarkdownToPdf.app` (via `gui/createApp.sh`), and unzips it
into `installDir` — defaulting to `~/Applications` on macOS or `~/.local/share` on Linux.
On Linux it also runs the installed app's `createLauncher.sh` to create a `.desktop` launcher.
```

- [ ] **Step 2: Add a one-line pointer in the root `README.md`**

In `README.md`, find the **MarkdownToPdf GUI** section:

```markdown
## MarkdownToPdf GUI

A desktop application for interactive Markdown editing and PDF generation is available
in the [gui module](gui/readme.md).
```

Replace it with:

```markdown
## MarkdownToPdf GUI

A desktop application for interactive Markdown editing and PDF generation is available
in the [gui module](gui/readme.md). On macOS or Linux, `./install.sh` builds and installs
it in one step — see the [gui readme](gui/readme.md#one-command-build--install-macos--linux)
for details.
```

- [ ] **Step 3: Proofread the rendered Markdown**

Open both edited files and check:
- `gui/readme.md`: the new subsection's fenced code block opens with ` ```bash ` and closes
  with a single ` ``` ` — count the backtick fences to confirm there's no stray nesting
  (the instructions above wrap a `bash` fence inside a `markdown` fence; only the outer
  `markdown` fence is not part of the file itself).
- `README.md`: the new sentence reads correctly inline and the link
  `gui/readme.md#one-command-build--install-macos--linux` matches the heading anchor
  GitHub generates for `### One-command build + install (macOS / Linux)` (lowercase,
  spaces to hyphens, punctuation stripped).

- [ ] **Step 4: Commit**

```bash
git add gui/readme.md README.md
git commit -m "docs: document install.sh one-command build and install"
```

---

## Task 3: `macInstall.sh` — end-user install script shipped in the release zip

**Files:**
- Create: `gui/src/main/assembly/mac/macInstall.sh`
- Modify: `gui/createApp.sh`
- Modify: `gui/readme.md`

**Interfaces:** None — this is a standalone shell script plus a packaging-script change. No Java code involved.

- [ ] **Step 1: Create `macInstall.sh`**

Create `gui/src/main/assembly/mac/macInstall.sh` with this content:

```zsh
#!/usr/bin/env zsh
# Installs MarkdownToPdf.app into ~/Applications, removes the macOS quarantine
# attribute, and ensures bundled scripts are executable.
# Run this from the folder where md2pdf-gui.zip was unzipped (MarkdownToPdf.app
# must be next to this script).

DIR="${0:A:h}"
APP_NAME="MarkdownToPdf.app"
SOURCE_APP="$DIR/$APP_NAME"
TARGET_DIR="$HOME/Applications"
TARGET_APP="$TARGET_DIR/$APP_NAME"

if [[ ! -d "$SOURCE_APP" ]]; then
  echo "Could not find $APP_NAME next to this script ($DIR). Aborting."
  exit 1
fi

mkdir -p "$TARGET_DIR"

if [[ -d "$TARGET_APP" ]]; then
  echo "$TARGET_APP already exists."
  read -q "REPLY?Replace it? (y/n) "
  echo
  if [[ "$REPLY" != "y" ]]; then
    echo "Aborted, nothing was changed."
    exit 0
  fi
  rm -rf "$TARGET_APP"
fi

echo "Copying $APP_NAME to $TARGET_DIR"
cp -R "$SOURCE_APP" "$TARGET_DIR/"

echo "Removing quarantine attribute"
xattr -dr com.apple.quarantine "$TARGET_APP" 2>/dev/null || true

echo "Ensuring bundled scripts are executable"
chmod +x "$TARGET_APP/Contents/MacOS/markdownToPdf"
for f in "$TARGET_APP"/*.sh(N) "$TARGET_APP"/*.zsh(N); do
  chmod +x "$f"
done

echo "Installed $APP_NAME to $TARGET_DIR"
echo "You can now launch it from Applications (or Spotlight)."
```

Note the `(N)` after each glob in the `for` loop — zsh's `NULL_GLOB` qualifier, so the loop
doesn't error out if one of the patterns matches nothing.

- [ ] **Step 2: Syntax-check the script**

```bash
zsh -n gui/src/main/assembly/mac/macInstall.sh
```

Expected: no output, exit code 0 (zsh `-n` parses without executing).

- [ ] **Step 3: Make the source script executable**

```bash
chmod +x gui/src/main/assembly/mac/macInstall.sh
```

- [ ] **Step 4: Wire it into `gui/createApp.sh`**

Open `gui/createApp.sh`. Find this block:

```bash
cp "src/main/assembly/mac/markdownToPdf" "${MACOS_DIR}/"
cp "$DIR"/target/MarkdownToPdf*.jar "$targetDir"/
cp src/main/assembly/mac/run.zsh "$targetDir"/
cp src/main/assembly/linux/* "$targetDir"/
cp src/main/resources/MarkdownToPdf-rounded.* "$targetDir"/
cp src/main/assembly/win/* "$targetDir"/

chmod +x "${MACOS_DIR}/markdownToPdf"
chmod +x  "$targetDir"/*.sh
chmod +x  "$targetDir"/*.zsh
```

Replace it with (adds two lines after the existing copies, and one `chmod` line):

```bash
cp "src/main/assembly/mac/markdownToPdf" "${MACOS_DIR}/"
cp "$DIR"/target/MarkdownToPdf*.jar "$targetDir"/
cp src/main/assembly/mac/run.zsh "$targetDir"/
cp src/main/assembly/linux/* "$targetDir"/
cp src/main/resources/MarkdownToPdf-rounded.* "$targetDir"/
cp src/main/assembly/win/* "$targetDir"/
cp src/main/assembly/mac/macInstall.sh "$DIR/target/"

chmod +x "${MACOS_DIR}/markdownToPdf"
chmod +x  "$targetDir"/*.sh
chmod +x  "$targetDir"/*.zsh
chmod +x "$DIR/target/macInstall.sh"
```

Then find the zip step:

```bash
cd "$DIR/target"
zip -r md2pdf-gui.zip "${appName}"
```

Replace it with:

```bash
cd "$DIR/target"
zip -r md2pdf-gui.zip "${appName}" macInstall.sh
```

- [ ] **Step 5: Update the macOS section of `gui/readme.md`**

Find the existing macOS subsection:

```markdown
### macOS

Double-click `MarkdownToPdf.app`, or from a terminal:

```zsh
./run.zsh
```
```

Keep the rest of that subsection (the `.app` bundle structure block and the "first time you
open the app" note) unchanged, but insert this paragraph immediately after the `./run.zsh`
code block and before the `.app` bundle structure block:

```markdown
If you downloaded a release zip rather than building from source, run `./macInstall.sh`
(included at the root of the zip, next to `MarkdownToPdf.app`) after unzipping. It copies
the app into `~/Applications`, removes the macOS quarantine attribute so you don't have to
right-click → Open, and makes sure the bundled scripts are executable.
```

- [ ] **Step 6: Rebuild the zip and verify its contents**

```bash
mvn install && mvn package -P fatjar -pl gui
cd gui && source ./createApp.sh skipInstructions && cd ..
unzip -l gui/target/md2pdf-gui.zip
```

Expected: the listing includes both `MarkdownToPdf.app/...` entries and a top-level
`macInstall.sh` entry (not nested under `MarkdownToPdf.app/`).

- [ ] **Step 7: Manual verification on macOS**

```bash
cd /tmp && rm -rf md2pdf-test && mkdir md2pdf-test && cd md2pdf-test
unzip /path/to/MarkdownToPdf/gui/target/md2pdf-gui.zip
./macInstall.sh
```

Check:
1. Script reports "Installed MarkdownToPdf.app to /Users/<you>/Applications".
2. `xattr -l ~/Applications/MarkdownToPdf.app` shows no `com.apple.quarantine` entry.
3. `ls -l ~/Applications/MarkdownToPdf.app/Contents/MacOS/markdownToPdf` shows the executable
   bit set (`-rwx...`).
4. Double-clicking `~/Applications/MarkdownToPdf.app` from Finder launches the app without a
   Gatekeeper warning.
5. Re-run `./macInstall.sh` a second time: it detects the existing install, prompts to
   replace, and answering `n` leaves the existing install untouched (exit code 0, message
   "Aborted, nothing was changed.").

- [ ] **Step 8: Commit**

```bash
git add gui/src/main/assembly/mac/macInstall.sh gui/createApp.sh gui/readme.md
git commit -m "feat: add macInstall.sh to de-quarantine and install the app from the release zip"
```

---

## Task 4: `macInstall.sh` — JDK detection and auto-install

**Files:**
- Modify: `gui/src/main/assembly/mac/macInstall.sh` (created in Task 3)

**Interfaces:**
- Consumes: nothing from other tasks.
- Produces: `javaMajorVersion` / `javaIsSuitable` zsh functions — Task 5 defines its **own** identical copies (not shared code; see design §7 for why), but must stay behaviorally identical. If you change the detection logic here, make the same change in Task 5.

- [ ] **Step 1: Insert Java detection and auto-install logic**

In `gui/src/main/assembly/mac/macInstall.sh`, insert the following block immediately after the
existing "could not find `$APP_NAME`" guard (i.e., right after this existing block):

```zsh
if [[ ! -d "$SOURCE_APP" ]]; then
  echo "Could not find $APP_NAME next to this script ($DIR). Aborting."
  exit 1
fi
```

Insert:

```zsh

# ── Java detection ──────────────────────────────────────────────────────────

javaMajorVersion() {
  java -version 2>&1 | head -1 | cut -d'"' -f2 | sed '/^1\./s///' | cut -d'.' -f1
}

javaIsSuitable() {
  command -v java >/dev/null 2>&1 || return 1
  local v
  v=$(javaMajorVersion)
  case "$v" in
    (""|*[!0-9]*) return 1 ;;
  esac
  [[ "$v" -ge 21 ]] || return 1
  java --list-modules 2>/dev/null | grep -q '^javafx.controls' || return 1
  return 0
}

installJdkViaPkg() {
  local arch apiArch apiUrl response url sha1 tmpDir tmpPkg actualSha1
  arch=$(uname -m)
  case "$arch" in
    arm64) apiArch="arm" ;;
    x86_64) apiArch="x86" ;;
    *)
      echo "Unsupported architecture: $arch. Install a JavaFX-bundled JDK manually from https://bell-sw.com/pages/downloads/?version=java"
      return 1
      ;;
  esac

  echo "Looking up the latest Liberica Full JDK 21 for macOS ($apiArch)..."
  apiUrl="https://api.bell-sw.com/v1/liberica/releases?version-feature=21&os=macos&arch=${apiArch}&bitness=64&package-type=pkg&bundle-type=jdk-full&version-modifier=latest"
  response=$(curl -fsSL "$apiUrl")
  if [[ -z "$response" || "$response" == "[]" ]]; then
    echo "Could not find a Liberica JDK release. Install one manually from https://bell-sw.com/pages/downloads/?version=java"
    return 1
  fi
  url=$(echo "$response" | grep -o '"downloadUrl":"[^"]*"' | head -1 | cut -d'"' -f4)
  sha1=$(echo "$response" | grep -o '"sha1":"[^"]*"' | head -1 | cut -d'"' -f4)
  if [[ -z "$url" || -z "$sha1" ]]; then
    echo "Unexpected response from the BellSoft API. Install a JDK manually from https://bell-sw.com/pages/downloads/?version=java"
    return 1
  fi

  tmpDir=$(mktemp -d)
  tmpPkg="$tmpDir/liberica-jdk21-full.pkg"
  echo "Downloading $url"
  if ! curl -fL -o "$tmpPkg" "$url"; then
    echo "Download failed."
    rm -rf "$tmpDir"
    return 1
  fi

  actualSha1=$(shasum -a 1 "$tmpPkg" | cut -d' ' -f1)
  if [[ "$actualSha1" != "$sha1" ]]; then
    echo "Checksum mismatch (expected $sha1, got $actualSha1). Aborting install."
    rm -rf "$tmpDir"
    return 1
  fi

  echo "Installing (you may be asked for your password)..."
  sudo installer -pkg "$tmpPkg" -target /
  local installResult=$?
  rm -rf "$tmpDir"
  return $installResult
}

if javaIsSuitable; then
  echo "Found a suitable JDK: $(javaMajorVersion)"
else
  echo "No JavaFX-bundled JDK 21+ was found."
  read -q "REPLY?Install Liberica Full JDK 21 now? (y/n) "
  echo
  if [[ "$REPLY" == "y" ]]; then
    if command -v brew >/dev/null 2>&1; then
      brew tap bell-sw/liberica
      brew install --cask liberica-jdk21-full
    else
      installJdkViaPkg
    fi
    hash -r
    if javaIsSuitable; then
      echo "Java installed successfully: $(javaMajorVersion)"
    else
      echo "Java installation did not complete. Open a new terminal and re-run this script, or install manually from https://bell-sw.com/pages/downloads/?version=java"
    fi
  else
    echo "Skipping Java install. Install a JavaFX-bundled JDK 21+ manually before launching MarkdownToPdf: https://bell-sw.com/pages/downloads/?version=java"
  fi
fi
```

- [ ] **Step 2: Report Java status in the final summary**

Find the last line of the script (from Task 3):

```zsh
echo "Installed $APP_NAME to $TARGET_DIR"
echo "You can now launch it from Applications (or Spotlight)."
```

Replace it with:

```zsh
echo "Installed $APP_NAME to $TARGET_DIR"
if javaIsSuitable; then
  echo "Java: OK ($(javaMajorVersion))"
else
  echo "Java: not found — install a JavaFX-bundled JDK 21+ before launching MarkdownToPdf."
fi
echo "You can now launch it from Applications (or Spotlight)."
```

- [ ] **Step 3: Syntax-check**

```bash
zsh -n gui/src/main/assembly/mac/macInstall.sh
```

Expected: no output, exit code 0.

- [ ] **Step 4: Manual verification**

Two machines/scenarios to check if available (skip whichever you can't access, but note which
you skipped):

1. **Suitable JDK already installed:** run `./macInstall.sh` from an unzipped release (per
   Task 3 Step 7). Expected: prints "Found a suitable JDK: 21" (or higher) and proceeds
   straight to the app-copy steps — no install prompt.
2. **No JDK, Homebrew present:** `brew uninstall --cask liberica-jdk21-full 2>/dev/null` (or
   test in a VM/fresh user account with no JDK), then run `./macInstall.sh`, answer `y`.
   Expected: `brew install --cask liberica-jdk21-full` runs, and the final summary shows
   "Java: OK".
3. **No JDK, no Homebrew:** in an environment without `brew` on `PATH`, run `./macInstall.sh`,
   answer `y`. Expected: script prints the architecture it detected, downloads the pkg, prints
   no checksum-mismatch error, prompts for your password via `sudo installer`, and the final
   summary shows "Java: OK".
4. Answering `n` to the install prompt in any of the above: expected the script continues to
   install the app and prints "Java: not found — ..." in the summary, without erroring out.

- [ ] **Step 5: Commit**

```bash
git add gui/src/main/assembly/mac/macInstall.sh
git commit -m "feat: auto-install a JavaFX-bundled JDK from macInstall.sh when missing"
```

---

## Task 5: Startup guard for missing Java in `markdownToPdf`

**Files:**
- Modify: `gui/src/main/assembly/mac/markdownToPdf`

**Interfaces:**
- Consumes: nothing from other tasks (this file's own copy of the detection functions — see
  design §7 for why it's not shared with Task 4's copy in `macInstall.sh`).
- Produces: nothing consumed elsewhere.

- [ ] **Step 1: Add the startup guard**

Replace the full contents of `gui/src/main/assembly/mac/markdownToPdf` (currently):

```zsh
#!/usr/bin/env zsh
# This script goes into the Contents/MacOS folder
DIR="${0:A:h}"

source "$HOME/.zshrc"
source "$DIR/../../run.zsh"
```

with:

```zsh
#!/usr/bin/env zsh
# This script goes into the Contents/MacOS folder
DIR="${0:A:h}"

source "$HOME/.zshrc"

javaMajorVersion() {
  java -version 2>&1 | head -1 | cut -d'"' -f2 | sed '/^1\./s///' | cut -d'.' -f1
}

javaIsSuitable() {
  command -v java >/dev/null 2>&1 || return 1
  local v
  v=$(javaMajorVersion)
  case "$v" in
    (""|*[!0-9]*) return 1 ;;
  esac
  [[ "$v" -ge 21 ]] || return 1
  java --list-modules 2>/dev/null | grep -q '^javafx.controls' || return 1
  return 0
}

if ! javaIsSuitable; then
  osascript -e 'display dialog "MarkdownToPdf requires a JavaFX-bundled JDK 21 or later (e.g. Liberica Full JDK), but none was found.\n\nRun macInstall.sh from the folder where you unzipped MarkdownToPdf to install one automatically, or download one manually from https://bell-sw.com/pages/downloads/?version=java" with title "MarkdownToPdf" with icon stop buttons {"OK"} default button "OK"'
  exit 1
fi

source "$DIR/../../run.zsh"
```

- [ ] **Step 2: Syntax-check**

```bash
zsh -n gui/src/main/assembly/mac/markdownToPdf
```

Expected: no output, exit code 0.

- [ ] **Step 3: Manual verification**

After rebuilding and installing (Task 3 Step 6 rebuild, then `./macInstall.sh` or a manual
drag-install):

1. With a suitable JDK on `PATH`, double-click the installed `MarkdownToPdf.app`. Expected:
   app launches normally, same as before this change.
2. Temporarily rename/hide the JDK (e.g. `sudo mv "$(which java)" "$(which java).bak"`, or test
   in an account/VM with no JDK installed), then double-click the app. Expected: a native
   dialog titled "MarkdownToPdf" appears with the "requires a JavaFX-bundled JDK" message and
   an OK button; the app does not otherwise launch or show a stack trace. Restore the JDK
   afterward (`sudo mv "$(which java).bak" "$(which java)"`, adjusting for the actual path).

- [ ] **Step 4: Commit**

```bash
git add gui/src/main/assembly/mac/markdownToPdf
git commit -m "feat: show a native dialog instead of crashing when no suitable JDK is found at launch"
```

---

### Task 6: `MD2PDF_JAVA_HOME` manual JDK override (design §8)

**Files:**
- Modify: `gui/src/main/assembly/mac/macInstall.sh`
- Modify: `gui/src/main/assembly/mac/markdownToPdf`
- Modify: `gui/src/main/assembly/mac/run.zsh`
- Modify: `gui/readme.md`

**Rationale:** the sdkman-candidate loop added for Task 5's startup guard only helps when a
suitable JDK is sdkman-managed and discoverable through `sdk list java | grep installed`
parsing. A user with a suitable JDK installed some other way has no way to point the app at
it. See design §8.

- [x] **Step 1: Add a `javaBin()` helper to `macInstall.sh` and `markdownToPdf`**

Both files' `javaMajorVersion`/`javaIsSuitable` pair is updated identically (same duplication
pattern as Task 4/5) to resolve `MD2PDF_JAVA_HOME` first:

```zsh
javaBin() {
  if [[ -n "$MD2PDF_JAVA_HOME" && -x "$MD2PDF_JAVA_HOME/bin/java" ]]; then
    echo "$MD2PDF_JAVA_HOME/bin/java"
  else
    echo "java"
  fi
}

javaMajorVersion() {
  "$(javaBin)" -version 2>&1 | head -1 | cut -d'"' -f2 | sed '/^1\./s///' | cut -d'.' -f1
}

javaIsSuitable() {
  local bin
  bin=$(javaBin)
  if [[ "$bin" == "java" ]]; then
    command -v java >/dev/null 2>&1 || return 1
  fi
  local v
  v=$(javaMajorVersion)
  case "$v" in
    (""|*[!0-9]*) return 1 ;;
  esac
  [[ "$v" -ge 21 ]] || return 1
  "$bin" --list-modules 2>/dev/null | grep -q '^javafx.controls' || return 1
  return 0
}
```

- [x] **Step 2: Resolve and use the same override in `run.zsh`'s launch path**

Add near the top (after `JV=21`):

```zsh
JAVA_BIN="java"
if [[ -n "$MD2PDF_JAVA_HOME" && -x "$MD2PDF_JAVA_HOME/bin/java" ]]; then
  JAVA_BIN="$MD2PDF_JAVA_HOME/bin/java"
fi
```

Replace the existing `command -v java` / `java -version` calls with `"$JAVA_BIN"`, reset
`JAVA_BIN="java"` right before the sdkman-switch fallback re-checks version (since `sdk use
java` changes what bare `java` resolves to on `PATH`), and change the final launch line to:

```zsh
"$JAVA_BIN" -Xmx8g -Xdock:name=MarkdownToPdf -Xdock:icon=./Contents/Resources/md2pdf.icns -jar "./$JAR"
```

- [x] **Step 3: Update the `markdownToPdf` failure dialog**

Append a sentence to the existing `osascript` message pointing at `MD2PDF_JAVA_HOME` as a
remedy for users who already have a suitable JDK installed elsewhere.

- [x] **Step 4: Syntax-check all three scripts**

```bash
zsh -n gui/src/main/assembly/mac/macInstall.sh
zsh -n gui/src/main/assembly/mac/markdownToPdf
zsh -n gui/src/main/assembly/mac/run.zsh
```

Expected: no output, exit code 0 for all three.

- [x] **Step 5: Functional verification of `javaBin()`/`javaIsSuitable()`**

Verified by extracting the functions into an isolated `zsh -c` invocation with a fake
`MD2PDF_JAVA_HOME` pointing at a scratch directory containing a stub `bin/java` script that
prints a fake `-version`/`--list-modules` output — confirms the override is picked up and
`javaIsSuitable` returns success, and that unsetting the variable falls back to bare `java`.
No system-modifying commands (`brew`, `sudo installer`) are involved in this task, so no
further isolation was needed.

- [x] **Step 6: Document `MD2PDF_JAVA_HOME` in `gui/readme.md`**

Add a paragraph in the macOS section explaining the variable, why it must be set in
`~/.zshrc` (so a double-clicked `.app` picks it up, since `markdownToPdf` sources `.zshrc`
before checking Java), and a realistic example using an sdkman candidate path.

- [x] **Step 7: Commit**

```bash
git add gui/src/main/assembly/mac/macInstall.sh gui/src/main/assembly/mac/markdownToPdf \
  gui/src/main/assembly/mac/run.zsh gui/readme.md
git commit -m "feat: support MD2PDF_JAVA_HOME to override the JDK used by the mac app and installer"
```

---

## Self-Review Notes

- **Spec coverage:** Design §1–4 (drop zone, accept condition, drop handling, constraints) → Task 1. Design §5 (install.sh documentation) → Task 2. Design §6 (macInstall.sh) → Task 3. Design §7 (JDK detection/auto-install/startup guard) → Tasks 4–5. All spec items covered.
- **No automated tests:** intentional per spec §4 — no TestFX dependency exists in this project; Task 1 Step 6, Task 3 Step 7, Task 4 Step 4, and Task 5 Step 3 are the manual equivalent of a test cycle for their respective UI/shell behaviors.
- **Type/signature consistency:** `isSingleMarkdownFile(Dragboard)` and `handleDragDropped(DragEvent)` are used consistently between Steps 2 and 3 of Task 1; `Alerts.confirm(String, String, String)` matches the real signature read from `gui/src/main/java/se/alipsa/md2pdf/gui/widgets/Alerts.java`. Task 3's `macInstall.sh` path (`gui/src/main/assembly/mac/macInstall.sh`) and its packaged destination (zip root, alongside `MarkdownToPdf.app`) are consistent between Task 3 Steps 1, 4, and 6. `javaMajorVersion`/`javaIsSuitable` are defined identically (verbatim) in both Task 4 and Task 5 — intentional duplication per design §7, not a naming drift.
- **API verified live:** the BellSoft endpoint, its query parameters (`version-feature`, `os`, `arch`, `bitness`, `package-type`, `bundle-type`, `version-modifier`), and the `downloadUrl`/`sha1` response fields were confirmed against the real `api.bell-sw.com` during design, not assumed from documentation alone.
