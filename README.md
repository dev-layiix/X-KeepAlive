# X-KeepAlive

<p align="center">
  <img src="x-keepalive-icon.png" alt="X-KeepAlive logo" width="180">
</p>

> Android utility that helps selected games remain in the background longer when switching between apps. Uses Shizuku for supported background policies; no root required.

[**Download X-KeepAlive 1.0.41**](https://github.com/dev-layiix/X-KeepAlive/releases/latest) · [All releases](https://github.com/dev-layiix/X-KeepAlive/releases)

## Features

- Applies supported background policies through Shizuku, without root.
- Monitors up to 4 processes at the same time: you can add more games or apps, the list scrolls, each row shows the app icon and a switch, and each of the 4 active apps gets its own verified policy.
- The switch next to the active count starts and stops the service. It stays off until Shizuku is installed, running, and authorized. The apps left on stay on.
- If Shizuku is missing, the app says so and opens its Play Store page.
- The Guide tab opens one step at a time. The next step appears only after the current one is done, and each step has a button to the matching app or setting.
- Saves the original Android settings before changing them and attempts to restore those values when monitoring is stopped.
- **Tune tab (phone optimization):** fast animations (0.5x), battery-drain limits for chosen apps, bloatware disabling with one-tap restore, and cache trimming. Monitored games are always excluded. A "Recommended for you" section suggests what to restrict or disable based on your real 7-day usage, with an "Apply all" button.
- Home-screen widget mirroring the real monitoring state, with toggle and quick launch.
- Interface in 5 languages (Italian, English, Spanish, Brazilian Portuguese, Russian), picked on first launch.

Shizuku must be started and authorized by the user. X-KeepAlive does not open an ADB socket or restart Shizuku after a reboot.

## Background policy and restoration

Before changing a setting, X-KeepAlive reads the current state of the Doze whitelist, standby bucket, idle state, and the `RUN_IN_BACKGROUND` and `RUN_ANY_IN_BACKGROUND` AppOps modes. It stores the values it successfully reads.

If a read fails, that setting is not changed. When monitoring is stopped, the app attempts to restore the saved values rather than forcing AppOps to `default`. An item already present in the Doze whitelist is not removed.

If Shizuku drops briefly (for example its own update), the service waits 60 seconds for it to come back before turning off, instead of disabling automation. If Shizuku stays unavailable, the service turns off and the apps left on stay on. A command that is not confirmed, or a restoration that is incomplete, is reported in the local log and the saved record is retained. X-KeepAlive does not modify another package while a previous package still has an unrestored policy.

Restoration depends on Shizuku remaining available and Android accepting the commands. Review the app's status and local log for any reported failure.

## Limitations

X-KeepAlive cannot guarantee that Android will keep a process in memory. Android may terminate an app under memory pressure, and a game may reload even if its process remains alive, for example if its activity or graphics surface is recreated.

The app does not:
- Start, close, or reopen games.
- Simulate input, automate gameplay, or provide bots or macros.
- Modify game files, memory, or code.
- Use hidden APIs or attempt to bypass anti-cheat systems.
- Restart wireless debugging or Shizuku after reboot.

Using background-management tools with a particular game may be subject to that game's rules. X-KeepAlive is not endorsed or certified by game developers or anti-cheat providers, and this README does not guarantee that its use is free from restrictions or bans.

## Installation

Download [X-KeepAlive 1.0.41](https://github.com/dev-layiix/X-KeepAlive/releases/latest) from the Releases page and install it on your Android device. You may need to allow installation from the source used to download the file.

Release builds signed with the X-KeepAlive certificate can update one another. A debug APK uses a different certificate and cannot be installed over a release build.

## First launch

On the first launch the app opens a step-by-step setup:

1. Turn on Developer options: open Settings, About phone (then Software information on Samsung/Xiaomi), tap Build number 7 times, enter your screen lock, and wait for "You are now a developer".
2. Turn on Wireless debugging in Developer options and tap "Pair device with pairing code".
3. Install Shizuku from the Play Store, open it, choose "Start via wireless debugging", and type the 6-digit code.
4. Authorize X-KeepAlive when Shizuku asks.
5. Grant usage access and allow the foreground-service notification.
6. Add the games or apps you want on Home (only four can be switched on at once).
7. Turn on the switch next to the active count. The app switches you left on stay on.

To stop monitoring, use the in-app switch or the Stop action in the notification. The app will attempt to restore the saved settings while Shizuku is available.

## Shizuku and wireless debugging

Shizuku is an independent Android service that must be configured and started by the user. Follow the instructions in the Shizuku app and Android's Developer options to start it through wireless debugging, then return to X-KeepAlive and grant authorization.

After a reboot, wireless debugging and Shizuku may need to be started again manually. X-KeepAlive cannot restart them itself.

## Privacy

X-KeepAlive has no Google AdMob integration or advertising SDK. The updated source does not declare the `INTERNET` or `ACCESS_NETWORK_STATE` permissions.

Settings and event logs are stored locally using DataStore and the app's local event log. This describes the behavior implemented in the source; it is not a blanket guarantee about every Android system-level service or device configuration.

## License

X-KeepAlive is licensed under the **GNU General Public License v3.0 (GPL-3.0-only)**. You may use, study, modify, and redistribute the source code under the terms of this license. If you distribute the program or modified versions, you must comply with the GPL-3.0 terms, including the applicable source-code and license-notice requirements.

See the [`LICENSE`](LICENSE) file for the full license text, or read the [GNU GPL v3.0](https://www.gnu.org/licenses/gpl-3.0.html).

## Tests and verification

The following checks were reported for version 1.0.41:

- `:policy-core:test`: 46 tests passed.
- Release build: completed successfully, signed with the X-KeepAlive certificate.
- On-device activation, deactivation, and restoration with Shizuku: not part of the automated build. Confirm those on a phone.

A successful build and unit tests do not replace testing on a real device. Behavior may vary by Android version, device manufacturer, system settings, memory pressure, and the app being monitored.
