#!/usr/bin/env bash
set -euo pipefail

DIR="$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" > /dev/null 2>&1 && pwd )"

if command -v java ; then
	javaVersion=$(java -version 2>&1 | head -1 | cut -d'"' -f2 | sed '/^1\./s///' | cut -d'.' -f1)
	if [[ (( $javaVersion -ge 25 )) ]]; then
	  echo "Java $javaVersion OK"
	else
	  echo "Java version 25 or greater required"
	  exit 1
	fi
else
  echo "Java not found in path"
  exit 1
fi

cd "$DIR/.."
mvn clean package
