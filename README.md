# GoodbyeDPI for Android

DPI circumvention for Android, inspired by GoodbyeDPI. No root required.

The app runs a local VPN that fragments TLS ClientHello and HTTP requests so the host name never appears in a single packet. DNS goes to Quad9 (9.9.9.9) by default, over HTTPS when possible.

## Features

- SNI fragmentation and TLS record splitting
- HTTP Host header tricks
- QUIC blocking
- Configurable DNS (Quad9, Cloudflare, Google, AdGuard or custom) with DNS over HTTPS
- Light, dark and system themes
- Quick Settings tile

## Download

Get the APK from [Releases](../../releases):

- `arm64-v8a`: 64-bit devices
- `armeabi-v7a`: 32-bit devices

## Build

```sh
./scripts/fetch-native.sh
./gradlew assembleRelease
```

Releases are built and published by GitHub Actions when `versionName` changes. Set `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD` secrets to sign with your own key.

## License

Uses [hev-socks5-tunnel](https://github.com/heiher/hev-socks5-tunnel) (MIT).
