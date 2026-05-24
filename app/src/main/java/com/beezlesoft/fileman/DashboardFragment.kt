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
import com.google.android.material.color.MaterialColors
import com.google.android.material.R as MaterialR
import com.beezlesoft.fileman.databinding.FragmentDashboardBinding
import java.io.File

@Suppress("TooManyFunctions")
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
                menu.findItem(R.id.action_system).isVisible = false
                menu.findItem(R.id.action_root).isVisible = false
                menu.findItem(R.id.action_paste).isVisible = false
                menu.findItem(R.id.action_sort).isVisible = false
                menu.findItem(R.id.action_new_folder).isVisible = false
                menu.findItem(R.id.action_search).isVisible = false
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
        
        addFavorites(items)
        addVolumes(currentContext, items)
        addAdvancedFolders(items)

        binding.dashboardRecyclerView.layoutManager = LinearLayoutManager(context)
        binding.dashboardRecyclerView.adapter = DashboardAdapter(items) { item ->
            val bundle = Bundle().apply {
                putString("initialPath", item.path.absolutePath)
            }
            val actionId = R.id.action_DashboardFragment_to_FileListFragment
            findNavController().navigate(actionId, bundle)
        }
    }

    private fun addFavorites(items: MutableList<DashboardItem>) {
        val favorites = favoritePrefs.all
        if (favorites.isNotEmpty()) {
            favorites.forEach { (path, name) ->
                val file = File(path)
                if (file.exists()) {
                    val subtitle = "Favorite • $path"
                    val icon = R.drawable.ic_folder
                    items.add(DashboardItem(name.toString(), icon, file, subtitle))
                } else {
                    favoritePrefs.edit().remove(path).apply()
                }
            }
        }
    }

    private fun addVolumes(currentContext: Context, items: MutableList<DashboardItem>) {
        try {
            val storageManager = currentContext.getSystemService(Context.STORAGE_SERVICE) as StorageManager
            val primaryVolumePath = currentContext.getExternalFilesDir(null)
                ?.absolutePath?.substringBefore("/Android") ?: ""

            storageManager.storageVolumes.forEach { volume ->
                addVolumeItem(currentContext, volume, primaryVolumePath, items)
            }
        } catch (e: SecurityException) {
            android.util.Log.e("Dashboard", "Storage discovery failed", e)
            val internalStorage = Environment.getExternalStorageDirectory()
            items.add(DashboardItem(getString(R.string.menu_internal_storage), R.drawable.ic_folder, internalStorage))
        }
    }

    private fun addVolumeItem(
        context: Context, 
        volume: android.os.storage.StorageVolume, 
        primaryPath: String, 
        items: MutableList<DashboardItem>
    ) {
        val path = volume.directory ?: return
        val absolutePath = path.absolutePath
        
        if (volume.isPrimary) {
            val title = getString(R.string.menu_internal_storage)
            items.add(DashboardItem(title, R.drawable.ic_folder, path, getCapacityStr(context, path)))
        } else {
            if (absolutePath == primaryPath || primaryPath.startsWith(absolutePath)) {
                return
            }
            val title = volume.getDescription(context)
            items.add(DashboardItem(title, R.drawable.ic_folder, path, getCapacityStr(context, path)))
        }
    }

    private fun getCapacityStr(context: Context, path: File): String {
        val totalSpace = path.totalSpace
        val freeSpace = path.usableSpace
        val usedSpace = totalSpace - freeSpace
        
        return if (totalSpace > 0) {
            val used = Formatter.formatShortFileSize(context, usedSpace)
            val total = Formatter.formatShortFileSize(context, totalSpace)
            "$used used of $total • ${path.absolutePath}"
        } else {
            path.absolutePath
        }
    }

    private fun addAdvancedFolders(items: MutableList<DashboardItem>) {
        if (showAdvanced) {
            val appPrivateDir = requireContext().filesDir.parentFile ?: requireContext().filesDir
            items.add(DashboardItem(getString(R.string.menu_app_private), R.drawable.ic_folder, appPrivateDir))
            items.add(DashboardItem(getString(R.string.menu_system_files), R.drawable.ic_folder, File("/system")))
            items.add(DashboardItem(getString(R.string.menu_system_root), R.drawable.ic_folder, File("/")))
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
            val inflater = LayoutInflater.from(parent.context)
            val binding = com.beezlesoft.fileman.databinding.ItemFileBinding.inflate(inflater, parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            val context = holder.itemView.context
            
            // Resolve colors using helper
            val colorPrimary = MaterialColors.getColor(
                context, MaterialR.attr.colorPrimary, 0xFF0061A4.toInt()
            )
            val colorOnSurface = MaterialColors.getColor(
                context, MaterialR.attr.colorOnSurface, 0xFF1A1C1E.toInt()
            )
            val colorVariant = MaterialColors.getColor(
                context, MaterialR.attr.colorOnSurfaceVariant, 0xFF43474E.toInt()
            )
            
            val outValue = TypedValue()
            if (context.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)) {
                holder.binding.root.setBackgroundResource(outValue.resourceId)
            }
            
            holder.binding.fileName.text = item.title
            holder.binding.fileName.setTextColor(colorOnSurface)
            
            holder.binding.fileIcon.setImageResource(item.iconRes)
            holder.binding.fileIcon.setColorFilter(colorPrimary)
            
            holder.binding.fileInfo.text = item.subtitle ?: item.path.absolutePath
            holder.binding.fileInfo.setTextColor(colorVariant)

            holder.itemView.setOnClickListener { onClick(item) }
        }

        override fun getItemCount() = items.size
    }
}

