package com.example.kotlinapprider.Common

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.kotlinapprider.Model.DriverGeoModel
import com.example.kotlinapprider.Model.RiderInfoModel
import com.example.kotlinapprider.R
import com.google.android.gms.maps.model.Marker
import java.lang.StringBuilder

object Common {
    val markerList: MutableMap<String,Marker> = HashMap<String,Marker>()
    val DRIVER_INFO_REFERENCE: String="DriverInfo"
    val driversFound: MutableSet<DriverGeoModel> = HashSet<DriverGeoModel>()
    val DRIVERS_LOCATION_REFERENCES: String="DriversLocation" //load drivers

    val NOTI_TITLE: String= "title"// for fcm
    val NOTI_BODY: String ="body"
    val TOKEN_REFERENCE: String="Token"

    val DRIVERS_LOCATION_REFERENCE : String="riders locations"
    var currentRider: RiderInfoModel?= null
    val RIDER_INFO_REFERENCE: String="riders"


    fun buildWelcomeMessage(): String {
        return StringBuilder("Welcome ")
            .append(currentRider!!.firstName)
            .append(" ")
            .append(currentRider!!.lastName)
            .toString()

    }


    fun showNotification(context: Context, id: Int, title: String?, body: String?, intent: Intent?) {
        var pendingIntent: PendingIntent? = null
        if(intent!= null)
            pendingIntent= PendingIntent.getActivity(context,id,intent!!, PendingIntent.FLAG_UPDATE_CURRENT)
        val NOTIFICATION_CHANNEL_ID= "tway_driver"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        {
            val notificationChannel = NotificationChannel(NOTIFICATION_CHANNEL_ID,"driver", NotificationManager.IMPORTANCE_HIGH)
            notificationChannel.description="riding"
            notificationChannel.enableLights(true)
            notificationChannel.lightColor= Color.RED
            notificationChannel.enableVibration(true)
            notificationChannel.vibrationPattern= longArrayOf(0,1000,500,1000)

            notificationManager.createNotificationChannel(notificationChannel)
        }
        val builder = NotificationCompat.Builder(context,NOTIFICATION_CHANNEL_ID)
        builder.setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(false)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(Notification.DEFAULT_VIBRATE)
            .setSmallIcon(R.drawable.ic_baseline_motor)
            .setLargeIcon(BitmapFactory.decodeResource(context.resources,R.drawable.ic_baseline_motor))

        if(pendingIntent != null)
            builder.setContentIntent(pendingIntent!!)
        val notification =builder.build()
        notificationManager.notify(id,notification)

    }

    fun buildName(firstName: String?, lastName: String?): String? {
        return java.lang.StringBuilder(firstName).append(" ").append(lastName).toString()
    }


}