package com.elasticrock.exhibition

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.ContentUris
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.MediaStore.Images
import android.provider.MediaStore.getVersion
import android.service.dreams.DreamService
import android.text.format.DateUtils
import android.util.Log
import android.view.View.GONE
import android.view.View.VISIBLE
import android.widget.ImageView
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import kotlin.random.Random

class ExhibitionDreamService : DreamService() {

    private val imageListCachePath = "imagelist"
    private lateinit var imageListCacheFile : File
    private val defaultScope = CoroutineScope(Job() + Dispatchers.Default)
    private val mainScope = MainScope()
    private val tag = "DreamService"

    data class Image(
        val uri: Uri,
        val exposure: Double?,
        val aperture: String?,
        val iso: Int?,
        val path: String,
        val datetaken: Long?
    )

    private val imageList = mutableListOf<Image>()

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        imageListCacheFile = File(applicationContext.cacheDir, imageListCachePath)
        isInteractive = false
        isFullscreen = true
        setContentView(R.layout.exhibition_dream)

        fun indexImages() {
            Log.d(tag, "Indexing")

            val collection =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    Images.Media.getContentUri(
                        MediaStore.VOLUME_EXTERNAL
                    )
                } else {
                    Images.Media.EXTERNAL_CONTENT_URI
                }

            val projection =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    arrayOf(
                        Images.Media._ID,
                        Images.Media.EXPOSURE_TIME,
                        Images.Media.F_NUMBER,
                        Images.Media.ISO,
                        Images.Media.DATA,
                        Images.Media.DATE_TAKEN
                    )
                } else {
                    arrayOf(
                        Images.Media._ID,
                        Images.Media.DATA,
                        Images.Media.DATE_TAKEN
                    )
                }

            val query = contentResolver.query(
                collection,
                projection,
                null,
                null,
                null)
            query?.use { cursor ->

                val exposureColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    cursor.getColumnIndexOrThrow(Images.Media.EXPOSURE_TIME)
                } else {
                    null
                }
                val apertureColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    cursor.getColumnIndexOrThrow(Images.Media.F_NUMBER)
                } else {
                    null
                }
                val isoColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    cursor.getColumnIndexOrThrow(Images.Media.ISO)
                } else {
                    null
                }

                val idColumn = cursor.getColumnIndexOrThrow(Images.Media._ID)
                val dataColumn = cursor.getColumnIndexOrThrow(Images.Media.DATA)
                val dateColumn = cursor.getColumnIndexOrThrow(Images.Media.DATE_TAKEN)

                while (cursor.moveToNext()) {

                    val id = cursor.getLong(idColumn)
                    val exposure = if (exposureColumn != null) {
                        cursor.getDouble(exposureColumn)
                    } else {
                        null
                    }
                    val aperture = if (apertureColumn != null) {
                        cursor.getString(apertureColumn)
                    } else {
                        null
                    }
                    val iso = if (isoColumn != null) {
                        cursor.getInt(isoColumn)
                    } else {
                        null
                    }
                    val data = cursor.getString(dataColumn)
                    val date = cursor.getLong(dateColumn)

                    val contentUri: Uri = ContentUris.withAppendedId(
                        Images.Media.EXTERNAL_CONTENT_URI,
                        id
                    )

                    imageList += Image(contentUri, exposure, aperture, iso, data, date)
                }
            }
        }

        fun readFromCache() {
            Log.d(tag, "Reading from cache")
            val cachedText = imageListCacheFile.readText()
            val lines = cachedText.split("\n")

            var currentIndex = 0

            while (currentIndex < lines.size) {
                val uri = Uri.parse(lines[currentIndex++])
                val exposure = lines[currentIndex++].toDoubleOrNull()
                val aperture = lines[currentIndex++]
                val iso = lines[currentIndex++].toIntOrNull()
                val path = lines[currentIndex++]
                val dateTaken = lines[currentIndex++].toLongOrNull()

                imageList.add(
                    Image(uri, exposure, aperture, iso, path, dateTaken)
                )
            }
        }

        suspend fun writeToCache() {
            Log.d(tag, "Writing to cache")
            if (!imageListCacheFile.exists()) {
                imageListCacheFile.delete()
            }
            File.createTempFile(imageListCachePath, null, applicationContext.cacheDir)
            val textToWrite = imageList.joinToString("\n") { image ->
                "${image.uri}\n${image.exposure}\n${image.aperture}\n${image.iso}\n${image.path}\n${image.datetaken}"
            }
            imageListCacheFile.writeText(textToWrite)
            DataStore(dataStore).saveMediaStoreVersion(getVersion(applicationContext))
        }

        fun displayContent() {
            val numberOfURIs = imageList.size

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
            fun formatDateTime(dateTaken: Long): String {
                val now = System.currentTimeMillis()
                return DateUtils.getRelativeTimeSpanString(dateTaken, now, DateUtils.MINUTE_IN_MILLIS).toString()
            }
            fun displayImage() {
                val currentIndex = Random.nextInt(1, numberOfURIs)

                val contentUri = imageList[currentIndex].uri
                val path = imageList[currentIndex].path
                val dateRaw = imageList[currentIndex].datetaken
                val exposure = imageList[currentIndex].exposure
                val aperture = imageList[currentIndex].aperture
                val iso = imageList[currentIndex].iso

                val date : String? = if (dateRaw != null) {
                    formatDateTime(dateRaw)
                } else {
                    null
                }

                val photoImageView = findViewById<ImageView>(R.id.image_view)
                val bitmap = loadBitmapFromUri(contentUri, contentResolver)
                if (bitmap != null) {
                    photoImageView.setImageBitmap(bitmap)
                }

                @SuppressLint("SetTextI18n")
                findViewById<TextView>(R.id.metadata).text = (if (date != null) {"$date\n"} else {""}) +
                        (if (exposure != null && aperture != null && iso != null) {"$exposure, f$aperture, $iso\n"} else {""}) +
                        path
            }

            if (numberOfURIs == 0) {
                Log.d(tag, "No images found")
                findViewById<TextView>(R.id.no_files).visibility = VISIBLE
            } else {
                Log.d(tag, "Displaying image")
                findViewById<TextView>(R.id.no_files).visibility = GONE
                displayImage()
            }
        }

        defaultScope.launch {
            if (imageListCacheFile.exists() && DataStore(dataStore).readMediaStoreVersion() == getVersion(applicationContext)) {
                readFromCache()
            } else {
                indexImages()
                writeToCache()
            }
        }

        mainScope.launch {
            val imageSwitchDelayMillis = async { DataStore(dataStore).readTimeoutValue().toLong() }
            repeat(2147483647) {
                displayContent()
                delay(imageSwitchDelayMillis.await())
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        defaultScope.cancel()
    }

    override fun onDestroy() {
        super.onDestroy()
        mainScope.cancel()
    }
}