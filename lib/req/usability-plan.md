# Md2Pdf Lib Usability Implementation Plan

## Goal

Improve `md2pdf` as a small, predictable library for transforming Markdown to HTML and PDF with custom styling. The implementation should keep the current fluent API intact while adding safer defaults and ergonomic extension points.

## Phases

### Phase 1: Low-Risk Fixes

Status: Implemented

This phase contains small correctness and dependency cleanup work with minimal API impact.

- Priority 1: Stream ownership
- Priority 5: Warning-clean default CSS
- Priority 6: Remove transitive Commons IO dependency

Expected outcome: the current API behaves more predictably, emits fewer PDF-rendering warnings, and depends only on declared dependencies.

### Phase 2: Styling Ergonomics

Status: Implemented

This phase improves the core custom styling workflow while preserving existing behavior.

- Priority 3: Append-style CSS
- Priority 4: Header/footer page layout controls
- Priority 9: MathML default font warning cleanup

Expected outcome: users can keep default Markdown styling while adding overrides, and page headers/footers no longer impose surprising fixed margins.

### Phase 3: Resource Resolution

Status: Implemented

This phase changes how relative resources are resolved for PDF rendering.

- Priority 2: Base URI and relative resources

Expected outcome: images, CSS URLs, and font URLs can resolve relative to the Markdown file or an explicit caller-provided base URI.

This phase should be reviewed separately because it affects file access behavior and all relative external resources.

### Phase 4: Optional API Expansion

Status: Implemented

This phase adds broader convenience features after the foundational API is stable.

- Priority 7: Markdown options
- Priority 8: PDF metadata

Expected outcome: callers get more control over Markdown parsing/rendering and generated PDF document metadata.

## Priority 1: Stream Ownership

Phase: 1

### Problem

`Job.toPdf(OutputStream)` currently closes the caller-provided stream. This is surprising for library users and inconsistent with `toHtml(OutputStream)`.

### Implementation

- Change `toPdf(OutputStream)` to wrap with `BufferedOutputStream` without try-with-resources.
- Call `flush()` after rendering.
- Do not close the caller-provided stream.
- Keep `toPdf(File)` and `toPdf(Path)` closing internally opened streams.
- Ensure `flush()` is inside the existing `try` block so any `IOException` is caught and rethrown as `Md2PdfException`.

### Tests

- Add a test with a custom `OutputStream` that records `close()` calls.
- Assert `toPdf(out)` writes bytes and does not close `out`.
- Keep existing byte-array and file output tests.

### Compatibility

This is a behavior fix. Code relying on `toPdf(OutputStream)` to close streams should be considered incorrect; note the change in release notes.

## Priority 2: Base URI and Relative Resources

Phase: 3

### Problem

PDF rendering uses the process working directory as the base URI. Relative images, CSS URLs, and font URLs should resolve relative to the Markdown file, a caller-provided path, or another explicit base.

### API

```java
new Md2PdfEngine()
    .markdown(path)
    .basePath(path.getParent())
    .toPdf();

new Md2PdfEngine()
    .markdown(markdown)
    .basePath(path)
    .toPdf();
```

Add the minimal high-value overload first:

- `basePath(Path basePath)`

Defer broader URI-oriented overloads to Phase 4 unless a real use case appears:

- `baseUri(String baseUri)`
- `baseUri(URI baseUri)`
- `baseFile(File baseFile)`

### Implementation

- Add `String baseUri` to `Job`.
- Set default `baseUri` to the current working directory for `markdown(String)` and `markdown(InputStream)`.
- In `markdown(File)` and `markdown(Path)`, default `baseUri` to the parent directory of the markdown file.
- Pass the job base URI into `xhtmlToPdf(...)` and use it in `builder.withW3cDocument(doc, baseUri)`.
- Preserve explicit base path overrides via `.basePath(...)`.
- Keep `baseUri` on `Job` and pass it through to the synchronized `xhtmlToPdf(...)` methods as a parameter. Do not store it on `Md2PdfEngine`; this avoids shared-state races between concurrent jobs on the same engine.

### Tests

- Add a Markdown file in `target` or test resources with a relative image reference.
- Render via `markdown(Path)` and verify the PDF contains an image XObject.
- Add a test where `.basePath(...)` resolves an image for `markdown(String)`.

### Compatibility

This improves file/path behavior. `markdown(String)` should keep the current working directory default to avoid surprises.

### Implementation Result

`markdown(File)` and `markdown(Path)` now use the Markdown file's parent directory as the base URI for PDF rendering. `markdown(String)` and `markdown(InputStream)` keep the current working directory default. `.basePath(Path)` was added as the explicit override for Markdown strings and other caller-managed sources.

## Priority 3: Append-Style CSS

Phase: 2

### Problem

`css(...)` replaces all default Markdown styling. Users often want default styling plus overrides.

### API

Keep current replacement behavior:

```java
.css("body { font-family: serif; }")
```

Add append behavior:

```java
.addCss("body { font-family: serif; }")
.addCss(path)
.addCss(file)
.addCss(url)
.addCss(inputStream)
```

Optional later enhancement:

```java
.defaultCss(false)
```

### Implementation

- Keep `css` as the replacement stylesheet.
- Add `List<String> additionalCss`.
- In `buildHtml()`:
  - if replacement `css` exists, start with it;
  - otherwise start with `DEFAULT_CSS`;
  - append page header/footer CSS when needed;
  - append all `additionalCss` in insertion order.
- Consider renaming internal variables to `replacementCss` and `additionalCss` for clarity.

### Tests

- Verify `.css(...)` still replaces default CSS.
- Verify `.addCss(...)` preserves default CSS and includes the custom rules after it.
- Verify file/path/url/input stream overloads reuse existing read helpers.

### Compatibility

No breaking change. This adds a safer, more convenient default path for custom styling.

## Priority 4: Header/Footer Page Layout Controls

Phase: 2

### Problem

The new header/footer helper injects an `@page` rule with a fixed `0.75in` margin. Users may already define page size or margins.

### API

Short-term:

```java
.pageMargins("0.75in")
.pageMargins("0.5in", "0.75in", "0.5in", "0.75in")
```

Possible later:

```java
.pageSize("A4")
.pageSize("4.18in", "6.88in")
```

### Implementation

- Remove hard-coded margin from `PAGE_HEADER_FOOTER_CSS` or make it conditional.
- Add `String pageMarginCss`.
- If the user calls `pageMargins(...)`, inject `@page { margin: ... }`.
- Keep `0.75in` as the default margin whenever `pageHeader(...)` or `pageFooter(...)` is used and no explicit page margin is configured. This prevents header/footer content from overlapping body content in the simplest use case.
- Make `.pageMargins(...)` override that default.
- Keep header/footer margin boxes in a separate `@page` block:

```css
@page {
  @top-center { content: element(md2pdf-page-header) }
  @bottom-center { content: element(md2pdf-page-footer) }
}
```

- Ensure user CSS appended after default CSS can still override page rules.

### Tests

- Verify header/footer CSS includes the default `margin: 0.75in` when header/footer are used and no explicit margin is configured.
- Verify `.pageMargins("1in")` appears in generated HTML.
- Verify `.pageMargins("1in")` replaces the default header/footer margin.
- Verify header/footer still render in PDF.

### Compatibility

This preserves ergonomic default behavior while allowing callers to override margins explicitly.

## Priority 5: Warning-Clean Default CSS

Phase: 1

### Problem

OpenHTMLtoPDF warns about unsupported CSS in the default stylesheet, including `rgba(...)` and `overflow: auto`.

### Implementation

- Replace `rgba(27,31,35,0.05)` with a supported color such as `#f6f8fa`.
- Remove `overflow: auto` from the `table` rule; `display: block` already handles table containment well enough for PDF output.
- Replace `overflow: auto` in the `pre` rule with intentional wrapping behavior, such as `white-space: pre-wrap` and `word-wrap: break-word`, so long lines do not silently clip.
- Review default CSS for browser-only declarations that OpenHTMLtoPDF skips.

### Tests

- Existing rendering tests should still pass.
- Optional: add a log-capture test only if it is not brittle.

### Compatibility

Visual output may change slightly but should become more predictable in PDFs.

## Priority 6: Remove Transitive Commons IO Dependency

Phase: 1

### Problem

`ImageUtil` uses `org.apache.commons.io.IOUtils`, but `commons-io` is not declared directly in `lib/pom.xml`.

### Implementation

- Replace `IOUtils.toByteArray(is)` with `is.readAllBytes()`.
- Remove the `IOUtils` import.
- Do not add a direct dependency unless there is another reason to keep Commons IO.
- No change is needed for the null handling in `readBytes(String, Class<?>)`: the null check already throws `Md2PdfException`, and the outer catch only handles `IOException`.

### Tests

- Add or extend tests for `ImageUtil.asDataUrl(...)`.
- Verify resource, URL, and byte-array conversion paths.

### Compatibility

No API change. Reduces accidental dependency coupling.

## Priority 7: Markdown Options

Phase: 4

### Problem

The engine always enables tables and hard HTML soft breaks. Users may want other CommonMark extensions or renderer behavior.

### API Options

Conservative first step:

```java
Md2PdfEngine engine = Md2PdfEngine.builder()
    .tables(true)
    .softbreak("<br />\n")
    .build();
```

Later:

```java
.extensions(List.of(TablesExtension.create(), AutolinkExtension.create()))
```

### Implementation

- Introduce a small builder for `Md2PdfEngine`.
- Keep `new Md2PdfEngine()` as the default.
- Avoid exposing too much CommonMark internals until there is a clear need.

### Tests

- Verify default behavior is unchanged.
- Verify a custom softbreak setting changes generated HTML.

### Compatibility

Additive only.

### Implementation Result

`Md2PdfEngine.builder()` now supports `tables(boolean)` and `softbreak(String)`. The default constructor keeps table support enabled and soft breaks rendered as `<br />\n`.

## Priority 8: PDF Metadata

Phase: 4

### Problem

PDF metadata is useful for generated reports but currently not exposed.

### API

```java
.title("Quarterly Report")
.author("Alipsa")
.subject("Sales")
```

or:

```java
.metadata(metadata -> metadata
    .title("Quarterly Report")
    .author("Alipsa"))
```

### Implementation

- Check whether OpenHTMLtoPDF exposes producer/title hooks directly through `PdfRendererBuilder`.
- If not, use a custom `PDDocument` and set metadata before/after rendering if supported cleanly.

### Tests

- Render PDF and inspect metadata with PDFBox.

### Compatibility

Additive only.

### Implementation Result

PDF metadata is available through fluent `Job` methods: `.title(...)`, `.author(...)`, `.subject(...)`, and `.producer(...)`. Metadata is applied after rendering using PDFBox so it is preserved reliably in the final PDF.

## Priority 9: MathML Default Font Warning Cleanup

Phase: 2

### Problem

MathML elements inherit the default body font stack:

```css
font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
```

OpenHTMLtoPDF's `MathMLDrawer` tries to find each inherited family in declared `@font-face` rules and logs warnings when it cannot. These warnings are noisy for normal users and are not caused by invalid document input.

### Non-Goal

Do not auto-discover or auto-add system fonts. Mapping names like `-apple-system`, `Segoe UI`, `Roboto`, or `Arial` to actual `.ttf` files is platform-specific and brittle. Bundling a default font is also a product decision because it affects licensing, artifact size, and visual output.

### Implementation

- Add a focused test that renders MathML with the default stylesheet and captures or observes the current warning behavior if practical.
- Experiment with default CSS changes that prevent MathML from inheriting the browser-style body font stack without degrading normal Markdown output.
- Candidate CSS changes:
  - add a MathML-specific `math { font-family: serif; }` rule;
  - simplify the default body font family to PDF-safe generic families;
  - remove the default body font family and let OpenHTMLtoPDF choose its default font.
- Keep explicit `.font(file, family)` as the supported way for users to embed and select real fonts when exact typography matters.

### Tests

- Render the existing MathML test document.
- Verify MathML still renders as a Form XObject.
- Verify the noisy missing-font warnings are reduced or eliminated, if log capture can be done without brittle test setup.

### Compatibility

Default visual output may change slightly if the default font stack changes. Prefer the smallest CSS adjustment that removes MathML warning noise while preserving readable default output.

### Implementation Result

The default body font stack was removed so MathML no longer inherits the browser-oriented family list. This reduces the warning noise to OpenHTMLtoPDF/JEuclid's internal generic `serif` default. Fully eliminating that final warning would require registering a real font for MathML, either explicitly through `.font(...)` or through a future deliberate bundled-font feature.

## Suggested Execution Order

1. Phase 1: fix stream ownership, clean default CSS warnings, remove Commons IO usage.
2. Phase 2: add `addCss(...)`, refine header/footer page margin handling, add page margin helpers, clean up MathML default font warnings.
3. Phase 3: add base URI support and tests for relative resources.
4. Phase 4: add markdown engine builder options and PDF metadata support.

## Documentation Updates

Update README with examples for:

- Rendering from a Markdown file with relative images.
- Default styling plus overrides using `.addCss(...)`.
- Full stylesheet replacement using `.css(...)`.
- Headers/footers with `page-number` and `total-pages`.
- Page margins when using headers/footers.

## Release Notes

Mention:

- `toPdf(OutputStream)` no longer closes caller-owned streams.
- `markdown(Path/File)` now resolves relative PDF resources from the Markdown file directory.
- New `.addCss(...)`, `.baseUri(...)`, `.basePath(...)`, and page margin helpers.
