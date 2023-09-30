package com.elasticrock.exhibition

import android.content.ContentResolver
import android.content.ContentUris
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Looper
import android.provider.MediaStore
import android.provider.MediaStore.Images
import android.service.dreams.DreamService
import android.util.Log
import android.widget.ImageView
import androidx.core.os.HandlerCompat.postDelayed
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.random.Random

class ExhibitionDreamService : DreamService() {

    data class Image(val uri: Uri)
    private val imageList = mutableListOf<Image>()
    private val mainScope = MainScope()

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        setContentView(R.layout.exhibition_dream)

        fun getImagePaths() {

            val collection =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    Images.Media.getContentUri(
                        MediaStore.VOLUME_EXTERNAL
                    )
                } else {
                    Images.Media.EXTERNAL_CONTENT_URI
                }

            val projection = arrayOf(
                Images.Media._ID
            )

            val query = contentResolver.query(
                collection,
                projection,
                null,
                null,
                null)
            query?.use { cursor ->
                // Cache column indices.
                val idColumn = cursor.getColumnIndexOrThrow(Images.Media._ID)

                while (cursor.moveToNext()) {
                    // Get values of columns for a given Images.
                    val id = cursor.getLong(idColumn)

                    val contentUri: Uri = ContentUris.withAppendedId(
                        Images.Media.EXTERNAL_CONTENT_URI,
                        id
                    )

                    // Stores column values and the contentUri in a local object
                    // that represents the media file.
                    Log.d("DreamService", "$contentUri")
                    imageList += Image(contentUri)
                }
            }
        }

        getImagePaths()
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()

        fun loadBitmapFromUri(uri: Uri, contentResolver: ContentResolver): Bitmap? {
            return try {
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    BitmapFactory.decodeStream(inputStream)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

        fun displayNextImage() {
            val numberOfURIs = imageList.size
            val imageSwitchDelayMillis = 10000L // 10 seconds
            val currentIndex = Random.nextInt(1, numberOfURIs)
            val contentUri = imageList[currentIndex].uri
            // Load and display the image from the content URI using your preferred method
            Log.d("DreamService", "Display image $contentUri")
            val photoImageView = findViewById<ImageView>(R.id.image_view)
            val bitmap = loadBitmapFromUri(contentUri, contentResolver)
            if (bitmap != null) {
                photoImageView.setImageBitmap(bitmap)
            }

            // Schedule the next image to be displayed after a delay
            postDelayed(
                android.os.Handler(Looper.getMainLooper()),
                { displayNextImage() },
                null,
                imageSwitchDelayMillis)
        }
        mainScope.launch { displayNextImage() }
    }

    override fun onDreamingStopped() {
        super.onDreamingStopped()
        mainScope.cancel()
    }
}