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
import com.google.android.material.color.MaterialColors
import com.google.android.material.R as MaterialR
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
@Suppress("TooManyFunctions")
class FileAdapter(
    private var files: List<File>,
    private val onItemClick: (File) -> Unit,
    private val onItemLongClick: (File, View) -> Unit,
    private val onSelectionChanged: (Int) -> Unit
) : RecyclerView.Adapter<FileAdapter.FileViewHolder>() {

    companion object {
        private const val THUMBNAIL_PERCENT = 0.1
        private const val CORNER_RADIUS = 8f
        
        private const val DEFAULT_PRIMARY = 0xFF0061A4
        private const val DEFAULT_ON_SURFACE = 0xFF1A1C1E
        private const val DEFAULT_ON_SURFACE_VARIANT = 0xFF43474E
        private const val DEFAULT_OUTLINE = 0xFF73777F
        private const val DEFAULT_SECONDARY_CONTAINER = 0xFFD1E4FF
    }

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
        
        val colors = resolveColors(context)
        bindBackground(holder, file, colors)
        
        val displayName = resolveDisplayName(context, file)
        holder.binding.fileName.text = displayName
        holder.binding.fileName.setTextColor(colors.onSurface)
        
        bindMetadata(holder, file, colors)
        bindIconAndThumbnail(holder, file, colors)
        
        setupListeners(holder, file)
    }

    private data class AdapterColors(
        val primary: Int,
        val onSurface: Int,
        val onSurfaceVariant: Int,
        val outline: Int,
        val secondaryContainer: Int
    )

    private fun resolveColors(context: Context): AdapterColors {
        fun getColor(id: Int, default: Long): Int {
            return MaterialColors.getColor(context, id, default.toInt())
        }

        return AdapterColors(
            primary = getColor(MaterialR.attr.colorPrimary, DEFAULT_PRIMARY),
            onSurface = getColor(MaterialR.attr.colorOnSurface, DEFAULT_ON_SURFACE),
            onSurfaceVariant = getColor(MaterialR.attr.colorOnSurfaceVariant, DEFAULT_ON_SURFACE_VARIANT),
            outline = getColor(MaterialR.attr.colorOutline, DEFAULT_OUTLINE),
            secondaryContainer = getColor(MaterialR.attr.colorSecondaryContainer, DEFAULT_SECONDARY_CONTAINER)
        )
    }

    private fun bindBackground(holder: FileViewHolder, file: File, colors: AdapterColors) {
        val isSelected = selectedItems.contains(file)
        holder.binding.root.isActivated = isSelected
        
        if (isSelected) {
            holder.binding.root.setBackgroundColor(colors.secondaryContainer)
        } else {
            val outValue = TypedValue()
            val resolve = holder.itemView.context.theme.resolveAttribute(
                android.R.attr.selectableItemBackground, outValue, true
            )
            if (resolve) {
                holder.binding.root.setBackgroundResource(outValue.resourceId)
            } else {
                holder.binding.root.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            }
        }
    }

    private fun resolveDisplayName(context: Context, file: File): String {
        if (file.name == "..") return ".."
        
        val baseName = if (file.name.isEmpty()) file.absolutePath else file.name
        
        val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
        val volume = storageManager.storageVolumes.find { it.directory?.absolutePath == file.absolutePath }
        
        return volume?.let { 
            if (it.isPrimary) context.getString(R.string.menu_internal_storage)
            else it.getDescription(context)
        } ?: baseName
    }

    private fun bindMetadata(holder: FileViewHolder, file: File, colors: AdapterColors) {
        val context = holder.itemView.context
        holder.binding.fileInfo.setTextColor(colors.onSurfaceVariant)
        
        if (file.name == "..") {
            holder.binding.fileInfo.text = context.getString(R.string.file_info_parent_dir)
        } else {
            val size = if (file.isDirectory) "" else Formatter.formatShortFileSize(context, file.length())
            val date = dateFormat.format(Date(file.lastModified()))
            holder.binding.fileInfo.text = if (size.isEmpty()) date else "$size • $date"
        }
    }

    private fun bindIconAndThumbnail(holder: FileViewHolder, file: File, colors: AdapterColors) {
        val context = holder.itemView.context
        val iconRes = if (file.name == "..") R.drawable.ic_folder else getIconForFile(file)
        val isNavFolder = file.isDirectory || file.name == ".."
        val tintColor = if (isNavFolder && file.name != "..") colors.primary else colors.outline

        holder.binding.fileIcon.dispose()
        holder.binding.fileIcon.setImageResource(iconRes)
        holder.binding.fileIcon.setColorFilter(tintColor)

        if (file.name != "..") {
            val extension = file.extension.lowercase()
            val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: ""
            
            val imgExts = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp")
            val vidExts = setOf(
                "mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", 
                "3gp", "ts", "mpg", "mpeg", "m4v"
            )

            val isImage = mimeType.startsWith("image/") || extension in imgExts
            val isVideo = mimeType.startsWith("video/") || extension in vidExts

            val canShowMedia = showThumbnails && !file.isDirectory
            if (canShowMedia && (isImage || isVideo)) {
                holder.binding.fileIcon.load(file, getImageLoader(context)) {
                    crossfade(true)
                    placeholder(iconRes)
                    error(iconRes)
                    if (isVideo) videoFramePercent(THUMBNAIL_PERCENT)
                    transformations(RoundedCornersTransformation(CORNER_RADIUS))
                    listener(onSuccess = { _, _ -> 
                        holder.binding.fileIcon.clearColorFilter()
                    })
                }
            }
        }
    }

    private fun setupListeners(holder: FileViewHolder, file: File) {
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
}

