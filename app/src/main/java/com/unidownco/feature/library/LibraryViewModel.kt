package com.unidownco.feature.library

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.unidownco.core.common.Platform
import com.unidownco.core.database.UniDownDatabase
import com.unidownco.core.storage.StorageManager
import com.unidownco.data.repository.DownloadRepository
import com.unidownco.domain.models.DownloadItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class LibraryViewModel(application: Application) : AndroidViewModel(application) {

    private val db = UniDownDatabase.getInstance(application)
    private val storageManager = StorageManager(application)
    val repository = DownloadRepository(application, db.downloadDao(), storageManager)

    private val _selectedPlatform = MutableStateFlow<Platform?>(null)
    val selectedPlatform: StateFlow<Platform?> = _selectedPlatform.asStateFlow()

    val completedVideos: StateFlow<List<DownloadItem>> = combine(
        repository.getCompletedLibrary(),
        _selectedPlatform
    ) { list, platformFilter ->
        if (platformFilter == null) list else list.filter { it.platform == platformFilter }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onFilterPlatform(platform: Platform?) {
        _selectedPlatform.value = platform
    }

    fun onExportToGallery(item: DownloadItem) {
        val uri = repository.exportToGallery(item)
        if (uri != null) {
            Toast.makeText(getApplication(), "Saved to ${StorageManager.RELATIVE_MOVIES_PATH} in Gallery!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(getApplication(), "Failed to export video.", Toast.LENGTH_SHORT).show()
        }
    }

    fun onDelete(id: Long) {
        viewModelScope.launch {
            repository.deleteDownload(id)
        }
    }

    fun playVideo(item: DownloadItem) {
        val path = item.downloadPath ?: return
        val file = File(path)
        if (!file.exists()) {
            Toast.makeText(getApplication(), "File no longer exists on disk.", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val context = getApplication<Application>()
            val uri: Uri = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            } else {
                Uri.fromFile(file)
            }

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "video/mp4")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(getApplication(), "No media player available: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun shareVideo(item: DownloadItem) {
        val path = item.downloadPath ?: return
        val file = File(path)
        if (!file.exists()) return

        try {
            val context = getApplication<Application>()
            val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "video/mp4"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(intent, "Share Video").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (_: Exception) {}
    }
}
