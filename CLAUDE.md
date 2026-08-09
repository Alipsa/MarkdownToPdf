# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build commands

```bash
# Full build: compile, test, spotless format check, SpotBugs check
mvn verify

# Build and install to local Maven repo
mvn install

# Build a self-contained platform archive (linux | macos | windows | no-jdk)
mvn install -DskipTests && ./gui/createApp.sh linux

# Auto-format all code (Google Java Format)
mvn spotless:apply
```

## Code style

All Java is formatted with **Google Java Format** enforced by the Spotless plugin at `verify`. Always run `mvn spotless:apply` before committing. Do not run `spotless:apply` and `verify` in the same command — apply first, then verify.

SpotBugs false positives are documented and suppressed in `lib/spotbugs-exclude.xml` and `gui/spotbugs-exclude.xml`.

## Module structure

This is a Maven multi-module project with `${revision}` CI-friendly versioning (resolved by flatten-maven-plugin).

`lib` pipeline: commonmark-java (Markdown → HTML) → jsoup (HTML → well-formed XHTML) → OpenHTMLtoPDF + Batik (XHTML → PDF).

`gui` is a JavaFX application. JavaFX dependencies are `provided` scope — the build requires a
JavaFX-bundled JDK (e.g. Liberica Full JDK, Azul Zulu+FX). The platform release archives
bundle their own Java runtime, so end users do not need a JDK; only the separate `-no-jdk`
archive requires a JavaFX-bundled JDK.

## Key constraints

- **Zero new Maven dependencies** for anything in the `gui` model layer or `lib` core. The CSS round-trip parser in `StyleProfile.fromCss()` is intentionally hand-written for this reason.
- **No `--add-exports`/`--add-opens` config needed** — Spotless 3.x handles Google Java Format's module requirements automatically.
- Build-generated files such as `.flattened-pom.xml` and `dependency-reduced-pom.xml` are ignored by `.gitignore`; do not commit them.
- The `${revision}` property in the root POM controls the version for all modules. Bump it in `pom.xml` only; flatten-maven-plugin propagates it. `gui/MarkdownToPdf.xml` inherits the root parent, so its launcher dependency follows `${revision}` as well.
