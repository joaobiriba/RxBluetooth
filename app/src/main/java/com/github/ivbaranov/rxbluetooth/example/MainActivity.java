package com.github.ivbaranov.rxbluetooth.example;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import com.github.ivbaranov.rxbluetooth.Action;
import com.github.ivbaranov.rxbluetooth.BluetoothConnection;
import com.github.ivbaranov.rxbluetooth.RxBluetooth;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.schedulers.Schedulers;

public class MainActivity extends AppCompatActivity {

    public static final String LOG_TAG = MainActivity.class.getSimpleName();


    private static final int REQUEST_ENABLE_BT = 1;


    // Unique UUID for this application
    private static final UUID MY_UUID =
            UUID.fromString("8ce255c0-200a-11e0-ac64-0800200c9a66");
    // Name for the SDP record when creating server socket
    private static final String NAME_SDP = "SDP_SERVICE";


    private Button start;
    private Button stop;
    private Button listen;
    private ListView result;
    private TextView messageReceived;
    private RxBluetooth rxBluetooth;
    private Subscription deviceSubscription;
    private Subscription discoveryStartSubscription;
    private Subscription discoveryFinishSubscription;
    private Subscription bluetoothStateOnSubscription;
    private Subscription bluetoothStateOtherSubscription;
    private List<BluetoothDevice> devices = new ArrayList<>();

    private Subscription bluetoothConnectSubscrition;
    private Subscription bluetoothListenSubscrition;


    BluetoothConnection bluetoothConnection;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        start = (Button) findViewById(R.id.start);
        stop = (Button) findViewById(R.id.stop);
        result = (ListView) findViewById(R.id.result);
        listen = (Button) findViewById(R.id.listen);
        messageReceived = (TextView) findViewById(R.id.message_received);

        result.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapter, View v, int position,
                                    long arg3) {
                BluetoothDevice device = (BluetoothDevice) adapter.getItemAtPosition(position);
                Log.d(LOG_TAG, "onItemClick: " + device.getName());
                bluetoothConnectSubscrition = rxBluetooth.observeConnectDevice(device, MY_UUID)
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribeOn(Schedulers.io())
                        .subscribe(new Action1<BluetoothSocket>() {
                            @Override
                            public void call(BluetoothSocket bluetoothSocket) {
                                try {
                                    bluetoothConnection = new BluetoothConnection(bluetoothSocket);

                                    listen.setText("Send");
                                    listen.setOnClickListener(new View.OnClickListener() {
                                        @Override
                                        public void onClick(View v) {
                                            bluetoothConnection.send("Hello"); // String
                                            Log.e(LOG_TAG, "call: sent");
                                        }
                                    });


                                } catch (Exception e) {
                                    Log.e(LOG_TAG, "call: " + e.getMessage());
                                }
                            }
                        }, new Action1<Throwable>() {
                            @Override
                            public void call(Throwable throwable) {
                                Log.e(LOG_TAG, "call: " + throwable.getMessage());
                            }
                        });
            }
        });

        rxBluetooth = new RxBluetooth(this);
        if (!rxBluetooth.isBluetoothEnabled()) {
            rxBluetooth.enableBluetooth(this, REQUEST_ENABLE_BT);
        }

        deviceSubscription = rxBluetooth.observeDevices()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeOn(Schedulers.computation())
                .subscribe(new Action1<BluetoothDevice>() {

                    @Override
                    public void call(BluetoothDevice bluetoothDevice) {
                        Log.d(LOG_TAG, "call: " + bluetoothDevice.getName());
                        addDevice(bluetoothDevice);
                    }
                });

        discoveryStartSubscription = rxBluetooth.observeDiscovery()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeOn(Schedulers.computation())
                .filter(Action.isEqualTo(BluetoothAdapter.ACTION_DISCOVERY_STARTED))
                .subscribe(new Action1<String>() {
                    @Override
                    public void call(String action) {
                        start.setText(R.string.button_searching);
                    }
                });

        discoveryFinishSubscription = rxBluetooth.observeDiscovery()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeOn(Schedulers.computation())
                .filter(Action.isEqualTo(BluetoothAdapter.ACTION_DISCOVERY_FINISHED))
                .subscribe(new Action1<String>() {
                    @Override
                    public void call(String action) {
                        start.setText(R.string.button_restart);
                    }
                });

        bluetoothStateOnSubscription = rxBluetooth.observeBluetoothState()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeOn(Schedulers.computation())
                .filter(Action.isEqualTo(BluetoothAdapter.STATE_ON))
                .subscribe(new Action1<Integer>() {
                    @Override
                    public void call(Integer integer) {
                        start.setBackgroundColor(getResources().getColor(R.color.colorActive));
                    }
                });

        bluetoothStateOtherSubscription = rxBluetooth.observeBluetoothState()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeOn(Schedulers.computation())
                .filter(Action.isEqualTo(BluetoothAdapter.STATE_OFF, BluetoothAdapter.STATE_TURNING_OFF,
                        BluetoothAdapter.STATE_TURNING_ON))
                .subscribe(new Action1<Integer>() {
                    @Override
                    public void call(Integer integer) {
                        start.setBackgroundColor(getResources().getColor(R.color.colorInactive));
                    }
                });

        start.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                devices.clear();
                setAdapter(devices);
                rxBluetooth.startDiscovery();
            }
        });
        stop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                rxBluetooth.cancelDiscovery();
            }
        });

        listen.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bluetoothListenSubscrition = rxBluetooth.observeBluetoothSocket(NAME_SDP, MY_UUID)
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribeOn(Schedulers.io())
                        .subscribe(new Action1<BluetoothSocket>() {
                            @Override
                            public void call(BluetoothSocket bluetoothSocket) {
                                try {
                                    Log.d(LOG_TAG, "call: socket " + bluetoothSocket.getRemoteDevice().getName());
                                    bluetoothConnection = new BluetoothConnection(bluetoothSocket);

                                    // Or just observe string
                                    bluetoothConnection.observeStringStream()
                                            .observeOn(AndroidSchedulers.mainThread())
                                            .subscribeOn(Schedulers.io())
                                            .subscribe(new Action1<String>() {
                                                @Override
                                                public void call(String string) {
                                                    Log.d(LOG_TAG, "call: received " + string);
                                                }
                                            }, new Action1<Throwable>() {
                                                @Override
                                                public void call(Throwable throwable) {
                                                    // Error occured
                                                    Log.d(LOG_TAG, "call: error " + throwable.toString());
                                                }
                                            });
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                        });
            }
        });


    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (rxBluetooth != null) {
            // Make sure we're not doing discovery anymore
            rxBluetooth.cancelDiscovery();
        }

        unsubscribe(deviceSubscription);
        unsubscribe(discoveryStartSubscription);
        unsubscribe(discoveryFinishSubscription);
        unsubscribe(bluetoothStateOnSubscription);
        unsubscribe(bluetoothStateOtherSubscription);
        unsubscribe(bluetoothListenSubscrition);
        unsubscribe(bluetoothConnectSubscrition);
    }

    private void addDevice(BluetoothDevice device) {
        String deviceName;
        deviceName = device.getAddress();
        if (!TextUtils.isEmpty(device.getName())) {
            deviceName += " " + device.getName();
        }
        devices.add(device);

        setAdapter(devices);
    }

    private void setAdapter(List<BluetoothDevice> list) {
        int itemLayoutId = android.R.layout.simple_list_item_1;
        result.setAdapter(new ArrayAdapter<>(this, itemLayoutId, list));
    }

    private static void unsubscribe(Subscription subscription) {
        if (subscription != null && !subscription.isUnsubscribed()) {
            subscription.unsubscribe();
            subscription = null;
        }
    }


}
