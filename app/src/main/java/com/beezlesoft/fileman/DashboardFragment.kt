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
import android.os.Bundle
import android.os.Environment
import android.os.storage.StorageManager
import android.text.format.Formatter
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.annotation.AttrRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.beezlesoft.fileman.databinding.FragmentDashboardBinding
import java.io.File

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private val prefs by lazy { requireContext().getSharedPreferences("settings", Context.MODE_PRIVATE) }
    private var showAdvanced: Boolean
        get() = prefs.getBoolean("show_advanced", false)
        set(value) = prefs.edit().putBoolean("show_advanced", value).apply()

    private var showHidden: Boolean
        get() = prefs.getBoolean("show_hidden", false)
        set(value) = prefs.edit().putBoolean("show_hidden", value).apply()

    private var confirmDelete: Boolean
        get() = prefs.getBoolean("confirm_delete", true)
        set(value) = prefs.edit().putBoolean("confirm_delete", value).apply()

    private val favoritePrefs by lazy { requireContext().getSharedPreferences("favorites", Context.MODE_PRIVATE) }

    data class DashboardItem(
        val title: String, 
        val iconRes: Int, 
        val path: File,
        val subtitle: String? = null
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (requireActivity() as AppCompatActivity).supportActionBar?.title = getString(R.string.app_name)
        setupMenu()
        setupRecyclerView()
    }

    private fun setupMenu() {
        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.menu_main, menu)
                // Hide navigation shortcuts and paste on dashboard
                menu.findItem(R.id.action_home_nav).isVisible = false
                menu.findItem(R.id.action_home).isVisible = false
                menu.findItem(R.id.action_app_private).isVisible = false
                menu.findItem(R.id.action_storage).isVisible = false
                menu.findItem(R.id.action_system).isVisible = false
                menu.findItem(R.id.action_root).isVisible = false
                menu.findItem(R.id.action_paste).isVisible = false
                menu.findItem(R.id.action_sort).isVisible = false
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    R.id.action_settings -> {
                        showSettingsDialog()
                        true
                    }
                    else -> false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private fun setupRecyclerView() {
        val currentContext = context ?: return
        val items = mutableListOf<DashboardItem>()
        
        // Add Favorites
        val favorites = favoritePrefs.all
        if (favorites.isNotEmpty()) {
            // Header or label for favorites
            favorites.forEach { (path, name) ->
                val file = File(path)
                if (file.exists()) {
                    items.add(DashboardItem(name.toString(), R.drawable.ic_folder, file, "Favorite • $path"))
                } else {
                    favoritePrefs.edit().remove(path).apply()
                }
            }
        }

        // Use StorageManager to find all available volumes
        try {
            val storageManager = currentContext.getSystemService(Context.STORAGE_SERVICE) as StorageManager
            val primaryVolumePath = currentContext.getExternalFilesDir(null)?.absolutePath?.substringBefore("/Android") ?: ""

            storageManager.storageVolumes.forEach { volume ->
                val path = volume.directory
                if (path != null) {
                    val absolutePath = path.absolutePath
                    val title = if (volume.isPrimary) {
                        getString(R.string.menu_internal_storage)
                    } else {
                        // Skip if this is a redundant view of the primary storage
                        if (absolutePath == primaryVolumePath || primaryVolumePath.startsWith(absolutePath)) {
                            return@forEach
                        }
                        volume.getDescription(currentContext)
                    }

                    // Calculate capacity
                    val totalSpace = path.totalSpace
                    val freeSpace = path.usableSpace
                    val usedSpace = totalSpace - freeSpace
                    
                    val capacityStr = if (totalSpace > 0) {
                        val used = Formatter.formatShortFileSize(currentContext, usedSpace)
                        val total = Formatter.formatShortFileSize(currentContext, totalSpace)
                        "$used used of $total • $absolutePath"
                    } else {
                        absolutePath
                    }

                    items.add(DashboardItem(title, R.drawable.ic_folder, path, capacityStr))
                }
            }
        } catch (e: Exception) {
            // Fallback: Add at least the primary storage if volume discovery fails
            val internalStorage = Environment.getExternalStorageDirectory()
            items.add(DashboardItem(getString(R.string.menu_internal_storage), R.drawable.ic_folder, internalStorage))
        }

        if (showAdvanced) {
            items.add(DashboardItem(getString(R.string.menu_app_private), R.drawable.ic_folder, requireContext().filesDir.parentFile ?: requireContext().filesDir))
            items.add(DashboardItem(getString(R.string.menu_system_files), R.drawable.ic_folder, File("/system")))
            items.add(DashboardItem(getString(R.string.menu_system_root), R.drawable.ic_folder, File("/")))
        }

        binding.dashboardRecyclerView.layoutManager = LinearLayoutManager(context)
        binding.dashboardRecyclerView.adapter = DashboardAdapter(items) { item ->
            val bundle = Bundle().apply {
                putString("initialPath", item.path.absolutePath)
            }
            findNavController().navigate(R.id.action_DashboardFragment_to_FileListFragment, bundle)
        }
    }

    private fun showSettingsDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.dialog_settings_title)
            .setMultiChoiceItems(
                arrayOf(
                    getString(R.string.dialog_settings_advanced), 
                    getString(R.string.dialog_settings_hidden_files),
                    getString(R.string.dialog_settings_confirm_delete)
                ),
                booleanArrayOf(showAdvanced, showHidden, confirmDelete)
            ) { _, which, isChecked ->
                when (which) {
                    0 -> {
                        showAdvanced = isChecked
                        setupRecyclerView() // Refresh list
                    }
                    1 -> {
                        showHidden = isChecked
                    }
                    2 -> {
                        confirmDelete = isChecked
                    }
                }
            }
            .setPositiveButton(R.string.dialog_close, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private class DashboardAdapter(
        private val items: List<DashboardItem>,
        private val onClick: (DashboardItem) -> Unit
    ) : androidx.recyclerview.widget.RecyclerView.Adapter<DashboardAdapter.ViewHolder>() {

        class ViewHolder(val binding: com.beezlesoft.fileman.databinding.ItemFileBinding) : 
            androidx.recyclerview.widget.RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val themedContext = androidx.appcompat.view.ContextThemeWrapper(parent.context, R.style.Theme_FileMan)
            val inflater = LayoutInflater.from(themedContext)
            val binding = com.beezlesoft.fileman.databinding.ItemFileBinding.inflate(inflater, parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            
            holder.binding.fileName.text = item.title
            holder.binding.fileIcon.setImageResource(item.iconRes)
            
            // Hardcoded M3 primary for now to prevent crashes
            holder.binding.fileIcon.setColorFilter(0xFF0061A4.toInt())
            
            holder.binding.fileInfo.text = item.subtitle ?: item.path.absolutePath
            holder.itemView.setOnClickListener { onClick(item) }
        }

        override fun getItemCount() = items.size
    }
}
