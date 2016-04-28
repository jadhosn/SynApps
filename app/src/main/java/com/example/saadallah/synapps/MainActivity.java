package com.example.saadallah.synapps;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.drawable.ColorDrawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.wifi.WifiManager;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Date;

public class MainActivity extends AppCompatActivity implements WifiP2pManager.ChannelListener, DeviceActionListener {

    private android.support.v7.app.ActionBar bar; //ActionBar-Drawer
    private ActionBarDrawerToggle toggle; //ActionBar-Drawer
    private DrawerLayout drawer; //ActionBar-Drawer

    // WiFi p2p stuff
    private WifiManager wifiManager;
    private WifiP2pManager mManager;
    private WifiP2pManager.Channel mChannel;
    private WiFiDirectBroadcastReceiver mReceiver;
    private final IntentFilter p2pIntent = new IntentFilter();

    // Database helper
    DatabaseHelper myDb = new DatabaseHelper(this);

    // Bluetooth stuff
    final BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

    //Cellular Network
    TelephonyManager teleMan;
    String phoneNumber;

    // Discovered Device List
    private String[] peersMacArrayStr;
    private String[] peersNameArrayStr;
    private ArrayList<WifiP2pDevice> PeerNames;

    // Time discovered = connected Device List
    private Date[] timeDiscovered;

    // Location
    //LocationManager mLocationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

    // popup name
    boolean popupNameButtonFlag = false; // flag on click
    String DeviceNameFromUser = "";

    // flags
    boolean running = true;

    // Device Mac


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //--------------------------------------------------------------------------------
        // drawer stuff! To copy paste on each activity...
        drawer = (DrawerLayout) findViewById(R.id.drawer_layout);

        bar = this.getSupportActionBar();
        bar.setDisplayHomeAsUpEnabled(true);
        bar.setHomeButtonEnabled(true);
        bar.setBackgroundDrawable(new ColorDrawable(getResources().getColor(R.color.myBlue)));

        toggle = new ActionBarDrawerToggle(this, drawer, 0, 0) {
            @Override
            public void onDrawerOpened(View drawerView) {
                super.onDrawerOpened(drawerView);
            }

            @Override
            public void onDrawerClosed(View drawerView) {
                super.onDrawerClosed(drawerView);
            }
        };

        toggle.syncState();
        drawer.setDrawerListener(toggle);

        //------------------------------------------------------------------------------------
        // setting the toggle button in drawer

        //Wifi

        Switch wifiSwitch = (Switch) findViewById(R.id.wifi_switch);
        wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);

        if (wifiManager.isWifiEnabled()) // checks is Wifi is ON or OFF and sets the initial value of the toggle
            wifiSwitch.setChecked(true);
        else
            wifiSwitch.setChecked(false);

        wifiSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() { // switches wifi
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) { // Enable/Disable wifi when switch event
                if (isChecked) {
                    wifiManager.setWifiEnabled(true);
                    Log.d("wifiIsEnabled=", "true");
                } else {
                    wifiManager.setWifiEnabled(false);
                    Log.d("wifiIsEnabled=", "false");
                }
            }
        });

        //Bluetooth
        Switch bluetoothSwitch = (Switch) findViewById(R.id.bluetooth_switch);

        if (mBluetoothAdapter == null) {
            // Device does not support Bluetooth, popup here??
        }

        if (mBluetoothAdapter.isEnabled()) // checks is Bluetooth is ON or OFF and sets the initial value of the toggle
            bluetoothSwitch.setChecked(true);
        else
            bluetoothSwitch.setChecked(false);

        bluetoothSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() { // switches wifi
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) { // Enable/Disable wifi when switch event
                if (isChecked) {
                    mBluetoothAdapter.enable();
                    Log.d("bluetoothIsEnabled=", "true");
                } else {
                    mBluetoothAdapter.disable();
                    Log.d("bluetoothIsEnabled=", "false");
                }
            }
        });

        //Location


        GPSTracker myLocationListener = new GPSTracker(this);
        Location myLocation = myLocationListener.getLocation();

        if(myLocation != null) {
            Log.d("location", "Longitude=" + myLocation.getLongitude());
            Log.d("location", "Latitude=" + myLocation.getLatitude());
        }
        else {
            Log.d("location", "No location available");
        }

        // Device Mac

        //----------------------------------------------------------------------------------
        //Cellular Network
//        teleMan =(TelephonyManager)getSystemService(Context.TELEPHONY_SERVICE);
//        phoneNumber = teleMan.getSimSerialNumber(); // get the phone number

        //-----------------------------------------------------------------------------------
        // WiFi p2p status checking

        p2pIntent.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        p2pIntent.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        p2pIntent.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        p2pIntent.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);


        mManager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        mChannel = mManager.initialize(this, getMainLooper(), null);
        mReceiver = new WiFiDirectBroadcastReceiver(mManager, mChannel, this);

        //-------------------------------------------------------------------------------------------------------------
        // WiFi p2p discovering peers and connecting to them

        Runnable myRunnable = new Runnable() {

            @Override
            public void run() {

                // discovering peers
                while (running) {

                    mManager.discoverPeers(mChannel, new WifiP2pManager.ActionListener() { // starts discovering peers
                        @Override
                        public void onSuccess() {
                            Log.d("p2p Notification", "Starting Discovery");
                            Toast.makeText(getApplicationContext(), "Starting Discovery", Toast.LENGTH_SHORT).show();
                        }

                        @Override
                        public void onFailure(int reason) {
                            Toast.makeText(MainActivity.this, "Could not initiate peer discovery", Toast.LENGTH_SHORT).show();
                        }
                    });


                    synchronized (this) {
                        try {
                            wait(5000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }

                    // Thread calling connect
                    Log.d("Thread", "entered");

                    boolean deviceDetectedFlag = mReceiver.isBroadcastFlag(); // THIS IS ZE FLAG... HAPPY NOW??

                    myDb.resetFlags();
                    PeerNames = mReceiver.getPeerNames(); // Now that we have a list of peers, we try to connect to each of them
                    //Up here, we are feeling the MAC array string: the thread takes MAC address from devices that are discovered
                    //It only discovers devices
                    peersMacArrayStr = new String[PeerNames.size()];
                    timeDiscovered = new java.util.Date[PeerNames.size()];

                    int peersnamesize = PeerNames.size();
                    for (int i = 0; i < peersnamesize; i++) {
                        //saves the time at which the device got connected/discovered
                        timeDiscovered[i] = new java.util.Date();
                        long Detection_time = System.currentTimeMillis();


                        // retrieve MAC Address of device i
                        WifiP2pDevice targetDevice = PeerNames.get(i);
                        peersMacArrayStr[i] = targetDevice.deviceAddress;


                        // removing the columns from the strings in MAC addresses
                        String[] macAddressParts = peersMacArrayStr[i].split(":");
                        peersMacArrayStr[i] = macAddressParts[0] + macAddressParts[1] + macAddressParts[2] + macAddressParts[3] + macAddressParts[4] + macAddressParts[5];


                        //Checking if its a new device
                        Cursor result = myDb.getDatabyMAC(peersMacArrayStr[i]);

                        if (result.getCount() == 0) {
                            Log.d("Device=", "New");

                            String descriptionNamePopup = null;

                            openDescriptionNamePopup(peersMacArrayStr[i], targetDevice.deviceName);

                            // NEED TO INSERT A WHILE LOOP AROUND HERE!!!!!

                            while (!popupNameButtonFlag) ; // wait until a button is pressed

                            Log.d("popup", "New device detected called: " + descriptionNamePopup);

                            if (DeviceNameFromUser == "") {
                                DeviceNameFromUser = targetDevice.deviceName;
                            }

                            myDb.insertData(peersMacArrayStr[i], Detection_time, Detection_time, 0, 1, 0, "No#yet", targetDevice.deviceName, 1);
                            myDb.updateDescriptionName(peersMacArrayStr[i], DeviceNameFromUser);// to connect to pop up function

                        } else if (result.getCount() == 1)   //Its an old device:  if the MAC appears here, it means that its still connected
                        {
                            Log.d("Device=", "old");
                            String detected_frequency = "";
                            String fetched_lt = "";
                            String fetched_cumulative = "";

                            myDb.updateExistsStatus(peersMacArrayStr[i], 1);

                            Cursor result_lt_init = myDb.getlttimeinit(peersMacArrayStr[i]); //fetching the old time stamp stored already in the db

                            if (result_lt_init != null && result_lt_init.getCount() > 0) {
                                result_lt_init.moveToFirst();
                                fetched_lt = result_lt_init.getString(0);
                            }
                            long fetched_lt_init_long = Long.valueOf(fetched_lt);
                            long lt_range = Detection_time - fetched_lt_init_long;

//---------------------------------------Updated 17/4/2016

                            if(lt_range>=50000)         // update the frequency of detection only if its an old device reconnecting
                            {
                                Log.d("Entered", "Frequency Update");
                                Log.d("popup", "lt_range: " + lt_range);
                                Cursor result_Detection_Frequency = myDb.getDetectionFrequency(peersMacArrayStr[i]);

                                if (result_Detection_Frequency != null && result_Detection_Frequency.getCount() > 0) {
                                    result_Detection_Frequency.moveToFirst();
                                    detected_frequency = result_Detection_Frequency.getString(0);
                                }
                                int detected_frequency_int = 0;
                                detected_frequency_int = Integer.parseInt(detected_frequency);

                                myDb.updateDetectionFrequency(peersMacArrayStr[i], detected_frequency_int);
                            }

                            //---------------------------------------

                            myDb.update_lt_detection_lt_range(peersMacArrayStr[i], Detection_time, lt_range);// anyways update the ltrange since it always must be up to date

                            Cursor result_cum_result = myDb.getCumulativeDuration(peersMacArrayStr[i]);

                            if (result_cum_result != null && result_cum_result.getCount() > 0) {
                                result_cum_result.moveToFirst();
                                fetched_cumulative = result_cum_result.getString(0);
                            }
                            long fetched_cumulative_long = Long.valueOf(fetched_cumulative);
                            myDb.updateCumulativeDetectionDuration(peersMacArrayStr[i], lt_range, fetched_cumulative_long);

                        }
                        // connect to all the devices
                        // connect(i);

                        popupNameButtonFlag = false; // resetting the flags
                        DeviceNameFromUser = "";
                    }

                    PeerNames.clear();

                    mReceiver.setBroadcastFlag(false);
                    Log.d("device detected flag", "false");

                    //                   while(!mReceiver.isBroadcastFlag()){} // waits for the flag Broadcast flag to turn true
                }
            }
        };

        Thread myThread = new Thread(myRunnable);
        myThread.start();

        //------------------------------------------------------------------------------------------


    }



    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        if (!drawer.isDrawerOpen(Gravity.LEFT)){
            drawer.openDrawer(Gravity.LEFT);
        }
        else{
            drawer.closeDrawer(Gravity.LEFT);
        }

        int id = item.getItemId();

        if (id == R.id.action_connectivity_state) {

            Intent connectivityStateIntent = new Intent(MainActivity.this, Connectivity_State.class);
            connectivityStateIntent.putExtra("bluetooth_state", mBluetoothAdapter.isEnabled());
            connectivityStateIntent.putExtra("wifi_state", wifiManager.isWifiEnabled());
            connectivityStateIntent.putExtra("network_type", teleMan.getNetworkType());
            connectivityStateIntent.putExtra("phone_number", phoneNumber); // attach the phone number to the intent
            startActivity(connectivityStateIntent);
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mReceiver, p2pIntent);

    }
    /* unregister the broadcast receiver */
    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mReceiver);
    }

    public void onClickClearData(View view) { //Don't forget to implement this method!
    }

    public void onGenerateGraphClick(View view) { //Don't forget to implement this method!

        Graph myDevicesGraph = new Graph();
    }

    public void onNetworkDetailsClick(View view) {
        Intent networkDetailsIntent = new Intent(MainActivity.this, NetworkDetails.class);
        networkDetailsIntent.putExtra("MacArray", peersMacArrayStr);
        startActivity(networkDetailsIntent);
    }

    public void onClickYes(View view) { //Don't forget to implement this method!
    }

    public void onClickNo(View view) { //Don't forget to implement this method!

    }

    public void onConnectivityClick(View view) {
        Intent connectivityStateIntent = new Intent(MainActivity.this, Connectivity_State.class);
        connectivityStateIntent.putExtra("bluetooth_state", mBluetoothAdapter.isEnabled());
        connectivityStateIntent.putExtra("wifi_state", wifiManager.isWifiEnabled());
        //       connectivityStateIntent.putExtra("network_type", teleMan.getNetworkType());
        connectivityStateIntent.putExtra("phone_number", phoneNumber); // attach the phone number to the intent
        startActivity(connectivityStateIntent);
    }

    public void openDescriptionNamePopup(String MAC, String defaultDeviceName){

        class myRunnableClass implements Runnable{
            String MAC;
            String defaultDeviceName;
            String DeviceName;

            public myRunnableClass(String MAC, String defaultDeviceName){
                this.MAC = MAC;
                this.defaultDeviceName = defaultDeviceName;
            }

            public String getDeviceName(){
                return DeviceName;
            }

            public void run() {
                final RelativeLayout popupName = (RelativeLayout) findViewById(R.id.popup_name);
                TextView MacAddressValuePopup = (TextView) findViewById(R.id.mac_address_value);
                final EditText popupEditText = (EditText) findViewById(R.id.popup_editText);
                Button popupOkButton = (Button) findViewById(R.id.popup_ok_button);
                Button popupIgnoreButton = (Button) findViewById(R.id.popup_ignore_button);

                String MacFormatted = MAC.substring(0,2) + ":" + MAC.substring(2,4) + ":" + MAC.substring(4,6) + ":" + MAC.substring(6,8) + ":" +
                        MAC.substring(8,10) + ":" + MAC.substring(10, 12); // puts back the MAC address into a standardized form

                MacAddressValuePopup.setText(MacFormatted);
                popupEditText.setText(defaultDeviceName);

                popupName.setVisibility(View.VISIBLE);

                // OK button click listener
                View.OnClickListener onOKClickListener = new View.OnClickListener() {

                    @Override
                    public void onClick(View v) {

                        DeviceNameFromUser = String.valueOf(popupEditText.getText());
                        popupName.setVisibility(View.GONE);
                        popupNameButtonFlag = true;
                    }
                };
                popupOkButton.setOnClickListener(onOKClickListener);

                // Ignore button click listener

                View.OnClickListener onIgnoreClickListener = new View.OnClickListener() {

                    @Override
                    public void onClick(View v) {

                        DeviceNameFromUser = defaultDeviceName;
                        popupName.setVisibility(View.GONE);
                        popupNameButtonFlag = true;
                    }
                };
                popupIgnoreButton.setOnClickListener(onIgnoreClickListener);

            } // end run
        } // end class




        myRunnableClass myUIRunnable = new myRunnableClass(MAC, defaultDeviceName);
        runOnUiThread(myUIRunnable);
    }

    @Override
    public void connect(int deviceIndex) {
        // Picking the first device found on the network.
        WifiP2pDevice device = PeerNames.get(deviceIndex);

        WifiP2pConfig config = new WifiP2pConfig();
        config.deviceAddress = device.deviceAddress;
        config.wps.setup = WpsInfo.PBC;

        mManager.connect(mChannel, config, new WifiP2pManager.ActionListener() {

            @Override
            public void onSuccess() {
                Toast.makeText(getApplicationContext(), "Connection succeeded!", Toast.LENGTH_SHORT).show();

            }

            @Override
            public void onFailure(int reason) {
                Toast.makeText(getApplicationContext(), "Connection failed. Retry.",
                        Toast.LENGTH_SHORT).show();
            }
        });
    }


    @Override
    public void onChannelDisconnected() {
        // we will try once more

        if (mManager != null) {
            Toast.makeText(getApplicationContext(), "Channel lost. Trying again", Toast.LENGTH_SHORT).show();
            mManager.initialize(this, getMainLooper(), this);
        } else {
            Toast.makeText(getApplicationContext(), "Severe! Channel is probably lost permanently. Try Disable/Re-Enable P2P.", Toast.LENGTH_SHORT).show();

        }
    }

    @Override
    public void disconnect(WifiP2pDevice device) {
        WifiP2pManager.ActionListener actionListener = new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Toast.makeText(getApplicationContext(), "Disconnected Successfully.", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onFailure(int reason) {
                Toast.makeText(getApplicationContext(), "Error Disconnecting", Toast.LENGTH_SHORT).show();
            }
        };
    }

    @Override
    public void disconnect() {
        mManager.removeGroup(mChannel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Toast.makeText(getApplicationContext(), "Disconnected Successfully.", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onFailure(int reason) {
                Toast.makeText(getApplicationContext(), "Error Disconnecting", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        running = false;
    }
}