package com.eddy.assistant.proactive

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.eddy.assistant.brain.AssistantCommand
import com.eddy.assistant.memory.EddyMemory
import java.util.Calendar

class EddyProactiveScheduler(
    private val context: Context,
    private val memory: EddyMemory,
) {
    fun maybeSchedule(command: AssistantCommand) {
        if (!memory.shouldScheduleProactive(command)) return
        val message = memory.proactiveMessage(command) ?: return

        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val key = requestKey(command)
        val intent = Intent(context, EddyProactiveReceiver::class.java).apply {
            putExtra(EddyProactiveReceiver.EXTRA_MESSAGE, message)
            putExtra(EddyProactiveReceiver.EXTRA_NOTIFICATION_ID, key)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            key,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val nextDay = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        alarmManager.setInexactRepeating(
            AlarmManager.RTC_WAKEUP,
            nextDay.timeInMillis,
            AlarmManager.INTERVAL_DAY,
            pendingIntent,
        )
        memory.markProactiveScheduled(command)
    }

    private fun requestKey(command: AssistantCommand): Int = when (command) {
        is AssistantCommand.OpenApp -> 1_000 + command.app.ordinal
        AssistantCommand.OpenCamera -> 2_001
        is AssistantCommand.OpenMaps -> 2_002
        else -> 9_999
    }
}
