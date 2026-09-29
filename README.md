<div align="center">

<img src="docs/logo.png" width="132" alt="Shizuku Next">

# Shizuku Next

<img width="200" alt="Screenshot_20260928_115734_Shizuku Next" src="https://github.com/user-attachments/assets/377def8a-0e21-45a8-abd1-22ad83e53fa2" />
<img width="200" alt="Screenshot_20260928_115739_Shizuku Next" src="https://github.com/user-attachments/assets/339d8b1b-0c2c-4374-9a9f-13a5aa3fb2c7" />
<img width="200" alt="Screenshot_20260928_115745_Shizuku Next" src="https://github.com/user-attachments/assets/146ac504-a78e-48c5-81d6-d9367dc87616" />

#

An Android app that allows other apps to use system-level APIs that require ADB/root privileges.

**Shizuku Next is a fork of [thedjchi's Shizuku](https://github.com/thedjchi/Shizuku), which is itself a fork of
[RikkaApps' Shizuku](https://github.com/RikkaApps/Shizuku).** Shizuku the server, the API, the shell and
everything that makes this possible is RikkaW's work, and the fork this is built on is thedjchi's. Their credit
is given in full below; please support them.

**Note from [thedjchi](https://github.com/thedjchi/Shizuku), whose fork this continues:** *"I'm pausing maintenance
for the time being, I simply haven't had time to work on this and it was a side project."* This fork picks up where
his left off.

⚠️ **This build is signed with this fork's own key, so it will not install over the official Shizuku or over
thedjchi's fork** Android refuses to replace an app signed with a different key. Uninstall that one first, and
note that a Shizuku server it started may still be running until you stop it or reboot. Nothing else changes: the
package name (`moe.shizuku.privileged.api`), the interfaces and the API are untouched, so apps that use Shizuku
keep working.

[![Stars](https://img.shields.io/github/stars/rushiranpise/Shizuku-Next?style=for-the-badge&color=bfb330&labelColor=807820&logo=data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCAyNCAyNCI+PHRpdGxlPnN0YXI8L3RpdGxlPjxwYXRoIGQ9Ik0xMiwxNy4yN0wxOC4xOCwyMUwxNi41NCwxMy45N0wyMiw5LjI0TDE0LjgxLDguNjJMMTIsMkw5LjE5LDguNjJMMiw5LjI0TDcuNDUsMTMuOTdMNS44MiwyMUwxMiwxNy4yN1oiIGZpbGw9IndoaXRlIiAvPjwvc3ZnPg==)](https://github.com/rushiranpise/Shizuku-Next/stargazers)
[![Downloads](https://img.shields.io/github/downloads/rushiranpise/Shizuku-Next/total?style=for-the-badge&color=bf7830&labelColor=805020&logo=data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCAyNCAyNCI+PHRpdGxlPmRvd25sb2FkPC90aXRsZT48cGF0aCBkPSJNNSwyMEgxOVYxOEg1TTE5LDlIMTVWM0g5VjlINUwxMiwxNkwxOSw5WiIgZmlsbD0id2hpdGUiIC8+PC9zdmc+)](https://github.com/rushiranpise/Shizuku-Next/releases)

[![Latest Stable](https://img.shields.io/github/v/release/rushiranpise/Shizuku-Next?style=for-the-badge&color=3060bf&labelColor=204080&label=Latest%20Stable&logo=data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIGhlaWdodD0iMjRweCIgdmlld0JveD0iMCAtOTYwIDk2MCA5NjAiIHdpZHRoPSIyNHB4IiBmaWxsPSIjZTNlM2UzIj48cGF0aCBkPSJNNDQwLTgycS03Ni04LTE0MS41LTQxLjV0LTExNC04N1ExMzYtMjY0IDEwOC0zMzNUODAtNDgwcTAtOTEgMzYuNS0xNjhUMjE2LTc4MGgtOTZ2LTgwaDI0MHYyNDBoLTgwdi0xMDlxLTU1IDQ0LTg3LjUgMTA4LjVUMTYwLTQ4MHEwIDEyMyA4MC41IDIxMi41VDQ0MC0xNjN2ODFabS0xNy0yMTRMMjU0LTQ2Nmw1Ni01NiAxMTMgMTEzIDIyNy0yMjcgNTYgNTctMjgzIDI4M1ptMTc3IDE5NnYtMjQwaDgwdjEwOXE1NS00NSA4Ny41LTEwOVQ4MDAtNDgwcTAtMTIzLTgwLjUtMjEyLjVUNTIwLTc5N3YtODFxMTUyIDE1IDI1NiAxMjh0MTA0IDI3MHEwIDkxLTM2LjUgMTY4VDc0NC0xODBoOTZ2ODBINjAwWiIvPjwvc3ZnPg==)](https://github.com/rushiranpise/Shizuku-Next/releases/latest?q=prerelease%3Afalse&expanded=true)
[![Latest Beta](https://img.shields.io/github/v/release/rushiranpise/Shizuku-Next?sort=semver&style=for-the-badge&color=30bf60&labelColor=208040&label=Latest%20Beta&logo=data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIGhlaWdodD0iMjRweCIgdmlld0JveD0iMCAtOTYwIDk2MCA5NjAiIHdpZHRoPSIyNHB4IiBmaWxsPSIjZTNlM2UzIj48cGF0aCBkPSJNMjAwLTEyMHEtNTEgMC03Mi41LTQ1LjVUMTM4LTI1MGwyMjItMjcwdi0yNDBoLTQwcS0xNyAwLTI4LjUtMTEuNVQyODAtODAwcTAtMTcgMTEuNS0yOC41VDMyMC04NDBoMzIwcTE3IDAgMjguNSAxMS41VDY4MC04MDBxMCAxNy0xMS41IDI4LjVUNjQwLTc2MGgtNDB2MjQwbDIyMiAyNzBxMzIgMzkgMTAuNSA4NC41VDc2MC0xMjBIMjAwWm04MC0xMjBoNDAwTDU0NC00MDBINDE2TDI4MC0yNDBabS04MCA0MGg1NjBMNTIwLTQ5MnYtMjY4aC04MHYyNjhMMjAwLTIwMFptMjgwLTI4MFoiLz48L3N2Zz4=)](https://github.com/rushiranpise/Shizuku-Next/releases)

[![Bug Reports](https://img.shields.io/github/issues-search/rushiranpise/Shizuku-Next?query=label%3Abug%20state%3Aopen&style=for-the-badge&color=bf3030&labelColor=802020&label=Bug%20Reports)](https://github.com/rushiranpise/Shizuku-Next/issues?q=is%3Aissue%20state%3Aopen%20label%3Abug)
[![Feature Requests](https://img.shields.io/github/issues-search/rushiranpise/Shizuku-Next?query=label%3Aenhancement%20state%3Aopen&style=for-the-badge&color=30a7bf&labelColor=207080&label=Feature%20Requests)](https://github.com/rushiranpise/Shizuku-Next/issues?q=is%3Aissue%20state%3Aopen%20label%3Aenhancement)

[![Translate on Crowdin](https://img.shields.io/badge/Translate%20on%20Crowdin-2e3340?style=for-the-badge&logo=crowdin&logoColor=ffffff)](https://crowdin.com/project/shizuku)

</div>

## ⚠️ Disclaimer

This is a **FORK** of Shizuku. If you are looking for the original version, please visit the [RikkaApps/Shizuku](https://github.com/RikkaApps/Shizuku) repository.

## ⬇️ Download

Releases of **this fork** are on the [releases page](https://github.com/rushiranpise/Shizuku-Next/releases), with the [latest](https://github.com/rushiranpise/Shizuku-Next/releases/latest) on top the badges above track this repository. Building it yourself is covered under [Building the App](#building-the-app).

The fork this is based on publishes its own releases [on his repository](https://github.com/thedjchi/Shizuku/releases). Because the two are signed with different keys, neither can replace the other: uninstall the one you have before installing the other.

## ✨ Added Features

Everything below is either inherited from the fork this is based on or added here. The two are kept apart
on purpose, because the second list is the only part this project is responsible for.

### 🍴 From [thedjchi's fork](https://github.com/thedjchi/Shizuku) his work, carried over

Shizuku Next started from his fork of Shizuku, so these are his features. A couple have been extended here
since the transport split inside **TCP mode**, for instance and where that is so, the extra behaviour
also appears under this fork's own list below.

* **More robust "start on boot":** waits for a Wi-Fi connection before starting the Shizuku service
* **TCP mode:** keep the classic ADB port open (i.e. the `adb tcpip` command) so the USB start and the watchdog can restart Shizuku without Wi-Fi or pairing. With it off, an open port is closed whenever Shizuku starts over wireless
* **Watchdog service:** automatically restarts Shizuku if it stops unexpectedly, and can alert you of crashes/potential fixes
* **Start/stop intents:** toggle Shizuku on-demand using automation apps (e.g., Tasker, MacroDroid, Automate)
* **[BETA] Stealth mode:** hide Shizuku from other apps that don't work when Shizuku is installed
* **[BETA] In-app updates:** option to automatically check for new updates, and can automatically download/install the latest version from GitHub
* **Android/Google TV and VR headset support:** UI is now compatible with D-Pad remotes, all TVs are supported (including Android 14+ TVs that require pairing), and the multi-window pairing dialog is toggleable in settings for VR headsets
* **MediaTek support:** fixes a critical bug in the original v13.6.0 which prevented Shizuku from working on MediaTek devices
* And more!

### 🦊 Added by Shizuku Next (this fork)

The interface work, the start-method handling and the reliability fixes below are ours, built on top of his
fork, which is built on [RikkaApps' Shizuku](https://github.com/RikkaApps/Shizuku).

* **Automated setup:** the separate "Pair" button has been removed pressing "Start" detects when wireless debugging still needs to be paired and launches the pairing flow automatically
* **Pair without typing:** Shizuku reads the pairing code and port straight out of the system's "Pair with device" dialog, pairs, and starts itself so the code never has to be typed, and can't expire while you switch apps. Manual pairing (notification + typed code) stays one tap away as a fallback the *Manual* button beside *Automated* in the same dialog and both labels are single words so they fit on one button row instead of stacking
* **Wireless debugging auto-disable (optional):** restore the old behaviour of turning wireless debugging off once Shizuku has started over it, instead of leaving it on so Shizuku can restart itself
* **Wireless debugging stays enabled:** starting Shizuku no longer turns off wireless debugging, so it can restart with USB debugging off and no Wi-Fi connection; the status card shows whether it runs over wireless or USB debugging
* **Accurate status label:** the home status card shows the server's real UID (e.g. `uid 1000`) instead of always assuming adb, plus three facts that can't be confused with each other the transport it actually runs on, the method this launch used (*Current*) and the method the next Start will use (*Default*) so wireless, USB, system and root stay apart. The four read as one row across the card label above value, a hairline rule between each pair so the whole state is legible without reading left to right. Every status notification names the method too ("USB debugging · Waiting to retry")
* **Fewer cryptic failure cards:** a pairing request that the device rejects is reported as a pairing failure instead of being retried until the dead socket reports "Socket closed"
* **One-tap battery-optimization bypass:** "fix" whitelists Shizuku directly through Shizuku/root (`deviceidle whitelist`, `appops`) instead of only opening the system dialog
* **Duplicate server cleanup:** the starter detects stale Shizuku server processes via multiple `/proc` vectors and terminates them cleanly (with a short yield) before starting a new one
* **Clearer auto-start progress:** the wireless/boot start notification now shows more states waiting for Wi-Fi, waiting for unlock, and connecting
* **Auto wake-up:** when an app requests the Shizuku binder while the server is down, the manager tries to start it in the background (if start on boot is enabled and you did not deliberately stop it)
* **Shell, in the app:** a terminal opened from the navigation bar, next to Settings, which runs commands as the uid Shizuku runs as (2000 over adb, 0 with root, 1000 with the exploit) with the working directory and anything you export carried from command to command, so `cd` and `export` behave the way a session does even though every command is its own process. A second backend runs through the root shell for when Shizuku is stopped, and asks for root on the spot rather than assuming it, saying so when it is refused. Output streams in as it arrives, stderr in red, non-zero exits marked, with copy and clear in the header. There is no tty to be had from an app, so a program that needs one (nano, an ssh prompt) belongs in the rish export instead
* **Fade behind the navigation bar:** the bar's band carries a gradient from transparent to the page colour, so a list scrolling under the floating bar fades out instead of arriving at it at full contrast. It is drawn on the bar's own band rather than by the pages, so it leaves with the bar and is already there before a page has scrolled; a gradient rather than a blur, because blurring a scrolling page means drawing it into an offscreen layer and re-blurring it every frame
* **Reliable TCP-port rebinding:** after switching adbd to the configured TCP/IP port, Shizuku waits until the new port is actually listening before connecting (custom ports avoid the 5555 conflict)
* **Batch permission management:** long-press an app to enter multi-select, then grant or revoke permission for many apps at once, with select-all and a confirmation, plus a Toggle all action that flips every listed app in one tap (narrow the list with the search first, it only touches what is listed)
* **Device info card:** the home screen describes the device the way KernelSU's manager does manager version, kernel version, device model, fingerprint, SELinux status and seccomp status (SELinux is read through the server when one is running, and otherwise inferred the way KernelSU's manager does `getenforce` being denied means the policy is enforcing)
* **New Material 3 interface:** the manager UI is rebuilt in Jetpack Compose with a KernelSU-style layout a bottom tab bar (Home · Apps · Manage · Settings), tonal status cards with dynamic color, hairline outlines on pure-black OLED themes, and one rounded card per app in the Apps tab so a long list reads as separate entries instead of a flat column
* **Start as system (UID 1000):** a "Start (system)" card launches Shizuku under the system UID either via a built-in device exploit or by copying a command for your own privilege escalation, selected in settings
* **Start method setting:** pick how Shizuku starts Wireless debugging, USB debugging, System (UID 1000) or Root and the Start button, start on boot, the watchdog and the start intents all follow it instead of guessing from the last method that happened to work. Root is only offered where the device can actually grant it, and a device that loses root (an OTA, root switched off in the manager) has a stored Root rewritten to Wireless debugging with a line in settings explaining the change rather than every start pointing at a method that can't run
* **Separate USB start:** home now has "Start via USB debugging" next to the wireless one, and the two never cross over in transport: a wireless start always goes over the wireless port (so it can't come back reporting USB), and never enables USB debugging or pairing; a USB start uses the classic ADB port, where the connection authenticates itself and Android asks you to allow USB debugging once
* **The USB start reopens its port by itself:** Android clears the classic ADB port on every reboot and it isn't persistent, so a USB start borrows the wireless connection to reopen it whenever Wi-Fi is available, then starts over the port as usual. With no Wi-Fi to borrow it says exactly what to do connect to Wi-Fi, or run `adb tcpip 5555` from a computer
* **Restart timing:** choose whether unattended restarts wait for an unmetered Wi-Fi connection (starts you trigger yourself never wait); an unattended start that fails retries with backoff rather than giving up on the first try
* **One start at a time:** while Shizuku is running, every start row on the home screen wireless, USB, system, root and the "Start using computer" ADB command is dimmed and inert, so a tap can't silently do nothing; **Restart** is how you relaunch it
* **ADB without Developer options:** a startup switch that turns the Developer options flag off and puts back the two ADB settings it would otherwise take with it, so Shizuku still starts and restarts on a device that looks clean to apps which refuse to run when Developer options is on. It is re-applied after every reboot, and switching it off puts the flag back
* **Asks for the minimum, and only once:** WRITE_SECURE_SETTINGS is needed to switch wireless debugging on, nothing else — so a USB start, or a wireless start with that toggle already on, works on a fresh install without it, and no start is aborted by a setting the app isn't allowed to write. Only ADB can hand that permission out, so Shizuku grants it to itself as soon as a server is running (which is also what lets it enable its own accessibility service for pairing instead of asking you to). A brand-new install still needs either one `adb shell pm grant …` or the manual pairing fallback before the first server exists
* **One-tap ADB port fix:** a USB start that finds the classic ADB port closed offers to open it and start in one tap, instead of only explaining `adb tcpip`
* **Clearer apps list:** the Apps tab now says what is happening a spinner while it loads, "Shizuku is not running" with a Start button, no apps matching the search, or no app having asked for permission yet instead of a blank page
* **Search and sort authorized apps:** filter the apps list by name, narrow it to All, Granted, Revoked or Hidden with the count shown on the filter in force, and sort it alphabetically, by most recently updated or by most recently installed
* **Per-app system permissions (Manage tab):** a fourth tab listing every installed app, and behind each one the things that normally need a computer grant or revoke its system and runtime permissions, and each row says which permission it is by name and what the platform's protection for it is. The split comes from the platform's own protection levels rather than a list of ours, and one adb is not allowed to change keeps its switch off and says why, instead of failing when tapped. Handing an app a privileged permission asks first, and the app's page in system settings is one tap away in the header
* **App ops:** the switches the platform itself consults run in background, foreground services, keep awake, notifications, clipboard, draw over other apps and installing other apps, each as Default / Allow / Ignore / Deny. Every change is read back before it is reported, because the platform answers a refused op with success so "it worked" has to mean the op actually changed. Capabilities that are special access in system settings rather than app ops (all files access, usage access, exact alarms, modifying system settings) are deliberately absent no app can change them, and the section says so
* **Per-app actions and battery:** force stop, suspend (greys an app out and stops it running while keeping its data), disable, clear data, uninstall for this user, restore a removed system app, plus the app standby bucket from Unrestricted down to Never and the battery-optimisation exemption. Buckets only the system can assign are reported rather than offered, so no switch promises a change the platform will not make
* **App facts:** version, UID, target and minimum SDK, ABI, data directory, signature, install source, install and update dates, debuggable, backup allowed and whether the app has a launcher entry. All of it is read from the local package manager, so the tab opens and lists apps with Shizuku stopped; only the rows that change something need it, and they are dimmed until it is running
* **More resilient watchdog:** self-heals a dead server on screen unlock (even if the manager wasn't running when it died) and never fights a deliberate Stop
* **Watchdog control intents:** enable/disable the watchdog via `moe.shizuku.privileged.api.WATCHDOG_ON`, `...WATCHDOG_OFF`, or `...WATCHDOG_TOGGLE`
* **Status broadcasts:** automation apps can react to `moe.shizuku.privileged.api.SHIZUKU_CHANGED` and `...WATCHDOG_CHANGED`, each carrying a `status` extra (1 = on, 0 = off)
* **Stability on some Chinese devices (Xiaomi/OPPO/Lenovo):** background starts no longer force USB debugging on, so Shizuku no longer dies when the USB mode is File Transfer and the screen is off

The fork's own name and icon the fox in [`docs/logo.png`](docs/logo.png) are this project's. Everything
underneath is the work credited below.

## 📝 User Guide

Please read the [wiki](https://github.com/thedjchi/Shizuku/wiki) for setup, info, and troubleshooting steps it belongs to the fork this one is based on, and documents the same behaviour, because the features and the server come from there.

## ☑️ Requirements

**Minimum Version: Android 7+**
- **Root mode:** Requires a rooted device
- **Wireless Debugging mode:** Works on Android 11+ and all Android TVs
- **PC mode:** Works on all devices
- **Start on boot:** Available only when using Wireless Debugging or Root mode

## 🔒 Privacy

Shizuku takes user privacy very seriously.

* No tracking or analytics
* No telemetry
* No proprietary libraries
* No Google Play Services
* Open-source codebase
* Reproducible builds
* Internet access is only used for wireless debugging connections and to fetch updates from GitHub
* Only required permissions are declared

### Permissions

* **INTERNET:** required for the wireless debugging start mode to work. Also used to fetch updates from GitHub
* **ACCESS_NETWORK_STATE:** used to determine when Wi-Fi is available for background start via wireless debugging
* **POST_NOTIFICATIONS:** required for pairing notification and other alerts
* **RECEIVE_BOOT_COMPLETED:** required for start on boot
* **FOREGROUND_SERVICE:** prevents watchdog from being killed
* **REQUEST_IGNORE_BATTERY_OPTIMIZATIONS:** prevents start on boot and watchdog services from being killed
* **WRITE_SECURE_SETTINGS:** used to toggle USB and wireless debugging in the background when starting/stopping Shizuku
* **REQUEST_DELETE_PACKAGES:** used to request uninstall for Shizuku/stub when using stealth mode
* **REQUEST_INSTALL_PACKAGES:** used to request install for app updates, as well as Shizuku stub when using stealth mode

## 🌎 Translations

Contribute translations through the [Crowdin project](https://crowdin.com/project/shizuku).

## 🎁 Donations

This fork and all of its features are free, and there will never be ads. It has no donation setup of its own.

If you want to support the work it is built on, support the people who made it: **[thedjchi](https://www.buymeacoffee.com/thedjchi)**, whose fork most of these features come from, and **[RikkaW / RikkaApps](https://github.com/RikkaApps/Shizuku)**, who wrote Shizuku itself.

## 📱 Developer Guide

### API & Demo Project
The API guide and a demo project are available in the [Shizuku-API](https://github.com/thedjchi/Shizuku-API) repository

### Notes

1. Shizuku has different permissions in root and ADB mode. You can see permissions granted to ADB [here](https://cs.android.com/android/platform/superproject/main/+/main:frameworks/base/packages/Shell/AndroidManifest.xml).
   If your app requires root permission, use `ShizukuService#getUid` to check if Shizuku is running as root or ADB, or use `ShizukuService#checkPermission` to check if the server has sufficient permissions.
2. On devices running Android 8 or lower, if you need to use Shizuku in a Service or Broadcast Receiver that might not be started by an Activity, please trigger the send binder by starting a transparent activity.
3. Please prefer using `ShizukuBinderWrapper` instead of directly using `transactRemote` when possible, as API calls can change across Android versions.

## 🤝 Contritbuting

### Building the App

- Clone with `git clone --recurse-submodules`
- Run gradle task `:manager:assembleDebug` or `:manager:assembleRelease`

The `:manager:assembleDebug` task generates a debuggable server. You can attach a debugger to `shizuku_server` to debug the server. In Android Studio, ensure `Run/Debug configurations > Always install with package manager` is checked, so that the server will use the latest code.

### Submitting Changes

1. Fork the repository
2. Create a feature branch (`git checkout -b branch-name`)
3. Make your changes
4. Commit your changes (`git commit -m 'Commit message'`)
5. Push to the branch (`git push origin branch-name`)
6. Open a Pull Request

## 🙏 Credits

* **[RikkaW / RikkaApps](https://github.com/RikkaApps/Shizuku)** the original Shizuku: the server, the API, the
  shell, and the foundation all of this is built on.
* **[thedjchi](https://github.com/thedjchi/Shizuku)** the fork this project is based on. Everything under
  *From thedjchi's fork* above is his.
* Everyone who contributed upstream, and the translators on [Crowdin](https://crowdin.com/project/shizuku).

## 📃 License

All code files in this project are licensed under [Apache 2.0](LICENSE)
