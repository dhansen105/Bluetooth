package com.daniel.bluetooth;


import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.UUID;


public class BluetoothConnection {
    //CONSTANTS
    public final int UNSUPPORTED = -1;
    public final int SUPPORTED = 0;
    public final int IDLE = 1;
    public final int OFF = 2;
    public final int SCANNING = 3;
    public final int CONNECTING = 4;
    public final int CONNECTED = 5;


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
     * @param context Context from the Activity to be updated
     * @param uuid uuid string */
    public BluetoothConnection(Context context, String uuid, BluetoothListener btListener) {
        _context = context;
        _uuid = UUID.fromString(uuid);
        _scannedDevices = new ArrayList<>();
        _btAdapter = BluetoothAdapter.getDefaultAdapter();
        _selectedDevice = null;
        _btListener = btListener;
        _btStream = null;
        _btSocket = null;

        //update state of bluetooth
        if (_btAdapter == null) //bluetooth adapter doesn't exist
            updateState(UNSUPPORTED);
        else {
            //adapter already active
            if(_btAdapter.isEnabled())
                updateState(IDLE);
            else//adapter off
                updateState(OFF);
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
        if(_state == SCANNING)

        if(_btAdapter.isDiscovering()) {
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
            final byte[] buffer = new byte[1024];  // buffer store for the stream

            // Keep listening to the InputStream until an exception occurs
            while (true) {
                try {
                    // Read from the InputStream
                    final int bytes = _iStream.read(buffer);

                    if(bytes > 0) {
                        // Send the obtained bytes to the UI activity
                        ((MainActivity) _context).runOnUiThread(new Runnable() {
                            public void run() {
                                _btListener.dataReceived(Hex.hexToString(buffer));
                            }
                        });
                    }
                } catch (IOException e) {
                    break;
                }
            }
        }

        public void write(String s) {
            try {
                //write to output stream
                _oStream.write(Hex.stringToHex(s));
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


    /** RETURNS THE STATUS OF THE BLUETOOTH CONNECTION */
    public int getStatus() {
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
