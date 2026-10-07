package com.vicechanger.app.utils

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * Share sheet wiring. Uses the platform's own ACTION_SEND chooser, which is what actually
 * works for Messenger, WhatsApp, Discord and everything else - there is no official Messenger
 * audio API, so pretending otherwise would be a lie in the UI.
 */
object ShareIntents {

    const val MESSENGER_PACKAGE = "com.facebook.orca"

    fun audioUri(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    /** Generic chooser: every app that accepts audio can be picked. */
    fun shareAudio(context: Context, file: File, displayName: String, title: String = "Share voice"): Intent {
        val uri = audioUri(context, file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "audio/x-wav"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TITLE, displayName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, title).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /** Direct-to-Messenger send, offered only when Messenger is actually installed. */
    fun shareToMessenger(context: Context, file: File, displayName: String): Intent? {
        if (!isInstalled(context, MESSENGER_PACKAGE)) return null
        val uri = audioUri(context, file)
        return Intent(Intent.ACTION_SEND).apply {
            type = "audio/x-wav"
            setPackage(MESSENGER_PACKAGE)
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TITLE, displayName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun isInstalled(context: Context, packageName: String): Boolean = runCatching {
        context.packageManager.getPackageInfo(packageName, 0)
        true
    }.getOrElse { false }

    /** True when any installed app can receive an audio ACTION_SEND. */
    fun hasAudioShareTarget(context: Context): Boolean {
        val intent = Intent(Intent.ACTION_SEND).apply { type = "audio/x-wav" }
        val flags = PackageManager.MATCH_DEFAULT_ONLY
        return context.packageManager.queryIntentActivities(intent, flags).isNotEmpty()
    }

    /** Open the app's system settings page, for the "permission denied for good" case. */
    fun appSettingsIntent(context: Context): Intent = Intent(
        android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null),
    ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
}
