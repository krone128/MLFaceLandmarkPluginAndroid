package com.visionsnap.facetracking

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ImageSaver {

    private const val TAG = "ImageSaver"

    suspend fun saveImageProxyToJpeg(context: Context, imageProxy: androidx.camera.core.ImageProxy, fileName: String? = null): Uri? = withContext(Dispatchers.IO) {
        val bitmap = imageProxy.toBitmap()

        run {
            val finalFileName = fileName ?: generateFileName()
            val imageFile = createImageFile(context, finalFileName)

            return@withContext try {
                val fos = FileOutputStream(imageFile)
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos) // Adjust quality as needed (0-100)
                fos.flush()
                fos.close()

                // Important: Update MediaStore so the image appears in Gallery apps
                val values = android.content.ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, finalFileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
                    put(MediaStore.Images.Media.DATE_MODIFIED, System.currentTimeMillis() / 1000)
                    put(MediaStore.Images.Media.IS_PENDING, 1) // Mark as pending initially
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/YourAppName") // Customize path
                    put(MediaStore.Images.Media.DATA, imageFile.absolutePath) // Directly set the path
                }

                val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)

                if (uri != null) {
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0) // Now it's not pending anymore
                    context.contentResolver.update(uri, values, null, null)

                    imageProxy.close() // Close the ImageProxy
                    uri
                } else {
                    Log.e(TAG, "Failed to insert image into MediaStore")
                    imageProxy.close()
                    null
                }


            } catch (e: IOException) {
                Log.e(TAG, "Error saving image: ${e.message}")
                imageProxy.close()
                null
            }
        }
    }

    private fun generateFileName(): String {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        return "IMG_$timeStamp.jpg"
    }

    private fun createImageFile(context: Context, fileName: String): File {
        val storageDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES) // Or context.filesDir for internal storage
        return File(storageDir, fileName)
    }
}
