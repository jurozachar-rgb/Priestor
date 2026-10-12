package sk.kvz.priestor

import android.app.Application
import sk.kvz.priestor.reminders.Reminders

class PriestorApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Reminders.createChannel(this)
        Reminders.schedulePeriodic(this)
    }
}
