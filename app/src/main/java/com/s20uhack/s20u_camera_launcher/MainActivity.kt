package com.s20uhack.s20u_camera_launcher

import android.app.AlertDialog
import android.content.ContentValues
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.MediaStore.Images
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.s20uhack.s20u_camera_launcher.ui.theme.S20U_camera_launcherTheme
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date


class MainActivity : ComponentActivity() {
    private val tag: String = "S20UcameraLauncher"
    private val filenamePrefix: String = "" // "S20U_"
    private val RequestImageCapture: Int = 1;
    private var currentPhotoPath: String? = null
    private var currentPhotoFilename: String? = null
    private var currentImage: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            S20U_camera_launcherTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background
                ) {
                    if (checkPermission()) {
                        dispatchTakePictureIntent()
                    }
                }
            }
        }
    }

    private val PERMISSION_REQUEST_CODE = 200
    private fun checkPermission(): Boolean {
        requestPermission()
        return if (ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // Permission is not granted
            Log.w(tag, "Camera permission was not granted")
            Toast.makeText(
                this, "Camera Permission is Required to Use camera.", Toast.LENGTH_SHORT
            ).show()
            finish()
            false
        } else {
            // Camera permission granted
            true
        }
    }

    private fun requestPermission() {
        ActivityCompat.requestPermissions(
            this, arrayOf<String>(android.Manifest.permission.CAMERA), PERMISSION_REQUEST_CODE
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        if (permissions != null) super.onRequestPermissionsResult(
            requestCode, permissions, grantResults
        )

        if (requestCode == CAMERA_PERM_CODE) {
            if (grantResults.size > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                dispatchTakePictureIntent()
            } else {
                Toast.makeText(
                    this, "Camera Permission is Required to Use camera.", Toast.LENGTH_SHORT
                ).show()
            }
        }/*
        when (requestCode) {
            PERMISSION_REQUEST_CODE -> if (grantResults.size > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(applicationContext, "Permission Granted", Toast.LENGTH_SHORT).show()

                // main logic
            } else {
                Toast.makeText(applicationContext, "Permission Denied", Toast.LENGTH_SHORT).show()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA)
                        != PackageManager.PERMISSION_GRANTED
                    ) {
                        showMessageOKCancel("You need to allow access permissions",
                            DialogInterface.OnClickListener { dialog, which ->
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                    requestPermission()
                                }
                            })
                    }
                }
            }
        }
        */
    }

    private fun showMessageOKCancel(message: String, okListener: DialogInterface.OnClickListener) {
        AlertDialog.Builder(this@MainActivity).setMessage(message)
            .setPositiveButton("OK", okListener).setNegativeButton("Cancel", null).create().show()
    }

    private fun dispatchTakePictureIntent() {
        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        // Ensure that there's a camera activity to handle the intent
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            // Create the File where the photo should go
            var photoFile: File? = null
            try {
                photoFile = createImageFile()
            } catch (ex: IOException) {
            }
            if (photoFile != null) {
                try {
                    val photoURI: Uri = FileProvider.getUriForFile(
                        this, "com.s20uhack.fileprovider", photoFile
                    )
                    Log.i(tag, "- photoURI = " + photoURI.toString())
                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                } catch (ex: IOException) {
                    Log.e(tag, "Cannot get file uri: " + ex.toString())
                }

                // Force using the tele-zoom camera as soon as possible
                takePictureIntent.putExtra("samsung.android.scaler.zoomMapRatio", 0.0)
                // Try to skip the confirmation, if allowed
                takePictureIntent.putExtra("android.intent.extra.quickCapture", true);
                startActivityForResult(takePictureIntent, CAMERA_REQUEST_CODE)
            } else {
                Log.e(tag, "CreateImageFile result = NULL")
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == CAMERA_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                var f = File(currentPhotoPath)
                Log.d(tag, "ABsolute Url of Image is " + Uri.fromFile(f))

                val copyTarget = File(
                    Environment.getExternalStorageDirectory()
                        .toString() + "/DCIM/Camera/" + currentPhotoFilename
                )
                try {
                    Log.i(
                        tag,
                        "Copy output photo to " + copyTarget.absolutePath
                    )
                    // Files.copy(f.toPath(), copyTarget.toPath())
                    f.copyTo(copyTarget)
                    f = copyTarget
                } catch (ex: Exception) {
                    Log.e(
                        tag,
                        "Failed to copy photo to " + copyTarget.toString() + " : err=" + ex.toString()
                    )
                }

                val mediaScanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
                val contentUri: Uri = Uri.fromFile(f)
                mediaScanIntent.setData(contentUri)
                this.sendBroadcast(mediaScanIntent)

                // Take another photo
                ClearPhoto()
                dispatchTakePictureIntent()
            } else if (resultCode == RESULT_CANCELED) {
                DeleteTempFile()
                finish()
            } else {
                Toast.makeText(this, "Camera intent failed.", Toast.LENGTH_SHORT).show()
                Log.e(tag, "Camera IMAGE_CAPTURE intent result is FAILED " + resultCode)
                DeleteTempFile()
                Thread.sleep(500)
                finish()
            }
        }
    }

    private fun DeleteTempFile() {
        currentImage?.delete()
        ClearPhoto()
    }

    private fun ClearPhoto() {
        currentImage = null
        currentPhotoFilename = null
        currentPhotoPath = null
    }

    @Throws(IOException::class)
    private fun createImageFile(): File {
        // Create an image file name
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        val imageFileName = filenamePrefix + timeStamp
        currentPhotoFilename = imageFileName + ".jpg"

        val storageDir: File? =
            getExternalFilesDir(Environment.DIRECTORY_DCIM)  // App data subfolder - OK
        val image: File = File.createTempFile(imageFileName, ".jpg", storageDir)

        // Save a file: path for use with ACTION_VIEW intents
        currentPhotoPath = image.getAbsolutePath()
        currentImage = image
        return image
    }

    companion object {
        const val CAMERA_PERM_CODE = 101
        const val CAMERA_REQUEST_CODE = 102
    }
}
