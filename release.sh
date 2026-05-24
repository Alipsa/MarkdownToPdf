#!/usr/bin/env bash
set -e
BASEDIR="$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
if command -v jdk21; then
  . jdk21
fi
mvn -Prelease clean site deploy
echo "creating the viewer zip"
cd "$BASEDIR/gui"
source ./createApp.sh skipInstructions
echo "build the runtime fatjar"
cd "$BASEDIR/lib"
mvn -DskipTests -Pfatjar -q package
echo "Released and ready for manual release at github!"
echo "Upload the following"
echo "- lib/target/md2pdf-[version]-javadoc.jar"
echo "- lib/target/md2pdf-[version]-jar-with-dependencies.jar"
echo "- gui/target/journo-viewer.zip"
echo "- gui/MarkdownToPdf.xml"