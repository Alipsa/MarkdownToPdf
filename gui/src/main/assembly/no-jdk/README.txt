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
