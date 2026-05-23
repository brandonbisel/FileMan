# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Test Commands

```bash
# Build
./gradlew assembleDebug
./gradlew assembleRelease

# Unit tests (no device required)
./gradlew testDebugUnitTest

# Single unit test class
./gradlew testDebugUnitTest --tests "com.beezlesoft.fileman.FileSorterTest"

# Instrumented UI tests (requires emulator or device)
./gradlew connectedAndroidTest

# Install on connected device/emulator
./gradlew installDebug

# Static analysis & lint
./gradlew detekt
./gradlew lintDebug

# Test coverage report
./gradlew koverXmlReportDebug

# Security vulnerability scan (requires NVD API key in gradle.properties)
./gradlew dependencyCheckAnalyze
```

## Architecture

Single-activity app using Jetpack Navigation: `MainActivity` hosts a `NavHostFragment` that navigates between `DashboardFragment` (landing page with storage locations and favorites) and `FileListFragment` (primary file browser).

**No ViewModel layer** — fragment state is managed in-fragment, with persistence via SharedPreferences. Business logic lives in pure utility objects that are easy to unit test:

- `FileSorter` — sorts file lists by NAME/SIZE/DATE, always directories first
- `FileFilter` — filters hidden files and applies search queries
- `FileOperations` — recursive copy/delete via coroutines with progress callbacks

**UI access**: ViewBinding everywhere — no `findViewById`.

**Async**: `lifecycleScope` in fragments; `withContext(Dispatchers.IO)` in utility objects.

**Thumbnails**: Coil with `VideoFrameDecoder`; video frames extracted at 10% of duration.

## Persistence (SharedPreferences)

Four namespaces — do not conflate them:

| Name | Contents |
|------|----------|
| `settings` | Global prefs: Advanced Mode, Show Hidden Files |
| `directory_sort_settings` | Per-directory sort type overrides |
| `directory_thumbnail_settings` | Per-directory thumbnail toggle |
| `favorites` | Bookmarked directory paths |

## Key Conventions

- **Colors**: Always use theme attributes (`?attr/colorPrimary`, `?attr/colorSurface`) — never hardcode colors. Supports Dynamic Color and dark mode.
- **Strings**: All user-facing text goes in `res/values/strings.xml` — no inline string literals.
- **Menus**: Use `MenuProvider` API; `setHasOptionsMenu()` is deprecated and must not be used.
- **KDoc**: Required on all public classes and functions.
- **License header**: Apache License 2.0 header required at the top of every new source file (see existing files for the exact format).
- **Dependencies**: New libraries must be compatible with Apache 2.0, MIT, BSD, or EPL.

## Testing

- Unit tests live in `app/src/test/java/com/beezlesoft/fileman/` and cover `FileSorter`, `FileFilter`, and `FileOperations`.
- Instrumented (Espresso) tests live in `app/src/androidTest/` and require a running device or emulator.
- Before committing: `detekt` and `lintDebug` must pass without new warnings.

## Further Reading

`AGENTS.md` contains the full developer guide: architecture rationale, git/release workflow, licensing compliance details, and CI/CD setup.
