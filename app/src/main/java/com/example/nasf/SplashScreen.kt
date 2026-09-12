package com.example.nasf

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle

class SplashScreen : AppCompatActivity() {

    private val splash = 3000L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash_screen)

        android.os.Handler().postDelayed({
            // Start your main activity here
            val intent = Intent(this, Login::class.java)
            startActivity(intent)
            finish()
        },splash)
    }
}