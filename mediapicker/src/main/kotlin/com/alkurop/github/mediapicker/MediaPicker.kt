package com.alkurop.github.mediapicker

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.support.v7.app.AppCompatActivity
import io.reactivex.Notification
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject

object MediaPicker {
    var mainFolder = "MediaPicker"
    val sdCardPath = Environment.getExternalStorageDirectory().absolutePath
    private const val tag = "MediaPicker_Tag"
    internal val resultSubjectMap = hashMapOf<String, Subject<Notification<Pair<MediaType, Uri?>>>>()
    private val fragmentMap = hashMapOf<String, MediaPickerInternalFragment>()
    var fileDirectory: String = "${sdCardPath}/${mainFolder}"

    fun getFileProviderAuthority(context: Context): String {
        return context.applicationContext.packageName + ".com.alkurop.github.mediapicker.MediaFileProvider"
    }

    fun fromGallery(activity: AppCompatActivity, mediaType: MediaType) {
        val name = activity.localClassName

        if (resultSubjectMap.containsKey(name).not()) {
            resultSubjectMap.put(name, createSubject())
        }

        val doerFragment = MediaPickerInternalFragment.createInstance(name, mediaType)
        fragmentMap.put(name, doerFragment)

        activity.supportFragmentManager
            .beginTransaction()
            .add(doerFragment, tag)
            .commitNowAllowingStateLoss()

        doerFragment.fromGallery(mediaType)

    }

    fun fromCamera(activity: AppCompatActivity, mediaType: MediaType) {
        val name = activity.localClassName
        if (resultSubjectMap.containsKey(name).not()) {
            resultSubjectMap.put(name, createSubject())
        }

        val doerFragment = MediaPickerInternalFragment.createInstance(name, mediaType)
        fragmentMap.put(name, doerFragment)

        activity.supportFragmentManager
            .beginTransaction()
            .add(doerFragment, tag)
            .commitNowAllowingStateLoss()

        doerFragment.fromCamera(mediaType)

    }

    fun getResult(activity: AppCompatActivity): Observable<Pair<MediaType, Uri>> {
        val name = activity.localClassName
        if (resultSubjectMap.containsKey(name).not()) {
            resultSubjectMap.put(name, createSubject())
        }

        val mediaSubscriber = resultSubjectMap[name]!!
        return mediaSubscriber
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .filter { it.isOnNext || it.isOnError }
            .dematerialize<Pair<MediaType, Uri>>()
            .doFinally {
                val fragment = fragmentMap[name]
                fragment?.let {
                    activity.supportFragmentManager
                        .beginTransaction()
                        .remove(fragmentMap[name])
                        .commitNowAllowingStateLoss()
                    fragmentMap.remove(name)
                }
            }
    }

    fun createSubject(): Subject<Notification<Pair<MediaType, Uri?>>> =
        PublishSubject.create()
}

enum class MediaType {
    PHOTO,
    VIDEO,
    AUDIO,
    LOADING
}
