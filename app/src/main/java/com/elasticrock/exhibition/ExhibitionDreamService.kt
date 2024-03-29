package com.elasticrock.exhibition

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.ContentUris
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.MediaStore.Images
import android.service.dreams.DreamService
import android.text.format.DateUtils
import android.view.View.GONE
import android.view.View.VISIBLE
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import android.widget.Toast.LENGTH_SHORT
import androidx.core.content.ContextCompat
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

class ExhibitionDreamService : DreamService() {

    private val mainScope = MainScope()

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

        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            "android.permission.READ_MEDIA_IMAGES"
        } else {
            "android.permission.READ_EXTERNAL_STORAGE"
        }

        if (ContextCompat.checkSelfPermission(applicationContext, permission) != PERMISSION_GRANTED) {
            finish()
            Toast.makeText(applicationContext, getString(R.string.access_to_storage_not_granted), LENGTH_SHORT).show()
        }

        isInteractive = false
        isFullscreen = true
        setContentView(R.layout.exhibition_dream)

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

    override fun onDreamingStarted() {
        super.onDreamingStarted()

        val numberOfURIs = imageList.size

        val imageHistoryList = mutableListOf<Int>()
        val imageHistoryListMaxSize = if (numberOfURIs >= 120) { 60 } else { numberOfURIs/2 }

        fun findCommonPrefix(strings: List<String>): String {
            if (strings.isEmpty()) return ""

            // Sort the strings to find the common prefix
            val sortedStrings = strings.sorted()
            val first = sortedStrings.first()
            val last = sortedStrings.last()

            // Find the common prefix between the first and last strings
            var i = 0
            while (i < first.length && i < last.length && first[i] == last[i]) {
                i++
            }

            // Return the common prefix
            return first.substring(0, i)
        }

        val commonPrefix = findCommonPrefix(imageList.map { it.path })

        fun displayContent() {

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

            fun getIndex(): Int {
                var randomIndex: Int

                if (imageHistoryList.size >= imageHistoryListMaxSize) {
                    imageHistoryList.removeFirst()
                }

                do {
                    randomIndex = Random.nextInt(1, numberOfURIs)
                } while (randomIndex in imageHistoryList)

                imageHistoryList.add(randomIndex)

                return randomIndex
            }

            val currentIndex = getIndex()

            val contentUri = imageList[currentIndex].uri
            val path = imageList[currentIndex].path.removePrefix(commonPrefix)
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
            findViewById<TextView>(R.id.no_files).visibility = VISIBLE
        } else {
            findViewById<TextView>(R.id.no_files).visibility = GONE
            mainScope.launch {
                val imageSwitchDelayMillis = async { DataStoreRepository(dataStore).readTimeoutValue().toLong() }
                while (true) {
                    displayContent()
                    delay(imageSwitchDelayMillis.await())
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mainScope.cancel()
    }
}