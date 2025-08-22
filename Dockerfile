# Build image for OverCast API
FROM node:18-alpine

WORKDIR /app

# Copy package metadata and install dependencies for the API
COPY src/api/package.json ./src/api/package.json
COPY src/api/package-lock.json ./src/api/package-lock.json

# Install dependencies in the src/api directory
RUN cd src/api && npm install --production

# Copy application code and weather_notes
COPY src/api ./src/api
COPY weather_notes ./weather_notes
# Copy static frontend so the API's express static middleware can serve it
COPY src/web ./src/web

ENV PORT=8302
EXPOSE 8302

# Run the API
CMD ["node", "src/api/index.js"]
