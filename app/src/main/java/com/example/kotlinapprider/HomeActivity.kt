package com.example.kotlinapprider

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.view.Menu
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.navigation.NavigationView
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import androidx.drawerlayout.widget.DrawerLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.bumptech.glide.Glide
import com.bumptech.glide.Glide.init
import com.example.kotlinapprider.Common.Common
import com.example.kotlinapprider.Model.RiderInfoModel
import com.example.kotlinapprider.Utils.UserUtils
import com.example.kotlinapprider.ui.home.HomeFragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference

class HomeActivity : AppCompatActivity() {
    private lateinit var navView: NavigationView
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navController: NavController

    private lateinit var img_avatar: ImageView
    private lateinit var waitingDialog: AlertDialog
    private lateinit var storageReference: StorageReference
    private var imageUri: Uri? = null

    private lateinit var appBarConfiguration: AppBarConfiguration

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        drawerLayout = findViewById(R.id.drawer_layout) as DrawerLayout
        navView = findViewById(R.id.nav_view) as NavigationView
        navController = findNavController(R.id.nav_host_fragment)

        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.nav_home
            ), drawerLayout
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)
        init()
    }

    private fun init() {
        // avatar
        storageReference = FirebaseStorage.getInstance().getReference()
        waitingDialog = AlertDialog.Builder(this)
            .setMessage("Waiting..")
            .setCancelable(false).create()

        navView.setNavigationItemSelectedListener { it ->
            if (it.itemId == R.id.nav_signout) {
                val builder = AlertDialog.Builder(this@HomeActivity)
                builder.setTitle("Sign out")
                    .setMessage("Are you sure?")
                    .setNegativeButton("Cancel") { dialog, id -> dialog.dismiss() }

                    .setPositiveButton("Sign out") { dialog, id ->

                        FirebaseAuth.getInstance().signOut()
                        val intent =
                            Intent(this@HomeActivity, OpeningScreenActivity::class.java)
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        startActivity(intent)
                        finish()

                    }.setCancelable(false)
                val dialog = builder.create()
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                        .setTextColor(
                            ContextCompat.getColor(
                                this@HomeActivity,
                                android.R.color.holo_red_dark
                            )
                        )
                    dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
                        .setTextColor(
                            ContextCompat.getColor(
                                this@HomeActivity,
                                R.color.colorAccent
                            )
                        )

                }
                dialog.show()
            }

             if(it.itemId== R.id.nav_profil){


                goToRiderProfileActivity(Common.currentRider)

               //  startActivity(Intent(this,RiderProfileActivity::class.java))
                 drawerLayout.closeDrawer(navView)
             }
             if(it.itemId== R.id.nav_home){


                 val homeFragment = HomeFragment()
                 supportFragmentManager.beginTransaction().apply {
                     replace(R.id.nav_host_fragment,homeFragment)
                     commit()
                 }
                 drawerLayout.closeDrawer(navView)
             }


            true

        }
        val headerView = navView.getHeaderView(0)
        val txt_name = headerView.findViewById<View>(R.id.txt_name) as TextView
        val txt_phone = headerView.findViewById<View>(R.id.txt_phone) as TextView
        img_avatar = headerView.findViewById<ImageView>(R.id.img_avatar) as ImageView
        txt_name.setText(Common.buildWelcomeMessage())
        txt_phone.setText(Common.currentRider!!.phoneNumber)

        Glide.with(getApplicationContext())
            .load(Common.currentRider!!.avatar)
            .into(img_avatar);

        if (Common.currentRider != null && Common.currentRider!!.avatar != null && TextUtils.isEmpty(Common.currentRider!!.avatar)) {
            Glide.with(getApplicationContext())
                .load(Common.currentRider!!.avatar)
                .into(img_avatar);
        }
        img_avatar.setOnClickListener {
            val intent = Intent()
            intent.setType("image/*")
            intent.setAction(Intent.ACTION_GET_CONTENT)
            startActivityForResult(
                Intent.createChooser(intent, "Select Picture"),
                PICK_IMAGE_REQUEST
            )
        }

    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == Activity.RESULT_OK) {
            if (data != null && data.data != null) {
                imageUri = data.data
                img_avatar.setImageURI(imageUri)
                showDialogUpload()
            }
        }
    }

    private fun showDialogUpload() {
        val builder = AlertDialog.Builder(this@HomeActivity)
        builder.setTitle("Change Profil Photo")
            .setMessage("Are you sure?")
            .setNegativeButton("Cancel") { dialog, id -> dialog.dismiss() }
            .setPositiveButton("Change") { dialog, id ->
                if (imageUri != null) {
                    waitingDialog.show()
                    val avatarFolder = storageReference.child("avatars/" + FirebaseAuth.getInstance().currentUser?.uid)
                    avatarFolder.putFile(imageUri!!)
                        .addOnFailureListener { e ->
                            Snackbar.make(drawerLayout, e.message!!, Snackbar.LENGTH_LONG).show()
                        }.addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                avatarFolder.downloadUrl.addOnSuccessListener { uri ->
                                    val update_data = HashMap<String, Any>()
                                    update_data.put("avatar", uri.toString())

                                    UserUtils.updateUser(drawerLayout, update_data)
                                }
                            }
                            waitingDialog.dismiss()
                        }.addOnProgressListener { taskSnapshot ->
                            val progress =
                                (100.0 * taskSnapshot.bytesTransferred / taskSnapshot.totalByteCount)
                            waitingDialog.setMessage(
                                StringBuilder("Uploading: ").append(progress).append("%")
                            )
                        }
                }
            }.setCancelable(false)
        val dialog = builder.create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setTextColor(ContextCompat.getColor(this@HomeActivity, android.R.color.holo_red_dark))
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
                .setTextColor(ContextCompat.getColor(this@HomeActivity, R.color.colorAccent))
        }
        dialog.show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.home, menu)
        return true
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    companion object {
        val PICK_IMAGE_REQUEST = 7272
    }





    private fun goToRiderProfileActivity(model: RiderInfoModel?) {
        Common.currentRider=model
        startActivity(Intent(this,RiderProfileActivity::class.java))
        finish()

    }
}