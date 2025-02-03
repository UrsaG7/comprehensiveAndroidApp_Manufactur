package com.example.new_app_test

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.new_app_test.databinding.EMasterLayoutBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface FetchMasterData{
    @GET("/get_master")
    suspend fun getMaster(): Response<List<ResponseMasterItem>>

    @POST("/master_deleteRow")
    suspend fun deleteRow(@Body request: DeleteRowRequest): Response<DeleteResponse>
}

data class ResponseMasterItem(
    val itemID:String,
    val name:String,
    val type:String,
    val unit:String,
    @SerializedName("standard_amt") val standardAmt:Int,
    val price:Double,
    val currency:String,
    @SerializedName("measuring_unit") val measureUnit: String
)

data class DeleteRowRequest(
    val itemId: String
)

data class DeleteResponse(
    val success: Boolean,
    val message: String?,
    val error: String?
)

class MasterRetroClient(baseUrl: String) {
    private val retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    fun <T> masterCreateService(serviceClass: Class<T>): T {
        return retrofit.create(serviceClass)
    }
}

class MasterItemAdapter(private val itemList: List<ResponseMasterItem>,
                        private val onItemClickListener: OnItemClickListener?) : RecyclerView.Adapter<MasterItemAdapter.ItemViewHolder>() {

    interface OnItemClickListener{
        fun onItemLongClick(item:ResponseMasterItem)
    }
    class ItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val itemID: TextView = itemView.findViewById(R.id.page5_itemID)
        val itemName: TextView = itemView.findViewById(R.id.page5_itemName)
        val itemType: TextView = itemView.findViewById(R.id.page5_type)
        val standardAmt: TextView = itemView.findViewById(R.id.page5_standardAmt)
        val measuringUnit: TextView = itemView.findViewById(R.id.page5_measuringUnit)
        val itemPrice: TextView = itemView.findViewById(R.id.page5_price)
        val currency: TextView = itemView.findViewById(R.id.page5_currency)
        val itemUnit: TextView = itemView.findViewById(R.id.page5_unit)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.master_table_items, parent, false)
        return ItemViewHolder(view)
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
        val masterItem = itemList[position]
        holder.itemID.text = masterItem.itemID
        holder.itemName.text = masterItem.name
        holder.itemType.text = masterItem.type
        holder.itemUnit.text = masterItem.unit
        holder.standardAmt.text = masterItem.standardAmt.toString()
        holder.itemPrice.text = masterItem.price.toString()
        holder.currency.text = masterItem.currency
        holder.measuringUnit.text = masterItem.measureUnit

        holder.itemView.setOnLongClickListener {
            onItemClickListener?.onItemLongClick(masterItem)
            true
        }

    }
    override fun getItemCount(): Int = itemList.size
}

class MasterTable : AppCompatActivity() {
    private val retrofitClient by lazy { MasterRetroClient("http://192.168.0.18:5000") }
    private val fetchApiService by lazy { retrofitClient.masterCreateService(FetchMasterData::class.java) }
    private lateinit var recyclerView: RecyclerView
    private lateinit var itemAdapter: MasterItemAdapter

    private lateinit var binding: EMasterLayoutBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.e_master_layout)
    try {
        binding = EMasterLayoutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWindowInsets()
        setupRecycler()
        setupReturnButton()
        setupNewEntryButton()

        }catch (e:Exception){
            Log.e("Setup Failure", "Crash in onCreate", e)
        }
    }

    private fun setupWindowInsets(){
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun setupNewEntryButton(){
        binding.page5FabAddItem.setOnClickListener {
            navigateToMasterEntry()
        }
    }

    private fun setupReturnButton(){
        binding.page5ReturnButton.setOnClickListener{
            navigateToMain()
        }
    }

    private fun setupRecycler(){
        recyclerView = findViewById(R.id.page5_recyclerItem)
        recyclerView.layoutManager = LinearLayoutManager(this)

        fetchData()
    }

    private fun fetchData() {
        lifecycleScope.launch {
            try {
                val response = fetchApiService.getMaster()
                if (response.isSuccessful && response.body() != null) {
                    val itemList = response.body()!!
                    Log.d("Item Passing Through", "Fetching Success $itemList")
                    itemAdapter = MasterItemAdapter(itemList, object: MasterItemAdapter.OnItemClickListener{
                        override fun onItemLongClick(item: ResponseMasterItem) {
                            showItemOptions(item)
                        }
                    })
                    recyclerView.adapter = itemAdapter
                } else {
                    Log.e("ItemListActivity", "API Error: ${response.errorBody()?.string()}")
                }
            } catch (e: Exception) {
                Log.e("ItemListActivity", "Error: ${e.message}")
            }
        }
    }

    private fun showItemOptions(item: ResponseMasterItem) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Item Options")
            .setItems(arrayOf("Edit","View Details","Delete")) { _, which ->
                when (which) {
                    0 -> editItem(item)
                    1 -> showItemDetails(item)
                    2 -> deleteItem(item)
                }
            }
            .show()
    }

    private fun editItem(item: ResponseMasterItem){
        Log.d("testing data","$item.")
        val intent = Intent(this, EntryMaster::class.java).apply {
            putExtra("itemID", item.itemID)
            putExtra("name", item.name)
            putExtra("type", item.type)
            putExtra("unit", item.unit)
            putExtra("standardAmt", item.standardAmt)
            putExtra("price", item.price)
            putExtra("currency", item.currency)
            putExtra("measureUnit", item.measureUnit)
        }
        startActivity(intent)
    }

    private fun showItemDetails(item: ResponseMasterItem) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Item Details")
            .setMessage("""
            Item ID: ${item.itemID}
            Name: ${item.name}
            Type: ${item.type}
            Unit: ${item.unit}
            Standard Amount: ${item.standardAmt}
            Price: ${item.price}
            Currency: ${item.currency}
            Measuring Unit: ${item.measureUnit}
        """.trimIndent())
            .setPositiveButton("Close", null)
            .show()
    }

    private fun deleteItem(item: ResponseMasterItem){
        val itemId = DeleteRowRequest(item.itemID)
        lifecycleScope.launch {
            try {
                val deleteResponse=fetchApiService.deleteRow(itemId)
                Log.d("Delete Response", "Response Code: ${deleteResponse.code()}")
                if (deleteResponse.isSuccessful){
                    withContext(Dispatchers.Main) {
                        Log.d("Toast Check", "About to show toast")
                        Toast.makeText(this@MasterTable, "Deleted Successfully", Toast.LENGTH_LONG).show()
                    }
                    Log.d("Delete Results Feedback", "Results Feedback $deleteResponse")
                }else{
                    Log.e("ItemListActivity", "API Error: ${deleteResponse.errorBody()?.string()}")
                }
            }catch (e: Exception){
                Log.e("Delete Row Status", "$e")
            }
        }

    }

    private fun navigateToMain() {
        startActivity(Intent(this, MainPage::class.java))
        finish()
    }

    private fun navigateToMasterEntry() {
        startActivity(Intent(this,EntryMaster::class.java))
        finish()
    }
}