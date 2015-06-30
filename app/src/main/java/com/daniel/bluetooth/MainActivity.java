package com.daniel.bluetooth;

import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Bundle;
import android.app.Activity;
import android.bluetooth.BluetoothDevice;

import java.util.ArrayList;
import java.util.UUID;

import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity implements BluetoothConnection.BluetoothListener {

    //CONSTANTS
    private static final String BLUETOOTH_UUID = "ba287a8e-41df-44f8-b2b8-e7b4d9938deb";
    private static final int SEND_UI = 0;
    private static final int MAIN_UI = 1;
    private static final int DISABLED_UI = 2;


    //UI ELEMENT MEMBERS
    private LinearLayout _ll_data_view;
    private LinearLayout _ll_main_view;
    private TextView _tv_status;
    private Switch _switch_bluetooth;
    private Button _btn_scan_devices;
    private Button _btn_paired_devices;
    private EditText _et_send_data;
    private Button _btn_send_data;
    private ListView _lv_devices;
    private ListView _lv_data;
    private int _ui_state;

    //MEMBERS
    private BluetoothConnection _btConn;
    private ArrayList<BluetoothDevice> _devices;
    private ArrayAdapter<String> _btArrayAdapter;
    private ArrayAdapter<String> _dataArrayAdapter;


    @Override /** ACTIVITY ON CREATE METHOD */
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //link ui with elements
        linkUi();

        //create bluetooth connection
        _devices = new ArrayList<>();
        _btConn = new BluetoothConnection(BLUETOOTH_UUID, this);
        updateBluetoothState(_btConn.getState());

        //initialize the arrayAdapters that contains the devices and messages
        _dataArrayAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);
        _btArrayAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);
        _lv_devices.setAdapter(_btArrayAdapter);
        _lv_data.setAdapter(_dataArrayAdapter);
    }


    /** LINKS THE UI ELEMENTS TO THE MEMBERS IN MAIN ACTIVITY */
    private void linkUi() {
        _ui_state = MAIN_UI;

        //link ui to members
        _ll_main_view = (LinearLayout)findViewById(R.id.ll_main_view);
        _ll_data_view = (LinearLayout)findViewById(R.id.ll_data_view);
        _btn_send_data = (Button) findViewById(R.id.btn_send_data);
        _et_send_data = (EditText)findViewById(R.id.et_send_data);
        _switch_bluetooth = (Switch)findViewById(R.id.switch_bluetooth);
        _btn_paired_devices = (Button)findViewById(R.id.btn_paired_devices);
        _btn_scan_devices = (Button)findViewById(R.id.btn_scan_devices);
        _tv_status = (TextView)findViewById(R.id.tv_status);
        _lv_devices = (ListView)findViewById(R.id.lv_devices);
        _lv_data = (ListView)findViewById(R.id.lv_data);

        //link bluetooth switch to event
        _switch_bluetooth.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                if (b)
                    _btConn.bluetoothOn();
                else
                    _btConn.bluetoothOff();
            }
        });

        //link list view to connection event on chosen adapter
        _lv_devices.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                if(_btConn.getState() == BluetoothConnection.SCANNING)
                    onScanButtonClick(null);
                _btConn.connectToDevice(_devices.get(i));
            }
        });
    }


    /** DISPLAYS PAIRED DEVICES FROM BLUETOOTH WHEN PAIR BUTTON CLICKED */
    public void onPairButtonClick(View view) {
        _btArrayAdapter.clear();
        _devices.clear();

        ArrayList<BluetoothDevice> devices = _btConn.getPairedDeviceList();
        if(devices == null)
            return;

        for(BluetoothDevice bd : devices) {
            _btArrayAdapter.add(bd.getName() + "\n" + bd.getAddress());
            _devices.add(bd);
        }

        _btArrayAdapter.notifyDataSetChanged();
    }


    /** STARTS SCANNING FOR DISCOVERABLE DEVICES CALLS UPDATE DEVICE LIST WHEN ITEMS ARE FOUND
     *   CALLED WHEN SCAN BUTTON IS CLICKED */
    public void onScanButtonClick(View view) {
        //if not at idle return or cancel scan if already scanning
        if(_btConn.getState() != BluetoothConnection.IDLE) {
            if (_btConn.getState() == BluetoothConnection.SCANNING)
                _btConn.cancelScanForDevices();

            return;
        }

        //clear devices and scan for new ones
        _devices.clear();
        _btArrayAdapter.clear();
        _btConn.scanForDevices();
    }


    /** CALLED WHEN DEVICE CONNECTED AND SEND BUTTON IS ACTIVE AND CLICKED */
    public void onSendButtonClick(View view) {
        //cannot send if not connected
        if(_btConn.getState() != BluetoothConnection.CONNECTED)
            return;

        String send = _et_send_data.getText().toString();
        //cannot send nothing
        if(send.isEmpty()){
            sendToast("Cannot Send Nothing");
            return;
        }

        //add sent data to list view
        _dataArrayAdapter.add("--> " + send);
        _dataArrayAdapter.notifyDataSetChanged();

        //send the data
        _btConn.sendData(send);
    }


    @Override /** CALLED WHEN THE BLUETOOTH CONNECTION RECEIVES DATA */
    public void dataReceived(String data) {
        if(_ui_state != SEND_UI)
            return;

        _dataArrayAdapter.add("<-- " + data);
        _btArrayAdapter.notifyDataSetChanged();
    }


    @Override /** CALLED ONCE SCAN DEVICE LIST HAS BEEN UPDATED */
    public void updateDeviceList(ArrayList<BluetoothDevice> devices) {
        _devices = devices;

        //clear list view and reconstruct with new device list
        _btArrayAdapter.clear();
        for(BluetoothDevice bd : _devices)
            _btArrayAdapter.add(bd.getName() + "\n" + bd.getAddress());

        //display changes
        _btArrayAdapter.notifyDataSetChanged();
    }

    @Override
    public void updateBluetoothState(int connection) {
        String s = "";

        switch (connection) {
            case BluetoothConnection.UNSUPPORTED:
                s = "ADAPTER NOT FOUND";
                changeUiState(DISABLED_UI);
                break;
            case BluetoothConnection.IDLE:
                s = "IDLE";
                _btn_scan_devices.setText("SCAN FOR DEVICES");
                if(_ui_state != MAIN_UI)
                    changeUiState(MAIN_UI);
                _switch_bluetooth.setChecked(true);
                break;
            case BluetoothConnection.OFF:
                _switch_bluetooth.setChecked(false);
                s = "OFF";
                break;
            case BluetoothConnection.SCANNING:
                _btn_scan_devices.setText("CANCEL SCAN");
                s = "SCANNING";
                break;
            case BluetoothConnection.CONNECTING:
                s = "CONNECTING";
                break;
            case BluetoothConnection.CONNECTED:
                if(_ui_state != SEND_UI)
                    changeUiState(SEND_UI);
                s = "CONNECTED";
                break;
        }

        _tv_status.setText("STATUS: " + s);
    }


    /** DISABLES THE BUTTONS IN THE UI */
    private void changeUiState(int state) {
        _ui_state = state;

        switch (state) {
            case MAIN_UI:
                _ll_data_view.setVisibility(View.GONE);
                _ll_main_view.setVisibility(View.VISIBLE);
                _btn_paired_devices.setEnabled(true);
                _btn_scan_devices.setEnabled(true);
                _switch_bluetooth.setEnabled(true);
                _btArrayAdapter.clear();
                _btArrayAdapter.notifyDataSetChanged();
                break;
            case DISABLED_UI:
                _btn_paired_devices.setEnabled(false);
                _btn_scan_devices.setEnabled(false);
                _switch_bluetooth.setEnabled(false);

                break;
            case SEND_UI:
                _ll_data_view.setVisibility(View.VISIBLE);
                _ll_main_view.setVisibility(View.GONE);
                _dataArrayAdapter.clear();
                _dataArrayAdapter.notifyDataSetChanged();
                break;
        }
    }


    /** DISPLAYS A TOAST NOTIFICATION ON TOP OF UI */
    private void sendToast(String msg) {
        Toast t = Toast.makeText(this, msg, Toast.LENGTH_SHORT);
        t.show();
    }
}