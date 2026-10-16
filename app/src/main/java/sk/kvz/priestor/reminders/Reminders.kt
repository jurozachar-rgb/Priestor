package sk.kvz.priestor.reminders

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import sk.kvz.priestor.MainActivity
import sk.kvz.priestor.R
import sk.kvz.priestor.data.Repo
import sk.kvz.priestor.data.Space
import sk.kvz.priestor.data.TaskItem
import sk.kvz.priestor.ui.formatDue
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Pripomienky termínov.
 *
 * Každý telefón si sám naplánuje lokálne upozornenia pre úlohy vo všetkých oblastiach,
 * kde je členom. Plán sa obnovuje pri otvorení aplikácie, po zmene úlohy,
 * po reštarte telefónu a každé 3 hodiny na pozadí (aby sa zachytili úlohy od rodiny).
 *
 *  - úloha s časom:  hodinu pred termínom a v čase termínu
 *  - úloha bez času: deň vopred o 18:00 a v deň termínu o 8:00
 */
object Reminders {
    const val CHANNEL_ID = "reminders"
    const val EXTRA_SPACE_ID = "spaceId"
    private const val PREFS = "reminders"
    private const val KEY_SCHEDULED = "scheduled"
    private const val SYNC_ONCE = "reminder-sync-once"
    private const val SYNC_PERIODIC = "reminder-sync-periodic"

    fun createChannel(context: Context) {
        val ch = NotificationChannel(CHANNEL_ID, "Pripomienky termínov", NotificationManager.IMPORTANCE_HIGH)
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    /** Naplánuje pravidelnú obnovu na pozadí (volá sa pri štarte aplikácie). */
    fun schedulePeriodic(context: Context) {
        val req = PeriodicWorkRequestBuilder<ReminderSyncWorker>(3, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(SYNC_PERIODIC, ExistingPeriodicWorkPolicy.KEEP, req)
    }

    /** Okamžitá obnova plánu (po zmene úlohy, po otvorení aplikácie). */
    fun syncNow(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            SYNC_ONCE, ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<ReminderSyncWorker>().build()
        )
    }

    private data class Trigger(val key: String, val at: Long, val whenText: String)

    private fun triggersFor(task: TaskItem): List<Trigger> {
        val due = task.dueAt ?: return emptyList()
        val zone = ZoneId.systemDefault()
        return if (task.hasTime) {
            listOf(
                Trigger("${task.id}|before", due - 60 * 60 * 1000, "o hodinu"),
                Trigger("${task.id}|at", due, "teraz"),
            )
        } else {
            val day = Instant.ofEpochMilli(due).atZone(zone).toLocalDate()
            listOf(
                Trigger("${task.id}|eve", day.minusDays(1).atTime(LocalTime.of(18, 0)).atZone(zone).toInstant().toEpochMilli(), "zajtra"),
                Trigger("${task.id}|day", day.atTime(LocalTime.of(8, 0)).atZone(zone).toInstant().toEpochMilli(), "dnes"),
            )
        }
    }

    fun reschedule(context: Context, items: List<Pair<Space, TaskItem>>) {
        val now = System.currentTimeMillis()
        val am = context.getSystemService(AlarmManager::class.java)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val old = prefs.getStringSet(KEY_SCHEDULED, emptySet()).orEmpty()
        val new = mutableSetOf<String>()

        for ((space, task) in items) {
            for (t in triggersFor(task)) {
                if (t.at <= now) continue
                val section = space.sectionName(task.sectionId)
                val text = listOfNotNull(space.name, section, formatDue(task.dueAt!!, task.hasTime))
                    .joinToString(" · ")
                val intent = Intent(context, ReminderReceiver::class.java)
                    .setData(Uri.parse("priestor://reminder/${t.key}"))
                    .putExtra("title", task.title)
                    .putExtra("text", text)
                    .putExtra(EXTRA_SPACE_ID, space.id)
                val pi = PendingIntent.getBroadcast(
                    context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                // Nepresný budík – nevyžaduje špeciálne povolenie, môže sa oneskoriť o pár minút.
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t.at, pi)
                new += t.key
            }
        }
        (old - new).forEach { cancel(context, am, it) }
        prefs.edit().putStringSet(KEY_SCHEDULED, new).apply()
    }

    fun cancelAll(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getStringSet(KEY_SCHEDULED, emptySet()).orEmpty().forEach { cancel(context, am, it) }
        prefs.edit().remove(KEY_SCHEDULED).apply()
    }

    private fun cancel(context: Context, am: AlarmManager, key: String) {
        val intent = Intent(context, ReminderReceiver::class.java).setData(Uri.parse("priestor://reminder/$key"))
        val pi = PendingIntent.getBroadcast(
            context, 0, intent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) ?: return
        am.cancel(pi)
        pi.cancel()
    }

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!Reminders.canNotify(context)) return
        val title = intent.getStringExtra("title") ?: return
        val spaceId = intent.getStringExtra(Reminders.EXTRA_SPACE_ID)
        val open = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(Reminders.EXTRA_SPACE_ID, spaceId)
        val key = intent.data?.toString() ?: title
        val contentPi = PendingIntent.getActivity(
            context, key.hashCode(), open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        // Na zamknutej obrazovke sa nezobrazí názov úlohy, len všeobecný text.
        val publicVersion = NotificationCompat.Builder(context, Reminders.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Priestor")
            .setContentText("Blíži sa termín úlohy")
            .build()
        val n = NotificationCompat.Builder(context, Reminders.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setContentTitle(title)
            .setContentText(intent.getStringExtra("text"))
            .setContentIntent(contentPi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(key.hashCode(), n)
        } catch (_: SecurityException) {
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Reminders.syncNow(context)
    }
}

class ReminderSyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        if (Firebase.auth.currentUser == null) {
            Reminders.cancelAll(applicationContext)
            return Result.success()
        }
        return try {
            Reminders.reschedule(applicationContext, Repo.openTasksWithDue())
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
