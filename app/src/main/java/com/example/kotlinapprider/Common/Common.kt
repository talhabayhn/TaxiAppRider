package com.example.kotlinapprider.Common

import android.animation.ValueAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.example.kotlinapprider.Model.AnimationModel
import com.example.kotlinapprider.Model.DriverGeoModel
import com.example.kotlinapprider.Model.RiderInfoModel
import com.example.kotlinapprider.R
import com.example.kotlinapprider.RequestDriverActivity
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.maps.android.ui.IconGenerator
import java.lang.StringBuilder
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.HashSet

object Common {
    val BASE_FARE: Double = 3.0 // setting table
    val RIDER_TOTAL_FEE: String="TotalFeeRider"
    val RIDER_DURATION_VALUE: String="DurationRiderValue"
    val RIDER_DURATION_TEXT: String="DurationRider"
    val RIDER_DISTANCE_VALUE: String= "DistanceRiderValue"
    val RIDER_DISTANCE_TEXT: String= "DistanceRider"
    val RIDER_REQUEST_COMPLETE_TRIP: String="RequestCompleteTripToRider"
    val REQUEST_DRIVER_DECLINE_AND_REMOVE_TRIP: String="DeclineAndRemoveTrip"
    val TRIP: String="Trips"
    val TRIP_KEY: String= "TripKey"
    val REQUEST_DRIVER_ACCEPT: String ="Accept"
    val DESTINATION_LOCATION: String="DestinationLocation"
    val DESTINATION_LOCATION_STRING: String="DestinationLocationString"
    val PICKUP_LOCATION_STRING: String="PickupLocationString"
    val REQUEST_DRIVER_DECLINE: String?= "Decline"
    val RIDER_KEY: String="RiderKey"
    val PICKUP_LOCATION: String= "PickupLocation"
    val REQUEST_DRIVER_TITLE: String= "RequestDriver"
    val driverSubscribe: MutableMap<String, AnimationModel> = HashMap<String,AnimationModel>()

    val markerList: MutableMap<String,Marker> = HashMap<String,Marker>()
    val DRIVER_INFO_REFERENCE: String="DriverInfo"
    val driversFound: MutableMap<String,DriverGeoModel> = HashMap<String,DriverGeoModel>()
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
        return StringBuilder(firstName).append(" ").append(lastName).toString()
    }



    // Car animation
     fun decodePoly(encoded: String): ArrayList<LatLng?> {
        val poly = ArrayList<LatLng?>()
        var index = 0
        val len = encoded.length
        var lat = 0
        var lng = 0

        while (index < len) {
            var b: Int
            var shift = 0
            var result = 0
            do {
                b = encoded[index++].toInt() - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlat = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lat += dlat

            shift = 0
            result = 0
            do {
                b = encoded[index++].toInt() - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlng = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lng += dlng

            val p = LatLng(lat.toDouble() / 1E5,
                lng.toDouble() / 1E5)
            poly.add(p)
        }

        return poly
    }

    //GET BEARING
    fun getBearing(begin: LatLng, end: LatLng): Float {
        val lat = Math.abs(begin.latitude - end.latitude)
        val lng = Math.abs(begin.longitude - end.longitude)

        if (begin.latitude < end.latitude && begin.longitude < end.longitude)
            return Math.toDegrees(Math.atan(lng / lat)).toFloat()
        else if (begin.latitude >= end.latitude && begin.longitude < end.longitude)
            return ((90 - Math.toDegrees(Math.atan(lng / lat)) + 90).toFloat())
        else if (begin.latitude >= end.latitude && begin.longitude >= end.longitude)
            return ((Math.toDegrees(Math.atan(lng / lat)) + 180).toFloat())
        else if (begin.latitude < end.latitude && begin.longitude >= end.longitude)
            return (((90 - Math.toDegrees(Math.atan(lng / lat))) + 270).toFloat())
        return (-1f).toFloat()
    }

    fun setWelcomeMessage(txtWelcome: TextView) {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        if(hour >= 1 && hour <=12)
            txtWelcome.setText(StringBuilder("Good morning"))
        else if(hour > 12 && hour <=12)
            txtWelcome.setText(StringBuilder("Good afternoon"))
        else
            txtWelcome.setText(StringBuilder("Good evening"))

    }

    fun formatDuration(duration: String): CharSequence? {
        if(duration.contains("mins"))
            return duration.substring(0,duration.length-1)
        else
            return duration
    }

    fun formatAddress(startAddress: String): CharSequence? {
        val firstIndexComma = startAddress.indexOf(",")
        return startAddress.substring(0,firstIndexComma)
    }

    fun valueAnimator(duration: Int, listener:ValueAnimator.AnimatorUpdateListener):ValueAnimator{

        val va= ValueAnimator.ofFloat(0f,100f)
        va.duration = duration.toLong()
        va.addUpdateListener(listener)
        va.repeatCount = ValueAnimator.INFINITE
        va.repeatMode= ValueAnimator.RESTART
        va.start()
        return  va
    }

    fun createIconWithDuration(context: Context,duration: String): Bitmap? {
        val view = LayoutInflater.from(context).inflate(R.layout.pickup_info_with_duration_windows,null)
        val txt_time= view.findViewById<View>(R.id.txt_duration) as TextView
        txt_time.setText(getNumberFromText(duration!!))
        val generator = IconGenerator(context)
        generator.setContentView(view)
        generator.setBackground(ColorDrawable(Color.TRANSPARENT))
        return  generator.makeIcon()
    }

    private fun getNumberFromText(s: String): String {
        return  s.substring(0,s.indexOf(" "))
    }

    fun calculateFeeBaseOnMeters(meters: Int): Double {


        val number : Double =((meters*BASE_FARE)/1000)
     return  String.format("%.2f", number).toDouble();
    }


}