# md2pdf — MarkdownToPdf library module
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/se.alipsa/md2pdf/badge.svg)](https://maven-badges.herokuapp.com/maven-central/se.alipsa/md2pdf)
[![javadoc](https://javadoc.io/badge2/se.alipsa/md2pdf/javadoc.svg)](https://javadoc.io/doc/se.alipsa/md2pdf)

This is the core library module. See [the main README](../README.md) for full API
documentation and usage examples.

## Spring Boot integration

Inject the engine as a singleton via `@PostConstruct`:

```java
import se.alipsa.md2pdf.Md2PdfEngine;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

@Service
public class ReportService {

    private Md2PdfEngine engine;

    @PostConstruct
    protected void initialize() {
        engine = new Md2PdfEngine();
    }

    public byte[] generateReport(String markdown) throws Md2PdfException {
        return engine.markdown(markdown).toPdf();
    }

    public void generateReport(String markdown, OutputStream out) throws Md2PdfException {
        engine.markdown(markdown).toPdf(out);
    }
}
```

## Building

Prerequisites:
1. JDK 21 or later
2. Maven 3.9.9 or later

```bash
mvn install
```
