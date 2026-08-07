# Drag-and-drop Markdown loading — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let users drop a `.md`/`.markdown` file onto the Markdown editor pane to open it, document the existing (but undocumented) `install.sh` one-command Mac/Linux install script, and add a `macInstall.sh` end-user install script shipped inside the release zip.

**Architecture:** `MarkdownTab`'s editor `VBox` gets `setOnDragOver`/`setOnDragDropped` handlers wired in the constructor. Drop handling reuses the existing `loadFile(Path)` method (already used by project loading) after an unsaved-changes confirmation identical to the one `newDocument()` uses. Separately, `gui/readme.md` and `README.md` get a short addition documenting `install.sh`, and a new `macInstall.sh` (packaged by `gui/createApp.sh` at the root of `md2pdf-gui.zip`) copies the app to `~/Applications`, de-quarantines it, and fixes executable bits for end users who download a pre-built release instead of building from source.

**Tech Stack:** Java 21, JavaFX 23 (built-in `Dragboard`/`TransferMode` API — no new dependency), Maven.

## Global Constraints

- Zero new Maven dependencies (see project CLAUDE.md "Key constraints").
- All Java formatted with Google Java Format; run `mvn spotless:apply` before verify, never in the same command as `mvn verify`.
- No automated test coverage for the drag-and-drop behavior itself — this project has no JavaFX/TestFX UI-testing dependency. Verification is by manual run of the GUI (requires a JavaFX-bundled JDK, e.g. Liberica Full JDK).

---

## File Map

| File | Change |
|---|---|
| `gui/src/main/java/se/alipsa/md2pdf/gui/MarkdownTab.java` | +imports, +`wireDragAndDrop`, +`isSingleMarkdownFile`, +`handleDragDropped`, constructor wires the editor `VBox` to the new handlers |
| `gui/readme.md` | +documentation of `install.sh` under a new subsection in **Running**; macOS subsection updated to mention `macInstall.sh` |
| `README.md` | +one-line pointer to the install script from the GUI section |
| `gui/src/main/assembly/mac/macInstall.sh` | new script — installs the app bundle from an unzipped release into `~/Applications`, de-quarantines it, fixes executable bits |
| `gui/createApp.sh` | copies `macInstall.sh` to the zip root and adds it to the `zip -r` invocation |

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

## Self-Review Notes

- **Spec coverage:** Design §1–4 (drop zone, accept condition, drop handling, constraints) → Task 1. Design §5 (install.sh documentation) → Task 2. Design §6 (macInstall.sh) → Task 3. All spec items covered.
- **No automated tests:** intentional per spec §4 — no TestFX dependency exists in this project; Task 1 Step 6 and Task 3 Step 7 are the manual equivalent of a test cycle for their respective UI/shell behaviors.
- **Type/signature consistency:** `isSingleMarkdownFile(Dragboard)` and `handleDragDropped(DragEvent)` are used consistently between Steps 2 and 3 of Task 1; `Alerts.confirm(String, String, String)` matches the real signature read from `gui/src/main/java/se/alipsa/md2pdf/gui/widgets/Alerts.java`. Task 3's `macInstall.sh` path (`gui/src/main/assembly/mac/macInstall.sh`) and its packaged destination (zip root, alongside `MarkdownToPdf.app`) are consistent between Task 3 Steps 1, 4, and 6.
