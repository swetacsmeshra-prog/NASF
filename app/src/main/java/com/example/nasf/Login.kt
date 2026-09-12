package com.example.nasf

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import com.vishnusivadas.advanced_httpurlconnection.PutData

class Login : AppCompatActivity() {

    private lateinit var progressBar: ProgressBar
    private lateinit var login_username: EditText
    private lateinit var login_password: EditText
    private lateinit var text_signup: TextView
    private lateinit var login: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)


        login_username = findViewById(R.id.login_username)
        login_password = findViewById(R.id.login_password)
        progressBar = findViewById(R.id.progressBar)
        text_signup = findViewById(R.id.signupText)
        login = findViewById(R.id.login_btn)

        text_signup.setOnClickListener {
            val intent = Intent(this, Signup::class.java)
            startActivity(intent)
            finish()
            Toast.makeText(applicationContext, "Sign-up Screen", Toast.LENGTH_SHORT).show()
        }

        login.setOnClickListener {

            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            //finish()
            Toast.makeText(applicationContext, "Sign-up Screen", Toast.LENGTH_SHORT).show()

            val username = login_username.text.toString()
            val password = login_password.text.toString()

            if (username != "" && password != "") {

                //Start ProgressBar first (Set visibility VISIBLE)
                progressBar.visibility = ProgressBar.VISIBLE

                val handler = Handler(Looper.getMainLooper())
                handler.post(Runnable {
                    //Starting Write and Read data with URL
                    //Creating array for parameters

                    val field = arrayOfNulls<String>(2)
                    field[0] = "username"
                    field[1] = "password"

                    //Creating array for data
                    val data = arrayOfNulls<String>(2)
                    data[0] = username
                    data[1] = password

                    val putData = PutData("http://172.17.30.66/loginapi/login.php", "POST", field, data)

                    if (putData.startPut()) {
                        if (putData.onComplete()) {
                            progressBar.visibility = ProgressBar.GONE
                            val result = putData.result

                            if (result.equals("Login Success")) {
                                Toast.makeText(applicationContext, result, Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(applicationContext, result, Toast.LENGTH_SHORT).show()
                            }
                            //End ProgressBar (Set visibility to GONE)
                            Log.i("PutData", result)
                        }
                    }
                    //End Write and Read data with URL
                    val intent = Intent(applicationContext, MainActivity::class.java)
                    startActivity(intent)
                    finish()
                })
            } else {
                Toast.makeText(applicationContext, "All fields required", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
