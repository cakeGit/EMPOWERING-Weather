// UV bar rendering and ranking helpers

export function renderUV(uv) {
    const uvFill = document.getElementById("uvFill");
    const uvLabel = document.getElementById("uvLabel");
    if (!uvFill || !uvLabel) return;
    if (uv == null || Number.isNaN(uv)) {
        uvFill.style.width = "0%";
        uvFill.style.background = "transparent";
        uvLabel.textContent = "—";
        return;
    }
    const max = 11; // for percentage scale
    const pct = Math.min(100, Math.round((uv / max) * 100));
    uvFill.style.width = pct + "%";
    let label = "Low";
    let color = "#2ecc71"; // green
    if (uv >= 11) {
        label = "Extreme";
        color = "#7e0000";
    } else if (uv >= 8) {
        label = "Very high";
        color = "#ff3b30";
    } else if (uv >= 6) {
        label = "High";
        color = "#ff9500";
    } else if (uv >= 3) {
        label = "Moderate";
        color = "#ffd700";
    } else {
        label = "Low";
        color = "#2ecc71";
    }
    uvFill.style.background = color;
    uvLabel.textContent = label;
}

export function getUVRank(uv) {
    const val = Number(uv);
    if (Number.isNaN(val)) return { label: "", detail: "", type: "" };
    if (val <= 2)
        return {
            label: "Low",
            detail: "Low UV — minimal protection needed",
            type: "low",
        };
    if (val <= 5)
        return {
            label: "Moderate",
            detail: "Moderate UV — take precautions",
            type: "ok",
        };
    if (val <= 7)
        return {
            label: "High",
            detail: "High UV — protection recommended",
            type: "high",
        };
    if (val <= 10)
        return {
            label: "Very high",
            detail: "Very high UV — extra protection required",
            type: "very",
        };
    return {
        label: "Extreme",
        detail: "Extreme UV — avoid sun exposure",
        type: "extreme",
    };
}
