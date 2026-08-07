#!/usr/bin/env zsh

JV=21

DIR="${0:A:h}"
cd "$DIR" || exit

# MD2PDF_JAVA_HOME, if set to a JDK home containing bin/java, overrides the JDK
# on PATH — useful when the default JVM isn't JavaFX-bundled but a suitable one
# is installed elsewhere (e.g. a non-default sdkman candidate). md2pdf-install.sh
# records the JDK it validated in md2pdf.env; an MD2PDF_JAVA_HOME already set in
# the environment (e.g. exported from ~/.zshrc) wins over it.
if [[ -z "$MD2PDF_JAVA_HOME" && -f "$DIR/md2pdf.env" ]]; then
  source "$DIR/md2pdf.env"
fi

JAVA_BIN="java"
if [[ -n "$MD2PDF_JAVA_HOME" && -x "$MD2PDF_JAVA_HOME/bin/java" ]]; then
  JAVA_BIN="$MD2PDF_JAVA_HOME/bin/java"
fi

if command -v "$JAVA_BIN" ; then
	javaVersion=$("$JAVA_BIN" -version 2>&1 | head -1 | cut -d'"' -f2 | sed '/^1\./s///' | cut -d'.' -f1)
	if [[ (( $javaVersion -ge $JV )) ]]; then
	  echo "Java $javaVersion OK"
	else
	  echo "Java version $JV or greater required, trying to switch with sdkman"
	  if [[ -f "$HOME/.sdkman/bin/sdkman-init.sh" ]]; then
	    source "$HOME/.sdkman/bin/sdkman-init.sh"
      jdk=$(sdk list java | grep installed | grep -E "$JV." | head -n 1 | cut -d '|' -f 6 | sed 's/^ *//g')
      jdk=$(echo "$jdk" | xargs)
      sdk use java "${jdk}"
      JAVA_BIN="java"
      javaVersion=$("$JAVA_BIN" -version 2>&1 | head -1 | cut -d'"' -f2 | sed '/^1\./s///' | cut -d'.' -f1)
      if [[ (( $javaVersion -ge $JV )) ]]; then
      	  echo "Java $javaVersion OK"
      else
        echo "Failed to switch to java $JV"
	      exit 1
	    fi
	  fi
	fi
else
  echo "Java not found in path"
  read -r
  exit 1
fi

JAR=$(ls -1 -t MarkdownToPdf-*-with-dependencies.jar 2>/dev/null | head -1)
if [[ -z "$JAR" ]]; then
  echo "No MarkdownToPdf-*-with-dependencies.jar found in $DIR"
  exit 1
fi
"$JAVA_BIN" -Xmx8g -Xdock:name=MarkdownToPdf -Xdock:icon=./Contents/Resources/md2pdf.icns -jar "./$JAR"

