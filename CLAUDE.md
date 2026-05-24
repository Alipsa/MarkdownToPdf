# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build commands

```bash
# Full build: compile, test, spotless format check, SpotBugs check
mvn verify

# Build and install to local Maven repo
mvn install

# Build the GUI standalone fat-jar
mvn install -P fatjar

# Auto-format all code (Google Java Format)
mvn spotless:apply

# Run all tests
mvn test

# Run tests for one module only
mvn test -pl lib
mvn test -pl gui

# Run a single test class
mvn test -pl lib -Dtest=OutputTest
mvn test -pl gui -Dtest=StyleProfileTest

# Run a single test method
mvn test -pl lib -Dtest=OutputTest#testMarkdownString
```

## Code style

All Java is formatted with **Google Java Format** (2-space indent, 100-char line limit) enforced by the Spotless plugin at `verify`. Always run `mvn spotless:apply` before committing. Do not run `spotless:apply` and `verify` in the same command — apply first, then verify.

SpotBugs runs at `verify` with `effort=Max`, `threshold=Medium`. False positives are documented and suppressed in `lib/spotbugs-exclude.xml` and `gui/spotbugs-exclude.xml`.

## Module structure

This is a Maven multi-module project with `${revision}` CI-friendly versioning (resolved by flatten-maven-plugin).

### `lib` — the engine library (`se.alipsa:md2pdf`)

Four source files:
- **`Md2PdfEngine`** — the entire public API. Entry point is `engine.markdown(...)` which returns an inner `Job` for fluent configuration. Job chain: `css()`, `addCss()`, `font()`, `pageHeader()`, `pageFooter()`, `pageMargins()`, `basePath()`, plus PDF metadata setters. Terminal methods: `toPdf()` (returns `byte[]` or writes to file/stream) and `toHtml()`.
- **`Md2PdfException`** — checked exception thrown by all rendering operations.
- **`Slf4jXRLogger`** — bridges OpenHTMLtoPDF's internal `XRLog` system to SLF4J. Supports both Logback and Log4j2 via reflection.
- **`ImageUtil`** — helper for encoding images as base64 data URLs.

Pipeline: commonmark-java (Markdown → HTML) → jsoup (HTML → well-formed XHTML) → OpenHTMLtoPDF + Batik (XHTML → PDF).

### `gui` — the desktop application (`se.alipsa:MarkdownToPdf`)

JavaFX 23.0.2 application. JavaFX dependencies are `provided` scope — **requires a JavaFX-bundled JDK** (e.g. Liberica Full JDK, Azul Zulu+FX). Standard OpenJDK will not run the GUI.

**Tab hierarchy:**
- `MarkdownToPdf extends Application` — main window; builds the three-tab UI and wires project/style management.
- `BaseTab extends Tab` — abstract base tracking the backing file, dirty state (`isChanged`), and a reference to the main window (`gui`).
- `MarkdownTab extends BaseTab` — left-pane Markdown editor + right-pane live HTML preview (`WebView`). Calls `StyleTab.getActiveCss()` on every preview refresh.
- `StyleTab extends BaseTab` — toggles between `StyleEditorPanel` (visual form) and `CssTextArea` (raw CSS editor). The toggle calls `StyleProfile.fromCss()` to round-trip CSS back into form controls when switching from CSS mode to visual mode.

**Editor hierarchy:**
- `CodeTextArea extends CodeArea` (RichTextFX) — abstract; handles indentation (Tab/Shift-Tab), auto-indent on Enter, Ctrl+F wiring, and dirty-state propagation via `blockChange` flag.
- `MarkdownTextArea extends CodeTextArea` — regex-based Markdown syntax highlighting.
- `CssTextArea extends CodeTextArea` — regex-based CSS syntax highlighting.

**Model (`gui/src/main/java/.../model/`):**
- `StyleProfile` — all visual styling parameters. `toCss()` generates CSS; `fromCss(String)` parses CSS back into fields (custom zero-dependency parser). Unknown CSS rules are preserved in `extraCss` and re-appended by `toCss()`. Persists via Java `Properties`.
- `StyleProfileManager` — three built-in profiles (`Default`, `Minimal`, `Print`) defined in code; user profiles stored as `.properties` files under `~/.config/md2pdf/styles/`. Built-ins cannot be overwritten.
- `Project` — name + Markdown file path + style profile name; serialised to `.jpr` (Java properties format).

**Assembly scripts** (`gui/src/main/assembly/`): platform launch scripts (Linux `run.sh`, macOS `run.zsh`, Windows `run.cmd`) and shortcut creators. All require a JavaFX-bundled JDK; the minimum Java version check in scripts is `JV=21`.

## Key constraints

- **Zero new Maven dependencies** for anything in the `gui` model layer or `lib` core. The CSS round-trip parser in `StyleProfile.fromCss()` is intentionally hand-written for this reason.
- **No `--add-exports`/`--add-opens` config needed** — Spotless 3.x handles Google Java Format's module requirements automatically.
- `gui/dependency-reduced-pom.xml` is a build side-effect of the shade plugin; ignore it.
- The `${revision}` property in the root POM controls the version for all modules. Bump it in `pom.xml` only; flatten-maven-plugin propagates it.
