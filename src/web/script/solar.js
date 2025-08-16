// Solar helper used by the stats chart

export function solarSineForHours(hourly, location) {
    let sunriseEpoch = null;
    let sunsetEpoch = null;
    try {
        const astro = (location && location.astro) || location || null;
        if (astro && astro.sunrise && astro.sunset) {
            const parseTime = (txt) => {
                const m = String(txt)
                    .trim()
                    .match(/(\d{1,2}):(\d{2})\s*(AM|PM)/i);
                if (!m) return null;
                let hr = Number(m[1]);
                const min = Number(m[2]);
                const ampm = m[3].toUpperCase();
                if (ampm === "PM" && hr !== 12) hr += 12;
                if (ampm === "AM" && hr === 12) hr = 0;
                return { hr, min };
            };
            const s = parseTime(astro.sunrise);
            const e = parseTime(astro.sunset);
            if (s && e) {
                sunriseEpoch = { hr: s.hr, min: s.min };
                sunsetEpoch = { hr: e.hr, min: e.min };
            }
        }
    } catch (e) {}

    const hours = hourly.map((h) => {
        const epoch = h.time_epoch || 0;
        return { epoch, date: new Date(epoch * 1000) };
    });
    if (hours.length === 0) return [];

    const baseDate = hours[0].date;
    const baseYear = baseDate.getFullYear();
    const baseMonth = baseDate.getMonth();
    const baseDay = baseDate.getDate();
    const makeEpochFromObj = (o) =>
        Math.floor(
            new Date(baseYear, baseMonth, baseDay, o.hr, o.min, 0).getTime() /
                1000
        );
    const makeEpoch = (h) =>
        Math.floor(
            new Date(baseYear, baseMonth, baseDay, h, 0, 0).getTime() / 1000
        );

    if (sunriseEpoch && typeof sunriseEpoch === "object") {
        sunriseEpoch = makeEpochFromObj(sunriseEpoch);
    } else {
        sunriseEpoch = makeEpoch(6);
    }
    if (sunsetEpoch && typeof sunsetEpoch === "object") {
        sunsetEpoch = makeEpochFromObj(sunsetEpoch);
    } else {
        sunsetEpoch = makeEpoch(18);
    }

    return hours.map(({ epoch }) => {
        let v = 0;
        if (epoch >= sunriseEpoch && epoch <= sunsetEpoch) {
            const t = (epoch - sunriseEpoch) / (sunsetEpoch - sunriseEpoch);
            v = Math.sin(t * Math.PI);
        }
        return v;
    });
}
