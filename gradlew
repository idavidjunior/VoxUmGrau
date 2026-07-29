#!/bin/sh
DIRNAME="$(dirname "$0")"
APP_HOME="$DIRNAME"
CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
java -Xmx64m -Xms64m -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
