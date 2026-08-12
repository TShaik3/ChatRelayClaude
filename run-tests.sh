#!/usr/bin/env bash
# Compiles the app and its test suite, then runs every test via the JUnit 5
# console launcher. No Maven/Gradle needed -- lib/junit-platform-console-standalone-*.jar
# is the only dependency, downloaded once from Maven Central.
set -euo pipefail
cd "$(dirname "$0")"

JUNIT_JAR=$(ls lib/junit-platform-console-standalone-*.jar | head -1)

rm -rf out test-out
mkdir -p out test-out

echo "Compiling main sources..."
javac -d out $(find src -name '*.java')

echo "Compiling tests..."
javac -cp "out:${JUNIT_JAR}" -d test-out $(find test -name '*.java')

echo "Running tests..."
java -jar "${JUNIT_JAR}" execute -cp "out:test-out" --scan-classpath=test-out --details=tree "$@"
