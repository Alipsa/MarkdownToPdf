# Decouple `lib` and `gui` versioning — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let `lib` (`md2pdf`) and `gui` (`MarkdownToPdf`) release independently, on independent version numbers, with `gui` releases never touching Maven Central.

**Architecture:** `gui/pom.xml` declares its own literal `<version>` while its `<parent><version>${revision}</version>` reference stays textually untouched (this exact combination is what keeps Maven's `relativePath` matching working — verified empirically, see spec). `release.sh` becomes a two-branch script (`lib` / `gui`) instead of one flow. `UpdateChecker` is rewritten to filter GitHub's release list by tag prefix instead of trusting the repo-wide `releases/latest` endpoint. Every script/workflow step that read the shared `revision` property to name or locate a `gui` artifact is repointed at `-Dexpression=project.version -pl gui`.

**Tech Stack:** Java 25 (gui) / Java 21 (lib), Maven multi-module reactor with `flatten-maven-plugin` (`resolveCiFriendliesOnly`), Bash release/build scripts, GitHub Actions, JUnit 5.

**Spec:** `docs/superpowers/specs/2026-08-16-decouple-lib-gui-versioning-design.md` — six rounds of review, all mechanisms in this plan verified empirically against that spec. Read it alongside this plan; the "why" for every step below lives there.

## Global Constraints

- Zero new Maven dependencies in the `gui` model layer or `lib` core (CLAUDE.md).
- `gui/pom.xml`'s `<parent><version>` element must stay the literal, untouched text `${revision}` — never a computed/overridden value or a literal version number. Changing this breaks `relativePath` matching (verified: `mvn install` fails with `Non-resolvable parent POM ... 'parent.relativePath' points at wrong local POM`).
- `gui` must never gain a Maven Central deploy step. `central-publishing-maven-plugin`'s `skipPublishing=true` in `gui/pom.xml` stays as-is.
- Every place that currently evaluates `-Dexpression=revision` to name or locate a **gui** artifact must switch to `-Dexpression=project.version` scoped `-pl gui` — scoping alone is not enough once gui stops overriding `revision`.
- All existing CI gates (`mvn verify`, `shellcheck`, `spotless:check`, `spotbugs:check`) must keep passing after every task.
- Run `mvn spotless:apply` before `mvn verify` on any touched Java file; never run `spotless:apply` and `verify` in the same command (CLAUDE.md).

---

### Task 1: `gui` declares its own literal `<version>`

**Files:**
- Modify: `pom.xml:25` (stale comment)
- Modify: `gui/pom.xml:11-13` (add gui's own `<version>`), `gui/pom.xml:190` (antrun `Implementation-Version` entry)

**Interfaces:**
- Produces: gui's own resolvable version (`0.2.0` initially — same value as lib's current `revision`, since this task introduces the *mechanism*, not a version bump; the two diverge at the next gui-only release). Every later task that reads gui's version via `-Dexpression=project.version -pl gui` depends on this.

- [ ] **Step 1: Fix the stale comment in the root POM**

`pom.xml:25` currently reads:
```xml
    <!-- Bump revision here when releasing; GUI packaging uses the JavaFX version below. -->
    <revision>0.2.0</revision>
```
This is now only true for a `lib` release. Change it to:
```xml
    <!-- Bump revision here when releasing lib. gui has its own independent <version> in
         gui/pom.xml; GUI packaging uses the JavaFX version below. -->
    <revision>0.2.0</revision>
```

- [ ] **Step 2: Add gui's own literal `<version>`, leaving `<parent><version>` untouched**

`gui/pom.xml:7-13` currently reads:
```xml
  <parent>
    <groupId>se.alipsa</groupId>
    <artifactId>md2pdf-parent</artifactId>
    <version>${revision}</version>
  </parent>
  <artifactId>MarkdownToPdf</artifactId>
  <packaging>jar</packaging>
```
Change to:
```xml
  <parent>
    <groupId>se.alipsa</groupId>
    <artifactId>md2pdf-parent</artifactId>
    <version>${revision}</version>
  </parent>
  <artifactId>MarkdownToPdf</artifactId>
  <!-- gui's own version, independent of the parent's <revision>. Bump this when releasing
       gui; keep gui/MarkdownToPdf.xml's dependency version in lockstep (release.sh checks
       this). The <parent><version> above MUST stay the literal text "${revision}" —
       overriding revision here or making the parent reference a literal both break the
       build (see the design spec's "Rejected" section). -->
  <version>0.2.0</version>
  <packaging>jar</packaging>
```

Do **not** touch the `<md2pdf.version>${revision}</md2pdf.version>` property at `gui/pom.xml:24` — it already tracks lib's actual version automatically and needs no change.

- [ ] **Step 3: Fix the antrun-generated `Implementation-Version`**

`gui/pom.xml:189-190` currently reads:
```xml
                <propertyfile file="${project.build.outputDirectory}/MarkdownToPdf.properties">
                  <entry key="Implementation-Version" value="${revision}"/>
```
Change the entry to:
```xml
                <propertyfile file="${project.build.outputDirectory}/MarkdownToPdf.properties">
                  <entry key="Implementation-Version" value="${project.version}"/>
```
This is not cosmetic: `MarkdownToPdf.java`'s `readCurrentVersion()` reads this same key from the bundled `MarkdownToPdf.properties` and feeds it to both the About dialog and `UpdateChecker`'s comparison baseline. Left as `${revision}`, the running app would report itself as lib's version instead of its own the moment the two diverge — while `maven-jar-plugin`'s own manifest entry (`gui/pom.xml:234`, unchanged) already correctly uses `${pom.version}`. Matching them here closes that gap.

- [ ] **Step 4: Build and verify the mechanism**

Run:
```bash
mvn -q install -DskipTests
```
Expected: `BUILD SUCCESS` (no output on success since `-q`; check exit code with `echo $?` → `0`).

Run:
```bash
mvn -q org.apache.maven.plugins:maven-help-plugin:3.5.1:evaluate -Dexpression=revision -DforceStdout
```
Expected output: `0.2.0` (lib's version, unchanged).

Run:
```bash
mvn -q -pl gui org.apache.maven.plugins:maven-help-plugin:3.5.1:evaluate -Dexpression=project.version -DforceStdout
```
Expected output: `0.2.0` (gui's own version, now explicit rather than inherited — same value today, but resolved via gui's own `<version>` element, not via the parent).

Run:
```bash
grep -A2 "<parent>" ~/.m2/repository/se/alipsa/MarkdownToPdf/0.2.0/MarkdownToPdf-0.2.0.pom
```
Expected: shows `<parent>...<version>0.2.0</version></parent>` — the installed, flattened gui POM's parent reference resolves correctly, and its own coordinate (the file's version-numbered path, `0.2.0`) confirms gui's own `<version>` resolved too. No `${revision}` left unresolved anywhere in the file.

- [ ] **Step 5: Commit**

```bash
git add pom.xml gui/pom.xml
git commit -m "gui: declare its own literal <version>, independent of lib's revision"
```

---

### Task 2: `gui/MarkdownToPdf.xml` points directly at gui's own version

**Files:**
- Modify: `gui/MarkdownToPdf.xml:35-37`

**Interfaces:**
- Consumes: gui's own version from Task 1 (`0.2.0`).
- Produces: a second, independent place gui's version is written — `release.sh`'s new precondition (Task 5) checks this file's dependency version against `gui/pom.xml`'s.

- [ ] **Step 1: Point the dependency version at gui's own literal version, not the parent's**

`gui/MarkdownToPdf.xml:33-37` currently reads:
```xml
    <dependency>
      <groupId>se.alipsa</groupId>
      <artifactId>MarkdownToPdf</artifactId>
      <version>${project.version}</version>
    </dependency>
```
`${project.version}` here resolves through this file's own `<parent>` inheritance (`gui/MarkdownToPdf.xml:22-27`, unchanged, still `${revision}`) — i.e. lib's version — while the gui artifact it depends on is now installed under gui's own literal version. Change to:
```xml
    <dependency>
      <groupId>se.alipsa</groupId>
      <artifactId>MarkdownToPdf</artifactId>
      <!-- gui's own version — must match gui/pom.xml's <version> exactly. Nothing else
           builds, tests, or touches this file (only CLAUDE.md references it), so a stale
           value here is invisible until someone runs `mvn -f gui/MarkdownToPdf.xml
           javafx:run`. release.sh's gui flow checks this stays in sync. -->
      <version>0.2.0</version>
    </dependency>
```

- [ ] **Step 2: Verify the dependency resolves**

Run (requires Task 1's `mvn install` to already have installed `se.alipsa:MarkdownToPdf:0.2.0`):
```bash
mvn -q -f gui/MarkdownToPdf.xml org.apache.maven.plugins:maven-help-plugin:3.5.1:evaluate -Dexpression=project.version -DforceStdout
```
Expected output: `0.2.0` (this is the *launcher* POM's own version, inherited from the parent — confirms the file's model still builds correctly after the edit).

Run:
```bash
mvn -q -f gui/MarkdownToPdf.xml dependency:resolve
```
Expected: exits `0` with no `Could not resolve dependencies` error — confirms `se.alipsa:MarkdownToPdf:0.2.0` resolves from the local repo (installed by Task 1).

- [ ] **Step 3: Commit**

```bash
git add gui/MarkdownToPdf.xml
git commit -m "gui/MarkdownToPdf.xml: point the launcher dependency at gui's own version"
```

---

### Task 3: `CLAUDE.md` rewrite

**Files:**
- Modify: `CLAUDE.md:43`, `CLAUDE.md:84`

**Interfaces:**
- Consumes: the mechanism from Tasks 1-2 (this task only rewrites prose to describe it accurately).

- [ ] **Step 1: Fix the now-half-true multi-module description**

`CLAUDE.md:43` currently reads:
```
This is a Maven multi-module project with `${revision}` CI-friendly versioning (resolved by flatten-maven-plugin).
```
Change to:
```
This is a Maven multi-module project. `lib` uses `${revision}` CI-friendly versioning (resolved by flatten-maven-plugin); `gui` has its own independent `<version>`, see below.
```

- [ ] **Step 2: Rewrite all three sentences of the `${revision}` bullet**

`CLAUDE.md:84` currently reads:
```
- The `${revision}` property in the root POM controls the version for all modules. Bump it in `pom.xml` only; flatten-maven-plugin propagates it. `gui/MarkdownToPdf.xml` inherits the root parent, so its launcher dependency follows `${revision}` as well.
```
All three sentences are wrong post-decoupling, not just the third. Change to:
```
- The `${revision}` property in the root POM controls `lib`'s version (and the parent's). `gui` has its own independent `<version>` in `gui/pom.xml`, bumped separately at gui release time. A gui release requires bumping **two** files in lockstep: `gui/pom.xml`'s `<version>` and the dependency `<version>` in `gui/MarkdownToPdf.xml` (`release.sh gui` checks they match). `gui/MarkdownToPdf.xml`'s own `<parent>` reference still follows `${revision}` (i.e. lib's version) since that file is unchanged by gui's version override — only its dependency on the built `MarkdownToPdf` artifact needs gui's version directly.
```

- [ ] **Step 3: Verify**

Re-read `CLAUDE.md:40-46` and `CLAUDE.md:80-90` and confirm: no sentence still claims `revision` controls "all modules," no instruction says "bump `pom.xml` only" for a gui release, and the two-file lockstep is stated plainly.

- [ ] **Step 4: Commit**

```bash
git add CLAUDE.md
git commit -m "docs: describe gui's independent versioning in CLAUDE.md"
```

---

### Task 4: `UpdateChecker` learns the new tag scheme

**Files:**
- Modify: `gui/src/main/java/se/alipsa/md2pdf/gui/update/UpdateChecker.java`
- Test: `gui/src/test/java/test/alipsa/md2pdf/gui/update/UpdateCheckerTest.java`
- Test: `gui/src/test/java/test/alipsa/md2pdf/gui/update/UpdateCheckerHttpTest.java`
- No change: `gui/src/main/java/se/alipsa/md2pdf/gui/update/GitHubReleaseJson.java`, `gui/src/test/java/test/alipsa/md2pdf/gui/update/GitHubReleaseJsonTest.java`, `gui/src/main/java/se/alipsa/md2pdf/gui/update/VersionComparator.java` — see Step 1's note on why.

**Interfaces:**
- Consumes: `GitHubReleaseJson.extractBracketedRegion(String json, int openIndex)`, `GitHubReleaseJson.splitTopLevelObjects(String arrayBody)`, `GitHubReleaseJson.extractTagName(String json)`, `GitHubReleaseJson.extractHtmlUrl(String json)`, `GitHubReleaseJson.extractAssets(String json)` — all unchanged, all already public static. `VersionComparator.isNewer(String candidate, String current)` — unchanged.
- Produces: `UpdateChecker.parseAndEvaluate(String currentVersion, UpdatePlatform platform, String responseJson)` — same signature as before, but `responseJson` is now a **JSON array** (`GET /releases` shape), not a single release object.

This is written test-first (TDD): the test files are rewritten to assert the *new* behavior first, confirmed failing against the *old* implementation, then `UpdateChecker.java` is rewritten to make them pass.

- [ ] **Step 1: Rewrite `UpdateCheckerTest.java` to assert the new tag scheme and array shape**

Design note: `GitHubReleaseJson`'s existing methods (`extractTagName`, `extractHtmlUrl`, `extractAssets`) operate on a **single** release JSON object and are reused unchanged, per-candidate, inside `UpdateChecker`'s new array-scanning logic — so `GitHubReleaseJsonTest.java` needs no changes; its fixtures already correctly exercise that single-release shape. The new array-splitting and prefix-filtering logic lives entirely in `UpdateChecker` (using `GitHubReleaseJson`'s already-public `extractBracketedRegion`/`splitTopLevelObjects` as building blocks), so its test coverage belongs in `UpdateCheckerTest`, which already exercises `UpdateChecker.parseAndEvaluate` directly with hand-built JSON.

Replace the full contents of `gui/src/test/java/test/alipsa/md2pdf/gui/update/UpdateCheckerTest.java` with:

```java
package test.alipsa.md2pdf.gui.update;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import se.alipsa.md2pdf.gui.update.UpdateChecker;
import se.alipsa.md2pdf.gui.update.UpdateInfo;
import se.alipsa.md2pdf.gui.update.UpdatePlatform;

public class UpdateCheckerTest {

  private static String releaseJson(String tag, String... assetLines) {
    StringBuilder assets = new StringBuilder();
    for (int i = 0; i < assetLines.length; i++) {
      if (i > 0) {
        assets.append(',');
      }
      assets.append(assetLines[i]);
    }
    return """
        {
          "tag_name": "%s",
          "html_url": "https://github.com/Alipsa/MarkdownToPdf/releases/tag/%s",
          "assets": [%s]
        }
        """
        .formatted(tag, tag, assets);
  }

  private static String asset(String name, String url) {
    return """
        {"name": "%s", "browser_download_url": "%s"}
        """
        .formatted(name, url)
        .strip();
  }

  // GET /releases returns a top-level array, not a single release — releases/latest is
  // repo-wide and would let an unrelated lib release (tagged md2pdf-v*) shadow the actual
  // latest gui release, or return no platform zips at all.
  private static String releasesArray(String... releaseJsons) {
    return "[" + String.join(",", releaseJsons) + "]";
  }

  @Test
  void updateAvailableWithMatchingAssetIsReturned() {
    String release =
        releaseJson(
            "MarkdownToPdf-v0.1.2",
            asset("md2pdf-0.1.2-linux-x64.zip", "https://example.com/md2pdf-0.1.2-linux-x64.zip"),
            asset("SHA256SUMS", "https://example.com/SHA256SUMS"));

    Optional<UpdateInfo> result =
        UpdateChecker.parseAndEvaluate("0.1.1", UpdatePlatform.LINUX_X64, releasesArray(release));

    assertTrue(result.isPresent());
    UpdateInfo info = result.get();
    assertEquals("0.1.2", info.latestVersion());
    assertEquals("MarkdownToPdf-v0.1.2", info.tagName());
    assertEquals("md2pdf-0.1.2-linux-x64.zip", info.assetName());
    assertEquals("https://example.com/md2pdf-0.1.2-linux-x64.zip", info.downloadUrl());
    assertEquals("https://example.com/SHA256SUMS", info.checksumsUrl());
    assertEquals(
        "https://github.com/Alipsa/MarkdownToPdf/releases/tag/MarkdownToPdf-v0.1.2",
        info.releaseHtmlUrl());
  }

  @Test
  void alreadyLatestVersionReturnsEmpty() {
    String release =
        releaseJson(
            "MarkdownToPdf-v0.1.1",
            asset("md2pdf-0.1.1-linux-x64.zip", "https://example.com/md2pdf-0.1.1-linux-x64.zip"),
            asset("SHA256SUMS", "https://example.com/SHA256SUMS"));

    assertTrue(
        UpdateChecker.parseAndEvaluate("0.1.1", UpdatePlatform.LINUX_X64, releasesArray(release))
            .isEmpty());
  }

  @Test
  void updateAvailableButNoAssetForThisPlatformReturnsEmpty() {
    String release =
        releaseJson(
            "MarkdownToPdf-v0.1.2",
            asset(
                "md2pdf-0.1.2-macos-aarch64.zip",
                "https://example.com/md2pdf-0.1.2-macos-aarch64.zip"),
            asset("SHA256SUMS", "https://example.com/SHA256SUMS"));

    assertTrue(
        UpdateChecker.parseAndEvaluate("0.1.1", UpdatePlatform.LINUX_X64, releasesArray(release))
            .isEmpty());
  }

  @Test
  void missingChecksumsAssetStillReturnsUpdate() {
    String release =
        releaseJson(
            "MarkdownToPdf-v0.1.2",
            asset("md2pdf-0.1.2-linux-x64.zip", "https://example.com/md2pdf-0.1.2-linux-x64.zip"));

    Optional<UpdateInfo> result =
        UpdateChecker.parseAndEvaluate("0.1.1", UpdatePlatform.LINUX_X64, releasesArray(release));

    assertTrue(result.isPresent());
    assertNull(result.get().checksumsUrl());
  }

  @Test
  void missingHtmlUrlReturnsEmpty() {
    String release =
        """
        {
          "tag_name": "MarkdownToPdf-v0.1.2",
          "assets": [%s]
        }
        """
            .formatted(
                asset(
                    "md2pdf-0.1.2-linux-x64.zip",
                    "https://example.com/md2pdf-0.1.2-linux-x64.zip"));

    assertTrue(
        UpdateChecker.parseAndEvaluate("0.1.1", UpdatePlatform.LINUX_X64, releasesArray(release))
            .isEmpty());
  }

  @Test
  void authorProfileShapedHtmlUrlReturnsEmpty() {
    String release =
        """
        {
          "tag_name": "MarkdownToPdf-v0.1.2",
          "html_url": "https://github.com/someuser",
          "assets": [%s]
        }
        """
            .formatted(
                asset(
                    "md2pdf-0.1.2-linux-x64.zip",
                    "https://example.com/md2pdf-0.1.2-linux-x64.zip"));

    assertTrue(
        UpdateChecker.parseAndEvaluate("0.1.1", UpdatePlatform.LINUX_X64, releasesArray(release))
            .isEmpty());
  }

  @Test
  void unsupportedPlatformReturnsEmpty() {
    String release =
        releaseJson(
            "MarkdownToPdf-v0.1.2",
            asset("md2pdf-0.1.2-linux-x64.zip", "https://example.com/md2pdf-0.1.2-linux-x64.zip"),
            asset("SHA256SUMS", "https://example.com/SHA256SUMS"));

    assertTrue(
        UpdateChecker.parseAndEvaluate(
                "0.1.1", UpdatePlatform.UNSUPPORTED, releasesArray(release))
            .isEmpty());
  }

  @Test
  void libReleaseInTheArrayIsIgnored() {
    // A newer lib release (md2pdf-v*) must never shadow the actual latest gui release.
    String libRelease =
        releaseJson(
            "md2pdf-v9.9.9",
            asset("md2pdf-9.9.9-sources.jar", "https://example.com/md2pdf-9.9.9-sources.jar"));
    String guiRelease =
        releaseJson(
            "MarkdownToPdf-v0.1.2",
            asset("md2pdf-0.1.2-linux-x64.zip", "https://example.com/md2pdf-0.1.2-linux-x64.zip"));

    Optional<UpdateInfo> result =
        UpdateChecker.parseAndEvaluate(
            "0.1.1", UpdatePlatform.LINUX_X64, releasesArray(libRelease, guiRelease));

    assertTrue(result.isPresent());
    assertEquals("0.1.2", result.get().latestVersion());
  }

  @Test
  void picksHighestVersionAmongMatchingPrefixRegardlessOfArrayOrder() {
    // GitHub sorts /releases by the tagged commit's date, not publish time, so a gui release
    // cut from an older commit is not guaranteed to sort above a newer one — "first match" is
    // not a safe selection rule. The older release is placed first here on purpose.
    String olderGuiRelease =
        releaseJson(
            "MarkdownToPdf-v0.1.1",
            asset("md2pdf-0.1.1-linux-x64.zip", "https://example.com/md2pdf-0.1.1-linux-x64.zip"));
    String newerGuiRelease =
        releaseJson(
            "MarkdownToPdf-v0.1.2",
            asset("md2pdf-0.1.2-linux-x64.zip", "https://example.com/md2pdf-0.1.2-linux-x64.zip"));

    Optional<UpdateInfo> result =
        UpdateChecker.parseAndEvaluate(
            "0.1.0", UpdatePlatform.LINUX_X64, releasesArray(olderGuiRelease, newerGuiRelease));

    assertTrue(result.isPresent());
    assertEquals("0.1.2", result.get().latestVersion());
  }

  @Test
  void noMatchingPrefixInArrayReturnsEmpty() {
    String libRelease =
        releaseJson(
            "md2pdf-v9.9.9",
            asset("md2pdf-9.9.9-sources.jar", "https://example.com/md2pdf-9.9.9-sources.jar"));

    assertTrue(
        UpdateChecker.parseAndEvaluate("0.1.0", UpdatePlatform.LINUX_X64, releasesArray(libRelease))
            .isEmpty());
  }
}
```

- [ ] **Step 2: Rewrite `UpdateCheckerHttpTest.java`'s stubbed path and fixture**

Replace the full contents of `gui/src/test/java/test/alipsa/md2pdf/gui/update/UpdateCheckerHttpTest.java` with:

```java
package test.alipsa.md2pdf.gui.update;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import se.alipsa.md2pdf.gui.update.UpdateCheckException;
import se.alipsa.md2pdf.gui.update.UpdateChecker;
import se.alipsa.md2pdf.gui.update.UpdateInfo;
import se.alipsa.md2pdf.gui.update.UpdatePlatform;

/**
 * Exercises {@link UpdateChecker#checkForUpdate(String)} end-to-end against a real local HTTP
 * server (JDK-bundled {@code com.sun.net.httpserver}, so this adds no dependency) via the {@link
 * UpdateChecker#API_URL_PROPERTY} override seam — the same seam intended for manual QA against a
 * fixture server.
 *
 * <p>{@code @Isolated}: this test mutates the {@code md2pdf.update.apiUrl} system property, which
 * is global JVM state. Harmless with this project's default sequential test execution, but
 * isolating it keeps that true even if parallel execution is ever enabled.
 */
@Isolated
public class UpdateCheckerHttpTest {

  private HttpServer server;

  @BeforeEach
  void startServer() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.start();
    // HttpServer context matching is path-only; the query string does not need to be part
    // of the registered context path below.
    System.setProperty(
        UpdateChecker.API_URL_PROPERTY,
        "http://127.0.0.1:" + server.getAddress().getPort() + "/releases?per_page=100");
  }

  @AfterEach
  void stopServer() {
    if (server != null) {
      server.stop(0);
    }
    System.clearProperty(UpdateChecker.API_URL_PROPERTY);
  }

  private void respond(int status, String body) {
    server.createContext(
        "/releases",
        exchange -> {
          byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
          // sendResponseHeaders' responseLength contract: 0 means chunked with unspecified
          // length, -1 means no response body at all. A genuinely empty body must send -1, not
          // 0, or the client is left waiting on a chunked stream that never starts.
          exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
          try (OutputStream os = exchange.getResponseBody()) {
            if (bytes.length > 0) {
              os.write(bytes);
            }
          }
        });
  }

  @Test
  void non200StatusThrowsUpdateCheckException() {
    respond(500, "boom");
    UpdateChecker checker = new UpdateChecker();
    assertThrows(UpdateCheckException.class, () -> checker.checkForUpdate("0.1.0"));
  }

  @Test
  void emptyBodyReturnsEmptyWithoutThrowing() throws UpdateCheckException {
    respond(200, "");
    UpdateChecker checker = new UpdateChecker();
    assertTrue(checker.checkForUpdate("0.1.0").isEmpty());
  }

  @Test
  void malformedJsonReturnsEmptyWithoutThrowing() throws UpdateCheckException {
    respond(200, "{not json at all");
    UpdateChecker checker = new UpdateChecker();
    assertTrue(checker.checkForUpdate("0.1.0").isEmpty());
  }

  @Test
  void wellFormedNewerReleaseIsReturned() throws UpdateCheckException {
    UpdatePlatform platform = UpdatePlatform.detectCurrent();
    String assetName = "md2pdf-99.0.0" + platform.assetSuffix();
    respond(
        200,
        """
        [
          {
            "tag_name": "MarkdownToPdf-v99.0.0",
            "html_url": "https://github.com/Alipsa/MarkdownToPdf/releases/tag/MarkdownToPdf-v99.0.0",
            "assets": [
              {"name": "%s", "browser_download_url": "https://example.com/%s"}
            ]
          }
        ]
        """
            .formatted(assetName, assetName));

    Optional<UpdateInfo> result = new UpdateChecker().checkForUpdate("0.1.0");

    // Deterministic on every platform CI runs on: UNSUPPORTED never matches (no asset suffix to
    // build a real file name from), every supported platform matches the asset built above.
    assertEquals(platform != UpdatePlatform.UNSUPPORTED, result.isPresent());
  }
}
```

- [ ] **Step 3: Run the tests and confirm they fail against the old implementation**

Run:
```bash
mvn -q -pl gui test -Dtest=UpdateCheckerTest,UpdateCheckerHttpTest 2>&1 | tail -60
```
Expected: multiple failures — e.g. `updateAvailableWithMatchingAssetIsReturned` fails because `parseAndEvaluate` still expects a single-object response and `extractTagName` on a top-level array (`[` first char) will not find `"tag_name"` before the first `"assets"` the way the old code assumes, and the old `tagName.startsWith("v")` strip logic mishandles `MarkdownToPdf-v...`. Confirm the failures are the *expected* new-behavior mismatches, not compile errors.

- [ ] **Step 4: Rewrite `UpdateChecker.java`**

Replace the full contents of `gui/src/main/java/se/alipsa/md2pdf/gui/update/UpdateChecker.java` with:

```java
package se.alipsa.md2pdf.gui.update;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Checks GitHub Releases for a MarkdownToPdf version newer than the one currently running. Uses
 * only {@link HttpClient} and {@link GitHubReleaseJson}'s hand-written field extraction — no JSON
 * or HTTP library dependency is added, per CLAUDE.md's zero-new-dependency constraint for the
 * {@code gui} module.
 */
public class UpdateChecker {

  /** Creates an update checker. */
  public UpdateChecker() {}

  /** System property that overrides the GitHub API URL, for manual QA against a local fixture. */
  public static final String API_URL_PROPERTY = "md2pdf.update.apiUrl";

  /**
   * gui releases are tagged {@code MarkdownToPdf-v<version>}; lib releases share the same
   * repository and are tagged {@code md2pdf-v<version>}. {@code releases/latest} is repo-wide,
   * so a lib-only release published after a gui release would shadow it and ship no platform
   * zips at all — the releases list must be filtered to this prefix instead.
   */
  private static final String TAG_PREFIX = "MarkdownToPdf-v";

  private static final String DEFAULT_API_URL =
      "https://api.github.com/repos/Alipsa/MarkdownToPdf/releases?per_page=100";

  private static final Logger LOGGER = LogManager.getLogger(UpdateChecker.class);

  /**
   * Fetches recent GitHub releases and returns update info if the latest release tagged {@code
   * MarkdownToPdf-v*} is newer than {@code currentVersion} and ships an asset for this platform.
   *
   * @param currentVersion the version currently running
   * @return update information when a newer platform release is available
   * @throws UpdateCheckException on any network, HTTP-status or interrupt failure
   */
  public Optional<UpdateInfo> checkForUpdate(String currentVersion) throws UpdateCheckException {
    String apiUrl = System.getProperty(API_URL_PROPERTY, DEFAULT_API_URL);
    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(URI.create(apiUrl))
            .header("Accept", "application/vnd.github+json")
            .timeout(Duration.ofSeconds(10))
            .GET()
            .build();
    HttpResponse<String> response;
    try (HttpClient httpClient =
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()) {
      response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    } catch (IOException e) {
      throw new UpdateCheckException("Failed to reach " + apiUrl, e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new UpdateCheckException("Interrupted while checking for updates", e);
    }
    if (response.statusCode() != 200) {
      throw new UpdateCheckException(
          "GitHub returned HTTP " + response.statusCode() + " for " + apiUrl);
    }
    return parseAndEvaluate(currentVersion, UpdatePlatform.detectCurrent(), response.body());
  }

  /**
   * Pure evaluation of a {@code GET /releases} JSON array response against the currently running
   * version and platform. No network access — used directly by tests.
   *
   * <p>The response is a top-level array of releases, not a single release: {@code
   * releases/latest} is repo-wide and would let an unrelated {@code lib} release (tagged {@code
   * md2pdf-v*}) shadow the actual latest {@code gui} release, or return no platform zips at all.
   * This scans every entry, keeps only tags starting with {@link #TAG_PREFIX}, and picks the
   * highest version among matches via {@link VersionComparator} — not "the first match" — because
   * GitHub sorts {@code /releases} by the tagged commit's date, not publish time, so a gui release
   * cut from an older commit is not guaranteed to sort above a newer lib release.
   *
   * @param currentVersion the version currently running
   * @param platform the platform whose release asset should be selected
   * @param responseJson the {@code GET /releases} JSON array response
   * @return update information when a newer matching release is available
   */
  public static Optional<UpdateInfo> parseAndEvaluate(
      String currentVersion, UpdatePlatform platform, String responseJson) {
    if (platform == UpdatePlatform.UNSUPPORTED) {
      LOGGER.info("Skipping update check: no release archive for this platform.");
      return Optional.empty();
    }
    String releaseJson = selectLatestGuiRelease(responseJson);
    if (releaseJson == null) {
      LOGGER.warn("Skipping update check: no {}* release found in the fetched page.", TAG_PREFIX);
      return Optional.empty();
    }
    String tagName = GitHubReleaseJson.extractTagName(releaseJson);
    String latestVersion = tagName.substring(TAG_PREFIX.length());
    if (!VersionComparator.isNewer(latestVersion, currentVersion)) {
      LOGGER.info(
          "No update available: latest release {} is not newer than the running {}.",
          latestVersion,
          currentVersion);
      return Optional.empty();
    }
    // extractHtmlUrl takes the first "html_url" before "assets", which is the release's own
    // field only because it precedes the "assets" array in GitHub's current (but spec-unordered)
    // response — nothing stops a future field reorder from handing back the uploader's profile
    // URL instead. A release page URL always contains "/releases/"; a profile URL never does, so
    // this converts a reorder from silently opening the wrong page into a skipped notification.
    String htmlUrl = GitHubReleaseJson.extractHtmlUrl(releaseJson);
    if (htmlUrl == null || !htmlUrl.contains("/releases/")) {
      LOGGER.info(
          "Skipping update check: release {} had no usable html_url ({}).", tagName, htmlUrl);
      return Optional.empty();
    }
    List<GitHubReleaseJson.Asset> assets = GitHubReleaseJson.extractAssets(releaseJson);
    String expectedAssetName = "md2pdf-" + latestVersion + platform.assetSuffix();
    String downloadUrl = findAssetUrl(assets, expectedAssetName);
    if (downloadUrl == null) {
      LOGGER.info(
          "Release {} is newer but ships no '{}' asset for this platform.",
          tagName,
          expectedAssetName);
      return Optional.empty();
    }
    String checksumsUrl = findAssetUrl(assets, "SHA256SUMS");
    return Optional.of(
        new UpdateInfo(
            latestVersion, tagName, expectedAssetName, downloadUrl, checksumsUrl, htmlUrl));
  }

  /**
   * Scans a {@code GET /releases} JSON array and returns the JSON object of the release with the
   * highest version among those tagged {@link #TAG_PREFIX}, or {@code null} if none match.
   */
  private static String selectLatestGuiRelease(String responseJson) {
    int openBracket = responseJson.indexOf('[');
    if (openBracket < 0) {
      return null;
    }
    String arrayBody = GitHubReleaseJson.extractBracketedRegion(responseJson, openBracket);
    String bestJson = null;
    String bestVersion = null;
    for (String candidate : GitHubReleaseJson.splitTopLevelObjects(arrayBody)) {
      String tagName = GitHubReleaseJson.extractTagName(candidate);
      if (tagName == null || !tagName.startsWith(TAG_PREFIX)) {
        continue;
      }
      String candidateVersion = tagName.substring(TAG_PREFIX.length());
      if (bestVersion == null || VersionComparator.isNewer(candidateVersion, bestVersion)) {
        bestJson = candidate;
        bestVersion = candidateVersion;
      }
    }
    return bestJson;
  }

  private static String findAssetUrl(List<GitHubReleaseJson.Asset> assets, String name) {
    for (GitHubReleaseJson.Asset asset : assets) {
      if (name.equals(asset.name())) {
        return asset.browserDownloadUrl();
      }
    }
    return null;
  }
}
```

- [ ] **Step 5: Run the tests and confirm they pass**

Run:
```bash
mvn -q -pl gui test -Dtest=UpdateCheckerTest,UpdateCheckerHttpTest,GitHubReleaseJsonTest
```
Expected: `BUILD SUCCESS`, all tests green, including the unmodified `GitHubReleaseJsonTest` (confirms the single-release extraction helpers still work unchanged).

- [ ] **Step 6: Format and run the full gui test suite**

```bash
mvn spotless:apply
mvn -pl gui verify
```
Expected: `BUILD SUCCESS`.

- [ ] **Step 7: Commit**

```bash
git add gui/src/main/java/se/alipsa/md2pdf/gui/update/UpdateChecker.java \
        gui/src/test/java/test/alipsa/md2pdf/gui/update/UpdateCheckerTest.java \
        gui/src/test/java/test/alipsa/md2pdf/gui/update/UpdateCheckerHttpTest.java
git commit -m "UpdateChecker: filter releases by MarkdownToPdf-v tag prefix instead of releases/latest"
```

---

### Task 5: `release.sh` — module argument, `--skip-deploy` grammar, and both flows

**Files:**
- Modify: `release.sh` (full rewrite)

**Interfaces:**
- Consumes: gui's own version (Task 1, read via `-Dexpression=project.version -pl gui`), the `gui/MarkdownToPdf.xml` lockstep target (Task 2).
- Produces: the `./release.sh lib [--skip-deploy]` / `./release.sh gui` invocations that `docs/release-process.md` (Task 8) and `gui/readme.md` (Task 10) document.

This script has no automated test harness — it drives real git tags, a real Maven Central deploy, and a real GitHub release. Verification here is: syntax/lint checks, actually running the argument-validation paths that `die` before touching git/gh/mvn state (safe, exercised below), and code-review tracing for the rest.

- [ ] **Step 1: Replace `release.sh` in full**

```bash
#!/usr/bin/env bash
# Drives a MarkdownToPdf release. lib and gui release independently, on independent
# version numbers; gui releases never touch Maven Central.
#
#   ./release.sh lib [--skip-deploy]
#   ./release.sh gui
#
# Builds nothing. Every release asset comes from the CI run for HEAD, so what ships is
# byte-identical to what was tested. Runs on Linux or macOS.
set -euo pipefail

BASEDIR="$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" > /dev/null 2>&1 && pwd )"
cd "$BASEDIR"

die() { printf 'ERROR: %s\n' "$*" >&2; exit 1; }
step() { printf '\n=== %s\n' "$*"; }

MODULE="${1:-}"
case "$MODULE" in
  lib)
    SKIP_DEPLOY=0
    case "${2:-}" in
      "") ;;
      --skip-deploy) SKIP_DEPLOY=1 ;;
      *) die "unrecognized argument: ${2}. usage: ./release.sh lib [--skip-deploy]" ;;
    esac
    ;;
  gui)
    [ -z "${2:-}" ] \
      || die "gui has no Maven Central deploy step, so --skip-deploy does not apply. usage: ./release.sh gui"
    ;;
  "")
    die "usage: ./release.sh lib [--skip-deploy] | ./release.sh gui"
    ;;
  *)
    die "unrecognized module: $MODULE. usage: ./release.sh lib [--skip-deploy] | ./release.sh gui"
    ;;
esac

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

if [ "$MODULE" = "lib" ]; then
  VERSION="$(mvn -q org.apache.maven.plugins:maven-help-plugin:3.5.1:evaluate -Dexpression=revision -DforceStdout)"
  TAG="md2pdf-v$VERSION"
else
  VERSION="$(mvn -q -pl gui org.apache.maven.plugins:maven-help-plugin:3.5.1:evaluate -Dexpression=project.version -DforceStdout)"
  TAG="MarkdownToPdf-v$VERSION"
  # gui/MarkdownToPdf.xml duplicates gui's own version in a second, non-reactor file that
  # nothing else builds, tests, or touches (only CLAUDE.md references it) — a stale value
  # there is invisible until a developer runs `mvn -f gui/MarkdownToPdf.xml javafx:run`,
  # possibly releases later.
  LAUNCHER_VERSION="$(sed -n -e '/<artifactId>MarkdownToPdf<\/artifactId>/,/<\/dependency>/ s/.*<version>\(.*\)<\/version>.*/\1/p' gui/MarkdownToPdf.xml)"
  [ "$LAUNCHER_VERSION" = "$VERSION" ] \
    || die "gui/MarkdownToPdf.xml's dependency version ($LAUNCHER_VERSION) does not match gui/pom.xml's version ($VERSION) — bump both together before releasing"
fi
case "$VERSION" in *-SNAPSHOT) die "refusing to release a snapshot version: $VERSION" ;; esac
echo "Releasing $MODULE $VERSION"

git fetch --tags --quiet
git rev-parse -q --verify "refs/tags/$TAG" > /dev/null && die "tag $TAG already exists locally"
git ls-remote --exit-code --tags origin "$TAG" > /dev/null 2>&1 && die "tag $TAG already exists on the remote"
git push --dry-run --quiet origin HEAD || die "git push would fail"

if [ "$MODULE" = "lib" ]; then
  # The POM, not the directory: a directory listing can 200 on a partially-populated or
  # stale path, and only the POM's presence means the version is actually published.
  CENTRAL="https://repo1.maven.org/maven2/se/alipsa/md2pdf/$VERSION/md2pdf-$VERSION.pom"
  if curl -sfI "$CENTRAL" > /dev/null; then
    [ "$SKIP_DEPLOY" -eq 1 ] \
      || die "$VERSION is already on Maven Central. Maven Central cannot be overwritten; re-run with './release.sh lib --skip-deploy' to finish the rest of the release."
  fi
fi

# ── 1b. release notes ───────────────────────────────────────────────
# Composed here rather than at step 8: a module whose changelog still heads its section
# -SNAPSHOT has nothing to say about $VERSION, and that has to stop the release before the
# irreversible deploy — not surface as a published release page with a section missing.
step "Composing release notes"

# All three changelogs head every version section "## <version>", optionally followed by a
# date, so a section runs from that heading to the next "## " one. Only "## " closes a
# section: a deeper "### " subheading inside one is part of it and is kept. Leading and
# trailing blank lines are dropped so the composed file has no ragged gaps between sections.
extract_section() {
  awk -v ver="$2" '
    /^## / {
      if (started) { exit }
      heading = $0
      sub(/^##[[:space:]]+/, "", heading)
      if (heading == ver || index(heading, ver " ") == 1) { started = 1 }
      next
    }
    started {
      if (NF == 0) { if (printed) pending++; next }
      while (pending > 0) { print ""; pending-- }
      print; printed = 1
    }
  ' "$1"
}

# Outside $STAGING on purpose: step 3 empties that directory, and step 8 uploads every file
# in it as a release asset. .release-staging itself is gitignored.
NOTES="$BASEDIR/.release-staging/release-notes-$VERSION.md"
mkdir -p "$(dirname "$NOTES")"
: > "$NOTES"

add_section() {
  local title="$1" file="$2" body
  body="$(extract_section "$file" "$VERSION")"
  [ -n "$body" ] \
    || die "$file has no section for $VERSION — bump its heading from -SNAPSHOT before releasing"
  printf '## %s\n\n%s\n\n' "$title" "$body" >> "$NOTES"
}
if [ "$MODULE" = "lib" ]; then
  add_section "md2pdf-parent — build and release tooling" release.md
  add_section "md2pdf — library"                          lib/release.md
else
  add_section "MarkdownToPdf — desktop application"        gui/release.md
fi
cat "$NOTES"

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
# Keep staging outside target/: the release deploy includes `clean`, and -am brings the
# aggregator parent into the reactor, so Maven clean removes every module's target tree.
# This directory is ignored so a failed post-deploy recovery does not dirty the checkout.
STAGING="$BASEDIR/.release-staging/release-$VERSION"
# Emptied, not reused: the --skip-deploy recovery re-runs this step over a directory a
# previous attempt already populated, and a stale file here would ship unhashed under a
# SHA256SUMS that appears to account for it.
rm -rf "$STAGING"
mkdir -p "$STAGING"

if [ "$MODULE" = "lib" ]; then
  ASSETS=(
    "md2pdf-$VERSION-sources.jar"
    "md2pdf-$VERSION-javadoc.jar"
  )
else
  ASSETS=(
    "md2pdf-$VERSION-linux-x64.zip"
    "md2pdf-$VERSION-macos-aarch64.zip"
    "md2pdf-$VERSION-windows-x64.zip"
    "md2pdf-$VERSION-no-jdk.zip"
  )
fi

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
if [ "$MODULE" = "lib" ]; then
  EXPECTED_COUNT=2
else
  EXPECTED_COUNT=4
fi
count="$(find "$STAGING" -maxdepth 1 -type f | wc -l | tr -d ' ')"
[ "$count" -eq "$EXPECTED_COUNT" ] || die "expected exactly $EXPECTED_COUNT files in $STAGING, found $count"
# Per-asset floors, because the assets differ by three orders of magnitude: a platform zip
# carries a ~100 MB runtime, the no-jdk zip is ~15 MB, and the javadoc/sources jars are each
# tens of KB. A single 1 MB floor would abort every release on the small jars.
asset_floor() {
  case "$1" in
    *-linux-x64.zip|*-macos-aarch64.zip|*-windows-x64.zip) echo 40000000 ;;  # 40 MB
    *-no-jdk.zip)                                          echo  5000000 ;;  #  5 MB
    *-javadoc.jar|*-sources.jar)                           echo    10000 ;;  # 10 KB
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
if [ "$MODULE" = "gui" ]; then
  for label in linux-x64 macos-aarch64 windows-x64; do
    unzip -l "$STAGING/md2pdf-$VERSION-$label.zip" | grep -F 'MarkdownToPdf' > /dev/null \
      || die "md2pdf-$VERSION-$label.zip has no MarkdownToPdf entry"
  done
  unzip -l "$STAGING/md2pdf-$VERSION-no-jdk.zip" | grep -F 'MarkdownToPdf.jar' > /dev/null \
    || die "the no-jdk zip has no MarkdownToPdf.jar"
fi
echo "  $EXPECTED_COUNT assets OK"

# ── 5. checksums ────────────────────────────────────────────────────
step "Generating SHA256SUMS"
# Hashed by name from the staging directory so the file records bare basenames: a user
# who downloads the assets and SHA256SUMS into one directory can then run
# `sha256sum -c SHA256SUMS` with nothing else.
( cd "$STAGING" && sha256 "${ASSETS[@]}" > SHA256SUMS )
cat "$STAGING/SHA256SUMS"

# ── 6. Maven Central — the point of no return ───────────────────────
if [ "$MODULE" = "gui" ]; then
  step "No Maven Central deploy for gui — this is the entire point"
elif [ "$SKIP_DEPLOY" -eq 1 ]; then
  step "Skipping the Maven Central deploy (--skip-deploy)"
else
  step "Publishing lib to Maven Central"
  # -pl lib -am, never a bare deploy: the release profile is on the aggregator parent and
  # gui is a module, so an unqualified deploy would hand the GUI application to the
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
FINAL_COUNT=$((EXPECTED_COUNT + 1))
count="$(find "$STAGING" -maxdepth 1 -type f | wc -l | tr -d ' ')"
[ "$count" -eq "$FINAL_COUNT" ] || die "expected exactly $FINAL_COUNT files in $STAGING, found $count"
if [ "$MODULE" = "lib" ]; then
  TITLE="md2pdf $VERSION"
else
  TITLE="MarkdownToPdf $VERSION"
fi
# gh release create takes filenames or globs, never a directory.
gh release create "$TAG" "$STAGING"/* \
  --title "$TITLE" \
  --notes-file "$NOTES"

printf '\nReleased %s %s\n' "$MODULE" "$VERSION"
```

- [ ] **Step 2: Syntax and lint checks**

```bash
bash -n release.sh
```
Expected: no output, exit `0`.

```bash
shellcheck release.sh
```
Expected: no findings (this mirrors `ci.yml`'s `lint-scripts` job, which shellchecks this exact file — a failure here is a failure there).

- [ ] **Step 3: Exercise the argument-validation paths that `die` before touching git/gh/mvn state**

These are safe to run for real: the `case` block at the top of the script `die`s before the "Preconditions" step even begins, so none of these reach git status checks, `mvn`, or network calls.

```bash
./release.sh; echo "exit=$?"
```
Expected: `ERROR: usage: ./release.sh lib [--skip-deploy] | ./release.sh gui` on stderr, `exit=1`.

```bash
./release.sh nonsense; echo "exit=$?"
```
Expected: `ERROR: unrecognized module: nonsense. usage: ./release.sh lib [--skip-deploy] | ./release.sh gui`, `exit=1`.

```bash
./release.sh gui --skip-deploy; echo "exit=$?"
```
Expected: `ERROR: gui has no Maven Central deploy step, so --skip-deploy does not apply. usage: ./release.sh gui`, `exit=1`.

```bash
./release.sh lib --bogus; echo "exit=$?"
```
Expected: `ERROR: unrecognized argument: --bogus. usage: ./release.sh lib [--skip-deploy]`, `exit=1`.

- [ ] **Step 4: Manual trace of the rest of the flow (cannot be safely executed — a real run tags, deploys, and opens a GitHub release)**

Re-read the full script and confirm by inspection:
- `./release.sh lib` — tag `md2pdf-v$VERSION`, notes from `release.md` + `lib/release.md` only, assets are the two lib jars, Central deploy runs unless `--skip-deploy`, title `md2pdf $VERSION`.
- `./release.sh gui` — tag `MarkdownToPdf-v$VERSION`, notes from `gui/release.md` only, assets are the four platform zips, no Central deploy ever, title `MarkdownToPdf $VERSION`, and the `LAUNCHER_VERSION` precondition fires before anything else if `gui/MarkdownToPdf.xml`'s dependency version is out of sync with `gui/pom.xml`'s.

- [ ] **Step 5: Commit**

```bash
git add release.sh
git commit -m "release.sh: split lib/gui into independent release flows"
```

---

### Task 6: `install.sh` and `gui/buildAndRun.sh` read gui's own version

**Files:**
- Modify: `install.sh:27`
- Modify: `gui/buildAndRun.sh:16`

**Interfaces:**
- Consumes: gui's own version from Task 1.

- [ ] **Step 1: Fix `install.sh`**

`install.sh:27` currently reads:
```bash
VERSION="$(mvn -q org.apache.maven.plugins:maven-help-plugin:3.5.1:evaluate -Dexpression=revision -DforceStdout)"
```
Change to:
```bash
VERSION="$(mvn -q -pl gui org.apache.maven.plugins:maven-help-plugin:3.5.1:evaluate -Dexpression=project.version -DforceStdout)"
```
(`install.sh:30`'s `unzip -q "gui/target/md2pdf-$VERSION-$LABEL.zip" ...` needs no change — it already consumes `$VERSION` generically.)

- [ ] **Step 2: Fix `gui/buildAndRun.sh`**

`gui/buildAndRun.sh:16` currently reads:
```bash
VERSION="$(mvn -q org.apache.maven.plugins:maven-help-plugin:3.5.1:evaluate -Dexpression=revision -DforceStdout)"
```
Change to:
```bash
VERSION="$(mvn -q -pl gui org.apache.maven.plugins:maven-help-plugin:3.5.1:evaluate -Dexpression=project.version -DforceStdout)"
```

- [ ] **Step 3: Syntax/lint checks**

```bash
bash -n install.sh && bash -n gui/buildAndRun.sh
shellcheck install.sh gui/buildAndRun.sh
```
Expected: no output beyond nothing, exit `0` for both.

- [ ] **Step 4: Verify the evaluate call in isolation**

```bash
mvn -q -pl gui org.apache.maven.plugins:maven-help-plugin:3.5.1:evaluate -Dexpression=project.version -DforceStdout
```
Expected output: `0.2.0` (matches Task 1's value; confirms both scripts now read the same value `gui/target/md2pdf-<this-value>-<label>.zip` was built under by `createApp.sh`).

- [ ] **Step 5: Commit**

```bash
git add install.sh gui/buildAndRun.sh
git commit -m "install.sh, buildAndRun.sh: read gui's own version, not the shared revision"
```

---

### Task 7: `ci.yml` — split `APP_VERSION`/`LIB_VERSION`, add the sources-jar upload

**Files:**
- Modify: `.github/workflows/ci.yml`

**Interfaces:**
- Consumes: gui's own version (`project.version -pl gui`) and lib's version (`revision`, root-scoped).
- Produces: CI artifacts `md2pdf-<gui-version>-<label>`, `md2pdf-<gui-version>-no-jdk`, `md2pdf-<lib-version>-javadoc`, `md2pdf-<lib-version>-sources` — `release.sh` (Task 5) downloads these by exactly these names.

- [ ] **Step 1: Split the version-read step**

`.github/workflows/ci.yml:78-79` currently reads:
```yaml
      - name: Read the project version
        run: echo "APP_VERSION=$(mvn -q org.apache.maven.plugins:maven-help-plugin:3.5.1:evaluate -Dexpression=revision -DforceStdout)" >> "$GITHUB_ENV"
```
Change to:
```yaml
      - name: Read the project version
        run: |
          echo "APP_VERSION=$(mvn -q -pl gui org.apache.maven.plugins:maven-help-plugin:3.5.1:evaluate -Dexpression=project.version -DforceStdout)" >> "$GITHUB_ENV"
          echo "LIB_VERSION=$(mvn -q org.apache.maven.plugins:maven-help-plugin:3.5.1:evaluate -Dexpression=revision -DforceStdout)" >> "$GITHUB_ENV"
```
Every existing `${{ env.APP_VERSION }}` reference in the "Package"/upload/unpack/verify/no-jdk steps (`ci.yml:88-148`) is unchanged — it already correctly names and locates gui's own artifacts, and now resolves via `project.version` instead of the no-longer-shared `revision`.

- [ ] **Step 2: Rename the javadoc jar's version variable and add the sources jar step**

`.github/workflows/ci.yml:151-160` currently reads:
```yaml
      - name: Build the javadoc jar
        if: matrix.platform == 'linux'
        run: mvn -pl lib javadoc:jar

      - uses: actions/upload-artifact@v7
        if: matrix.platform == 'linux'
        with:
          name: md2pdf-${{ env.APP_VERSION }}-javadoc
          path: lib/target/md2pdf-${{ env.APP_VERSION }}-javadoc.jar
          if-no-files-found: error
```
Change to:
```yaml
      - name: Build the javadoc jar
        if: matrix.platform == 'linux'
        run: mvn -pl lib javadoc:jar

      - uses: actions/upload-artifact@v7
        if: matrix.platform == 'linux'
        with:
          name: md2pdf-${{ env.LIB_VERSION }}-javadoc
          path: lib/target/md2pdf-${{ env.LIB_VERSION }}-javadoc.jar
          if-no-files-found: error

      - name: Build the sources jar
        if: matrix.platform == 'linux'
        run: mvn -pl lib source:jar

      - uses: actions/upload-artifact@v7
        if: matrix.platform == 'linux'
        with:
          name: md2pdf-${{ env.LIB_VERSION }}-sources
          path: lib/target/md2pdf-${{ env.LIB_VERSION }}-sources.jar
          if-no-files-found: error
```

- [ ] **Step 3: Verify the underlying Maven commands work standalone**

```bash
mvn -q -pl lib -am install -DskipTests
mvn -q -pl lib source:jar
ls -la lib/target/md2pdf-*-sources.jar
```
Expected: the last command lists `lib/target/md2pdf-0.2.0-sources.jar` (already verified during plan-writing — this reproduces it).

```bash
mvn -q -pl lib javadoc:jar
ls -la lib/target/md2pdf-*-javadoc.jar
```
Expected: lists `lib/target/md2pdf-0.2.0-javadoc.jar`.

- [ ] **Step 4: Validate the YAML is well-formed**

```bash
python3 -c "import yaml; yaml.safe_load(open('.github/workflows/ci.yml')); print('OK')"
```
Expected: `OK`. (If `pyyaml` is unavailable, `ruby -ryaml -e "YAML.load_file('.github/workflows/ci.yml'); puts 'OK'"` is an equivalent fallback.)

- [ ] **Step 5: Commit**

```bash
git add .github/workflows/ci.yml
git commit -m "ci: split APP_VERSION/LIB_VERSION, upload the lib sources jar"
```

---

### Task 8: `docs/release-process.md` rewrite

**Files:**
- Modify: `docs/release-process.md` (full rewrite)

**Interfaces:**
- Consumes: the two `release.sh` invocations from Task 5, the two-file gui version bump from Tasks 1-2.

- [ ] **Step 1: Replace the file in full**

```markdown
# MarkdownToPdf release process

`lib` and `gui` release independently, on independent version numbers. `gui` releases never
touch Maven Central.

```
./release.sh lib [--skip-deploy]
./release.sh gui
```

Run either from a clean `main` checkout. Both download the artifacts from the green CI run for
`HEAD`, create the version tag, and open the GitHub release; `./release.sh lib` additionally
publishes the library to Maven Central. Use `./release.sh lib --skip-deploy` only when Maven
Central already received the release and a later release step needs recovery; see
[`gui/readme.md`](../gui/readme.md) for lib's and gui's recovery instructions.

## Before releasing a lib version

Bump `<revision>` in the root `pom.xml`, then give **both** relevant changelogs a
`## <version>` section — [`release.md`](../release.md) and [`lib/release.md`](../lib/release.md).
`release.md` covers the shared build, CI and release tooling; `lib/release.md` covers the
library itself.

## Before releasing a gui version

Bump **two** files in lockstep:

- `gui/pom.xml`'s own `<version>`.
- The dependency `<version>` in `gui/MarkdownToPdf.xml` — `release.sh gui` checks these match
  and refuses to proceed otherwise, but the values still have to be written by hand in both
  places.

Then give [`gui/release.md`](../gui/release.md) a `## <version>` section. If this is the first
release under the new `MarkdownToPdf-v<version>` tag scheme, that section must also say plainly
that installs predating this change will not detect this or any future update automatically (the
old `UpdateChecker` parses the new tag scheme incorrectly) and should be updated manually from
the GitHub releases page.

## What `release.sh` checks

`release.sh` composes the GitHub release notes from the relevant changelog section(s) above and
checks them in its preconditions — before anything is downloaded and long before the irreversible
Maven Central deploy (lib only). A module whose heading still reads `-SNAPSHOT` has no section for
the version being released, and the run aborts with:

    ERROR: gui/release.md has no section for 0.1.1 — bump its heading from -SNAPSHOT before releasing

For a gui release, it also checks that `gui/MarkdownToPdf.xml`'s dependency version matches
`gui/pom.xml`'s version, and dies with a clear message before doing anything else if they've
drifted.

Push the release commit and wait for its CI run to go green before running `./release.sh`;
the script releases the artifacts built by the run for `HEAD`, so a later commit means a
later CI run and a re-check.
```

- [ ] **Step 2: Verify against the spec's checklist**

Re-read `docs/superpowers/specs/2026-08-16-decouple-lib-gui-versioning-design.md`'s "`docs/release-process.md` rewrite" section and confirm every point is covered: both version-bump instructions named explicitly (not just the changelog gate), the two-file gui lockstep spelled out, the new invocations named in the opening paragraph, and the stranded-install documentation requirement carried into the gui release checklist (rather than hand-editing `gui/release.md`'s current, already-released `0.2.0` section, which would misrepresent history).

- [ ] **Step 3: Commit**

```bash
git add docs/release-process.md
git commit -m "docs: rewrite release-process.md for independent lib/gui releases"
```

---

### Task 9: User-facing download docs — `README.md` and `gui/readme.md`

**Files:**
- Modify: `README.md:54-61` (download section)
- Modify: `gui/readme.md:39-47` (download section)

**Interfaces:**
- None — this task only adds a clarifying sentence; no code or filenames change.

- [ ] **Step 1: Add the "Latest" badge caveat to `README.md`**

After `README.md`'s existing Downloads table (`README.md:56-61`), add:
```markdown
GitHub's repo-sidebar "Latest" badge is repo-wide and can point at a `lib`-only release (which
ships no application) when one is newer than the latest `gui` release. Download the newest
release tagged `MarkdownToPdf-v*` specifically, not whatever the sidebar highlights.
```

- [ ] **Step 2: Add the same caveat to `gui/readme.md`**

After `gui/readme.md`'s existing Downloads table (`gui/readme.md:41-46`), add the same paragraph:
```markdown
GitHub's repo-sidebar "Latest" badge is repo-wide and can point at a `lib`-only release (which
ships no application) when one is newer than the latest `gui` release. Download the newest
release tagged `MarkdownToPdf-v*` specifically, not whatever the sidebar highlights.
```

- [ ] **Step 3: Verify**

Re-read both files and confirm the note reads naturally directly under each Downloads table and doesn't duplicate or contradict the "Building from source" sections below it.

- [ ] **Step 4: Commit**

```bash
git add README.md gui/readme.md
git commit -m "docs: note that GitHub's repo-wide Latest badge can point at a lib-only release"
```

---

### Task 10: `gui/readme.md`'s recovery section splits into two, one per module

**Files:**
- Modify: `gui/readme.md:119-132`

**Interfaces:**
- Consumes: the tag formats and `release.sh` invocations from Task 5.

- [ ] **Step 1: Replace the "Recovery after a partial release" section**

`gui/readme.md:119-132` currently reads:
```markdown
## Recovery after a partial release

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
Replace with:
```markdown
## Recovery after a partial release

Both `./release.sh lib` and `./release.sh gui` can fail after the tag is pushed but before the
GitHub release is created (step 8) — a `gh` auth expiry, a network drop, or an asset upload error
partway through several ~100 MB zips all leave the tag pushed with no release to show for it.
Re-running the same command then dies in its own preconditions, since the tag now exists both
locally and on the remote.

### lib

`./release.sh lib` additionally publishes to Maven Central in step 6, and that step cannot be
undone or repeated — steps 7 and 8 (the tag and the GitHub release) are both reversible on their
own.

If a lib release fails after the deploy:

    git push --delete origin md2pdf-v<version>
    git tag -d md2pdf-v<version>
    gh release delete md2pdf-v<version> --yes    # only if a partial release was created
    ./release.sh lib --skip-deploy

`--skip-deploy` re-downloads the same CI artifacts and resumes from the tag. It works from a
clean checkout: nothing in steps 3-5 is built locally.

### gui

gui has no irreversible step — every stage is safe to redo, so recovery is just
delete-and-re-run, every time:

    git push --delete origin MarkdownToPdf-v<version>
    git tag -d MarkdownToPdf-v<version>
    gh release delete MarkdownToPdf-v<version> --yes    # only if a partial release was created
    ./release.sh gui
```

- [ ] **Step 2: Verify**

Re-read `gui/readme.md`'s full "Recovery after a partial release" section and confirm: both blocks are present, the lib block keeps the "cannot be undone or repeated" framing scoped to the Central-deploy step specifically (not implying gui also has one), and the gui block states plainly that gui's recovery is unconditional delete-and-re-run.

- [ ] **Step 3: Commit**

```bash
git add gui/readme.md
git commit -m "docs: split the recovery section into lib and gui blocks"
```

---

### Task 11: `MarkdownToPdf-dist`'s `build-mas.sh` usage comment

**Files:**
- Modify: `/Users/pernyf/project/MarkdownToPdf-dist/build-mas.sh:5` — **a separate git repository**, sibling to this one on disk (`/Users/pernyf/project/MarkdownToPdf-dist`). This task's commit belongs to that repo's own history and its own PR, not this one's.

**Interfaces:**
- None — this file already accepts an arbitrary git ref string via `--tag`; only the usage example changes.

- [ ] **Step 1: Update the usage example**

`build-mas.sh:5` (in the `MarkdownToPdf-dist` repo) currently reads:
```bash
#   ./build-mas.sh --tag v0.2.0 --build-number 3 [--upload]
```
Change to:
```bash
#   ./build-mas.sh --tag MarkdownToPdf-v0.2.0 --build-number 3 [--upload]
```
No other change: `--tag` already accepts an arbitrary git ref string (confirmed by reading `build-mas.sh`'s argument handling — `TAG="$2"` with no format validation), so only the example in the header comment needs updating to reflect the new tag scheme going forward. Pre-existing bare `vX.Y.Z` tags are untouched by this plan and remain valid `--tag` arguments for historical releases.

- [ ] **Step 2: Verify**

```bash
cd /Users/pernyf/project/MarkdownToPdf-dist
bash -n build-mas.sh
grep -n "^#   ./build-mas.sh" build-mas.sh
```
Expected: `bash -n` exits `0`; the grep shows both usage lines, the `--tag` one now reading `MarkdownToPdf-v0.2.0`.

- [ ] **Step 3: Commit (in the `MarkdownToPdf-dist` repo, not this one)**

```bash
cd /Users/pernyf/project/MarkdownToPdf-dist
git add build-mas.sh
git commit -m "docs: update --tag usage example for the MarkdownToPdf-v tag scheme"
```

---

## Final full-repo verification (after all tasks)

- [ ] Run the complete build once more from a clean state:
```bash
cd /Users/pernyf/project/MarkdownToPdf
mvn spotless:apply
mvn verify
```
Expected: `BUILD SUCCESS` — compiles, all tests (including the rewritten `UpdateChecker`/`UpdateCheckerHttp` tests), spotless format check, and SpotBugs all pass.

- [ ] Re-run the release.sh safe argument-validation smoke tests once more against the final state (Task 5, Step 3) to confirm nothing regressed.

- [ ] Confirm `git log --oneline` shows one commit per task, in order, and `git status` is clean.
