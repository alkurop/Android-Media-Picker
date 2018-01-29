package com.alkurop.github.mediapicker

import android.Manifest
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.support.v7.app.AppCompatActivity
import android.widget.Toast
import com.github.alkurop.jpermissionmanager.PermissionOptionalDetails
import com.github.alkurop.jpermissionmanager.PermissionsManager
import kotlinx.android.synthetic.main.activity_main.*
import timber.log.Timber

class MainActivity : AppCompatActivity() {
    lateinit var permissionMananager: PermissionsManager

    val mainFolder = "TestAPP"
    val sdCardPath = Environment.getExternalStorageDirectory()
    val filePath = "${sdCardPath}/${mainFolder}"

    val subscriber: ((Pair<MediaType, Uri>) -> Unit) = {
        Toast.makeText(this, it.toString(), Toast.LENGTH_SHORT).show()
        Timber.d("$it")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        permissionMananager = PermissionsManager(this)

        imageFromGallery.setOnClickListener {
            askGaleryPermissions {
                MediaPicker.fromGallery(this, MediaType.PHOTO)
            }
        }
        audioFromGal.setOnClickListener {
            askGaleryPermissions {
                MediaPicker.fromGallery(this, MediaType.AUDIO)
            }
        }
        videoFromCam.setOnClickListener {
            askGaleryPermissions {
                MediaPicker.fromGallery(this, MediaType.VIDEO)
            }
        }
        videoFromGal.setOnClickListener {
            askGaleryPermissions {
                MediaPicker.fromCamera(this, MediaType.VIDEO)
            }
        }
        imageFromCam.setOnClickListener {
            askCameraPermissions {
                MediaPicker.fromCamera(this, MediaType.PHOTO)
            }
        }

        MediaPicker.getResult(this)
                .subscribe({
                    subscriber.invoke(it)
                }, { Timber.e(it) })
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        permissionMananager.onRequestPermissionsResult(requestCode, permissions, grantResults)
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    fun askCameraPermissions(onSuccessOperator: (() -> Unit)) {
        permissionMananager.clearPermissionsListeners()
        permissionMananager.clearPermissions()

        val permissionCameraDetails = PermissionOptionalDetails(
                "camera",
                "camera"
        )

        val permissionStorageDetails = PermissionOptionalDetails(
                "storage",
                "storage"
        )

        permissionMananager.addPermissions(mapOf(
                Pair(Manifest.permission.CAMERA, permissionCameraDetails),
                Pair(Manifest.permission.WRITE_EXTERNAL_STORAGE, permissionStorageDetails)
        ))

        permissionMananager.addPermissionsListener {
            for (mutableEntry in it) {
                if (mutableEntry.value.not()) {
                    return@addPermissionsListener
                }
            }
            onSuccessOperator.invoke()
        }
        permissionMananager.makePermissionRequest()
    }

    fun askGaleryPermissions(onSuccessOperator: (() -> Unit)) {
        permissionMananager.clearPermissionsListeners()
        permissionMananager.clearPermissions()

        val permissionStorageDetails = PermissionOptionalDetails(
                "storage",
                "storage"
        )

        permissionMananager.addPermissions(mapOf(
                Pair(Manifest.permission.WRITE_EXTERNAL_STORAGE, permissionStorageDetails)
        ))

        permissionMananager.addPermissionsListener {
            for (mutableEntry in it) {
                if (mutableEntry.value.not()) {
                    return@addPermissionsListener
                }
            }
            onSuccessOperator.invoke()
        }
        permissionMananager.makePermissionRequest()
    }
}
