package com.unidownco.engine.exporter

import android.net.Uri
import java.io.File

interface GalleryExporter {
    suspend fun exportToGallery(file: File, title: String): Uri?
}
