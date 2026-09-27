#!/usr/bin/env bash
# Dev-only: runs the VFX harness (forge/run/vfxdemo.txt) and waits until the last screenshot exists.
# Usage: scripts/vfx-demo.sh <last-screenshot-file-name> [world]
cd "$(dirname "$0")/.." || exit 1
LAST="$1"
WORLD="${2:-New World (4)}"
for p in $(tasklist //FI "IMAGENAME eq java.exe" //FO CSV //NH 2>/dev/null | awk -F'","' '{print $2}'); do
  wmic process where processid="$p" get commandline 2>/dev/null | grep -q "devlaunchinjector\|TransformerRuntime" && taskkill //F //PID "$p" >/dev/null 2>&1
done
rm -f forge/run/screenshots/vfx_*.png
ROTASUTILS_VFX_DEMO=1 ./gradlew :forge:runClient --offline --args="--quickPlaySingleplayer '$WORLD'" > build/vfx-demo.log 2>&1 &
sleep 30
until [ -f "forge/run/screenshots/$LAST" ] || grep -aqE "Crash report|BUILD FAILED" build/vfx-demo.log; do sleep 4; done
sleep 2
for p in $(tasklist //FI "IMAGENAME eq java.exe" //FO CSV //NH 2>/dev/null | awk -F'","' '{print $2}'); do
  wmic process where processid="$p" get commandline 2>/dev/null | grep -q "devlaunchinjector\|TransformerRuntime" && taskkill //F //PID "$p" >/dev/null 2>&1
done
ls forge/run/screenshots | grep vfx_
grep -aE "Crash report|Unknown or incomplete command|Incorrect argument" build/vfx-demo.log | head -5
