# MarkdownToPdf Runtime Release History
(Note, dates are in yyyy-MM-dd format)

## 0.1.1 (2026-08-11)
- **Breaking:** renamed `Md2PdfEngine.Job` to `Md2PdfEngine.Renderer`. Code that named the
  type explicitly must be updated; the fluent `engine.markdown(...)` chain is unchanged.
- Updated dependencies
  - openhtmltopdf 1.1.37 -> 1.1.70
  - commonmark 0.28.0 -> 0.30.0
  - jsoup 1.22.2 -> 1.23.1

## 0.1.0 (2026-05-17)
- Initial release