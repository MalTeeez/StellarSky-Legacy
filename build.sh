#!/usr/bin/env bash
# Builds Stellar Sky, which also contains StellarAPI (see build.gradle.kts).
set -euo pipefail
cd "$(dirname "$0")"

# gtnhgradle 2.x needs Gradle itself to run on a JDK 25+ JVM. The mod code still
# targets JVM 8. Override with STELLAR_JAVA_HOME.
jdk_major() {
    "$1/bin/java" -version 2>&1 | sed -n 's/.*version "\([0-9]*\).*/\1/p'
}

if [ "$(jdk_major "${JAVA_HOME:-/nonexistent}")" -lt 25 ] 2>/dev/null; then
    for candidate in "${STELLAR_JAVA_HOME:-}" ~/.gradle/jdks/*25* ~/.gradle/jdks/*21*; do
        if [ -x "$candidate/bin/java" ] && [ "$(jdk_major "$candidate")" -ge 25 ] 2>/dev/null; then
            export JAVA_HOME="$candidate"
            break
        fi
    done
fi

if [ "$(jdk_major "${JAVA_HOME:-/nonexistent}")" -lt 25 ] 2>/dev/null; then
    echo "build.sh: Gradle needs a JDK 25+ JVM for gtnhgradle 2.x; set STELLAR_JAVA_HOME." >&2
    exit 1
fi

exec ./gradlew build "$@"
