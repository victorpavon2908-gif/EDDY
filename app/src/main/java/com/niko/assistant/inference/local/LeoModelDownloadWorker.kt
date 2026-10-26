package com.niko.assistant.inference.local

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.work.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Durable opt-in work. Only catalog IDs persist; credentials and conversation never do. */
class LeoModelDownloadWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val spec = LeoLocalModelCatalog.find(inputData.getString(MODEL)) ?: return Result.failure()
        return try {
            // Bound each attempt below Android's data-sync foreground-service time allowance.
            val completed = withTimeoutOrNull(45 * 60 * 1000L) {
                report("Preparando ${spec.label}")
                LeoLocalModelStore(applicationContext, spec).install { report(it) }
                true
            } == true
            if (completed) Result.success(workDataOf(STATUS to "Modelo activado: ${spec.label}"))
            else retryOrFail("Tiempo agotado; el archivo parcial se conserva.")
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: IOException) { retryOrFail("Conexión interrumpida; descarga parcial conservada.") }
        catch (error: Exception) {
            Result.failure(workDataOf(STATUS to (error.message ?: "No se pudo validar o activar. Revisá RAM y espacio, y reintentá desde Ajustes.")))
        }
    }
    private fun retryOrFail(message: String): Result = if (runAttemptCount < 3) Result.retry()
        else Result.failure(workDataOf(STATUS to message))
    private suspend fun report(message: String) {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Descargas de LEO", NotificationManager.IMPORTANCE_LOW))
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Cerebro de LEO").setContentText(message).setOnlyAlertOnce(true).setOngoing(true)
            .addAction(android.R.drawable.ic_delete, "Cancelar", WorkManager.getInstance(applicationContext).createCancelPendingIntent(id))
            .build()
        setForeground(ForegroundInfo(7042, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC))
        setProgress(workDataOf(STATUS to message))
    }
    companion object {
        const val NAME = "leo-authorized-model-install"
        const val MODEL = "model_id"
        const val STATUS = "status"
        private const val CHANNEL = "leo_model_download"
        fun request(model: LeoLocalModelSpec): OneTimeWorkRequest = OneTimeWorkRequestBuilder<LeoModelDownloadWorker>()
            .setInputData(workDataOf(MODEL to model.id))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).setRequiresStorageNotLow(true).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build()
        fun enqueue(context: Context, model: LeoLocalModelSpec) = WorkManager.getInstance(context)
            .enqueueUniqueWork(NAME, ExistingWorkPolicy.REPLACE, request(model))
    }
}
