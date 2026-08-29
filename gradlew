#!/bin/sh
# Gradle wrapper script
exec java -Xmx64m -Xms64m \
    -Dorg.gradle.appname=gradlew \
    -classpath "$( dirname "$0" )/gradle/wrapper/gradle-wrapper.jar" \
    org.gradle.wrapper.GradleWrapperMain "$@"
