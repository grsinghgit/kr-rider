package com.kr.rider

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.kr.rider.ui.LoginFragment

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // ✅ Load LoginFragment by default
        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.fragment_container, LoginFragment())
                .commit()
        }
    }
}