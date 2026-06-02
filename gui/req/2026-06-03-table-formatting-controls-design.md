# Table Formatting Controls — Design Spec

**Date:** 2026-06-03
**Status:** Approved

## Summary

Add a first-class "Tables" section to the `StyleEditorPanel` visual editor, backed by six new fields in `StyleProfile`. The fields round-trip through `toCss()` / `fromCss()` and through the `.properties` persistence layer.

---

## 1. `StyleProfile` model

Six new fields added to the `// Tables` section:

```java
private boolean tableBorderCollapse   = true;
private String  tableHeaderBackground = "#f6f8fa";
private String  tableHeaderColor      = "#000000";
private String  tableBorderColor      = "#dfe2e5";
private boolean tableStripeEnabled    = false;
private String  tableStripeBackground = "#f0f3f6";
```

Getters and setters follow the existing naming convention. All six are serialised by `saveToProperties()` and deserialised by `fromProperties()` using the field names as keys.

---

## 2. `toCss()` output

Four new CSS rules appended after the blockquote block, before `@page`:

```css
table { border-collapse: collapse; width: 100%; }
th { background-color: #f6f8fa; color: #000000; }
td, th { border: 1px solid #dfe2e5; padding: 6px 13px; }
tr:nth-child(even) { background-color: #f0f3f6; }
```

Rules:
- `border-collapse` emits `collapse` or `separate` based on `tableBorderCollapse`.
- `width: 100%` is always emitted (no backing field).
- `padding: 6px 13px` on `td, th` is always emitted (no backing field).
- The `tr:nth-child(even)` rule is **omitted** when `tableStripeEnabled` is `false`.

---

## 3. `fromCss()` parsing

Four new cases in the selector `switch`:

| Selector | Declarations parsed |
|---|---|
| `table` | `border-collapse` → `tableBorderCollapse` (`collapse` = true, anything else = false) |
| `th` | `background-color` → `tableHeaderBackground`; `color` → `tableHeaderColor` |
| `td, th` | `border` shorthand → last whitespace-separated token → `tableBorderColor` |
| `tr:nth-child(even)` | `background-color` → `tableStripeBackground`; sets `tableStripeEnabled = true` |

`normalizeSelector` already lowercases and collapses whitespace, so both selectors match exactly as generated. The bare `td` selector (if present in user raw CSS) falls through to `extraCss` — only the compound `td, th` selector is owned.

---

## 4. `StyleEditorPanel` UI

New "Tables" section inserted between "Block Quotes" and "Page (PDF)":

```
Border collapse:      [✓ Collapse cell borders]         ← CheckBox
Header background:    [colour picker]
Header text colour:   [colour picker]
Cell border colour:   [colour picker]
Striped rows:         [✓ checkbox]  [colour picker]     ← picker disabled when unchecked
```

Implementation details:
- Stripe colour picker is in a `HBox` with the stripe checkbox.
- Picker is disabled (`setDisable(true/false)`) via a listener on `stripeCheckBox.selectedProperty()`.
- All five controls call `fireChange()` via `wireChangeListeners()`.
- `applyProfile()` reads all six fields from the profile into controls.
- `buildProfile()` writes all six controls back to a new profile.

---

## 5. Built-in profiles

`StyleProfileManager.buildDefault()`, `buildMinimal()`, and `buildPrint()` do not explicitly set table fields; they inherit the `StyleProfile` constructor defaults (border collapse on, no stripes). No changes needed to `StyleProfileManager`.

---

## 6. Tests (`StyleProfileTest`)

Four new tests:

1. **`tableCssEmittedByDefault`** — default `StyleProfile` `toCss()` contains `border-collapse`, `th {`, `td, th {`; does **not** contain `nth-child` (stripes off by default).
2. **`tableStripeAppearsWhenEnabled`** — set `tableStripeEnabled = true`, verify `toCss()` contains `tr:nth-child(even)`.
3. **`tableRoundTripViaFromCss`** — set all six table fields, round-trip through `toCss()` → `fromCss()`, assert all six values are preserved.
4. **`tableRoundTripViaProperties`** — set all six table fields, round-trip through `saveToProperties()` → `fromProperties()`, assert all six values are preserved.

**Existing test updated:** `unknownSelectorsPreservedInExtraCss` currently uses `table` and `th` as the unknown selectors. Since those are now first-class, that test is revised to use a genuinely unknown selector (e.g. `caption`) to verify the `extraCss` fallback still works.

---

## Files changed

| File | Change |
|---|---|
| `gui/src/main/java/.../model/StyleProfile.java` | +6 fields, getters/setters, `toCss()`, `fromCss()`, `saveToProperties()`, `fromProperties()` |
| `gui/src/main/java/.../gui/StyleEditorPanel.java` | +5 controls, "Tables" section, `applyProfile()`, `buildProfile()`, `wireChangeListeners()` |
| `gui/src/test/java/.../gui/StyleProfileTest.java` | +4 new tests, update `unknownSelectorsPreservedInExtraCss` |
