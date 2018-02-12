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
import io.reactivex.android.schedulers.AndroidSchedulers.mainThread
import io.reactivex.schedulers.Schedulers.io
import io.reactivex.subjects.Subject
import java.io.File
import android.media.ExifInterface
import android.os.Build
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.BitmapFactory
import android.R.array
import android.R.attr.bitmap
import android.opengl.ETC1.getHeight
import io.reactivex.Completable
import java.io.ByteArrayInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.channels.Channels


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
        val createFileName = getFileDirectory(MediaPicker.fileDirectory) + "/" + createFileName(ext)
        val authority = MediaPicker.getFileProviderAuthority(activity.applicationContext)
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
            MediaType.AUDIO -> "audio/mp3"
            else -> {
                ""
            }
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
                resultSubject.onNext(Notification.createOnNext(Pair(MediaType.LOADING, pendingCameraUri!!)))

                Completable.fromAction { handleSamplingAndRotationBitmap(context, pendingCameraUri!!) }
                    .subscribeOn(io())
                    .observeOn(mainThread())
                    .subscribe({
                                   galleryAddPic(pendingCameraUri!!)
                                   resultSubject.onNext(Notification.createOnNext(Pair(mediaType!!, pendingCameraUri!!)))
                               }, {
                                   resultSubject.onNext(Notification.createOnError(it))

                               })
            } else {
                resultSubject.onNext(Notification.createOnError(error))
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        handleResult(requestCode, resultCode, data)
    }

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
            val file = File(getFileDirectory(MediaPicker.fileDirectory), createFileName(ext))
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

    fun handleSamplingAndRotationBitmap(context: Context, selectedImage: Uri) {

        val options = BitmapFactory.Options()
        options.inJustDecodeBounds = true
        val path = MediaPicker.fileDirectory + "/" + selectedImage.lastPathSegment
        val img = BitmapFactory.decodeFile(path)
        val rotateImg = rotateImageIfRequired(context, img, selectedImage) ?: return

        val out = FileOutputStream(path)
        out.use {
            rotateImg.compress(Bitmap.CompressFormat.JPEG, 80, out)
        }
    }


    private fun rotateImageIfRequired(context: Context, img: Bitmap, selectedImage: Uri): Bitmap? {

        val input = context.contentResolver.openInputStream(selectedImage)
        val ei: ExifInterface
        if (Build.VERSION.SDK_INT > 23)
            ei = ExifInterface(input)
        else
            ei = ExifInterface(selectedImage.path)

        val orientation = ei.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)

        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> return rotateImage(img, 90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> return rotateImage(img, 180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> return rotateImage(img, 270f)
            else -> return null
        }
    }

    private fun rotateImage(img: Bitmap, degree: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(degree)
        val rotatedImg = Bitmap.createBitmap(img, 0, 0, img.width, img.height, matrix, true)
        img.recycle()
        return rotatedImg
    }

}
