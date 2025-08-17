import { ensureChartJsLoaded } from "./utils.js";
import { solarSineForHours } from "./solar.js";

let _chartInstances = [];

function clearPreviousCharts() {
    _chartInstances.forEach((c) => {
        try {
            c.destroy();
        } catch (e) {}
    });
    _chartInstances = [];
}

function createCard(id, title) {
    const row = document.getElementById("statsCardsRow");
    const card = document.createElement("div");
    card.className = "stats-chart-card card";
    card.id = id + "_card";
    card.innerHTML = `
        <div class="card-title">${title}</div>
        <canvas id="${id}" class="stats-chart-canvas" aria-hidden="false"></canvas>`;
    row.appendChild(card);
    return document.getElementById(id);
}

export function renderStatsChart(next24, payload) {
    const row = document.getElementById("statsCardsRow");
    if (!row) return;
    row.innerHTML = "";
    row.classList.remove("hidden");

    const labels = next24.map((h) => (h.time ? h.time.split(" ")[1] : ""));
    const temps = next24.map((h) =>
        h.temp_c == null ? null : Number(h.temp_c)
    );
    const hums = next24.map((h) =>
        h.humidity == null ? null : Number(h.humidity)
    );
    const uvs = next24.map((h) => (h.uv == null ? null : Number(h.uv)));
    const rains = next24.map((h) => {
        if (h.precip_mm != null) return Number(h.precip_mm);
        if (h.totalprecip_mm != null) return Number(h.totalprecip_mm);
        if (h.chance_of_rain != null) return Number(h.chance_of_rain);
        if (h.chanceofrain != null) return Number(h.chanceofrain);
        return 0;
    });

    const sun = solarSineForHours(
        next24,
        payload.weather &&
            payload.weather.forecast &&
            payload.weather.forecast.forecastday &&
            payload.weather.forecast.forecastday[0] &&
            payload.weather.forecast.forecastday[0].astro
            ? payload.weather.forecast.forecastday[0]
            : null
    ).map((v) => Math.round(v * 100));

    ensureChartJsLoaded(() => {
        try {
            clearPreviousCharts();
            const Chart = window.Chart;

            // Helper to fade canvas in once chart data/animation starts
            function fadeCanvas(canvas) {
                try {
                    // Using requestAnimationFrame to ensure DOM painted; small timeout lets Chart begin its animation
                    requestAnimationFrame(() => {
                        setTimeout(() => {
                            canvas.style.opacity = "1";
                        }, 20);
                    });
                } catch (e) {
                    // ignore
                }
            }

            // Common options to enable tooltip at cursor / hover value
            const commonOptions = {
                responsive: true,
                maintainAspectRatio: false,
                plugins: {
                    legend: { display: false },
                    tooltip: { enabled: true, mode: "index", intersect: false },
                },
                interaction: { mode: "index", intersect: false },
                layout: { padding: { bottom: 18 } },
            };

            // Temperature chart
            const tempCanvas = createCard("stats_temp", "Temperature (°C)");
            const tctx = tempCanvas.getContext("2d");
            const tempChart = new Chart(tctx, {
                type: "line",
                data: {
                    labels,
                    datasets: [
                        {
                            label: "Temp (°C)",
                            data: temps,
                            borderColor: "rgba(255,99,132,0.9)",
                            backgroundColor: "rgba(255,99,132,0.2)",
                            tension: 0.2,
                            pointRadius: 0,
                            fill: false,
                            // animate point/line/fill to smooth appearance
                            animations: { tension: { duration: 600 } },
                        },
                    ],
                },
                options: {
                    …commonOptions,
                    scales: {
                        x: { display: true, ticks: { padding: 6 } },
                        y: { ticks: { callback: (v) => v + "°C" } },
                    },
                },
            });
            fadeCanvas(tempCanvas);
            _chartInstances.push(tempChart);

            // Rain chart
            const rainCanvas = createCard("stats_rain", "Rain (mm)");
            const rctx = rainCanvas.getContext("2d");
            const rainChart = new Chart(rctx, {
                type: "bar",
                data: {
                    labels,
                    datasets: [
                        {
                            label: "Rain (mm)",
                            data: rains,
                            backgroundColor: "rgba(54,162,235,0.6)",
                            borderColor: "rgba(54,162,235,0.9)",
                            animations: { tension: { duration: 600 } },
                        },
                    ],
                },
                options: {
                    …commonOptions,
                    scales: {
                        x: { display: true, ticks: { padding: 6 } },
                        y: {
                            beginAtZero: true,
                            ticks: { callback: (v) => v + " mm" },
                        },
                    },
                },
            });
            fadeCanvas(rainCanvas);
            _chartInstances.push(rainChart);

            // Sun chart (relative)
            const sunCanvas = createCard("stats_sun", "Sun (relative)");
            const sctx = sunCanvas.getContext("2d");
            const sunChart = new Chart(sctx, {
                type: "line",
                data: {
                    labels,
                    datasets: [
                        {
                            label: "Sun (relative)",
                            data: sun,
                            borderColor: "rgba(255,204,0,0.9)",
                            backgroundColor: "rgba(255,204,0,0.2)",
                            tension: 0.4,
                            pointRadius: 0,
                            fill: true,
                            animations: { tension: { duration: 700 } },
                        },
                    ],
                },
                options: {
                    …commonOptions,
                    scales: {
                        x: { display: true, ticks: { padding: 6 } },
                        y: { min: 0, max: 100, ticks: { display: false } },
                    },
                },
            });
            fadeCanvas(sunCanvas);
            _chartInstances.push(sunChart);

            // UV chart
            const uvCanvas = createCard("stats_uv", "UV index");
            const uctx = uvCanvas.getContext("2d");
            const uvChart = new Chart(uctx, {
                type: "line",
                data: {
                    labels,
                    datasets: [
                        {
                            label: "UV index",
                            data: uvs,
                            borderColor: "rgba(255,159,64,0.95)",
                            backgroundColor: "rgba(255,159,64,0.2)",
                            tension: 0.2,
                            pointRadius: 0,
                            fill: false,
                            animations: { tension: { duration: 600 } },
                        },
                    ],
                },
                options: {
                    …commonOptions,
                    scales: {
                        x: { display: true, ticks: { padding: 6 } },
                        y: { beginAtZero: true },
                    },
                },
            });
            fadeCanvas(uvCanvas);
            _chartInstances.push(uvChart);

            // Humidity chart
            const humCanvas = createCard("stats_hum", "Humidity (%)");
            const hctx = humCanvas.getContext("2d");
            const humChart = new Chart(hctx, {
                type: "line",
                data: {
                    labels,
                    datasets: [
                        {
                            label: "Humidity (%)",
                            data: hums,
                            borderColor: "rgba(0,150,200,0.9)",
                            backgroundColor: "rgba(0,150,200,0.2)",
                            tension: 0.2,
                            pointRadius: 0,
                            fill: false,
                            animations: { tension: { duration: 600 } },
                        },
                    ],
                },
                options: {
                    …commonOptions,
                    scales: {
                        x: { display: true, ticks: { padding: 6 } },
                        y: {
                            beginAtZero: true,
                            ticks: { callback: (v) => v + "%" },
                        },
                    },
                },
            });
            fadeCanvas(humCanvas);
            _chartInstances.push(humChart);
        } catch (e) {
            console.error("renderStatsChart: chart construction failed", e);
        }
    });
}
