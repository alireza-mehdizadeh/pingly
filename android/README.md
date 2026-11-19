# Pingly for Android

Kotlin · Jetpack Compose · Material 3 · minSdk 26 (Android 8) · targetSdk 36 (Android 16).
Fully Persian and right-to-left, typeset in Vazirmatn Round-Dots with Persian digits.

## Architecture

```
ir.byalireza.pingly
├── PinglyApp / AppContainer      hand-rolled DI, wiring of realtime → repositories → reactions
├── core/Persian                  Persian digits, phone parsing, Jalali calendar, relative times
├── data/
│   ├── SecureStore               AES-GCM secrets sealed by a non-exportable Android Keystore key
│   ├── AppPrefs                  synchronous prefs (readable by receivers at boot)
│   ├── crypto/E2ee               ECDH P-256 + HKDF-SHA256 + AES-256-GCM end-to-end encryption
│   ├── api/                      typed OkHttp client, DTOs, Persian error mapping
│   ├── realtime/RealtimeClient   self-healing WebSocket (pings, backoff + jitter, network callback)
│   └── repo/                     Account (couple state, keys), Event (decrypted history), Media
├── domain/                       event types, vibration patterns, sounds, styles, presets
├── service/                      the always-on background layer (see below)
└── ui/                           Compose screens + view models, one package per feature
```

The server is the source of truth; the app keeps a cached copy of the couple state so names and
notifications work offline. Everything private (love notes, questions, answers, captions, voice
notes, photos, voice titles) is encrypted on the phone before upload.

## Staying reachable in the background

Pingly's job is to receive the partner's actions when the app is closed and the screen is off.
It uses every mechanism Android supports, and never anything that bypasses it:

| Layer | What it does |
|-------|--------------|
| Foreground service (`remoteMessaging` type) | Holds the realtime connection with a persistent notification. No time limit on Android 14–16. |
| `START_STICKY`, `onTaskRemoved`, `onDestroy` | Swiping from recents doesn't stop it; if the system kills it, a restart alarm is armed. |
| Boot / update receivers | `BOOT_COMPLETED`, `QUICKBOOT_POWERON`, `MY_PACKAGE_REPLACED` restart it. `remoteMessaging` is allowed to start from boot on Android 15+. |
| Alarm watchdog | Every 15 min, `setExactAndAllowWhileIdle` (falls back to inexact) checks the service and the socket. |
| WorkManager watchdog | An independent periodic check that survives reboots on its own. |
| Network callback + backoff | Reconnects instantly when the network returns; exponential backoff with jitter otherwise. WebSocket pings detect dead connections within ~25 s. |
| Battery-optimization exemption | Requested during setup; keeps network access in Doze and lets the service restart from the background. |
| Full-screen intents + "display over other apps" | Photos, notes and questions open over the lock screen and wake the display. |
| OEM autostart screens | Deep links for Xiaomi, Huawei/Honor, Oppo/Realme, Vivo, OnePlus, Samsung, Asus, Meizu, Transsion. |
| Honest failure | If Android refuses a restart, Pingly posts a notification instead of going quiet. |

The **only** in-app way to switch Pingly off on purpose is the **Pause** button on the
dashboard (it tells the server, so the partner sees it and the server refuses their actions).
A user's **Force Stop** from system settings is respected — Android keeps a force-stopped app
down until it is opened again, and opening Pingly restarts everything.

## Permissions

| Permission | Why |
|------------|-----|
| `INTERNET`, `ACCESS_NETWORK_STATE` | Realtime connection |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_REMOTE_MESSAGING`, `POST_NOTIFICATIONS` | Always-on service + its notification |
| `RECEIVE_BOOT_COMPLETED`, `WAKE_LOCK`, `SCHEDULE_EXACT_ALARM` | Restart after boot, keep the CPU up while reacting, watchdog alarms |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Ask (via the system dialog) not to be put to sleep |
| `VIBRATE`, `USE_FULL_SCREEN_INTENT`, `SYSTEM_ALERT_WINDOW` | Remote vibration and full-screen display |
| `RECORD_AUDIO` | Recording voice notes (asked only when the user taps record) |

## Builds

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew testDebugUnitTest       # unit tests (calendar, phone, crypto incl. RFC 5869 vectors, domain)
./gradlew assembleDebug           # talks to http://10.0.2.2:3000 (local server, emulator)
./gradlew assembleStaging         # minified like release, local server — for QA of R8 output
./gradlew assembleRelease         # minified, production server https://pingly.byalireza.ir
```

Release signing reads `android/keystore.properties` (git-ignored):

```properties
storeFile=keystore/pingly-release.jks
storePassword=…
keyAlias=pingly
keyPassword=…
```

Keep the keystore and its passwords safe: every future update must be signed with the same key.

## Regenerating brand assets

```bash
python3 ../branding/tools/generate_android_icons.py   # launcher, monochrome, mascots, status icon
python3 ../branding/tools/generate_sounds.py          # the original sound pack in res/raw
```
