package com.kr.rider

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.kr.rider.ui.DriverLoginFragment

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // ✅ Check if already logged in
        val sharedPref = getSharedPreferences("driver_prefs", Context.MODE_PRIVATE)
        val isLoggedIn = sharedPref.getBoolean("isDriverLoggedIn", false)

        if (isLoggedIn) {
            // ✅ Already logged in - go to DriverActivity
            startActivity(Intent(this, DriverActivity::class.java))
            finish()
            return
        }

        // ✅ Load DriverLoginFragment
        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.fragment_container, DriverLoginFragment())
                .commit()
        }
    }
}