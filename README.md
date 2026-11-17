<div align="center">

<img src="https://github.com/user-attachments/assets/74917e3c-5f46-4b7b-a5d6-f2896826698e" width="112" alt="Disha">

# Disha

<sub><i>Disha (දිශා)</i> · "directions" in Sinhala</sub>

**Google Maps turn-by-turn navigation on your TVS SmartXonnect cluster.**

The next turn, how far to it, and how long to go, on the bike's own screen, with your phone in your pocket.

[![Release](https://img.shields.io/github/v/release/sameeramperera/Disha?include_prereleases&label=release)](https://github.com/sameeramperera/Disha/releases)
[![Android](https://img.shields.io/badge/Android-9%2B%20(API%2028)-3DDC84)](#bikes-and-phones)
[![Java](https://img.shields.io/badge/Java-no%20Gradle-ED8B00)](#building-from-source)
[![Status](https://img.shields.io/badge/status-beta-orange)](#beta)

<p align="center">
  <img src="https://github.com/user-attachments/assets/8250fcc0-4bb3-42eb-935c-f425db033a93" width="26%" alt="Disha Find bike screen listing nearby Bluetooth devices">
  &nbsp;&nbsp;&nbsp;&nbsp;
  <img src="https://github.com/user-attachments/assets/79b33a09-5006-4110-b155-026856a6a09f" width="26%" alt="Disha dashboard while connected to the bike">
  &nbsp;&nbsp;&nbsp;&nbsp;
  <img src="https://github.com/user-attachments/assets/b69a7d76-0ef4-4811-99c8-8339919b47e1" width="26%" alt="Disha connected to the bike while Google Maps navigation runs">
</p>

</div>

## Why Disha

TVS Connect, the official app, can show directions on the cluster, but its
maps are often not accurate enough. Wrong turns, missing roads, and
routes that do not match what Google Maps would take. Most riders already
trust Google Maps, so Disha was built to put **Google Maps' directions** on the
cluster instead.

- **Works with the Google Maps you already use.** No separate maps, no
  downloads, no account. Start a trip in Google Maps and Disha follows it.
- **Starts by itself.** When you start navigation in Google Maps, Disha connects to the
  bike on its own. Switch the ignition off and on, and it reconnects within
  seconds.
- **Keeps your rides.** Every ride is recorded from the bike's own speed and
  odometer: distance, time, average and top speed.
- **Nothing leaves your phone.** Disha has no internet permission at all.

## Get started

1. **Install it:** download `Disha.apk` from
   [Releases](https://github.com/sameeramperera/Disha/releases) and open it.
   Allow *Install unknown apps* when Android asks.
2. **Complete the setup list.** Disha shows what is still missing, each with a
   **Fix** button:
   - **Bluetooth & notifications** — lets the app talk to the bike
   - **Read Google Maps** — needed to see the turn arrows
   - **Run with screen off** — stops the phone pausing navigation
3. **Find your bike:** switch the ignition on, tap **Find bike**, and pick it
   from the list. Likely bikes are marked with a star. Disha remembers it.
4. **Ride:** start navigation in Google Maps. The directions show up on the
   cluster.


> [!IMPORTANT]
> <a id="beta"></a>**Disha is in beta.** It has been tested on the TVS Raider.
> It may work on other SmartXonnect bikes, but that is not tested or guaranteed, and
> support for other bikes is not planned. If something
> looks wrong, please [open an issue](https://github.com/sameeramperera/Disha/issues)
> and attach the log (**Share log** in the app).


## How it works

1. **Google Maps** shows an ongoing notification while it navigates: the road,
   the distance to the next turn, the time and distance left, and the turn
   arrow as a picture.
2. **Disha reads that notification** (this is the *Read Google Maps*
   permission). It reads the text for the numbers, and works out the turn from
   the shape of the arrow picture.
3. **It sends the update to the cluster** over Bluetooth LE, in the same format
   TVS's own app uses, and keeps repeating it so the cluster keeps the
   navigation panel open.
4. **The bike sends back** its speed and odometer about three times a second.
   Disha uses those to record rides.


## Bikes and phones

<table>
<tr><td><b>Bike</b></td><td>Built for the <b>TVS Raider</b> with SmartXonnect. Other SmartXonnect bikes may work, but are not supported.</td></tr>
<tr><td><b>Phone</b></td><td>Android 9 or newer, with Bluetooth LE.</td></tr>
<tr><td><b>Google Maps</b></td><td>Set to <b>English</b>. Disha reads Maps' words, so other languages are not understood yet.</td></tr>
</table>

Tried it on another bike? You are welcome to say how it went in an
[issue](https://github.com/sameeramperera/Disha/issues), but fixes for other
bikes are not planned.

## Privacy and permissions

Disha has **no internet permission**. It cannot send anything anywhere. There
is no account, no cloud and no analytics. Everything stays on your phone:

- Your bike and your rides are saved inside the app.
- The log is kept on the phone, and leaves it only when you tap **Share log**.

<details>
<summary><b>Every permission, and why</b></summary>

<br>

<table>
<tr><td><code>BIND_NOTIFICATION_LISTENER_SERVICE</code></td><td>To read Google Maps' navigation notification. Disha ignores every other app's notifications.</td></tr>
<tr><td><code>BLUETOOTH_SCAN</code>, <code>BLUETOOTH_CONNECT</code></td><td>To find the bike and talk to it.</td></tr>
<tr><td><code>BLUETOOTH</code>, <code>BLUETOOTH_ADMIN</code>, <code>ACCESS_FINE_LOCATION</code></td><td>The same, on older Android versions, which need location permission for any Bluetooth scan. Disha never reads your location.</td></tr>
<tr><td><code>FOREGROUND_SERVICE</code>, <code>FOREGROUND_SERVICE_CONNECTED_DEVICE</code></td><td>To keep the link to the bike alive while you ride with the screen off.</td></tr>
<tr><td><code>POST_NOTIFICATIONS</code></td><td>For the notification Android requires while the link is running.</td></tr>
<tr><td><code>REQUEST_IGNORE_BATTERY_OPTIMIZATIONS</code></td><td>To ask Android not to pause Disha with the screen off.</td></tr>
<tr><td><code>RECEIVE_BOOT_COMPLETED</code></td><td>To start waiting for the bike again after the phone restarts.</td></tr>
</table>

</details>

## Building from source

Needs only Java 17 or newer. No Android Studio, no Gradle.

```sh
tools/fetch.sh                  # downloads the Android build tools, once
./build.sh 0.1.0-beta.1 1       # version name, version code
```

The signed APK is written to `Disha.apk`. The keystore password is read from
`DISHA_KS_PASS`, or asked for.


## Disclaimer

Disha is an independent, unofficial project. It is not affiliated with,
endorsed by, or connected to TVS Motor Company or Google in any way. TVS,
TVS Connect, SmartXonnect and Raider are trademarks of TVS Motor Company.
Google Maps is a trademark of Google LLC. These names are used only to
describe what Disha works with.

Use it at your own risk. Keep your eyes on the road, and do not handle your
phone while riding.
