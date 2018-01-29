package com.alkurop.github.mediapicker

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.support.v4.app.Fragment
import android.support.v4.content.FileProvider
import android.webkit.MimeTypeMap
import io.reactivex.Notification
import io.reactivex.Observable
import io.reactivex.observers.DisposableObserver
import io.reactivex.schedulers.Schedulers.io
import io.reactivex.subjects.PublishSubject
import java.io.File


private val CAMERA_URI = "pendingCamera"
private val MEDIA_TYPE = "mediaType"


class MediaPicker(context: Context) {
    companion object {
        private const val CODE_GALLERY = 1001
        private const val CODE_CAMERA = 1002
    }

    private val statePublsher = PublishSubject.create<Notification<Pair<MediaType, Uri>>>()
    private val appContext = context.applicationContext
    private var pendingCameraUri: Uri? = null
    private var type: MediaType? = null

    fun fromGallery(activity: Activity, mediaType: MediaType) {
        type = mediaType
        try {
            activity.startActivityForResult(getGalleryIntent(mediaType), CODE_GALLERY)
        } catch (e: Throwable) {
            statePublsher.onNext(Notification.createOnError(e))
        }
    }

    fun fromCamera(activity: Activity, mediaType: MediaType) {
        type = mediaType
        try {
            activity.startActivityForResult(getCameraIntent(mediaType), CODE_CAMERA)
        } catch (e: Throwable) {
            statePublsher.onNext(Notification.createOnError(e))
        }
    }

    fun fromGallery(fragment: Fragment, mediaType: MediaType) {
        type = mediaType
        try {
            fragment.startActivityForResult(getGalleryIntent(mediaType), CODE_GALLERY)
        } catch (e: Throwable) {
            statePublsher.onNext(Notification.createOnError(e))
        }
    }

    fun fromCamera(fragment: Fragment, mediaType: MediaType) {
        type = mediaType
        try {
            fragment.startActivityForResult(getCameraIntent(mediaType), CODE_CAMERA)
        } catch (e: Throwable) {
            statePublsher.onNext(Notification.createOnError(e))
        }
    }

    private fun getCameraIntent(mediaType: MediaType): Intent {
        val intent = Intent(if (mediaType == MediaType.PHOTO) MediaStore.ACTION_IMAGE_CAPTURE else MediaStore.ACTION_VIDEO_CAPTURE)
        val ext = if (mediaType == MediaType.PHOTO) ".jpg" else ".mp4"
        val photoURI = FileProvider
                .getUriForFile(appContext,
                        appContext.applicationContext.packageName + ".com.alkurop.github.mediapicker.MediaFileProvider",
                        File(getFileDirectory(), createFileName(ext)))

        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
        return intent
    }

    private fun getGalleryIntent(mediaType: MediaType): Intent {
        val action: String = Intent.ACTION_GET_CONTENT
        val intent = Intent(action)
        intent.type = when (mediaType) {
            MediaType.PHOTO -> "image/*"
            MediaType.VIDEO -> "video/*"
            MediaType.AUDIO -> "audio/*"
        }
        return intent
    }

    fun handleResult(requestCode: Int, resultCode: Int, data: Intent?): Observable<Pair<Uri, MediaType>> {
        val error = IllegalStateException("Request failed")

        if (requestCode == CODE_GALLERY && resultCode == Activity.RESULT_OK) {
            if (data == null) {
                statePublsher.onError(error)
            } else {
                val contentUri = data.data
                if (contentUri !== null) {
                    copyToLocal(contentUri)
                            .map {
                                galleryAddPic(appContext, it)
                                Pair(type!!, it)
                            }
                            .materialize()
                            .subscribeOn(io())
                            .subscribeWith(Subscriber(statePublsher))
                } else {
                    statePublsher.onError(error)
                }
            }
        } else if (requestCode == CODE_CAMERA && resultCode == Activity.RESULT_OK) {
            if (pendingCameraUri != null) {
                statePublsher.onNext(Notification.createOnNext(Pair(type!!, pendingCameraUri!!)))
                statePublsher.onNext(Notification.createOnComplete())
            } else {
                statePublsher.onNext(Notification.createOnError(error))
            }
        }
        return statePublsher.dematerialize()
    }

    private fun copyToLocal(uri: Uri): Observable<Uri> {
        return Observable.fromCallable {

            val contentResolver = appContext.contentResolver
            val type = contentResolver.getType(uri)
            val singleton = MimeTypeMap.getSingleton()

            var ext = singleton.getExtensionFromMimeType(type)
            if (null === ext) {
                ext = ".jpg"
            } else {
                ext = "." + ext
            }
            val file = File(getFileDirectory(), createFileName(ext))

            file.outputStream().buffered().use { bufferedOutputStream ->
                contentResolver.openInputStream(uri).buffered().use { bufferedInputStream ->
                    bufferedInputStream.copyTo(bufferedOutputStream)
                }
            }
            Uri.fromFile(file)
        }
    }

    fun onSaveState(bundle: Bundle) {
        bundle.putParcelable(CAMERA_URI, pendingCameraUri)
        bundle.putParcelable(CAMERA_URI, pendingCameraUri)
    }

    fun onRestore(bundle: Bundle?) {
        pendingCameraUri = bundle?.getParcelable(CAMERA_URI)
    }

    private fun galleryAddPic(context: Context, uri: Uri) {
        val mediaScanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
        mediaScanIntent.data = uri
        context.sendBroadcast(mediaScanIntent)
    }

}

class Subscriber<T>(val publisher: PublishSubject<T>) : DisposableObserver<T>() {
    override fun onComplete() {
        publisher.onComplete()
    }

    override fun onNext(value: T) {
        publisher.onNext(value)
    }

    override fun onError(e: Throwable) {
        publisher.onError(e)
    }
}

enum class MediaType {
    PHOTO,
    VIDEO,
    AUDIO
}
