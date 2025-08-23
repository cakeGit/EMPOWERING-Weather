locationStore.js

What it is

- A tiny cross-platform location persistence helper used by the web app and the
  Capacitor-built app.
- Exposes `saveLocation(lat, lon, source)` and `getStoredLocation()`.
- Uses Capacitor Preferences on native and localStorage on web.
- Performs a one-time migration from cookies (`last_location` JSON or
  `last_lat`/`last_lon`).
- Enforces a TTL (default 15 minutes) so widgets/apps don't rely on very stale
  coordinates.

How to use

- In `main.js` (or your UI code) import dynamically to avoid bundling issues:

```js
const mod = await import("./locationStore.js");
await mod.saveLocation(lat, lon, "geo_quick");
const cached = await mod.getStoredLocation();
```

New helpers

- `isCacheCloseTo(lat, lon, degrees = 1)` returns true when stored cache exists
  and is within ±degrees for both lat & lon.
- When `getStoredLocation()` returns an expired value it will now include
  `stale: true` to signal callers they may want to refresh.

Notes

- TTL_MS is set in the file; change it in `locationStore.js` if you prefer a
  different cache age.
- The module now prefers a static Capacitor Preferences import when available in
  native bundles, with a runtime fallback to the Capacitor plugin bridge for
  compatibility.
- If you prefer to force a static import, replace the require-fallback with a
  static `import { Preferences } from '@capacitor/preferences'` and rebuild the
  native app.
