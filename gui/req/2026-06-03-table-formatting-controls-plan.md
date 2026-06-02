# Table Formatting Controls — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a "Tables" section to the StyleEditorPanel with 5 controls backed by 6 new fields in StyleProfile, with full CSS round-trip and properties persistence.

**Architecture:** New fields added to `StyleProfile`; `toCss()` emits 3–4 new CSS rules; `fromCss()` handles 4 new selectors; `StyleEditorPanel` gets a new grid section. All changes follow existing patterns exactly.

**Tech Stack:** Java 21, JavaFX 23, JUnit 5, Maven. Run tests with `mvn test -pl gui -Dtest=StyleProfileTest`.

---

## File Map

| File | Change |
|---|---|
| `gui/src/main/java/se/alipsa/md2pdf/model/StyleProfile.java` | +6 fields, getters/setters, `toCss()`, `fromCss()`, `saveToProperties()`, `fromProperties()` |
| `gui/src/main/java/se/alipsa/md2pdf/gui/StyleEditorPanel.java` | +6 field declarations, `buildTableGrid()`, section wired into layout, `configureControls()`, `wireChangeListeners()`, `applyProfile()`, `buildProfile()` |
| `gui/src/test/java/test/alipsa/md2pdf/gui/StyleProfileTest.java` | +4 new tests, update `unknownSelectorsPreservedInExtraCss` |

---

## Task 1: StyleProfile — fields, toCss(), and toCss() tests

**Files:**
- Modify: `gui/src/main/java/se/alipsa/md2pdf/model/StyleProfile.java`
- Test: `gui/src/test/java/test/alipsa/md2pdf/gui/StyleProfileTest.java`

- [ ] **Step 1: Write two failing toCss() tests**

Add to the end of `StyleProfileTest` (before the closing `}`):

```java
@Test
void tableCssEmittedByDefault() {
  StyleProfile p = new StyleProfile("Test");
  String css = p.toCss();
  assertTrue(css.contains("border-collapse"), "CSS must contain border-collapse");
  assertTrue(css.contains("th {"), "CSS must contain th rule");
  assertTrue(css.contains("td, th {"), "CSS must contain td, th rule");
  assertFalse(css.contains("nth-child"), "CSS must not contain stripe rule when stripes disabled");
}

@Test
void tableStripeAppearsWhenEnabled() {
  StyleProfile p = new StyleProfile("Test");
  p.setTableStripeEnabled(true);
  assertTrue(
      p.toCss().contains("tr:nth-child(even)"),
      "CSS must contain stripe rule when tableStripeEnabled=true");
}
```

- [ ] **Step 2: Run tests to confirm they fail**

```bash
mvn test -pl gui -Dtest=StyleProfileTest#tableCssEmittedByDefault+tableStripeAppearsWhenEnabled
```

Expected: FAIL — `setTableStripeEnabled` method not found.

- [ ] **Step 3: Add the 6 table fields to StyleProfile**

In `StyleProfile.java`, locate the `// Passthrough CSS rules` comment (line ~52). Insert above it:

```java
// Tables
private boolean tableBorderCollapse   = true;
private String  tableHeaderBackground = "#f6f8fa";
private String  tableHeaderColor      = "#000000";
private String  tableBorderColor      = "#dfe2e5";
private boolean tableStripeEnabled    = false;
private String  tableStripeBackground = "#f0f3f6";
```

- [ ] **Step 4: Add getters and setters for the 6 table fields**

Add after the `setExtraCss` method (before `@Override public String toString()`):

```java
public boolean isTableBorderCollapse() { return tableBorderCollapse; }
public void setTableBorderCollapse(boolean tableBorderCollapse) { this.tableBorderCollapse = tableBorderCollapse; }

public String getTableHeaderBackground() { return tableHeaderBackground; }
public void setTableHeaderBackground(String tableHeaderBackground) { this.tableHeaderBackground = tableHeaderBackground; }

public String getTableHeaderColor() { return tableHeaderColor; }
public void setTableHeaderColor(String tableHeaderColor) { this.tableHeaderColor = tableHeaderColor; }

public String getTableBorderColor() { return tableBorderColor; }
public void setTableBorderColor(String tableBorderColor) { this.tableBorderColor = tableBorderColor; }

public boolean isTableStripeEnabled() { return tableStripeEnabled; }
public void setTableStripeEnabled(boolean tableStripeEnabled) { this.tableStripeEnabled = tableStripeEnabled; }

public String getTableStripeBackground() { return tableStripeBackground; }
public void setTableStripeBackground(String tableStripeBackground) { this.tableStripeBackground = tableStripeBackground; }
```

- [ ] **Step 5: Update toCss() to emit table rules**

In `toCss()`, locate the blockquote block ending `"}\n"` (just before `"@page {\n"`). Replace the section from the blockquote closing brace to the `@page` rule:

```java
// OLD — the closing of the blockquote block runs straight into @page:
      + "  color: "
      + blockquoteTextColor
      + ";\n"
      + "}\n"
      + "@page {\n"

// NEW — insert table rules in between:
      + "  color: "
      + blockquoteTextColor
      + ";\n"
      + "}\n"
      + "table { border-collapse: "
      + (tableBorderCollapse ? "collapse" : "separate")
      + "; width: 100%; }\n"
      + "th { background-color: "
      + tableHeaderBackground
      + "; color: "
      + tableHeaderColor
      + "; }\n"
      + "td, th { border: 1px solid "
      + tableBorderColor
      + "; padding: 6px 13px; }\n"
      + (tableStripeEnabled
          ? "tr:nth-child(even) { background-color: " + tableStripeBackground + "; }\n"
          : "")
      + "@page {\n"
```

- [ ] **Step 6: Run the two new tests to confirm they pass**

```bash
mvn test -pl gui -Dtest=StyleProfileTest#tableCssEmittedByDefault+tableStripeAppearsWhenEnabled
```

Expected: PASS for both.

- [ ] **Step 7: Run the full test suite to confirm no regressions**

```bash
mvn test -pl gui -Dtest=StyleProfileTest
```

Expected: all previously-passing tests still pass (the `unknownSelectorsPreservedInExtraCss` test is not broken yet because `fromCss()` hasn't changed).

- [ ] **Step 8: Format and commit**

```bash
mvn spotless:apply
git add gui/src/main/java/se/alipsa/md2pdf/model/StyleProfile.java \
        gui/src/test/java/test/alipsa/md2pdf/gui/StyleProfileTest.java
git commit -m "feat: add table fields to StyleProfile and emit table CSS"
```

---

## Task 2: StyleProfile — fromCss() parsing

**Files:**
- Modify: `gui/src/main/java/se/alipsa/md2pdf/model/StyleProfile.java`
- Test: `gui/src/test/java/test/alipsa/md2pdf/gui/StyleProfileTest.java`

- [ ] **Step 1: Write the failing fromCss() round-trip test**

Add to `StyleProfileTest`:

```java
@Test
void tableRoundTripViaFromCss() {
  StyleProfile original = new StyleProfile("Test");
  original.setTableBorderCollapse(false);
  original.setTableHeaderBackground("#eeeeee");
  original.setTableHeaderColor("#111111");
  original.setTableBorderColor("#aaaaaa");
  original.setTableStripeEnabled(true);
  original.setTableStripeBackground("#dddddd");

  StyleProfile parsed = StyleProfile.fromCss(original.toCss());

  assertFalse(parsed.isTableBorderCollapse(), "border-collapse: separate must round-trip");
  assertEquals("#eeeeee", parsed.getTableHeaderBackground());
  assertEquals("#111111", parsed.getTableHeaderColor());
  assertEquals("#aaaaaa", parsed.getTableBorderColor());
  assertTrue(parsed.isTableStripeEnabled(), "stripe enabled must round-trip");
  assertEquals("#dddddd", parsed.getTableStripeBackground());
}
```

- [ ] **Step 2: Run the test to confirm it fails**

```bash
mvn test -pl gui -Dtest=StyleProfileTest#tableRoundTripViaFromCss
```

Expected: FAIL — fields keep their defaults after `fromCss()` because the selectors fall into `extraCss`.

- [ ] **Step 3: Add four new apply methods to StyleProfile**

Add after `applyPage` (before the `// Properties persistence` comment):

```java
private static void applyTable(StyleProfile p, Map<String, String> decls) {
  if (decls.containsKey("border-collapse")) {
    p.tableBorderCollapse = "collapse".equalsIgnoreCase(decls.get("border-collapse").trim());
  }
}

private static void applyTh(StyleProfile p, Map<String, String> decls) {
  if (decls.containsKey("background-color")) p.tableHeaderBackground = decls.get("background-color");
  if (decls.containsKey("color")) p.tableHeaderColor = decls.get("color");
}

private static void applyTdTh(StyleProfile p, Map<String, String> decls) {
  if (decls.containsKey("border")) {
    String[] parts = decls.get("border").trim().split("\\s+");
    if (parts.length > 0) p.tableBorderColor = parts[parts.length - 1];
  }
}

private static void applyStripe(StyleProfile p, Map<String, String> decls) {
  p.tableStripeEnabled = true;
  if (decls.containsKey("background-color")) p.tableStripeBackground = decls.get("background-color");
}
```

- [ ] **Step 4: Add four new cases to the switch in fromCss()**

In `fromCss()`, locate the switch statement inside the `for (String[] block : blocks)` loop. Add four cases after the `case "blockquote"` line:

```java
case "table" -> applyTable(p, decls);
case "th" -> applyTh(p, decls);
case "td, th" -> applyTdTh(p, decls);
case "tr:nth-child(even)" -> applyStripe(p, decls);
```

- [ ] **Step 5: Update the existing unknownSelectorsPreservedInExtraCss test**

`table` and `th` are now first-class selectors and will no longer land in `extraCss`. Replace the test body:

```java
@Test
void unknownSelectorsPreservedInExtraCss() {
  String css = "body { color: #333; }\ncaption { font-style: italic; }";
  StyleProfile p = StyleProfile.fromCss(css);
  assertEquals("#333", p.getBodyColor());
  String extra = p.getExtraCss();
  assertTrue(extra.contains("caption"), "unknown caption selector must land in extraCss");
  String rebuilt = p.toCss();
  assertTrue(rebuilt.contains("caption"), "extraCss must appear in toCss() output");
}
```

- [ ] **Step 6: Run all StyleProfileTest tests**

```bash
mvn test -pl gui -Dtest=StyleProfileTest
```

Expected: all tests pass, including the updated `unknownSelectorsPreservedInExtraCss` and the new `tableRoundTripViaFromCss`.

- [ ] **Step 7: Format and commit**

```bash
mvn spotless:apply
git add gui/src/main/java/se/alipsa/md2pdf/model/StyleProfile.java \
        gui/src/test/java/test/alipsa/md2pdf/gui/StyleProfileTest.java
git commit -m "feat: parse table selectors in StyleProfile.fromCss()"
```

---

## Task 3: StyleProfile — properties persistence

**Files:**
- Modify: `gui/src/main/java/se/alipsa/md2pdf/model/StyleProfile.java`
- Test: `gui/src/test/java/test/alipsa/md2pdf/gui/StyleProfileTest.java`

- [ ] **Step 1: Write the failing properties round-trip test**

Add to `StyleProfileTest`:

```java
@Test
void tableRoundTripViaProperties() {
  StyleProfile original = new StyleProfile("Test");
  original.setTableBorderCollapse(false);
  original.setTableHeaderBackground("#eeeeee");
  original.setTableHeaderColor("#111111");
  original.setTableBorderColor("#aaaaaa");
  original.setTableStripeEnabled(true);
  original.setTableStripeBackground("#dddddd");

  Properties props = new Properties();
  original.saveToProperties(props);
  StyleProfile loaded = StyleProfile.fromProperties(props);

  assertFalse(loaded.isTableBorderCollapse());
  assertEquals("#eeeeee", loaded.getTableHeaderBackground());
  assertEquals("#111111", loaded.getTableHeaderColor());
  assertEquals("#aaaaaa", loaded.getTableBorderColor());
  assertTrue(loaded.isTableStripeEnabled());
  assertEquals("#dddddd", loaded.getTableStripeBackground());
}
```

Note: `Properties` must be imported — it is already imported in `StyleProfileTest` (used by the existing `propertiesRoundTrip` test).

- [ ] **Step 2: Run the test to confirm it fails**

```bash
mvn test -pl gui -Dtest=StyleProfileTest#tableRoundTripViaProperties
```

Expected: FAIL — table fields are not written/read from properties.

- [ ] **Step 3: Add 6 lines to saveToProperties()**

In `saveToProperties()`, add after the `props.setProperty("extraCss", ...)` line:

```java
props.setProperty("tableBorderCollapse", String.valueOf(tableBorderCollapse));
props.setProperty("tableHeaderBackground", tableHeaderBackground);
props.setProperty("tableHeaderColor", tableHeaderColor);
props.setProperty("tableBorderColor", tableBorderColor);
props.setProperty("tableStripeEnabled", String.valueOf(tableStripeEnabled));
props.setProperty("tableStripeBackground", tableStripeBackground);
```

- [ ] **Step 4: Add 6 lines to fromProperties()**

In `fromProperties()`, add after the `p.extraCss = props.getProperty("extraCss", "");` line:

```java
p.tableBorderCollapse = Boolean.parseBoolean(props.getProperty("tableBorderCollapse", "true"));
p.tableHeaderBackground = props.getProperty("tableHeaderBackground", p.tableHeaderBackground);
p.tableHeaderColor = props.getProperty("tableHeaderColor", p.tableHeaderColor);
p.tableBorderColor = props.getProperty("tableBorderColor", p.tableBorderColor);
p.tableStripeEnabled = Boolean.parseBoolean(props.getProperty("tableStripeEnabled", "false"));
p.tableStripeBackground = props.getProperty("tableStripeBackground", p.tableStripeBackground);
```

- [ ] **Step 5: Run all StyleProfileTest tests**

```bash
mvn test -pl gui -Dtest=StyleProfileTest
```

Expected: all tests pass, including `tableRoundTripViaProperties`.

- [ ] **Step 6: Format and commit**

```bash
mvn spotless:apply
git add gui/src/main/java/se/alipsa/md2pdf/model/StyleProfile.java \
        gui/src/test/java/test/alipsa/md2pdf/gui/StyleProfileTest.java
git commit -m "feat: persist table fields in StyleProfile properties"
```

---

## Task 4: StyleEditorPanel — Tables section

**Files:**
- Modify: `gui/src/main/java/se/alipsa/md2pdf/gui/StyleEditorPanel.java`

No unit tests for UI — validate manually by running the app and checking the Tables section renders and that the live preview updates as you change controls.

- [ ] **Step 1: Add 6 field declarations to StyleEditorPanel**

After the `// Blockquotes` field group (around line 56–57), add:

```java
// Tables
private final CheckBox    tableBorderCollapseCheckBox  = new CheckBox("Collapse cell borders");
private final ColorPicker tableHeaderBackgroundPicker  = new ColorPicker();
private final ColorPicker tableHeaderColorPicker       = new ColorPicker();
private final ColorPicker tableBorderColorPicker       = new ColorPicker();
private final CheckBox    tableStripeCheckBox          = new CheckBox();
private final ColorPicker tableStripeBackgroundPicker  = new ColorPicker();
```

- [ ] **Step 2: Add buildTableGrid() method**

Add after `buildBlockquoteGrid()`:

```java
private GridPane buildTableGrid() {
  GridPane g = grid();
  addRow(g, 0, "Border collapse", tableBorderCollapseCheckBox);
  addRow(g, 1, "Header background", tableHeaderBackgroundPicker);
  addRow(g, 2, "Header text colour", tableHeaderColorPicker);
  addRow(g, 3, "Cell border colour", tableBorderColorPicker);
  HBox stripeRow = row(tableStripeCheckBox, tableStripeBackgroundPicker);
  addRow(g, 4, "Striped rows", stripeRow);
  return g;
}
```

- [ ] **Step 3: Wire the Tables section into the layout**

In the constructor, in `content.getChildren().addAll(...)`, insert the Tables section between Block Quotes and Page:

```java
// OLD:
buildSection("Block Quotes", buildBlockquoteGrid()),
buildSection("Page (PDF)", buildPageGrid()));

// NEW:
buildSection("Block Quotes", buildBlockquoteGrid()),
buildSection("Tables", buildTableGrid()),
buildSection("Page (PDF)", buildPageGrid()));
```

- [ ] **Step 4: Configure the stripe picker's initial disabled state**

In `configureControls()`, add at the end of the method body:

```java
tableStripeBackgroundPicker.setDisable(true);
tableStripeCheckBox
    .selectedProperty()
    .addListener((o, ov, nv) -> tableStripeBackgroundPicker.setDisable(!nv));
```

- [ ] **Step 5: Wire change listeners for the 6 new controls**

In `wireChangeListeners()`, add at the end of the method body:

```java
tableBorderCollapseCheckBox.selectedProperty().addListener((o, ov, nv) -> fireChange());
tableHeaderBackgroundPicker.valueProperty().addListener((o, ov, nv) -> fireChange());
tableHeaderColorPicker.valueProperty().addListener((o, ov, nv) -> fireChange());
tableBorderColorPicker.valueProperty().addListener((o, ov, nv) -> fireChange());
tableStripeCheckBox.selectedProperty().addListener((o, ov, nv) -> fireChange());
tableStripeBackgroundPicker.valueProperty().addListener((o, ov, nv) -> fireChange());
```

- [ ] **Step 6: Update applyProfile() to populate the 6 controls**

In `applyProfile(StyleProfile p)`, add after the blockquote pickers block:

```java
tableBorderCollapseCheckBox.setSelected(p.isTableBorderCollapse());
tableHeaderBackgroundPicker.setValue(Color.web(p.getTableHeaderBackground()));
tableHeaderColorPicker.setValue(Color.web(p.getTableHeaderColor()));
tableBorderColorPicker.setValue(Color.web(p.getTableBorderColor()));
tableStripeCheckBox.setSelected(p.isTableStripeEnabled());
tableStripeBackgroundPicker.setDisable(!p.isTableStripeEnabled());
tableStripeBackgroundPicker.setValue(Color.web(p.getTableStripeBackground()));
```

- [ ] **Step 7: Update buildProfile() to read the 6 controls**

In `buildProfile()`, add after the blockquote pickers block:

```java
p.setTableBorderCollapse(tableBorderCollapseCheckBox.isSelected());
p.setTableHeaderBackground(toHex(tableHeaderBackgroundPicker.getValue()));
p.setTableHeaderColor(toHex(tableHeaderColorPicker.getValue()));
p.setTableBorderColor(toHex(tableBorderColorPicker.getValue()));
p.setTableStripeEnabled(tableStripeCheckBox.isSelected());
p.setTableStripeBackground(toHex(tableStripeBackgroundPicker.getValue()));
```

- [ ] **Step 8: Run the full test suite**

```bash
mvn test -pl gui
```

Expected: all tests pass (UI changes don't affect unit tests).

- [ ] **Step 9: Format and commit**

```bash
mvn spotless:apply
git add gui/src/main/java/se/alipsa/md2pdf/gui/StyleEditorPanel.java
git commit -m "feat: add Tables section to StyleEditorPanel"
```

---

## Task 5: Final verification

- [ ] **Step 1: Run the complete build**

```bash
mvn verify
```

Expected: BUILD SUCCESS, all tests pass, Spotless and SpotBugs clean.

- [ ] **Step 2: Build the fat-jar and smoke-test**

```bash
mvn install && mvn package -P fatjar -pl gui
```

Launch and manually verify:
- "Tables" section appears between "Block Quotes" and "Page (PDF)"
- "Collapse cell borders" checkbox is checked by default
- Stripe colour picker is disabled until "Striped rows" checkbox is ticked
- Changing any table control triggers a live preview refresh
- Save + reload a profile preserves all table settings

- [ ] **Step 3: Commit spec and plan alongside the feature**

```bash
git add gui/req/
git commit -m "docs: add table formatting controls spec and plan"
```
