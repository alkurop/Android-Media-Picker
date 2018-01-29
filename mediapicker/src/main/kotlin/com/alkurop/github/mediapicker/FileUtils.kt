package com.alkurop.github.mediapicker

import android.os.Environment
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream


fun getFileDirectory(): String {

    val directory = File(Environment.getExternalStorageDirectory().absolutePath
            + File.separator + "images")
    if (!directory.exists()) {
        directory.mkdirs();
    }
    return directory.absolutePath
}

fun createFileName(ext: String): String {
    return "" + System.currentTimeMillis() + ext
}

fun clearFolder(file: File?) {
    if (file == null) return
    if (file.isDirectory) {
        val list = file.list();
        for (child in list) {
            File(child).delete()
        }
    }
}

fun InputStream?.safeClose() {
    try {
        this?.close()
    } catch (e: IOException) {
        e.printStackTrace()
    }
}


fun OutputStream?.safeClose() {
    try {
        this?.close()
    } catch (e: IOException) {
        e.printStackTrace()
    }
}
