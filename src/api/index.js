// Clean implementation: serve static frontend and provide /api and /health
const express = require("express");
const axios = require("axios");
const fs = require("fs");
const path = require("path");
require("dotenv").config({
    path: path.join(__dirname, "..", "..", ".env"),
});

const app = express();
const PORT = process.env.PORT || 8302;

// Permissive CORS middleware (allows all origins). Useful for development or
// when the API must be accessible from any origin. Remove or tighten in prod.
app.use((req, res, next) => {
    res.setHeader("Access-Control-Allow-Origin", "*");
    res.setHeader(
        "Access-Control-Allow-Methods",
        "GET,POST,PUT,PATCH,DELETE,OPTIONS"
    );
    res.setHeader(
        "Access-Control-Allow-Headers",
        "Origin, X-Requested-With, Content-Type, Accept, Authorization"
    );
    // Allow credentials header if clients need it (note: Access-Control-Allow-Origin can't be * with credentials)
    res.setHeader("Access-Control-Allow-Credentials", "true");
    if (req.method === "OPTIONS") {
        // Immediately respond to preflight requests
        return res.sendStatus(204);
    }
    next();
});

// Serve static frontend from src/web
const WEB_DIR = path.join(__dirname, "..", "web");
app.use(express.static(WEB_DIR));

// Simple in-memory cache keyed by rounded lat/lon string
// Cache entries: { ts: epoch_seconds, data: weatherResponse }
const cache = new Map();
const CACHE_TTL_SECONDS = 3600; // 1 hour

// Load weather notes files into memory (weather_notes lives at repo root)
const NOTES_DIR = path.join(__dirname, "..", "..", "weather_notes");
const CATEGORIES = [
    "cloudy",
    "cold",
    "foggy",
    "hot",
    "humid",
    "mild",
    "rainy",
    "snowy",
    "stormy",
    "thunderstorm",
    "windy",
];

const notes = {};
for (const cat of CATEGORIES) {
    const file = path.join(NOTES_DIR, `${cat}.txt`);
    try {
        const raw = fs.readFileSync(file, "utf8");
        notes[cat] = raw.split(/\r?\n/).filter(Boolean);
    } catch (err) {
        notes[cat] = [];
        console.warn(`Warning: could not read notes file for ${cat}: ${file}`);
    }
}

function seededRandom(seed) {
    // Simple mulberry32 PRNG
    return function () {
        let t = (seed += 0x6d2b79f5);
        t = Math.imul(t ^ (t >>> 15), t | 1);
        t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
        return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
    };
}

function pickQuip(weather, lat, lon) {
    // Determine a best-fit category from the weather response
    function determineCategory(data) {
        if (!data || !data.current) return null;
        const cur = data.current;
        const text = (
            (cur.condition && cur.condition.text) ||
            ""
        ).toLowerCase();
        const temp = typeof cur.temp_c === "number" ? cur.temp_c : null;
        const humidity = typeof cur.humidity === "number" ? cur.humidity : null;
        const wind = typeof cur.wind_kph === "number" ? cur.wind_kph : 0;
        const precip = typeof cur.precip_mm === "number" ? cur.precip_mm : 0;
        const cloud = typeof cur.cloud === "number" ? cur.cloud : 0;

        // Priority-ordered checks
        if (/thunder/.test(text)) return "thunderstorm";
        if (/storm|squall/.test(text)) return "stormy";
        if (/snow|sleet|blizzard|ice/.test(text)) return "snowy";
        if (/rain|drizzle|shower/.test(text) || precip > 0.5) return "rainy";
        if (/fog|mist|haze|smoke/.test(text)) return "foggy";
        if (wind >= 40) return "windy";
        if (temp !== null && temp >= 30) return "hot";
        if (temp !== null && temp <= 0) return "cold";
        if (humidity !== null && humidity >= 85 && temp !== null && temp >= 20)
            return "humid";
        if (cloud >= 70 || /cloud|overcast/.test(text)) return "cloudy";
        if (temp !== null && temp >= 10 && temp <= 25) return "mild";
        if (temp !== null && temp <= 5) return "cold";

        return null;
    }

    const category = determineCategory(weather);
    const available = CATEGORIES.filter((c) => (notes[c] || []).length > 0);

    // If determineCategory returns a category we have notes for, use it.
    let chosenCategory = null;
    if (category && available.includes(category)) {
        chosenCategory = category;
    } else if (available.length > 0) {
        // Fallback: pick a deterministic category based on date + rounded coords
        const now = new Date();
        const daySeed = Number(
            `${now.getFullYear()}${now.getMonth() + 1}${now.getDate()}`
        );
        const coordSeed =
            Math.round((lat || 0) * 100) + Math.round((lon || 0) * 100);
        const seed = daySeed + coordSeed;
        const rnd = seededRandom(seed)();
        chosenCategory = available[Math.floor(rnd * available.length)];
    }

    if (!chosenCategory) return "";

    // Pick a deterministic quip from the chosen category using the same seed
    const now = new Date();
    const daySeed2 = Number(
        `${now.getFullYear()}${now.getMonth() + 1}${now.getDate()}`
    );
    const seed2 =
        daySeed2 +
        Math.round((lat || 0) * 100) +
        Math.round((lon || 0) * 100) +
        chosenCategory.length;
    const rnd2 = seededRandom(seed2)();
    const list = notes[chosenCategory] || [];
    if (list.length === 0) return "";
    return list[Math.floor(rnd2 * list.length)];
}

// Health endpoint
app.get("/health", (req, res) => res.json({ status: "ok" }));

// API endpoint (moved from root to /api)
app.get("/api", async (req, res) => {
    const { lat, lon } = req.query;
    if (!lat || !lon)
        return res
            .status(400)
            .json({ error: "missing lat and lon query params" });
    const latNum = Number(lat);
    const lonNum = Number(lon);
    if (Number.isNaN(latNum) || Number.isNaN(lonNum))
        return res.status(400).json({ error: "lat and lon must be numbers" });

    const key = `${Math.round(latNum)}:${Math.round(lonNum)}`;
    const nowSec = Math.floor(Date.now() / 1000);

    if (cache.has(key)) {
        const entry = cache.get(key);
        const age = nowSec - entry.ts;
        if (age < CACHE_TTL_SECONDS) {
            const weather = entry.data;
            const quip = pickQuip(weather, latNum, lonNum);
            return res.json({ cache_age: age, weather_quip: quip, weather });
        }
    }

    const apiKey = process.env.WEATHERAPI_KEY;
    if (!apiKey)
        return res
            .status(500)
            .json({ error: "WEATHERAPI_KEY not set in environment" });

    try {
        // Fetch 2 days to guarantee at least 24 hours of hourly forecast from "now"
        // (if it's late in the day, a single-day forecast might not include a full next 24h)
        const url = `https://api.weatherapi.com/v1/forecast.json?key=${encodeURIComponent(
            apiKey
        )}&q=${latNum},${lonNum}&days=2&aqi=no&alerts=no`;
        const resp = await axios.get(url, { timeout: 5000 });
        const data = resp.data;
        cache.set(key, { ts: nowSec, data });
        const quip = pickQuip(data, latNum, lonNum);
        return res.json({ cache_age: 0, weather_quip: quip, weather: data });
    } catch (err) {
        console.error("weather fetch error", err && err.toString());
        return res.status(502).json({ error: "failed to fetch weather data" });
    }
});

app.listen(PORT, () => {
    console.log(`OverCast API listening on port ${PORT}`);
});
