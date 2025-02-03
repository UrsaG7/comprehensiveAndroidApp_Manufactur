package com.example.new_app_test

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainPage : AppCompatActivity() {
    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.b_main_layout)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        val returnButton = findViewById<Button>(R.id.page2_returnButton)
        val purchaseRequestButton = findViewById<ImageButton>(R.id.page2_imgButton)
        val updateTableButton = findViewById<ImageButton>(R.id.page2_tableUpdateButton)

        purchaseRequestButton.setOnClickListener{
            navigateToPR()
        }

        updateTableButton.setOnClickListener{
            navigateToUpdateTable()
        }
        returnButton.setOnClickListener{
            navigateToLogin()
        }
    }
    private fun navigateToPR(){
        startActivity(Intent(this, EntryPurchase::class.java))
        finish()
    }
    private fun navigateToLogin(){
        startActivity(Intent(this, LoginPage::class.java))
        finish()
    }
    private fun navigateToUpdateTable(){
        startActivity(Intent(this, MasterTable::class.java))
        finish()
    }
}