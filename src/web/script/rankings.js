// Rankings for humidity and wind

export function getHumidityRank(h) {
    const val = Number(h);
    if (Number.isNaN(val)) return { label: "", detail: "", type: "" };
    if (val < 30)
        return {
            label: "Dry",
            detail: "Low humidity — air may feel dry",
            type: "low",
        };
    if (val < 60)
        return {
            label: "Comfortable",
            detail: "Pleasant humidity",
            type: "ok",
        };
    if (val < 75)
        return {
            label: "Humid",
            detail: "Moderately humid — may feel sticky",
            type: "high",
        };
    return {
        label: "Very humid",
        detail: "High humidity — can feel oppressive",
        type: "very",
    };
}

export function getWindRank(w) {
    const val = Number(w);
    if (Number.isNaN(val)) return { label: "", detail: "", type: "" };
    if (val < 12)
        return { label: "Calm", detail: "Little to no wind", type: "calm" };
    if (val < 30)
        return { label: "Light", detail: "Light breeze", type: "light" };
    if (val < 60)
        return { label: "Breezy", detail: "Noticeable wind", type: "breezy" };
    return {
        label: "Windy",
        detail: "Strong wind — take care",
        type: "strong",
    };
}
