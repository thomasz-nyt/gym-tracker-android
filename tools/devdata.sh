#!/usr/bin/env bash
#
# Snapshot and restore REP's on-device data during development.
#
#   tools/devdata.sh save [file]      # default: dev-data.tar (gitignored)
#   tools/devdata.sh load [file]
#
# READ THIS FIRST, because it is usually the answer:
#
#   You almost certainly do not need to uninstall.
#
# `./gradlew :app:installDebug` runs `adb install -r`, which replaces the APK and **keeps the
# app's data**. Two things in this repo already make that reliable:
#
#   - `debug.keystore` is checked in and wired up in AndroidApplicationConventionPlugin, so
#     every build on every machine is signed identically. A signature mismatch is the usual
#     reason Android refuses to update in place and forces an uninstall.
#   - The Room builder in DataModule lists explicit migrations and **never calls
#     `fallbackToDestructiveMigration()`**. Room will therefore never silently wipe your
#     workouts to fit a new schema.
#
# So if you are uninstalling regularly, the cause is usually one of:
#
#   1. A schema change with no migration. Room throws at startup rather than wiping, and
#      uninstalling is the quick escape. The real fix is to write the migration — the chain is
#      at GymTrackerDatabase.MIGRATION_1_2 .. MIGRATION_6_7, currently at version 7.
#   2. Installing a build signed with a different key — e.g. Android Studio's own
#      ~/.android/debug.keystore from before the checked-in one landed. Uninstall once, then
#      it stays fixed.
#
# This script is for the times you genuinely do have to wipe: testing a migration from a real
# database, testing first-run behaviour, or switching between branches whose schemas disagree.
#
# It is NOT a backup of anything precious. `android:allowBackup="false"` is set, there is no
# sync until M2 (US-07..US-11), and a tar in your working directory is not a backup strategy.
set -euo pipefail

PACKAGE="com.gymtracker"
ARCHIVE="${2:-dev-data.tar}"
ADB="${ADB:-adb}"

die() { echo "error: $*" >&2; exit 1; }

installed() { "$ADB" shell pm list packages | grep -q "^package:${PACKAGE}$"; }

case "${1:-}" in
  save)
    installed || die "$PACKAGE is not installed on the device."
    # Stop first so Room is not mid-write. The -wal and -shm files below are why: a snapshot
    # of the .db alone can be missing everything logged since the last checkpoint.
    "$ADB" shell am force-stop "$PACKAGE"
    "$ADB" exec-out run-as "$PACKAGE" tar cf - databases files/datastore > "$ARCHIVE"
    echo "saved $(du -h "$ARCHIVE" | cut -f1) to $ARCHIVE"
    tar tf "$ARCHIVE" | sed 's/^/  /'
    ;;

  load)
    [ -f "$ARCHIVE" ] || die "no such archive: $ARCHIVE"
    installed || die "$PACKAGE is not installed. Run ./gradlew :app:installDebug first."
    "$ADB" shell am force-stop "$PACKAGE"
    # Via /data/local/tmp because adb push cannot write into an app's private directory, and
    # extracting through run-as is what gives the files the app's own uid.
    "$ADB" push "$ARCHIVE" /data/local/tmp/rep-devdata.tar >/dev/null
    "$ADB" shell run-as "$PACKAGE" tar xf /data/local/tmp/rep-devdata.tar
    "$ADB" shell rm -f /data/local/tmp/rep-devdata.tar
    echo "restored $ARCHIVE into $PACKAGE"
    ;;

  *)
    sed -n '2,40p' "$0" | sed 's/^# \{0,1\}//'
    exit 1
    ;;
esac
