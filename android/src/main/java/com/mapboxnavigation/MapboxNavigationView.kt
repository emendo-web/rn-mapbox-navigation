package com.mapboxnavigation
import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.res.Resources
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import androidx.core.app.ActivityCompat
import androidx.core.view.isVisible
import com.facebook.react.uimanager.ThemedReactContext
import com.mapbox.api.directions.v5.DirectionsCriteria
import com.mapbox.common.location.Location
import com.mapbox.api.directions.v5.models.RouteOptions
import com.mapbox.bindgen.Expected
import com.mapbox.bindgen.ExpectedFactory
import com.mapbox.common.location.AccuracyLevel
import com.mapbox.common.location.DeviceLocationProvider
import com.mapbox.common.location.DeviceLocationProviderFactory
import com.mapbox.common.location.IntervalSettings
import com.mapbox.common.location.LocationProviderRequest
import com.mapbox.common.location.LocationService
import com.mapbox.common.location.LocationServiceFactory
import com.mapbox.navigation.core.trip.session.LocationObserver
import com.mapbox.geojson.Point
import com.mapbox.maps.EdgeInsets
import com.mapbox.maps.Style
import com.mapbox.maps.plugin.animation.camera
import com.mapbox.maps.plugin.compass.compass
import com.mapbox.maps.plugin.locationcomponent.location
import com.mapbox.maps.plugin.scalebar.scalebar
import com.mapbox.navigation.base.ExperimentalPreviewMapboxNavigationAPI
import com.mapbox.navigation.base.extensions.applyDefaultNavigationOptions
import com.mapbox.navigation.base.extensions.applyLanguageAndVoiceUnitOptions
import com.mapbox.navigation.base.options.LocationOptions
import com.mapbox.navigation.base.options.NavigationOptions
import com.mapbox.navigation.base.route.NavigationRoute
import com.mapbox.navigation.base.route.NavigationRouterCallback
import com.mapbox.navigation.base.route.RouterFailure
import com.mapbox.navigation.core.MapboxNavigation
import com.mapbox.navigation.core.MapboxNavigationProvider
import com.mapbox.navigation.core.directions.session.RoutesObserver
import com.mapbox.navigation.core.replay.route.ReplayRouteMapper
import com.mapbox.navigation.core.trip.session.LocationMatcherResult
import com.mapbox.navigation.core.trip.session.RouteProgressObserver
import com.mapbox.navigation.core.trip.session.VoiceInstructionsObserver
import com.mapbox.navigation.tripdata.maneuver.api.MapboxManeuverApi
import com.mapbox.navigation.ui.base.util.MapboxNavigationConsumer
import com.mapbox.navigation.ui.maps.camera.NavigationCamera
import com.mapbox.navigation.ui.maps.camera.data.MapboxNavigationViewportDataSource
import com.mapbox.navigation.ui.maps.camera.lifecycle.NavigationBasicGesturesHandler
import com.mapbox.navigation.ui.maps.camera.state.NavigationCameraState
import com.mapbox.navigation.ui.maps.camera.transition.NavigationCameraTransitionOptions
import com.mapbox.navigation.ui.maps.location.NavigationLocationProvider
import com.mapbox.navigation.ui.maps.route.arrow.api.MapboxRouteArrowApi
import com.mapbox.navigation.ui.maps.route.arrow.api.MapboxRouteArrowView
import com.mapbox.navigation.ui.maps.route.arrow.model.RouteArrowOptions
import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineApi
import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineView
import com.mapbox.navigation.ui.maps.route.line.model.MapboxRouteLineApiOptions
import com.mapbox.navigation.ui.maps.route.line.model.MapboxRouteLineViewOptions
import com.mapbox.navigation.voice.api.MapboxSpeechApi
import com.mapbox.navigation.voice.api.MapboxVoiceInstructionsPlayer
import com.mapbox.navigation.voice.model.SpeechAnnouncement
import com.mapbox.navigation.voice.model.SpeechError
import com.mapbox.navigation.voice.model.SpeechValue
import com.mapbox.navigation.voice.model.SpeechVolume
import com.mapboxnavigation.databinding.NavigationViewBinding
import java.util.Locale
import com.mapbox.navigation.tripdata.maneuver.model.Maneuver
@SuppressLint("ViewConstructor")
class MapboxNavigationView(private val context: ThemedReactContext, private val accessToken: String?) :
    FrameLayout(context.baseContext) {

    private companion object {
        private const val BUTTON_ANIMATION_DURATION = 1500L
    }

    private var origin: Point? = null
    private var destination: Point? = null
    private var shouldSimulateRoute = false
    private var showsEndOfRouteFeedback = false
    private var isVoiceInstructionsMuted = false
        set(value) {
            field = value
            if (value) {
                voiceInstructionsPlayer.volume(SpeechVolume(0f))
            } else {
                voiceInstructionsPlayer.volume(SpeechVolume(1f))
            }
        }



    /**
     * Generates updates for the [MapboxManeuverView] to display the upcoming maneuver instructions
     * and remaining distance to the maneuver point.
     */
    private lateinit var maneuverApi: MapboxManeuverApi

    /**
     * API for generating speech announcements from the Mapbox Voice API.
     */
    private lateinit var speechAPI: MapboxSpeechApi

    /**
     * API for playing voice instructions.
     */
    private lateinit var voiceInstructionsPlayer: MapboxVoiceInstructionsPlayer


    /*
     * Below are generated camera padding values to ensure that the route fits well on screen while
     * other elements are overlaid on top of the map (including instruction view, buttons, etc.)
     */
    private val pixelDensity = Resources.getSystem().displayMetrics.density


    private val overviewPadding: EdgeInsets by lazy {
        EdgeInsets(
            140.0 * pixelDensity,
            40.0 * pixelDensity,
            120.0 * pixelDensity,
            40.0 * pixelDensity
        )
    }

    private val landscapeOverviewPadding: EdgeInsets by lazy {
        EdgeInsets(
            30.0 * pixelDensity,
            380.0 * pixelDensity,
            110.0 * pixelDensity,
            20.0 * pixelDensity
        )
    }

    private val followingPadding: EdgeInsets by lazy {
        EdgeInsets(
            180.0 * pixelDensity,
            40.0 * pixelDensity,
            150.0 * pixelDensity,
            40.0 * pixelDensity
        )
    }

    private val landscapeFollowingPadding: EdgeInsets by lazy {
        EdgeInsets(
            30.0 * pixelDensity,
            380.0 * pixelDensity,
            110.0 * pixelDensity,
            40.0 * pixelDensity
        )
    }


    /**
     * View binding for the [NavigationViewBinding] layout.
     */
    private var binding: NavigationViewBinding =
        NavigationViewBinding.inflate(LayoutInflater.from(context), this, true)

    /**
     * Data source for the [NavigationCamera] to follow the user location and progress along the route.
     */
    private lateinit var viewportDataSource: MapboxNavigationViewportDataSource

    /**
     * Camera that follows the user location and progress along the route.
     */
    private lateinit var navigationCamera: NavigationCamera

    /**
     * Location provider that provides location updates to the Navigation SDK.
     */
    private var locationProvider: DeviceLocationProvider? = null

    /**
     * API for drawing the maneuver arrow on the map.
     */
    private val routeArrowApi: MapboxRouteArrowApi = MapboxRouteArrowApi()

    /**
     * View for drawing the maneuver arrow on the map. Works with the [routeArrowApi].
     */
    private lateinit var routeArrowView: MapboxRouteArrowView

    /**
     * Generates updates for the [routeLineView] with the geometries and properties of the routes that should be drawn on the map.
     */
    private lateinit var routeLineApi: MapboxRouteLineApi

    /**
     * Draws route lines on the map based on the data from the [routeLineApi]
     */
    private lateinit var routeLineView: MapboxRouteLineView

    /**
     * Navigation SDK instance.
     */
    private lateinit var mapboxNavigation: MapboxNavigation

    /**
     * [NavigationLocationProvider] is a utility class that helps to provide location updates generated by the Navigation SDK
     * to the Maps SDK in order to update the user location indicator on the map.
     */
    private val navigationLocationProvider = NavigationLocationProvider()

    /**
     * Button to recenter camera on the user lcoation
     */
    private val recenterButton by lazy { findViewById<Button>(R.id.recenter) }

    private val overviewButton by lazy { findViewById<Button>(R.id.routeOverview) }

    private val soundButton by lazy { findViewById<Button>(R.id.soundButton) }



    /**
     * Observes location updates and provides the location updates to the Navigation SDK.
     */
    private val locationObserver = object : LocationObserver {
        override fun onNewRawLocation(rawLocation: Location) {
            // not handled
        }

        override fun onNewLocationMatcherResult(locationMatcherResult: LocationMatcherResult) {
            val enhancedLocation = locationMatcherResult.enhancedLocation
            // update location puck's position on the map
            navigationLocationProvider.changePosition(
                location = enhancedLocation,
                keyPoints = locationMatcherResult.keyPoints,
            )

            // update camera position to account for new location
            viewportDataSource.onLocationChanged(enhancedLocation)
            viewportDataSource.evaluate()
        }
    }

    /**
     * Gets notified with progress along the currently active route.
     */
    private val routesObserver = RoutesObserver { routeUpdateResult ->
        Log.d("MapboxNavigationView", "routesObserver")

        if(routeUpdateResult.navigationRoutes.isNotEmpty()){
            Log.d("MapboxNavigationView", "routesObserver: routeUpdateResult.navigationRoutes.isNotEmpty()")
            routeLineApi.setNavigationRoutes(routeUpdateResult.navigationRoutes) { value ->
                binding.mapView.mapboxMap.style?.apply {
                    routeLineView.renderRouteDrawData(this, value)
                }
            }

            /// update the camera position to account for the new route
            viewportDataSource.onRouteChanged(routeUpdateResult.navigationRoutes.first())
            viewportDataSource.evaluate()

        } else {
            Log.d("MapboxNavigationView", "routesObserver: routeUpdateResult.navigationRoutes.isEmpty()")
            routeLineApi.clearRouteLine { value ->
                binding.mapView.mapboxMap.style?.apply {
                    routeLineView.renderClearRouteLineValue(
                        this,
                        value
                    )
                }
            }

            viewportDataSource.clearRouteData()
            viewportDataSource.evaluate()

        }
    }

    /**
     * Gets notified with progress along the currently active route.
     */
    private val routeProgressObserver = RouteProgressObserver { routeProgress ->

        Log.d("MapboxNavigationView", "routeProgressObserver")
        // update the camera position to account for the progressed fragment of the route
        viewportDataSource.onRouteProgressChanged(routeProgress)
        viewportDataSource.evaluate()

        if (binding.mapView.mapboxMap.style != null) {
            Log.d("MapboxNavigationView", "render maneuver arrow on map")
            val maneuverArrowResult = routeArrowApi.addUpcomingManeuverArrow(routeProgress)
            routeArrowView.renderManeuverUpdate(binding.mapView.mapboxMap.style!!, maneuverArrowResult)
        }

//        val maneuvers = routeProgress.currentLegProgress?.currentStepProgress?.step?.maneuver()

        /// TODO: Update top trip progress banner
        /// TODO: Update bottom trip progress banner
    }

    private val speechCallback =
        MapboxNavigationConsumer<Expected<SpeechError, SpeechValue>> { expected ->
            expected.fold(
                { error ->
                    // In case of error, a fallback announcement is returned that can be played
                    // using [MapboxVoiceInstructionsPlayer]
                    voiceInstructionsPlayer.play(
                        error.fallback,
                        voiceInstructionsPlayerCallback
                    )
                },
                { value ->
                    // The announcement data obtained (synthesized speech mp3 file from Mapbox's API Voice) is played
                    // using [MapboxVoiceInstructionsPlayer]
                    voiceInstructionsPlayer.play(
                        value.announcement,
                        voiceInstructionsPlayerCallback
                    )
                }
            )
        }


    private val voiceInstructionsPlayerCallback =
        MapboxNavigationConsumer<SpeechAnnouncement> { value ->
            // Remove already consumed file to free-up space
            speechAPI.clean(value)
        }


    /**
     * Observes when a new voice instruction should be played.
     */
    private val voiceInstructionsObserver =
        VoiceInstructionsObserver { voiceInstructions ->
            // The data obtained must be used to generate the speech announcement
            speechAPI.generate(
                voiceInstructions,
                speechCallback
            )
        }

    fun onCreate() {

        checkPermissionAndInitProvider()

        val locationOptions = initLocationBuilder().build()


        binding.mapView.location.apply {
            setLocationProvider(navigationLocationProvider)
            puckBearingEnabled = true
            enabled = true
        }

        mapboxNavigation = if (MapboxNavigationProvider.isCreated()) {
            MapboxNavigationProvider.retrieve()
        } else {
            MapboxNavigationProvider.create(
                NavigationOptions.Builder(context).locationOptions(locationOptions).build()
            )
        }

        /// initialize Navigation Camera
        viewportDataSource = MapboxNavigationViewportDataSource(binding.mapView.mapboxMap)

        Log.d("MapboxNavigationView", "initCamera")
        initCamera(viewportDataSource)

        Log.d("MapboxNavigationView", "initPadding")
        initPadding(viewportDataSource)

        Log.d("MapboxNavigationView", "initCameraToOrigin")
        initCameraToOrigin()

        Log.d("MapboxNavigationView", "initMapStyle")
        initMapStyle()

        Log.d("MapboxNavigationView", "initRouteApiAndView")
        initRouteApiAndView()

        Log.d("MapboxNavigationView", "initObserver")
        initObserver()

        Log.d("MapboxNavigationView", "initVoiceInstructionsPlayer")
        initVoiceInstructionsPlayer()



        Log.d("MapboxNavigationView", "loadDestination")
        loadDestination()

        Log.d("MapboxNavigationView", "initButtonsListener")
        initButtonsListener()
    }



    private fun initVoiceInstructionsPlayer(){
        speechAPI = MapboxSpeechApi(
            context,
            Locale.FRANCE.language
        )
        voiceInstructionsPlayer = MapboxVoiceInstructionsPlayer(
            context,
            Locale.FRANCE.language
        )
    }

    private fun initButtonsListener() {
        recenterButton.setOnClickListener{
            Log.d("ButtonClick", "recenterButton.setOnClickListener")
            navigationCamera.requestNavigationCameraToFollowing(
                stateTransitionOptions = NavigationCameraTransitionOptions.Builder()
                    .maxDuration(BUTTON_ANIMATION_DURATION)
                    .build()
            )
        }

        overviewButton.setOnClickListener{
            Log.d("ButtonClick", "overviewButton.setOnClickListener")
            navigationCamera.requestNavigationCameraToOverview(
                stateTransitionOptions = NavigationCameraTransitionOptions.Builder()
                    .maxDuration(BUTTON_ANIMATION_DURATION)
                    .build()
            )
        }

        soundButton.setOnClickListener{
            // mute/unmute voice instructions
            isVoiceInstructionsMuted = !isVoiceInstructionsMuted
        }
    }

    private fun initLocationBuilder(): LocationOptions.Builder {
        val locOptionsBuilder = LocationOptions.Builder()
            .locationProviderFactory(
                DeviceLocationProviderFactory { request ->
                    ExpectedFactory.createValue(locationProvider!!)
                },
                when {
                    shouldSimulateRoute -> LocationOptions.LocationProviderType.MOCKED
                    else -> LocationOptions.LocationProviderType.REAL
                }
            )

        return locOptionsBuilder
//        val locationService : LocationService = LocationServiceFactory.getOrCreate()
//        val request = LocationProviderRequest.Builder()
//            .interval(IntervalSettings.Builder().interval(0L).minimumInterval(0L).maximumInterval(0L).build())
//            .displacement(0F)
//            .accuracy(AccuracyLevel.HIGHEST)
//            .build();
//        val result = locationService.getDeviceLocationProvider(request)
//        if (result.isValue) {
//            locationProvider = result.value!!
//        } else {
//            Log.d("ERROR","Failed to get device location provider")
//        }
    }

    private fun initCamera(vds: MapboxNavigationViewportDataSource){
        navigationCamera = NavigationCamera(
            binding.mapView.mapboxMap,
            binding.mapView.camera,
            vds,
        )

        // set the animations lifecycle listener to ensure the NavigationCamera stops
        // automatically following the user location when the map is interacted with
        binding.mapView.camera.addCameraAnimationsLifecycleListener(
            NavigationBasicGesturesHandler(navigationCamera)
        )

        navigationCamera.registerNavigationCameraStateChangeObserver { navigationCameraState ->
            // shows/hide the recenter button depending on the camera state
            when (navigationCameraState) {
                NavigationCameraState.TRANSITION_TO_FOLLOWING,
                NavigationCameraState.FOLLOWING -> binding.recenter.visibility = View.INVISIBLE
                NavigationCameraState.TRANSITION_TO_OVERVIEW,
                NavigationCameraState.OVERVIEW,
                NavigationCameraState.IDLE -> binding.recenter.visibility = View.VISIBLE
            }
        }
    }

    private fun initCameraToOrigin(){
        navigationCamera.requestNavigationCameraToFollowing(
            stateTransitionOptions = NavigationCameraTransitionOptions.Builder()
                .maxDuration(0) // instant transition
                .build()
        )
    }

    private fun initPadding(vds: MapboxNavigationViewportDataSource){
        if (this.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            vds.overviewPadding = landscapeOverviewPadding
        } else {
            vds.overviewPadding = overviewPadding
        }
        if (this.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            viewportDataSource.followingPadding = landscapeFollowingPadding
        } else {
            viewportDataSource.followingPadding = followingPadding
        }
    }

    private fun initMapStyle(){
        binding.mapView.scalebar.enabled = false
        binding.mapView.compass.enabled = false

        binding.mapView.mapboxMap.loadStyle(Style.MAPBOX_STREETS)
    }

    private fun initObserver(){
        mapboxNavigation.registerRoutesObserver(routesObserver)
        mapboxNavigation.registerLocationObserver(locationObserver)
        mapboxNavigation.registerRouteProgressObserver(routeProgressObserver)
        mapboxNavigation.registerVoiceInstructionsObserver(voiceInstructionsObserver)
        // TODO: Implement arrival observer
    }

    private fun initRouteApiAndView(){
        val mapboxRouteLineApiOptions = MapboxRouteLineApiOptions.Builder().build()
        val mapboxRouteLineViewOptions = MapboxRouteLineViewOptions.Builder(context)
            .routeLineBelowLayerId("road-label")
            .build()
        val mapboxRouteArrowOptions = RouteArrowOptions.Builder(context).build()

        routeLineApi = MapboxRouteLineApi(mapboxRouteLineApiOptions)
        routeLineView = MapboxRouteLineView(mapboxRouteLineViewOptions)
        routeArrowView = MapboxRouteArrowView(mapboxRouteArrowOptions)
    }

    private fun checkPermissionAndInitProvider(){
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val locationService : LocationService = LocationServiceFactory.getOrCreate()

        val request = LocationProviderRequest.Builder()
            .interval(IntervalSettings.Builder().interval(0L).minimumInterval(0L).maximumInterval(0L).build())
            .displacement(0F)
            .accuracy(AccuracyLevel.HIGHEST)
            .build();

        val result = locationService.getDeviceLocationProvider(request)
        if (result.isValue) {
            locationProvider = result.value!!
        } else {
            Log.d("ERROR","Failed to get device location provider")
        }
    }

    /**
     * Checks for location permission and starts the trip session if permission is granted.
     */
    private fun checkPermissionAndStartTripSession() {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Implement permission cancel
            return
        }
        mapboxNavigation.startTripSession(true)

        uiShowButtons(true)
        uiShowManeuver(true)
    }

    /**
     * Checks for location permission and starts the MOCKED trip session if permission is granted.
     */
    @OptIn(ExperimentalPreviewMapboxNavigationAPI::class)
    private fun checkPermissionAndStartMockedTripSession(routes: List<NavigationRoute>){
        val route = routes.firstOrNull()
        if(route != null) {
            Log.d("t", "route is not null")
            mapboxNavigation.mapboxReplayer.run {
                stop()
                clearEvents()
                val replayEvents = ReplayRouteMapper().mapDirectionsRouteGeometry(route.directionsRoute)
                pushEvents(replayEvents)
                seekTo(replayEvents.first())
                play()
            }

            mapboxNavigation.startReplayTripSession()
            uiShowButtons(true)
            uiShowManeuver(true)
        } else {
            Log.d("t", "route is null")
        }
    }

    private fun loadDestination(){
        this.origin?.let { this.destination?.let { it1 ->  findRoute(it, it1) } }
    }

    private fun findRoute(origin: Point, destination: Point) {
        try {
            Log.d("MapboxNavigationView", "findRoute")
            /// TODO: Implement speech api
            mapboxNavigation.requestRoutes(
                RouteOptions.builder()
                    .applyDefaultNavigationOptions()
                    .applyLanguageAndVoiceUnitOptions(context)
                    .coordinatesList(listOf(origin, destination))
                    .profile(DirectionsCriteria.PROFILE_DRIVING)
                    .steps(true)
                    .build(),
                object : NavigationRouterCallback {
                    override fun onCanceled(routeOptions: RouteOptions, routerOrigin: String) {
                        Log.d("MapboxNavigationView", "onCanceled")
                        /// Not implemented
                    }

                    override fun onFailure(
                        reasons: List<RouterFailure>,
                        routeOptions: RouteOptions
                    ) {
                        Log.d("MapboxNavigationView", "onFailure")
                        /// Not implemented
                    }

                    override fun onRoutesReady(
                        routes: List<NavigationRoute>,
                        routerOrigin: String
                    ) {
                        Log.d("MapboxNavigationView", "onRoutesReady")
                        mapboxNavigation.setNavigationRoutes(routes)

                        if(shouldSimulateRoute){
                            checkPermissionAndStartMockedTripSession(routes)
                        } else {
                            checkPermissionAndStartTripSession()
                        }
                    }
                }
            )
        } catch (ex: Exception) {
            Log.d("MapboxNavigationView", "Error finding route $ex")
//            sendErrorToReact(ex.toString())
        }

    }

    private fun uiShowButtons(activate: Boolean){
        binding.soundButton.visibility = if (activate) View.VISIBLE else View.INVISIBLE
        binding.routeOverview.visibility = if (activate) View.VISIBLE else View.INVISIBLE
        binding.recenter.visibility = if (activate) View.VISIBLE else View.INVISIBLE

    }

    private fun uiShowManeuver(activate: Boolean){
        binding.maneuverCardView.visibility = if (activate) View.VISIBLE else View.INVISIBLE
    }

    private fun uiShowTripProgressBanner(activate: Boolean){
        binding.tripProgressCard.visibility = if (activate) View.VISIBLE else View.INVISIBLE
    }

    /**
     * ---------------------------------------------------------------------------------------------
     *                                              LIFECYCLE
     * ---------------------------------------------------------------------------------------------
     */

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        onCreate()
    }


    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()

        mapboxNavigation.unregisterRoutesObserver(routesObserver)
        mapboxNavigation.unregisterRouteProgressObserver(routeProgressObserver)
        mapboxNavigation.unregisterLocationObserver(locationObserver)
        mapboxNavigation.unregisterVoiceInstructionsObserver(voiceInstructionsObserver)
//        mapboxNavigation.unregisterRouteProgressObserver(replayProgressObserver)
    }

    fun onDropViewInstance() {
        this.onDestroy()
    }

    private fun onDestroy() {
        MapboxNavigationProvider.destroy()
        mapboxNavigation.unregisterVoiceInstructionsObserver(voiceInstructionsObserver)
        speechAPI.cancel()
        voiceInstructionsPlayer.shutdown()

//        mapboxReplayer.finish()
//        maneuverApi.cancel()
        routeLineApi.cancel()
        routeLineView.cancel()
    }



    /**
     * ---------------------------------------------------------------------------------------------
     *                                              REACT PROPS
     * ---------------------------------------------------------------------------------------------
     */
    fun setOrigin(origin: Point?) {
        this.origin = origin
    }

    fun setDestination(destination: Point?) {
        this.destination = destination
    }

    fun setShouldSimulateRoute(shouldSimulateRoute: Boolean) {
        this.shouldSimulateRoute = shouldSimulateRoute
    }

    fun setShowsEndOfRouteFeedback(showsEndOfRouteFeedback: Boolean) {
        this.showsEndOfRouteFeedback = showsEndOfRouteFeedback
    }

    fun setMute(mute: Boolean) {

    }
}


