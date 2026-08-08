# Bundled Runtime Packaging Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship MarkdownToPdf as three self-contained per-platform archives, each carrying a `jlink`-slimmed BellSoft Liberica Full JDK 21 runtime, so that installing the app requires no JDK on the user's machine.

**Architecture:** `mvn package` produces `MarkdownToPdf.jar` plus a `lib/` directory of unmodified dependency jars wired through the manifest `Class-Path`; the shade fat-jar is deleted. A rewritten `gui/createApp.sh <platform>` runs `jlink` natively on each platform, assembles the platform layout, and zips it with a platform-native installer. A GitHub Actions matrix builds and installs all three natively and uploads them as artifacts; `release.sh` becomes a driver that downloads those artifacts, publishes `lib` to Maven Central and creates the GitHub release.

**Tech Stack:** Maven multi-module (`${revision}` + flatten-maven-plugin), BellSoft Liberica Full JDK 21 (`jdk+fx`), `jlink`, JavaFX 21.0.12, bash / zsh / cmd installers, GitHub Actions.

**Spec:** `gui/req/2026-08-08-bundled-runtime-packaging-design.md`. Read it before starting. Where this plan and the spec disagree, the spec is right and the plan has a bug — say so rather than guessing.

## Global Constraints

Every task's requirements implicitly include this section.

- **Liberica version: `21.0.12`.** The single source of truth is `.github/versions.env` (`LIBERICA_VERSION=21.0.12`). Never a bare major.
- **`javafx.version` is `21.0.12`** in `gui/pom.xml`, and must equal the JavaFX version inside the runtime. CI asserts this.
- **`jlink` flags are exactly `--strip-debug --no-header-files --no-man-pages --compress=2`.** JDK 21 accepts only `--compress={0|1|2}`; the `zip-<n>` form is JDK 22+ and makes JDK 21 `jlink` **fail**.
- **`copy-dependencies` must set `includeScope=runtime`.** The default copies `provided` and `test` too, which would put JavaFX jars in `lib/` beside a runtime that already contains the JavaFX modules.
- **Zero new Maven dependencies** in `lib` core or the `gui` model layer. New Maven *plugins* are fine.
- **Google Java Format via Spotless.** Run `mvn spotless:apply` before committing Java changes. Never run `spotless:apply` and `verify` in the same command.
- **`JAVA_HOME` must point at a JavaFX-bundled JDK 21** to build locally. On the maintainer's machine: `export JAVA_HOME=/home/per/.sdkman/candidates/java/21.0.12.fx-librca`. A plain JDK cannot compile `gui`; JDK 25 breaks `google-java-format`.
- **Targets and artifact names:** `md2pdf-<version>-linux-x64.zip`, `md2pdf-<version>-macos-aarch64.zip`, `md2pdf-<version>-windows-x64.zip`, `md2pdf-<version>-no-jdk.zip`, `md2pdf-<version>-javadoc.jar`. `<version>` is the full `${revision}`, e.g. `0.1.1-SNAPSHOT`. **The CI artifact name equals the file's basename without extension.**
- **`BUNDLE_VERSION` = `${revision}` with any `-qualifier` suffix removed** (`0.1.1-SNAPSHOT` → `0.1.1`), and must match `^[0-9]+(\.[0-9]+){0,2}$`. macOS only; Apple rejects anything else in the version keys.
- **`actions/setup-java@v5`.** v1–v4 are deprecated.
- **Runner labels are pinned versioned labels, never `-latest`.**
- **The jlink module set** (verbatim, order irrelevant):
  `javafx.controls, javafx.swing, javafx.web, java.desktop, java.logging, java.management, java.naming, java.net.http, java.prefs, java.scripting, java.sql, java.xml, jdk.charsets, jdk.crypto.ec, jdk.unsupported, jdk.zipfs`
- **No `--add-modules` is needed to run the app.** In a custom `jlink` image (and in Liberica Full), every non-`java.*` module present is a root module, so `javafx.*` resolves for classpath code automatically. Adding `--add-modules` at launch is a sign something else is wrong.
- **The bundled runtime has no `jdk.compiler`, so `java Foo.java` source-file mode does not work on it** — it fails with `InternalError: Module jdk.compiler not in boot Layer`. The smoke tests are compiled with the full JDK on `$JAVA_HOME` and run as `.class` files. Do not add `jdk.compiler` to the module set to make source mode work; it is ~20 MB of compiler in every user's download to save one `javac` call in CI.
- **No shipped script reads or writes `md2pdf.env` or `MD2PDF_JAVA_HOME`, or parses `java -version` to decide what to launch.** The rule is about the launchers and installers — the JDK-detection logic this work deletes. `verify-install.sh` does parse `java -version`, but it is a CI assertion checking that the bundled runtime is the version it should be, not a script choosing a JDK. If you find yourself writing JDK *selection* into anything that ships, stop.
- **`0.1.1-SNAPSHOT` in the command examples is today's `${revision}`.** If it has been bumped, substitute. Scripts must never hardcode it; examples may.
- **Every command-line Maven plugin invocation must resolve to a fixed version**, one of two ways:
  - **The root `pom.xml` manages the plugin.** A goal prefix then resolves to the managed version, and a short invocation is fine. Verified: `mvn spotless:apply` → `spotless:3.9.0`, `mvn -pl lib javadoc:jar` → `javadoc:3.12.0`.
  - **Otherwise write the full coordinate**, e.g. `mvn org.apache.maven.plugins:maven-help-plugin:3.5.1:evaluate …`. `maven-help-plugin` is not managed anywhere in this build, so every use of it in this plan is spelled out in full. (Verified working at 3.5.1.)

  An unqualified prefix for an *unmanaged* plugin resolves whatever version is newest on Central, which makes the build depend on the day it runs. `maven-dependency-plugin` is unmanaged until Task 2 Step 3 adds it to `pluginManagement` at 3.8.1 — so Task 1, which runs before that, writes its coordinate out in full.
- **Command-line tools the packaging needs:** `unzip`, `find`, `awk`, `sed`, and an archiver. On Linux and macOS the archiver must be `zip` — a `jlink` image contains symlinks, and `zip -y` is what stores them rather than following them. On Windows there are no symlinks in the image, so `7z` is an acceptable fallback. **Git Bash does not ship `zip` or `unzip` by default**; on a developer Windows machine install them (e.g. via MSYS2 or the Git for Windows SDK) before running `createApp.sh`. `createApp.sh` checks for them and fails with a clear message rather than half-building an archive.

### Already verified against this repo (2026-08-08)

Do not re-litigate these; they were run, not assumed.

- `copy-dependencies` with `includeScope=runtime` produces **exactly 45 jars**, with no `javafx-*` and no test jars. The `check-lib-classpath.sh` in Task 2 passes against that output unchanged.
- A jar on the **classpath** (`java -cp app.jar Foo.java`) honours the manifest `Class-Path`, so the smoke tests resolve `lib/` without naming it.
- `EngineSmoke` produces a **~1.6 KB** PDF (1602-1603 bytes; it varies by a byte between runs, so do not assert an exact size) and prints three harmless `SLF4J(W): No SLF4J providers were found` lines before the OK line. That is expected — the smoke test runs without a logging backend configured. Do not "fix" it.
- Both smoke tests **compile** against `MarkdownToPdf.jar` with the full Liberica JDK and no `--add-modules`, and the compiled classes run correctly. `javac` emits an annotation-processing note unless `-proc:none` is passed, which the compile helper does.
- `jlink --compress=2` on 21.0.12 prints `Warning: The 2 argument for --compress is deprecated and may be removed in a future release`. Expected and harmless — `2` is still the only ZIP level JDK 21 accepts, and the `zip-<n>` replacement is JDK 22+ and makes JDK 21 fail outright. Do not "fix" the warning.
- Liberica Full 21.0.12 ships **seven** `javafx.*` modules: `base`, `controls`, `fxml`, `graphics`, `media`, `swing`, `web`.
- `mvn -pl gui org.apache.maven.plugins:maven-dependency-plugin:3.8.1:build-classpath -DincludeScope=runtime -Dmdep.outputFile=<file>` works and is the reliable way to get a classpath for an ad-hoc run.
- `env -i` supplies a default `PATH` of `/bin:/usr/bin` when none is given, so `xvfb-run` is still found. The plan sets `PATH` explicitly anyway, so the scrubbing is visible rather than incidental.

## File Structure

**Created**

| Path | Responsibility |
|---|---|
| `.github/versions.env` | The pinned Liberica version, one `KEY=value` line. |
| `.github/workflows/ci.yml` | Four jobs: `verify`, `lint-scripts`, `build` (3-platform matrix), `install-failures`. |
| `.github/scripts/check-lib-classpath.sh` | Asserts manifest `Class-Path` and `lib/` agree exactly. |
| `.github/scripts/EngineSmoke.java` | Markdown → PDF through `Md2PdfEngine`; asserts `%PDF`. |
| `.github/scripts/ToolkitSmoke.java` | Starts the JavaFX toolkit, constructs a `WebView`. |
| `.github/scripts/compile-smoke.sh` | Compiles both smoke tests with the full JDK, since the bundled runtime has no compiler. |
| `.github/scripts/verify-install.sh` | All post-install assertions for one platform. |
| `.github/scripts/verify-launcher.sh` | Starts the installed launcher and asserts the app stays up. Called by `verify-install.sh`. |
| `.github/scripts/verify-no-jdk.sh` | Assertions specific to the `-no-jdk` archive. |
| `.github/scripts/test-install-failures.sh` | Asserts the installer's failure paths fail loudly. |
| `gui/src/main/assembly/mac/entitlements.plist` | JVM entitlements for later notarization; unused for now. |
| `gui/src/main/assembly/mac/sign-app.sh` | Enumerates Mach-O files and signs inside-out. |
| `gui/src/main/assembly/mac/md2pdf-install.zsh` | macOS installer. |
| `gui/src/main/assembly/win/md2pdf-install.cmd` | Windows installer, native `cmd`. |
| `gui/src/main/assembly/no-jdk/README.txt` | The one command that runs the `-no-jdk` archive. |

**Modified**

| Path | Change |
|---|---|
| `gui/pom.xml` | `javafx.version` → 21.0.12; delete the `fatjar` profile; add `copy-dependencies`; add `classpathPrefix`; add the `skipPublishing` guard. |
| `pom.xml` | Add `maven-dependency-plugin` to `pluginManagement`; remove the "bump Info.plist too" comment. |
| `gui/createApp.sh` | Rewritten: takes a platform argument, runs `jlink`, assembles the layout, zips. |
| `gui/build.sh` | Drop `-Pfatjar`. |
| `gui/src/main/assembly/linux/run.sh` | 56 lines → 5. |
| `gui/src/main/assembly/linux/createLauncher.sh` | Keep the failure semantics; drop `chmod +x run.sh` duplication. |
| `gui/src/main/assembly/win/run.cmd` | 31 lines → 4. |
| `gui/src/main/assembly/win/createShortcut.ps1` | Target `runtime\bin\javaw.exe` directly. |
| `gui/src/main/assembly/mac/Info.plist` | Becomes a template; four defects fixed. |
| `gui/src/main/assembly/mac/markdownToPdf` | 67 lines → 8; becomes the only macOS launcher. |
| `gui/src/main/assembly/install.sh` | Rewritten as the Linux-only installer. |
| `release.sh` | Rewritten as a release driver. |
| `install.sh` (root) | Build-from-source convenience wrapper, updated for the new flow. |
| `README.md`, `gui/readme.md`, `release.md` | JDK requirements removed, Linux prerequisites and recovery added. |

**Deleted**

- `gui/src/main/assembly/mac/run.zsh` — `Contents/MacOS/markdownToPdf` is the only entry point.

---

### Task 1: Pin JavaFX to the runtime's version

This gates every other task. If the GUI cannot compile against JavaFX 21.0.12, the runtime baseline is wrong and the spec needs revisiting before any packaging work happens.

**Files:**
- Modify: `gui/pom.xml:24-25`

**Interfaces:**
- Produces: `javafx.version` = `21.0.12`, relied on by the CI assertion in Task 10.

- [ ] **Step 1: Confirm the runtime's JavaFX version**

```bash
/home/per/.sdkman/candidates/java/21.0.12.fx-librca/bin/java --list-modules | grep '^javafx'
```
Expected, all at `@21.0.12`:

```
javafx.base@21.0.12
javafx.controls@21.0.12
javafx.fxml@21.0.12
javafx.graphics@21.0.12
javafx.media@21.0.12
javafx.swing@21.0.12
javafx.web@21.0.12
```

What matters is that `javafx.controls`, `javafx.swing` and `javafx.web` are present at `21.0.12` — those are the three the module set requests by name. Assert on the names and the version, not on the count; the count is incidental and would change if BellSoft added or dropped an unrelated module. If the version is not `21.0.12`, **stop and report** — the rest of this plan is built on that number.

- [ ] **Step 2: Change the property and delete the stale comment**

In `gui/pom.xml`, replace:

```xml
    <!-- don't forget to update run.sh / run.zsh / run.cmd if javafx version is changed -->
    <javafx.version>23.0.2</javafx.version>
```

with:

```xml
    <!-- Must equal the JavaFX version inside the bundled Liberica runtime; CI asserts it -->
    <javafx.version>21.0.12</javafx.version>
```

- [ ] **Step 3: Compile and run the full build**

```bash
export JAVA_HOME=/home/per/.sdkman/candidates/java/21.0.12.fx-librca
mvn verify
```
Expected: BUILD SUCCESS, 59 tests (30 `lib` + 29 `gui`).

If compilation fails, every error is a use of a JavaFX API added after 21. Fix each one by using the 21 equivalent. Do **not** raise `javafx.version` back — that reintroduces the compile-against-newer-than-runtime defect this task exists to remove. If the API has no 21 equivalent, stop and report; the decision is which Liberica build to base on, and that is not yours to make silently.

- [ ] **Step 4: Smoke-test the GUI by hand**

```bash
mvn install -DskipTests
mvn -q -pl gui org.apache.maven.plugins:maven-dependency-plugin:3.8.1:build-classpath \
  -DincludeScope=runtime -Dmdep.outputFile=/tmp/md2pdf-cp.txt
"$JAVA_HOME/bin/java" -cp "gui/target/classes:$(cat /tmp/md2pdf-cp.txt)" se.alipsa.md2pdf.gui.MarkdownToPdf
```
Expected: the window opens, all three tabs render, the Markdown preview shows HTML. Close it. A clean compile does not prove the runtime behaviour survived a JavaFX downgrade; this is the only check that does.

- [ ] **Step 5: Commit**

```bash
git add gui/pom.xml
git commit -m "build: pin javafx.version to 21.0.12 to match the bundled runtime"
```

---

### Task 2: Replace the fat-jar with a lib/ directory

**Files:**
- Modify: `gui/pom.xml` (delete the `fatjar` profile at lines 255-333; add `maven-dependency-plugin`; add `classpathPrefix`)
- Modify: `pom.xml` (add `maven-dependency-plugin` to `pluginManagement`)
- Modify: `gui/build.sh:22`
- Create: `.github/scripts/check-lib-classpath.sh`
- Delete: `gui/dependency-reduced-pom.xml`

**Interfaces:**
- Produces: `gui/target/MarkdownToPdf-<version>.jar` with a manifest `Class-Path` of `lib/<name>.jar` entries, and `gui/target/lib/` containing exactly those files.
- Produces: `check-lib-classpath.sh <jar> <libdir>` — exit 0 on agreement, non-zero with a message otherwise. Used by Tasks 5, 9 and 10.

- [ ] **Step 1: Write the failing assertion script**

Create `.github/scripts/check-lib-classpath.sh`:

```bash
#!/usr/bin/env bash
# Asserts that a jar's manifest Class-Path and its lib/ directory agree exactly.
#
# A flat lib/ directory can silently lose a jar: copy-dependencies names files
# artifactId-version.jar, so two dependencies from different groups sharing both
# will overwrite each other. prependGroupId is not an option because
# maven-jar-plugin generates Class-Path entries with the plain layout and has no
# matching setting, so renaming the files would desynchronise them from the
# manifest. Comparing the two directly is what catches it.
set -euo pipefail

JAR="${1:?usage: check-lib-classpath.sh <jar> <libdir>}"
LIBDIR="${2:?usage: check-lib-classpath.sh <jar> <libdir>}"

fail() { printf 'FAIL: %s\n' "$*" >&2; exit 1; }

# Manifest values wrap at 72 bytes with a leading space on continuation lines,
# so the header has to be unfolded before it can be split on spaces.
classpath="$(unzip -p "$JAR" META-INF/MANIFEST.MF | tr -d '\r' | awk '
  /^Class-Path: / { cp = substr($0, 13); inCp = 1; next }
  inCp && /^ /    { cp = cp substr($0, 2); next }
  inCp            { inCp = 0 }
  END             { print cp }
')"

[ -n "$classpath" ] || fail "no Class-Path in $JAR manifest"

entries=()
for e in $classpath; do entries+=("$e"); done
[ "${#entries[@]}" -gt 0 ] || fail "empty Class-Path in $JAR"

# 1. every entry is under lib/ and resolves to a file
for e in "${entries[@]}"; do
  case "$e" in
    lib/*) ;;
    *) fail "Class-Path entry not under lib/: $e" ;;
  esac
  [ -f "$LIBDIR/${e#lib/}" ] || fail "Class-Path entry has no file: $e"
done

# 2. entries are unique — a duplicate name is what a filename collision produces
uniq_count="$(printf '%s\n' "${entries[@]}" | sort -u | wc -l | tr -d ' ')"
[ "$uniq_count" -eq "${#entries[@]}" ] \
  || fail "duplicate Class-Path entries: ${#entries[@]} listed, $uniq_count unique (filename collision in lib/)"

# 3. lib/ holds exactly those files and nothing else
file_count="$(find "$LIBDIR" -maxdepth 1 -name '*.jar' | wc -l | tr -d ' ')"
[ "$file_count" -eq "${#entries[@]}" ] \
  || fail "$LIBDIR has $file_count jars but Class-Path lists ${#entries[@]}"

# 4. scope regressions: provided and test dependencies must not be here
for pattern in 'javafx-*' 'junit-*' 'opentest4j-*' 'apiguardian-*'; do
  found="$(find "$LIBDIR" -maxdepth 1 -name "$pattern" -print -quit)"
  [ -z "$found" ] \
    || fail "$found is in $LIBDIR — copy-dependencies is missing includeScope=runtime"
done

printf 'OK: %s jars, Class-Path consistent\n' "${#entries[@]}"
```

```bash
chmod +x .github/scripts/check-lib-classpath.sh
```

- [ ] **Step 2: Run it to verify it fails**

```bash
mvn -q install -DskipTests
.github/scripts/check-lib-classpath.sh gui/target/MarkdownToPdf-0.1.1-SNAPSHOT.jar gui/target/lib
```
Expected: FAIL — the `Class-Path` entries are bare jar names, not `lib/…`, so the first check trips ("Class-Path entry not under lib/"). `gui/target/lib` does not exist yet either.

- [ ] **Step 3: Add the plugin version to root pluginManagement**

`maven-dependency-plugin` is not currently managed in `pom.xml`. Inside `<build><pluginManagement><plugins>`, beside the other managed plugins:

```xml
        <plugin>
          <groupId>org.apache.maven.plugins</groupId>
          <artifactId>maven-dependency-plugin</artifactId>
          <version>3.8.1</version>
        </plugin>
```

- [ ] **Step 4: Configure copy-dependencies and the classpath prefix in `gui/pom.xml`**

Add to `<build><plugins>`:

```xml
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-dependency-plugin</artifactId>
        <executions>
          <execution>
            <id>copy-runtime-dependencies</id>
            <phase>package</phase>
            <goals>
              <goal>copy-dependencies</goal>
            </goals>
            <configuration>
              <outputDirectory>${project.build.directory}/lib</outputDirectory>
              <!-- Mandatory. The default copies provided and test scope too, which would put
                   the JavaFX jars next to a runtime that already contains the JavaFX modules. -->
              <includeScope>runtime</includeScope>
              <overWriteReleases>true</overWriteReleases>
              <overWriteSnapshots>true</overWriteSnapshots>
            </configuration>
          </execution>
        </executions>
      </plugin>
```

And in the existing `maven-jar-plugin` `<manifest>` block, after `<addClasspath>true</addClasspath>`:

```xml
              <classpathPrefix>lib/</classpathPrefix>
```

- [ ] **Step 5: Delete the fatjar profile**

Remove the entire `<profiles>…</profiles>` block from `gui/pom.xml` (lines 255-333, the `fatjar` profile and the `maven-shade-plugin` inside it). It is the only profile in that file.

Then:

```bash
rm -f gui/dependency-reduced-pom.xml
```

`rm`, not `git rm`: the file is matched by `dependency-reduced-pom.xml` in `.gitignore` and has never been tracked, so `git rm` fails. Leave the ignore rule in place — it costs nothing and removing it is a separate decision.

- [ ] **Step 6: Drop -Pfatjar from build.sh**

In `gui/build.sh`, change the last line from `mvn -Pfatjar clean package || exit 1` to:

```bash
mvn clean package || exit 1
```

- [ ] **Step 7: Rebuild and run the assertion script**

```bash
mvn -q install -DskipTests
.github/scripts/check-lib-classpath.sh gui/target/MarkdownToPdf-0.1.1-SNAPSHOT.jar gui/target/lib
```
Expected, verbatim: `OK: 45 jars, Class-Path consistent`. This exact configuration and this exact script were run against this repo on 2026-08-08 and produced that line — if you get anything else, the difference is in what you changed, not in the approach. The script derives the count rather than hardcoding it, so a later dependency change does not break the check.

- [ ] **Step 8: Verify the app still runs from the jar**

```bash
"$JAVA_HOME/bin/java" -jar gui/target/MarkdownToPdf-0.1.1-SNAPSHOT.jar
```
Expected: the window opens. This must run on a **JavaFX-bundled JDK 21** — `$JAVA_HOME` above is one. It will not run on a plain OpenJDK, and that is correct: JavaFX is `provided` scope and deliberately absent from `lib/`.

- [ ] **Step 9: Full build**

```bash
mvn verify
```
Expected: BUILD SUCCESS.

- [ ] **Step 10: Commit**

```bash
git add pom.xml gui/pom.xml gui/build.sh .github/scripts/check-lib-classpath.sh
git commit -m "build: replace the shade fat-jar with a lib/ directory and manifest Class-Path"
```

---

### Task 3: Smoke tests

A hand-curated module set makes "does it actually run" the central risk. These two programs are how every later task checks a runtime. They live in `.github/scripts/`, are compiled to a temporary directory on demand, and are never shipped in any archive.

**Files:**
- Create: `.github/scripts/EngineSmoke.java`
- Create: `.github/scripts/ToolkitSmoke.java`
- Create: `.github/scripts/compile-smoke.sh`

**Interfaces:**
- Produces: `compile-smoke.sh <app-jar> <output-dir> [jdk-home]` — compiles both tests into `<output-dir>` with `[jdk-home]`, defaulting to `$JAVA_HOME`, and **prints the classpath to run them with** on stdout (native paths and `;` on Windows, POSIX and `:` elsewhere). Diagnostics go to stderr.
- Produces: `EngineSmoke` — run as `<java> -cp "$(compile-smoke.sh …)" EngineSmoke`; exit 0 on success. Resolves dependencies through the jar's manifest `Class-Path`, so it also proves `lib/` is complete.
- Produces: `ToolkitSmoke` — same invocation; exit 0 on success. Needs a display (`xvfb-run` on Linux).

**They are compiled, not run from source.** The bundled runtime has no `jdk.compiler`, so `java EngineSmoke.java` on it fails with `InternalError: Module jdk.compiler not in boot Layer`. Compiling with the full JDK and running the `.class` files on the bundled runtime tests exactly what needed testing — the *runtime* module set — without putting a compiler in every user's download.

- [ ] **Step 1: Write the engine smoke test**

Create `.github/scripts/EngineSmoke.java`:

```java
// Converts Markdown to PDF on whatever runtime invokes it. Exercises Batik, PDFBox and
// OpenHTMLtoPDF, so a java.* module missing from the jlink set fails here rather than in
// the field. Run with MarkdownToPdf.jar on the classpath: dependencies resolve through
// that jar's manifest Class-Path, which is also what proves lib/ is complete.
import se.alipsa.md2pdf.Md2PdfEngine;

public class EngineSmoke {
  public static void main(String[] args) throws Exception {
    String md = "# Smoke\n\nHello *world*.\n\n- one\n- two\n\n`code`\n";
    byte[] pdf = new Md2PdfEngine().markdown(md).toPdf();

    // Observed output for this input is ~1.6 KB; 500 catches a truncated or empty render
    // without being so tight that a harmless rendering change trips it.
    if (pdf.length < 500) {
      System.err.println("EngineSmoke: PDF is only " + pdf.length + " bytes");
      System.exit(1);
    }
    String magic = new String(pdf, 0, 4, java.nio.charset.StandardCharsets.US_ASCII);
    if (!"%PDF".equals(magic)) {
      System.err.println("EngineSmoke: expected %PDF, got " + magic);
      System.exit(1);
    }
    System.out.println("EngineSmoke: OK (" + pdf.length + " bytes)");
  }
}
```

- [ ] **Step 2: Write the toolkit smoke test**

Create `.github/scripts/ToolkitSmoke.java`:

```java
// Starts the JavaFX toolkit and constructs a WebView. This is the only assertion that
// javafx.web's WebKit native library actually loads — a class-load check passes without it.
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.scene.web.WebView;

public class ToolkitSmoke {
  public static void main(String[] args) throws Exception {
    CountDownLatch done = new CountDownLatch(1);
    AtomicReference<Throwable> failure = new AtomicReference<>();

    Platform.startup(
        () -> {
          try {
            WebView view = new WebView();
            view.getEngine().loadContent("<html><body><h1>ok</h1></body></html>");
          } catch (Throwable t) {
            failure.set(t);
          } finally {
            done.countDown();
          }
        });

    if (!done.await(60, TimeUnit.SECONDS)) {
      System.err.println("ToolkitSmoke: JavaFX toolkit did not start within 60s");
      Runtime.getRuntime().halt(1);
    }
    Platform.exit();

    Throwable t = failure.get();
    if (t != null) {
      System.err.println("ToolkitSmoke: failed on the FX thread");
      t.printStackTrace();
      Runtime.getRuntime().halt(1);
    }
    System.out.println("ToolkitSmoke: OK");
    Runtime.getRuntime().halt(0);
  }
}
```

`Runtime.halt` rather than `System.exit`: the FX toolkit keeps non-daemon threads alive, and a shutdown hook race here would turn a passing test into a hung CI job.

- [ ] **Step 3: Write the compile helper**

Create `.github/scripts/compile-smoke.sh`:

```bash
#!/usr/bin/env bash
# Compiles the smoke tests with a full JDK, for running on a runtime that has no compiler,
# and prints the classpath to run them with.
#
#   CP="$(compile-smoke.sh <app-jar> <output-dir> [jdk-home])"
#   "$some_java" -cp "$CP" EngineSmoke
#
# The bundled runtime deliberately omits jdk.compiler, so `java EngineSmoke.java` fails on
# it with "InternalError: Module jdk.compiler not in boot Layer". Compiling here and running
# the .class files there tests the runtime module set, which is the point, without shipping
# a compiler to every user.
#
# This script prints the classpath rather than leaving each caller to build one, because
# getting it right on Windows means both a ';' separator and native paths — Git Bash hands
# POSIX paths and colon lists to a native java.exe with heuristic, unreliable conversion.
# That logic belongs in one place.
set -euo pipefail

JAR="${1:?usage: compile-smoke.sh <app-jar> <output-dir> [jdk-home]}"
OUT="${2:?usage: compile-smoke.sh <app-jar> <output-dir> [jdk-home]}"
JDK_HOME="${3:-${JAVA_HOME:-}}"
SCRIPTS="$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" > /dev/null 2>&1 && pwd )"

[ -n "$JDK_HOME" ] \
  || { echo "pass a jdk-home or set JAVA_HOME to a JavaFX-bundled JDK 21" >&2; exit 1; }
JAVAC="$JDK_HOME/bin/javac"
[ -x "$JAVAC" ] || [ -x "$JAVAC.exe" ] || { echo "no javac in $JDK_HOME/bin" >&2; exit 1; }
[ -f "$JAR" ] || { echo "no such jar: $JAR" >&2; exit 1; }

rm -rf "$OUT"
mkdir -p "$OUT"
# -proc:none: dependencies on the classpath carry annotation processors, and javac warns
# at length about implicitly running them. Diagnostics go to stderr so stdout stays clean
# for the classpath this prints.
"$JAVAC" -proc:none -cp "$JAR" -d "$OUT" \
  "$SCRIPTS/EngineSmoke.java" "$SCRIPTS/ToolkitSmoke.java" >&2

native() {
  if command -v cygpath > /dev/null 2>&1; then cygpath -w "$1"; else printf '%s' "$1"; fi
}
if command -v cygpath > /dev/null 2>&1; then
  printf '%s;%s\n' "$(native "$JAR")" "$(native "$OUT")"
else
  printf '%s:%s\n' "$JAR" "$OUT"
fi
```

```bash
chmod +x .github/scripts/compile-smoke.sh
```

`cygpath` is present exactly on the Git Bash / MSYS platforms that need the conversion, so its presence is the platform test — no `$PLATFORM` argument is needed and no caller can forget to pass one.

No `--add-modules` is needed to compile `ToolkitSmoke` — in a JavaFX-bundled image the `javafx.*` modules are already roots. If `javafx.scene.web` is not found, the JDK is a plain one, not a missing flag.

- [ ] **Step 4: Run both against the current build**

```bash
mvn -q install -DskipTests
JAR=gui/target/MarkdownToPdf-0.1.1-SNAPSHOT.jar
CP="$(.github/scripts/compile-smoke.sh "$JAR" /tmp/md2pdf-smoke)"
"$JAVA_HOME/bin/java" -cp "$CP" EngineSmoke
xvfb-run -a "$JAVA_HOME/bin/java" -cp "$CP" ToolkitSmoke
```
Expected: `EngineSmoke: OK (~1600 bytes)` — preceded by three `SLF4J(W): No SLF4J providers were found` lines, which are expected and must not be "fixed" — and `ToolkitSmoke: OK`. Both exit 0.

If `xvfb-run` is not installed: `sudo apt-get install -y xvfb`.

- [ ] **Step 5: Commit**

```bash
git add .github/scripts/EngineSmoke.java .github/scripts/ToolkitSmoke.java .github/scripts/compile-smoke.sh
git commit -m "test: add engine and JavaFX toolkit smoke tests"
```

---

### Task 4: The jlink runtime

**Files:**
- Modify: `gui/createApp.sh` (rewritten; this task adds the argument parsing and the `jlink` step only — layout comes in Tasks 5, 7 and 8)

**Interfaces:**
- Produces: `createApp.sh <linux|macos|windows>`, failing with a usage message on anything else.
- Produces: shell functions used by later tasks — `app_version()` (echoes `${revision}` read from the built jar manifest), `build_runtime <output-dir>` (creates the jlink image), `require_arch <expected>`.

- [ ] **Step 1: Write the new createApp.sh skeleton**

Replace the whole of `gui/createApp.sh`:

```bash
#!/usr/bin/env bash
# Builds the self-contained MarkdownToPdf archive for one platform.
#
# Must run natively on the target platform: jlink can only produce an image for the
# platform whose jmods it is given, and $JAVA_HOME is the only JDK we have.
#
#   ./createApp.sh linux | macos | windows
#
# Windows: run under Git Bash. Build scripts and installers have different audiences —
# only the installers have to be native to the platform's shell.
set -euo pipefail

DIR="$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" > /dev/null 2>&1 && pwd )"
TARGET="$DIR/target"

PLATFORM="${1:-}"
case "$PLATFORM" in
  linux)   ARCH_LABEL="linux-x64"      ; EXPECTED_ARCH="x86_64" ;;
  macos)   ARCH_LABEL="macos-aarch64"  ; EXPECTED_ARCH="arm64"  ;;
  windows) ARCH_LABEL="windows-x64"    ; EXPECTED_ARCH="x86_64" ;;
  *) echo "usage: $0 <linux|macos|windows>" >&2; exit 2 ;;
esac

# The module set is hand-curated: jlink's --bind-services or a whole-JDK image would
# undo the size saving this packaging exists for. jdk.localedata is deliberately absent
# (~15 MB); the consequence is English-only locale formatting.
MODULES="javafx.controls,javafx.swing,javafx.web,\
java.desktop,java.logging,java.management,java.naming,\
java.net.http,java.prefs,java.scripting,java.sql,java.xml,\
jdk.charsets,jdk.crypto.ec,jdk.unsupported,jdk.zipfs"

die() { echo "ERROR: $*" >&2; exit 1; }

# Git Bash ships neither zip nor unzip, so a Windows build fails here with a usable
# message rather than half-writing an archive.
require_tools() {
  local missing=()
  for t in unzip find awk sed; do
    command -v "$t" > /dev/null || missing+=("$t")
  done
  # A jlink image contains symlinks. zip -y stores them; 7z follows them, which inflates
  # the archive and produces a runtime that breaks on extraction — so 7z is acceptable
  # only on Windows, where the image has no symlinks to lose.
  if ! command -v zip > /dev/null; then
    if [ "$PLATFORM" = "windows" ] && command -v 7z > /dev/null; then
      :
    else
      missing+=("zip")
    fi
  fi
  [ "${#missing[@]}" -eq 0 ] || die "missing required tools: ${missing[*]}"
}

make_zip() {                  # make_zip <output.zip> <directory-whose-contents-to-zip>
  local zipfile="$1" srcdir="$2"
  rm -f "$zipfile"
  if command -v zip > /dev/null; then
    ( cd "$srcdir" && zip -q -r -y "$zipfile" . )
  else
    ( cd "$srcdir" && 7z a -tzip -bso0 -bsp0 "$zipfile" . )
  fi
}

require_arch() {
  local actual
  case "$PLATFORM" in
    windows) actual="${PROCESSOR_ARCHITECTURE:-unknown}"
             [ "$actual" = "AMD64" ] || die "expected AMD64, got $actual" ;;
    *)       actual="$(uname -m)"
             [ "$actual" = "$EXPECTED_ARCH" ] || die "expected $EXPECTED_ARCH, got $actual" ;;
  esac
}

# The version comes from the built jar's manifest rather than from the POM, so the
# archive name can never disagree with the jar inside it.
app_version() {
  local version
  version="$(unzip -p "$(app_jar)" META-INF/MANIFEST.MF | tr -d '\r' \
    | awk -F': ' '/^Implementation-Version: /{print $2; exit}')"
  # addDefaultImplementationEntries writes this; an empty value means the manifest is not
  # the one maven-jar-plugin produces, and every archive name downstream would be wrong.
  [ -n "$version" ] || die "no Implementation-Version in $(app_jar) manifest"
  printf '%s\n' "$version"
}

# Exactly one candidate, never "the first one". target/ accumulates: bump ${revision} without
# a clean and two versions sit side by side, and picking either one silently produces an
# archive whose name, contents and version need not agree.
app_jar() {
  local jars count
  jars="$(find "$TARGET" -maxdepth 1 -name 'MarkdownToPdf-*.jar' \
    ! -name '*-sources.jar' ! -name '*-javadoc.jar' | sort)"
  # A string rather than an array on purpose: macOS ships bash 3.2, where expanding an
  # empty array under `set -u` is an "unbound variable" error, and empty is a case this
  # function has to report rather than crash on.
  count="$(printf '%s' "$jars" | grep -c . || true)"
  case "$count" in
    0) die "no application jar in $TARGET — run 'mvn install' first" ;;
    1) printf '%s\n' "$jars" ;;
    *) die "$TARGET holds $count application jars:
$jars
Run 'mvn clean install' — packaging will not guess which one to ship." ;;
  esac
}

build_runtime() {
  local out="$1"
  [ -n "${JAVA_HOME:-}" ] || die "JAVA_HOME is not set"
  [ -d "$JAVA_HOME/jmods" ] \
    || die "$JAVA_HOME has no jmods/ — a Liberica *Full* JDK is required, not a JRE"

  rm -rf "$out"
  # --compress=2 is ZIP and is the strongest level JDK 21 accepts. The zip-<n> syntax is
  # JDK 22+ and makes JDK 21 jlink fail rather than fall back.
  "$JAVA_HOME/bin/jlink" \
    --module-path "$JAVA_HOME/jmods" \
    --add-modules "$MODULES" \
    --strip-debug --no-header-files --no-man-pages --compress=2 \
    --output "$out"
}

require_tools
require_arch
[ -d "$TARGET/lib" ] || die "$TARGET/lib is missing — run 'mvn install' first"
VERSION="$(app_version)"
echo "Building MarkdownToPdf $VERSION for $ARCH_LABEL"
```

```bash
chmod +x gui/createApp.sh
```

- [ ] **Step 2: Add a temporary runtime build call and run it**

Append to `gui/createApp.sh` for this step only:

```bash
build_runtime "$TARGET/runtime"
echo "runtime built at $TARGET/runtime"
```

```bash
mvn -q install -DskipTests
./gui/createApp.sh linux
```
Expected: a `gui/target/runtime` directory, built in well under a minute.

- [ ] **Step 3: Assert the runtime is correct and complete**

```bash
gui/target/runtime/bin/java -version
gui/target/runtime/bin/java --list-modules | grep -c .
gui/target/runtime/bin/java --list-modules | grep '^javafx.web'
du -sh gui/target/runtime
```
Expected: version 21.0.12; roughly 20 modules (the 16 requested plus transitive `javafx.base`, `javafx.graphics`, `javafx.media`, `java.base`); `javafx.web@21.0.12`; on the order of 100 MB.

Now the assertion that matters — both smoke tests **on the bundled runtime, with the ambient JDK scrubbed**:

```bash
JAR="$(find gui/target -maxdepth 1 -name 'MarkdownToPdf-*.jar' ! -name '*-sources.jar' ! -name '*-javadoc.jar')"
# The same rule createApp.sh's app_jar() applies: exactly one, never "the first one". A
# target/ left over from an earlier ${revision} would otherwise get smoke-tested in place
# of the jar you just built, and the runtime would be declared good on the wrong evidence.
[ "$(printf '%s' "$JAR" | grep -c .)" = 1 ] \
  || { printf 'not exactly one application jar:\n%s\nrun: mvn clean install -DskipTests\n' "$JAR"; false; }
cp "$JAR" gui/target/MarkdownToPdf.jar
CP="$(.github/scripts/compile-smoke.sh gui/target/MarkdownToPdf.jar /tmp/md2pdf-smoke)"
env -i PATH=/usr/bin:/bin HOME="$HOME" \
  gui/target/runtime/bin/java -cp "$CP" EngineSmoke
env -i PATH=/usr/bin:/bin HOME="$HOME" DISPLAY="${DISPLAY:-}" \
  xvfb-run -a gui/target/runtime/bin/java -cp "$CP" ToolkitSmoke
```

The tests are compiled with `$JAVA_HOME` first and then run on the bundled runtime, because the runtime has no `jdk.compiler`. `java …/EngineSmoke.java` against it fails with `InternalError: Module jdk.compiler not in boot Layer`.
Expected: both print OK and exit 0.

`env -i` is not decoration. Without it the test can silently lean on `$JAVA_HOME` or a `java` on `PATH`, and the whole point of this step is that it cannot.

If `EngineSmoke` throws `NoClassDefFoundError` on a `java.*` class or `ToolkitSmoke` fails to load a native library, a module is missing from `MODULES`. Add it, rebuild, re-run — and note it in the commit message, since the curated list is the design's stated risk.

Note the `cp` above: `MarkdownToPdf.jar` beside `lib/` is what the manifest `Class-Path` expects. Task 5 makes packaging do this properly.

- [ ] **Step 4: Remove the temporary call**

Delete the two lines added in Step 2. Task 5 calls `build_runtime` from the real packaging path.

- [ ] **Step 5: Commit**

```bash
git add gui/createApp.sh
git commit -m "build: add jlink runtime construction to createApp.sh"
```

---

### Task 5: Linux archive — layout and launchers

**Files:**
- Modify: `gui/createApp.sh` (add the packaging body)
- Modify: `gui/src/main/assembly/linux/run.sh`
- Modify: `gui/src/main/assembly/linux/createLauncher.sh`
- Create: `.github/scripts/verify-install.sh`
- Create: `.github/scripts/verify-launcher.sh`

**Interfaces:**
- Consumes: `build_runtime`, `app_version`, `app_jar`, `die` from Task 4; `check-lib-classpath.sh` from Task 2.
- Produces: `gui/target/md2pdf-<version>-linux-x64.zip`.
- Produces: `verify-install.sh <linux|macos|windows> <install-dir> <expected-javafx-version>` — every post-install assertion for that platform, exit 0 on success. All three arguments are required. Used by Tasks 6, 7, 8 and 10.
- Produces: `verify-launcher.sh <linux|macos|windows> <install-dir>` — starts the installed launcher, asserts the app is still running after `MD2PDF_LAUNCH_SETTLE` (default 10) seconds, stops it. Called by `verify-install.sh` as its final step; no other caller needs to invoke it directly.

- [ ] **Step 1: Write the verification script (it fails first)**

Create `.github/scripts/verify-install.sh`:

```bash
#!/usr/bin/env bash
# Asserts an installed MarkdownToPdf is complete and runnable.
#
#   verify-install.sh <linux|macos|windows> <install-dir> <expected-javafx-version>
#
# Runs against the installed tree, never the build tree, and always on the bundled
# runtime with the ambient JDK scrubbed — otherwise a missing runtime would be masked
# by whatever java happens to be on PATH.
set -euo pipefail

PLATFORM="${1:?usage: verify-install.sh <platform> <install-dir> <javafx-version>}"
DEST="${2:?usage: verify-install.sh <platform> <install-dir> <javafx-version>}"
FX_VERSION="${3:?usage: verify-install.sh <platform> <install-dir> <javafx-version>}"

SCRIPTS="$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" > /dev/null 2>&1 && pwd )"
fail() { printf 'FAIL: %s\n' "$*" >&2; exit 1; }
ok()   { printf 'ok: %s\n' "$*"; }

# Windows environment variables hold native paths (C:\Users\…), which bash file tests do
# not understand. Anything coming from the Windows environment has to be converted before
# it is used as a path here — including the install directory this script is handed.
unixpath() {
  if command -v cygpath > /dev/null 2>&1; then cygpath -u "$1"; else printf '%s' "$1"; fi
}
DEST="$(unixpath "$DEST")"

case "$PLATFORM" in
  macos)   ROOT="$DEST/Contents"; APP="$ROOT/app"; JAVA="$ROOT/runtime/bin/java" ;;
  linux)   ROOT="$DEST";          APP="$DEST";     JAVA="$DEST/runtime/bin/java" ;;
  windows) ROOT="$DEST";          APP="$DEST";     JAVA="$DEST/runtime/bin/java.exe" ;;
  *) fail "unknown platform: $PLATFORM" ;;
esac

[ -d "$DEST" ] || fail "nothing installed at $DEST"
[ -x "$JAVA" ] || fail "$JAVA missing or not executable"
ok "runtime present"

# The unzip tool may not preserve Unix modes; the installer re-applies them. If this
# fails the installer's chmod pass regressed.
if [ "$PLATFORM" != "windows" ]; then
  [ -x "$ROOT/runtime/lib/jspawnhelper" ] \
    || fail "runtime/lib/jspawnhelper is not executable — the installer's chmod pass is incomplete"
  ok "execute bits re-applied"
fi

version="$("$JAVA" -version 2>&1 | head -1 | cut -d'"' -f2)"
case "$version" in 21.*) ok "runtime java $version" ;; *) fail "expected java 21, got $version" ;; esac

modules="$("$JAVA" --list-modules)"
printf '%s\n' "$modules" | grep -q '^javafx.web@' || fail "javafx.web is not in the runtime"
actual_fx="$(printf '%s\n' "$modules" | sed -n 's/^javafx\.controls@//p')"
[ "$actual_fx" = "$FX_VERSION" ] \
  || fail "runtime JavaFX is $actual_fx but javafx.version is $FX_VERSION — they must match"
ok "javafx $actual_fx matches the POM"

[ -f "$APP/MarkdownToPdf.jar" ] || fail "$APP/MarkdownToPdf.jar missing"
[ -d "$APP/lib" ] || fail "$APP/lib missing"
"$SCRIPTS/check-lib-classpath.sh" "$APP/MarkdownToPdf.jar" "$APP/lib"

case "$PLATFORM" in
  linux)
    launcher="${XDG_DATA_HOME:-$HOME/.local/share}/applications/MarkdownToPdf.desktop"
    [ -f "$launcher" ] || fail "no .desktop launcher at $launcher"
    grep -q "^Exec=$DEST/run.sh$" "$launcher" || fail "$launcher does not exec $DEST/run.sh"
    [ -x "$DEST/run.sh" ] || fail "run.sh is not executable"
    ok "desktop launcher"
    ;;
  macos)
    exe="$(/usr/libexec/PlistBuddy -c 'Print :CFBundleExecutable' "$ROOT/Info.plist")"
    [ -x "$ROOT/MacOS/$exe" ] || fail "CFBundleExecutable=$exe but MacOS/$exe is missing or not executable"
    for key in CFBundleVersion CFBundleShortVersionString; do
      v="$(/usr/libexec/PlistBuddy -c "Print :$key" "$ROOT/Info.plist")"
      printf '%s' "$v" | grep -Eq '^[0-9]+(\.[0-9]+){0,2}$' \
        || fail "$key=$v is not one to three period-separated integers — Apple rejects it"
    done
    /usr/libexec/PlistBuddy -c 'Print :CFBundleIdentifier' "$ROOT/Info.plist" > /dev/null \
      || fail "CFBundleIdentifier is missing — codesign cannot sign the bundle without it"
    codesign --verify --deep --strict "$DEST" || fail "codesign verification failed"
    ok "bundle metadata and signature"
    ;;
  windows)
    [ -f "$DEST/run.cmd" ] || fail "run.cmd missing"
    [ -x "$ROOT/runtime/bin/javaw.exe" ] || fail "runtime/bin/javaw.exe missing"
    userprofile="$(unixpath "${USERPROFILE:-$HOME}")"
    shortcut_found=0
    for d in "$userprofile/Desktop" "$userprofile/OneDrive/Desktop"; do
      [ -f "$d/MarkdownToPdf.lnk" ] && shortcut_found=1
      [ -f "$d/MarkdownToPdf.cmd" ] && shortcut_found=1
    done
    [ "$shortcut_found" -eq 1 ] || fail "neither a .lnk nor a stub .cmd was created on the Desktop"
    ok "windows shortcut or stub"
    ;;
esac

# The real test: run the app's own code on the installed runtime, with nothing from the
# ambient environment available to stand in for it.
# Compiled here with the full JDK, run below on the bundled runtime: the runtime has no
# jdk.compiler, so source-file mode fails on it outright. This has to happen before the
# scrubbing, since it is the one step that legitimately needs JAVA_HOME. compile-smoke.sh
# prints the classpath in the form the local java expects.
SMOKE="$(mktemp -d)"
trap 'rm -rf "$SMOKE"' EXIT
CP="$("$SCRIPTS/compile-smoke.sh" "$APP/MarkdownToPdf.jar" "$SMOKE")"

# PATH is set explicitly rather than left to env's built-in default, so that what is kept
# (xvfb-run, the system utilities) and what is dropped (any JDK on the caller's PATH) is
# visible in the script rather than a property of env.
scrub() {
  if [ "$PLATFORM" = "windows" ]; then
    JAVA_HOME= "$@"
  else
    env -i PATH=/usr/bin:/bin HOME="$HOME" DISPLAY="${DISPLAY:-}" "$@"
  fi
}

scrub "$JAVA" -cp "$CP" EngineSmoke || fail "engine smoke failed"

if [ "$PLATFORM" = "linux" ]; then
  scrub xvfb-run -a "$JAVA" -cp "$CP" ToolkitSmoke || fail "toolkit smoke failed"
else
  scrub "$JAVA" -cp "$CP" ToolkitSmoke || fail "toolkit smoke failed"
fi

# Everything above proves the installed *files* are right and that the app's code runs on
# the installed runtime. It does not prove the launcher works, so it is the last thing
# checked — after this, a pass means the thing the user double-clicks actually starts.
"$SCRIPTS/verify-launcher.sh" "$PLATFORM" "$DEST"

printf '\nPASS: %s install at %s\n' "$PLATFORM" "$DEST"
```

```bash
chmod +x .github/scripts/verify-install.sh
```

Now its companion. Create `.github/scripts/verify-launcher.sh`:

```bash
#!/usr/bin/env bash
# Starts the installed launcher and asserts the application is still running a few seconds
# later, then stops it.
#
#   verify-launcher.sh <linux|macos|windows> <install-dir>
#
# Checking that run.sh / run.cmd / the bundle executable *exist* cannot catch a launcher
# that points at the wrong java, quotes a path badly, or names a main class that is not
# there. Each of those leaves a file that is present and a program that dies on its first
# line. Starting it is the only assertion that separates the two.
#
# Called at the end of verify-install.sh, so every caller of that script gets this for free
# and there is only one entry point to remember. It is a separate file because it is the
# only check here that starts a GUI and has to clean processes up afterwards.
set -euo pipefail

PLATFORM="${1:?usage: verify-launcher.sh <platform> <install-dir>}"
DEST="${2:?usage: verify-launcher.sh <platform> <install-dir>}"

unixpath() {
  if command -v cygpath > /dev/null 2>&1; then cygpath -u "$1"; else printf '%s' "$1"; fi
}
DEST="$(unixpath "$DEST")"
fail() { printf 'FAIL: %s\n' "$*" >&2; exit 1; }

# Seconds to wait before deciding the app is up. A launcher defect kills the process in
# well under a second; JavaFX toolkit startup on a cold runner is a few. Overridable so a
# slow machine does not need a code change.
SETTLE="${MD2PDF_LAUNCH_SETTLE:-10}"
LOG="$(mktemp)"
trap 'rm -f "$LOG"' EXIT

case "$PLATFORM" in
  windows)
    # run.cmd uses `start`, so it returns at once and the JVM is not its child. Two
    # separate signals are needed: its exit status catches a wrong or badly quoted
    # javaw.exe path, and tasklist catches a JVM that started and then died.
    taskkill //F //IM javaw.exe > /dev/null 2>&1 || true   # no pre-existing process to mistake for ours
    ( cd "$DEST" && cmd //c run.cmd ) > "$LOG" 2>&1 \
      || { cat "$LOG" >&2; fail "run.cmd exited non-zero"; }
    sleep "$SETTLE"
    tasklist //FI "IMAGENAME eq javaw.exe" | grep -qi 'javaw.exe' \
      || { cat "$LOG" >&2; fail "run.cmd left no javaw.exe running — the app died on startup"; }
    taskkill //F //IM javaw.exe > /dev/null 2>&1 || true
    ;;
  *)
    case "$PLATFORM" in
      # The bundle executable is read from Info.plist rather than hardcoded, so this fails
      # for the right reason if CFBundleExecutable and the file on disk ever disagree.
      macos) exe="$(/usr/libexec/PlistBuddy -c 'Print :CFBundleExecutable' "$DEST/Contents/Info.plist")"
             launcher=("$DEST/Contents/MacOS/$exe") ;;
      linux) launcher=(xvfb-run -a "$DEST/run.sh") ;;
      *) fail "unknown platform: $PLATFORM" ;;
    esac
    "${launcher[@]}" > "$LOG" 2>&1 &
    pid=$!
    sleep "$SETTLE"
    if ! kill -0 "$pid" 2> /dev/null; then
      cat "$LOG" >&2
      fail "the launcher exited within ${SETTLE}s instead of running the app"
    fi
    kill "$pid" 2> /dev/null || true
    # Backstop: on Linux the killed process is xvfb-run, and the JVM under it can outlive
    # the wrapper. A leaked JVM would hold the runner until the job times out.
    pkill -f 'MarkdownToPdf\.jar' 2> /dev/null || true
    wait "$pid" 2> /dev/null || true
    ;;
esac

printf 'ok: launcher started and was still running after %ss\n' "$SETTLE"
```

```bash
chmod +x .github/scripts/verify-launcher.sh
```

This runs the real GUI, so it needs the same display the toolkit smoke test does — `xvfb-run` on Linux, nothing extra on macOS or Windows. That is not a new requirement: `ToolkitSmoke` already constructs a `WebView` on all three runners, so any platform where this cannot start is one where the existing smoke test would already have failed.

- [ ] **Step 2: Run it to verify it fails**

```bash
.github/scripts/verify-install.sh linux "$HOME/.local/share/MarkdownToPdf" 21.0.12
```
Expected: FAIL — "nothing installed at …". Nothing packages or installs yet.

- [ ] **Step 3: Rewrite run.sh**

Replace the whole of `gui/src/main/assembly/linux/run.sh`:

```bash
#!/usr/bin/env bash
# Launches MarkdownToPdf on the runtime bundled beside this script.
set -euo pipefail
DIR="$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" > /dev/null 2>&1 && pwd )"
exec "$DIR/runtime/bin/java" -Xmx8g -jar "$DIR/MarkdownToPdf.jar"
```

Everything else that was in this file — `-version` parsing, the sdkman recovery loop, `md2pdf.env`, `MD2PDF_JAVA_HOME`, the newest-matching-jar search — is deleted, not moved.

- [ ] **Step 4: Trim createLauncher.sh**

In `gui/src/main/assembly/linux/createLauncher.sh`, delete the `chmod +x run.sh` line — the installer now owns execute bits for the whole tree, and a launcher script should not be quietly fixing up the install. Everything else in the file stays, including `set -euo pipefail` and the non-fatal Desktop-symlink handling.

- [ ] **Step 5: Add the packaging body to createApp.sh**

Append to `gui/createApp.sh`:

```bash
STAGE="$TARGET/stage-$ARCH_LABEL"
rm -rf "$STAGE"

# The application jar is copied under a fixed name so launchers, .desktop entries and
# Windows shortcuts can reference a path that survives a version bump. The version stays
# readable from the manifest's Implementation-Version.
stage_app() {                 # stage_app <dir-to-put-jar-and-lib-in>
  mkdir -p "$1"
  cp "$(app_jar)" "$1/MarkdownToPdf.jar"
  cp -R "$TARGET/lib" "$1/lib"
}

case "$PLATFORM" in
  linux)
    APPDIR="$STAGE/MarkdownToPdf"
    mkdir -p "$APPDIR"
    stage_app "$APPDIR"
    build_runtime "$APPDIR/runtime"
    cp "$DIR/src/main/assembly/linux/run.sh"           "$APPDIR/"
    cp "$DIR/src/main/assembly/linux/createLauncher.sh" "$APPDIR/"
    cp "$DIR/src/main/resources/MarkdownToPdf-rounded.png" "$APPDIR/"
    cp "$DIR/src/main/assembly/install.sh"             "$STAGE/md2pdf-install.sh"
    chmod +x "$APPDIR"/*.sh "$STAGE/md2pdf-install.sh"
    ;;
esac

"$DIR/../.github/scripts/check-lib-classpath.sh" \
  "$(find "$STAGE" -name MarkdownToPdf.jar | head -1)" \
  "$(find "$STAGE" -name lib -type d | head -1)"

ZIP="$TARGET/md2pdf-${VERSION}-${ARCH_LABEL}.zip"
make_zip "$ZIP" "$STAGE"
echo "Built $ZIP"
```

`make_zip` (Task 4) is used rather than a bare `zip` call because of the symlink and Git Bash constraints described there.

- [ ] **Step 6: Build and inspect the zip**

```bash
mvn -q install -DskipTests
./gui/createApp.sh linux
unzip -l gui/target/md2pdf-*-linux-x64.zip | head -20
du -h gui/target/md2pdf-*-linux-x64.zip
```
Expected: top-level entries `MarkdownToPdf/` and `md2pdf-install.sh`; `MarkdownToPdf/` contains `runtime/`, `MarkdownToPdf.jar`, `lib/`, `run.sh`, `createLauncher.sh`, `MarkdownToPdf-rounded.png`. Size 80–110 MB.

The `check-lib-classpath.sh` call inside the script must have printed its `OK:` line. If it did not run, the `find` produced nothing and the layout is wrong.

- [ ] **Step 7: Commit**

```bash
git add gui/createApp.sh gui/src/main/assembly/linux \
  .github/scripts/verify-install.sh .github/scripts/verify-launcher.sh
git commit -m "build: assemble the linux archive with a bundled runtime"
```

---

### Task 6: Linux installer

**Files:**
- Modify: `gui/src/main/assembly/install.sh` (rewritten as the Linux-only `md2pdf-install.sh`)
- Create: `.github/scripts/test-install-failures.sh`

**Interfaces:**
- Consumes: the Linux zip layout from Task 5.
- Produces: an installer that takes an optional target directory, defaults to `${XDG_DATA_HOME:-$HOME/.local/share}/MarkdownToPdf`, is non-interactive-safe, and exits non-zero if the launcher is not created.
- Produces: `test-install-failures.sh` — runs on Linux only, used by Task 10's `install-failures` job.

- [ ] **Step 1: Write the failure-path test first**

Create `.github/scripts/test-install-failures.sh`:

```bash
#!/usr/bin/env bash
# Asserts the installer's failure paths fail loudly. Platform-independent logic, so it
# runs on Linux only.
#
#   test-install-failures.sh <path-to-unpacked-zip-root>
set -euo pipefail

SRC="${1:?usage: test-install-failures.sh <unpacked-zip-root>}"
fail() { printf 'FAIL: %s\n' "$*" >&2; exit 1; }
ok()   { printf 'ok: %s\n' "$*"; }

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

# 1. A broken createLauncher.sh must fail the install. This previously printed the error
#    and exited 0, reporting a successful install that had no launcher.
copy="$WORK/broken"
cp -R "$SRC" "$copy"
printf '#!/usr/bin/env bash\nexit 3\n' > "$copy/MarkdownToPdf/createLauncher.sh"
chmod +x "$copy/MarkdownToPdf/createLauncher.sh"
if (cd "$copy" && bash md2pdf-install.sh "$WORK/dest-broken" < /dev/null); then
  fail "installer exited 0 with a failing createLauncher.sh"
fi
ok "launcher failure exits non-zero"

# 2. A non-interactive re-install over an existing install must cancel, not clobber.
good="$WORK/good"
cp -R "$SRC" "$good"
dest="$WORK/dest-good"
(cd "$good" && bash md2pdf-install.sh "$dest" < /dev/null) || fail "first install failed"
marker="$dest/DO-NOT-DELETE"
touch "$marker"
if (cd "$good" && bash md2pdf-install.sh "$dest" < /dev/null); then
  fail "non-interactive re-install exited 0 instead of cancelling"
fi
[ -f "$marker" ] || fail "non-interactive re-install destroyed the existing installation"
ok "non-interactive re-install cancels without clobbering"

# 3. MD2PDF_REPLACE_EXISTING=1 is the explicit opt-in and must go through.
(cd "$good" && MD2PDF_REPLACE_EXISTING=1 bash md2pdf-install.sh "$dest" < /dev/null) \
  || fail "MD2PDF_REPLACE_EXISTING=1 re-install failed"
[ ! -f "$marker" ] || fail "MD2PDF_REPLACE_EXISTING=1 did not actually replace"
ok "MD2PDF_REPLACE_EXISTING=1 replaces"

# 4. Installing a directory onto itself must be refused rather than emptying it. This is
#    what happens when the archive is unzipped straight into the install parent, so the
#    source MarkdownToPdf/ and the destination are literally the same directory.
selfroot="$WORK/self"
mkdir -p "$selfroot"
cp -R "$SRC/." "$selfroot/"
if (cd "$selfroot" && bash md2pdf-install.sh "$selfroot/MarkdownToPdf" < /dev/null); then
  fail "installing a directory onto itself exited 0"
fi
[ -f "$selfroot/MarkdownToPdf/MarkdownToPdf.jar" ] \
  || fail "installing a directory onto itself destroyed it"
ok "self-install refused"

printf '\nPASS: installer failure paths\n'
```

```bash
chmod +x .github/scripts/test-install-failures.sh
```

- [ ] **Step 2: Run it to verify it fails**

```bash
rm -rf /tmp/md2pdf-unpack && mkdir -p /tmp/md2pdf-unpack
unzip -q gui/target/md2pdf-*-linux-x64.zip -d /tmp/md2pdf-unpack
.github/scripts/test-install-failures.sh /tmp/md2pdf-unpack
```
Expected: FAIL on the first case — the current installer still contains the JDK-detection flow and will not behave as asserted.

- [ ] **Step 3: Rewrite the installer**

Replace the whole of `gui/src/main/assembly/install.sh` (461 lines, all of it):

```bash
#!/usr/bin/env bash
# MarkdownToPdf installer for Linux.
#
#   bash md2pdf-install.sh [target-directory]
#
# The archive bundles its own Java runtime, so this script installs no JDK, detects no
# JDK, and records no JDK. If you are about to add version parsing here, don't — that
# code is what this packaging removed.
set -euo pipefail

SCRIPTDIR="$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" > /dev/null 2>&1 && pwd )"
APP_NAME="MarkdownToPdf"
SRC="$SCRIPTDIR/$APP_NAME"

RED=$'\033[0;31m'; GRN=$'\033[0;32m'; YEL=$'\033[0;33m'; NC=$'\033[0m'
info() { printf "${GRN}[INSTALL]${NC} %s\n" "$*"; }
warn() { printf "${YEL}[WARN]${NC}  %s\n" "$*" >&2; }
die()  { printf "${RED}[ERROR]${NC} %s\n" "$*" >&2; exit 1; }

# read at EOF returns 1, which under set -e kills the script — so a prompt in any
# non-TTY context (CI, a pipe, cron) aborts the install rather than defaulting.
NONINTERACTIVE=0
[ -t 0 ] || NONINTERACTIVE=1

DEST="${1:-${XDG_DATA_HOME:-$HOME/.local/share}/$APP_NAME}"

[ -d "$SRC" ] || die "$APP_NAME is not next to this script — run it from the unzipped archive."

# ── Linux host libraries ────────────────────────────────────────────
# jlink cannot bundle these; JavaFX needs GTK 3 and javafx.web needs the WebKit stack.
# Warns rather than aborts: the libraries may be present under names this check does not
# know, and a wrong guess must not block a working install.
check_host_libraries() {
  local missing=()
  for lib in libgtk-3.so.0 libgthread-2.0.so.0 libXtst.so.6; do
    ldconfig -p 2>/dev/null | grep -q "$lib" || missing+=("$lib")
  done
  [ "${#missing[@]}" -eq 0 ] && return 0
  warn "These shared libraries were not found: ${missing[*]}"
  warn "MarkdownToPdf needs GTK 3 and the WebKit dependencies, which no bundled runtime can supply."
  warn "  Debian/Ubuntu: sudo apt-get install libgtk-3-0 libxtst6"
  warn "  Fedora/RHEL:   sudo dnf install gtk3 libXtst"
  warn "  Arch:          sudo pacman -S gtk3 libxtst"
  warn "Continuing — if they are present under different names the app will start fine."
}

# ── existing installation ───────────────────────────────────────────
resolve_dest() {
  if [ -d "$DEST" ] && [ "$(cd "$SRC" && pwd -P)" = "$(cd "$DEST" && pwd -P)" ]; then
    die "$APP_NAME is already installed at $DEST and this script is running from there — nothing to do."
  fi
  [ -d "$DEST" ] || return 0

  if [ "${MD2PDF_REPLACE_EXISTING:-0}" = "1" ]; then
    info "Replacing the existing installation at $DEST"
    rm -rf "$DEST"
    return 0
  fi

  if [ "$NONINTERACTIVE" -eq 1 ]; then
    die "An installation already exists at $DEST. Re-run with MD2PDF_REPLACE_EXISTING=1 to replace it."
  fi

  warn "Existing installation found at: $DEST"
  info "  [r]emove the old one and replace it"
  info "  [k]eep both by renaming the old one to ${DEST}-old"
  info "  [c]ancel"
  read -rp "  Choose (r/k/c): " choice
  case "$choice" in
    r|R) info "Removing $DEST"; rm -rf "$DEST" ;;
    k|K) [ -d "${DEST}-old" ] && die "Backup already exists: ${DEST}-old"
         info "Renaming to ${DEST}-old"; mv "$DEST" "${DEST}-old" ;;
    c|C) die "Installation cancelled — nothing was changed." ;;
    *)   die "Unrecognized choice: $choice" ;;
  esac
}

install_app() {
  info "Installing to $DEST"
  mkdir -p "$DEST"
  cp -R "$SRC/." "$DEST/" || die "Copy failed."

  # Not every unzip tool preserves Unix modes, and this covers more than the launcher:
  # runtime/bin/* and runtime/lib/jspawnhelper are unusable without the execute bit.
  chmod +x "$DEST"/*.sh
  chmod +x "$DEST"/runtime/bin/* 2>/dev/null || true
  [ -f "$DEST/runtime/lib/jspawnhelper" ] && chmod +x "$DEST/runtime/lib/jspawnhelper"

  info "Creating the desktop launcher …"
  [ -x "$DEST/createLauncher.sh" ] \
    || die "createLauncher.sh is missing from $DEST — the archive looks incomplete."
  (cd "$DEST" && bash createLauncher.sh) \
    || die "createLauncher.sh failed — $APP_NAME is installed at $DEST but has no launcher. Retry with 'bash $DEST/createLauncher.sh', or start it with 'bash $DEST/run.sh'."

  echo ""
  info "Installation complete."
  info "Run with:  bash $DEST/run.sh"
}

check_host_libraries
resolve_dest
install_app
```

- [ ] **Step 4: Repackage and run the failure tests**

```bash
mvn -q install -DskipTests
./gui/createApp.sh linux
rm -rf /tmp/md2pdf-unpack && mkdir -p /tmp/md2pdf-unpack
unzip -q gui/target/md2pdf-*-linux-x64.zip -d /tmp/md2pdf-unpack
.github/scripts/test-install-failures.sh /tmp/md2pdf-unpack
```
Expected: four `ok:` lines and `PASS: installer failure paths`.

- [ ] **Step 5: Run a real install and verify it**

```bash
rm -rf "$HOME/.local/share/MarkdownToPdf"
(cd /tmp/md2pdf-unpack && bash md2pdf-install.sh < /dev/null)
.github/scripts/verify-install.sh linux "$HOME/.local/share/MarkdownToPdf" 21.0.12
```
Expected: every `ok:` line, then `PASS: linux install at …`.

- [ ] **Step 6: Launch it once by hand**

```bash
bash "$HOME/.local/share/MarkdownToPdf/run.sh"
```
Expected: the window opens on the bundled runtime, with no JDK involved. Close it.

- [ ] **Step 7: Commit**

```bash
git add gui/src/main/assembly/install.sh .github/scripts/test-install-failures.sh
git commit -m "feat: rewrite the linux installer for the bundled runtime"
```

---

### Task 7: macOS bundle, Info.plist and signing

Must be done on an Apple Silicon Mac. On Apple Silicon the kernel refuses to execute binaries with an absent or invalid signature, so an unsigned `jlink` image inside a `.app` dies on launch with no useful message — signing is not optional even for local builds.

**Files:**
- Modify: `gui/src/main/assembly/mac/Info.plist` (becomes a template)
- Modify: `gui/src/main/assembly/mac/markdownToPdf`
- Delete: `gui/src/main/assembly/mac/run.zsh`
- Create: `gui/src/main/assembly/mac/sign-app.sh`
- Create: `gui/src/main/assembly/mac/entitlements.plist`
- Create: `gui/src/main/assembly/mac/md2pdf-install.zsh`
- Modify: `gui/createApp.sh` (the `macos` case)
- Modify: `pom.xml` (remove the "bump Info.plist too" comment)

**Interfaces:**
- Consumes: `build_runtime`, `stage_app`, `app_version` from Tasks 4-5.
- Produces: `gui/target/md2pdf-<version>-macos-aarch64.zip`.
- Produces: `sign-app.sh <app-bundle>` — signs every Mach-O inside-out, then the bundle.

- [ ] **Step 1: Turn Info.plist into a template**

Replace `gui/src/main/assembly/mac/Info.plist`:

```xml
<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<plist version="1.0">
  <dict>
    <!-- Must match the file in Contents/MacOS exactly. Packaging writes the launcher and
         this key from one variable; a mismatch only appears to work because APFS is
         case-insensitive by default, and fails outright on a case-sensitive volume. -->
    <key>CFBundleExecutable</key>
    <string>@EXECUTABLE@</string>
    <key>CFBundleIdentifier</key>
    <string>se.alipsa.md2pdf</string>
    <key>CFBundleName</key>
    <string>MarkdownToPdf</string>
    <key>CFBundlePackageType</key>
    <string>APPL</string>
    <!-- Free-form, so it is where the full ${revision} and the commit survive. -->
    <key>CFBundleGetInfoString</key>
    <string>MarkdownToPdf @FULL_VERSION@ (@COMMIT@)</string>
    <!-- Apple allows only one to three period-separated integers here. -->
    <key>CFBundleVersion</key>
    <string>@BUNDLE_VERSION@</string>
    <key>CFBundleShortVersionString</key>
    <string>@BUNDLE_VERSION@</string>
    <key>CFBundleIconFile</key>
    <string>md2pdf.icns</string>
    <key>CFBundleIconName</key>
    <string>md2pdf</string>
  </dict>
</plist>
```

Then remove the now-false comment in `pom.xml`:

```
    <!-- Bump here, in gui/pom.xml <md2pdf.version>, and in Info.plist when releasing -->
```
becomes

```
    <!-- Bump here and in gui/pom.xml <md2pdf.version> when releasing -->
```

- [ ] **Step 2: Rewrite the macOS launcher**

Replace `gui/src/main/assembly/mac/markdownToPdf`:

```zsh
#!/usr/bin/env zsh
# Contents/MacOS/markdownToPdf — the app bundle's only entry point.
set -e
DIR="${0:A:h}"          # Contents/MacOS
CONTENTS="${DIR:h}"     # Contents
exec "$CONTENTS/runtime/bin/java" -Xmx8g \
  -Xdock:name=MarkdownToPdf \
  -Xdock:icon="$CONTENTS/Resources/md2pdf.icns" \
  -jar "$CONTENTS/app/MarkdownToPdf.jar"
```

It no longer sources `~/.zshrc`, reads `md2pdf.env`, or shows an `osascript` dialog — there is no JDK to fail to find.

```bash
git rm gui/src/main/assembly/mac/run.zsh
```

- [ ] **Step 3: Write the entitlements plist**

Create `gui/src/main/assembly/mac/entitlements.plist`. Unused today; committed because notarization requires the hardened runtime, and a JVM under hardened runtime does not start without these.

```xml
<?xml version="1.0" encoding="UTF-8"?>
<plist version="1.0">
  <dict>
    <key>com.apple.security.cs.allow-jit</key>
    <true/>
    <key>com.apple.security.cs.allow-unsigned-executable-memory</key>
    <true/>
    <key>com.apple.security.cs.disable-library-validation</key>
    <true/>
  </dict>
</plist>
```

- [ ] **Step 4: Write the signing script**

Create `gui/src/main/assembly/mac/sign-app.sh`:

```bash
#!/usr/bin/env bash
# Signs a MarkdownToPdf.app, inside-out.
#
#   sign-app.sh <path-to-.app>
#
# Apple requires nested code to be signed deepest-first, and "nested code" in a jlink
# image is more than the dylibs: bin/java, bin/keytool, bin/jrunscript and
# lib/jspawnhelper are all Mach-O. jspawnhelper is not in bin/ and is the one most often
# missed. Enumerating rather than listing is deliberate — a hardcoded list stops being
# correct the next time the module set changes, and surfaces as a rejected notarization
# months later.
set -euo pipefail

APP="${1:?usage: sign-app.sh <app-bundle>}"
[ -d "$APP" ] || { echo "no such bundle: $APP" >&2; exit 1; }

IDENTITY="$(security find-identity -v -p codesigning 2>/dev/null \
  | grep -m1 'Developer ID Application' \
  | sed -n 's/.*"\(.*\)".*/\1/p' || true)"

if [ -n "$IDENTITY" ]; then
  echo "Signing with: $IDENTITY"
  SIGN=(codesign --force --timestamp --sign "$IDENTITY")
else
  # Not an error: GitHub does not expose secrets to forked-PR runs, so fork builds take
  # this path correctly. Ad-hoc signatures satisfy the Apple Silicon kernel; they do not
  # satisfy Gatekeeper, which is why the installer still strips the quarantine flag.
  echo "No Developer ID found — ad-hoc signing"
  SIGN=(codesign --force --sign -)
fi

# Deepest first. -r on sort reverses the depth-sorted list so children precede parents.
while IFS= read -r f; do
  case "$(file -b "$f")" in
    *Mach-O*) "${SIGN[@]}" "$f" ;;
  esac
done < <(find "$APP" -type f \( -perm -u+x -o -name '*.dylib' \) \
         | awk -F/ '{print NF"\t"$0}' | sort -rn | cut -f2-)

"${SIGN[@]}" "$APP"
codesign --verify --deep --strict --verbose=2 "$APP"
echo "Signed and verified: $APP"
```

```bash
chmod +x gui/src/main/assembly/mac/sign-app.sh
```

Note the two branches are not one command with a swapped argument: ad-hoc signing may use `--deep`, Developer ID signing must not, because Apple discourages `--deep` for distribution. This script signs individually in both cases, which is correct for both.

- [ ] **Step 5: Write the macOS installer**

Create `gui/src/main/assembly/mac/md2pdf-install.zsh`:

```zsh
#!/usr/bin/env zsh
# MarkdownToPdf installer for macOS.
#
#   zsh md2pdf-install.zsh [target-directory]
set -euo pipefail

SCRIPTDIR="${0:A:h}"
APP_NAME="MarkdownToPdf.app"
SRC="$SCRIPTDIR/$APP_NAME"

RED=$'\033[0;31m'; GRN=$'\033[0;32m'; YEL=$'\033[0;33m'; NC=$'\033[0m'
info() { printf "${GRN}[INSTALL]${NC} %s\n" "$*"; }
warn() { printf "${YEL}[WARN]${NC}  %s\n" "$*" >&2; }
die()  { printf "${RED}[ERROR]${NC} %s\n" "$*" >&2; exit 1; }

NONINTERACTIVE=0
[ -t 0 ] || NONINTERACTIVE=1

DEST="${1:-$HOME/Applications/$APP_NAME}"
[ -d "$SRC" ] || die "$APP_NAME is not next to this script — run it from the unzipped archive."

if [[ -d "$DEST" && "$(cd "$SRC" && pwd -P)" == "$(cd "$DEST" && pwd -P)" ]]; then
  die "$APP_NAME is already installed at $DEST and this script is running from there — nothing to do."
fi

if [[ -d "$DEST" ]]; then
  if [[ "${MD2PDF_REPLACE_EXISTING:-0}" == "1" ]]; then
    info "Replacing the existing installation at $DEST"
    rm -rf "$DEST"
  elif (( NONINTERACTIVE )); then
    die "An installation already exists at $DEST. Re-run with MD2PDF_REPLACE_EXISTING=1 to replace it."
  else
    warn "Existing installation found at: $DEST"
    info "  [r]emove and replace   [k]eep both   [c]ancel"
    read -r "choice?  Choose (r/k/c): "
    case "$choice" in
      r|R) rm -rf "$DEST" ;;
      k|K) [[ -d "${DEST}-old" ]] && die "Backup already exists: ${DEST}-old"
           mv "$DEST" "${DEST}-old" ;;
      c|C) die "Installation cancelled — nothing was changed." ;;
      *)   die "Unrecognized choice: $choice" ;;
    esac
  fi
fi

info "Installing to $DEST"
mkdir -p "${DEST:h}"
cp -R "$SRC" "$DEST" || die "Copy failed."

# Not every unzip tool preserves Unix modes.
chmod +x "$DEST/Contents/MacOS/"*
chmod +x "$DEST/Contents/runtime/bin/"* 2>/dev/null || true
[[ -f "$DEST/Contents/runtime/lib/jspawnhelper" ]] && chmod +x "$DEST/Contents/runtime/lib/jspawnhelper"

# Signing is not notarization: without this the user has to right-click → Open the first
# time, because Gatekeeper flags anything a browser downloaded.
info "Removing the quarantine attribute …"
xattr -dr com.apple.quarantine "$DEST" 2>/dev/null || true

echo ""
info "Installation complete."
info "Open $APP_NAME from Finder or Spotlight."
if (( ! NONINTERACTIVE )); then
  read -r "reply?  Open now? [y/N] "
  case "$reply" in y|Y) open "$DEST" ;; esac
fi
```

- [ ] **Step 6: Add the macos case to createApp.sh**

In `gui/createApp.sh`, add to the `case "$PLATFORM" in` block:

```bash
  macos)
    EXECUTABLE="markdownToPdf"
    BUNDLE_VERSION="${VERSION%%-*}"
    printf '%s' "$BUNDLE_VERSION" | grep -Eq '^[0-9]+(\.[0-9]+){0,2}$' \
      || die "BUNDLE_VERSION '$BUNDLE_VERSION' is not one to three period-separated integers"
    COMMIT="$(git -C "$DIR" rev-parse --short HEAD 2>/dev/null || echo unknown)"

    CONTENTS="$STAGE/MarkdownToPdf.app/Contents"
    mkdir -p "$CONTENTS/MacOS" "$CONTENTS/Resources"
    stage_app "$CONTENTS/app"
    build_runtime "$CONTENTS/runtime"

    # The executable name and CFBundleExecutable come from one variable so they cannot
    # diverge again.
    cp "$DIR/src/main/assembly/mac/$EXECUTABLE" "$CONTENTS/MacOS/$EXECUTABLE"
    chmod +x "$CONTENTS/MacOS/$EXECUTABLE"
    cp "$DIR/src/main/assembly/mac/md2pdf.icns" "$CONTENTS/Resources/"
    sed -e "s|@EXECUTABLE@|$EXECUTABLE|g" \
        -e "s|@BUNDLE_VERSION@|$BUNDLE_VERSION|g" \
        -e "s|@FULL_VERSION@|$VERSION|g" \
        -e "s|@COMMIT@|$COMMIT|g" \
        "$DIR/src/main/assembly/mac/Info.plist" > "$CONTENTS/Info.plist"

    cp "$DIR/src/main/assembly/mac/md2pdf-install.zsh" "$STAGE/"
    chmod +x "$STAGE/md2pdf-install.zsh"

    bash "$DIR/src/main/assembly/mac/sign-app.sh" "$STAGE/MarkdownToPdf.app"
    ;;
```

- [ ] **Step 7: Build, install and verify on a Mac**

```bash
mvn -q install -DskipTests
./gui/createApp.sh macos
rm -rf /tmp/md2pdf-unpack && mkdir -p /tmp/md2pdf-unpack
unzip -q gui/target/md2pdf-*-macos-aarch64.zip -d /tmp/md2pdf-unpack
rm -rf "$HOME/Applications/MarkdownToPdf.app"
(cd /tmp/md2pdf-unpack && zsh md2pdf-install.zsh < /dev/null)
.github/scripts/verify-install.sh macos "$HOME/Applications/MarkdownToPdf.app" 21.0.12
```
Expected: `ok: launcher started …` then `PASS: macos install at …`, including the `codesign --verify --deep --strict` assertion.

- [ ] **Step 8: Double-click it in Finder**

Open `~/Applications` in Finder and double-click MarkdownToPdf. Expected: it launches, with the correct Dock icon and name. `verify-launcher.sh` already ran `Contents/MacOS/<CFBundleExecutable>` directly, so what is left for this step is everything Launch Services adds on top: that Finder resolves the bundle at all, that Gatekeeper admits it, and that the icon and Dock name are right. None of those are reachable from a shell.

- [ ] **Step 9: Commit**

```bash
git add gui/createApp.sh gui/src/main/assembly/mac pom.xml
git rm gui/src/main/assembly/mac/run.zsh
git commit -m "feat: build a signed macOS bundle with a bundled runtime"
```

---

### Task 8: Windows archive and installer

**Files:**
- Modify: `gui/src/main/assembly/win/run.cmd`
- Modify: `gui/src/main/assembly/win/createShortcut.ps1`
- Create: `gui/src/main/assembly/win/md2pdf-install.cmd`
- Modify: `gui/createApp.sh` (the `windows` case)

**Interfaces:**
- Produces: `gui/target/md2pdf-<version>-windows-x64.zip`.

- [ ] **Step 1: Rewrite run.cmd**

Replace `gui/src/main/assembly/win/run.cmd`:

```cmd
@echo off
setlocal
rem The quotes around the whole assignment are required, not style: without them cmd.exe
rem parses &, ^, ( and ) in the install path as syntax. C:\Users\A & B\... is a legal
rem Windows path and would break the launcher.
set "DIR=%~dp0"
start "" "%DIR%runtime\bin\javaw.exe" -Xmx8g -jar "%DIR%MarkdownToPdf.jar"
```

`%~dp0` already ends in a backslash, so `%DIR%runtime\…` is correct and `%DIR%\runtime\…` would not be.

- [ ] **Step 2: Rewrite createShortcut.ps1**

Replace `gui/src/main/assembly/win/createShortcut.ps1`:

```powershell
# Creates the desktop shortcut for MarkdownToPdf.
# Targets runtime\bin\javaw.exe directly rather than cmd.exe /c run.cmd — now possible
# because the runtime is at a known path inside the install directory, and it removes
# the console window that the cmd.exe target flashed up on every launch.
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition

if (Test-Path -Path "$env:USERPROFILE\Desktop") {
    $desktop = "$env:USERPROFILE\Desktop"
} elseif (Test-Path -Path "$env:USERPROFILE\OneDrive\Desktop") {
    $desktop = "$env:USERPROFILE\OneDrive\Desktop"
} else {
    Write-Error "Could not locate the Desktop folder; shortcut was not created."
    exit 1
}

$WScriptObj = New-Object -ComObject WScript.Shell
$shortcut = $WScriptObj.CreateShortcut("$desktop\MarkdownToPdf.lnk")
$shortcut.TargetPath = "$scriptDir\runtime\bin\javaw.exe"
$shortcut.Arguments = "-Xmx8g -jar `"$scriptDir\MarkdownToPdf.jar`""
$shortcut.WorkingDirectory = "$scriptDir"
$shortcut.IconLocation = "$scriptDir\MarkdownToPdf-rounded.ico, 0"
$shortcut.Save()
```

- [ ] **Step 3: Write the Windows installer**

Create `gui/src/main/assembly/win/md2pdf-install.cmd`. Native `cmd` because PowerShell is often restricted or disabled on corporate Windows machines, and an installer that cannot run is worse than an ugly one.

```cmd
@echo off
setlocal EnableDelayedExpansion
rem MarkdownToPdf installer for Windows.
rem
rem   md2pdf-install.cmd [target-directory]

set "SCRIPTDIR=%~dp0"
set "APP_NAME=MarkdownToPdf"
set "SRC=%SCRIPTDIR%%APP_NAME%"

if "%~1"=="" (
  set "DEST=%LOCALAPPDATA%\Programs\%APP_NAME%"
) else (
  set "DEST=%~1"
)

if not exist "%SRC%\" (
  echo [ERROR] %APP_NAME% is not next to this script - run it from the unzipped archive.
  exit /b 1
)

rem Refuse to install a directory onto itself.
if /I "%SRC%"=="%DEST%" (
  echo [ERROR] Source and destination are the same directory - nothing to do.
  exit /b 1
)

if exist "%DEST%\" (
  if "%MD2PDF_REPLACE_EXISTING%"=="1" (
    echo [INSTALL] Replacing the existing installation at %DEST%
    rmdir /S /Q "%DEST%"
  ) else (
    echo [ERROR] An installation already exists at %DEST%.
    echo         Set MD2PDF_REPLACE_EXISTING=1 to replace it.
    exit /b 1
  )
)

echo [INSTALL] Installing to %DEST%
mkdir "%DEST%" 2>nul
xcopy /E /I /Q /Y "%SRC%" "%DEST%" >nul
if errorlevel 1 (
  echo [ERROR] Copy failed.
  exit /b 1
)

rem ── desktop shortcut ────────────────────────────────────────────
rem cmd cannot create a .lnk, so PowerShell does it. Success is decided by testing for
rem the file, never by the exit code: -ExecutionPolicy Bypass is silently ignored when
rem policy comes from Group Policy, and under AppLocker Constrained Language Mode
rem PowerShell runs the script but fails at New-Object -ComObject.
set "DESKTOP=%USERPROFILE%\Desktop"
if not exist "%DESKTOP%\" set "DESKTOP=%USERPROFILE%\OneDrive\Desktop"
set "LNK=%DESKTOP%\%APP_NAME%.lnk"
set "STUB=%DESKTOP%\%APP_NAME%.cmd"

rem Delete first, so the test below means "this run created a shortcut" rather than
rem "a file with this name exists" - a shortcut left by an earlier install would point
rem at a path that has since moved and would satisfy the test while being broken.
if exist "%LNK%" del /Q "%LNK%"
if exist "%STUB%" del /Q "%STUB%"

echo [INSTALL] Creating the desktop shortcut ...
powershell -NoProfile -ExecutionPolicy Bypass -File "%DEST%\createShortcut.ps1" >nul 2>&1

if exist "%LNK%" (
  echo [INSTALL] Shortcut created.
) else (
  echo [WARN]  Could not create a .lnk shortcut - writing a launcher script instead.
  rem Must be a generated stub, not a copy of run.cmd: run.cmd finds everything through
  rem %%~dp0, its own directory, so a copy on the Desktop resolves that to the Desktop
  rem and finds neither runtime nor jar.
  > "%STUB%" echo @echo off
  >> "%STUB%" echo call "%DEST%\run.cmd"
  if exist "%STUB%" (
    echo [INSTALL] Launcher script created at %STUB%
  ) else (
    echo [ERROR] Could not create a shortcut or a launcher script.
    exit /b 1
  )
)

echo.
echo [INSTALL] Installation complete.
echo [INSTALL] Run with: %DEST%\run.cmd
exit /b 0
```

- [ ] **Step 4: Add the windows case to createApp.sh**

```bash
  windows)
    APPDIR="$STAGE/MarkdownToPdf"
    mkdir -p "$APPDIR"
    stage_app "$APPDIR"
    build_runtime "$APPDIR/runtime"
    cp "$DIR/src/main/assembly/win/run.cmd"            "$APPDIR/"
    cp "$DIR/src/main/assembly/win/createShortcut.ps1" "$APPDIR/"
    cp "$DIR/src/main/resources/MarkdownToPdf-rounded.ico" "$APPDIR/"
    cp "$DIR/src/main/assembly/win/md2pdf-install.cmd" "$STAGE/"
    ;;
```

- [ ] **Step 5: Build and install on Windows**

In Git Bash. `createApp.sh` needs `unzip` and either `zip` or `7z`, none of which Git Bash ships by default — `require_tools` will tell you which is missing before it builds anything.

```bash
export JAVA_HOME="/c/path/to/liberica-full-21.0.12"
mvn -q install -DskipTests
./gui/createApp.sh windows
```

Then in `cmd.exe`:

```cmd
cd /d %TEMP%
rmdir /S /Q md2pdf-unpack 2>nul
powershell -Command "Expand-Archive -Force 'C:\path\to\repo\gui\target\md2pdf-0.1.1-SNAPSHOT-windows-x64.zip' '%TEMP%\md2pdf-unpack'"
cd md2pdf-unpack
set MD2PDF_REPLACE_EXISTING=1
md2pdf-install.cmd
```
Expected: exit 0, a `MarkdownToPdf.lnk` on the Desktop, and the app starts from it with **no console window**.

- [ ] **Step 6: Verify the install**

Back in Git Bash:

```bash
.github/scripts/verify-install.sh windows "$(cygpath -u "$LOCALAPPDATA")/Programs/MarkdownToPdf" 21.0.12
```
Expected: `ok: launcher started …` then `PASS: windows install at …`. If `run.cmd` is reported as leaving no `javaw.exe`, the launcher is broken — read its `%DIR%` construction before suspecting the runner.

- [ ] **Step 7: Test the stub fallback**

Force the PowerShell path to fail and confirm the fallback works, since on the machines that need it we will not get a second chance:

```cmd
rmdir /S /Q "%LOCALAPPDATA%\Programs\MarkdownToPdf"
del "%TEMP%\md2pdf-unpack\MarkdownToPdf\createShortcut.ps1"
cd /d %TEMP%\md2pdf-unpack
md2pdf-install.cmd
```
Expected: the `[WARN]` line, a `MarkdownToPdf.cmd` stub on the Desktop, exit 0 — and double-clicking the stub starts the app.

- [ ] **Step 8: Commit**

```bash
git add gui/createApp.sh gui/src/main/assembly/win
git commit -m "feat: build the windows archive with a bundled runtime and cmd installer"
```

---

### Task 9: The -no-jdk archive

For users who already have a JavaFX-bundled JDK and do not want a 100 MB download. It contains **no launchers and no installer** — adding PATH-based launchers would reintroduce the JDK-detection logic this work deletes, and this audience by definition already has a working JDK.

**Files:**
- Create: `gui/src/main/assembly/no-jdk/README.txt`
- Modify: `gui/createApp.sh` (a `no-jdk` platform argument)
- Create: `.github/scripts/verify-no-jdk.sh`

**Interfaces:**
- Produces: `gui/target/md2pdf-<version>-no-jdk.zip` containing `MarkdownToPdf.jar`, `lib/`, `README.txt`.
- Produces: `verify-no-jdk.sh <unpacked-dir> <java-binary>`.

- [ ] **Step 1: Write the verification script**

Create `.github/scripts/verify-no-jdk.sh`:

```bash
#!/usr/bin/env bash
# Asserts the -no-jdk archive is what it claims: the app and its dependencies, no runtime,
# runnable on a JavaFX-bundled JDK that the user supplies.
#
#   verify-no-jdk.sh <unpacked-dir> <java-binary>
set -euo pipefail

DIR="${1:?usage: verify-no-jdk.sh <unpacked-dir> <java>}"
JAVA="${2:?usage: verify-no-jdk.sh <unpacked-dir> <java>}"
SCRIPTS="$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" > /dev/null 2>&1 && pwd )"
fail() { printf 'FAIL: %s\n' "$*" >&2; exit 1; }

# Both arguments are likely to arrive as native Windows paths on Git Bash — $JAVA_HOME as
# set by actions/setup-java is C:\hostedtoolcache\..., and `unzip -d` is commonly given the
# same. dirname, cd and the file tests below all need POSIX form.
unixpath() {
  if command -v cygpath > /dev/null 2>&1; then cygpath -u "$1"; else printf '%s' "$1"; fi
}
DIR="$(unixpath "$DIR")"
JAVA="$(unixpath "$JAVA")"
[ -x "$JAVA" ] || fail "not an executable java binary: $JAVA"

[ -f "$DIR/MarkdownToPdf.jar" ] || fail "MarkdownToPdf.jar missing"
[ -f "$DIR/README.txt" ]        || fail "README.txt missing"
# An assembly slip that added one would produce a 100 MB "no-jdk" download.
[ ! -d "$DIR/runtime" ]         || fail "runtime/ is present — this is not a no-jdk archive"
for f in run.sh run.cmd run.zsh md2pdf-install.sh md2pdf-install.cmd md2pdf-install.zsh; do
  [ ! -e "$DIR/$f" ] || fail "$f is present — the no-jdk archive ships no launchers or installers"
done

"$SCRIPTS/check-lib-classpath.sh" "$DIR/MarkdownToPdf.jar" "$DIR/lib"

# Both smoke tests, on the user-supplied JDK. The engine smoke touches no JavaFX at all,
# so on its own it would pass on a plain OpenJDK and prove nothing about what this
# archive actually needs. The toolkit smoke is what demonstrates that this archive plus a
# JavaFX-bundled JDK is a working installation — and, with check-lib-classpath.sh having
# just asserted there is no javafx-* jar in lib/, that the JavaFX modules came from the
# JDK. That is the module resolution the archive depends on.
SMOKE="$(mktemp -d)"
trap 'rm -rf "$SMOKE"' EXIT
# Compile with the JDK that is about to run it, derived from the java binary — not with
# whatever JAVA_HOME happens to hold. This script's whole subject is "does the archive work
# on the JDK the user supplied", so compiling against a different one would test something
# nobody asked about.
JDK_HOME="$( cd -- "$( dirname -- "$JAVA" )/.." > /dev/null 2>&1 && pwd )"
CP="$("$SCRIPTS/compile-smoke.sh" "$DIR/MarkdownToPdf.jar" "$SMOKE" "$JDK_HOME")"

"$JAVA" -cp "$CP" EngineSmoke || fail "engine smoke failed"
if [ "$(uname -s)" = "Linux" ]; then
  xvfb-run -a "$JAVA" -cp "$CP" ToolkitSmoke || fail "toolkit smoke failed"
else
  "$JAVA" -cp "$CP" ToolkitSmoke || fail "toolkit smoke failed"
fi

printf '\nPASS: no-jdk archive at %s\n' "$DIR"
```

```bash
chmod +x .github/scripts/verify-no-jdk.sh
```

- [ ] **Step 2: Run it to verify it fails**

```bash
.github/scripts/verify-no-jdk.sh /tmp/md2pdf-nojdk "$JAVA_HOME/bin/java"
```
Expected: FAIL — the directory does not exist.

- [ ] **Step 3: Write README.txt**

Create `gui/src/main/assembly/no-jdk/README.txt`:

```
MarkdownToPdf — no-JDK archive
==============================

This archive contains the application and its dependencies, but no Java runtime.

Requirements
------------
A JavaFX-bundled JDK 21 or later. A plain OpenJDK will NOT work: it has no JavaFX
modules, and this archive does not supply them. BellSoft Liberica "Full" and Azul
Zulu "FX" builds both qualify.

    https://bell-sw.com/pages/downloads/?version=java-21&package=jdk-full

Running
-------
    java -jar MarkdownToPdf.jar

The lib/ directory next to the jar is resolved through the jar's manifest, so it must
stay where it is.

If you would rather not manage a JDK at all, the platform archives on the release page
bundle their own runtime and need nothing installed:

    md2pdf-<version>-linux-x64.zip
    md2pdf-<version>-macos-aarch64.zip
    md2pdf-<version>-windows-x64.zip
```

- [ ] **Step 4: Add the no-jdk case to createApp.sh**

Add `no-jdk` to the platform `case` at the top of the script:

```bash
  no-jdk)  ARCH_LABEL="no-jdk"           ; EXPECTED_ARCH="" ;;
```

Make `require_arch` a no-op for it — the archive is platform-independent, so an architecture assertion would be meaningless:

```bash
require_arch() {
  [ -n "$EXPECTED_ARCH" ] || return 0
  ...
}
```

And add the packaging case:

```bash
  no-jdk)
    stage_app "$STAGE"
    cp "$DIR/src/main/assembly/no-jdk/README.txt" "$STAGE/"
    ;;
```

- [ ] **Step 5: Build and verify**

```bash
mvn -q install -DskipTests
./gui/createApp.sh no-jdk
rm -rf /tmp/md2pdf-nojdk && mkdir -p /tmp/md2pdf-nojdk
unzip -q gui/target/md2pdf-*-no-jdk.zip -d /tmp/md2pdf-nojdk
du -h gui/target/md2pdf-*-no-jdk.zip
.github/scripts/verify-no-jdk.sh /tmp/md2pdf-nojdk "$JAVA_HOME/bin/java"
```
Expected: roughly 15 MB, then `PASS: no-jdk archive at …`.

- [ ] **Step 6: Commit**

```bash
git add gui/createApp.sh gui/src/main/assembly/no-jdk .github/scripts/verify-no-jdk.sh
git commit -m "feat: add the runtime-free no-jdk archive"
```

---

### Task 10: CI workflow

**Files:**
- Create: `.github/versions.env`
- Create: `.github/workflows/ci.yml`

**Interfaces:**
- Consumes: every script from Tasks 2, 3, 5, 6 and 9; `createApp.sh` from Tasks 4-9.
- Produces: five uploaded artifacts per run, named exactly as the file basenames — consumed by `release.sh` in Task 11.

- [ ] **Step 1: Pin the JDK version**

Create `.github/versions.env`:

```
LIBERICA_VERSION=21.0.12
```

One `KEY=value` line, no export, no comments — it is appended verbatim to `$GITHUB_ENV`.

- [ ] **Step 2: Write the workflow**

Create `.github/workflows/ci.yml`:

```yaml
name: CI

on:
  push:
    branches: [main]
  pull_request:

jobs:
  verify:
    runs-on: ubuntu-22.04
    steps:
      - uses: actions/checkout@v4
      # Shell-sourcing a file does not populate a later action's inputs, so the value has
      # to go through $GITHUB_ENV to reach the with: block below.
      - run: cat .github/versions.env >> "$GITHUB_ENV"
      - uses: actions/setup-java@v5
        with:
          java-version: ${{ env.LIBERICA_VERSION }}
          distribution: liberica
          java-package: jdk+fx
          cache: maven
      - run: mvn verify

  lint-scripts:
    runs-on: ubuntu-22.04
    steps:
      - uses: actions/checkout@v4
      - run: sudo apt-get update && sudo apt-get install -y shellcheck zsh
      - name: shellcheck the bash scripts
        run: |
          shellcheck \
            release.sh install.sh \
            gui/createApp.sh gui/build.sh \
            gui/src/main/assembly/install.sh \
            gui/src/main/assembly/linux/*.sh \
            gui/src/main/assembly/mac/sign-app.sh \
            .github/scripts/*.sh
      # shellcheck refuses zsh and is not a zsh linter, so these get a syntax check only.
      - name: syntax-check the zsh scripts
        run: |
          zsh -n gui/src/main/assembly/mac/md2pdf-install.zsh
          zsh -n gui/src/main/assembly/mac/markdownToPdf
          zsh -n gui/src/main/assembly/mac/mkicns.zsh
      # md2pdf-install.cmd has no linter; the build job running it is its only safety net.

  build:
    strategy:
      fail-fast: false
      matrix:
        include:
          # Pinned versioned labels, never -latest: macos-latest is a moving alias whose
          # architecture GitHub has already changed once, and a silent migration would
          # produce an x64 runtime under an aarch64 filename.
          - os: ubuntu-22.04
            platform: linux
            label: linux-x64
          - os: macos-14
            platform: macos
            label: macos-aarch64
          - os: windows-2022
            platform: windows
            label: windows-x64
    runs-on: ${{ matrix.os }}
    defaults:
      run:
        shell: bash
    steps:
      - uses: actions/checkout@v4
      - run: cat .github/versions.env >> "$GITHUB_ENV"
      - uses: actions/setup-java@v5
        with:
          java-version: ${{ env.LIBERICA_VERSION }}
          distribution: liberica
          java-package: jdk+fx
          cache: maven

      - name: Install Xvfb
        if: matrix.platform == 'linux'
        run: sudo apt-get update && sudo apt-get install -y xvfb

      - name: Read the project version
        run: echo "APP_VERSION=$(mvn -q org.apache.maven.plugins:maven-help-plugin:3.5.1:evaluate -Dexpression=revision -DforceStdout)" >> "$GITHUB_ENV"

      - run: mvn install -DskipTests

      - name: Package
        run: ./gui/createApp.sh ${{ matrix.platform }}

      - uses: actions/upload-artifact@v4
        with:
          name: md2pdf-${{ env.APP_VERSION }}-${{ matrix.label }}
          path: gui/target/md2pdf-${{ env.APP_VERSION }}-${{ matrix.label }}.zip
          if-no-files-found: error

      - name: Unpack and install
        run: |
          set -euo pipefail
          rm -rf "$RUNNER_TEMP/unpack" && mkdir -p "$RUNNER_TEMP/unpack"
          unzip -q "gui/target/md2pdf-$APP_VERSION-${{ matrix.label }}.zip" -d "$RUNNER_TEMP/unpack"
          cd "$RUNNER_TEMP/unpack"
          case "${{ matrix.platform }}" in
            linux)   bash md2pdf-install.sh < /dev/null ;;
            macos)   zsh md2pdf-install.zsh < /dev/null ;;
            windows) MD2PDF_REPLACE_EXISTING=1 cmd //c md2pdf-install.cmd ;;
          esac

      - name: Verify the install
        run: |
          set -euo pipefail
          case "${{ matrix.platform }}" in
            linux)   dest="$HOME/.local/share/MarkdownToPdf" ;;
            macos)   dest="$HOME/Applications/MarkdownToPdf.app" ;;
            # $LOCALAPPDATA is a native path (C:\Users\...); verify-install.sh converts it,
            # but convert here too so the value is usable if anything else touches it.
            windows) dest="$(cygpath -u "$LOCALAPPDATA")/Programs/MarkdownToPdf" ;;
          esac
          fx="$(mvn -q -pl gui org.apache.maven.plugins:maven-help-plugin:3.5.1:evaluate -Dexpression=javafx.version -DforceStdout)"
          .github/scripts/verify-install.sh ${{ matrix.platform }} "$dest" "$fx"

      # ── Linux-only extra artifacts ──────────────────────────────
      - name: Build the no-jdk archive
        if: matrix.platform == 'linux'
        run: ./gui/createApp.sh no-jdk

      - name: Verify the no-jdk archive
        if: matrix.platform == 'linux'
        run: |
          set -euo pipefail
          rm -rf "$RUNNER_TEMP/nojdk" && mkdir -p "$RUNNER_TEMP/nojdk"
          unzip -q "gui/target/md2pdf-$APP_VERSION-no-jdk.zip" -d "$RUNNER_TEMP/nojdk"
          .github/scripts/verify-no-jdk.sh "$RUNNER_TEMP/nojdk" "$JAVA_HOME/bin/java"

      - uses: actions/upload-artifact@v4
        if: matrix.platform == 'linux'
        with:
          name: md2pdf-${{ env.APP_VERSION }}-no-jdk
          path: gui/target/md2pdf-${{ env.APP_VERSION }}-no-jdk.zip
          if-no-files-found: error

      - name: Build the javadoc jar
        if: matrix.platform == 'linux'
        run: mvn -pl lib javadoc:jar

      - uses: actions/upload-artifact@v4
        if: matrix.platform == 'linux'
        with:
          name: md2pdf-${{ env.APP_VERSION }}-javadoc
          path: lib/target/md2pdf-${{ env.APP_VERSION }}-javadoc.jar
          if-no-files-found: error

  install-failures:
    runs-on: ubuntu-22.04
    needs: build
    steps:
      - uses: actions/checkout@v4
      - run: cat .github/versions.env >> "$GITHUB_ENV"
      - uses: actions/setup-java@v5
        with:
          java-version: ${{ env.LIBERICA_VERSION }}
          distribution: liberica
          java-package: jdk+fx
      - uses: actions/download-artifact@v4
        with:
          pattern: md2pdf-*-linux-x64
          path: ${{ runner.temp }}/dl
          merge-multiple: true
      - name: Assert the failure paths fail loudly
        run: |
          set -euo pipefail
          rm -rf "$RUNNER_TEMP/unpack" && mkdir -p "$RUNNER_TEMP/unpack"
          unzip -q "$RUNNER_TEMP"/dl/*.zip -d "$RUNNER_TEMP/unpack"
          .github/scripts/test-install-failures.sh "$RUNNER_TEMP/unpack"
```

- [ ] **Step 3: Push the branch and watch the run**

```bash
git add .github/versions.env .github/workflows/ci.yml
git commit -m "ci: build, install and verify on all three platforms"
git push -u origin HEAD
gh run watch
```
Expected: all six jobs green.

The likely first-run failures, in order of probability:

- **`macos-14` architecture assertion fails.** Check `uname -m` in the job log; if GitHub changed the image, pin a different label rather than loosening the assertion.
- **Toolkit smoke flaky on Windows.** If it proves unstable across several runs, restrict the toolkit smoke to Linux under Xvfb rather than deleting it — a broken `javafx.web` native library is exactly the failure this change could introduce.
- **`setup-java` cannot resolve `21.0.12`.** Check the available Liberica versions and update `.github/versions.env`; do not fall back to a bare major, which defeats the pinning.

- [ ] **Step 4: Deliberately break something and confirm CI catches it**

This is the acceptance test for whether the CI work was worth doing. On a throwaway commit, remove `jdk.crypto.ec` from `MODULES` in `createApp.sh` and push.

Expected: the `build` job fails. Revert the throwaway commit.

- [ ] **Step 5: Commit any fixes**

```bash
git add -A
git commit -m "ci: fix the issues surfaced by the first run"
```

---

### Task 11: release.sh

**Files:**
- Modify: `release.sh` (rewritten)
- Modify: `gui/pom.xml` (the `skipPublishing` guard)

**Interfaces:**
- Consumes: the five CI artifacts from Task 10, by name.

- [ ] **Step 1: Guard the GUI against Maven Central publication**

The `release` profile is on the aggregator parent and `gui` is in `<modules>`, so a bare `mvn -Prelease deploy` at the root hands the GUI application to the central-publishing plugin. The command in `release.sh` scopes it to `-pl lib -am`; this makes that unbypassable by hand.

In `gui/pom.xml`, add to `<build><plugins>`:

```xml
      <plugin>
        <groupId>org.sonatype.central</groupId>
        <artifactId>central-publishing-maven-plugin</artifactId>
        <configuration>
          <!-- The GUI is an application, not a library. It has no place on Maven Central,
               and an artifact published there cannot be withdrawn. -->
          <skipPublishing>true</skipPublishing>
        </configuration>
      </plugin>
```

- [ ] **Step 2: Rewrite release.sh**

```bash
#!/usr/bin/env bash
# Drives a MarkdownToPdf release.
#
#   ./release.sh [--skip-deploy]
#
# Builds nothing. Every release asset comes from the CI run for HEAD, so what ships is
# byte-identical to what was tested. Runs on Linux or macOS.
set -euo pipefail

BASEDIR="$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" > /dev/null 2>&1 && pwd )"
cd "$BASEDIR"

SKIP_DEPLOY=0
[ "${1:-}" = "--skip-deploy" ] && SKIP_DEPLOY=1

die() { printf 'ERROR: %s\n' "$*" >&2; exit 1; }
step() { printf '\n=== %s\n' "$*"; }

# ── 1. preconditions ────────────────────────────────────────────────
step "Preconditions"

# sha256sum is GNU coreutils and absent on macOS, which ships shasum. Resolved here
# rather than at the point of use so an unusable host fails before anything is
# downloaded — and long before the irreversible step.
if command -v sha256sum > /dev/null; then
  sha256() { sha256sum "$@"; }
elif command -v shasum > /dev/null; then
  sha256() { shasum -a 256 "$@"; }
else
  die "need sha256sum or shasum"
fi

command -v gh  > /dev/null || die "gh is not installed"
command -v mvn > /dev/null || die "mvn is not installed"
gh auth status > /dev/null 2>&1 || die "gh is not authenticated"

[ -z "$(git status --porcelain)" ] || die "working tree is not clean"
[ "$(git rev-parse --abbrev-ref HEAD)" = "main" ] || die "not on main"

VERSION="$(mvn -q org.apache.maven.plugins:maven-help-plugin:3.5.1:evaluate -Dexpression=revision -DforceStdout)"
case "$VERSION" in *-SNAPSHOT) die "refusing to release a snapshot version: $VERSION" ;; esac
TAG="v$VERSION"
echo "Releasing $VERSION"

git fetch --tags --quiet
git rev-parse -q --verify "refs/tags/$TAG" > /dev/null && die "tag $TAG already exists locally"
git ls-remote --exit-code --tags origin "$TAG" > /dev/null 2>&1 && die "tag $TAG already exists on the remote"
git push --dry-run --quiet origin HEAD || die "git push would fail"

# The POM, not the directory: a directory listing can 200 on a partially-populated or
# stale path, and only the POM's presence means the version is actually published.
CENTRAL="https://repo1.maven.org/maven2/se/alipsa/md2pdf/$VERSION/md2pdf-$VERSION.pom"
if curl -sfI "$CENTRAL" > /dev/null; then
  [ "$SKIP_DEPLOY" -eq 1 ] \
    || die "$VERSION is already on Maven Central. Maven Central cannot be overwritten; re-run with --skip-deploy to finish the rest of the release."
fi

# ── 2. wait for CI ──────────────────────────────────────────────────
step "Waiting for CI"
SHA="$(git rev-parse HEAD)"
RUN_ID="$(gh run list --commit "$SHA" --workflow ci.yml --limit 1 --json databaseId --jq '.[0].databaseId')"
[ -n "$RUN_ID" ] || die "no CI run for $SHA — push the commit and wait for CI to start"
# The whole run, not just the build jobs: verify, lint-scripts and install-failures are
# separate gates, and releasing past a failing test defeats the point of running it.
gh run watch "$RUN_ID" --exit-status || die "CI run $RUN_ID did not succeed"

# ── 3. download the assets ──────────────────────────────────────────
step "Downloading release assets"
STAGING="$BASEDIR/target/release-$VERSION"
# Emptied, not reused: the --skip-deploy recovery re-runs this step over a directory a
# previous attempt already populated, and a stale file here would ship unhashed under a
# SHA256SUMS that appears to account for it.
rm -rf "$STAGING"
mkdir -p "$STAGING"

ASSETS=(
  "md2pdf-$VERSION-linux-x64.zip"
  "md2pdf-$VERSION-macos-aarch64.zip"
  "md2pdf-$VERSION-windows-x64.zip"
  "md2pdf-$VERSION-no-jdk.zip"
  "md2pdf-$VERSION-javadoc.jar"
)

# One call per artifact. gh run download extracts multiple artifacts into separate
# subdirectories and only flattens when a single artifact is named — and the artifact
# name is the file's basename, so this loop is just the list above.
for asset in "${ASSETS[@]}"; do
  name="${asset%.*}"
  echo "  $asset"
  gh run download "$RUN_ID" -n "$name" -D "$STAGING" || die "could not download artifact $name"
done

# ── 4. sanity-check ─────────────────────────────────────────────────
step "Checking the staged assets"
count="$(find "$STAGING" -maxdepth 1 -type f | wc -l | tr -d ' ')"
[ "$count" -eq 5 ] || die "expected exactly 5 files in $STAGING, found $count"
# Per-asset floors, because the assets differ by three orders of magnitude: a platform zip
# carries a ~100 MB runtime, the no-jdk zip is ~15 MB, and the javadoc jar is ~130 KB. A
# single 1 MB floor would abort every release on the javadoc jar.
asset_floor() {
  case "$1" in
    *-linux-x64.zip|*-macos-aarch64.zip|*-windows-x64.zip) echo 40000000 ;;  # 40 MB
    *-no-jdk.zip)                                          echo  5000000 ;;  #  5 MB
    *-javadoc.jar)                                         echo    20000 ;;  # 20 KB
    *) echo 1 ;;
  esac
}
for asset in "${ASSETS[@]}"; do
  f="$STAGING/$asset"
  [ -f "$f" ] || die "missing: $asset"
  size="$(wc -c < "$f" | tr -d ' ')"
  floor="$(asset_floor "$asset")"
  [ "$size" -gt "$floor" ] || die "$asset is only $size bytes (expected more than $floor)"
done
for label in linux-x64 macos-aarch64 windows-x64; do
  unzip -l "$STAGING/md2pdf-$VERSION-$label.zip" | grep -q 'MarkdownToPdf' \
    || die "md2pdf-$VERSION-$label.zip has no MarkdownToPdf entry"
done
unzip -l "$STAGING/md2pdf-$VERSION-no-jdk.zip" | grep -q 'MarkdownToPdf.jar' \
  || die "the no-jdk zip has no MarkdownToPdf.jar"
echo "  5 assets OK"

# ── 5. checksums ────────────────────────────────────────────────────
step "Generating SHA256SUMS"
# Hashed by name from the staging directory so the file records bare basenames: a user
# who downloads the assets and SHA256SUMS into one directory can then run
# `sha256sum -c SHA256SUMS` with nothing else.
( cd "$STAGING" && sha256 "${ASSETS[@]}" > SHA256SUMS )
cat "$STAGING/SHA256SUMS"

# ── 6. Maven Central — the point of no return ───────────────────────
if [ "$SKIP_DEPLOY" -eq 1 ]; then
  step "Skipping the Maven Central deploy (--skip-deploy)"
else
  step "Publishing lib to Maven Central"
  # -pl lib -am, never a bare deploy: the release profile is on the aggregator parent and
  # gui is a module, so an unscoped deploy would hand the GUI application to the
  # central-publishing plugin. -am is required because the parent POM is itself a
  # published artifact that lib resolves against.
  mvn -Prelease -pl lib -am clean site deploy
fi

# ── 7. tag ──────────────────────────────────────────────────────────
step "Tagging $TAG"
git tag -a "$TAG" -m "Release $VERSION"
git push origin "$TAG"

# ── 8. GitHub release ───────────────────────────────────────────────
step "Creating the GitHub release"
count="$(find "$STAGING" -maxdepth 1 -type f | wc -l | tr -d ' ')"
[ "$count" -eq 6 ] || die "expected exactly 6 files in $STAGING, found $count"
# gh release create takes filenames or globs, never a directory.
gh release create "$TAG" "$STAGING"/* \
  --title "MarkdownToPdf $VERSION" \
  --generate-notes

printf '\nReleased %s\n' "$VERSION"
```

```bash
chmod +x release.sh
```

- [ ] **Step 3: Dry-run everything before the irreversible step**

Do not run this against a real release. Check the parts that can be checked:

```bash
bash -n release.sh
shellcheck release.sh
```
Expected: both clean.

Then confirm the precondition block refuses to proceed:

```bash
./release.sh
```
Expected: exit 1 at the **first unmet precondition**, with nothing downloaded, tagged or deployed. On the feature branch that is `ERROR: not on main`; on `main` with the version unbumped it is `ERROR: refusing to release a snapshot version: 0.1.1-SNAPSHOT`. Either is a pass — what is being checked is that it stops early and says why.

To exercise the snapshot check specifically without switching branches, temporarily comment out the `not on main` check, re-run, and restore it.

- [ ] **Step 4: Confirm the deploy scope**

```bash
mvn -Prelease -pl lib -am clean install -DskipTests
```
Expected: exactly two modules in the reactor — `md2pdf-parent` and `md2pdf`. If `MarkdownToPdf` appears, the `-pl` scoping is wrong.

- [ ] **Step 5: Commit**

```bash
git add release.sh gui/pom.xml
git commit -m "release: drive releases from CI artifacts rather than local builds"
```

---

### Task 12: Documentation and the source-install wrapper

**Files:**
- Modify: `README.md`
- Modify: `gui/readme.md`
- Modify: `release.md`, `gui/release.md`
- Modify: `install.sh` (root)

- [ ] **Step 1: Update the root README**

Find every statement of a JDK requirement and replace it. The platform archives need no JDK; the `-no-jdk` archive needs a JavaFX-bundled JDK 21+.

Add a Linux prerequisites section — "no JDK required" is accurate, "nothing to install" is not:

```markdown
### Linux prerequisites

The bundled runtime supplies Java, but not the system libraries JavaFX draws with.
GTK 3 and the WebKit dependencies must be present:

    Debian/Ubuntu: sudo apt-get install libgtk-3-0 libxtst6
    Fedora/RHEL:   sudo dnf install gtk3 libXtst
    Arch:          sudo pacman -S gtk3 libxtst

The installer checks for these and reports what is missing, but cannot install them.
```

Document the four downloads and the per-platform install command:

```markdown
| Download | Install |
|---|---|
| `md2pdf-<version>-linux-x64.zip` | unzip, then `bash md2pdf-install.sh` |
| `md2pdf-<version>-macos-aarch64.zip` | unzip, then `zsh md2pdf-install.zsh` |
| `md2pdf-<version>-windows-x64.zip` | unzip, then double-click `md2pdf-install.cmd` |
| `md2pdf-<version>-no-jdk.zip` | unzip, then `java -jar MarkdownToPdf.jar` (needs a JavaFX-bundled JDK 21+) |
```

And the checksum verification commands, since the tool differs per platform:

```markdown
Verify a download against `SHA256SUMS` from the same release:

    Linux:   sha256sum -c SHA256SUMS
    macOS:   shasum -a 256 -c SHA256SUMS
    Windows: certutil -hashfile <file> SHA256   (compare the line by eye)
```

- [ ] **Step 2: Update gui/readme.md**

Same substance as Step 1 for the GUI-specific parts, plus the release recovery procedure, which needs to exist somewhere it can be found on a bad day:

```markdown
### Recovery after a partial release

`release.sh` publishes to Maven Central in step 6, and that step cannot be undone or
repeated. Steps 7 and 8 — the tag and the GitHub release — are both reversible.

If a release fails after the deploy:

    git push --delete origin v<version>
    git tag -d v<version>
    gh release delete v<version> --yes    # only if a partial release was created
    ./release.sh --skip-deploy

`--skip-deploy` re-downloads the same CI artifacts and resumes from the tag. It works
from a clean checkout: nothing in steps 3-5 is built locally.
```

Also remove any remaining instruction to build with `-P fatjar`, and replace it with `./gui/createApp.sh <platform>`.

- [ ] **Step 3: Update the release docs**

In `release.md` and `gui/release.md`, replace the manual upload instructions with `./release.sh`, and remove the references to `MarkdownToPdf-<version>-jar-with-dependencies.jar`, which no longer exists.

- [ ] **Step 4: Update the root install.sh**

It currently builds from source, calls `createApp.sh` with no argument and unzips `md2pdf-gui.zip`, none of which exist any more. Replace with:

```bash
#!/usr/bin/env bash
# Builds MarkdownToPdf from source and installs it on this machine.
# For a normal install, download a release archive instead — this is a developer
# convenience and requires a JavaFX-bundled JDK 21 to build with.
set -euo pipefail
BASEDIR="$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" > /dev/null 2>&1 && pwd )"
cd "$BASEDIR"

# An array, not a string: "$INSTALLER" would look for a command with a space in its name
# and $INSTALLER unquoted is the word-splitting that shellcheck (rightly) rejects.
case "$OSTYPE" in
  darwin*) PLATFORM=macos; LABEL=macos-aarch64; INSTALLER=(zsh md2pdf-install.zsh) ;;
  linux*)  PLATFORM=linux; LABEL=linux-x64;     INSTALLER=(bash md2pdf-install.sh) ;;
  *) echo "Unsupported platform for a source install: $OSTYPE" >&2; exit 1 ;;
esac

mvn install -DskipTests
./gui/createApp.sh "$PLATFORM"

VERSION="$(mvn -q org.apache.maven.plugins:maven-help-plugin:3.5.1:evaluate -Dexpression=revision -DforceStdout)"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
unzip -q "gui/target/md2pdf-$VERSION-$LABEL.zip" -d "$WORK"
cd "$WORK"
MD2PDF_REPLACE_EXISTING=1 "${INSTALLER[@]}"
```

- [ ] **Step 5: Sweep for stale references**

```bash
grep -rn "jar-with-dependencies\|fatjar\|MD2PDF_JAVA_HOME\|md2pdf.env\|md2pdf-gui.zip\|run.zsh" \
  --include='*.md' --include='*.sh' --include='*.zsh' --include='*.cmd' --include='*.xml' \
  --include='*.yml' . | grep -v '^./gui/req/'
```
Expected: no output. Design documents under `gui/req/` legitimately discuss what was removed; everything else must not.

- [ ] **Step 6: Final full build and commit**

```bash
mvn spotless:apply
mvn verify
git add -A
git commit -m "docs: document the bundled-runtime downloads, Linux prerequisites and release recovery"
```

---

## Self-Review

**Spec coverage.** Every section of the design maps to a task: artifact layout → 5/7/8/9; `Info.plist` → 7; the jlink runtime → 4; JavaFX alignment → 1 (+ asserted in 5's `verify-install.sh`); application assembly → 2; Linux host prerequisites → 6 (preflight) and 12 (READMEs); launchers → 5/7/8; installers and non-interactive behaviour → 6/7/8; Windows shortcut fallback → 8; macOS signing → 7; CI workflow → 10; smoke tests → 3; `release.sh` → 11; the `-no-jdk` archive → 9.

**Two things the spec leaves to implementation, decided here:**

- **`VERSION` comes from the built jar's manifest**, not from `mvn help:evaluate`, inside `createApp.sh`. An archive can then never be named for a different version than the jar it contains. The CI workflow does use `help:evaluate`, because it needs the version before anything is built.
- **`verify-install.sh` takes the expected JavaFX version as an argument** rather than reading `gui/pom.xml` itself, so it stays runnable against an installed tree with no source checkout nearby.

**What was actually executed while writing this plan**, on the maintainer's Linux machine against this repo:

- The Task 2 POM changes were applied and `mvn install` run; `check-lib-classpath.sh` passed against the result (45 jars).
- `EngineSmoke.java` and `ToolkitSmoke.java` were compiled against the produced jar and `EngineSmoke` was run (~1.6 KB PDF, pass).
- A `jlink` image **without** `jdk.compiler` was built and confirmed to fail source-file mode with `InternalError: Module jdk.compiler not in boot Layer`, and to run a precompiled class fine. This is why Task 3 compiles rather than running from source.
- `maven-dependency-plugin:3.8.1:build-classpath` and the pinned `maven-help-plugin:3.5.1:evaluate` invocations were confirmed to work, as was prefix resolution through `pluginManagement` for `spotless:apply` (3.9.0) and `javadoc:jar` (3.12.0).
- Asset sizes were measured: the javadoc jar is ~130 KB, which is why the release sanity check uses per-asset floors.

The POMs were then reverted, so the repo is unchanged — but Tasks 2 and 3 are known-good, not merely plausible. Everything in Tasks 4-12 is unrun.

**Known gaps, deliberate:**

- Tasks 7 and 8 cannot be executed or verified on the maintainer's Linux machine. Their verification steps require a Mac and a Windows box respectively; CI (Task 10) is the first place all three run together. Expect Task 10 to surface real defects in 7 and 8 — that is the intended order, not a failure of it.
- `md2pdf-install.cmd` has no automated failure-path test. `test-install-failures.sh` is bash and Linux-only by design; the Windows installer's only coverage is the `build` job running it successfully, plus the manual stub test in Task 8 Step 7.
