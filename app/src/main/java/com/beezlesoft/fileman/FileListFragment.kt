/*
 * Copyright 2026 Brandon Bisel
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.beezlesoft.fileman

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.Settings
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.FileProvider
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.beezlesoft.fileman.databinding.FragmentFirstBinding
import java.io.File
import kotlinx.coroutines.launch

/**
 * The primary fragment responsible for displaying the file list and handling user interactions.
 * It manages directory navigation, file operations (copy, move, rename, delete), search, 
 * multi-selection, and favorites.
 */
class FileListFragment : Fragment() {

    private var _binding: FragmentFirstBinding? = null
    /**
     * View binding for the fragment layout.
     */
    private val binding get() = _binding!!

    private lateinit var adapter: FileAdapter
    private var currentPath: File = Environment.getExternalStorageDirectory()
    private val rootPath: File = File("/")

    private var allFiles: List<File> = emptyList()
    private var currentQuery: String = ""

    private val prefs by lazy { requireContext().getSharedPreferences("settings", Context.MODE_PRIVATE) }
    private val sortPrefs by lazy { requireContext().getSharedPreferences("directory_sort_settings", Context.MODE_PRIVATE) }
    private val favoritePrefs by lazy { requireContext().getSharedPreferences("favorites", Context.MODE_PRIVATE) }

    /**
     * Whether to show advanced system folders in the navigation menu and root listing.
     */
    private var showAdvanced: Boolean
        get() = prefs.getBoolean("show_advanced", false)
        set(value) = prefs.edit().putBoolean("show_advanced", value).apply()

    /**
     * Whether to show hidden files (starting with a dot) in the file list.
     */
    private var showHidden: Boolean
        get() = prefs.getBoolean("show_hidden", false)
        set(value) = prefs.edit().putBoolean("show_hidden", value).apply()

    /**
     * Whether to prompt for confirmation before deleting a file.
     */
    private var confirmDelete: Boolean
        get() = prefs.getBoolean("confirm_delete", true)
        set(value) = prefs.edit().putBoolean("confirm_delete", value).apply()

    /**
     * The global default sorting strategy.
     */
    private var globalSortType: FileSorter.SortType
        get() = FileSorter.SortType.valueOf(prefs.getString("sort_type", FileSorter.SortType.NAME.name)!!)
        set(value) = prefs.edit().putString("sort_type", value.name).apply()

    /**
     * Gets the sorting strategy for the current directory, falling back to the global default.
     */
    private var currentSortType: FileSorter.SortType
        get() {
            val savedSort = sortPrefs.getString(currentPath.absolutePath, null)
            return if (savedSort != null) FileSorter.SortType.valueOf(savedSort) else globalSortType
        }
        set(value) {
            if (value == globalSortType) {
                sortPrefs.edit().remove(currentPath.absolutePath).apply()
            } else {
                sortPrefs.edit().putString(currentPath.absolutePath, value.name).apply()
            }
        }

    private var clipboardFiles: List<File>? = null
    private var isMoveOperation: Boolean = false
    private var actionMode: ActionMode? = null

    private val pathHistory = mutableListOf<File>()

    /**
     * Callback for handling the system back button. Navigates back through the history of visited folders.
     */
    private val onBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            if (pathHistory.isNotEmpty()) {
                val prevPath = pathHistory.removeAt(pathHistory.size - 1)
                loadFiles(prevPath, addToHistory = false)
            } else {
                isEnabled = false
                requireActivity().onBackPressedDispatcher.onBackPressed()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requireActivity().onBackPressedDispatcher.addCallback(this, onBackPressedCallback)
        
        // Handle initial path argument
        val initialPathStr = arguments?.getString("initialPath")
        if (!initialPathStr.isNullOrEmpty()) {
            currentPath = File(initialPathStr)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFirstBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupMenu()
        setupRecyclerView()
        checkPermissionsAndLoadFiles()
    }

    /**
     * Sets up the toolbar menu using the modern [MenuProvider] API.
     */
    private fun setupMenu() {
        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.menu_main, menu)
                menu.findItem(R.id.action_home_nav).isVisible = true
                menu.findItem(R.id.action_system).isVisible = showAdvanced
                menu.findItem(R.id.action_root).isVisible = showAdvanced
                menu.findItem(R.id.action_app_private).isVisible = showAdvanced
                menu.findItem(R.id.action_paste).isVisible = clipboardFiles != null

                val searchItem = menu.findItem(R.id.action_search)
                val searchView = searchItem.actionView as androidx.appcompat.widget.SearchView
                searchView.setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
                    override fun onQueryTextSubmit(query: String?): Boolean = false
                    override fun onQueryTextChange(newText: String?): Boolean {
                        currentQuery = newText ?: ""
                        filterFiles()
                        return true
                    }
                })
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    R.id.action_home_nav -> {
                        findNavController().popBackStack(R.id.DashboardFragment, false)
                        true
                    }
                    R.id.action_paste -> {
                        performPaste()
                        true
                    }
                    R.id.action_new_folder -> {
                        showNewFolderDialog()
                        true
                    }
                    R.id.sort_name -> {
                        currentSortType = FileSorter.SortType.NAME
                        loadFiles(currentPath, addToHistory = false)
                        true
                    }
                    R.id.sort_size -> {
                        currentSortType = FileSorter.SortType.SIZE
                        loadFiles(currentPath, addToHistory = false)
                        true
                    }
                    R.id.sort_date -> {
                        currentSortType = FileSorter.SortType.DATE
                        loadFiles(currentPath, addToHistory = false)
                        true
                    }
                    R.id.action_home -> {
                        loadFiles(Environment.getExternalStorageDirectory())
                        true
                    }
                    R.id.action_app_private -> {
                        loadFiles(requireContext().filesDir.parentFile ?: requireContext().filesDir)
                        true
                    }
                    R.id.action_root -> {
                        loadFiles(File("/"))
                        true
                    }
                    R.id.action_storage -> {
                        loadFiles(File("/storage"))
                        true
                    }
                    R.id.action_system -> {
                        loadFiles(File("/system"))
                        true
                    }
                    R.id.action_settings -> {
                        showSettingsDialog()
                        true
                    }
                    else -> false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    /**
     * Initializes the RecyclerView with the [FileAdapter] and handles clicks, multi-selection, 
     * and Action Mode management.
     */
    private fun setupRecyclerView() {
        adapter = FileAdapter(
            files = emptyList(),
            onItemClick = { file ->
                if (file.name == "..") {
                    currentPath.parentFile?.let { loadFiles(it) }
                } else if (file.isDirectory) {
                    loadFiles(file)
                } else {
                    openFile(file)
                }
            },
            onItemLongClick = { file, v ->
                if (file.name != "..") {
                    showContextMenu(file, v)
                }
            },
            onSelectionChanged = { count ->
                if (count == 0) {
                    actionMode?.finish()
                } else {
                    if (actionMode == null) {
                        actionMode = (requireActivity() as AppCompatActivity).startSupportActionMode(actionModeCallback)
                    }
                    actionMode?.title = count.toString()
                }
            }
        )

        binding.recyclerView.layoutManager = LinearLayoutManager(context)
        binding.recyclerView.adapter = adapter
    }

    /**
     * Callback for managing the Contextual Action Bar (CAB) during multi-selection mode.
     */
    private val actionModeCallback = object : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            mode.menuInflater.inflate(R.menu.menu_file_selection, menu)
            adapter.setMultiSelectMode(true)
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean = false

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            val selectedFiles = adapter.getSelectedFiles()
            return when (item.itemId) {
                R.id.action_share -> {
                    val filesToShare = selectedFiles.filter { !it.isDirectory }
                    if (filesToShare.isNotEmpty()) {
                        // Bulk sharing is complex, for now we share the first or show toast
                        if (filesToShare.size == 1) shareFile(filesToShare[0])
                        else Toast.makeText(context, "Bulk sharing not fully supported yet", Toast.LENGTH_SHORT).show()
                    }
                    mode.finish()
                    true
                }
                R.id.action_copy -> {
                    clipboardFiles = selectedFiles
                    isMoveOperation = false
                    requireActivity().invalidateOptionsMenu()
                    Toast.makeText(context, "Copied ${selectedFiles.size} items", Toast.LENGTH_SHORT).show()
                    mode.finish()
                    true
                }
                R.id.action_move -> {
                    clipboardFiles = selectedFiles
                    isMoveOperation = true
                    requireActivity().invalidateOptionsMenu()
                    Toast.makeText(context, "Moving ${selectedFiles.size} items", Toast.LENGTH_SHORT).show()
                    mode.finish()
                    true
                }
                R.id.action_delete -> {
                    showBulkDeleteConfirmation(selectedFiles)
                    true
                }
                else -> false
            }
        }

        override fun onDestroyActionMode(mode: ActionMode) {
            adapter.setMultiSelectMode(false)
            actionMode = null
        }
    }

    /**
     * Securely opens a file using [FileProvider] and an [Intent.ACTION_VIEW].
     * @param file The file to be opened.
     */
    private fun openFile(file: File) {
        try {
            val uri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                file
            )
            val extension = file.extension.lowercase()
            val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "*/*"

            val intent = Intent(Intent.ACTION_VIEW)
            intent.setDataAndType(uri, mimeType)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            
            startActivity(Intent.createChooser(intent, getString(R.string.menu_open)))
        } catch (e: Exception) {
            Toast.makeText(context, getString(R.string.msg_open_failed, e.message), Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Shares a file using [FileProvider] and an [Intent.ACTION_SEND].
     * @param file The file to be shared.
     */
    private fun shareFile(file: File) {
        try {
            val uri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                file
            )
            val extension = file.extension.lowercase()
            val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "*/*"

            val intent = Intent(Intent.ACTION_SEND)
            intent.type = mimeType
            intent.putExtra(Intent.EXTRA_STREAM, uri)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            
            startActivity(Intent.createChooser(intent, getString(R.string.menu_share)))
        } catch (e: Exception) {
            Toast.makeText(context, getString(R.string.msg_share_failed, e.message), Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Displays a context menu (Popup) for a specific file or folder.
     */
    private fun showContextMenu(file: File, view: View) {
        val popup = PopupMenu(requireContext(), view)
        popup.menuInflater.inflate(R.menu.menu_file_context, popup.menu)
        
        val favoriteItem = popup.menu.findItem(R.id.action_favorite)
        val isFavorite = favoritePrefs.contains(file.absolutePath)
        
        if (file.isDirectory) {
            favoriteItem.isVisible = true
            favoriteItem.setTitle(if (isFavorite) R.string.menu_unfavorite else R.string.menu_favorite)
        } else {
            favoriteItem.isVisible = false
        }

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_open -> {
                    if (file.isDirectory) loadFiles(file)
                    else openFile(file)
                    true
                }
                R.id.action_share -> {
                    if (!file.isDirectory) shareFile(file)
                    else Toast.makeText(context, getString(R.string.msg_cannot_share_folders), Toast.LENGTH_SHORT).show()
                    true
                }
                R.id.action_copy -> {
                    clipboardFiles = listOf(file)
                    isMoveOperation = false
                    requireActivity().invalidateOptionsMenu()
                    Toast.makeText(context, getString(R.string.msg_copy_toast, file.name), Toast.LENGTH_SHORT).show()
                    true
                }
                R.id.action_move -> {
                    clipboardFiles = listOf(file)
                    isMoveOperation = true
                    requireActivity().invalidateOptionsMenu()
                    Toast.makeText(context, getString(R.string.msg_move_toast, file.name), Toast.LENGTH_SHORT).show()
                    true
                }
                R.id.action_rename -> {
                    showRenameDialog(file)
                    true
                }
                R.id.action_delete -> {
                    showDeleteConfirmation(file)
                    true
                }
                R.id.action_details -> {
                    showFileDetails(file)
                    true
                }
                R.id.action_favorite -> {
                    toggleFavorite(file)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    /**
     * Toggles the favorite status of a directory and updates persistence.
     */
    private fun toggleFavorite(file: File) {
        val path = file.absolutePath
        if (favoritePrefs.contains(path)) {
            favoritePrefs.edit().remove(path).apply()
            Toast.makeText(context, getString(R.string.msg_favorite_removed), Toast.LENGTH_SHORT).show()
        } else {
            favoritePrefs.edit().putString(path, file.name).apply()
            Toast.makeText(context, getString(R.string.msg_favorite_added), Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Displays a dialog to rename the selected file or folder.
     */
    private fun showRenameDialog(file: File) {
        val container = FrameLayout(requireContext())
        val editText = EditText(requireContext())
        val params = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        val margin = (16 * resources.displayMetrics.density).toInt()
        params.setMargins(margin, margin / 2, margin, margin / 2)
        editText.layoutParams = params
        editText.setText(file.name)
        editText.selectAll()
        container.addView(editText)

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.menu_rename)
            .setView(container)
            .setPositiveButton(R.string.menu_rename) { _, _ ->
                val newName = editText.text.toString()
                if (newName.isNotEmpty() && newName != file.name) {
                    val destination = File(file.parentFile, newName)
                    if (destination.exists()) {
                        Toast.makeText(context, getString(R.string.msg_file_exists), Toast.LENGTH_SHORT).show()
                    } else if (file.renameTo(destination)) {
                        Toast.makeText(context, getString(R.string.msg_renamed_success), Toast.LENGTH_SHORT).show()
                        loadFiles(currentPath, addToHistory = false)
                    } else {
                        Toast.makeText(context, getString(R.string.msg_rename_failed), Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    /**
     * Displays a dialog to create a new folder in the current directory.
     */
    private fun showNewFolderDialog() {
        val container = FrameLayout(requireContext())
        val editText = EditText(requireContext())
        val params = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        val margin = (16 * resources.displayMetrics.density).toInt()
        params.setMargins(margin, margin / 2, margin, margin / 2)
        editText.layoutParams = params
        editText.setHint(R.string.menu_new_folder)
        container.addView(editText)

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.menu_new_folder)
            .setView(container)
            .setPositiveButton(R.string.dialog_ok) { _, _ ->
                val folderName = editText.text.toString()
                if (folderName.isNotEmpty()) {
                    val newFolder = File(currentPath, folderName)
                    if (newFolder.exists()) {
                        Toast.makeText(context, getString(R.string.msg_file_exists), Toast.LENGTH_SHORT).show()
                    } else if (newFolder.mkdir()) {
                        Toast.makeText(context, getString(R.string.msg_folder_created_success), Toast.LENGTH_SHORT).show()
                        loadFiles(currentPath, addToHistory = false)
                    } else {
                        Toast.makeText(context, getString(R.string.msg_folder_create_failed), Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    /**
     * Executes the copy or move operation using the current [clipboardFiles].
     */
    private fun performPaste() {
        val sources = clipboardFiles ?: return
        val dialog = showProgressDialog(getString(R.string.menu_paste))

        lifecycleScope.launch {
            var successCount = 0
            try {
                sources.forEach { source ->
                    val destination = File(currentPath, source.name)
                    if (!destination.exists()) {
                        if (isMoveOperation) {
                            if (source.renameTo(destination)) {
                                successCount++
                            } else {
                                FileOperations.copyRecursive(source, destination)
                                FileOperations.deleteRecursive(source)
                                successCount++
                            }
                        } else {
                            FileOperations.copyRecursive(source, destination)
                            successCount++
                        }
                    }
                }
                
                if (successCount > 0) {
                    Toast.makeText(context, if (isMoveOperation) getString(R.string.msg_moved_success) else getString(R.string.msg_copied_success), Toast.LENGTH_SHORT).show()
                }
                
                clipboardFiles = null
                requireActivity().invalidateOptionsMenu()
                loadFiles(currentPath, addToHistory = false)
            } catch (e: Exception) {
                Toast.makeText(context, getString(R.string.msg_operation_failed, e.message), Toast.LENGTH_LONG).show()
            } finally {
                dialog.dismiss()
            }
        }
    }

    /**
     * Deletes a file or directory, optionally prompting for confirmation based on settings.
     */
    private fun showDeleteConfirmation(file: File) {
        if (!confirmDelete) {
            performDelete(file)
            return
        }

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.dialog_confirm_delete_title)
            .setMessage(getString(R.string.dialog_confirm_delete_message, file.name))
            .setPositiveButton(R.string.menu_delete) { _, _ ->
                performDelete(file)
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    /**
     * Performs the actual deletion of a single file or directory.
     */
    private fun performDelete(file: File) {
        val dialog = showProgressDialog(getString(R.string.menu_delete))
        lifecycleScope.launch {
            try {
                if (FileOperations.deleteRecursive(file)) {
                    Toast.makeText(context, getString(R.string.msg_deleted_success), Toast.LENGTH_SHORT).show()
                    loadFiles(currentPath, addToHistory = false)
                } else {
                    Toast.makeText(context, getString(R.string.msg_delete_failed), Toast.LENGTH_SHORT).show()
                }
            } finally {
                dialog.dismiss()
            }
        }
    }

    /**
     * Shows a confirmation dialog before deleting multiple selected items.
     */
    private fun showBulkDeleteConfirmation(files: List<File>) {
        if (!confirmDelete) {
            performBulkDelete(files)
            return
        }

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.dialog_confirm_delete_title)
            .setMessage("Are you sure you want to delete ${files.size} items?")
            .setPositiveButton(R.string.menu_delete) { _, _ ->
                performBulkDelete(files)
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    /**
     * Performs the bulk deletion of multiple files or directories in the background.
     */
    private fun performBulkDelete(files: List<File>) {
        val dialog = showProgressDialog(getString(R.string.menu_delete))
        lifecycleScope.launch {
            try {
                files.forEach { FileOperations.deleteRecursive(it) }
                loadFiles(currentPath, addToHistory = false)
                actionMode?.finish()
            } finally {
                dialog.dismiss()
            }
        }
    }

    /**
     * Displays a non-cancelable progress dialog with a generic message and indeterminate progress bar.
     * 
     * @param title The title/message to display in the dialog.
     * @return The created [AlertDialog] instance.
     */
    private fun showProgressDialog(title: String): AlertDialog {
        val view = layoutInflater.inflate(R.layout.layout_progress, null)
        val message = view.findViewById<android.widget.TextView>(R.id.progressMessage)
        message.text = title
        
        return AlertDialog.Builder(requireContext())
            .setView(view)
            .setCancelable(false)
            .show()
    }

    /**
     * Shows a detailed alert dialog with metadata for the selected file.
     */
    private fun showFileDetails(file: File) {
        val details = """
            Name: ${file.name}
            Path: ${file.absolutePath}
            Size: ${if (file.isDirectory) "N/A" else "${file.length()} bytes"}
            Readable: ${file.canRead()}
            Writable: ${file.canWrite()}
            Last Modified: ${java.util.Date(file.lastModified())}
        """.trimIndent()

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.dialog_file_details_title)
            .setMessage(details)
            .setPositiveButton(R.string.dialog_ok, null)
            .show()
    }

    /**
     * Displays the settings dialog to toggle advanced browsing options, hidden files, 
     * delete confirmations, and global defaults.
     */
    private fun showSettingsDialog() {
        val options = arrayOf(
            getString(R.string.dialog_settings_advanced), 
            getString(R.string.dialog_settings_hidden_files),
            getString(R.string.dialog_settings_confirm_delete),
            getString(R.string.dialog_settings_global_sort)
        )
        val checked = booleanArrayOf(showAdvanced, showHidden, confirmDelete, false)

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.dialog_settings_title)
            .setMultiChoiceItems(options, checked) { _, which, isChecked ->
                when (which) {
                    0 -> {
                        showAdvanced = isChecked
                        requireActivity().invalidateOptionsMenu()
                        if (!showAdvanced && (currentPath.absolutePath == "/" || currentPath.absolutePath.startsWith("/system"))) {
                            loadFiles(Environment.getExternalStorageDirectory())
                        }
                    }
                    1 -> {
                        showHidden = isChecked
                        loadFiles(currentPath, addToHistory = false)
                    }
                    2 -> {
                        confirmDelete = isChecked
                    }
                    3 -> {
                        if (isChecked) {
                            globalSortType = currentSortType
                            Toast.makeText(context, getString(R.string.msg_global_sort_set, currentSortType.name), Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setPositiveButton(R.string.dialog_close, null)
            .show()
    }

    /**
     * Removes saved sort preferences for subdirectories of [parentDir] that no longer exist on disk.
     * This prevents the settings database from being cluttered with paths to deleted folders.
     */
    private fun cleanupSortPrefs(parentDir: File) {
        val orphaned = FileOperations.getOrphanedPrefKeys(parentDir, sortPrefs.all.keys)
        if (orphaned.isNotEmpty()) {
            val editor = sortPrefs.edit()
            orphaned.forEach { editor.remove(it) }
            editor.apply()
        }
    }

    /**
     * Requests the required MANAGE_EXTERNAL_STORAGE permission if not already granted.
     */
    private fun checkPermissionsAndLoadFiles() {
        if (Environment.isExternalStorageManager()) {
            loadFiles(currentPath, addToHistory = false)
        } else {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:${requireContext().packageName}")
                startActivity(intent)
            } catch (e: Exception) {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                startActivity(intent)
            }
        }
    }

    /**
     * Filters the current [allFiles] list based on [showHidden] and [currentQuery],
     * then updates the adapter with the results.
     */
    private fun filterFiles() {
        val filtered = FileFilter.filterFiles(allFiles, showHidden, currentQuery)

        if (filtered.isEmpty()) {
            if (!currentPath.canRead()) {
                Toast.makeText(context, getString(R.string.msg_permission_denied_folder, currentPath.absolutePath), Toast.LENGTH_SHORT).show()
            } else if (currentPath.isDirectory && currentQuery.isEmpty()) {
                Toast.makeText(context, getString(R.string.msg_directory_empty), Toast.LENGTH_SHORT).show()
            }
        }
        
        val sortedFiles = FileSorter.sortFiles(filtered, currentSortType)
        val displayList = mutableListOf<File>()
        
        // Add ".." entry if not at system root and not searching
        if (currentPath.parentFile != null && currentQuery.isEmpty()) {
            displayList.add(File(currentPath, ".."))
        }
        
        displayList.addAll(sortedFiles)
        adapter.updateFiles(displayList)
    }

    /**
     * Loads and displays the contents of the specified directory.
     * @param directory The directory to list.
     * @param addToHistory Whether to add the current path to the navigation history.
     */
    private fun loadFiles(directory: File, addToHistory: Boolean = true) {
        if (addToHistory && directory.absolutePath != currentPath.absolutePath) {
            pathHistory.add(currentPath)
            onBackPressedCallback.isEnabled = true
        }
        
        currentPath = directory
        cleanupSortPrefs(directory)
        
        var files = directory.listFiles()?.toList()
        
        // Fallback for restricted root (/): show known readable system directories manually
        if (files == null && directory.absolutePath == "/") {
            val knownRoots = arrayOf("/system", "/storage", "/proc", "/sys", "/etc", "/mnt", "/vendor", "/dev")
            files = knownRoots.map { File(it) }.filter { it.exists() }
        }

        // Fallback for restricted /storage: use StorageManager to find volumes
        if (files == null && directory.absolutePath == "/storage") {
            val storageManager = requireContext().getSystemService(Context.STORAGE_SERVICE) as StorageManager
            files = storageManager.storageVolumes.mapNotNull { it.directory }
        }
        
        val finalFiles = files ?: emptyList()
        allFiles = finalFiles // Full list, filtering happens in filterFiles()
        
        filterFiles()
        (requireActivity() as AppCompatActivity).supportActionBar?.title = directory.absolutePath
    }

    override fun onResume() {
        super.onResume()
        if (Environment.isExternalStorageManager()) {
            loadFiles(currentPath, addToHistory = false)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}