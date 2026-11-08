# Flex Standby

A bedside clock for the Galaxy Z Fold 8 in tent mode.

- **Outside screen:** a calendar and an analog clock. When the alarm rings, it shows big flip-style time cards with **Snooze** and **Stop**.
- **Inside screen:** a light only. It stays dark, then glows in your accent colour while the alarm rings.
- **Half-fold button:** when the phone is half-folded, a round button appears in the bottom-right corner. Tap it to start standby.
- **8 accent colours:** the clock, the calendar and the glow all use the same colour.

## Get the APK (no computer setup needed)

1. Create a new repository on GitHub, for example `flex-standby`.
2. Upload everything in this folder, including the hidden `.github` folder. Using the web page's "upload files" works.
3. Open the repository's **Actions** tab. The "Build APK" run starts on its own (or press **Run workflow**).
4. When it turns green (about 5 minutes), open the run and download **FlexStandby-apk** at the bottom.
5. Unzip it on your phone and install `app-debug.apk`. Allow "install unknown apps" if Android asks.

## First-time setup on the phone

1. Open **Flex Standby**.
2. Tap **Allow "display over other apps"** and switch it on. This is needed for the half-fold button.
3. Tap **Allow notifications**.
4. Set your alarm time and switch the alarm on.
5. Switch on **Show the clock button when half-folded**.

## Using it

1. Half-fold the phone and stand it like a tent, with the hinge on top.
2. Tap the round button in the bottom-right corner of the outside screen.
3. Use the colour dot to change colours, and the bell to test the alarm glow.
4. To stop the alarm, tap **Stop** or **Snooze**, or press a volume button.

## Good to know

- Lighting the inside screen while you use the outside one relies on Android's dual-screen feature (Jetpack WindowManager). If your phone doesn't allow it, the outside screen pulses with the glow colour instead.
- Samsung's battery saver can stop background apps. If the button stops appearing, set Flex Standby's battery usage to **Unrestricted**.
