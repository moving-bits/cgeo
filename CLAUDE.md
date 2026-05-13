# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## About

c:geo is an open-source Android geocaching app written in Java. It provides a full-featured client for geocaching.com and other geocaching platforms (OpenCaching variants etc.).
It uses AndroidX libraries and Material 2 design libraries. It is prepared for Android 15+ edge2edge mode.

## Build Commands

Always run Gradle in **offline mode**:

```bash
# Build debug APK
./gradlew --offline assembleBasicDebug

# Run unit tests (no device needed)
./gradlew --offline testBasicDebug

# Run a single test class
./gradlew --offline testBasicDebugUnitTest --tests cgeo.geocaching.location.GeopointTest

# Check code style
./gradlew --offline checkstyle
```

**Do not run PMD checks** (`./gradlew pmd`) — the project is not set up for it.  
**Do not run instrumented tests** (`connectedAndroidTest`) — they require a configured geocaching.com account on an emulator.

## API Keys Setup

1. Copy `templates/private.properties` to the repository root
2. Fill in API keys (Google Maps v2, OpenCaching OKAPI keys, etc.)
3. Gradle generates `main/src/main/res/values/keys.xml` automatically on build

If `keys.xml` already exists, delete it before regenerating after key changes. Alternatively, copy `main/templates/keys.xml` to `main/src/main/res/values/` and edit manually.

## Code Style

Enforced by Checkstyle (`checkstyle.xml`). Key rules:
- No tabs — spaces only
- No star imports; no unused imports
- Import order (groups separated by blank lines): `cgeo.*` → `android.*` → `androidx.*` → `java.*` → `javax.*` → everything else
- Static and non-static imports within the same group are not separated
- Prefer `final` for local variables and parameters

## Module Structure

- `main/` — Application module; all geocaching logic lives here
- `mapswithme-api/` — Maps.ME integration library
- `organicmaps-api/` — Organic Maps integration library

### Key Package Layout (`main/src/main/java/cgeo/geocaching/`)

| Package | Purpose |
|---|---|
| `models/` | Core data models: `Geocache`, `Waypoint`, `Trackable` |
| `connector/` | Plugin-style connectors per geocaching service (gc, oc, al, su, …) |
| `storage/` | `DataStore` — SQLite wrapper; `CacheCache` — in-memory cache layer |
| `unifiedmap/` | New unified map implementation (Mapsforge/VTM + Google Maps) |
| `maps/` | Legacy map utilities and tile providers |
| `activity/` | Android Activity classes for all UI screens |
| `ui/` | Fragments, dialogs, custom views |
| `location/` | `Geopoint`, coordinate parsing, distance calculations |
| `network/` | HTTP client, cookie management, web scraping helpers |
| `filters/` | Cache filtering logic |
| `loaders/` | RxJava3-based async data loaders |
| `settings/` | User preferences |
| `enumerations/` | `CacheType`, `CacheSize`, `WaypointType`, etc. |

### Connector Architecture

`ConnectorFactory` dispatches cache operations to the appropriate `IConnector` implementation. Each connector handles login, search, cache detail fetching, and log submission for its respective service. Adding a new geocaching service means implementing `IConnector` (and related interfaces) and registering it with `ConnectorFactory`.

### Map System

Two parallel map backends share a common API:
- **Mapsforge/VTM** (`unifiedmap/`) — offline-capable vector tiles
- **Google Maps** — requires Google API-enabled device/emulator image and a Maps API v2 key

`UnifiedMapActivity` is the entry point; it delegates to the active backend.

### Data Persistence

`DataStore` is the single SQLite access point, protected by a `ReentrantReadWriteLock`. Avoid bypassing it with direct `SQLiteDatabase` calls. Schema migrations are handled inside `DataStore`.

## Branching & Commit Conventions

- `master` — new feature development (nightly builds)
- `release` — bug fixes for already-released versions (merge back to `master` regularly)

When working on a GitHub issue (`$ISSUE_NUMBER`):
- Branch: `copilot/issue-$ISSUE_NUMBER-$NAME`
- PR title: `fix #$ISSUE_NUMBER: $TITLE`
- Commit message: `rel to #$ISSUE_NUMBER: $TITLE`
- Feature work should target `master`; bug fixes should target `release`

## Testing Notes

- Unit tests: `main/src/test/java/` — prefer these; no device needed
- Instrumented tests: `main/src/androidTest/java/` — require an Android emulator with a geocaching.com account configured
- Test classes live in the same package as the class under test
- Always verify that both main code and test code compile and that unit tests pass before finishing a change
