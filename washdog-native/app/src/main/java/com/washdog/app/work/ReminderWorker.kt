package com.washdog.app.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.washdog.app.MainActivity
import com.washdog.app.R
import com.washdog.app.data.AppRepository
import com.washdog.app.data.WashdogSupabase
import com.washdog.app.util.RemindLogic
import io.github.jan.supabase.auth.auth
import java.util.concurrent.TimeUnit

/**
 * Job periódico (1×/día) que revisa clientes sin visitar hace ≥30 días y lanza una
 * notificación local para recordar reagendar. Corre sin UI activa; lee la caché local
 * del usuario logueado.
 */
class ReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            WashdogSupabase.appContext = applicationContext
            try { WashdogSupabase.client.auth.awaitInitialization() } catch (_: Exception) {}
            if (WashdogSupabase.client.auth.currentUserOrNull() == null) return Result.success()

            val repo = AppRepository(applicationContext)
            val clients = repo.loadClients()
            val appointments = repo.loadAppointments()
            // Mismo criterio que la UI: ≥30 días sin visita y sin cita futura agendada.
            val toRemind = RemindLogic.clientsNeedingReminder(clients, appointments)
            if (toRemind.isNotEmpty()) notify(toRemind.size, toRemind.first().petName)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun notify(count: Int, firstPet: String) {
        val ctx = applicationContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return

        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Recordatorios", NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = "Clientes que necesitan reagendar" }
            )
        }

        val intent = Intent(ctx, MainActivity::class.java)
            .apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK }
        val pending = PendingIntent.getActivity(
            ctx, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val text = if (count == 1) "$firstPet lleva más de 30 días sin visitar"
                   else "$count clientes llevan más de 30 días sin visitar"

        val notification = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("🐾 Recordatorio WashDog")
            .setContentText(text)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(ctx).notify(NOTIF_ID, notification)
        } catch (_: SecurityException) { /* permiso revocado en carrera */ }
    }

    companion object {
        private const val CHANNEL_ID = "washdog_reminders"
        private const val NOTIF_ID   = 1001
        private const val WORK_NAME  = "washdog_reminder_daily"

        /** Programa (idempotente) la revisión diaria de recordatorios. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request
            )
        }
    }
}
