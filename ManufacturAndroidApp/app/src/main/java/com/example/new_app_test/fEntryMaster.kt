package com.example.new_app_test

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
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
import com.example.new_app_test.databinding.FEntryMasterBinding
import com.google.gson.Gson
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface MaterialApi {
    @GET("/get_matType")
    suspend fun getMaterials(): Response<List<String>>

    @POST("/generate_itemID")
    suspend fun generateItemId(@Body request: GenerateItemRequest): Response<GenerateItemResponse>

    @POST("/update_itemID")
    suspend fun updateItemId(@Body request: GenerateItemRequest): Response<UpdateItemResponse>

    @GET("/get_master/get_unit")
    suspend fun getUnit(): Response<List<String>>

    @GET("/get_master/get_measure")
    suspend fun getMeasure(): Response<List<String>>

    @GET("/get_master/get_currency")
    suspend fun getCurrencies(): Response<List<String>>

    @POST("/entry_masterTable")
    suspend fun dataSubmission(@Body request: SubmitRequest): Response<SubmitResponse>

    @POST("/update_masterTable")
    suspend fun dataUpdate(@Body request: SubmitRequest): Response<SubmitResponse>
}

data class GenerateItemRequest(
    val typeID: String
)

data class GenerateItemResponse(
    val itemID: String,
    val counter: Int
)

data class UpdateItemResponse(
    val itemID: String,
    val newCount: Int
)

data class SubmitRequest(
    val itemId:String,
    val name:String,
    val itemType:String,
    val unit:String,
    val standardAmt:Int,
    val price:Double,
    val currency:String,
    val measureUnit:String
)

data class SubmitResponse(
    val success: Boolean,
    val message: String,
    val error: String
)

class EntryMaster : AppCompatActivity() {
    private val retrofitClient by lazy { MasterRetroClient("http://192.168.0.18:5000") }
    private val apiService by lazy { retrofitClient.masterCreateService(MaterialApi::class.java) }
    private var selectedType: String = null.toString()
    private var currentItemId: String = null.toString()
    private var selectedUnit: String = null.toString()
    private var selectedCurrency: String = null.toString()
    private var selectedMeasure: String = null.toString()
    private var isEditMode = false
    private var intentItemID : String = null.toString()
    private var intentType : String = null.toString()
    private var intentUnit : String = null.toString()
    private var intentMeasure : String = null.toString()
    private var intentCurrency : String = null.toString()


    private lateinit var binding: FEntryMasterBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = FEntryMasterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWindowInsets()
        setupReturnButton()

        checkIfEditing()
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    @SuppressLint("SetTextI18n")
    private fun checkIfEditing() {
        val extras = intent.extras

        if (extras != null && extras.containsKey("itemID")) {
            Log.d("Received Data Through Extras", "$extras")
            binding.page6InputItemName.setText(extras.getString("name", ""))
            binding.page6InputStandardAmt.setText(extras.getInt("standardAmt", 0).toString())
            binding.page6InputItemPrice.setText(extras.getDouble("price", 0.0).toString())

            binding.page6SpinnerItemType.isEnabled = false

            intentType = extras.getString("type", "")
            intentUnit = extras.getString("unit", "")
            intentMeasure = extras.getString("measureUnit", "")
            intentCurrency = extras.getString("currency","")

            isEditMode=true
            binding.page6SubmitButton.text = "Update Data"
            intentItemID = extras.getString("itemID")?: ""
            currentItemId = intentItemID
            updateItemIdDisplay(intentItemID)

            binding.page6SubmitButton.text = "Update Data"
            lifecycleScope.launch {
                delay(500)
                setSpinnerSelections()
            }
            Log.d("Intent Item ID", intentItemID)

        } else {
            isEditMode = false
            binding.page6SpinnerItemType.isEnabled = true
            binding.page6SubmitButton.text = "Submit Data"
        }

        setupSpinner()
        setupSubmitButton()
    }

    private fun setupSpinner() {
        prepareSpinner("itemType")
        prepareSpinner("unit")
        prepareSpinner("currency")
        prepareSpinner("measureUnit")

    }

    private fun prepareSpinner(act:String) {
        val selectedSpinnerID = when (act){
            "itemType"->binding.page6SpinnerItemType
            "unit"->binding.page6SpinnerOrderUnit
            "currency"->binding.page6SpinnerCurrency
            "measureUnit"->binding.page6SpinnerMeasureUnit
            else->null
        }
        selectedSpinnerID?.let { loadMaterialTypes(act,it) }
        selectedSpinnerID?.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                    val selectedItem = parent.getItemAtPosition(position).toString()

                    when (act) {
                        "itemType" -> {
                            selectedType = selectedItem
                            if (!isEditMode){
                            generateItemId(selectedType)
                                }
                        }
                        "unit" -> {
                            Log.d("Spinner Selection", "$act selected: $selectedItem")
                            selectedUnit = selectedItem
                        }
                        "currency" -> {
                            Log.d("Spinner Selection", "$act selected: $selectedItem")
                            selectedCurrency = selectedItem
                        }
                        "measureUnit" -> {
                            Log.d("Spinner Selection", "$act selected: $selectedItem")
                            selectedMeasure = selectedItem
                        }
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>) {}
            }
    }


    private fun setupReturnButton() {
        binding.page6ReturnButton.setOnClickListener {
            navigateToPr()
        }
    }

    private fun setupSubmitButton(){
        binding.page6SubmitButton.setOnClickListener {
            submitEntry(selectedType)
        }
    }

    private fun loadMaterialTypes(act:String, spinner: Spinner) {
        lifecycleScope.launch {
            try {
                val materials = getMaterials(act)
                updateSpinnerWithMaterials(materials, spinner)
            } catch (e: Exception) {
                handleError("Error loading materials", e)
            }
        }
    }

    private suspend fun getMaterials(act: String): List<String> {
        val action = when(act){
            "itemType"->apiService.getMaterials()
            "unit"->apiService.getUnit()
            "currency"->apiService.getCurrencies()
            "measureUnit"->apiService.getMeasure()
            else->null
        }
        if (action == null) {
            Log.e("getMaterials", "API call failed: Response is null for $act")
            return emptyList()
        }

        return if (action.isSuccessful) {
            action.body() ?: emptyList()
        } else {
            throw RuntimeException("Failed to load $act: ${action.code()}")
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

    private fun generateItemId(typeId: String) {
        lifecycleScope.launch {
            try {
                val result = generateItemIdFromApi(typeId)
                updateItemIdDisplay(result.itemID)
                currentItemId = result.itemID

            } catch (e: Exception) {
                handleError("Failed to generate ID", e)
            }
        }
    }

    private suspend fun generateItemIdFromApi(typeId: String): GenerateItemResponse {
        val request = GenerateItemRequest(typeId)
        val response = apiService.generateItemId(request)

        return when (response.code()) {
            200, 201 -> response.body() ?: throw RuntimeException("Empty response body")
            400 -> throw RuntimeException("Invalid material type")
            404 -> throw RuntimeException("Material type not found")
            else -> throw RuntimeException("Error generating ID: ${response.code()}")
        }
    }

    private fun setSpinnerSelections() {
        val spinnerItemType = binding.page6SpinnerItemType
        val spinnerOrderUnit = binding.page6SpinnerOrderUnit
        val spinnerCurrency = binding.page6SpinnerCurrency
        val spinnerMeasureUnit = binding.page6SpinnerMeasureUnit

        fun setSpinnerSelection(spinner: Spinner, selectedValue: String) {
            val adapter = spinner.adapter
            for (i in 0 until adapter.count) {
                if (adapter.getItem(i).toString().equals(selectedValue, ignoreCase = true)) {
                    spinner.setSelection(i)
                    break
                }
            }
        }

        setSpinnerSelection(spinnerItemType, intentType)
        setSpinnerSelection(spinnerOrderUnit, intentUnit)
        setSpinnerSelection(spinnerCurrency, intentCurrency)
        setSpinnerSelection(spinnerMeasureUnit, intentMeasure)
    }

    private fun submitEntry(typeId: String) {
        lifecycleScope.launch {
            try {
                val itemId = if (isEditMode) intentItemID else currentItemId
                val itemName = binding.page6InputItemName.text.toString()
                val standardAmount = binding.page6InputStandardAmt.text.toString()
                val itemPrice = binding.page6InputItemPrice.text.toString()
                val updateRequest = GenerateItemRequest(typeId)

                val submissionRequest = SubmitRequest(
                    itemId = itemId,
                    name = itemName,
                    itemType = if (isEditMode) intentType else selectedType,
                    unit = selectedUnit,
                    standardAmt = standardAmount.toInt(),
                    price = itemPrice.toDouble(),
                    currency = selectedCurrency,
                    measureUnit = selectedMeasure
                )


                Log.d("ID_DEBUG", "Full request object: $submissionRequest")

                val gson = Gson()
                val jsonString = gson.toJson(submissionRequest)
                Log.d("ID_DEBUG", "Raw JSON being sent: $jsonString")

                val submissionResponse = if(isEditMode){
                    updateItemIdDisplay(itemId)
                    apiService.dataUpdate(submissionRequest)
                }else{
                    apiService.dataSubmission(submissionRequest)
                }

                if (submissionResponse.isSuccessful) {
                    if (!isEditMode) {
                        val updateResponse = apiService.updateItemId(updateRequest)
                        if (!submissionResponse.isSuccessful && !updateResponse.isSuccessful) {
                            throw RuntimeException("Failed to update item ID")
                        }
                    }
                    Log.d(this.toString(), "Update Success")
                    showError("Entry added successfully")
                    navigateToPr()
                }
            }catch (e:Exception){
                handleError("Update error", e)
            }
        }
    }

    private fun updateItemIdDisplay(itemId: String) {
        binding.page6ItemID.text = itemId
        Log.d("MaterialActivity", "Generated ID: $itemId")
    }

    private fun handleError(message: String, error: Exception) {
        Log.e("MaterialActivity", message, error)
        showError("$message: ${error.localizedMessage}")
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun navigateToPr() {
        startActivity(Intent(this, MasterTable::class.java))
        finish()
    }
}