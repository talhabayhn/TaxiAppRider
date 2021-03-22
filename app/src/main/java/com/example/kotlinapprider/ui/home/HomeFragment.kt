package com.example.kotlinapprider.ui.home

import android.Manifest
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.BitmapFactory
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.bumptech.glide.Glide.init
import com.example.kotlinapprider.Callback.FirebaseDriverInfoListener
import com.example.kotlinapprider.Callback.FirebaseFailedlistener
import com.example.kotlinapprider.Common.Common
import com.example.kotlinapprider.Model.AnimationModel
import com.example.kotlinapprider.Model.DriverGeoModel
import com.example.kotlinapprider.Model.DriverInfoModel
import com.example.kotlinapprider.Model.EventBus.SelectedPlaceEvent
import com.example.kotlinapprider.Model.GeoQueryModel
import com.example.kotlinapprider.R
import com.example.kotlinapprider.Remote.IGoogleAPI
import com.example.kotlinapprider.Remote.RetrofitClient
import com.example.kotlinapprider.RequestDriverActivity
import com.firebase.geofire.GeoFire

import com.firebase.geofire.GeoLocation
import com.firebase.geofire.GeoQuery
import com.firebase.geofire.GeoQueryEventListener
import com.google.android.gms.common.api.Status
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.AutocompleteSupportFragment
import com.google.android.libraries.places.widget.listener.PlaceSelectionListener
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.karumi.dexter.Dexter
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionDeniedResponse
import com.karumi.dexter.listener.PermissionGrantedResponse
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.single.PermissionListener
import com.sothree.slidinguppanel.SlidingUpPanelLayout
import io.reactivex.Observable
import io.reactivex.Scheduler
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import org.json.JSONObject
import java.io.IOException
import java.lang.Exception
import java.util.*
import kotlin.collections.ArrayList
import kotlinx.android.synthetic.main.fragment_home.*
import org.greenrobot.eventbus.EventBus

class HomeFragment : Fragment(), OnMapReadyCallback, FirebaseDriverInfoListener {
    private lateinit var homeViewModel: HomeViewModel
    private lateinit var mMap: GoogleMap
    private lateinit var mapFragment: SupportMapFragment

    private lateinit var slidingUpLayoutPanel: SlidingUpPanelLayout
    private lateinit var txt_welcome: TextView
    private lateinit var autoCompleteSupportFragment: AutocompleteSupportFragment


    // location
    lateinit var locationRequest: LocationRequest
    lateinit var locationCallback: LocationCallback
    lateinit var fusedLocationProviderClient: FusedLocationProviderClient
    //online system
    private lateinit var onlineRef: DatabaseReference
    private lateinit var currentUserRef: DatabaseReference
    private lateinit var driverLocationRef: DatabaseReference
    private lateinit var geofire: GeoFire
    //load driver
    var distance = 1.0
    val LIMIT_RANGE = 10.0
    var previousLocation: Location? = null
    var currentLocation: Location? = null
    var firstTime = true
    //listener
    lateinit var iFirebaseDriverInfoListener: FirebaseDriverInfoListener
    lateinit var iFirebaseFailedListener: FirebaseFailedlistener
    var cityName = ""

    val compositeDisposable = CompositeDisposable()// ???????????
    lateinit var iGoogleAPI: IGoogleAPI



    private val onlineValueEventListener = object : ValueEventListener {

        override fun onCancelled(error: DatabaseError) {
            Snackbar.make(mapFragment.requireView(), error.message, Snackbar.LENGTH_LONG).show()
        }

        override fun onDataChange(snapshot: DataSnapshot) {
            if (snapshot.exists()) {
                currentUserRef.onDisconnect().removeValue()
            }
        }

    }

    override fun onStop() {
        compositeDisposable.clear() //????????
        super.onStop()
    }

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

    private fun registerOnlineSystem() {
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
        initViews(root)
        mapFragment = childFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
        return root
    }

    private fun initViews(root: View?) {
        slidingUpLayoutPanel = root!!.findViewById(R.id.activity_main) as SlidingUpPanelLayout
        txt_welcome = root!!.findViewById(R.id.txt_welcome) as TextView

        Common.setWelcomeMessage(txt_welcome)

    }

    private fun init() {

        Places.initialize(requireContext(),getString(R.string.google_maps_key))
        autoCompleteSupportFragment= childFragmentManager.findFragmentById(R.id.autocomplete_fragment) as AutocompleteSupportFragment
        autoCompleteSupportFragment.setPlaceFields(Arrays.asList(Place.Field.ID,
        Place.Field.ADDRESS,
        Place.Field.LAT_LNG,
        Place.Field.NAME))
        autoCompleteSupportFragment.setOnPlaceSelectedListener(object : PlaceSelectionListener{
            override fun onPlaceSelected(p0: Place) {
                if (ActivityCompat.checkSelfPermission(
                        context!!,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                        context!!,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    Snackbar.make(requireView(),getString(R.string.permission_require),Snackbar.LENGTH_LONG).show()
                    return
                }
                fusedLocationProviderClient!!
                    .lastLocation.addOnSuccessListener { location->
                        val origin = LatLng(location.latitude,location.longitude)
                        val destination = LatLng(p0.latLng!!.latitude,p0.latLng!!.longitude)

                        startActivity(Intent(requireContext(),RequestDriverActivity::class.java))
                        EventBus.getDefault().postSticky(SelectedPlaceEvent(origin,destination))
                    }

            }

            override fun onError(p0: Status) {
                Snackbar.make(requireView(),p0.statusMessage!!,Snackbar.LENGTH_LONG).show()
            }

        })


        iGoogleAPI= RetrofitClient.instance!!.create(IGoogleAPI::class.java)

        iFirebaseDriverInfoListener = this

        // online sys following
        onlineRef = FirebaseDatabase.getInstance().getReference().child(".info/connected")

        driverLocationRef = FirebaseDatabase.getInstance().getReference("presentence_locations")
            .child(Common.DRIVERS_LOCATION_REFERENCE)
            .child(FirebaseAuth.getInstance().currentUser!!.uid)
            .child(Common.DRIVERS_LOCATION_REFERENCE)
        currentUserRef = FirebaseDatabase.getInstance().getReference("presentence_locations")
            .child(Common.DRIVERS_LOCATION_REFERENCE)
            .child(FirebaseAuth.getInstance().currentUser!!.uid)
            .child(Common.DRIVERS_LOCATION_REFERENCE)

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


                // use when change location calculate and load driver again
                if (firstTime) {
                    previousLocation = locationResult.lastLocation
                    currentLocation = locationResult.lastLocation

                    setRestrictedPlacesInCountry(locationResult!!.lastLocation)

                    firstTime = false
                } else {
                    previousLocation = currentLocation
                    currentLocation = locationResult.lastLocation
                }
                if (previousLocation!!.distanceTo(currentLocation) / 1000 <= LIMIT_RANGE) {
                   loadAvaibleDrivers();

                }


                //update location  /// taşı
                geofire.setLocation("location", GeoLocation(locationResult.lastLocation.latitude, locationResult.lastLocation.longitude))

                // being online snack
                { key: String?, error: DatabaseError? ->
                    if (error != null) {
                        Snackbar.make(mapFragment.requireView(), error.message, Snackbar.LENGTH_LONG).show()
                    } else {
                        Snackbar.make(mapFragment.requireView(), "You are online!", Snackbar.LENGTH_SHORT).show()
                    }

                }

            }

        }
        fusedLocationProviderClient =
            LocationServices.getFusedLocationProviderClient(requireContext())
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) {
            //Snackbar.make(requireView(),getString(R.string.permission_require),Snackbar.LENGTH_SHORT).show()
            return
        }
        fusedLocationProviderClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.myLooper()
        )
       // loadAvaibleDrivers()
    }
    private fun setRestrictedPlacesInCountry(location: Location?){
        try{
            val geoCoder = Geocoder(requireContext(), Locale.getDefault())
            var addressList= geoCoder.getFromLocation(location!!.latitude, location!!.longitude, 1)
            if(addressList.size> 0)
                autoCompleteSupportFragment.setCountry(addressList[0].countryCode)

        }catch (e:IOException){
            e.printStackTrace()
        }

    }

    //load map
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
                        //.findViewById<View>("1".toInt()).parent as View)
                        .findViewById<View>("1".toInt()).parent as View)
                        .findViewById<View>("2".toInt())

                    val locationButton = view.findViewById<View>("2".toInt())
                    val params = locationButton.layoutParams as RelativeLayout.LayoutParams
                    params.addRule(RelativeLayout.ALIGN_PARENT_TOP, 0)
                    params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, RelativeLayout.TRUE)
                    params.bottomMargin = 250


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

            })

            .check()

        //enable zoom
        mMap.uiSettings.isZoomControlsEnabled = true

        // load map
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

    private fun loadAvaibleDrivers() {
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Snackbar.make(
                requireView(),
                getString(R.string.permission_require),
                Snackbar.LENGTH_SHORT
            ).show()
            return
        }
        fusedLocationProviderClient.lastLocation
            .addOnFailureListener { e ->
                Snackbar.make(requireView(), e.message!!, Snackbar.LENGTH_SHORT).show()
            }
            .addOnSuccessListener { location ->
                // load all drivers in city
                val geoCoder = Geocoder(requireContext(), Locale.getDefault())
                var addressList: List<Address> = ArrayList()

                try {
                    addressList = geoCoder.getFromLocation(location.latitude, location.longitude, 1)
                    if(addressList.size>0)
                        cityName = addressList[0].locality


                    // querry
                    if(!TextUtils.isEmpty(cityName)) {
                        val driver_location_ref =
                            FirebaseDatabase.getInstance() // = https://kotlinapp3-default-rtdb.firebaseio.com/DriversLocation/JacksonVille
                                .getReference(Common.DRIVERS_LOCATION_REFERENCES)
                                .child(cityName)

                        val gf = GeoFire(driver_location_ref)

                        val geoQuerry = gf.queryAtLocation(
                            GeoLocation(location.latitude, location.longitude),
                            distance
                        )

                        geoQuerry.removeAllListeners()

                        geoQuerry.addGeoQueryEventListener(object : GeoQueryEventListener {
                            override fun onKeyEntered(key: String?, location: GeoLocation?) {
                                Common.driversFound.add(DriverGeoModel(key!!, location!!))
                                println(Common.driversFound.size)
                                println("entered")
                                //println("key is :"+key+" lcoation is:"+location)
                            }

                            override fun onKeyExited(key: String?) {

                            }

                            override fun onKeyMoved(key: String?, location: GeoLocation?) {

                            }

                            override fun onGeoQueryReady() {
                                if (distance <= LIMIT_RANGE) {
                                    distance++
                                    loadAvaibleDrivers()
                                    println("readyif")
                                } else {
                                    distance = 0.0
                                    addDriverMarker()
                                    println("ereadyelse")
                                }
                            }

                            override fun onGeoQueryError(error: DatabaseError?) {
                                Snackbar.make(requireView(), error!!.message, Snackbar.LENGTH_SHORT)
                                    .show()
                            }

                        })

                        driver_location_ref.addChildEventListener(object : ChildEventListener {
                            override fun onChildAdded(
                                snapshot: DataSnapshot,
                                previousChildName: String?
                            ) {
                                // have new driver
                                val geoQueryModel = snapshot.getValue(GeoQueryModel::class.java)
                                val geoLocation =
                                    GeoLocation(geoQueryModel!!.l!![0], geoQueryModel!!.l!![1])
                                val driverGeoModel = DriverGeoModel(snapshot.key, geoLocation)
                                val newDriverLocation = Location("")
                                newDriverLocation.latitude = geoLocation.latitude
                                newDriverLocation.longitude = geoLocation.longitude
                                val newDistance =
                                    location.distanceTo(newDriverLocation) / 1000 // km
                                if (newDistance <= LIMIT_RANGE) {
                                    findDriverByKey(driverGeoModel)
                                }
                            }

                            override fun onChildChanged(
                                snapshot: DataSnapshot,
                                previousChildName: String?
                            ) {
                            }

                            override fun onChildRemoved(snapshot: DataSnapshot) {}

                            override fun onChildMoved(
                                snapshot: DataSnapshot,
                                previousChildName: String?
                            ) {
                            }

                            override fun onCancelled(error: DatabaseError) {
                                Snackbar.make(requireView(), error.message, Snackbar.LENGTH_SHORT)
                                    .show()
                            }

                        })
                    }
                    else
                        Snackbar.make(requireView(), getString((R.string.city_name_not_found)), Snackbar.LENGTH_LONG)

                } catch (e: IOException) {
                    Snackbar.make(
                        requireView(),
                        getString(R.string.permission_require),
                        Snackbar.LENGTH_SHORT
                    ).show()
                }
            }
    }

    private fun addDriverMarker() {

        if (Common.driversFound.size > 0) {
            Observable.fromIterable(Common.driversFound)
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { driverGeoModel: DriverGeoModel? ->
                        findDriverByKey(driverGeoModel)
                        println("adddrivermarkerdrivergeomodel--"+driverGeoModel)  //com.example.kotlinapprider.Model.DriverGeoModel@60828d4 ve com.example.kotlinapprider.Model.DriverGeoModel@71f3110 yükledi
                    },
                    { t: Throwable? ->
                        Snackbar.make(requireView(), t!!.message!!, Snackbar.LENGTH_SHORT).show()
                    }
                )
        } else {
            Snackbar.make(
                requireView(),
                getString(R.string.drivers_not_found),
                Snackbar.LENGTH_SHORT
            ).show()
        }
    }

    private fun findDriverByKey(driverGeoModel: DriverGeoModel?) {
        FirebaseDatabase.getInstance()
            .getReference(Common.DRIVER_INFO_REFERENCE)
            .child(driverGeoModel!!.key!!)

            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.hasChildren()) {

                        driverGeoModel.driverInfoModel =
                            (snapshot.getValue(DriverInfoModel::class.java))
                        iFirebaseDriverInfoListener.onDriverInfoLoadSuccess(driverGeoModel)


                    } else {
                        iFirebaseFailedListener.onFirebaseFailed(getString(R.string.key_not_found) + driverGeoModel.key)
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    iFirebaseFailedListener.onFirebaseFailed(error.message)
                }

            })
    }

    override fun onDriverInfoLoadSuccess(driverGeoModel: DriverGeoModel?) {
        // if already have marker with this key, does not see it again
        if (!Common.markerList.containsKey(driverGeoModel!!.key))
            Common.markerList.put(driverGeoModel!!.key!!,
                mMap.addMarker(MarkerOptions()
                        .position(LatLng(driverGeoModel!!.geoLocation!!.latitude, driverGeoModel!!.geoLocation!!.longitude))
                        .flat(true)
                        .title(
                            Common.buildName(
                                driverGeoModel.driverInfoModel!!.firstName,
                                driverGeoModel.driverInfoModel!!.lastName
                            )
                        )
                        .snippet(driverGeoModel.driverInfoModel!!.phoneNumber)
                        .icon(BitmapDescriptorFactory.fromResource(R.drawable.car)))

            )



        if (!TextUtils.isEmpty(cityName)) {
            val driverLocation = FirebaseDatabase.getInstance()
                .getReference(Common.DRIVERS_LOCATION_REFERENCES)
                .child(cityName)
                .child(driverGeoModel!!.key!!)
            println("driverlocation**"+driverLocation)
            driverLocation.addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!snapshot.hasChildren()) {
                        if (Common.markerList.get(driverGeoModel!!.key!!) != null)
                        {
                            val marker = Common.markerList.get(driverGeoModel!!.key!!)
                           marker!!.remove() // remove marker from map
                            Common.markerList.remove(driverGeoModel!!.key!!) // remove marker info

                            Common.driverSubscribe.remove(driverGeoModel.key!!) // remove driver info

                            driverLocation.removeEventListener(this)

                        }

                    }
                    else{
                        if (Common.markerList.get(driverGeoModel!!.key!!) != null){
                            val geoQueryModel = snapshot!!.getValue(GeoQueryModel::class.java)
                            val animationModel = AnimationModel(false,geoQueryModel!!)
                            if (Common.driverSubscribe.get(driverGeoModel.key!!) != null)
                            {
                                val marker =Common.markerList.get(driverGeoModel!!.key!!)
                                val oldPosition= Common.driverSubscribe.get(driverGeoModel.key!!)

                                val from= StringBuilder()
                                    .append(oldPosition!!.geoQueryModel!!.l?.get(0))
                                    .append(",")
                                    .append(oldPosition!!.geoQueryModel!!.l?.get(1))
                                    .toString()

                                val to= StringBuilder()
                                    .append(animationModel.geoQueryModel!!.l?.get(0))
                                    .append(",")
                                    .append(animationModel.geoQueryModel!!.l?.get(1))
                                    .toString()

                                moveMarkerAnimation(driverGeoModel.key!!,animationModel,marker,from,to)
                            }
                            else{
                                Common.driverSubscribe.put(driverGeoModel.key!!,animationModel)
                            }
                        }

                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Snackbar.make(requireView(), error.message, Snackbar.LENGTH_SHORT).show()
                }

            })
        }

    }

    private fun moveMarkerAnimation(key: String
                                    , newData: AnimationModel
                                    , marker: Marker?
                                    , from: String
                                    , to:String)
    {
        if(!newData.isRun)
        {
            //Request API
            compositeDisposable.add(iGoogleAPI.getDirections("driving",
            "less_driving",from,to,
            getString(R.string.google_api_key))
                !!.subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe{ returnResult ->

                    Log.d("API_RETURN",returnResult)
                    try{
                        val jsonObject= JSONObject(returnResult)
                        val jsonArray = jsonObject.getJSONArray("routes")

                        for(i in 0 until jsonArray.length())
                        {
                            val route = jsonArray.getJSONObject(i)
                            val poly = route.getJSONObject("overview_polyline")
                            val polyline = poly.getString("points")
                            //polylineList = Common.decodePoly(polyline)
                            newData.polylineList= Common.decodePoly(polyline)
                        }

                        // moving
                        newData.index= -1
                        newData.next= 1

                        val runnable = object : Runnable{
                            override fun run() {
                                if(newData.polylineList!=null &&newData.polylineList!!.size >1)
                                {
                                    if(newData.index <newData.polylineList!!.size-2)
                                    {
                                        newData.index++
                                        newData.next = newData.index+1
                                        newData.start = newData.polylineList!![newData.index]!!
                                        newData.end = newData.polylineList!![newData.next]!!
                                    }
                                    val valueAnimator = ValueAnimator.ofInt(0,1)
                                    valueAnimator.duration= 3000
                                    valueAnimator.interpolator = LinearInterpolator()
                                    valueAnimator.addUpdateListener { value ->
                                        newData.v= value.animatedFraction
                                        newData.lat = newData.v*newData.end!!.latitude +(1-newData.v) * newData.start!!.latitude
                                        newData.lng = newData.v*newData.end!!.longitude +(1-newData.v) * newData.start!!.longitude
                                        val newPos = LatLng(newData.lat,newData.lng)
                                        marker!!.position = newPos
                                        marker!!.setAnchor(0.5f,0.5f)
                                        marker!!.rotation = Common.getBearing(newData.start!!,newPos)
                                    }
                                    valueAnimator.start()
                                    if(newData.index < newData.polylineList!!.size -2)
                                        newData.handler!!.postDelayed(this,1500)
                                    else if( newData.index<newData.polylineList!!.size-1)
                                    {
                                        newData.isRun= false
                                        Common.driverSubscribe.put(key,newData) // to update
                                    }

                                }
                            }

                        }

                        newData.handler!!.postDelayed(runnable,1500)

                    }catch (e:java.lang.Exception)
                    {
                        Snackbar.make(requireView(),e.message!!,Snackbar.LENGTH_LONG).show()
                    }

                }
            )
        }


    }


}

