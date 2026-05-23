# FileMan

A clean, functional Android file manager built with modern Android development practices.

## Features

- **Dashboard**: A central landing page for quick access to storage locations.
- **File Browsing**: Navigate through Main Storage, SD cards, and USB drives.
- **System Explorer**: Access system directories like `/system`, `/proc`, and `/etc` (requires enabling Advanced Mode).
- **History-Based Navigation**: The back button retraces your steps through visited folders.
- **Parent Directory Shortcut**: Familiar `..` entry at the top of every listing.
- **File Operations**:
  - Copy and Move (Paste)
  - Rename
  - Delete (Recursive for directories, with optional confirmation)
  - View File Details
- **Open Files**: Seamlessly open files with their corresponding system applications using `FileProvider`.
- **Sorting**: Sort files by Name, Size, or Last Modified date.
- **Per-Directory Memory**: Remembers your preferred sort order for each folder individually.
- **Advanced Mode**: Toggle visibility of restricted system folders and internal app files.
- **Mouse & Keyboard Support**:
  - Right-click for context menus.
  - Scroll wheel support.
  - Hover highlights and keyboard focus states.

## Permissions

This app requires the `MANAGE_EXTERNAL_STORAGE` permission (All Files Access) to provide full file management capabilities on Android 11+.

## Technical Implementation

- **Language**: Kotlin
- **Architecture**: Single-Activity with Jetpack Navigation and Fragments.
- **UI Components**: RecyclerView, Material Design components, ViewBinding.
- **Security**: Uses `androidx.core.content.FileProvider` for secure file sharing.
- **Storage Discovery**: Uses `StorageManager` to reliably find physical storage volumes.
- **Persistence**: Remembers user settings and directory preferences via SharedPreferences.
- **Testing**: Robust Unit tests for core logic and Espresso tests for UI.

## Building the Project

1. Open the project in Android Studio.
2. Sync the project with Gradle files.
3. Build and run the `:app` module on an emulator or physical device.

## Development & Contributing

This project follows a rigorous development workflow including **Git Flow**, **automated testing**, and specific **UI conventions**. 

For detailed information on project architecture, coding standards, and the release process, please refer to the [AGENTS.md](AGENTS.md) file.

## License

This project is licensed under the **Apache License 2.0**. It is open-source and free for modification and distribution. 

The official Play Store release may include additional features or integrations to support ongoing maintenance and development.
