package com.example.system

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

object SupportHelper {
    const val DEVELOPER_WHATSAPP_RAW = "+254707934241"
    const val DEVELOPER_WHATSAPP_DIGITS = "254707934241"

    fun openDeveloperWhatsApp(
        context: Context,
        customTopic: String = "Hello Developer, I need support with THE MASTER MIND app."
    ) {
        val encodedText = Uri.encode(customTopic)
        val waWebUri = Uri.parse("https://wa.me/$DEVELOPER_WHATSAPP_DIGITS?text=$encodedText")
        val waDeepUri = Uri.parse("whatsapp://send?phone=$DEVELOPER_WHATSAPP_DIGITS&text=$encodedText")

        val webIntent = Intent(Intent.ACTION_VIEW, waWebUri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val directIntent = Intent(Intent.ACTION_VIEW, waDeepUri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            if (directIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(directIntent)
            } else {
                context.startActivity(webIntent)
            }
        } catch (_: Exception) {
            try {
                context.startActivity(webIntent)
            } catch (_: Exception) {
                Toast.makeText(
                    context,
                    "WhatsApp support: $DEVELOPER_WHATSAPP_RAW",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}

object BackgroundMediaStorage {
    private const val FILE_NAME = "custom_master_bg.jpg"

    fun backgroundFile(context: Context): File = File(context.filesDir, FILE_NAME)

    fun hasCustomBackground(context: Context): Boolean = backgroundFile(context).exists()

    fun savePickedImage(context: Context, uri: Uri): Boolean {
        return try {
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOpts)
            }
            val maxDim = max(boundsOpts.outWidth, boundsOpts.outHeight).coerceAtLeast(1)
            var sampleSize = 1
            while (maxDim / sampleSize > 1600) {
                sampleSize *= 2
            }
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOpts)
            } ?: return false

            val outFile = backgroundFile(context)
            FileOutputStream(outFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 88, out)
            }
            bitmap.recycle()
            true
        } catch (_: Exception) {
            false
        }
    }

    fun clearCustomBackground(context: Context): Boolean {
        val file = backgroundFile(context)
        return if (file.exists()) file.delete() else true
    }

    fun loadBitmap(context: Context): Bitmap? {
        val file = backgroundFile(context)
        if (!file.exists()) return null
        return try {
            BitmapFactory.decodeFile(file.absolutePath)
        } catch (_: Exception) {
            null
        }
    }
}

object ProfileMediaStorage {
    private fun fileNameFor(userId: String?): String {
        val safeUid = userId?.replace(Regex("[^a-zA-Z0-9_-]"), "")?.take(64).orEmpty()
        return if (safeUid.isNotBlank()) "custom_profile_photo_$safeUid.jpg" else "custom_profile_photo.jpg"
    }

    fun profileFile(context: Context, userId: String? = null): File =
        File(context.filesDir, fileNameFor(userId))

    fun hasProfilePhoto(context: Context, userId: String? = null): Boolean =
        profileFile(context, userId).exists()

    fun savePickedProfileImage(context: Context, uri: Uri, userId: String? = null): Boolean {
        return try {
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOpts)
            }
            val maxDim = max(boundsOpts.outWidth, boundsOpts.outHeight).coerceAtLeast(1)
            var sampleSize = 1
            while (maxDim / sampleSize > 720) {
                sampleSize *= 2
            }
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOpts)
            } ?: return false

            val outFile = profileFile(context, userId)
            FileOutputStream(outFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            bitmap.recycle()
            true
        } catch (_: Exception) {
            false
        }
    }

    fun clearProfilePhoto(context: Context, userId: String? = null): Boolean {
        val file = profileFile(context, userId)
        return if (file.exists()) file.delete() else true
    }

    fun loadBitmap(context: Context, userId: String? = null): Bitmap? {
        val file = profileFile(context, userId)
        if (!file.exists()) return null
        return try {
            BitmapFactory.decodeFile(file.absolutePath)
        } catch (_: Exception) {
            null
        }
    }
}
