package org.zanytek.zanytime

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.icu.text.SimpleDateFormat
import android.os.*
import android.util.Log
import com.google.android.material.snackbar.Snackbar
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import kotlinx.coroutines.*
import org.zanytek.zanytime.databinding.ActivityMain2Binding
import java.util.*
import kotlin.system.measureTimeMillis

private const val ENABLE_BLUETOOTH_REQUEST_CODE = 1


class MainActivity2 : AppCompatActivity() {

    private lateinit var bluetoothGatt: BluetoothGatt
    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMain2Binding
    private val scope = CoroutineScope(newSingleThreadContext("name"))
    private var serviceConn: MainActivity2.ZanytimeServiceConn? = null

    private val bluetoothAdapter: BluetoothAdapter by lazy {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    override fun onResume() {
        super.onResume()
        if (!bluetoothAdapter.isEnabled) {
            promptEnableBluetooth()
        }
    }

    private fun promptEnableBluetooth() {
        if (!bluetoothAdapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, ENABLE_BLUETOOTH_REQUEST_CODE)
        }
    }

    var device: BluetoothDevice? = null
    var gatt: BluetoothGatt? = null

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMain2Binding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        val navController = findNavController(R.id.nav_host_fragment_content_main)
        appBarConfiguration = AppBarConfiguration(navController.graph)
        setupActionBarWithNavController(navController, appBarConfiguration)

        binding.fab.setOnClickListener { view ->
            if(getServiceState(this) == ServiceState.STOPPED)
                actionOnService(Actions.START)
            else
                actionOnService(Actions.STOP)
        }
    }

//    override fun onStart() {
//        super.onStart()
//        bindService(Intent(this, GattService::class.java), null)
//    }

    override fun onStart() {
        super.onStart()

        val latestServiceConn = MainActivity2.ZanytimeServiceConn()
        if (bindService(Intent(this, EndlessService::class.java), latestServiceConn, 0)) {
            serviceConn = latestServiceConn
        }
    }

    private fun actionOnService(action: Actions) {
        if (getServiceState(this) == ServiceState.STOPPED && action == Actions.STOP) return
        Intent(this, EndlessService::class.java).also {
            it.action = action.name
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Log.d("MainActivity2","Starting the service in >=26 Mode")
                startForegroundService(it)
                return
            }
            Log.d("MainActivity2","Starting the service in < 26 Mode")
            startService(it)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration)
                || super.onSupportNavigateUp()
    }

    private class ZanytimeServiceConn : ServiceConnection {
        var binding: DeviceAPI? = null

        override fun onServiceDisconnected(name: ComponentName?) {
            binding = null
        }

        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            binding = service as? DeviceAPI
        }
    }
}