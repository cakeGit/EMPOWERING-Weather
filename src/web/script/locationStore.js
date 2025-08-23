// Cross-platform location store for web + Capacitor
// - Uses Capacitor Preferences when running in native (if available)
// - Uses localStorage on web
// - One-time migration from cookies (last_location JSON or last_lat/last_lon)

const LOCATION_KEY = "last_location_v1";
const TTL_MS = 15 * 60 * 1000; // 15 minutes

// Prefer static import when available in native bundle. Keep a runtime fallback
let StaticPreferences = null;
try {
    // try resolving the Capacitor Preferences module when available in native bundles
    // bundlers may provide this; in the browser this will throw and be ignored
    // eslint-disable-next-line no-undef
    StaticPreferences = require("@capacitor/preferences");
} catch (_) {
    StaticPreferences = null;
}

function isNative() {
    try {
        return !!(
            window.Capacitor &&
            window.Capacitor.getPlatform &&
            window.Capacitor.getPlatform() !== "web"
        );
    } catch {
        return false;
    }
}

async function writeNative(payload) {
    try {
        if (StaticPreferences && StaticPreferences.Preferences) {
            await StaticPreferences.Preferences.set({
                key: LOCATION_KEY,
                value: JSON.stringify(payload),
            });
            return;
        }
        if (
            window.Capacitor &&
            window.Capacitor.Plugins &&
            window.Capacitor.Plugins.Preferences
        ) {
            const { Preferences } = window.Capacitor.Plugins;
            await Preferences.set({
                key: LOCATION_KEY,
                value: JSON.stringify(payload),
            });
            return;
        }
    } catch (e) {
        console.warn("locationStore: failed writeNative", e);
    }
}

async function readNative() {
    try {
        if (StaticPreferences && StaticPreferences.Preferences) {
            const r = await StaticPreferences.Preferences.get({
                key: LOCATION_KEY,
            });
            return r?.value ?? null;
        }
        if (
            window.Capacitor &&
            window.Capacitor.Plugins &&
            window.Capacitor.Plugins.Preferences
        ) {
            const { Preferences } = window.Capacitor.Plugins;
            const res = await Preferences.get({ key: LOCATION_KEY });
            return res?.value ?? null;
        }
        return null;
    } catch (e) {
        console.warn("locationStore: failed readNative", e);
        return null;
    }
}

function writeWeb(payload) {
    try {
        localStorage.setItem(LOCATION_KEY, JSON.stringify(payload));
    } catch (e) {
        console.warn("locationStore: writeWeb failed", e);
    }
}

function readWeb() {
    try {
        return localStorage.getItem(LOCATION_KEY);
    } catch (e) {
        console.warn("locationStore: readWeb failed", e);
        return null;
    }
}

function parseStored(raw) {
    if (!raw) return null;
    try {
        const o = JSON.parse(raw);
        if (!o.ts || Date.now() - o.ts > TTL_MS) return null;
        return o;
    } catch (e) {
        return null;
    }
}

export async function saveLocation(lat, lon, source = "app") {
    const payload = {
        lat: Number(lat),
        lon: Number(lon),
        ts: Date.now(),
        source,
    };
    if (isNative()) await writeNative(payload);
    else writeWeb(payload);
}

export async function getStoredLocation() {
    // Web one-time cookie migration
    if (!isNative()) {
        const migrated = migrateFromCookiesIfPresent();
        if (migrated) {
            writeWeb(migrated);
            return parseStored(JSON.stringify(migrated));
        }
    }

    const raw = isNative() ? await readNative() : readWeb();
    const parsed = parseStored(raw);
    if (parsed) return parsed; // fresh within TTL

    // If expired or malformed, try to return a stale value with a marker so callers
    // can decide whether to use it to avoid an immediate refresh.
    try {
        if (!raw) return null;
        const o = JSON.parse(raw);
        if (o && o.lat != null && o.lon != null) {
            // return stale copy
            return { ...o, stale: true };
        }
    } catch (e) {
        return null;
    }
    return null;
}

/**
 * Returns true if stored cache exists and is within `degrees` for both lat & lon
 * of the supplied reference coordinates. Returns false if no stored cache.
 */
export async function isCacheCloseTo(refLat, refLon, degrees = 1) {
    if (refLat == null || refLon == null) return false;
    const s = await getStoredLocation();
    if (!s) return false;
    const latDiff = Math.abs(Number(s.lat) - Number(refLat));
    const lonDiff = Math.abs(Number(s.lon) - Number(refLon));
    return latDiff <= degrees && lonDiff <= degrees;
}

export function migrateFromCookiesIfPresent() {
    try {
        if (typeof document === "undefined" || !document.cookie) return null;
        const cookies = Object.fromEntries(
            document.cookie.split(";").map((c) => {
                const [k, ...v] = c.trim().split("=");
                return [k, decodeURIComponent(v.join("="))];
            })
        );

        if (cookies.last_location) {
            try {
                const parsed = JSON.parse(cookies.last_location);
                if (parsed.lat != null && parsed.lon != null) {
                    return {
                        lat: Number(parsed.lat),
                        lon: Number(parsed.lon),
                        ts: parsed.ts || Date.now(),
                        source: "cookie",
                    };
                }
            } catch {}
        }

        if (cookies.last_lat && cookies.last_lon) {
            const lat = Number(cookies.last_lat);
            const lon = Number(cookies.last_lon);
            if (!Number.isNaN(lat) && !Number.isNaN(lon)) {
                return { lat, lon, ts: Date.now(), source: "cookie" };
            }
        }
    } catch (e) {
        // ignore
    }
    return null;
}

export default {
    saveLocation,
    getStoredLocation,
    migrateFromCookiesIfPresent,
};
