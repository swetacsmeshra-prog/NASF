package com.example.nasf.weather

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.widget.EditText
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import java.util.Locale

/**
 * Fills latitude / longitude from the device's current location when permission is granted.
 * Leaves the fields empty if permission is denied so the user can type values.
 */
class LocationAutofill(
    private val activity: AppCompatActivity,
    private val latField: EditText,
    private val lonField: EditText
) {
    private val permissionLauncher = activity.registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.any { it }) fetch()
    }

    fun start() {
        if (latField.text?.isNotBlank() == true && lonField.text?.isNotBlank() == true) return
        if (hasPermission()) fetch()
        else permissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    private fun hasPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_COARSE_LOCATION)
        return fine == PackageManager.PERMISSION_GRANTED ||
            coarse == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    private fun fetch() {
        val fused = LocationServices.getFusedLocationProviderClient(activity)
        fused.lastLocation
            .addOnSuccessListener { last ->
                if (last != null) {
                    apply(last)
                    return@addOnSuccessListener
                }
                fused.getCurrentLocation(
                    Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                    CancellationTokenSource().token
                ).addOnSuccessListener { current ->
                    if (current != null) apply(current) else fallbackGps()
                }.addOnFailureListener { fallbackGps() }
            }
            .addOnFailureListener { fallbackGps() }
    }

    @SuppressLint("MissingPermission")
    private fun fallbackGps() {
        val lm = activity.getSystemService(LocationManager::class.java) ?: return
        val loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            ?: lm.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)
        if (loc != null) apply(loc)
    }

    private fun apply(location: Location) {
        latField.setText(String.format(Locale.US, "%.6f", location.latitude))
        lonField.setText(String.format(Locale.US, "%.6f", location.longitude))
    }
}
