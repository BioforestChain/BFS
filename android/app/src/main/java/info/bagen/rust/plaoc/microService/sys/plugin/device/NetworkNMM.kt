package info.bagen.rust.plaoc.microService.sys.plugin.device

import android.content.Context
import android.net.ConnectivityManager
import android.net.ConnectivityManager.NetworkCallback
import android.net.NetworkCapabilities
import info.bagen.rust.plaoc.util.JsonUtil
import info.bagen.rust.plaoc.App
import info.bagen.rust.plaoc.microService.core.BootstrapContext
import info.bagen.rust.plaoc.microService.core.NativeMicroModule
import info.bagen.rust.plaoc.microService.sys.plugin.device.model.NetWorkInfo
import org.http4k.core.Method
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.routing.bind
import org.http4k.routing.routes


class NetworkNMM():NativeMicroModule("network.sys.dweb") {
    private val mStatusChangeListener: ArrayList<NetworkStatusChangeListener> = arrayListOf()

    private var mConnectivityCallback: ConnectivityCallback? = null
    private var mConnectivityManager: ConnectivityManager? = null

    // private var mReceiver: BroadcastReceiver? = null
    private var mNetworkStatus: NetworkStatus? = null

    val networkInfo = NetWorkInfo()

    override suspend fun _bootstrap(bootstrapContext: BootstrapContext)
 {
        apiRouting = routes(
            /** 获取当前网络状态*/
            "/status" bind Method.GET to defineHandler { request ->
                val result = JsonUtil.toJson(getNetworkStatus())
                Response(Status.OK).body(result)
            },
            /** 获取当前网络信息*/
            "/info" bind Method.GET to defineHandler { request ->
                val result = networkInfo.getNetWorkInfo()
                Response(Status.OK).body(result)
            },
        )
    }

    companion object {
        val sInstance by lazy(mode = LazyThreadSafetyMode.SYNCHRONIZED) {
            NetworkNMM()
        }
    }


    init {
        mConnectivityManager =
            App.appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        mConnectivityCallback = ConnectivityCallback()
        mConnectivityManager!!.registerDefaultNetworkCallback(mConnectivityCallback!!)
        /* // 由于当前应用的版本 minSdkVersion > Build.VERSION_CODES.M, 所以不考虑低于 Build.VERSION_CODES.M
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.M) {
          mReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
              mStatusChangeListener?.onNetworkStatusChanged(false)
            }
          }
        } else {
          mConnectivityCallback = ConnectivityCallback()
        }*/
    }

    interface NetworkStatusChangeListener {
        fun onNetworkStatusChanged(wasLostEvent: Boolean)
    }

    class ConnectivityCallback : NetworkCallback() {
        override fun onLost(network: android.net.Network) {
            super.onLost(network)
            sInstance.mStatusChangeListener.forEach { listener ->
                try {
                    listener.onNetworkStatusChanged(true)
                } catch (_: Throwable) {
                }
            }
        }

        override fun onCapabilitiesChanged(
            network: android.net.Network, networkCapabilities: NetworkCapabilities
        ) {
            super.onCapabilitiesChanged(network, networkCapabilities)
            sInstance.mStatusChangeListener.forEach { listener ->
                try {
                    listener.onNetworkStatusChanged(false)
                } catch (_: Throwable) {
                }
            }
        }
    }

    fun addStatusChangeListener(listener: NetworkStatusChangeListener) {
        mStatusChangeListener.add(listener)
    }

    fun removeStatusChangeListener(listener: NetworkStatusChangeListener) {
        mStatusChangeListener.remove(listener)
    }

    fun removeAllListeners() {
        mStatusChangeListener.clear()
    }
    fun getNetworkStatus(): NetworkStatus {
        return mNetworkStatus ?: getNetworkInfo()
    }
    private fun getNetworkInfo(): NetworkStatus {
        val networkStatus = NetworkStatus()
        mConnectivityManager?.let { connectivityManager ->
            val activeNetwork = connectivityManager.activeNetwork
            val capabilities =
                connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
            if (activeNetwork != null && capabilities != null) {
                networkStatus.connected =
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) &&
                            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    networkStatus.connectionType = ConnectionType.WIFI
                } else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                    networkStatus.connectionType = ConnectionType.CELLULAR
                } else {
                    networkStatus.connectionType = ConnectionType.UNKNOWN
                }
            }
        }
        return networkStatus
    }

    @SuppressWarnings("deprecated")
    private fun getNetworkInfoBeforeN(): NetworkStatus {
        val networkStatus = NetworkStatus()
        mConnectivityManager?.activeNetworkInfo?.let { networkInfo ->
            networkStatus.connected = networkInfo.isConnected
            val typeName = networkInfo.typeName
            if (typeName == "WIFI") {
                networkStatus.connectionType = ConnectionType.WIFI
            } else if (typeName == "MOBILE") {
                networkStatus.connectionType = ConnectionType.CELLULAR
            }
        }
        return networkStatus
    }


    override suspend fun _shutdown() {
        TODO("Not yet implemented")
    }

    /*fun startMonitoring() {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        mConnectivityManager!!.registerDefaultNetworkCallback(mConnectivityCallback!!)
      } else {
        val filter = IntentFilter("android.net.conn.CONNECTIVITY_CHANGE")
        App.appContext.registerReceiver(mReceiver, filter)
      }
    }

    fun stopMonitoring() {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        mConnectivityManager!!.unregisterNetworkCallback(mConnectivityCallback!!)
      } else {
        App.appContext.unregisterReceiver(mReceiver)
      }
    }*/
}

data class NetworkStatus(
    var connected: Boolean = false,
    var connectionType: ConnectionType = ConnectionType.NONE
)

enum class ConnectionType(type: String) {
    WIFI("wifi"), CELLULAR("cellular"), NONE("none"), UNKNOWN("unknown")
}
