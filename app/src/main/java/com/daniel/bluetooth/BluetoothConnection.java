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
import java.util.logging.Handler;
import java.util.logging.LogRecord;

public class BluetoothConnection {
    //CONSTANTS
    public final int STATUS_UNSUPPORTED = -1;
    public final int STATUS_SUPPORTED = 0;
    public final int STATUS_ON = 1;
    public final int STATUS_OFF = 2;
    public final int STATUS_SCANNING = 3;
    public final int STATUS_CONNECTING = 4;
    public final int STATUS_CONNECTED = 5;


    //MEMBERS
    private UUID _uuid;
    private int _status;
    private Context _context;
    private BluetoothAdapter _btAdapter;
    private ArrayList<BluetoothDevice> _scannedDevices;
    private BluetoothScanListener _scanListener;


    /** CONSTRUCTOR FOR BLUETOOTH CONNECTION
     * @param uuid uuid string */
    public BluetoothConnection(Context context, String uuid) {
        _context = context;
        _uuid = UUID.fromString(uuid);
        _scannedDevices = new ArrayList<>();
        _btAdapter = BluetoothAdapter.getDefaultAdapter();

        if (_btAdapter == null)
            _status = STATUS_UNSUPPORTED;
        else {
            _status = STATUS_SUPPORTED;
            if(_btAdapter.isEnabled())
                _status = STATUS_ON;
            else
                _status = STATUS_OFF;
        }
    }

    /** TURNS ON THE BLUETOOTH ADAPTER */
    public void bluetoothOn() {
        if(_status == STATUS_UNSUPPORTED)
            return;

        _btAdapter.enable();
        _status = STATUS_ON;
    }


    /** TURNS OFF THE BLUETOOTH ADAPTER */
    public void bluetoothOff() {
        if(_status == STATUS_UNSUPPORTED)
            return;

        _btAdapter.disable();
        _status = STATUS_OFF;
    }


    /** RETURNS AN ARRAY LIST OF PAIRED DEVICES*/
    public ArrayList<BluetoothDevice> getPairedDeviceList() {
        if(_status == STATUS_ON)
            return new ArrayList<>(_btAdapter.getBondedDevices());

        return null;
    }


    /** SCANS FOR DISCOVERABLE DEVICES AND UPDATES */
    public void scanForDevices(BluetoothScanListener scanListener) {
        if(_status == STATUS_ON) {
            _scannedDevices.clear();
            _scanListener = scanListener;
            _btAdapter.startDiscovery();
            _context.registerReceiver(_broadcastReceiver, new IntentFilter(BluetoothDevice.ACTION_FOUND));
            _status = STATUS_SCANNING;
        }
    }


    /** CANCELS SCANNING FOR DISCOVERABLE DEVICES */
    public void cancelScanForDevices() {
        if(_btAdapter.isDiscovering()) {
            _btAdapter.cancelDiscovery();
            _status = STATUS_ON;
        }
    }


    /** ATTEMPTS CONNECTION WITH THE GIVEN BLUETOOTH DEVICE */
    public void connectToDevice(BluetoothDevice device, BluetoothConnectListener listener) {
        BluetoothBackgroundConnection connectThread = new BluetoothBackgroundConnection(device, listener);
        connectThread.start();

        _status = STATUS_CONNECTING;
    }


    /** ATTEMPTS CONNECTION WITH THE GIVEN BLUETOOTH DEVICE */
    public BluetoothDataConnection setupDataConnection(BluetoothSocket btSocket) {
        return new BluetoothDataConnection(btSocket);
    }


    /** CLASS THAT HANDLES CREATING A BLUETOOTH CONNECTION IN A BACKGROUND THREAD */
    public class BluetoothBackgroundConnection extends Thread{
        BluetoothDevice _device;
        BluetoothConnectListener _connListener;

        public BluetoothBackgroundConnection(BluetoothDevice device, BluetoothConnectListener listener) {
            _device = device;
            _connListener = listener;
        }

        public void run() {
            BluetoothSocket btSocket = null;
            ((MainActivity) _context).runOnUiThread(new Runnable() {
                public void run() {
                    _connListener.updateConnectionInfo(STATUS_CONNECTING, null);
                }
            });

            //discovering will slow down connection
            if(_btAdapter.isDiscovering())
                _btAdapter.cancelDiscovery();

            //attempt to connect
            try {
                btSocket = _device.createRfcommSocketToServiceRecord(_uuid);
                btSocket.connect();
                _status = STATUS_CONNECTED;
                final BluetoothSocket socket = btSocket;
                ((MainActivity) _context).runOnUiThread(new Runnable() {
                    public void run() {
                        _connListener.updateConnectionInfo(STATUS_CONNECTED, socket);
                    }
                });
                return;
            } catch (IOException e1) {
                _status = STATUS_ON;

                //if failed first connection try fallback connection
                try {
                    btSocket = (BluetoothSocket) _device.getClass().getMethod("createRfcommSocket", new Class[]{int.class}).invoke(_device, 1);
                    btSocket.connect();
                    _status = STATUS_CONNECTED;
                    final BluetoothSocket socket = btSocket;
                    ((MainActivity) _context).runOnUiThread(new Runnable() {
                        public void run() {
                            _connListener.updateConnectionInfo(STATUS_CONNECTED, socket);
                        }
                    });
                    return;
                } catch (Exception e2) {
                    _status = STATUS_ON;
                }
            }

            if(btSocket != null) {
                try {
                    btSocket.close();
                } catch (IOException e3) {
                    _status = STATUS_ON;
                }
            }
            ((MainActivity) _context).runOnUiThread(new Runnable() {
                public void run() {
                    _connListener.updateConnectionInfo(STATUS_ON, null);
                }
            });
        }
    }


    public class BluetoothDataConnection extends Thread{
        InputStream _iStream;
        OutputStream _oStream;
        BluetoothSocket _btSocket;

        public BluetoothDataConnection(BluetoothSocket btSocket) {
            _btSocket = btSocket;

            try {
                _iStream = btSocket.getInputStream();
                _oStream = btSocket.getOutputStream();
            } catch (IOException e) { }
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
                                ((MainActivity) _context).dataReceived(Hex.hexToString(buffer));
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
                _oStream.write(Hex.stringToHex(s));
            } catch (IOException e) { }
        }
    }


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
                _scanListener.updateDeviceList(_scannedDevices);
            }
        }
    };


    /** RETURNS THE STATUS OF THE BLUETOOTH CONNECTION */
    public int getStatus() {
        return _status;
    }

    /** LISTENER INTERFACE CALLED WHENEVER BLUETOOTH SCAN DISCOVERS A NEW DEVICE */
    public interface BluetoothScanListener {
        void updateDeviceList(ArrayList<BluetoothDevice> devices);
    }

    /** LISTENER INTERFACE CALLED WHENEVER THE BLUETOOTH CONNECTION HAS BEEN MADE */
    public interface BluetoothConnectListener {
        void updateConnectionInfo(int connection, BluetoothSocket btSocket);
    }
}
