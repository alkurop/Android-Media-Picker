package com.alkurop.github.mediapicker

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.support.v4.app.Fragment
import android.support.v4.content.ContextCompat
import android.webkit.MimeTypeMap
import rx.Observable
import rx.android.schedulers.AndroidSchedulers
import rx.functions.Func1
import rx.schedulers.Schedulers
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.IOException
import java.util.*

private val CAMERA_URI = "pendingCamera"


class MediaPicker(context: Context, val permissionsRequiredCallback: ((list: List<String>) -> Unit)) {
    val appContext = context.applicationContext
    val CODE_GALLERY = 1001
    val CODE_CAMERA = 1002

    var pendingCameraUri: Uri? = null

    fun fromGallery(activity: Activity, mediaType: MediaType) {
        if (!checkPermissions(activity)) return
        try {
            activity.startActivityForResult(getGalleryIntent(mediaType), CODE_GALLERY)
        } catch(e: Throwable) {
            e.printStackTrace()
        }
    }

    fun fromCamera(activity: Activity, mediaType: MediaType) {
        if (!checkPermissions(activity)) return
        try {
            activity.startActivityForResult(getCameraIntent(mediaType), CODE_CAMERA)
        } catch(e: Throwable) {
            e.printStackTrace()
        }
    }

    fun fromGallery(fragment: Fragment, mediaType: MediaType) {
        if (!checkPermissions(fragment.activity)) return
        try {
            fragment.startActivityForResult(getGalleryIntent(mediaType), CODE_GALLERY)
        } catch(e: Throwable) {
            e.printStackTrace()
        }
    }

    fun fromCamera(fragment: Fragment, mediaType: MediaType) {
        if (!checkPermissions(fragment.activity)) return
        try {
            fragment.startActivityForResult(getCameraIntent(mediaType), CODE_CAMERA)
        } catch(e: Throwable) {
            e.printStackTrace()
        }
    }

    private fun getCameraIntent(mediaType: MediaType): Intent {
        val intent = Intent(if (mediaType == MediaType.PHOTO) MediaStore.ACTION_IMAGE_CAPTURE else MediaStore.ACTION_VIDEO_CAPTURE)
        val ext = if (mediaType == MediaType.PHOTO) ".jpg" else ".mp4"
        pendingCameraUri = Uri.fromFile(File(getFileDirectory(), createFileName(ext)))
        intent.putExtra(MediaStore.EXTRA_OUTPUT, pendingCameraUri)
        return intent
    }

    private fun getGalleryIntent(mediaType: MediaType): Intent {
        var action: String = Intent.ACTION_GET_CONTENT
        var intent: Intent = Intent(action)
        intent.type = if (mediaType == MediaType.PHOTO) "image/*" else "video/*"
        return intent
    }

    private fun checkPermissions(context: Context): Boolean {
        val read = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE)
        val write = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        val list = ArrayList<String>()
        if (read != PackageManager.PERMISSION_GRANTED) {
            list.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        if (write != PackageManager.PERMISSION_GRANTED) {
            list.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        if (list.size > 0) {
            permissionsRequiredCallback.invoke(list)
        }
        return list.size == 0

    }

    fun handleResult(requestCode: Int, resultCode: Int, data: Intent?, callback: Callback) {
        if (requestCode == CODE_GALLERY && resultCode == Activity.RESULT_OK) {
            if (data == null) {
                callback.onFailed()
                return
            }
            val contentUri = data.data
            if (contentUri !== null) {
                callback.onProgress(contentUri, true)
                copyToLocal(contentUri)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .doOnTerminate { callback.onProgress(contentUri, false) }
                        .subscribe({ uri: Uri -> callback.onSuccess(uri) },
                                { callback.onFailed() })
            } else {
                callback.onFailed()
            }
        } else if (requestCode == CODE_CAMERA && resultCode == Activity.RESULT_OK) {
            if (pendingCameraUri != null) {
                callback.onSuccess(pendingCameraUri!!)
            } else {
                callback.onFailed()
            }
        }
    }

    private fun copyToLocal(uri: Uri): Observable<Uri> {
        return Observable.just(uri)
                .flatMap(Func1<Uri, Observable<Uri?>> { uri: Uri ->
                    var bufferedOutputStream: BufferedOutputStream? = null
                    var bufferedInputStream: BufferedInputStream? = null
                    var file: File? = null
                    try {
                        val contentResolver = appContext.contentResolver
                        val type = contentResolver.getType(uri)
                        val singleton = MimeTypeMap.getSingleton()
                        var ext = singleton.getExtensionFromMimeType(type)
                        if (null === ext) {
                            ext = ".jpg"
                        } else {
                            ext = "." + ext
                        }
                        file = File(getFileDirectory(), createFileName(ext))
                        bufferedOutputStream = file.outputStream().buffered()
                        bufferedInputStream = contentResolver.openInputStream(uri).buffered()
                        bufferedInputStream.copyTo(bufferedOutputStream)
                    } catch(e: Throwable) {
                        e.printStackTrace()
                    } finally {
                        bufferedInputStream.safeClose()
                        bufferedOutputStream.safeClose()
                    }
                    if (file === null) return@Func1 Observable.error(IOException("Failed to save file"))
                    return@Func1 Observable.just(Uri.fromFile(file))
                }
                )

    }

    fun onSaveState(bundle: Bundle) {
        bundle.putParcelable(CAMERA_URI, pendingCameraUri)
    }

    fun onRestore(bundle: Bundle?) {
        pendingCameraUri = bundle?.getParcelable<Uri>(CAMERA_URI)
    }

    interface Callback {
        fun onProgress(uri: Uri, inProgress: Boolean)

        fun onSuccess(uri: Uri)

        fun onFailed()
    }
}


enum class MediaType {
    PHOTO,
    VIDEO
}