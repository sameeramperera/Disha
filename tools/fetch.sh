#!/usr/bin/env bash
# Keeps resuming each download until it is complete and valid.
cd "$(dirname "$0")"
get() { # file url
  until [ -f "$1.ok" ]; do
    curl -sSL --http1.1 -C - --connect-timeout 20 --speed-time 60 --speed-limit 100 -o "$1" "$2"
    if unzip -tq "$1" >/dev/null 2>&1; then touch "$1.ok"; echo "done $1 $(du -h "$1" | cut -f1)"; else sleep 3; fi
  done
}
get ecj.jar https://repo1.maven.org/maven2/org/eclipse/jdt/ecj/3.33.0/ecj-3.33.0.jar
get bt.zip  https://dl.google.com/android/repository/build-tools_r34-linux.zip
get pf.zip  https://dl.google.com/android/repository/platform-34-ext7_r03.zip
unzip -q -o bt.zip && unzip -q -o pf.zip && rm -f bt.zip pf.zip && echo "ALL DONE" && ls
