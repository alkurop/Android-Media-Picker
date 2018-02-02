package com.alkurop.github.mediapicker

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.support.v4.app.Fragment
import android.support.v4.content.FileProvider
import android.webkit.MimeTypeMap
import io.reactivex.Notification
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers.mainThread
import io.reactivex.schedulers.Schedulers.io
import io.reactivex.subjects.Subject
import java.io.File

internal class MediaPickerInternalFragment : Fragment() {

    companion object {
        private const val CODE_GALLERY = 1001
        private const val CODE_CAMERA = 1002
        private const val SUBSCRIBER_NAME = "SUBSCRIBER_NAME"
        private const val URI_KEY = "URI_KEY"
        private const val MEDIA_TYPE_KEY = "MEDIA_TYPE_KEY"

        fun createInstance(subscriberName: String, mediaType: MediaType): MediaPickerInternalFragment {
            val fragment = MediaPickerInternalFragment()
            val args = Bundle()
            args.putString(SUBSCRIBER_NAME, subscriberName)
            args.putInt(MEDIA_TYPE_KEY, mediaType.ordinal)
            fragment.arguments = args
            return fragment
        }
    }

    private var pendingCameraUri: Uri? = null
    private lateinit var resultSubject: Subject<Notification<Pair<MediaType, Uri>>>
    private var mediaType: MediaType? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingCameraUri = savedInstanceState?.getParcelable(URI_KEY)
        val string = arguments.getString(SUBSCRIBER_NAME)
        resultSubject = MediaPicker.resultSubjectMap[string]!!
        arguments.getInt(MEDIA_TYPE_KEY, -1).takeIf { it != -1 }?.let {
            mediaType = MediaType.values()[it]
        }
    }

    override fun onSaveInstanceState(outState: Bundle?) {
        val state = outState ?: Bundle()
        state.putParcelable(URI_KEY, pendingCameraUri)
        super.onSaveInstanceState(outState)
    }

    fun fromGallery(mediaType: MediaType) {
        this.mediaType = mediaType
        try {
            startActivityForResult(getGalleryIntent(mediaType), CODE_GALLERY)
        } catch (e: Throwable) {
            resultSubject.onNext(Notification.createOnError(e))
        }
    }

    fun fromCamera(mediaType: MediaType) {
        this.mediaType = mediaType
        try {
            startActivityForResult(getCameraIntent(mediaType), CODE_CAMERA)
        } catch (e: Throwable) {
            resultSubject.onNext(Notification.createOnError(e))
        }
    }

    private fun getCameraIntent(mediaType: MediaType): Intent {
        val intent = Intent(if (mediaType == MediaType.PHOTO) MediaStore.ACTION_IMAGE_CAPTURE else MediaStore.ACTION_VIDEO_CAPTURE)
        val ext = if (mediaType == MediaType.PHOTO) ".jpeg" else ".mp4"
        val createFileName = getFileDirectory(MediaPicker.imageDirectory) + createFileName(ext)
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
            MediaType.PHOTO -> "image/jpeg"
            MediaType.VIDEO -> "video/mp4"
            MediaType.AUDIO -> "audio/mp3   "
        }
        return intent
    }

    private fun handleResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val error = IllegalStateException("Request failed")

        if (requestCode == CODE_GALLERY && resultCode == Activity.RESULT_OK) {
            if (data == null) {
                resultSubject.onNext(Notification.createOnError(error))
            } else {
                val contentUri = data.data
                if (contentUri !== null) {
                    copyToLocal(contentUri).subscribeOn(io()).observeOn(mainThread()).subscribe {
                        resultSubject.onNext(Notification.createOnNext(Pair(mediaType!!, it)))
                    }
                } else {
                    resultSubject.onNext(Notification.createOnError(error))
                }
            }
        } else if (requestCode == CODE_CAMERA && resultCode == Activity.RESULT_OK) {
            if (pendingCameraUri != null) {

                galleryAddPic(pendingCameraUri!!)
                resultSubject.onNext(Notification.createOnNext(Pair(mediaType!!, pendingCameraUri!!)))

            } else {
                resultSubject.onNext(Notification.createOnError(error))
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        handleResult(requestCode, resultCode, data)
    }

    //unused method. If you decide to use ut plz add comment why
    private fun copyToLocal(uri: Uri): Observable<Uri> {
        return Observable.fromCallable {
            val contentResolver = activity.contentResolver
            val type = contentResolver.getType(uri)
            val singleton = MimeTypeMap.getSingleton()
            var ext = singleton.getExtensionFromMimeType(type)
            ext = if (null == ext) {
                ".jpeg"
            } else {
                "." + ext
            }
            val file = File(getFileDirectory(MediaPicker.imageDirectory), createFileName(ext))
            file.outputStream().buffered().use { bufferedOutputStream ->
                contentResolver.openInputStream(uri).buffered().use { bufferedInputStream ->
                    bufferedInputStream.copyTo(bufferedOutputStream)

                }
            }
            Uri.fromFile(file)
        }
    }

    private fun galleryAddPic(contentUri: Uri) {
        val mediaScanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
        mediaScanIntent.data = contentUri
        activity.sendBroadcast(mediaScanIntent)
    }

}
