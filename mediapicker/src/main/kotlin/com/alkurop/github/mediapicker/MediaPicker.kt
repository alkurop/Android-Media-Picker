package com.alkurop.github.mediapicker

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
    private val mainFolder = "TestAPP"
    private val sdCardPath = Environment.getExternalStorageDirectory()
    private const val tag = "MediaPicker_Tag"
    internal val resultSubjectMap = hashMapOf<String, Subject<Notification<Pair<MediaType, Uri>>>>()
    private val fragmentMap = hashMapOf<String, MediaPickerInternalFragment>()
    var imageDirectory: String = "$sdCardPath/$mainFolder"

    fun fromGallery(activity: AppCompatActivity, mediaType: MediaType) {
        val name = activity.localClassName

        if (resultSubjectMap.containsKey(name).not()) {
            resultSubjectMap.put(name, createSubject())
        }

        val doerFragment = MediaPickerInternalFragment.createInstance(name)
        fragmentMap.put(name, doerFragment)

        activity.supportFragmentManager
                .beginTransaction()
                .add(doerFragment, tag)
                .commitNow()

        doerFragment.fromGallery(mediaType)

    }

    fun fromCamera(activity: AppCompatActivity, mediaType: MediaType) {
        val name = activity.localClassName
        if (resultSubjectMap.containsKey(name).not()) {
            resultSubjectMap.put(name, createSubject())
        }

        val doerFragment = MediaPickerInternalFragment.createInstance(name)
        fragmentMap.put(name, doerFragment)

        activity.supportFragmentManager
                .beginTransaction()
                .add(doerFragment, tag)
                .commitNow()

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
                    activity.supportFragmentManager
                            .beginTransaction()
                            .remove(fragmentMap[name])
                            .commitNowAllowingStateLoss()
                    fragmentMap.remove(name)
                }
    }

    private fun createSubject(): Subject<Notification<Pair<MediaType, Uri>>> =
            PublishSubject.create()
}

enum class MediaType {
    PHOTO,
    VIDEO,
    AUDIO
}
