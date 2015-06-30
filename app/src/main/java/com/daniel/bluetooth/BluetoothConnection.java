package com.daniel.bluetooth;


import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.UUID;


public class BluetoothConnection {
    //CONSTANTS
    public static final int UNSUPPORTED = -1;
    public static final int SUPPORTED = 0;
    public static final int IDLE = 1;
    public static final int OFF = 2;
    public static final int SCANNING = 3;
    public static final int CONNECTING = 4;
    public static final int CONNECTED = 5;


    //MEMBERS
    private UUID _uuid;
    private int _state;
    private Context _context;
    private BluetoothSocket _btSocket;
    private BluetoothAdapter _btAdapter;
    private ArrayList<BluetoothDevice> _scannedDevices;
    private BluetoothListener _btListener;
    private BluetoothDevice _selectedDevice;
    private BluetoothStreamConnection _btStream;


    /** CONSTRUCTOR FOR BLUETOOTH CONNECTION
     * @param btListener listener that implements interface
     * @param uuid uuid string */
    public BluetoothConnection(String uuid, BluetoothListener btListener) {
        _context = (Context)btListener;
        _uuid = UUID.fromString(uuid);
        _scannedDevices = new ArrayList<>();
        _btAdapter = BluetoothAdapter.getDefaultAdapter();
        _selectedDevice = null;
        _btListener = btListener;
        _btStream = null;
        _btSocket = null;

        //update state of bluetooth without notifying ui because
        // ui still doesn't have reference to this object.
        if (_btAdapter == null) //bluetooth adapter doesn't exist
            _state = UNSUPPORTED;
        else {
            //adapter already active
            if(_btAdapter.isEnabled())
                _state = IDLE;
            else//adapter off
                _state = OFF;
        }
    }


    /** TURNS ON THE BLUETOOTH ADAPTER */
    public void bluetoothOn() {
        //cannot do anything unless current state is off
        if(_state != OFF)
            return;

        _btAdapter.enable();
        updateState(IDLE);
    }


    /** TURNS OFF THE BLUETOOTH ADAPTER */
    public void bluetoothOff() {
        //can only turn off bluetooth if in idle state
        if(_state != IDLE)
            return;

        _btAdapter.disable();
        updateState(OFF);
    }


    /** RETURNS AN ARRAY LIST OF PAIRED DEVICES*/
    public ArrayList<BluetoothDevice> getPairedDeviceList() {
        //can only give list of paired devices if in idle state
        if(_state == IDLE)
            return new ArrayList<>(_btAdapter.getBondedDevices());

        return null;
    }


    /** SCANS FOR DISCOVERABLE DEVICES AND UPDATES */
    public void scanForDevices() {
        //can only scan for devices if in idle state
        if(_state == IDLE) {
            _scannedDevices.clear();
            _btAdapter.startDiscovery();
            _context.registerReceiver(_broadcastReceiver, new IntentFilter(BluetoothDevice.ACTION_FOUND));
            updateState(SCANNING);
        }
    }


    /** CANCELS SCANNING FOR DISCOVERABLE DEVICES */
    public void cancelScanForDevices() {
        if(_state != SCANNING)
            return;

        if(_btAdapter.isDiscovering()) {
            _context.unregisterReceiver(_broadcastReceiver);
            _btAdapter.cancelDiscovery();
            updateState(IDLE);
        }
    }


    /** ATTEMPTS CONNECTION WITH THE GIVEN BLUETOOTH DEVICE */
    public void connectToDevice(BluetoothDevice device) {
        //attempting connection can only be done from idle state
        if(_state != IDLE)
            return;

        updateState(CONNECTING);
        _selectedDevice = device;

        //start connection attempt thread
        BluetoothConnectionAttempt connect = new BluetoothConnectionAttempt();
        connect.start();
    }


    /** ATTEMPTS CONNECTION WITH THE GIVEN BLUETOOTH DEVICE */
    private void setupStreamConnection(BluetoothSocket btSocket) {
        _btSocket = btSocket;
        _btStream =  new BluetoothStreamConnection();
        _btStream.start();
    }


    /** CLASS THAT HANDLES CREATING A BLUETOOTH CONNECTION IN A BACKGROUND THREAD */
    public class BluetoothConnectionAttempt extends Thread{
        public void run() {
            BluetoothSocket btSocket = null;

            //discovering will slow down connection
            if(_btAdapter.isDiscovering())
                _btAdapter.cancelDiscovery();

            try {
                //attempt to connect and create stream connection
                btSocket = _selectedDevice.createRfcommSocketToServiceRecord(_uuid);
                btSocket.connect();
                setupStreamConnection(btSocket);

                //update state in main thread
                ((MainActivity) _context).runOnUiThread(new Runnable() {
                    public void run() {
                        updateState(CONNECTED);
                    }
                });

                //successfully connected
                return;

            } catch (IOException e1) {
                _state = IDLE;

                try {
                    //attempt to connect and create stream connection
                    btSocket = (BluetoothSocket) _selectedDevice.getClass().getMethod("createRfcommSocket", new Class[]{int.class}).invoke(_selectedDevice, 1);
                    btSocket.connect();
                    setupStreamConnection(btSocket);

                    //update state in main thread
                    ((MainActivity) _context).runOnUiThread(new Runnable() {
                        public void run() {
                            updateState(CONNECTED);
                        }
                    });

                    //successfully connected
                    return;
                } catch (Exception e2) {
                    closeSocket(btSocket);
                }
            }

            closeSocket(btSocket);

            //update state in main thread
            ((MainActivity) _context).runOnUiThread(new Runnable() {
                public void run() {
                    _btListener.updateBluetoothState(IDLE);
                }
            });

            _btSocket = null;
        }
    }


    public class BluetoothStreamConnection extends Thread{
        InputStream _iStream;
        OutputStream _oStream;

        public BluetoothStreamConnection() {
            try {
                _iStream = _btSocket.getInputStream();
                _oStream = _btSocket.getOutputStream();
            } catch (IOException e) {
                closeSocket(_btSocket);
                _btSocket = null;
                updateState(IDLE);
            }
        }

        public void run() {
            // Keep listening to the InputStream until an exception occurs
            while (true) {
                try {
                    byte[] packet = new byte[20];

                    // Read from the InputStream
                    int bytes = _iStream.read(packet);
                    if(bytes > 0) {
                        //convert to string
                        final String x = Hex.hexToString(packet);

                        // Send the obtained bytes to the UI activity
                        ((MainActivity) _context).runOnUiThread(new Runnable() {
                            public void run() {
                                _btListener.dataReceived(x);
                            }
                        });
                    }
                } catch (IOException e) {
                    updateState(IDLE);
                    closeSocket(_btSocket);
                    _btSocket = null;
                    _btStream = null;
                    break;
                }
            }
        }

        public void write(String s) {
            try {
                s = s.concat("\r");
                byte[] bytes = Hex.stringToHex(s);

                _oStream.write(bytes);
            } catch (IOException e) {
                //disconnected, clean up mess
                updateState(IDLE);
                closeSocket(_btSocket);
                _btSocket = null;
                _btStream = null;
            }
        }
    }


    /** SENDS DATA TO THE OUTPUT STREAM IF AVAILABLE */
    public void sendData(String s) {
        if(_state == CONNECTED && _btStream != null)
            _btStream.write(s);
    }


    /** BROADCAST RECEIVER MEMBER*/
    final BroadcastReceiver _broadcastReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            //When discovery finds a device
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                //Get the BluetoothDevice object from the Intent
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

                //add device to device list
                _scannedDevices.add(device);

                //notify that new device has been found
                _btListener.updateDeviceList(_scannedDevices);
            }
        }
    };


    /** CLOSES THE SOCKET SAFELY */
    private void closeSocket(BluetoothSocket socket) {
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException e3) {
                // nothing
            }
        }
    }


    /** RETURNS THE STATE OF THE BLUETOOTH CONNECTION */
    public int getState() {
        return _state;
    }


    /** CHANGES STATE AND UPDATES THE INTERFACE OF THE STATE CHANGE */
    public void updateState(int state) {
        _state = state;
        _btListener.updateBluetoothState(state);
    }


    /** LISTENER INTERFACE CALLED WHENEVER BLUETOOTH SCAN DISCOVERS A NEW DEVICE */
    /** LISTENER INTERFACE CALLED WHENEVER THE BLUETOOTH CONNECTION HAS BEEN MADE */
    public interface BluetoothListener {
        void updateDeviceList(ArrayList<BluetoothDevice> devices);
        void updateBluetoothState(int connection);
        void dataReceived(String data);
    }
}
