/*
 * Copyright © 2020 NHSX. All rights reserved.
 */

package uk.nhs.nhsx.sonar.android.app

import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import io.reactivex.disposables.Disposable
import uk.nhs.nhsx.sonar.android.app.ble.LocationProviderChangedReceiver
import uk.nhs.nhsx.sonar.android.app.debug.TesterActivity
import uk.nhs.nhsx.sonar.android.app.edgecases.ReAllowGrantLocationPermissionActivity
import uk.nhs.nhsx.sonar.android.app.edgecases.ReEnableBluetoothActivity
import uk.nhs.nhsx.sonar.android.app.edgecases.ReEnableLocationActivity
import uk.nhs.nhsx.sonar.android.app.util.LocationHelper
import uk.nhs.nhsx.sonar.android.app.util.ShakeListener
import uk.nhs.nhsx.sonar.android.app.util.isBluetoothEnabled
import javax.inject.Inject

abstract class BaseActivity : AppCompatActivity() {

    private var locationSubscription: Disposable? = null

    @Inject
    lateinit var locationHelper: LocationHelper

    private lateinit var shakeListener: ShakeListener
    private lateinit var locationProviderChangedReceiver: LocationProviderChangedReceiver

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appComponent.inject(this)

        shakeListener = ShakeListener(this) {
            TesterActivity.start(this@BaseActivity)
        }

        locationProviderChangedReceiver = LocationProviderChangedReceiver(locationHelper)
    }

    override fun onResume() {
        super.onResume()
        shakeListener.start()

        listenBluetoothChange()
        checkLocationPermission()
        listenLocationChange()
    }

    private fun listenBluetoothChange() {
        if (!isBluetoothEnabled()) {
            bluetoothHasBeenDisabled()
        }
        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        registerReceiver(bluetoothStateBroadcastReceiver, filter)
    }

    private fun checkLocationPermission() {
        if (!locationHelper.locationPermissionsGranted()) {
            ReAllowGrantLocationPermissionActivity.start(this)
        }
    }

    private fun listenLocationChange() {
        registerReceiver(
            locationProviderChangedReceiver,
            IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION)
        )
        locationProviderChangedReceiver.onCreate()
        locationSubscription =
            locationProviderChangedReceiver.getLocationStatus().subscribe { isLocationEnabled ->
                if (!isLocationEnabled) {
                    ReEnableLocationActivity.start(this)
                }
            }
    }

    override fun onPause() {
        super.onPause()
        shakeListener.stop()
        unregisterReceiver(bluetoothStateBroadcastReceiver)
        unregisterReceiver(locationProviderChangedReceiver)
        locationSubscription?.dispose()
    }

    private fun bluetoothHasBeenDisabled() {
        ReEnableBluetoothActivity.start(this)
    }

    private val bluetoothStateBroadcastReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                val state = intent.getIntExtra(
                    BluetoothAdapter.EXTRA_STATE,
                    BluetoothAdapter.ERROR
                )
                if (state == BluetoothAdapter.STATE_OFF) {
                    bluetoothHasBeenDisabled()
                }
            }
        }
    }
}
