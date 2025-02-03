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
import com.example.new_app_test.databinding.DEntryPrLayoutBinding
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.launch
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface PurchaseApi {
    @GET("/get_matType")
    suspend fun getType(): Response<List<String>>

    @GET("/get_master/get_name")
    suspend fun getName(@Query("typeID") typeID:String): Response<List<String>>

    @GET("/get_master/get_itemInfo")
    suspend fun getItemInfo(@Query("typeID") typeID:String,
                            @Query("itemName") itemName:String): Response<List<ItemInformationRequest>>

    @POST("/entry_purchaseRequest")
    suspend fun sendPurcahse(@Body request: PurchaseEntryRequest): Response<PurchaseEntryResponse>
}

data class ItemInformationRequest(
    val itemID:String,
    val name:String,
    val itemType:String,
    val unit:String,
    @SerializedName("standard_amt") val standardAmt:Int,
    val price:Double,
    val currency:String,
    @SerializedName("measuring_unit") val measureUnit:String
)

data class PurchaseEntryRequest(
    val itemID :String,
    val department : String,
    val period : String,
    val userID: String,
    val lot : Int,
    val quantity : Int
)

data class PurchaseEntryResponse(
    val success: Boolean,
    val message: String,
    val error: String
)

class EntryPurchase : AppCompatActivity() {
    private val retrofitClient by lazy { MasterRetroClient("http://192.168.0.18:5000") }
    private val purchaseRequestApi by lazy { retrofitClient.masterCreateService(PurchaseApi::class.java) }
    private var selectedType : String = null.toString()
    private var selectedName : String = null.toString()
    private var numberLot: Int? = null
    private var numberQuantity: Int? = null
    private lateinit var getDataResponseList: Response<List<ItemInformationRequest>>

    private lateinit var binding : DEntryPrLayoutBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = DEntryPrLayoutBinding.inflate(layoutInflater)

        setContentView(binding.root)

        setupWindowInsets()
        setupReturnButton()
        setupSpinners()
        setupSubmitButton(getDataResponseList)

    }

    private fun setupWindowInsets(){
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun setupReturnButton(){
        binding.page4ReturnButton.setOnClickListener{
            navigateToPr()
        }
    }

    private fun setupSpinners(){
        prepareSpinners("itemType")
        prepareSpinners("name")
    }

    private fun prepareSpinners(act:String) {
        val selectedSpinnerID = when (act) {
            "itemType" -> binding.page4SpinnersType
            "name" -> binding.page4SpinnersName
            else -> null
        }
        selectedSpinnerID?.let { loadMaterialTypes(act, it) }
        selectedSpinnerID?.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    val selectedItem = parent.getItemAtPosition(position).toString()

                    when (act) {
                        "itemType" -> {
                            Log.d("Spinner Selection", "$act selected: $selectedItem")
                            selectedType = selectedItem
                            loadMaterialTypes("name", binding.page4SpinnersName)
                        }

                        "name" -> {
                            Log.d("Spinner Selection UNITTTTTTT", "$act selected: $selectedItem")
                            selectedName = selectedItem
                            setupItemInfo()

                        }

                    }
                }

                    override fun onNothingSelected(parent: AdapterView<*>) {}


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
            "itemType"->purchaseRequestApi.getType()
            "name"->purchaseRequestApi.getName(selectedType)
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

    private fun setupItemInfo(){
        lifecycleScope.launch {
            val itemResponse = purchaseRequestApi
                .getItemInfo(typeID = selectedType, itemName = selectedName)
            prefillText(itemResponse)
            getDataResponseList = itemResponse
        }
    }

    @SuppressLint("SetTextI18n")
    private fun prefillText(itemResponse: Response<List<ItemInformationRequest>>){
        val item = itemResponse.body()!!.first()
        Log.d("AT SETUP ITEM INFO", "$item")

        binding.page4Unit.text = item.unit
        binding.page4StandardAmt.text = item.standardAmt.toString()
        binding.page4Price.text = item.price.toString()
        binding.page4Currency.text = item.currency
        binding.page4Measure.text = item.measureUnit

    }

    private fun setupSubmitButton(response: Response<List<ItemInformationRequest>>){
        binding.page4SubmitButton.setOnClickListener {
            val lotAmount = binding.page4InputLot.text.toString()
            val quantityAmount = binding.page4InputQuantity.text.toString()
            val items = response.body()!!.first()

            numberLot = lotAmount.toInt()
            numberQuantity = quantityAmount.toInt()
            //val entrySubmission = PurchaseEntryRequest (
                // itemID = items.itemID,
               // department = ///////////////THIS IS NOT DONE
            //)





        }
    }

    private fun handleError(message: String, error: Exception) {
        Log.e("MaterialActivity", message, error)
        showError("$message: ${error.localizedMessage}")
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun navigateToPr(){
        startActivity(Intent(this, MainPage::class.java))
        finish()
    }
}