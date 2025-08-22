# OverCast - Mobile (Capacitor)

This adds a Capacitor wrapper around the static web app in `src/web` so you can
run it on your phone.

## Prereqs

- Node.js 18+
- Android Studio + SDKs (for Android build)
- Java 17 (recommended via Android Studio install)

## Install

```
npm install
```

## Add Android

```
npx cap add android
```

If you see a message about TypeScript, it's already installed, since we use
`capacitor.config.ts`.

## Run with live API from your PC

1. (Optional) Start the API locally if you want to test your own backend:

```
npm run dev:api
```

2. Find your PC's LAN IP address, e.g. `192.168.1.10`.
3. In a new terminal, set the server URL for Capacitor and sync:

PowerShell (Windows):

```
$env:CAP_SERVER_URL = "http://192.168.1.10:8302"; npx cap sync android
```

4. Open Android Studio and run on your device:

```
npx cap open android
```

This will load the app from your PC's server. Your phone must be on the same
Wi‑Fi network. Geolocation prompts will work on device.

## Offline bundle (no dev server)

If you prefer bundling the static files instead of using the dev server:

```
$env:CAP_SERVER_URL = ""; npx cap sync android
```

This uses the built files from `src/web`.

## Notes

- The web app and widget now call `https://weather.oreostack.uk/api` directly.
  To point at your own backend, change the URL in `src/web/script/main.js` and
  `android/app/src/main/java/com/empowering/weather/WeatherWidgetProvider.java`.
- To change the app name or id, edit `capacitor.config.ts`.
