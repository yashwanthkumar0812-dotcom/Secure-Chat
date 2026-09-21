package com.example.securechat.util

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log

object MediaSaver {

    fun saveMedia(context: Context, bytes: ByteArray, isAudio: Boolean) {
        val resolver = context.contentResolver
        val timestamp = System.currentTimeMillis()

        val contentValues = ContentValues().apply {
            if (isAudio) {
                put(MediaStore.Audio.Media.DISPLAY_NAME, "SecureChat_$timestamp.mp3")
                put(MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/SecureChat")
                    put(MediaStore.Audio.Media.IS_PENDING, 1)
                }
            } else {
                put(MediaStore.Images.Media.DISPLAY_NAME, "SecureChat_$timestamp.jpg")
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/SecureChat")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
        }

        val collectionUri = if (isAudio) {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val itemUri = resolver.insert(collectionUri, contentValues) ?: return

        try {
            resolver.openOutputStream(itemUri)?.use { outputStream ->
                outputStream.write(bytes)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(if (isAudio) MediaStore.Audio.Media.IS_PENDING else MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(itemUri, contentValues, null, null)
            }
        } catch (e: Exception) {
            Log.e("MediaSaver", "Failed to save media", e)
        }
    }
}
