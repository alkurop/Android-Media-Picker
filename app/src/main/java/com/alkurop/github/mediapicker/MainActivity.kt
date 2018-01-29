package com.alkurop.github.mediapicker

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.widget.Toast
import com.github.alkurop.jpermissionmanager.PermissionOptionalDetails
import com.github.alkurop.jpermissionmanager.PermissionsManager
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {
    lateinit var permissionMananager: PermissionsManager
    lateinit var mediaPicker: MediaPicker
    val subscriber: ((Uri) -> Unit) = {
        Toast.makeText(this, it.toString(), Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        permissionMananager = PermissionsManager(this)
        mediaPicker = MediaPicker(this)
        imageFromCam.setOnClickListener {
            askCameraPermissions {
                mediaPicker.fromCamera(this, MediaType.PHOTO)
                        .subscribe(subscriber)

            }
        }
        imageFromGallery.setOnClickListener {
            askGaleryPermissions {
                mediaPicker.fromGallery(this, MediaType.PHOTO)
                        .subscribe(subscriber)
            }
        }
        audioFromGal.setOnClickListener {
            askGaleryPermissions {
                mediaPicker.fromGallery(this, MediaType.AUDIO)
                        .subscribe(subscriber)
            }
        }
        videoFromCam.setOnClickListener {
            askGaleryPermissions {
                mediaPicker.fromGallery(this, MediaType.VIDEO)
                        .subscribe(subscriber)
            }
        }
        videoFromGal.setOnClickListener {
            askGaleryPermissions {
                mediaPicker.fromCamera(this, MediaType.VIDEO)
                        .subscribe(subscriber)
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle?) {
        val state = outState ?: Bundle()
        mediaPicker.onSaveState(state)
        super.onSaveInstanceState(state)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle?) {
        super.onRestoreInstanceState(savedInstanceState)
        mediaPicker.onRestore(savedInstanceState)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        permissionMananager.onActivityResult(requestCode)
        mediaPicker.handleResult(requestCode, resultCode, data)

        super.onActivityResult(requestCode, resultCode, data)
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
