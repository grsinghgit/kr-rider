package com.kr.rider

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.kr.rider.fragments.DriverHomeFragment
import com.kr.rider.fragments.DriverPendingRidesFragment

class DriverActivity : AppCompatActivity() {

    private lateinit var bottomNav: BottomNavigationView
    private val containerId = R.id.driver_fragment_container

    private val homeFragment = DriverHomeFragment()
    private val pendingRidesFragment = DriverPendingRidesFragment()  // ✅ New

    private var currentFragment: Fragment = homeFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_driver)

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = "🚗 Driver"

        bottomNav = findViewById(R.id.driver_bottom_navigation)

        if (savedInstanceState == null) {
            loadFragment(homeFragment, "HOME")
        }

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.driver_home -> {
                    loadFragment(homeFragment, "HOME")
                    true
                }
                R.id.driver_pending_rides -> {
                    loadFragment(pendingRidesFragment, "PENDING")  // ✅ New
                    true
                }
                R.id.driver_rides -> {
                    showComingSoonToast()
                    true
                }
                R.id.driver_wallet -> {
                    showComingSoonToast()
                    true
                }
                R.id.driver_profile -> {
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
            "PENDING" -> "📋 Pending Rides"
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