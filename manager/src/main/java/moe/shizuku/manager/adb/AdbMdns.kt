package moe.shizuku.manager.adb

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.lifecycle.Observer
import java.io.IOException
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket

@RequiresApi(Build.VERSION_CODES.R)
class AdbMdns(
    context: Context, private val serviceType: String,
    private val observer: Observer<Pair<String, Int>>
) {

    private var registered = false

    /**
     * Whether [NsdManager.discoverServices] has been called and not yet stopped.
     *
     * This is deliberately separate from [registered], which only flips once the platform's
     * `onDiscoveryStarted` callback arrives: a round that timed out or was cancelled before
     * that callback left the discovery registered with the system while dropping the only
     * reference to it, and every later round in that process then found nothing at all.
     */
    private var requested = false
    private var running = false
    private var serviceName: String? = null
    private val listener = DiscoveryListener(this)
    private val nsdManager: NsdManager = context.getSystemService(NsdManager::class.java)

    fun start() {
        if (running) return
        running = true
        // One discovery per process, whatever instance asks for it. The platform allows a
        // single registration per service type and client, so a round left behind by an
        // attempt that was cancelled or replaced silently refuses the next one with
        // FAILURE_ALREADY_ACTIVE (which the callbacks above only reported at verbose level),
        // and the attempt then waits out its whole deadline finding nothing. Each attempt
        // builds its own AdbMdns, so the previous one has to be stopped from here.
        synchronized(lock) {
            current?.let { if (it !== this) it.stop() }
            current = this
        }
        if (!registered && !requested) {
            requested = true
            nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener)
        }
    }

    fun stop() {
        if (!running) return
        running = false
        // Stopped whenever a discovery may be registered, not only once the start callback
        // has been seen: the platform refuses to stop a listener it never started, but it is
        // worse to leave one registered and unreachable, because that is what breaks the
        // next round in the same process.
        if (registered || requested) {
            try {
                nsdManager.stopServiceDiscovery(listener)
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "Discovery was not registered after all", e)
            }
        }
        requested = false
        synchronized(lock) {
            if (current === this) current = null
        }
    }

    private fun onDiscoveryStart() {
        registered = true
    }

    private fun onDiscoveryStop() {
        registered = false
        requested = false
    }

    private fun onDiscoveryFailed() {
        registered = false
        requested = false
    }

    private fun onServiceFound(info: NsdServiceInfo) {
        nsdManager.resolveService(info, ResolveListener(this))
    }

    private fun onServiceLost(info: NsdServiceInfo) {
        if (info.serviceName == serviceName) observer.onChanged("" to -1)
    }

    private fun onServiceResolved(resolvedService: NsdServiceInfo) {
        val host = resolvedService.host?.hostAddress ?: return
        if (running && NetworkInterface.getNetworkInterfaces()
                .asSequence()
                .any { networkInterface ->
                    networkInterface.inetAddresses
                        .asSequence()
                        .any { host == it.hostAddress }
                }
            && isPortAvailable(host, resolvedService.port)
        ) {
            serviceName = resolvedService.serviceName
            observer.onChanged(host to resolvedService.port)
        }
    }

    private fun isPortAvailable(host: String, port: Int) = try {
        ServerSocket().use {
            it.bind(InetSocketAddress(host, port), 1)
            false
        }
    } catch (e: IOException) {
        true
    }

    internal class DiscoveryListener(private val adbMdns: AdbMdns) : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(serviceType: String) {
            Log.v(TAG, "onDiscoveryStarted: $serviceType")

            adbMdns.onDiscoveryStart()
        }

        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
            Log.w(TAG, "onStartDiscoveryFailed: $serviceType, $errorCode")

            adbMdns.onDiscoveryFailed()
        }

        override fun onDiscoveryStopped(serviceType: String) {
            Log.v(TAG, "onDiscoveryStopped: $serviceType")

            adbMdns.onDiscoveryStop()
        }

        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
            Log.w(TAG, "onStopDiscoveryFailed: $serviceType, $errorCode")

            adbMdns.onDiscoveryFailed()
        }

        override fun onServiceFound(serviceInfo: NsdServiceInfo) {
            Log.v(TAG, "onServiceFound: ${serviceInfo.serviceName}")

            adbMdns.onServiceFound(serviceInfo)
        }

        override fun onServiceLost(serviceInfo: NsdServiceInfo) {
            Log.v(TAG, "onServiceLost: ${serviceInfo.serviceName}")

            adbMdns.onServiceLost(serviceInfo)
        }
    }

    internal class ResolveListener(private val adbMdns: AdbMdns) : NsdManager.ResolveListener {
        override fun onResolveFailed(nsdServiceInfo: NsdServiceInfo, i: Int) {}

        override fun onServiceResolved(nsdServiceInfo: NsdServiceInfo) {
            adbMdns.onServiceResolved(nsdServiceInfo)
        }

    }

    companion object {
        /** The discovery currently registered in this process, if any. */
        @Volatile
        private var current: AdbMdns? = null
        private val lock = Any()

        const val TLS_CONNECT = "_adb-tls-connect._tcp"
        const val TLS_PAIRING = "_adb-tls-pairing._tcp"
        const val TAG = "AdbMdns"
    }
}
