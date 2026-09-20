#!/usr/bin/env sh
set -eu

classes="${TMPDIR:-/tmp}/appointment-report-mailer-classes"
mkdir -p "$classes"
javac -d "$classes" $(find src/main/java -name '*.java')
java -cp "$classes" dev.healthreport.ReportServiceApplication
