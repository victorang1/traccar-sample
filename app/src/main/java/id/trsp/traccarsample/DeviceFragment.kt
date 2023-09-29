package id.trsp.traccarsample

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ListView
import androidx.fragment.app.ListFragment
import id.trsp.traccarsample.model.Device
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit

class DevicesFragment : ListFragment() {
    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        val application = activity?.application as MainApplication
        application.getServiceAsync(object : MainApplication.GetServiceCallback {
            override fun onServiceReady(client: OkHttpClient?, retrofit: Retrofit?, service: WebService?) {
                service!!.getDevices().enqueue(object : WebServiceCallback<List<Device>>(
                    context
                ) {
                    override fun onSuccess(response: Response<List<Device>>) {
                        listAdapter = ArrayAdapter<Any?>(
                            requireContext(),
                            R.layout.list_item,
                            android.R.id.text1,
                            response.body()!!
                        )
                    }
                })
            }

            override fun onFailure(): Boolean {
                return false
            }
        })
    }

    override fun onListItemClick(l: ListView, v: View, position: Int, id: Long) {
        val activity: Activity? = activity
        if (activity != null) {
            val device: Device = listAdapter?.getItem(position) as Device
            activity.setResult(
                MainFragment.RESULT_SUCCESS,
                Intent().putExtra(EXTRA_DEVICE_ID, device.getId())
            )
            activity.finish()
        }
    }

    companion object {
        const val EXTRA_DEVICE_ID = "deviceId"
    }
}