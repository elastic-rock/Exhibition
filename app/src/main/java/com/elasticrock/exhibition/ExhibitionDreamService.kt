package com.elasticrock.exhibition

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.icu.text.SimpleDateFormat
import android.net.Uri
import android.os.Build
import android.os.Looper
import android.provider.MediaStore
import android.provider.MediaStore.Images
import android.service.dreams.DreamService
import android.text.format.DateUtils
import android.util.Log
import android.view.View.VISIBLE
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.core.os.HandlerCompat.postDelayed
import java.util.Date
import java.util.Locale
import kotlin.random.Random

class ExhibitionDreamService : DreamService() {

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

                val idColumn = cursor.getColumnIndexOrThrow(Images.Media._ID)
                val exposureColumn = cursor.getColumnIndexOrThrow(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) { Images.Media.EXPOSURE_TIME } else { null })
                val apertureColumn = cursor.getColumnIndexOrThrow(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) { Images.Media.F_NUMBER } else { null })
                val isoColumn = cursor.getColumnIndexOrThrow(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) { Images.Media.ISO } else { null })
                val dataColumn = cursor.getColumnIndexOrThrow(Images.Media.DATA)
                val dateColumn = cursor.getColumnIndexOrThrow(Images.Media.DATE_TAKEN)

                while (cursor.moveToNext()) {

                    val id = cursor.getLong(idColumn)
                    val exposure = cursor.getDouble(exposureColumn)
                    val aperture = cursor.getString(apertureColumn)
                    val iso = cursor.getInt(isoColumn)
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

        val numberOfURIs = imageList.size

        fun displayNextImage() {
            val imageSwitchDelayMillis = 10000L
            val currentIndex = Random.nextInt(1, numberOfURIs)
            val contentUri = imageList[currentIndex].uri
            val path = imageList[currentIndex].path
            val dateRaw = imageList[currentIndex].datetaken
            val exposure = imageList[currentIndex].exposure
            val aperture = imageList[currentIndex].aperture
            val iso = imageList[currentIndex].iso

            val photoImageView = findViewById<ImageView>(R.id.image_view)
            val bitmap = loadBitmapFromUri(contentUri, contentResolver)
            if (bitmap != null) {
                photoImageView.setImageBitmap(bitmap)
            }

            findViewById<TextView>(R.id.path).text = path
            findViewById<TextView>(R.id.photo_properties).text = "$exposure, f$aperture, $iso"

            fun formatDateTime(context: Context, dateTaken: Long): String {
                val now = System.currentTimeMillis()
                return DateUtils.getRelativeTimeSpanString(dateTaken, now, DateUtils.MINUTE_IN_MILLIS).toString()
            }
            if (dateRaw != null) {
                val date = formatDateTime(this, dateRaw)
                findViewById<TextView>(R.id.date_taken).text = date
            }

            postDelayed(
                android.os.Handler(Looper.getMainLooper()),
                { displayNextImage() },
                null,
                imageSwitchDelayMillis)
        }

        if (numberOfURIs == 0) {
            findViewById<TextView>(R.id.no_files).visibility = VISIBLE
        } else {
            displayNextImage()
        }
    }

}