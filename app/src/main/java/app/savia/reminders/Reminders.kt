package app.savia.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Programa todas las alarmas de recordatorios (medicamentos, citas, periodo, autoexploración). */
object ReminderScheduler {
    /** Recalcula y reprograma todo. Llamar al cambiar medicamentos/citas/ajustes y al arrancar. */
    fun rescheduleAll(context: Context) {}
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {}
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ReminderScheduler.rescheduleAll(context)
    }
}
