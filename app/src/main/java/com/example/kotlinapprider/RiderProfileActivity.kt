package com.example.kotlinapprider

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import com.example.kotlinapprider.Common.Common
import com.example.kotlinapprider.Model.RiderInfoModel
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import kotlinx.android.synthetic.main.activity_rider_profile.*
import kotlinx.android.synthetic.main.activity_rider_profile.toolbar
import kotlinx.android.synthetic.main.app_bar_main.*

class RiderProfileActivity : AppCompatActivity() {
    private lateinit var userInfoRef: DatabaseReference
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_rider_profile)
        toolbar.setNavigationOnClickListener {
            startActivity(Intent(this,HomeActivity::class.java))
        }
        init()
    }

    private fun init() {

        val firstname = findViewById<TextInputEditText>(R.id.update_first_name)
        val lastName = findViewById<TextInputEditText>(R.id.update_last_name)
        val phoneNumber = findViewById<TextInputEditText>(R.id.update_phone_number)
        val address = findViewById<TextInputEditText>(R.id.update_address)
        val mailAddress = findViewById<TextInputEditText>(R.id.update_mail)
        val updateButton = findViewById<Button>(R.id.btn_update) as Button
        phoneNumber.setText(Common.currentRider!!.phoneNumber)
        lastName.setText(Common.currentRider!!.lastName)
        firstname.setText(Common.currentRider!!.firstName)
        address.setText(Common.currentRider!!.address)
        mailAddress.setText(Common.currentRider!!.mailAddress)

        updateButton.setOnClickListener {
            val model = RiderInfoModel()
            model.firstName = update_first_name.text.toString()
            model.lastName = update_last_name.text.toString()
            model.phoneNumber = update_phone_number.text.toString()
            model.address = update_address.text.toString()
            model.avatar = Common.currentRider!!.avatar
            model.mailAddress = mailAddress.text.toString()
            userInfoRef = FirebaseDatabase.getInstance().getReference(Common.RIDER_INFO_REFERENCE)
            userInfoRef.child(FirebaseAuth.getInstance().currentUser!!.uid).setValue(model)

            startActivity(Intent(this,HomeActivity::class.java))
        }
    }
}