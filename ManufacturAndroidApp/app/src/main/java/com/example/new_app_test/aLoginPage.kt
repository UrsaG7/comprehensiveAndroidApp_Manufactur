package com.example.new_app_test

import android.content.Intent
import android.os.Bundle
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.new_app_test.databinding.ALoginLayoutBinding
import kotlinx.coroutines.launch
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

data class CallRequest(
    val username: String,
    val password: String
)

data class CallResponse(
    val success: Boolean,
    val token: String?,
    val message: String
)

interface AuthApiService {
    @POST("api/auth/login")
    fun login(@Body loginRequest: CallRequest): Call<CallResponse>

    @POST("api/auth/register")
    fun register(@Body registerRequest: CallRequest): Call<CallResponse>

    @GET("/get_department")
    suspend fun getDepartment(): Response<List<String>>
}

class RetrofitClient(baseUrl: String) {
    private val retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    fun <T> createService(serviceClass: Class<T>): T {
        return retrofit.create(serviceClass)
    }
}

class LoginPage : AppCompatActivity() {
    private val retrofitClient by lazy { RetrofitClient("http://192.168.0.18:5000") }
    private val apiService by lazy { retrofitClient.createService(AuthApiService::class.java) }
    private var userField : String = null.toString()
    private var passField : String = null.toString()
    private var selectedDepartment : String = null.toString()

    private lateinit var binding : ALoginLayoutBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ALoginLayoutBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupWindowInsets()
        setupSpinners()
        setupSubmitButton()
        setupRegisterButton()
    }

    private fun setupWindowInsets(){
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun setupInput(){
        userField = binding.inputUser.text.toString()
        passField = binding.inputPass.text.toString()
    }

    private fun setupSubmitButton(){
        binding.buttonSubmit.setOnClickListener {
            setupInput()
            performLogin(userField, passField)
        }
    }

    private fun setupRegisterButton(){
        binding.buttonRegister.setOnClickListener {
            setupInput()
            performRegister(userField, passField)
        }
    }
    private fun setupSpinners(){
        prepareSpinners(binding.page1SpinnersDepartment)
    }

    private fun prepareSpinners(spinner: Spinner) {
        loadMaterialTypes(spinner)
        spinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    val selectedItem = parent.getItemAtPosition(position).toString()

                    selectedDepartment = selectedItem

                }
                override fun onNothingSelected(parent: AdapterView<*>) {}
            }
    }

    private fun loadMaterialTypes(spinner: Spinner) {
        lifecycleScope.launch {
            try {
                val materials = getMaterials()
                updateSpinnerWithMaterials(materials, spinner)
            } catch (e: Exception) {
                handleError("Error loading materials", e)
            }
        }
    }

    private suspend fun getMaterials(): List<String> {
        val action = apiService.getDepartment()

        return if (action.isSuccessful) {
            action.body() ?: emptyList()

        } else {
            throw RuntimeException("Failed to load departments: ${action.code()}")
        }
    }

    private fun updateSpinnerWithMaterials(materials: List<String>, spinner: Spinner) {
        Log.d("Materials", "Materials received: $materials")
        ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            materials
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinner.adapter = this
        }
    }

    private fun performLogin(username: String, password: String) {
        lifecycleScope.launch {
            val loginRequest = CallRequest(username, password)
            val act = "Login"
            apiService.login(loginRequest).enqueue(commonCallBack(act))
        }
    }
    private fun performRegister(username: String, password: String) {
        lifecycleScope.launch {
            val registerRequest = CallRequest(username, password)
            val act = "Register"
            apiService.register(registerRequest).enqueue(commonCallBack(act))
        }
    }

    private fun commonCallBack(act:String): Callback<CallResponse>{
        return object : Callback<CallResponse>{
            override fun onResponse(
                call: Call<CallResponse>,
                response: Response<CallResponse>
            ) {
                if (response.isSuccessful && response.body()?.success == true && act=="Login") {
                    saveAuthToken(response.body()?.token)
                    navigateToMainActivity()
                } else if(response.isSuccessful && response.body()?.success == true && act=="Register"){
                    val message = response.body()?.message
                    if (message != null) {
                        showError(message)
                    }
                } else {
                    val errorMessage = try {
                        response.errorBody()?.string()?.let { errorJson ->
                            JSONObject(errorJson).getString("message")
                        }
                    } catch (e: Exception) {
                        "Login Failed"
                    }
                    if (errorMessage != null) {
                        showError(errorMessage)
                    }
                }
            }

            override fun onFailure(call: Call<CallResponse>, t: Throwable) {
                showNetworkError(t)
            }
        }
    }

    private fun saveAuthToken(token: String?) {
        val masterKey = MasterKey.Builder(this)
            .setKeyGenParameterSpec(
                KeyGenParameterSpec.Builder(
                    MasterKey.DEFAULT_MASTER_KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(MasterKey.DEFAULT_AES_GCM_MASTER_KEY_SIZE)
                    .build()
            )
            .build()

        val sharedPreferences = EncryptedSharedPreferences.create(
            this,
            "auth_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

        token?.let {
            sharedPreferences.edit().putString("auth_token", it).apply()
        }
    }

    private fun navigateToMainActivity() {
        startActivity(Intent(this, MainPage::class.java))
        finish()
    }

    private fun handleError(message: String, error: Exception) {
        Log.e("MaterialActivity", message, error)
        showError("$message: ${error.localizedMessage}")
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun showNetworkError(t: Throwable) {
        Toast.makeText(this, "Network Error: ${t.localizedMessage}", Toast.LENGTH_SHORT).show()
    }
}

