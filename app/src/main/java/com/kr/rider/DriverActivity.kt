package com.kr.rider

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.kr.rider.fragments.DriverHomeFragment

class DriverActivity : AppCompatActivity() {

    private lateinit var bottomNav: BottomNavigationView
    private val containerId = R.id.driver_fragment_container

    private val homeFragment = DriverHomeFragment()
    private var currentFragment: Fragment = homeFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_driver)

        // Toolbar
        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = "🚗 Driver"

        bottomNav = findViewById(R.id.driver_bottom_navigation)

        // ✅ Load default fragment
        if (savedInstanceState == null) {
            loadFragment(homeFragment, "HOME")
        }

        // ✅ Bottom Navigation Click Listener
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.driver_home -> {
                    loadFragment(homeFragment, "HOME")
                    true
                }
                R.id.driver_pending_rides -> {
                    // ⏳ Coming soon
                    showComingSoonToast()
                    true
                }
                R.id.driver_rides -> {
                    // ⏳ Coming soon
                    showComingSoonToast()
                    true
                }
                R.id.driver_wallet -> {
                    // ⏳ Coming soon
                    showComingSoonToast()
                    true
                }
                R.id.driver_profile -> {
                    // ⏳ Coming soon
                    showComingSoonToast()
                    true
                }
                else -> false
            }
        }
    }

    private fun loadFragment(fragment: Fragment, tag: String) {
        if (currentFragment.javaClass == fragment.javaClass && currentFragment.isAdded) {
            return
        }

        currentFragment = fragment
        supportActionBar?.title = when (tag) {
            "HOME" -> "🚗 Driver"
            else -> "Driver"
        }

        supportFragmentManager.beginTransaction()
            .replace(containerId, fragment, tag)
            .commit()
    }

    private fun showComingSoonToast() {
        android.widget.Toast.makeText(
            this,
            "⏳ Coming Soon!",
            android.widget.Toast.LENGTH_SHORT
        ).show()
    }
}