package com.example.nasf.weather

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.UnknownHostException

object NetworkHelper {

    fun isOnline(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return false
        if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) return true
        // Some emulators report Wi‑Fi without VALIDATED until DNS is probed.
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    /**
     * Maps common network failures to actionable messages for farmers/testers.
     */
    fun friendlyMessage(throwable: Throwable): String {
        val root = generateSequence(throwable) { it.cause }.last()
        val msg = root.message.orEmpty()

        return when {
            root is UnknownHostException ||
                msg.contains("Unable to resolve host", ignoreCase = true) ||
                msg.contains("No address associated with hostname", ignoreCase = true) ->
                "Cannot reach NASA POWER (power.larc.nasa.gov). " +
                    "Check that the phone/emulator has internet (Wi‑Fi or mobile data). " +
                    "On emulator: Extended controls → Settings → disable Airplane mode, then cold boot. " +
                    "Corporate VPNs may block external sites while local ML servers still work."

            msg.contains("timeout", ignoreCase = true) ||
                msg.contains("timed out", ignoreCase = true) ->
                "NASA POWER request timed out. Check your connection and try again."

            msg.contains("validity interval is out-of-date", ignoreCase = true) ||
                msg.contains("Could not determine revocation status", ignoreCase = true) ->
                "Secure connection to NASA POWER failed (SSL date/OCSP). " +
                    "On phone/emulator: Settings → Date & time → turn ON Automatic date & time, " +
                    "then Retry. NASA API itself is usually fine — this is a device clock/SSL check issue."

            msg.contains("SSL", ignoreCase = true) ||
                msg.contains("certificate", ignoreCase = true) ||
                msg.contains("CertPathValidator", ignoreCase = true) ->
                "Secure connection to NASA POWER failed. Check device date/time (set Automatic) and network."

            else -> root.message ?: throwable.message ?: "Network error"
        }
    }
}
