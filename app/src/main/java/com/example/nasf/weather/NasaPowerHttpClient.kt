package com.example.nasf.weather

import android.util.Log
import okhttp3.OkHttpClient
import java.security.KeyStore
import java.security.cert.CertPathValidator
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.PKIXParameters
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * Shared OkHttp for NASA POWER.
 *
 * Some Android devices/emulators fail HTTPS with:
 * `CertPathValidatorException: Response is unreliable: its validity interval is out-of-date`
 * (wrong clock, or stale OCSP). We still validate the cert chain against the system
 * trust store, but retry without OCSP revocation when that specific failure occurs.
 */
object NasaPowerHttpClient {

    private const val TAG = "NasaPowerHttp"

    val shared: OkHttpClient by lazy { build() }

    private fun build(): OkHttpClient {
        val defaultTm = systemTrustManager()
        val tm = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
                defaultTm.checkClientTrusted(chain, authType)
            }

            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                try {
                    defaultTm.checkServerTrusted(chain, authType)
                } catch (e: CertificateException) {
                    if (!isOcspValidityFailure(e)) throw e
                    Log.w(TAG, "OCSP/date SSL check failed; validating chain without revocation", e)
                    validateChainWithoutRevocation(chain)
                }
            }

            override fun getAcceptedIssuers(): Array<X509Certificate> = defaultTm.acceptedIssuers
        }

        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, arrayOf<TrustManager>(tm), null)

        return OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, tm)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private fun systemTrustManager(): X509TrustManager {
        val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        factory.init(null as KeyStore?)
        return factory.trustManagers.filterIsInstance<X509TrustManager>().first()
    }

    private fun isOcspValidityFailure(error: Throwable): Boolean {
        generateSequence(error) { it.cause }.forEach { t ->
            val msg = t.message.orEmpty()
            if (msg.contains("validity interval is out-of-date", ignoreCase = true) ||
                msg.contains("Could not determine revocation status", ignoreCase = true)
            ) {
                return true
            }
        }
        return false
    }

    private fun validateChainWithoutRevocation(chain: Array<X509Certificate>) {
        if (chain.isEmpty()) throw CertificateException("Empty server certificate chain")
        val factory = CertificateFactory.getInstance("X.509")
        val certPath = factory.generateCertPath(chain.toList())
        val params = PKIXParameters(systemTrustStore())
        params.isRevocationEnabled = false
        CertPathValidator.getInstance("PKIX").validate(certPath, params)
    }

    private fun systemTrustStore(): KeyStore {
        return try {
            KeyStore.getInstance("AndroidCAStore").also { it.load(null) }
        } catch (_: Exception) {
            // Desktop/unit-test fallback
            val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            tmf.init(null as KeyStore?)
            val anchors = KeyStore.getInstance(KeyStore.getDefaultType())
            anchors.load(null, null)
            tmf.trustManagers.filterIsInstance<X509TrustManager>().first()
                .acceptedIssuers.forEachIndexed { index, cert ->
                    anchors.setCertificateEntry("ca-$index", cert)
                }
            anchors
        }
    }
}
