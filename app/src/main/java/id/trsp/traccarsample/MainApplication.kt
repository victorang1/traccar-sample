package id.trsp.traccarsample

import android.app.Application
import android.widget.Toast
import id.trsp.traccarsample.model.User
import okhttp3.JavaNetCookieJar
import okhttp3.OkHttpClient
import retrofit2.Call
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.jackson.JacksonConverterFactory
import java.net.CookieManager
import java.net.CookiePolicy
import java.util.LinkedList
import java.util.concurrent.TimeUnit

class MainApplication: Application() {

    companion object {
        val PREFERENCE_AUTHENTICATED = "authenticated"
        val PREFERENCE_URL = "url"
        val PREFERENCE_EMAIL = "email"
        val PREFERENCE_PASSWORD = "password"

        private val DEFAULT_SERVER = "http://demo.traccar.org" // local - http://10.0.2.2:8082
    }


    interface GetServiceCallback {
        fun onServiceReady(client: OkHttpClient?, retrofit: Retrofit?, service: WebService?)
        fun onFailure(): Boolean
    }

    private var client: OkHttpClient? = null
    private var service: WebService? = null
    private var retrofit: Retrofit? = null
    private var user: User? = null

    private val callbacks: MutableList<GetServiceCallback> = LinkedList()

    fun getServiceAsync(callback: GetServiceCallback) {
        if (service != null) {
            callback.onServiceReady(client, retrofit, service)
        } else {
            if (callbacks.isEmpty()) {
                initService()
            }
            callbacks.add(callback)
        }
    }

    fun getService(): WebService? {
        return service
    }

    fun getUser(): User? {
        return user
    }

    fun removeService() {
        service = null
        user = null
    }

    override fun onCreate() {
        super.onCreate()
        val preferences = getSharedPreferences("app_pref", MODE_PRIVATE)
        if (!preferences.contains(PREFERENCE_URL)) {
            preferences.edit().putString(PREFERENCE_URL, DEFAULT_SERVER).apply()
        }
    }

    private fun initService() {
        val preferences = getSharedPreferences("app_pref", MODE_PRIVATE)
        val url = preferences.getString(PREFERENCE_URL, "") ?: ""
        val email = preferences.getString(PREFERENCE_EMAIL, "") ?: ""
        val password = preferences.getString(PREFERENCE_PASSWORD, "") ?: ""
        val cookieManager = CookieManager()
        cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL)
        client = OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .cookieJar(JavaNetCookieJar(cookieManager)).build()
        try {
            retrofit = Retrofit.Builder()
                .client(client)
                .baseUrl(url)
                .addConverterFactory(JacksonConverterFactory.create())
                .build()
        } catch (e: IllegalArgumentException) {
            Toast.makeText(this, e.message, Toast.LENGTH_LONG).show()
            for (callback in callbacks) {
                callback.onFailure()
            }
            callbacks.clear()
        }
        val service: WebService = retrofit!!.create(WebService::class.java)
        service.addSession(email, password).enqueue(object : WebServiceCallback<User>(this) {
            override fun onSuccess(response: Response<User>) {
                this@MainApplication.service = service
                user = response.body()
                for (callback in callbacks) {
                    callback.onServiceReady(client, retrofit, service)
                }
                callbacks.clear()
            }

            override fun onFailure(call: Call<User>, t: Throwable?) {
                var handled = false
                for (callback in callbacks) {
                    handled = callback.onFailure()
                }
                callbacks.clear()
                if (!handled) {
                    super.onFailure(call, t)
                }
            }
        })
    }
}