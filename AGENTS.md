# AI Agent Guide for FileMan

This guide provides technical context and conventions for AI agents working on the FileMan project.

## Project Architecture

FileMan follows a **Single-Activity** architecture using **Jetpack Navigation**.

### Key Components

- **`MainActivity`**: The entry point. Hosts the `NavHostFragment`.
- **`DashboardFragment`**: The landing page. Displays high-level storage locations (Main Storage, Storage Devices, etc.).
- **`FileListFragment`**: The primary file browser. Handles directory listing, CRUD operations, and navigation history.
- **`FileAdapter`**: Manages the RecyclerView display for file/folder items, including metadata (size, date) and icons.

## Core Logic & Patterns

### 1. Separation of Concerns
To maintain testability, core filesystem logic is isolated from the UI:
- **`FileSorter`**: Logic for sorting lists of files (Name, Size, Date).
- **`FileFilter`**: Logic for filtering files based on hidden status and search queries.
- **`FileOperations`**: Logic for recursive copying, deleting, and identifying orphaned preferences. Now uses **Kotlin Coroutines** for background execution.

### 2. Navigation & History
- Navigation is session-based.
- `FileListFragment` maintains a `pathHistory` stack (List of `File` objects).
- The `onBackPressedCallback` pops from this stack to provide a standard "Back" experience.
- The `..` item in the file list allows for manual upward navigation.

### 3. Storage Discovery
- Direct listing of `/storage` is often restricted.
- Use `StorageManager.storageVolumes` in `FileListFragment` as a fallback to discover physical volumes (SD cards, USB).

### 4. Persistence
- User settings are stored in several `SharedPreferences` files:
  - `settings`: Global toggles (Advanced Mode, Show Hidden, Confirm Delete, Global Sort Default, Global Thumbnail Default).
  - `directory_sort_settings`: Per-directory sort overrides (Key = Absolute Path, Value = `SortType`).
  - `directory_thumbnail_settings`: Per-directory thumbnail overrides (Key = Absolute Path, Value = `Boolean`).
- **Orphan Cleanup**: `FileListFragment` calls `cleanupDirectoryPrefs()` on every load to remove saved preferences (sort and thumbnails) for directories that no longer exist.

### 5. Media & Async Tasks
- **Thumbnails**: Media thumbnails are loaded asynchronously in `FileAdapter` using the **Coil** library.
- **Video Previews**: For video files, Coil extracts a frame from **10% into the duration** to ensure a representative, non-black thumbnail.
- **Heavy Operations**: Copying and deleting tasks use `lifecycleScope` in the Fragment and `withContext(Dispatchers.IO)` in `FileOperations`.

## Licensing & Compliance

- **Project License**: Apache License 2.0.
- **Dependency Policy**: All future libraries, icons, or resources must be compatible with the Apache 2.0 license (e.g., Apache 2.0, MIT, BSD, or EPL).
- **Attribution**: Maintain the copyright headers in all source files. For the Play Store build, ensure open-source licenses are disclosed in the app's 'About' section as required by the respective libraries.

## Development Conventions

- **Strings**: Never hardcode user-facing strings. Use `strings.xml`.
- **KDoc**: All public classes and methods must have KDoc comments.
- **UI**: Use **ViewBinding**. Access views via `binding.viewId`.
- **Theming**: Use **Material Design 3 (M3)**. Prefer theme attributes (e.g., `?attr/colorPrimary`) over hardcoded colors to support Dynamic Color and Dark Mode.
- **Modern Menus**: Use the `MenuProvider` API instead of the deprecated `setHasOptionsMenu(true)`.

## Testing

- **Logic Tests**: Found in `app/src/test`. These should remain 100% passing and do not require an emulator.
- **UI Tests**: Found in `app/src/androidTest`. Use Espresso to verify Dashboard and FileList availability.

## CI/CD

The project uses **GitHub Actions** for Continuous Integration.
- **Workflow**: `.github/workflows/android-ci.yml`
- **Automation**: Automatically builds the app and runs unit tests on every push or pull request to `main` and `dev`.

## Git Workflow

The project follows a rigorous **Git Flow** pattern to ensure stability and traceability:

### 1. Persistent Branches
- **`main`**: The "Production" branch. Contains only stable, released code.
- **`dev`**: The "Integration" branch. This is where features and bug fixes are merged before a release cycle.

### 2. Development Workflow
- **Features & Fixes**: All new work must be done in a dedicated branch branched off `dev`.
  - Naming: `feature/your-feature-name` or `fix/your-bug-name`.
  - Merging: Merged back into `dev` after verification.

### 3. Release Cycle
- **Cutoff**: When `dev` is ready for release, tag it as `rc-X.Y.Z` (Release Candidate) and create a `release/X.Y.Z` branch from it.
- **Hardening**: Use the `release/` branch for final versioning, UI tweaks, and critical bug fixes.
- **RC Grooming**: Fixes for bugs found during the RC phase must use "Grooming Branches":
  - Naming: `groom/vX.Y.Z/short-description`.
  - **Strategy**: These branches **MUST be squash-merged** into the `release/` branch.
  - **Reasoning**: This ensures every fix adds exactly one commit to the release history, allowing CI to accurately number release candidates (RC1, RC2, etc.).
- **Completion**:
  1. Merge `release/` into `main`.
  2. Tag `main` with the final version `vX.Y.Z`.
  3. **Back-Merge**: Merge `release/` (or `main`) back into `dev` to ensure fixes are preserved.
  4. Delete the `release/` branch.

### 4. Emergency Hotfixes
- Branch from `main` (naming: `hotfix/description`).
- Once fixed and verified, merge into **both** `main` and `dev`.
- Tag `main` with a new patch version.

## Future Enhancement Ideas
- Background threading for large file operations (using Coroutines).
- Progress bars for copy/move tasks.
- Multi-selection mode for bulk actions.
- Search functionality.

## Local Build-Environment Context
AI agents must assume a standardized build environment based strictly on the configurations committed to this repository. However, developers may optionally provide a `.ai-local-tools.md` file in the project root to communicate machine-specific auxiliary tooling (e.g., active Docker containers, local Model Context Protocol (MCP) servers, or specific hardware emulators). 

**Strict Scope Limit:** If an `.ai-local-tools.md` file is present, agents may read it *only* to discover auxiliary local tools to assist their workflow. Agents **MUST NOT** rely on this file for project dependencies, hidden build flags, or logic that would break reproducibility on other machines or in the CI pipeline.
