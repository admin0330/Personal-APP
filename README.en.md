# Ym1r · Personal APP

[简体中文](README.md) | **English**

Ym1r is a personal application developed from Emailbox, with a native Android client, a Go backend and a React administration interface. It combines mail aggregation, notes, bookkeeping and account management.

Current public source version: **1.5.2 / versionCode 79**. This is a sanitized development snapshot. Publishing this source does not imply a corresponding stable APK release or completed physical-device acceptance testing.

## Features

- Multiple mail accounts and groups, message lists and bodies, attachments, search, OAuth reauthorization and synchronization status.
- Local-first notes and bookkeeping, plus mail caching with module-specific protection of sensitive data.
- Jetpack Compose UI with translucent and classic themes, a gesture drawer, liquid-glass Dock and animated numbers.
- Tenant isolation, permission checks, auditing, credential encryption and isolated message rendering.
- Recent mail improvements: cancellation of superseded requests, earlier display of cached content and message bodies, reduced body prefetching, bounded polling concurrency and interruption of stalled IMAP connections.

## Repository layout

| Path | Purpose |
| --- | --- |
| `android/` | Kotlin / Jetpack Compose client and tests |
| `api/`, `pkg/`, `configs/` | Go / Echo server, business logic and configuration |
| `db/` | SQLite / PostgreSQL migrations, queries and sqlc generated code |
| `web/` | React 19 / TypeScript / Vite administration interface |
| `docs/` | Configuration and historical design notes; source code defines current behavior |
| `scripts/` | Build, lint and key-generation tools |

## Run locally

Install Go (see `go.mod`) and Bun (see `web/package.json`):

```sh
git clone https://github.com/admin0330/Personal-APP.git
cd Personal-APP
cp .env.example .env
go run ./scripts/genkey
```

Put the generated value in `ENCRYPTION_KEY` in your local `.env`. Configure your own administrator username and initial password; no usable administrator password is provided. Start the server and Web development server in separate terminals:

```sh
go run .
```

```sh
cd web
bun install --frozen-lockfile
bun run dev
```

The default backend port is `1323`; Vite runs on `5173` and proxies `/api` to the local backend. In Windows PowerShell, use `Copy-Item .env.example .env`.

Production deployments need `APP_ENV=production`, a unique encryption key, HTTPS cookie settings and reviewed registration, reverse proxy and OAuth callback settings. See [configuration documentation](docs/configuration.md) (Chinese). The Compose file is a local development example; explicitly pass your own environment configuration when using Docker.

## Build Android

Install JDK 17 or later (local verification uses JDK 21) and Android SDK Platform 37. Set `ANDROID_HOME` or provide an untracked `android/local.properties`.

Public source uses `example.com` placeholders and system DNS. The current login UI uses a built-in server address. Before building for your deployment, edit these files under `android/app/src/main/java/com/masteralanlab/emailbox/`:

- `data/Prefs.kt`: `DEFAULT_SERVER`.
- `data/remote/ApiClient.kt`: default-host path normalization.
- `update/UpdateManager.kt`: stable and test update manifest URLs, hosted by you.
- `data/remote/ApiModels.kt`: image host mapping; return the original URL if no alternate host is used.

Update the related unit tests when changing hosts. Android server connections require HTTPS.

```sh
cd android
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

On Windows use `./gradlew.bat`. APK output is under `android/app/build/outputs/apk/`. Use `:app:assembleRelease` for a release build. Supply your private signing settings in untracked `android/keystore.properties`:

```properties
storeFile=/absolute/path/to/your-release.jks
storePassword=YOUR_PRIVATE_STORE_PASSWORD
keyAlias=YOUR_KEY_ALIAS
keyPassword=YOUR_PRIVATE_KEY_PASSWORD
```

Without these settings, the build uses the local debug signing key. That key is unsuitable for production distribution or upgrading an existing officially signed installation. Publishers should also choose their own application ID and update channels.

The icon artwork and density-specific and monochrome layers are included. The source artwork is `android/app/src/main/assets/branding/app_icon_source.png`; generation and APK verification scripts are in `android/tools/` and require Pillow.

## Verification and sanitization

```sh
go test ./...
go vet ./...
cd web
bun run lint
bun run test
bun run build
```

Use `make lint` and `make test` for the complete project checks. PostgreSQL parity tests require a separate test database.

The public snapshot excludes live databases, message contents, logs, screenshots, APKs, build caches, signing keys, local SDK settings, real environment configuration and private operational handovers. Service hosts are placeholders and production origin-IP overrides have been removed. Sanitization is performed in a separate public copy without changing private deployment settings. Never commit production secrets or runtime data.

## Attribution and license

Developed from [MasterAlanLab/emailbox](https://github.com/MasterAlanLab/emailbox). The repository's existing [MIT license](LICENSE) is retained. Third-party code and assets remain subject to their own licenses.

Liquid glass uses dependencies from [Kyant0/AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass). See [android/MORPHICONS.md](android/MORPHICONS.md) for the current icon mapping (the actual assets are Lucide). Animated numbers are implemented in Compose with design inspiration from [Rolling](https://rolling.kitlangton.dev/), rather than integration of a Swift library.
