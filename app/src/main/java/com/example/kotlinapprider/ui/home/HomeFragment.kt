package com.example.kotlinapprider.ui.home

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.content.res.Resources
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.bumptech.glide.Glide.init
import com.example.kotlinapprider.Common.Common
import com.example.kotlinapprider.R
import com.firebase.geofire.GeoFire
import com.firebase.geofire.GeoLocation
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.karumi.dexter.Dexter
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionDeniedResponse
import com.karumi.dexter.listener.PermissionGrantedResponse
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.single.PermissionListener
import java.lang.Exception

class HomeFragment : Fragment(), OnMapReadyCallback {

    private lateinit var homeViewModel: HomeViewModel
    private lateinit var mMap: GoogleMap
    private lateinit var mapFragment: SupportMapFragment

    // location
    lateinit var locationRequest: LocationRequest
    lateinit var locationCallback: LocationCallback
    lateinit var fusedLocationProviderClient: FusedLocationProviderClient

    //online system
    private lateinit var onlineRef: DatabaseReference
    private lateinit var currentUserRef: DatabaseReference
    private lateinit var driverLocationRef: DatabaseReference
    private lateinit var geofire: GeoFire

    private val onlineValueEventListener = object: ValueEventListener {

        override fun onCancelled(error: DatabaseError) {
            Snackbar.make(mapFragment.requireView(),error.message,Snackbar.LENGTH_LONG).show()
        }
        override fun onDataChange(snapshot: DataSnapshot) {
            if(snapshot.exists()){
                currentUserRef.onDisconnect().removeValue()
            }
        }

    }//

    override fun onDestroy() {
        fusedLocationProviderClient.removeLocationUpdates(locationCallback)
        geofire.removeLocation(FirebaseAuth.getInstance().currentUser!!.uid)
        onlineRef.removeEventListener(onlineValueEventListener)
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        registerOnlineSystem()
    }

    private fun registerOnlineSystem(){
        onlineRef.addValueEventListener(onlineValueEventListener)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        homeViewModel = ViewModelProvider(this).get(HomeViewModel::class.java)
        val root = inflater.inflate(R.layout.fragment_home, container, false)
        init()
        mapFragment = childFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
        return root
    }

    private fun init() {
        // online system takip
        onlineRef = FirebaseDatabase.getInstance().getReference().child(".info/connected")

        //driverLocationRef = FirebaseDatabase.getInstance().getReference(Common.DRIVERS_LOCATION_REFERENCE).child(FirebaseAuth.getInstance().currentUser!!.uid).child(Common.DRIVERS_LOCATION_REFERENCE)
       // currentUserRef = FirebaseDatabase.getInstance().getReference(Common.DRIVERS_LOCATION_REFERENCE).child(FirebaseAuth.getInstance().currentUser!!.uid).child(Common.DRIVERS_LOCATION_REFERENCE)

        driverLocationRef    = FirebaseDatabase.getInstance().getReference("presentence_locations").child(Common.DRIVERS_LOCATION_REFERENCE).child(FirebaseAuth.getInstance().currentUser!!.uid).child(Common.DRIVERS_LOCATION_REFERENCE)
        currentUserRef    = FirebaseDatabase.getInstance().getReference("presentence_locations").child(Common.DRIVERS_LOCATION_REFERENCE).child(FirebaseAuth.getInstance().currentUser!!.uid).child(Common.DRIVERS_LOCATION_REFERENCE)

        geofire = GeoFire(driverLocationRef)
        registerOnlineSystem()

        locationRequest = LocationRequest()
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
        locationRequest.setFastestInterval(3000)
        locationRequest.interval = 5000
        locationRequest.setSmallestDisplacement(10f)

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult?) {
                super.onLocationResult(locationResult)
                val newPos = LatLng(
                    locationResult!!.lastLocation.latitude,
                    locationResult!!.lastLocation.longitude
                )
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(newPos, 10f))

                //konum güncelle
                geofire.setLocation(
                    "location",
                    GeoLocation(locationResult.lastLocation.latitude,locationResult.lastLocation.longitude)
                )
                {
                        key:String?,error:DatabaseError?->
                    if(error!=null){
                        Snackbar.make(mapFragment.requireView(),error.message,Snackbar.LENGTH_LONG).show()
                    }
                    else{
                        Snackbar.make(mapFragment.requireView(),"You are online!",Snackbar.LENGTH_SHORT).show()
                    }

                }

            }

        }
        fusedLocationProviderClient =
            LocationServices.getFusedLocationProviderClient(requireContext())
        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        fusedLocationProviderClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.myLooper()
        )

    }

    override fun onMapReady(p0: GoogleMap?) {
        mMap = p0!!
        Dexter.withContext(requireContext())
            .withPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
            .withListener(object : PermissionListener {
                @SuppressLint("MissingPermission")
                override fun onPermissionGranted(p0: PermissionGrantedResponse?) {

                    // enable my location button
                    mMap.isMyLocationEnabled = true
                    mMap.uiSettings.isMapToolbarEnabled = true
                    mMap.setOnMyLocationClickListener {
                        fusedLocationProviderClient.lastLocation
                            .addOnFailureListener { e ->
                                Snackbar.make(
                                    requireView(), e.message!!,
                                    Snackbar.LENGTH_LONG
                                ).show()

                            }.addOnSuccessListener { location ->
                                val userLatLng = LatLng(location.latitude, location.longitude)
                                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(userLatLng, 10f))
                            }


                        true

                    }
                    // layout button
                    val view = (mapFragment.requireView()
                        .findViewById<View>("1".toInt())
                        .parent as View)

                    val locationButton = view.findViewById<View>("2".toInt())
                    val params = locationButton.layoutParams as RelativeLayout.LayoutParams
                    params.addRule(RelativeLayout.ALIGN_PARENT_TOP, 0)
                    params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, RelativeLayout.TRUE)
                    params.bottomMargin = 50

                }

                override fun onPermissionDenied(p0: PermissionDeniedResponse?) {
                    Snackbar.make(
                        requireView(),
                        p0!!.permissionName + "Permission" + p0.permissionName + "needed for run app ",
                        Snackbar.LENGTH_LONG
                    ).show()

                }

                override fun onPermissionRationaleShouldBeShown(
                    p0: PermissionRequest?,
                    p1: PermissionToken?
                ) {

                }

            }).check()






        try {
            val success = p0!!.setMapStyle(
                MapStyleOptions.loadRawResourceStyle(
                    requireContext(), R.raw.map_style
                )
            )
            if (!success)
                Snackbar.make(
                    requireView(),
                    "Load map style failed",
                    Snackbar.LENGTH_LONG
                ).show()

        } catch (e: Exception) {
            Snackbar.make(
                requireView(),
                "" + e.message,
                Snackbar.LENGTH_LONG
            ).show()
        }


    }
}