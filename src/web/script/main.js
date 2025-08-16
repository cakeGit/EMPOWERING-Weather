import {
    safeSetTextById,
    safeSetAttrById,
    humanizeAge,
    domSnapshot,
    installGlobalErrorHandlers,
} from "./utils.js";
import { applyWeatherTheme } from "./theme.js";
import { renderUV, getUVRank } from "./uv.js";
import { getHumidityRank, getWindRank } from "./rankings.js";
import { renderStatsChart } from "./statsChart.js";
import { installDebugModalHandlers } from "./debugModal.js";

(function () {
    installGlobalErrorHandlers();
    installDebugModalHandlers();

    const $ = (id) => document.getElementById(id);
    const status = $("status");
    const result = $("result");
    const retryBtn = document.getElementById("retryLoc");

    // Helper: extract rain chance percentage from various possible fields
    function extractRainChance(h) {
        if (!h) return null;
        if (h.daily_chance_of_rain != null)
            return Number(h.daily_chance_of_rain);
        if (h.chance_of_rain != null) return Number(h.chance_of_rain);
        if (h.chanceofrain != null) return Number(h.chanceofrain);
        if (h.pop != null) return Number(h.pop);
        // some APIs use will_it_rain as 0/1 hourly flag — translate to 0/100
        if (h.will_it_rain != null) return h.will_it_rain ? 100 : 0;
        return null;
    }

    function setStatus(msg) {
        const ok = safeSetTextById("status", msg);
        if (!ok) {
            console.warn(
                "setStatus: #status element not found; fallback to console"
            );
            console.log("Status:", msg);
        }
    }

    async function fetchWeather(lat, lon) {
        setStatus("Fetching...");
        try {
            const res = await fetch(
                `https://weather.oreostack.uk/api?lat=${encodeURIComponent(
                    lat
                )}&lon=${encodeURIComponent(lon)}`
            );
            if (!res.ok) {
                const err = await res
                    .json()
                    .catch(() => ({ error: res.statusText }));
                setStatus(
                    "Error fetching weather info: " +
                        (err.error || res.statusText)
                );
                result.classList.add("hidden");
                return;
            }
            const j = await res.json();
            render(j);
            setStatus("Last fetched: just now");
            try {
                status && status.classList.add("hidden");
            } catch (err) {
                console.error("fetchWeather: failed to hide #status", err);
            }
        } catch (e) {
            console.error("fetchWeather: network error", {
                message: e.message,
                error: e,
                lat,
                lon,
                dom: domSnapshot(),
            });
            setStatus(
                "Network error, couldn't fetch weather info: " +
                    (e && e.message ? e.message : String(e))
            );
            //Set debug data to the response
            const rawEl = document.getElementById("raw");
            if (rawEl) rawEl.textContent = JSON.stringify(res, null, 2);
            try {
                result && result.classList.add("hidden");
            } catch (err) {
                console.error("Error hiding result after network failure", err);
            }
        }
    }

    function render(payload) {
        try {
            result.classList.remove("hidden");
            safeSetTextById(
                "quip",
                payload.weather_quip
                    ? '"' + payload.weather_quip + '"'
                    : '"Something snarky about the weather"'
            );

            const locText =
                (payload.weather &&
                    payload.weather.location &&
                    `${payload.weather.location.name}, ${payload.weather.location.region}`) ||
                "—";
            safeSetTextById("locName", locText);

            const rawAge = payload.cache_age;
            const ageText =
                rawAge == null
                    ? "Data fetched — ago"
                    : `Data fetched ${humanizeAge(rawAge)} ago`;
            if (!safeSetTextById("cacheAgeText", ageText)) {
                safeSetTextById(
                    "cacheAge",
                    rawAge == null ? "—" : String(rawAge)
                );
            }

            const cur = payload.weather && payload.weather.current;
            if (cur) {
                try {
                    applyWeatherTheme(
                        cur,
                        payload.weather && payload.weather.location
                    );
                } catch (themeErr) {
                    console.error("applyWeatherTheme failed", themeErr);
                }
                safeSetTextById("tempC", cur.temp_c ?? "—");
                const icon =
                    cur.condition && cur.condition.icon
                        ? cur.condition.icon.startsWith("//")
                            ? "https:" + cur.condition.icon
                            : cur.condition.icon
                        : "";
                if (!document.getElementById("condIcon")) {
                    const condContainer =
                        document.querySelector(".cond") ||
                        document.querySelector("#result .cond");
                    if (condContainer) {
                        const img = document.createElement("img");
                        img.id = "condIcon";
                        img.src = icon || "";
                        img.alt = (cur.condition && cur.condition.text) || "";
                        condContainer.insertBefore(
                            img,
                            condContainer.firstChild
                        );
                    }
                }
                safeSetAttrById("condIcon", "src", icon);
                safeSetAttrById(
                    "condIcon",
                    "alt",
                    (cur.condition && cur.condition.text) || ""
                );
                safeSetTextById("feels", cur.feelslike_c ?? "—");
                safeSetTextById("humidity", cur.humidity ?? "—");
                safeSetTextById("wind", cur.wind_kph ?? "—");

                // Current rain chance: prefer percent fields, fall back to precip_mm presence
                const curRainPct = extractRainChance(cur);
                if (curRainPct != null) {
                    safeSetTextById("rainChance", `${curRainPct}%`);
                } else if (cur.precip_mm != null) {
                    // show mm as a fallback when percent is not provided
                    safeSetTextById("rainChance", `${cur.precip_mm} mm`);
                } else {
                    safeSetTextById("rainChance", "—");
                }

                const humRank = getHumidityRank(cur.humidity);
                const windRank = getWindRank(cur.wind_kph);
                const humEl = document.getElementById("humidityRank");
                const windEl = document.getElementById("windRank");
                if (humEl) {
                    humEl.textContent = humRank.label;
                    humEl.title = humRank.detail;
                    humEl.className = "rank " + humRank.type;
                }
                if (windEl) {
                    windEl.textContent = windRank.label;
                    windEl.title = windRank.detail;
                    windEl.className = "rank " + windRank.type;
                }

                const uvVal = cur.uv == null ? null : Number(cur.uv);
                safeSetTextById("uvValue", uvVal == null ? "—" : String(uvVal));
                renderUV(uvVal);
                const uvEl = document.getElementById("uvValue");
                if (uvEl) {
                    const uvRank = getUVRank(uvVal);
                    uvEl.title = uvRank.detail;
                    uvEl.className = "rank uv-value " + (uvRank.type || "");
                }
            }

            const forecastEl = document.getElementById("forecastRow");
            const days =
                payload.weather &&
                payload.weather.forecast &&
                payload.weather.forecast.forecastday;
            if (forecastEl) {
                forecastEl.innerHTML = "";
                const hours = [];
                if (Array.isArray(days)) {
                    days.forEach((d) => {
                        if (Array.isArray(d.hour)) hours.push(...d.hour);
                    });
                }
                const startEpoch =
                    (payload.weather &&
                        payload.weather.current &&
                        payload.weather.current.last_updated_epoch) ||
                    (payload.weather &&
                        payload.weather.location &&
                        payload.weather.location.localtime_epoch) ||
                    Math.floor(Date.now() / 1000);
                if (hours.length) {
                    const byEpoch = hours
                        .map((h) => ({ item: h, epoch: h.time_epoch || 0 }))
                        .sort((a, b) => a.epoch - b.epoch)
                        .map((x) => x.item);
                    let idx = byEpoch.findIndex(
                        (h) => (h.time_epoch || 0) >= startEpoch
                    );
                    if (idx === -1) idx = 0;
                    const next24 = byEpoch.slice(idx, idx + 24);
                    if (next24.length) {
                        forecastEl.classList.remove("hidden");
                        forecastEl.classList.add("hourly");
                        next24.forEach((hr) => {
                            const timeLabel = hr.time
                                ? hr.time.split(" ")[1]
                                : hr.hour || "";
                            const icon =
                                hr.condition && hr.condition.icon
                                    ? hr.condition.icon
                                    : "";
                            const iconUrl = icon.startsWith("//")
                                ? "https:" + icon
                                : icon;
                            const tc = hr.temp_c == null ? "\u2014" : hr.temp_c;
                            const card = document.createElement("div");
                            card.className = "forecast-card hour-card";
                            const rainPct = extractRainChance(hr);
                            card.innerHTML = `
                                <div class="f-date">${timeLabel}</div>
                                <img class="f-icon" src="${iconUrl}" alt="" />
                                <div class="f-temps"><span class="f-max">${tc}\u00b0C</span></div>
                                ${
                                    rainPct == null
                                        ? ""
                                        : `<div class="f-rain"><span class="rain-pct">${rainPct}%</span> chance</div>`
                                }`;
                            forecastEl.appendChild(card);
                        });
                        try {
                            renderStatsChart(next24, payload);
                        } catch (chartErr) {
                            console.error(
                                "render: failed to render stats chart",
                                chartErr
                            );
                        }
                    }
                } else if (Array.isArray(days) && days.length) {
                    forecastEl.classList.remove("hidden");
                    forecastEl.classList.remove("hourly");
                    days.forEach((fd) => {
                        const date = fd.date || "";
                        const day = fd.day || {};
                        const icon =
                            (day.condition && day.condition.icon) || "";
                        const iconUrl = icon.startsWith("//")
                            ? "https:" + icon
                            : icon;
                        const maxt =
                            day.maxtemp_c == null ? "\u2014" : day.maxtemp_c;
                        const mint =
                            day.mintemp_c == null ? "\u2014" : day.mintemp_c;
                        const rainPct = extractRainChance(day);
                        const card = document.createElement("div");
                        card.className = "forecast-card";
                        card.innerHTML = `
                            <div class="f-date">${date}</div>
                            <img class="f-icon" src="${iconUrl}" alt="" />
                            <div class="f-temps"><span class="f-max">${maxt}\u00b0C</span> <span class="f-min">${mint}\u00b0C</span></div>
                            ${
                                rainPct == null
                                    ? ""
                                    : `<div class="f-rain"><span class="rain-pct">${rainPct}%</span> chance</div>`
                            }`;
                        forecastEl.appendChild(card);
                    });
                } else {
                    forecastEl.classList.add("hidden");
                }
            }

            const rawEl = document.getElementById("raw");
            if (rawEl) rawEl.textContent = JSON.stringify(payload, null, 2);
        } catch (err) {
            console.error("render: error while rendering payload", {
                err,
                payload,
                dom: domSnapshot(),
            });
            throw err;
        }
    }

    async function startWithGeolocation() {
        setStatus("Locating...");
        const hasCap = !!(
            window.Capacitor &&
            window.Capacitor.Plugins &&
            (window.Capacitor.Plugins.Geolocation ||
                window.Capacitor.isNativePlatform?.())
        );

        const persistLatLon = async (lat, lon) => {
            // Best-effort: Capacitor Preferences when available, else localStorage
            try {
                if (
                    window.Capacitor &&
                    window.Capacitor.Plugins &&
                    window.Capacitor.Plugins.Preferences
                ) {
                    const { Preferences } = window.Capacitor.Plugins;
                    await Preferences.set({ key: "lat", value: String(lat) });
                    await Preferences.set({ key: "lon", value: String(lon) });
                } else if (window.localStorage) {
                    localStorage.setItem("lat", String(lat));
                    localStorage.setItem("lon", String(lon));
                }
            } catch (e) {
                console.warn("Failed to persist lat/lon", e);
            }
        };

        try {
            if (hasCap && window.Capacitor.Plugins.Geolocation) {
                const { Geolocation } = window.Capacitor.Plugins;
                try {
                    // Request permissions on native if needed
                    if (Geolocation.requestPermissions) {
                        await Geolocation.requestPermissions();
                    }
                } catch (_) {}
                const pos = await Geolocation.getCurrentPosition({
                    enableHighAccuracy: false,
                    timeout: 10000,
                });
                const { latitude, longitude } = pos.coords || pos || {};
                if (latitude != null && longitude != null) {
                    retryBtn && retryBtn.classList.add("hidden");
                    await persistLatLon(latitude, longitude);
                    fetchWeather(latitude, longitude);
                    return;
                }
                throw new Error("No coordinates from Capacitor Geolocation");
            }
        } catch (err) {
            console.warn(
                "Capacitor Geolocation failed, falling back to browser geolocation",
                err
            );
        }

        if (!navigator.geolocation) {
            setStatus("Geolocation not supported by your device.");
            return;
        }

        navigator.geolocation.getCurrentPosition(
            async (pos) => {
                const { latitude, longitude } = pos.coords;
                retryBtn && retryBtn.classList.add("hidden");
                await persistLatLon(latitude, longitude);
                fetchWeather(latitude, longitude);
            },
            (err) => {
                setStatus("Location error: " + err.message);
                retryBtn && retryBtn.classList.remove("hidden");
            },
            { enableHighAccuracy: false, timeout: 10000 }
        );
    }

    startWithGeolocation();

    // Periodic background location update (every THIRTY_SECONDS, best-effort)
    let _periodicLocTimer = null;
    function schedulePeriodicLocationUpdates() {
        if (_periodicLocTimer) clearInterval(_periodicLocTimer);
        const THIRTY_SECONDS = 30 * 1000;
        _periodicLocTimer = setInterval(async () => {
            try {
                const hasCap = !!(
                    window.Capacitor &&
                    window.Capacitor.Plugins &&
                    (window.Capacitor.Plugins.Geolocation ||
                        window.Capacitor.isNativePlatform?.())
                );
                let lat = null,
                    lon = null;
                if (hasCap && window.Capacitor.Plugins.Geolocation) {
                    const { Geolocation } = window.Capacitor.Plugins;
                    try {
                        if (Geolocation.requestPermissions)
                            await Geolocation.requestPermissions();
                    } catch (_) {}
                    const pos = await Geolocation.getCurrentPosition({
                        enableHighAccuracy: false,
                        timeout: 10000,
                    });
                    const c = pos && (pos.coords || pos);
                    if (c && c.latitude != null && c.longitude != null) {
                        lat = c.latitude;
                        lon = c.longitude;
                    }
                } else if (navigator.geolocation) {
                    const pos = await new Promise((res, rej) =>
                        navigator.geolocation.getCurrentPosition(res, rej, {
                            enableHighAccuracy: false,
                            timeout: 10000,
                        })
                    );
                    if (pos && pos.coords) {
                        lat = pos.coords.latitude;
                        lon = pos.coords.longitude;
                    }
                }
                if (lat != null && lon != null) {
                    try {
                        if (
                            window.Capacitor &&
                            window.Capacitor.Plugins &&
                            window.Capacitor.Plugins.Preferences
                        ) {
                            const { Preferences } = window.Capacitor.Plugins;
                            await Preferences.set({
                                key: "lat",
                                value: String(lat),
                            });
                            await Preferences.set({
                                key: "lon",
                                value: String(lon),
                            });
                        } else if (window.localStorage) {
                            localStorage.setItem("lat", String(lat));
                            localStorage.setItem("lon", String(lon));
                        }
                    } catch (e) {}
                    try {
                        fetchWeather(lat, lon);
                    } catch (e) {}
                }
            } catch (_) {
                /* ignore */
            }
        }, ONE_HOUR);
    }
    schedulePeriodicLocationUpdates();

    if (retryBtn) {
        retryBtn.addEventListener("click", () => {
            retryBtn.classList.add("hidden");
            startWithGeolocation();
        });
    }
})();
