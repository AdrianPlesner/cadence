# Cadence

Android app for keeping track of repeated tasks and when they were last carried out. Tasks live in groups, and every
device in a group keeps a full copy of the group's data. Devices sync directly with each other over the local network;
there is no server.

## How sync works

- Every edit is a row in an append-only change log, stamped with a hybrid logical clock and the id of the device that
  made it. Live tables are derived from the log with row-level last-writer-wins.
- Devices advertise a small HTTP server over DNS-SD (`_cadence._tcp`) while the app is in the foreground and exchange
  the changes the other side has not seen. Any device relays changes it received from a third device.
- All traffic is encrypted with a key derived from the group secret. The secret travels in the invite QR code, so
  holding it is what makes a device a member. Membership itself is synced data, so a device added on one phone appears
  on all of them.

## Building

    ./gradlew :app:assembleDebug
    ./gradlew :app:testDebugUnitTest
