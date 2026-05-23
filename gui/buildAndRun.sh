#!/usr/bin/env bash

if command -v jdk21; then
  source jdk21
fi

pushd .. || exit
mvn -Pfatjar -DskipTests install || exit 1
popd || exit

jarFilePath=$(ls -t target/MarkdownToPdf-*jar-with-dependencies.jar)
java -Xmx8g -jar "$jarFilePath"