package dev.anujyadav.harmoniq.services.globalsearch.download

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import dev.anujyadav.harmoniq.MainActivity
import dev.anujyadav.harmoniq.R
import dev.anujyadav.harmoniq.utils.AppStrings
import java.util.UUID

/** Progress (foreground) and result notifications for Global Search downloads. */
class DownloadNotifications(private val context: Context) {
    private val manager = NotificationManagerCompat.from(context)

    fun ensureChannel() {
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName(AppStrings.DownloadsChannelName)
                .setShowBadge(false)
                .setVibrationEnabled(false)
                .build()
        )
    }

    fun foregroundInfo(workId: UUID, title: String, text: String, percent: Int?): ForegroundInfo {
        val notification = progressNotification(workId, title, text, percent)
        val id = progressNotificationId(workId)
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                ForegroundInfo(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)

            else -> ForegroundInfo(id, notification)
        }
    }

    fun showSaved(workId: UUID, title: String) = notify(
        resultNotificationId(workId),
        baseBuilder(AppStrings.SavedX(title), AppStrings.DownloadSaved)
            .setAutoCancel(true)
            .build(),
    )

    fun showFailed(workId: UUID, title: String, reason: String) = notify(
        resultNotificationId(workId),
        baseBuilder(AppStrings.DownloadOfXFailedY(title, reason), reason)
            .setAutoCancel(true)
            .build(),
    )

    private fun progressNotification(workId: UUID, title: String, text: String, percent: Int?): Notification {
        val cancel = WorkManager.getInstance(context).createCancelPendingIntent(workId)
        return baseBuilder(AppStrings.DownloadingX(title), text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(PERCENT_MAX, percent ?: 0, percent == null)
            .addAction(R.drawable.material_icon_close, AppStrings.Cancel, cancel)
            .build()
    }

    private fun baseBuilder(title: String, text: String) = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.material_icon_download)
        .setContentTitle(title)
        .setContentText(text)
        .setCategory(NotificationCompat.CATEGORY_PROGRESS)
        .setContentIntent(openAppIntent())
        .setSilent(true)

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun notify(id: Int, notification: Notification) {
        // Android 13+: without the permission the result is simply not shown
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        manager.notify(id, notification)
    }

    companion object {
        const val CHANNEL_ID = "global_search_downloads"
        private const val PERCENT_MAX = 100

        // progress and result share no id, so the result survives the foreground notification going away
        private fun progressNotificationId(workId: UUID) = workId.hashCode()
        private fun resultNotificationId(workId: UUID) = workId.hashCode() xor RESULT_ID_SALT
        private const val RESULT_ID_SALT = 0x5EED
    }
}
