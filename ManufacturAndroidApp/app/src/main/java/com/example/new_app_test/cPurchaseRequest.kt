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
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.floatingactionbutton.FloatingActionButton
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET

data class ItemData(
    val requestID: String,
    val date: String,
    val department: String,
    val period: Int,
    val type: String,
    val user: String
)

interface OnItemClickListener {
    fun onItemClick(item: ItemData)
}

interface FetchAuthApiService{
    @GET("/get_data")
    fun fetchData(): Call<List<ItemData>>

}

class ItemAdapter(private val itemList: List<ItemData>,
                  private val listener: OnItemClickListener) : RecyclerView.Adapter<ItemAdapter.ItemViewHolder>() {

    class ItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val textRequestId: TextView = itemView.findViewById(R.id.page5_itemID)
        val textPeriod: TextView = itemView.findViewById(R.id.page5_itemName)
        val textType: TextView = itemView.findViewById(R.id.page5_type)
        val textUsername: TextView = itemView.findViewById(R.id.page5_measuringUnit)
        val textDepartment: TextView = itemView.findViewById(R.id.page5_price)
        val textDate: TextView = itemView.findViewById(R.id.page5_unit)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.recycler_item, parent, false)
        return ItemViewHolder(view)
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
        val item = itemList[position]
        holder.textRequestId.text = item.requestID
        holder.textPeriod.text = item.period.toString()
        holder.textType.text = item.type
        holder.textUsername.text = item.user
        holder.textDepartment.text = item.department
        holder.textDate.text = item.date

        holder.itemView.setOnClickListener {
            listener.onItemClick(item)
        }
    }
    override fun getItemCount(): Int = itemList.size
}

class RetroClient(baseUrl: String) {
    private val retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    fun <T> createService(serviceClass: Class<T>): T {
        return retrofit.create(serviceClass)
    }
}

class PurchasePage : AppCompatActivity(), OnItemClickListener {
    private lateinit var retroClient: RetroClient
    private lateinit var fetchApiService: FetchAuthApiService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.c_purchase_layout)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        retroClient = RetroClient("http://192.168.0.18:5000")
        fetchApiService = retroClient.createService(FetchAuthApiService::class.java)

        val recyclerView = findViewById<RecyclerView>(R.id.page3_recyclerItem)
        recyclerView.layoutManager = LinearLayoutManager(this)

        fetchApiService.fetchData().enqueue(fetchFromAPI(recyclerView))


        val returnToMainButton = findViewById<FloatingActionButton>(R.id.page3_returnButton)

        returnToMainButton.setOnClickListener {
            navigateToMain()
        }
        val addNewButton = findViewById<FloatingActionButton>(R.id.page3_fabAddItem)

        addNewButton.setOnClickListener{
            navigateToInsertPage()
        }
    }

    private fun fetchFromAPI(recyclerView: RecyclerView): Callback<List<ItemData>> {
        return object : Callback<List<ItemData>> {
            override fun onResponse(
                call: Call<List<ItemData>>,
                response: Response<List<ItemData>>
            ) {
                if (response.isSuccessful) {
                    val itemList = response.body()!!
                    recyclerView.adapter = ItemAdapter(itemList, this@PurchasePage)
                } else {
                    Log.e("MainActivity3", "Failed to fetch data: ${response.code()}")
                    Toast.makeText(this@PurchasePage, "Failed to fetch data.", Toast.LENGTH_SHORT)
                        .show()
                }
            }
            override fun onFailure(call: Call<List<ItemData>>, t: Throwable) {
                Log.e("MainActivity3", "Network error: $t")
                Toast.makeText(this@PurchasePage, "Network error.", Toast.LENGTH_SHORT).show()
            }
        }
    }
    override fun onItemClick(item: ItemData) {
        Toast.makeText(this, "Clicked: ${item.user}", Toast.LENGTH_SHORT).show()
    }

    private fun navigateToMain() {
        startActivity(Intent(this, MainPage::class.java))
        finish()
    }

    private fun navigateToInsertPage() {
        startActivity(Intent(this, EntryPurchase::class.java))
        finish()
    }
}