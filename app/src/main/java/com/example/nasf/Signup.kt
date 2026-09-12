package com.example.nasf

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import com.vishnusivadas.advanced_httpurlconnection.PutData

class Signup : AppCompatActivity() {

    private lateinit var editTextFullname: EditText
    private lateinit var editTextUsername: EditText
    private lateinit var edittextEmail: EditText
    private lateinit var editTextPassword: EditText

    private lateinit var signup: Button
    private lateinit var loginText: TextView
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup)

        editTextFullname = findViewById(R.id.fullname)
        editTextUsername = findViewById(R.id.username)
        edittextEmail = findViewById(R.id.email)
        editTextPassword = findViewById(R.id.password)

        signup = findViewById(R.id.signup_btn)
        loginText = findViewById(R.id.loginText)
        progressBar = findViewById(R.id.progressBar)

        loginText.setOnClickListener {

            val intent = Intent(this, Login::class.java)
            startActivity(intent)
            finish()
            Toast.makeText(applicationContext, "Login Screen", Toast.LENGTH_SHORT).show()
        }

        signup.setOnClickListener {

            val fullname = editTextFullname.text.toString()
            val username = editTextUsername.text.toString()
            val email = edittextEmail.text.toString()
            val password = editTextPassword.text.toString()

            if (fullname != "" && username != "" && email != "" && password != "") {

                progressBar.visibility = ProgressBar.VISIBLE
                //Start ProgressBar first (Set visibility VISIBLE)
                val handler = Handler(Looper.getMainLooper())
                handler.post(Runnable {
                    //Starting Write and Read data with URL
                    //Creating array for parameters

                    val field = arrayOfNulls<String>(4)
                    field[0] = "fullname"
                    field[1] = "username"
                    field[2] = "email"
                    field[3] = "password"

                    //Creating array for data
                    val data = arrayOfNulls<String>(4)
                    data[0] = fullname
                    data[1] = username
                    data[2] = email
                    data[3] = password

                    val putData = PutData("http://172.17.30.66/loginapi/signup.php", "POST", field, data)

                    if (putData.startPut()) {
                        if (putData.onComplete()) {
                            progressBar.visibility = ProgressBar.GONE
                            val result = putData.result

                            if (result.equals("Sign up Success")) {
                                Toast.makeText(applicationContext, result, Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(applicationContext, result, Toast.LENGTH_SHORT).show()
                            }
                            //End ProgressBar (Set visibility to GONE)
                            Log.i("PutData", result)
                        }
                    }
                    //End Write and Read data with URL
                    val intent = Intent(applicationContext, Login::class.java)
                    startActivity(intent)
                    //finish()
                })
            } else {
                Toast.makeText(applicationContext, "All fields required", Toast.LENGTH_SHORT).show()
            }
        }
    }
}