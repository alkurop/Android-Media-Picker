package com.alkurop.github.mediapicker

import android.app.Activity
import android.app.Fragment
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.support.v4.content.FileProvider
import android.webkit.MimeTypeMap
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers.mainThread
import io.reactivex.observers.DisposableObserver
import io.reactivex.schedulers.Schedulers.io
import io.reactivex.subjects.PublishSubject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.IOException

class DoerFragment : Fragment() {

    companion object {
        private const val CODE_GALLERY = 1001
        private const val CODE_CAMERA = 1002
    }

    private var pendingCameraUri: Uri? = null
    private lateinit var subscriber: Subscriber

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        retainInstance = true
    }
;
    override fun onSaveInstanceState(outState: Bundle?) {
        val bundle = outState ?: Bundle()
        bundle.putString("sad", "sad")
        super.onSaveInstanceState(bundle)
    }

    fun fromGallery(mediaType: MediaType): Observable<Uri> {
        subscriber = Subscriber()
        try {
            startActivityForResult(getGalleryIntent(mediaType), CODE_GALLERY)
        } catch (e: Throwable) {
            subscriber.resultPublisher.onError(e)
        }
        return subscriber.resultPublisher.subscribeOn(io()).observeOn(mainThread())
    }

    fun fromCamera(mediaType: MediaType): Observable<Uri> {
        subscriber = Subscriber()

        try {
            startActivityForResult(getCameraIntent(mediaType), CODE_CAMERA)
        } catch (e: Throwable) {
            subscriber.resultPublisher.onError(e)
        }
        return subscriber.resultPublisher.subscribeOn(io()).observeOn(mainThread())
    }

    private fun getCameraIntent(mediaType: MediaType): Intent {
        val intent = Intent(if (mediaType == MediaType.PHOTO) MediaStore.ACTION_IMAGE_CAPTURE else MediaStore.ACTION_VIDEO_CAPTURE)
        val ext = if (mediaType == MediaType.PHOTO) ".jpg" else ".mp4"
        val createFileName = getFileDirectory() + createFileName(ext)
        val authority = activity.applicationContext.packageName + ".com.alkurop.github.mediapicker.MediaFileProvider"
        pendingCameraUri = FileProvider.getUriForFile(activity, authority, File(createFileName))

        intent.putExtra(MediaStore.EXTRA_OUTPUT, pendingCameraUri)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
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

    private fun handleResult(requestCode: Int, resultCode: Int, data: Intent?): Observable<Uri> {
        val statePublsher = PublishSubject.create<Uri>()
        val error = IllegalStateException("Request failed")

        if (requestCode == CODE_GALLERY && resultCode == Activity.RESULT_OK) {
            if (data == null) {
                statePublsher.onError(error)
            } else {
                val contentUri = data.data
                if (contentUri !== null) {
                    return copyToLocal(activity.applicationContext, contentUri)
                } else {
                    statePublsher.onError(error)
                }
            }
        } else if (requestCode == CODE_CAMERA && resultCode == Activity.RESULT_OK) {
            if (pendingCameraUri != null) {
                statePublsher.onNext(pendingCameraUri!!)
                statePublsher.onComplete()
            } else {
                statePublsher.onError(error)
            }
        }
        return statePublsher
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        handleResult(requestCode, resultCode, data).subscribeWith(subscriber)
    }

    private fun copyToLocal(appContext: Context, input: Uri): Observable<Uri> {
        return Observable.just(input)

                .flatMap { uri: Uri ->
                    var bufferedOutputStream: BufferedOutputStream? = null
                    var bufferedInputStream: BufferedInputStream? = null
                    var file: File? = null
                    try {
                        val contentResolver = appContext.contentResolver
                        val type = contentResolver.getType(uri)
                        val singleton = MimeTypeMap.getSingleton()
                        var ext = singleton.getExtensionFromMimeType(type)
                        ext = if (null == ext) {
                            ".jpg"
                        } else {
                            "." + ext
                        }
                        file = File(getFileDirectory(), createFileName(ext))
                        bufferedOutputStream = file.outputStream().buffered()
                        bufferedInputStream = contentResolver.openInputStream(uri).buffered()
                        bufferedInputStream.copyTo(bufferedOutputStream)
                    } catch (e: Throwable) {
                        e.printStackTrace()
                    } finally {
                        bufferedInputStream.safeClose()
                        bufferedOutputStream.safeClose()
                    }
                    if (file === null) Observable.error<Uri>(IOException("Failed to save file"))
                    Observable.just(Uri.fromFile(file))
                }

    }

    class Subscriber : DisposableObserver<Uri>() {
        val resultPublisher = PublishSubject.create<Uri>()
        override fun onComplete() {
            resultPublisher.onComplete()
        }

        override fun onNext(t: Uri) {
            resultPublisher.onNext(t)
        }

        override fun onError(e: Throwable) {
            resultPublisher.onError(e)
        }
    }
}
