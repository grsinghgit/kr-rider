package com.kr.rider

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class DriverActivity : AppCompatActivity() {

    private lateinit var tvWelcome: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_driver)

        tvWelcome = findViewById(R.id.tvWelcome)

        val currentUser = FirebaseAuth.getInstance().currentUser
        val phone = currentUser?.phoneNumber ?: "Driver"

        tvWelcome.text = "🚗 Welcome, $phone!"
    }
}