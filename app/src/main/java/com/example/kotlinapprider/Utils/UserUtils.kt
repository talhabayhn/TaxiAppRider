package com.example.kotlinapprider.Utils

import android.content.Context
import android.view.View
import android.widget.RelativeLayout
import android.widget.Toast
import com.example.kotlinapprider.Common.Common
import com.example.kotlinapprider.Model.DriverGeoModel
import com.example.kotlinapprider.Model.FCMSendData
import com.example.kotlinapprider.Model.TokenModel
import com.example.kotlinapprider.R
import com.example.kotlinapprider.Remote.IFCMService
import com.example.kotlinapprider.Remote.RetrofitFCMClient
import com.google.android.gms.maps.model.LatLng
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import retrofit2.create

object UserUtils {
    fun updateUser(
        view: View,
        updateData: Map<String, Any>
    ) {
        FirebaseDatabase.getInstance()
            .getReference(Common.RIDER_INFO_REFERENCE)
             // kullanıcının içindeki avatara yaz
            .child(FirebaseAuth.getInstance().currentUser!!.uid)
            .updateChildren(updateData)
            .addOnFailureListener{e->
                Snackbar.make(view,e.message!!, Snackbar.LENGTH_LONG).show()
            }
            .addOnSuccessListener {
                Snackbar.make(view,"Update information success", Snackbar.LENGTH_LONG).show()
            }
    }

    fun updateToken(context: Context, token: String) {
        val tokenModel = TokenModel()
        tokenModel.token= token

        FirebaseDatabase.getInstance().getReference(Common.TOKEN_REFERENCE)
            .child(FirebaseAuth.getInstance().currentUser!!.uid)
            .setValue(tokenModel)
            .addOnFailureListener { e-> Toast.makeText(context,e.message, Toast.LENGTH_LONG).show() }
            .addOnSuccessListener {  }
    }

    fun sendRequestToDriver(
        context: Context,
        mainLayout: RelativeLayout?,
        foundDriver: DriverGeoModel?,
        target: LatLng
    ){
        val compositeDisposable= CompositeDisposable()
        val ifcmService= RetrofitFCMClient.instance!!.create(IFCMService::class.java)

        //getToken
        FirebaseDatabase.getInstance().getReference(Common.TOKEN_REFERENCE)
            .child(foundDriver!!.key!!).addListenerForSingleValueEvent(object : ValueEventListener{
                override fun onDataChange(snapshot: DataSnapshot) {
                    if(snapshot.exists()){
                        val tokenModel = snapshot.getValue(TokenModel::class.java)

                        val notificationData: MutableMap<String,String> = HashMap()
                        notificationData.put(Common.NOTI_TITLE,Common.REQUEST_DRIVER_TITLE)
                        notificationData.put(Common.NOTI_BODY,"This message represent for Request Driver action")
                        notificationData.put(Common.PICKUP_LOCATION,StringBuilder()
                            .append(target.latitude)
                            .append(",")
                            .append(target.longitude)
                            .toString())

                        val fcmSendData = FCMSendData(tokenModel!!.token,notificationData)

                        compositeDisposable.add(ifcmService.sendNotification(fcmSendData)!!
                            .subscribeOn(Schedulers.newThread())
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe({ fcmResponse ->
                                if (fcmResponse!!.success == 0) {
                                    compositeDisposable.clear()
                                    Snackbar.make(
                                        mainLayout!!,
                                        context.getString(R.string.send_request_driver_failed),
                                        Snackbar.LENGTH_LONG
                                    ).show()
                                }
                            },{t: Throwable? ->

                                compositeDisposable.clear()
                                Snackbar.make(mainLayout!!,t!!.message!!,Snackbar.LENGTH_LONG).show()

                            }))





                    }
                    else
                    {
                        Snackbar.make(mainLayout!!,context.getString(R.string.token_not_found),Snackbar.LENGTH_LONG).show()
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Snackbar.make(mainLayout!!,error.message,Snackbar.LENGTH_LONG).show()
                }


            })
    }

}