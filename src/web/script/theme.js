// Theme utilities: color blending and applying weather-driven theme

export function hexToRgb(h) {
    if (!h) return null;
    const input = String(h).trim();
    if (input.toLowerCase().startsWith("rgb")) return parseRgbString(input);
    const hex = input.replace(/^#/, "");

    // Support #RGB, #RGBA, #RRGGBB, #RRGGBBAA (ignore alpha if present)
    if (hex.length === 3) {
        return {
            r: parseInt(hex[0] + hex[0], 16),
            g: parseInt(hex[1] + hex[1], 16),
            b: parseInt(hex[2] + hex[2], 16),
        };
    }
    if (hex.length === 4) {
        return {
            r: parseInt(hex[0] + hex[0], 16),
            g: parseInt(hex[1] + hex[1], 16),
            b: parseInt(hex[2] + hex[2], 16),
        };
    }
    if (hex.length === 6 || hex.length === 8) {
        return {
            r: parseInt(hex.slice(0, 2), 16),
            g: parseInt(hex.slice(2, 4), 16),
            b: parseInt(hex.slice(4, 6), 16),
        };
    }
    return null;
}

export function parseRgbString(s) {
    // Supports: rgb(r,g,b), rgba(r,g,b,a), rgb(r g b / a), with numbers or percentages
    const m = s.match(
        /rgba?\(\s*([\d.]+%?)\s*[\s,]\s*([\d.]+%?)\s*[\s,]\s*([\d.]+%?)(?:\s*[\/,]\s*([\d.]+%?))?\s*\)/i
    );
    if (!m) return null;
    const to255 = (v) =>
        String(v).trim().endsWith("%")
            ? Math.round(Math.max(0, Math.min(100, parseFloat(v))) * 2.55)
            : Math.round(Math.max(0, Math.min(255, parseFloat(v))));
    return { r: to255(m[1]), g: to255(m[2]), b: to255(m[3]) };
}

export function shadeBlend(t, from, to) {
    try {
        const f = hexToRgb(from);
        const T = hexToRgb(to);
        if (!f || !T) return from;
        const tt = isFinite(Number(t)) ? Number(t) : 0;
        const r = Math.round(f.r + (T.r - f.r) * tt);
        const g = Math.round(f.g + (T.g - f.g) * tt);
        const b = Math.round(f.b + (T.b - f.b) * tt);
        const toHex = (n) =>
            Math.max(0, Math.min(255, n)).toString(16).padStart(2, "0");
        return `#${toHex(r)}${toHex(g)}${toHex(b)}`;
    } catch (e) {
        return from;
    }
}

export function applyWeatherTheme(cur, location) {
    if (!cur) return;
    const body = document.body;
    const cond = (cur.condition && cur.condition.text) || "";
    const text = String(cond).toLowerCase();
    const set = (name, value) => body.style.setProperty(name, value);

    let top = "#f3f7ff",
        mid = "#fbfdff",
        bottom = "#fff6f2";
    let b1 = "rgba(255,220,180,0.12)",
        b2 = "rgba(180,210,255,0.10)";

    const isNight = cur.is_day === 0;

    if (text.includes("sun") || text.includes("clear")) {
        top = "#fff9f0";
        mid = "#fffefc";
        bottom = "#fff6e8";
        b1 = "rgba(255,200,100,0.14)";
        b2 = "rgba(255,240,200,0.06)";
    } else if (text.includes("cloud") || text.includes("overcast")) {
        top = "#adadaeff";
        mid = "#d6d7d7ff";
        bottom = "#d3d5d6ff";
        b1 = "rgba(200,210,230,0.10)";
        b2 = "rgba(180,190,210,0.08)";
    } else if (text.includes("rain") || text.includes("drizzle")) {
        top = "#bed1ebff";
        mid = "#dcf0ffff";
        bottom = "#f8fbfd";
        b1 = "rgba(160,190,230,0.12)";
        b2 = "rgba(120,160,210,0.10)";
    } else if (text.includes("storm") || text.includes("thunder")) {
        top = "#adadaeff";
        mid = "#d6d7d7ff";
        bottom = "#d3d5d6ff";
        b1 = "rgba(140,150,180,0.12)";
        b2 = "rgba(100,110,130,0.12)";
    } else if (text.includes("snow")) {
        top = "#ffffffff";
        mid = "#e8eaebff";
        bottom = "#d7d7d7ff";
        b1 = "rgba(220,235,255,0.12)";
        b2 = "rgba(200,220,255,0.08)";
    } else if (text.includes("fog") || text.includes("mist")) {
        top = "#c0cddbff";
        mid = "#e3e5e7ff";
        bottom = "#f8f8f9";
        b1 = "rgba(200,200,210,0.08)";
        b2 = "rgba(220,220,230,0.06)";
    }

    const temp = Number(cur.temp_c);
    if (!Number.isNaN(temp)) {
        if (temp >= 28) {
            top = shadeBlend(0.08, top, "#fff0e6");
            mid = shadeBlend(0.05, mid, "#fff5ee");
        } else if (temp <= 8) {
            top = shadeBlend(0.08, top, "#eaf6ff");
            mid = shadeBlend(0.05, mid, "#f4fbff");
        }
    }

    if (isNight) {
        top = shadeBlend(0.9, top, "#0b2540");
        mid = shadeBlend(0.3, mid, "#071830");
        bottom = shadeBlend(0.1, bottom, "#061226");
        b1 = "rgba(80,110,150,0.10)";
        b2 = "rgba(50,80,120,0.06)";
    }

    set("--next-bg-top", top);
    set("--next-bg-mid", mid);
    set("--next-bg-bottom", bottom);
    set("--next-bubble1", b1);
    set("--next-bubble2", b2);

    const bodyEl = document.body;
    if (bodyEl._gradFadeTimer) {
        clearTimeout(bodyEl._gradFadeTimer);
        bodyEl._gradFadeTimer = null;
    }

    requestAnimationFrame(() => {
        bodyEl.classList.add("grad-fade");
        bodyEl._gradFadeTimer = setTimeout(() => {
            set(
                "--bg-top",
                getComputedStyle(bodyEl).getPropertyValue("--next-bg-top") ||
                    top
            );
            set(
                "--bg-mid",
                getComputedStyle(bodyEl).getPropertyValue("--next-bg-mid") ||
                    mid
            );
            set(
                "--bg-bottom",
                getComputedStyle(bodyEl).getPropertyValue("--next-bg-bottom") ||
                    bottom
            );
            set(
                "--bubble1",
                getComputedStyle(bodyEl).getPropertyValue("--next-bubble1") ||
                    b1
            );
            set(
                "--bubble2",
                getComputedStyle(bodyEl).getPropertyValue("--next-bubble2") ||
                    b2
            );
            bodyEl.classList.remove("grad-fade");
            try {
                bodyEl.style.removeProperty("--next-bg-top");
            } catch (e) {}
            try {
                bodyEl.style.removeProperty("--next-bg-mid");
            } catch (e) {}
            try {
                bodyEl.style.removeProperty("--next-bg-bottom");
            } catch (e) {}
            try {
                bodyEl.style.removeProperty("--next-bubble1");
            } catch (e) {}
            try {
                bodyEl.style.removeProperty("--next-bubble2");
            } catch (e) {}
            bodyEl._gradFadeTimer = null;
        }, 920);
    });
}
