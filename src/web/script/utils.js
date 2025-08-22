// Shared utilities: DOM safe setters, formatting, and small helpers

export function safeSetTextById(id, text) {
    const el = document.getElementById(id);
    if (!el) {
        console.error(
            `safeSetTextById: element with id="${id}" is null. text=`,
            text,
            new Error().stack
        );
        try {
            if (id === "condText") {
                const condContainer =
                    document.querySelector(".cond") ||
                    document.querySelector("#result .cond");
                if (condContainer) {
                    const d = document.createElement("div");
                    d.id = "condText";
                    d.textContent = text || "";
                    condContainer.appendChild(d);
                    return true;
                }
            }
            // small DOM snapshot for diagnostics
            const keys = [
                "status",
                "result",
                "locName",
                "cacheAgeText",
                "forecastRow",
                "condText",
                "condIcon",
                "uvValue",
            ];
            const snap = {};
            keys.forEach((k) => {
                const e = document.getElementById(k);
                snap[k] = e ? e.outerHTML.slice(0, 200) : null;
            });
            console.info("DOM snapshot (truncated outerHTML):", snap);
        } catch (dumpErr) {
            console.error("Error creating DOM snapshot", dumpErr);
        }
        return false;
    }
    try {
        el.textContent = text;
        return true;
    } catch (err) {
        console.error(`safeSetTextById: failed setting textContent on #${id}`, {
            err,
            id,
            text,
        });
        throw err;
    }
}

export function safeSetAttrById(id, prop, value) {
    let el = document.getElementById(id);
    if (!el) {
        if (id === "condIcon") {
            try {
                const condContainer =
                    document.querySelector(".cond") ||
                    document.querySelector("#result .cond") ||
                    document.getElementById("result");
                if (condContainer) {
                    const img = document.createElement("img");
                    img.id = "condIcon";
                    img.src = "";
                    img.alt = "";
                    condContainer.insertBefore(img, condContainer.firstChild);
                    el = img;
                }
            } catch (createErr) {
                console.error(
                    "safeSetAttrById: failed to create fallback #condIcon",
                    createErr
                );
            }
        }
        if (!el) {
            console.error(
                `safeSetAttrById: element with id="${id}" is null. prop=${prop} value=`,
                value,
                new Error().stack
            );
            console.info("DOM snapshot (truncated):", domSnapshot());
            return false;
        }
    }
    try {
        el[prop] = value;
        return true;
    } catch (err) {
        console.error(`safeSetAttrById: failed setting ${prop} on #${id}`, {
            err,
            id,
            prop,
            value,
        });
        throw err;
    }
}

export function humanizeAge(seconds) {
    if (seconds == null || Number.isNaN(Number(seconds))) return "—";
    const s = Number(seconds);
    if (s < 60) return `${s}s`;
    const m = Math.floor(s / 60);
    if (m < 60) return `${m}m`;
    const h = Math.floor(m / 60);
    if (h < 24) return `${h}h`;
    const d = Math.floor(h / 24);
    return `${d}d`;
}

export function domSnapshot() {
    try {
        const keys = [
            "status",
            "result",
            "locName",
            "cacheAgeText",
            "forecastRow",
            "quip",
            "raw",
        ];
        const snap = {};
        keys.forEach((k) => {
            const e = document.getElementById(k);
            snap[k] = e ? e.outerHTML.slice(0, 200) : null;
        });
        return snap;
    } catch (err) {
        return { error: String(err) };
    }
}

export function ensureChartJsLoaded(cb) {
    if (window.Chart) return cb();
    const s = document.createElement("script");
    s.src = "https://cdn.jsdelivr.net/npm/chart.js@4.4.0/dist/chart.umd.min.js";
    s.crossOrigin = "anonymous";
    s.onload = () => cb();
    s.onerror = () => console.warn("Failed to load Chart.js from CDN");
    document.head.appendChild(s);
}

// Cookie utility functions for location caching
export function setCookie(name, value, daysToExpire = 365) {
    const date = new Date();
    date.setTime(date.getTime() + (daysToExpire * 24 * 60 * 60 * 1000));
    const expires = `expires=${date.toUTCString()}`;
    document.cookie = `${name}=${value}; ${expires}; path=/; SameSite=Lax`;
}

export function getCookie(name) {
    const nameEQ = name + "=";
    const cookies = document.cookie.split(';');
    for (let i = 0; i < cookies.length; i++) {
        let cookie = cookies[i];
        while (cookie.charAt(0) === ' ') cookie = cookie.substring(1);
        if (cookie.indexOf(nameEQ) === 0) {
            return cookie.substring(nameEQ.length);
        }
    }
    return null;
}

export function deleteCookie(name) {
    document.cookie = `${name}=; expires=Thu, 01 Jan 1970 00:00:00 UTC; path=/;`;
}

// Location cache management utilities
export function getCachedLocation() {
    const lat = getCookie('weather_lat');
    const lon = getCookie('weather_lon');
    if (lat && lon) {
        const latitude = parseFloat(lat);
        const longitude = parseFloat(lon);
        if (!isNaN(latitude) && !isNaN(longitude)) {
            return { latitude, longitude };
        }
    }
    return null;
}

export function setCachedLocation(latitude, longitude) {
    setCookie('weather_lat', latitude.toString(), 365);
    setCookie('weather_lon', longitude.toString(), 365);
}

export function locationsDiffer(loc1, loc2, tolerance = 1.0) {
    if (!loc1 || !loc2) return true;
    const latDiff = Math.abs(loc1.latitude - loc2.latitude);
    const lonDiff = Math.abs(loc1.longitude - loc2.longitude);
    return latDiff > tolerance || lonDiff > tolerance;
}

export function showCacheNote() {
    const noteEl = document.getElementById('cacheNote');
    if (noteEl) {
        noteEl.style.display = 'inline';
    }
}

export function hideCacheNote() {
    const noteEl = document.getElementById('cacheNote');
    if (noteEl) {
        noteEl.style.display = 'none';
    }
}

// Global error handlers hook using domSnapshot
export function installGlobalErrorHandlers() {
    window.addEventListener("unhandledrejection", (ev) => {
        console.error("unhandledrejection captured", {
            reason: ev.reason,
            dom: domSnapshot(),
        });
    });
}
