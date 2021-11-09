package com.dji.GSDemo.GoogleMap;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import dji.common.flightcontroller.FlightControllerState;
import dji.common.mission.waypoint.WaypointMissionDownloadEvent;
import dji.common.mission.waypoint.WaypointMissionExecutionEvent;
import dji.common.mission.waypoint.WaypointMissionUploadEvent;
import dji.common.useraccount.UserAccountState;
import dji.common.util.CommonCallbacks;
import dji.sdk.base.BaseProduct;
import dji.sdk.flightcontroller.FlightController;
import dji.common.error.DJIError;
import dji.sdk.mission.MissionControl;
import dji.sdk.mission.timeline.TimelineElement;
import dji.sdk.mission.timeline.TimelineEvent;
import dji.sdk.mission.timeline.actions.AircraftYawAction;
import dji.sdk.mission.timeline.actions.GoToAction;
import dji.sdk.mission.timeline.actions.TakeOffAction;
import dji.sdk.mission.waypoint.WaypointMissionOperatorListener;
import dji.sdk.products.Aircraft;
import dji.sdk.useraccount.UserAccountManager;

public class TimelineActivity extends FragmentActivity implements View.OnClickListener, GoogleMap.OnMapClickListener, OnMapReadyCallback {

    protected static final String TAG = "GSDemoActivity";

    private GoogleMap gMap;

    private Button locate;
    private Button start,stop, cameraView;

    private double droneLocationLat = 181, droneLocationLng = 181;
    private final Map<Integer, Marker> mMarkers = new ConcurrentHashMap<Integer, Marker>();
    private Marker droneMarker = null;

    private FlightController mFlightController;

    private static long startTime = -1;

    // Timeline
    private MissionControl missionControl;
    private TimelineEvent preEvent;
    private TimelineElement preElement;
    private DJIError preError;

    @Override
    protected void onResume(){
        super.onResume();
        initFlightController();
    }

    @Override
    protected void onPause(){
        super.onPause();
    }

    @Override
    protected void onDestroy(){
        unregisterReceiver(mReceiver);
        super.onDestroy();
    }

    private void setResultToToast(final String string){
        TimelineActivity.this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(TimelineActivity.this, string, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void initUI() {

        locate = (Button) findViewById(R.id.locate);
        start = (Button) findViewById(R.id.start);
        stop = (Button) findViewById(R.id.stop);
        cameraView = (Button) findViewById(R.id.cameraView);

        locate.setOnClickListener(this);
        start.setOnClickListener(this);
        stop.setOnClickListener(this);
        cameraView.setOnClickListener(this);

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // When the compile and target version is higher than 22, please request the
        // following permissions at runtime to ensure the
        // SDK work well.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.VIBRATE,
                            Manifest.permission.INTERNET, Manifest.permission.ACCESS_WIFI_STATE,
                            Manifest.permission.WAKE_LOCK, Manifest.permission.ACCESS_COARSE_LOCATION,
                            Manifest.permission.ACCESS_NETWORK_STATE, Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.CHANGE_WIFI_STATE, Manifest.permission.MOUNT_UNMOUNT_FILESYSTEMS,
                            Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.SYSTEM_ALERT_WINDOW,
                            Manifest.permission.READ_PHONE_STATE,
                    }
                    , 1);
        }

        setContentView(R.layout.activity_waypoint1);

        IntentFilter filter = new IntentFilter();
        filter.addAction(DJIDemoApplication.FLAG_CONNECTION_CHANGE);
        registerReceiver(mReceiver, filter);

        initUI();

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
    }

    protected BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            onProductConnectionChange();
        }
    };

    private void onProductConnectionChange()
    {
        initFlightController();
        loginAccount();
    }

    private void loginAccount() {

        UserAccountManager.getInstance().logIntoDJIUserAccount(this,
                new CommonCallbacks.CompletionCallbackWith<UserAccountState>() {
                    @Override
                    public void onSuccess(final UserAccountState userAccountState) {
                        Log.e(TAG, "Login Success");
                    }
                    @Override
                    public void onFailure(DJIError error) {
                        setResultToToast("Login Error:"
                                + error.getDescription());
                    }
                });
    }

    private void initFlightController() {

        BaseProduct product = DJIDemoApplication.getProductInstance();
        if (product != null && product.isConnected()) {
            if (product instanceof Aircraft) {
                mFlightController = ((Aircraft) product).getFlightController();
            }
        }

        if (mFlightController != null) {
            mFlightController.setStateCallback(new FlightControllerState.Callback() {

                @Override
                public void onUpdate(FlightControllerState djiFlightControllerCurrentState) {
                    droneLocationLat = djiFlightControllerCurrentState.getAircraftLocation().getLatitude();
                    droneLocationLng = djiFlightControllerCurrentState.getAircraftLocation().getLongitude();
                    updateDroneLocation(); // update drone location on google map
                }
            });
        }
    }

    private WaypointMissionOperatorListener eventNotificationListener = new WaypointMissionOperatorListener() {
        @Override
        public void onDownloadUpdate(WaypointMissionDownloadEvent downloadEvent) {

        }

        @Override
        public void onUploadUpdate(WaypointMissionUploadEvent uploadEvent) {

        }

        @Override
        public void onExecutionUpdate(WaypointMissionExecutionEvent executionEvent) {

        }

        @Override
        public void onExecutionStart() {

        }

        @Override
        public void onExecutionFinish(@Nullable final DJIError error) {
            setResultToToast("Execution finished: " + (error == null ? "Success!" : error.getDescription()));
        }
    };

    private void setUpMap() {
        gMap.setOnMapClickListener(this); // add the listener for click for a map object
    }

    @Override
    public void onMapClick(LatLng point) {
        // What to do when user clicks the map:
    }

    public static boolean checkGpsCoordination(double latitude, double longitude) {
        return (latitude > -90 && latitude < 90 && longitude > -180 && longitude < 180) && (latitude != 0f && longitude != 0f);
    }

    // Update the drone location based on states from MCU.
    private void updateDroneLocation(){
        Log.d("Status", "Calling updateDroneLocation()");
        LatLng pos = new LatLng(droneLocationLat, droneLocationLng);
        final MarkerOptions markerOptions = new MarkerOptions();
        markerOptions.position(pos);

        if (gMap != null) {
            this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    CameraPosition cam = gMap.getCameraPosition();
                    double mapAngle = cam.bearing;
                    double droneAngle = mFlightController.getCompass().getHeading();

                    // normalize both angles:
                    mapAngle = normalizeAngle(mapAngle);
                    droneAngle = normalizeAngle(droneAngle);

                    double rotationAngle = mapAngle - droneAngle;
                    if (rotationAngle < 0) {
                        rotationAngle = -1 * rotationAngle;
                    } else {
                        rotationAngle = 360 - rotationAngle;
                    }

                    markerOptions.rotation((float) rotationAngle);
                    markerOptions.icon(BitmapDescriptorFactory.fromResource(R.drawable.aircraft));
                    markerOptions.anchor(0.5f, 0.5f); // center the icon

                    // print status:
                    // output altitude and speed:
                    TextView altitudeView = (TextView) (findViewById(R.id.altitudeTextView));
                    String temp = "Altitude: " + mFlightController.getState().getAircraftLocation().getAltitude() + "\n";
                    float vx = mFlightController.getState().getVelocityX();
                    float vy = mFlightController.getState().getVelocityY();
                    float vz = mFlightController.getState().getVelocityZ();
                    double v = Math.sqrt(vx*vx + vy*vy + vz*vz);
                    temp += String.format("Speed: %.2f m/s", v);
                    altitudeView.setText(temp);

                    // output flight time:
                    TextView timerTextView = (TextView) (findViewById(R.id.timerTextView));
                    double elapsedTime = startTime == -1 ? 0 : (System.currentTimeMillis() - startTime) / 1000.0;
                    int minutes = ((int) elapsedTime % 3600) / 60;
                    int seconds = (int) elapsedTime % 60;
                    temp = String.format("Flight Time is %02d:%02d", minutes, seconds);
                    timerTextView.setText(temp);
                }
            });
        }

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (droneMarker != null) {
                    droneMarker.remove();
                }

                if (checkGpsCoordination(droneLocationLat, droneLocationLng)) {
                    droneMarker = gMap.addMarker(markerOptions);
                }
            }
        });
    }

    /**
     * @param angle must be in degrees
     * @return the normalized angle from 0 to 360
     */
    private double normalizeAngle(double angle) {
        angle %= 360;
        if (angle < 0) {
            angle += 360;
        }
        return angle;
    }

    private void markWaypoint(LatLng point){
        //Create MarkerOptions object
        MarkerOptions markerOptions = new MarkerOptions();
        markerOptions.position(point);
        markerOptions.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE));
        Marker marker = gMap.addMarker(markerOptions);
        mMarkers.put(mMarkers.size(), marker);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.locate:{
                updateDroneLocation();
                cameraUpdate(); // Locate the drone's place
                break;
            }
            case R.id.start:{
                startTimelineMission();
                break;
            }
            case R.id.stop:{
                stopTimelineMission();
                break;
            }
            case R.id.cameraView:{
                toggleCameraView();
                break;
            }
            default:
                break;
        }
    }

    private void cameraUpdate(){
        LatLng pos = new LatLng(droneLocationLat, droneLocationLng);
        float zoomlevel = (float) 18.0;
        CameraUpdate cu = CameraUpdateFactory.newLatLngZoom(pos, zoomlevel);
        gMap.moveCamera(cu);

    }

    String nulltoIntegerDefalt(String value){
        if(!isIntValue(value)) value="0";
        return value;
    }

    boolean isIntValue(String val)
    {
        try {
            val=val.replace(" ","");
            Integer.parseInt(val);
        } catch (Exception e) {return false;}
        return true;
    }

    // Timeline:
    private void updateTimelineStatus(@Nullable TimelineElement element, TimelineEvent event, DJIError error) {

        if (element == preElement && event == preEvent && error == preError) {
            return;
        }

        preEvent = event;
        preElement = element;
        preError = error;
    }

    private void startTimelineMission(){
        startTime = System.currentTimeMillis();

        // Timeline:
        List<TimelineElement> elements = new ArrayList<>();
        missionControl = MissionControl.getInstance();
        MissionControl.Listener listener = new MissionControl.Listener() {
            @Override
            public void onEvent(@Nullable TimelineElement element, TimelineEvent event, DJIError error) {
                updateTimelineStatus(element, event, error);
            }
        };
        elements.add(new TakeOffAction());
        elements.add(new GoToAction((float) -1.00));
        elements.add(new AircraftYawAction(-100,false));
        elements.add(new AircraftYawAction(100,false));
        elements.add(new AircraftYawAction(-100,false));
        elements.add(new AircraftYawAction(100,false));
        if (missionControl.scheduledCount() > 0) {
            missionControl.unscheduleEverything();
            missionControl.removeAllListeners();
        }
        missionControl.scheduleElements(elements);
        missionControl.addListener(listener);
        missionControl.startTimeline();
    }

    private void stopTimelineMission() {
        startTime = -1;
        if (missionControl.scheduledCount() > 0) {
            missionControl.unscheduleEverything();
            missionControl.removeAllListeners();
        }
    }

    private void toggleCameraView(){
        startActivity(new Intent(this, CameraActivity.class));
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        if (gMap == null) {
            gMap = googleMap;
            setUpMap();
        }

        //updateDroneLocation();
        //cameraUpdate();

        LatLng loc = new LatLng(33.6846, -117.8265);
        gMap.addMarker(new MarkerOptions().position(loc).title("Marker"));
        gMap.moveCamera(CameraUpdateFactory.newLatLng(loc));
    }

}
