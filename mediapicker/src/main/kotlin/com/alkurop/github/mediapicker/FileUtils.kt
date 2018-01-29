package com.alkurop.github.mediapicker

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream


fun getFileDirectory(imageDirectory:String): String {
    if (!File(imageDirectory).exists()) {
        !File(imageDirectory).mkdirs()
    }
    return File(imageDirectory).absolutePath
}

fun createFileName(ext: String): String {
    return "" + System.currentTimeMillis() + ext
}
