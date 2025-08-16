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

function pickQuip(lat, lon) {
    const now = new Date();
    const daySeed = Number(
        `${now.getFullYear()}${now.getMonth() + 1}${now.getDate()}`
    );
    const latSeed = Math.round(lat || 0);
    const lonSeed = Math.round(lon || 0);
    const seed = daySeed + latSeed * 101 + lonSeed * 1009;
    const rnd = seededRandom(seed)();
    const available = CATEGORIES.filter((c) => (notes[c] || []).length > 0);
    if (available.length === 0) return "";
    const idx = Math.floor(rnd * available.length);
    const list = notes[available[idx]];
    const innerSeed = Math.floor(rnd * 100000);
    const innerRnd = seededRandom(innerSeed)();
    return list[Math.floor(innerRnd * list.length)];
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
            const quip = pickQuip(latNum, lonNum);
            return res.json({ cache_age: age, weather_quip: quip, weather });
        }
    }

    const apiKey = process.env.WEATHERAPI_KEY;
    if (!apiKey)
        return res
            .status(500)
            .json({ error: "WEATHERAPI_KEY not set in environment" });

    try {
        const url = `https://api.weatherapi.com/v1/forecast.json?key=${encodeURIComponent(
            apiKey
        )}&q=${latNum},${lonNum}&days=1&aqi=no&alerts=no`;
        const resp = await axios.get(url, { timeout: 5000 });
        const data = resp.data;
        cache.set(key, { ts: nowSec, data });
        const quip = pickQuip(latNum, lonNum);
        return res.json({ cache_age: 0, weather_quip: quip, weather: data });
    } catch (err) {
        console.error("weather fetch error", err && err.toString());
        return res.status(502).json({ error: "failed to fetch weather data" });
    }
});

app.listen(PORT, () => {
    console.log(`Empowering weather API listening on port ${PORT}`);
});
