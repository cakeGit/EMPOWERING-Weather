# Empowering weather API

Rest API served on port 8302 Cloudflared tunnel points the traffic going to
"weather.oreostack.uk/api" to localhost:8302

Request format

```
https://weather.oreostack.uk/api/
?lat=...&lon=...
```

Response format

```json
{
    "cache_age": "int", //Seconds since data was fetched, Data within 1 degree of latitude / longditude is cached for 1 hour
    "weather_quip": "string", //Randomly selected quip from weather_notes, see below
    "weather": { //Raw response from weather api, api key is in environment variable (docker) or dev.env, containing WEATHERAPI_KEY
        "location": {
            "name": "Dagenham",
            "region": "Barking & Dagenham Greater London",
            "country": "United Kingdom",
            "lat": 51.54,
            "lon": 0.14,
            "tz_id": "Europe/London",
            "localtime_epoch": 1755344991,
            "localtime": "2025-08-16 12:49"
        },
        "current": {
            "last_updated_epoch": 1755344700,
            "last_updated": "2025-08-16 12:45",
            "temp_c": 17.0,
            "temp_f": 62.6,
            "is_day": 1,
            "condition": {
                "text": "Partly cloudy",
                "icon": "//cdn.weatherapi.com/weather/64x64/day/116.png",
                "code": 1003
            },
            "wind_mph": 7.8,
            "wind_kph": 12.6,
            "wind_degree": 75,
            "wind_dir": "ENE",
            "pressure_mb": 1029.0,
            "pressure_in": 30.39,
            "precip_mm": 0.0,
            "precip_in": 0.0,
            "humidity": 83,
            "cloud": 75,
            "feelslike_c": 17.0,
            "feelslike_f": 62.6,
            "windchill_c": 23.9,
            "windchill_f": 75.0,
            "heatindex_c": 25.0,
            "heatindex_f": 77.1,
            "dewpoint_c": 11.9,
            "dewpoint_f": 53.4,
            "vis_km": 10.0,
            "vis_miles": 6.0,
            "uv": 5.8,
            "gust_mph": 9.0,
            "gust_kph": 14.5,
            "short_rad": 284.42,
            "diff_rad": 135.59,
            "dni": 211.87,
            "gti": 129.17
        },
        "forecast": {
            "forecastday": [
                {
                    "date": "2025-08-16",
                    "date_epoch": 1755302400,
                    "day": {
                        "maxtemp_c": 25.3,
                        "maxtemp_f": 77.5,
                        "mintemp_c": 15.5,
                        "mintemp_f": 59.9,
                        "avgtemp_c": 19.6,
                        "avgtemp_f": 67.3,
                        "maxwind_mph": 11.6,
                        "maxwind_kph": 18.7,
                        "totalprecip_mm": 0.0,
                        "totalprecip_in": 0.0,
                        "totalsnow_cm": 0.0,
                        "avgvis_km": 10.0,
                        "avgvis_miles": 6.0,
                        "avghumidity": 64,
                        "daily_will_it_rain": 0,
                        "daily_chance_of_rain": 0,
                        "daily_will_it_snow": 0,
                        "daily_chance_of_snow": 0,
                        "condition": {
                            "text": "Partly Cloudy ",
                            "icon": "//cdn.weatherapi.com/weather/64x64/day/116.png",
                            "code": 1003
                        },
                        "uv": 1.6
                    },
                    "astro": {
                        "sunrise": "05:47 AM",
                        "sunset": "08:19 PM",
                        "moonrise": "10:50 PM",
                        "moonset": "03:22 PM",
                        "moon_phase": "Last Quarter",
                        "moon_illumination": 53,
                        "is_moon_up": 1,
                        "is_sun_up": 0
                    },
                    "hour": [
                        {
                            "time_epoch": 1755298800,
                            "time": "2025-08-16 00:00",
                            "temp_c": 18.0,
                            "temp_f": 64.4,
                            "is_day": 0,
                            "condition": {
                                "text": "Clear ",
                                "icon": "//cdn.weatherapi.com/weather/64x64/night/113.png",
                                "code": 1000
                            },
                            "wind_mph": 7.6,
                            "wind_kph": 12.2,
                            "wind_degree": 54,
                            "wind_dir": "NE",
                            "pressure_mb": 1027.0,
                            "pressure_in": 30.33,
                            "precip_mm": 0.0,
                            "precip_in": 0.0,
                            "snow_cm": 0.0,
                            "humidity": 81,
                            "cloud": 13,
                            "feelslike_c": 18.0,
                            "feelslike_f": 64.4,
                            "windchill_c": 18.0,
                            "windchill_f": 64.4,
                            "heatindex_c": 18.0,
                            "heatindex_f": 64.4,
                            "dewpoint_c": 14.8,
                            "dewpoint_f": 58.6,
                            "will_it_rain": 0,
                            "chance_of_rain": 0,
                            "will_it_snow": 0,
                            "chance_of_snow": 0,
                            "vis_km": 10.0,
                            "vis_miles": 6.0,
                            "gust_mph": 10.7,
                            "gust_kph": 17.2,
                            "uv": 0,
                            "short_rad": 0,
                            "diff_rad": 0,
                            "dni": 0,
                            "gti": 0
                        }
                        //...Repeated x24
                    ]
                }
            ]
        }
    }
}
```

## Weather quips

These are randomly selected, a number picked for each weather note catagory each
day, so it can remain consistent. The weather categories are:

- cloudy
- cold
- foggy
- hot
- humid
- mild
- rainy
- snowy
- stormy
- thunderstorm
- windy

Each has a .txt file of \n seperated messages

## Run locally

From a PowerShell prompt in `src/api`:

1. Install dependencies:

   npm install

2. Copy `.env.example` to `.env` and set `WEATHERAPI_KEY`.

3. Start the server:

   npm start

The API will listen on the port from `.env` (default 8302). Query with
`?lat=...&lon=...` as shown above.

Notes: the `weather_notes` directory lives at the repository root under
`weather_notes/` and must contain the category .txt files.

## Docker

From repository root you can build and run a container for the API. Example:

```powershell
# Build image (run from repository root)
docker build -t empowering-weather-api .

# Run container, mapping port and providing the API key
docker run -e WEATHERAPI_KEY="your_key_here" -p 8302:8302 empowering-weather-api
```

This image copies the `src/api` code and the repository `weather_notes/`
directory into the container. The container listens on the port in `PORT`
(default 8302).
