import type { CapacitorConfig } from '@capacitor/cli';

// Optionally point the app to a dev server that serves both static files and /api
// Example (PowerShell): $env:CAP_SERVER_URL = "http://192.168.1.10:8302"
const serverUrl = process.env.CAP_SERVER_URL;

const config: CapacitorConfig = {
  appId: 'com.empowering.weather',
  appName: 'OverCast',
  webDir: 'src/web',
  bundledWebRuntime: false,
  server: serverUrl
    ? {
        url: serverUrl,
        cleartext: serverUrl.startsWith('http://'),
      }
    : {
        androidScheme: 'https',
      },
};

export default config;
