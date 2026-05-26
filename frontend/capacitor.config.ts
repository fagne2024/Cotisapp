import type { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'com.intouch.cotisapp',
  appName: 'CotisApp',
  webDir: 'dist/frontend/browser',
  server: {
    androidScheme: 'https',
    cleartext: true, // ← autorisé en dev (HTTP local), mettre false en prod avec HTTPS
  },
  plugins: {
    SplashScreen: {
      launchShowDuration: 2000,
      launchAutoHide: true,
      backgroundColor: '#1a5c3a',
      androidSplashResourceName: 'splash',
      androidScaleType: 'CENTER_CROP',
      showSpinner: false,
    },
    StatusBar: {
      style: 'LIGHT',
      backgroundColor: '#1a5c3a',
    },
  },
  android: {
    buildOptions: {
      keystorePath: 'cotisapp-release.keystore',
      keystoreAlias: 'cotisapp',
    },
  },
};

export default config;
