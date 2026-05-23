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
- **`FileOperations`**: Logic for recursive copying, deleting, and identifying orphaned preferences.

### 2. Navigation & History
- Navigation is session-based.
- `FileListFragment` maintains a `pathHistory` stack (List of `File` objects).
- The `onBackPressedCallback` pops from this stack to provide a standard "Back" experience.

### 3. Storage Discovery
- Direct listing of `/storage` is often restricted.
- Use `StorageManager.storageVolumes` in `FileListFragment` as a fallback to discover physical volumes (SD cards, USB).

### 4. Persistence
- User settings are stored in two `SharedPreferences` files:
  - `settings`: Global toggles (Advanced Mode, Show Hidden, Confirm Delete, Global Sort Default).
  - `directory_sort_settings`: Per-directory sort overrides (Key = Absolute Path, Value = `SortType`).
- **Orphan Cleanup**: `FileListFragment` calls `cleanupSortPrefs()` on every load to remove saved preferences for directories that no longer exist.

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
