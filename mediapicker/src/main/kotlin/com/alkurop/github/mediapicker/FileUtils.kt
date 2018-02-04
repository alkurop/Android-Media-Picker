package com.alkurop.github.mediapicker

import java.io.File


fun getFileDirectory(imageDirectory:String): String {
    if (!File(imageDirectory).exists()) {
        !File(imageDirectory).mkdirs()
    }
    return File(imageDirectory).absolutePath
}

fun createFileName(ext: String): String {
    return "" + System.currentTimeMillis() + ext
}
