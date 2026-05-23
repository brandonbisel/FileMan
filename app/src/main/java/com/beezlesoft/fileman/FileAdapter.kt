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
import android.net.Uri
import android.os.storage.StorageManager
import android.text.format.Formatter
import android.util.Size
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.AttrRes
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import coil.ImageLoader
import coil.decode.VideoFrameDecoder
import coil.dispose
import coil.load
import coil.request.videoFramePercent
import coil.transform.RoundedCornersTransformation
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Adapter for the RecyclerView to display file and directory entries.
 * Supports multi-selection, media thumbnails, and special handling for parent navigation items.
 *
 * @property files The list of files to display.
 * @property onItemClick Callback invoked when a file or directory is clicked.
 * @property onItemLongClick Callback invoked when a file or directory is long-clicked (or right-clicked).
 * @property onSelectionChanged Callback invoked when the number of selected items changes.
 */
class FileAdapter(
    private var files: List<File>,
    private val onItemClick: (File) -> Unit,
    private val onItemLongClick: (File, View) -> Unit,
    private val onSelectionChanged: (Int) -> Unit
) : RecyclerView.Adapter<FileAdapter.FileViewHolder>() {

    private val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
    private val selectedItems = mutableSetOf<File>()
    private var multiSelectMode = false
    private var imageLoader: ImageLoader? = null
    private var showThumbnails: Boolean = true

    private fun getImageLoader(context: Context): ImageLoader {
        return imageLoader ?: ImageLoader.Builder(context)
            .components {
                add(VideoFrameDecoder.Factory())
            }
            .build().also { imageLoader = it }
    }

    private fun getIconForFile(file: File): Int {
        if (file.isDirectory) return R.drawable.ic_folder
        return when (file.extension.lowercase()) {
            "pdf" -> R.drawable.ic_pdf
            "zip", "rar", "7z", "tar", "gz" -> R.drawable.ic_zip
            "mp3", "wav", "ogg", "m4a", "flac" -> R.drawable.ic_audio
            "jpg", "jpeg", "png", "gif", "webp", "bmp" -> R.drawable.ic_image
            "mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "3gp", "ts", "mpg", "mpeg", "m4v" -> R.drawable.ic_video
            else -> R.drawable.ic_file
        }
    }

    class FileViewHolder(val binding: com.beezlesoft.fileman.databinding.ItemFileBinding) : 
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = com.beezlesoft.fileman.databinding.ItemFileBinding.inflate(inflater, parent, false)
        return FileViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        val file = files[position]
        val context = holder.itemView.context
        
        // Resolve theme colors
        val colorPrimary = com.google.android.material.color.MaterialColors.getColor(context, com.google.android.material.R.attr.colorPrimary, 0xFF0061A4.toInt())
        val colorOnSurface = com.google.android.material.color.MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurface, 0xFF1A1C1E.toInt())
        val colorOnSurfaceVariant = com.google.android.material.color.MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFF43474E.toInt())
        val colorOutline = com.google.android.material.color.MaterialColors.getColor(context, com.google.android.material.R.attr.colorOutline, 0xFF73777F.toInt())
        val colorSecondaryContainer = com.google.android.material.color.MaterialColors.getColor(context, com.google.android.material.R.attr.colorSecondaryContainer, 0xFFD1E4FF.toInt())

        // Background handling
        val isSelected = selectedItems.contains(file)
        holder.binding.root.isActivated = isSelected
        
        if (isSelected) {
            holder.binding.root.setBackgroundColor(colorSecondaryContainer)
        } else {
            val outValue = TypedValue()
            if (context.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)) {
                holder.binding.root.setBackgroundResource(outValue.resourceId)
            } else {
                holder.binding.root.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            }
        }

        var displayName = if (file.name.isEmpty()) file.absolutePath else file.name
        
        // Resolve friendly names for storage volume roots
        val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
        val volume = storageManager.storageVolumes.find { it.directory?.absolutePath == file.absolutePath }
        if (volume != null) {
            displayName = if (volume.isPrimary) context.getString(R.string.menu_internal_storage)
            else volume.getDescription(context)
        }
        
        holder.binding.fileName.text = displayName
        holder.binding.fileName.setTextColor(colorOnSurface)
        
        val size = if (file.isDirectory) "" else Formatter.formatShortFileSize(context, file.length())
        val date = dateFormat.format(Date(file.lastModified()))
        holder.binding.fileInfo.setTextColor(colorOnSurfaceVariant)

        // Set base icon and color
        val iconRes = if (file.name == "..") R.drawable.ic_folder else getIconForFile(file)
        val isNavFolder = file.isDirectory || file.name == ".."
        val tintColor = if (isNavFolder && file.name != "..") colorPrimary else colorOutline

        // Always reset icon state to baseline
        holder.binding.fileIcon.dispose()
        holder.binding.fileIcon.setImageResource(iconRes)
        holder.binding.fileIcon.setColorFilter(tintColor)

        if (file.name == "..") {
            holder.binding.fileName.text = ".."
            holder.binding.fileInfo.text = context.getString(R.string.file_info_parent_dir)
        } else {
            holder.binding.fileInfo.text = if (size.isEmpty()) date else "$size • $date"

            val extension = file.extension.lowercase()
            val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: ""
            
            val isImage = mimeType.startsWith("image/") || extension in setOf("jpg", "jpeg", "png", "gif", "webp", "bmp")
            val isVideo = mimeType.startsWith("video/") || extension in setOf("mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "3gp", "ts", "mpg", "mpeg", "m4v")

            if (showThumbnails && !file.isDirectory && (isImage || isVideo)) {
                holder.binding.fileIcon.load(file, getImageLoader(context)) {
                    crossfade(true)
                    placeholder(iconRes)
                    error(iconRes)
                    if (isVideo) videoFramePercent(0.1)
                    transformations(RoundedCornersTransformation(8f))
                    listener(onSuccess = { _, _ -> 
                        holder.binding.fileIcon.clearColorFilter()
                    })
                }
            }
        }
        
        holder.itemView.setOnClickListener {
            if (multiSelectMode && file.name != "..") {
                toggleSelection(file)
            } else {
                onItemClick(file)
            }
        }
        holder.itemView.setOnLongClickListener { 
            if (file.name != "..") {
                if (!multiSelectMode) {
                    setMultiSelectMode(true)
                    toggleSelection(file)
                } else {
                    onItemLongClick(file, it)
                }
            }
            true 
        }
    }

    override fun getItemCount(): Int = files.size

    /**
     * Enables or disables multi-selection mode.
     * @param enabled True to enable, false to disable and clear selections.
     */
    fun setMultiSelectMode(enabled: Boolean) {
        multiSelectMode = enabled
        if (!enabled) {
            selectedItems.clear()
            notifyDataSetChanged()
        }
    }

    /**
     * Toggles the selection state of a specific file.
     * @param file The file to toggle.
     */
    fun toggleSelection(file: File) {
        if (selectedItems.contains(file)) {
            selectedItems.remove(file)
        } else {
            selectedItems.add(file)
        }
        notifyItemChanged(files.indexOf(file))
        onSelectionChanged(selectedItems.size)
    }

    /**
     * Returns a list of currently selected files.
     */
    fun getSelectedFiles(): List<File> = selectedItems.toList()

    /**
     * Updates whether media thumbnails should be displayed.
     * @param enabled True to show thumbnails, false to show icons only.
     */
    fun setShowThumbnails(enabled: Boolean) {
        if (showThumbnails != enabled) {
            showThumbnails = enabled
            notifyDataSetChanged()
        }
    }

    /**
     * Updates the adapter's data set and refreshes the list.
     * @param newFiles The new list of files to display.
     */
    fun updateFiles(newFiles: List<File>) {
        files = newFiles
        notifyDataSetChanged()
    }

    private fun Context.getColorFromAttr(@AttrRes attrColor: Int): Int {
        val typedValue = TypedValue()
        theme.resolveAttribute(attrColor, typedValue, true)
        return typedValue.data
    }
}