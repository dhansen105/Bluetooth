package com.daniel.bluetooth;

import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Bundle;
import android.app.Activity;
import android.bluetooth.BluetoothDevice;

import java.util.ArrayList;

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

public class MainActivity extends Activity implements BluetoothConnection.BluetoothScanListener, BluetoothConnection.BluetoothConnectListener {

    //CONSTANTS
    private static final String BLUETOOTH_UUID = "ba287a8e-41df-44f8-b2b8-e7b4d9938deb";
    public final int STATUS_UNSUPPORTED = -1;
    public final int STATUS_SUPPORTED = 0;
    public final int STATUS_ON = 1;
    public final int STATUS_OFF = 2;
    public final int STATUS_SCANNING = 3;
    public final int STATUS_CONNECTING = 4;
    public final int STATUS_CONNECTED = 5;

    //UI ELEMENT MEMBERS
    private LinearLayout _ll_data_view;
    private LinearLayout _ll_main_view;
    private BluetoothConnection _btConn;
    private TextView _tv_status;
    private Switch _switch_bluetooth;
    private Button _btn_scan_devices;
    private Button _btn_paired_devices;
    private EditText _et_send_data;
    private Button _btn_send_data;
    private ListView _lv_devices;
    private ListView _lv_data;
    private ArrayAdapter<String> _btArrayAdapter;
    private ArrayAdapter<String> _dataArrayAdapter;

    //MEMBERS
    private ArrayList<BluetoothDevice> _devices;
    private BluetoothConnection.BluetoothDataConnection _btDataConn;


    @Override /** ACTIVITY ON CREATE METHOD */
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        _devices = new ArrayList<>();
        _btConn = new BluetoothConnection(this, BLUETOOTH_UUID);

        //link ui with elements
        linkUi();

        //if bluetooth is not supported
        int status = _btConn.getStatus();
        if(status ==  STATUS_UNSUPPORTED) {
            //disable all buttons and update status
            _switch_bluetooth.setEnabled(false);
            _btn_paired_devices.setEnabled(false);
            _btn_scan_devices.setEnabled(false);
            sendToast("Your device does not support Bluetooth");
            updateStatus(STATUS_UNSUPPORTED);
        } else {//if bluetooth is supported
            //update switch and status with bt adapter status
            updateStatus(STATUS_SUPPORTED);

            //update status and switch if bluetooth already on
            if(_btConn.getStatus() == STATUS_ON) {
                updateStatus(STATUS_ON);
                _switch_bluetooth.setChecked(true);
            }

            //create the arrayAdapter that contains the BTDevices, and set it to the ListView
            _btArrayAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);
            _lv_devices.setAdapter(_btArrayAdapter);

            _dataArrayAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);
            _lv_data.setAdapter(_dataArrayAdapter);
        }
    }


    /** LINKS THE UI ELEMENTS TO THE MEMBERS IN MAIN ACTIVITY */
    private void linkUi() {
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

        //link listeners
        _switch_bluetooth.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                if (b) {
                    _btConn.bluetoothOn();
                    updateStatus(STATUS_ON);
                } else {
                    _btConn.bluetoothOff();
                    updateStatus(STATUS_OFF);
                }
            }
        });

        final BluetoothConnection.BluetoothConnectListener bcl = this;
        _lv_devices.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                _btConn.connectToDevice(_devices.get(i), bcl);
            }
        });
    }


    /** DISPLAYS PAIRED DEVICES FROM BLUETOOTH WHEN PAIR BUTTON CLICKED */
    public void onPairButtonClick(View view) {
        _btArrayAdapter.clear();
        _devices.clear();

        ArrayList<BluetoothDevice> devices = _btConn.getPairedDeviceList();
        for(BluetoothDevice bd : devices) {
            _btArrayAdapter.add(bd.getName() + "\n" + bd.getAddress());
            _devices.add(bd);
        }

        _btArrayAdapter.notifyDataSetChanged();
    }


    /** STARTS SCANNING FOR DISCOVERABLE DEVICES CALLS UPDATE DEVICE LIST WHEN ITEMS ARE FOUND
     *   CALLED WHEN SCAN BUTTON IS CLICKED */
    public void onScanButtonClick(View view) {
        if (_btConn.getStatus() == STATUS_SCANNING) {
            _btConn.cancelScanForDevices();
            updateStatus(STATUS_ON);
            _btn_scan_devices.setText("SCAN FOR DEVICES");
        } else {
            _btArrayAdapter.clear();
            _btConn.scanForDevices(this);
            updateStatus(STATUS_SCANNING);
            _btn_scan_devices.setText("CANCEL SCAN FOR DEVICES");
        }
    }


    public void onSendButtonClick(View view) {
        String send = _et_send_data.getText().toString();
        _dataArrayAdapter.add("--> " + send);
        _dataArrayAdapter.notifyDataSetChanged();
        _btDataConn.write(send);
    }


    /** DISPLAYS A TOAST NOTIFICATION ON TOP OF UI */
    private void sendToast(String msg) {
        Toast t = Toast.makeText(this, msg, Toast.LENGTH_SHORT);
        t.show();
    }


    /** UPDATES THE STATUS TEXT VIEW */
    private void updateStatus(int status) {
        String s = "";

        switch (status) {
            case STATUS_ON:
                s = "ADAPTER ON"; break;
            case STATUS_OFF:
                s = "ADAPTER OFF"; break;
            case STATUS_UNSUPPORTED:
                s = "UNSUPPORTED"; break;
            case STATUS_SUPPORTED:
                s = "SUPPORTED"; break;
            case STATUS_CONNECTING:
                s = "CONNECTING"; break;
            case STATUS_CONNECTED:
                s = "CONNECTED"; break;
            case STATUS_SCANNING:
                s = "SCANNING"; break;
        }

        _tv_status.setText("STATUS: " + s);
    }


    @Override /** CALLED ONCE CONNECTION IS MADE OR UNABLE TO CONNECT */
    public void updateConnectionInfo(int connection, BluetoothSocket btSocket) {
        updateStatus(connection);

        if(connection == STATUS_CONNECTED)
            setupDataTransfer(btSocket);
    }

    @Override /** CALLED ONCE SCAN DEVICE LIST HAS BEEN UPDATED */
    public void updateDeviceList(ArrayList<BluetoothDevice> devices) {
        _devices = devices;
        _btArrayAdapter.clear();
        for(BluetoothDevice bd : _devices)
            _btArrayAdapter.add(bd.getName() + "\n" + bd.getAddress());
        _btArrayAdapter.notifyDataSetChanged();
    }


    private void setupDataTransfer(BluetoothSocket btSocket) {
        _ll_data_view.setVisibility(View.VISIBLE);
        _ll_main_view.setVisibility(View.GONE);
        _btDataConn = _btConn.setupDataConnection(btSocket);
        _btDataConn.start();
    }


    public void dataReceived(String s) {
        _dataArrayAdapter.add("<-- " + s);
        _dataArrayAdapter.notifyDataSetChanged();
    }
}