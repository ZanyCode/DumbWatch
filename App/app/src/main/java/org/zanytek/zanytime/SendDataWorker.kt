package org.zanytek.zanytime

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.coroutines.suspendCoroutine

class SendDataWorker(val context: Context, userParameters: WorkerParameters) :
    CoroutineWorker(context, userParameters) {

    private fun getDataPackage(): ByteArray {
        val unixEpochSecond = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC)
        val stepsToday = 12000
        Log.i("MainActivity", "Steps: $stepsToday")
        val byte1 = unixEpochSecond.toByte()
        val byte2 = (unixEpochSecond shr (8 * 1)).toByte()
        val byte3 = (unixEpochSecond shr (8 * 2)).toByte()
        val byte4 = (unixEpochSecond shr (8 * 3)).toByte()
        val byte5 = stepsToday.toByte()
        val byte6 = (stepsToday shr (8 * 1)).toByte()
        val byte7 = (stepsToday shr (8 * 2)).toByte()
        return byteArrayOf(byte1, byte2, byte3, byte4, byte5, byte6, byte7)
    }

    override suspend fun doWork(): Result {
        Log.i("MainActivity", "Entering doWork")
        val serviceConn = connectService()
        serviceConn.writeDataToWatch(getDataPackage())
        Log.i("MainActivity", "Connected to service")
        return Result.success()
    }

    suspend fun connectService(): GattService.DataPlane {
        return suspendCoroutine { cont ->
            val latestGattServiceConn = object : ServiceConnection {
                override fun onServiceDisconnected(name: ComponentName?) {
                }

                override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                    val gattServiceData = service as GattService.DataPlane
                    Log.i("MainActivity", "Service Connected")
                    cont.resumeWith(kotlin.Result.success(gattServiceData))
                }
            }

            if (context.bindService(
                    Intent(GattService.DATA_PLANE_ACTION, null, context, GattService::class.java),
                    latestGattServiceConn,
                    0
                )
            ) {
                Log.i("MainActivity", "Service Bound")
            } else {
                cont.resumeWith(kotlin.Result.failure(Throwable("Could not bind to service")))
            }
        }
    }
}