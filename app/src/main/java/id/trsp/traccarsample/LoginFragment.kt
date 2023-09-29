package id.trsp.traccarsample

import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.os.Bundle
import android.preference.PreferenceManager
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import retrofit2.Retrofit

class LoginFragment : Fragment() {
    private var emailInput: TextView? = null
    private var passwordInput: TextView? = null
    private var loginButton: View? = null
    private val textWatcher: TextWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: Editable) {
            loginButton!!.isEnabled =
                emailInput!!.text.length > 0 && passwordInput!!.text.length > 0
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_login, container, false)
        emailInput = view.findViewById<View>(R.id.input_email) as TextView
        passwordInput = view.findViewById<View>(R.id.input_password) as TextView
        loginButton = view.findViewById(R.id.button_login)
        emailInput!!.addTextChangedListener(textWatcher)
        passwordInput!!.addTextChangedListener(textWatcher)
        val preferences = requireContext().getSharedPreferences("app_pref", MODE_PRIVATE)
        emailInput!!.text = preferences.getString(MainApplication.PREFERENCE_EMAIL, null)
        if (preferences.getBoolean(MainApplication.PREFERENCE_AUTHENTICATED, false)) {
            login()
        }
        view.findViewById<View>(R.id.button_settings).setOnClickListener {
            val dialogView = inflater.inflate(R.layout.view_settings, null)
            val input = dialogView.findViewById<View>(R.id.input_url) as EditText
            input.setText(preferences.getString(MainApplication.PREFERENCE_URL, null))
            AlertDialog.Builder(context)
                .setTitle(R.string.settings_title)
                .setView(dialogView)
                .setPositiveButton(
                    android.R.string.yes
                ) { dialog, which ->
                    val url = input.text.toString()
                    if (url.toHttpUrlOrNull() != null) {
                        preferences.edit().putString(
                            MainApplication.PREFERENCE_URL, url
                        ).apply()
                    } else {
                        Toast.makeText(context, R.string.error_invalid_url, Toast.LENGTH_LONG)
                            .show()
                    }
                }
                .setNegativeButton(android.R.string.no, null)
                .show()
        }
        loginButton!!.setOnClickListener(View.OnClickListener {
            preferences
                .edit()
                .putBoolean(MainApplication.PREFERENCE_AUTHENTICATED, true)
                .putString(MainApplication.PREFERENCE_EMAIL, emailInput!!.text.toString())
                .putString(MainApplication.PREFERENCE_PASSWORD, passwordInput!!.text.toString())
                .apply()
            login()
        })
        return view
    }

    private fun login() {
        val progress = ProgressDialog(context)
        progress.setMessage(getString(R.string.app_loading))
        progress.setCancelable(false)
        progress.show()
        val application = activity?.application as MainApplication
        application.getServiceAsync(object : MainApplication.GetServiceCallback {
            override fun onServiceReady(client: OkHttpClient?, retrofit: Retrofit?, service: WebService?) {
                if (progress.isShowing) {
                    progress.dismiss()
                }
                activity?.finish()
                startActivity(Intent(context, MainActivity::class.java))
            }

            override fun onFailure(): Boolean {
                if (progress.isShowing) {
                    progress.dismiss()
                }
                return false
            }
        })
    }
}
