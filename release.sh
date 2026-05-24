#!/usr/bin/env bash
set -e
BASEDIR="$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
if command -v jdk21; then
  . jdk21
fi
mvn -Prelease clean site deploy
echo "creating the gui zip"
cd "$BASEDIR/gui"
source ./createApp.sh skipInstructions
echo "build the runtime fatjar"
cd "$BASEDIR"
mvn install -DskipTests && mvn package -P fatjar -pl gui -DskipTests
echo "Released and ready for manual release at github!"
echo "Upload the following"
echo "- lib/target/md2pdf-[version]-javadoc.jar"
echo "- gui/target/MarkdownToPdf-[version]-jar-with-dependencies.jar"