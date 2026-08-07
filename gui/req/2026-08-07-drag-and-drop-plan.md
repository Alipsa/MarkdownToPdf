# Drag-and-drop Markdown loading — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let users drop a `.md`/`.markdown` file onto the Markdown editor pane to open it, and document the existing (but undocumented) `install.sh` one-command Mac/Linux install script.

**Architecture:** `MarkdownTab`'s editor `VBox` gets `setOnDragOver`/`setOnDragDropped` handlers wired in the constructor. Drop handling reuses the existing `loadFile(Path)` method (already used by project loading) after an unsaved-changes confirmation identical to the one `newDocument()` uses. Separately, `gui/readme.md` and `README.md` get a short addition documenting `install.sh`.

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
| `gui/readme.md` | +documentation of `install.sh` under a new subsection in **Running** |
| `README.md` | +one-line pointer to the install script from the GUI section |

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

## Self-Review Notes

- **Spec coverage:** Design §1–4 (drop zone, accept condition, drop handling, constraints) → Task 1. Design §5 (documentation) → Task 2. Both spec items covered.
- **No automated tests:** intentional per spec §4 — no TestFX dependency exists in this project; Task 1 Step 6 is the manual equivalent of a test cycle.
- **Type/signature consistency:** `isSingleMarkdownFile(Dragboard)` and `handleDragDropped(DragEvent)` are used consistently between Steps 2 and 3; `Alerts.confirm(String, String, String)` matches the real signature read from `gui/src/main/java/se/alipsa/md2pdf/gui/widgets/Alerts.java`.
