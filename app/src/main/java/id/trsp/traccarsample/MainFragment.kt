package id.trsp.traccarsample

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.preference.PreferenceManager
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import id.trsp.traccarsample.model.Device
import id.trsp.traccarsample.model.Position
import id.trsp.traccarsample.model.Update
import id.trsp.traccarsample.model.User
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import retrofit2.Response
import retrofit2.Retrofit
import java.io.IOException
import java.text.SimpleDateFormat

class MainFragment : Fragment() {
    private var map: GoogleMap? = null
    private val handler = Handler()
    private val objectMapper = ObjectMapper()
    private val devices: MutableMap<Long, Device?> = mutableMapOf()
    private val positions: MutableMap<Long, Position> = mutableMapOf()
    private val markers: MutableMap<Long, Marker?> = mutableMapOf()
    private var webSocket: WebSocket? = null
    private val deviceRequestActivityLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_SUCCESS) {
            val deviceId = it.data!!.getLongExtra(DevicesFragment.EXTRA_DEVICE_ID, 0)
            val position: Position? = positions[deviceId]
            if (position != null) {
                map!!.moveCamera(
                    CameraUpdateFactory.newLatLng(
                        LatLng(position.getLatitude(), position.getLongitude())
                    )
                )
                markers[deviceId]!!.showInfoWindow()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_main, container, false)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.main, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_devices -> {
                val intent = Intent(context, DevicesActivity::class.java)
                deviceRequestActivityLauncher.launch(intent)
                return true
            }

            R.id.action_logout -> {
                requireContext().getSharedPreferences("app_pref", Context.MODE_PRIVATE)
                    .edit().putBoolean(MainApplication.PREFERENCE_AUTHENTICATED, false).apply()
                (activity?.application as MainApplication).removeService()
                activity?.finish()
                startActivity(Intent(context, LoginActivity::class.java))
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d("<RESULT", "onViewCreated: ")
        createWebSocket()
    }

//    override fun onMapReady(googleMap: GoogleMap) {
//        map = googleMap
//        map!!.setInfoWindowAdapter(object : InfoWindowAdapter {
//            override fun getInfoWindow(marker: Marker): View? {
//                return null
//            }
//
//            override fun getInfoContents(marker: Marker): View {
//                val view: View = LayoutInflater.from(context).inflate(R.layout.view_info, null)
//                (view.findViewById<View>(R.id.title) as TextView).text =
//                    marker.title
//                (view.findViewById<View>(R.id.details) as TextView).text =
//                    marker.snippet
//                return view
//            }
//        })
//        createWebSocket()
//    }

    private fun formatDetails(position: Position): String {
        val application = context?.applicationContext as MainApplication
        val user: User? = application.getUser()
        val dateFormat: SimpleDateFormat
        dateFormat = if (user!!.getTwelveHourFormat()) {
            SimpleDateFormat("yyyy-MM-dd hh:mm:ss a")
        } else {
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
        }
        var speedUnit = getString(R.string.user_kn)
        var factor = 1.0
        if (user.getSpeedUnit() != null) {
            when (user.getSpeedUnit()) {
                "kmh" -> {
                    speedUnit = getString(R.string.user_kmh)
                    factor = 1.852
                }

                "mph" -> {
                    speedUnit = getString(R.string.user_mph)
                    factor = 1.15078
                }

                else -> {
                    speedUnit = getString(R.string.user_kn)
                    factor = 1.0
                }
            }
        }
        val speed: Double = position.getSpeed() * factor
        return StringBuilder()
            .append(getString(R.string.position_time)).append(": ")
            .append(dateFormat.format(position.getFixTime())).append('\n')
            .append(getString(R.string.position_latitude)).append(": ")
            .append(java.lang.String.format("%.5f", position.getLatitude())).append('\n')
            .append(getString(R.string.position_longitude)).append(": ")
            .append(java.lang.String.format("%.5f", position.getLongitude())).append('\n')
            .append(getString(R.string.position_altitude)).append(": ")
            .append(java.lang.String.format("%.1f", position.getAltitude())).append('\n')
            .append(getString(R.string.position_speed)).append(": ")
            .append(String.format("%.1f", speed)).append(' ')
            .append(speedUnit).append('\n')
            .append(getString(R.string.position_course)).append(": ")
            .append(java.lang.String.format("%.1f", position.getCourse()))
            .toString()
    }

    @Throws(IOException::class)
    private fun handleMessage(message: String) {
        val update: Update = objectMapper.readValue(message, Update::class.java)
        if (update != null && update.positions != null) {
            for (position in update.positions) {
                val deviceId: Long = position.getDeviceId()
                if (devices.containsKey(deviceId)) {
                    val location = LatLng(position.getLatitude(), position.getLongitude())
                    var marker = markers[deviceId]
                    if (marker == null) {
                        marker = map!!.addMarker(
                            MarkerOptions()
                                .title(devices[deviceId]!!.getName()).position(location)
                        )
                        markers[deviceId] = marker
                    } else {
                        marker.setPosition(location)
                    }
                    marker!!.setSnippet(formatDetails(position))
                    positions[deviceId] = position
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (webSocket != null) {
            webSocket!!.cancel()
        }
    }

    private fun reconnectWebSocket() {
        handler.post {
            if (activity != null) {
                createWebSocket()
            }
        }
    }

    private fun createWebSocket() {
        val application = activity?.application as MainApplication
        application.getServiceAsync(object : MainApplication.GetServiceCallback {

            override fun onServiceReady(client: OkHttpClient?, retrofit: Retrofit?, service: WebService?) {
                val user: User? = application.getUser()
//                map!!.moveCamera(
//                    CameraUpdateFactory.newLatLngZoom(
//                        LatLng(user!!.latitude, user.longitude), user.zoom.toFloat()
//                    )
//                )
                service!!.getDevices().enqueue(object : WebServiceCallback<List<Device>>(context) {
                    override fun onSuccess(response: Response<List<Device>>) {
                        for (device in response.body()!!) {
                            if (device != null) {
                                devices[device.id] = device
                            }
                        }
                        val request = Request.Builder()
                            .url(retrofit!!.baseUrl().toUrl().toString() + "api/socket").build()
                        webSocket = OkHttpClient().newWebSocket(request, object : WebSocketListener() {
                            override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                                Log.d("<RESULT>", "onOpen: success")
                            }

                            override fun onFailure(
                                webSocket: WebSocket,
                                t: Throwable,
                                response: okhttp3.Response?
                            ) {
                                Log.d("<RESULT>", "onOpen: failed" + t.message)
                                reconnectWebSocket()
                            }

                            override fun onMessage(webSocket: WebSocket, text: String) {
                                Log.d("<RESULT>", "onOpen: onMessage")
                                handler.post {
                                    try {
                                        handleMessage(text)
                                    } catch (e: IOException) {
                                        Log.w(MainFragment::class.java.simpleName, e)
                                    }
                                }
                            }

                            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                                Log.d("<RESULT>", "onOpen: onClosed")
                                reconnectWebSocket()
                            }
                        })
                    }

                })
            }

            override fun onFailure(): Boolean {
                return false
            }
        })
    }

    companion object {
        const val REQUEST_DEVICE = 1
        const val RESULT_SUCCESS = 1

        fun newInstance(): MainFragment {
            val args = Bundle()

            val fragment = MainFragment()
            fragment.arguments = args
            return fragment
        }
    }
}