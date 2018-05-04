#!/bin/sh
cd betterrandom
mvn jacoco:prepare-agent test jacoco:report -e
STATUS=$?
if [ "$STATUS" = 0 ] && [ "$TRAVIS" = "true" ]; then
  mvn coveralls:report
  STATUS=$?
fi
cd ..
exit "$STATUS"