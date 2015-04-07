package io.relayr.khaled.willywonkahats;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGattCharacteristic;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Arrays;
import java.util.List;

import io.relayr.RelayrSdk;
//import io.relayr.RelayrSdk$$InjectAdapter;
import io.relayr.ble.BleDevice;
import io.relayr.ble.BleDeviceMode;
import io.relayr.ble.BleDeviceType;
import io.relayr.ble.service.BaseService;
import io.relayr.ble.service.DirectConnectionService;
import io.relayr.model.Reading;
import rx.Observable;
import rx.Observer;
import rx.Subscriber;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action0;
import rx.functions.Func1;
import rx.subscriptions.Subscriptions;


public class MainActivity extends Activity {

    private Subscription mColorScannerSubscription = Subscriptions.empty();
    private Subscription mBridgeScannerSubscription = Subscriptions.empty();
    private Subscription mColorDeviceSubscription = Subscriptions.empty();
    private Subscription mBridgeSubscription = Subscriptions.empty();

    private boolean mColorStartedScanning;
    private boolean mBridgeStartedScanning;
    boolean foundBridge=false;
    boolean foundColor=false;
    private BleDevice mColorDevice;
    private BleDevice mBridgeDevice;
    private TextView mColorOutput;
    private TextView mColorError;
    private TextView mBridgeError;
    private List<BleDevice> discoveredDevices;
    private byte[] mRgbByte;

    private DirectConnectionService mDirectConnectionService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        new RelayrSdk.Builder(this).inMockMode(true).build();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mColorError=(TextView) findViewById(R.id.color_error);
        mColorOutput=(TextView) findViewById(R.id.color_output);
        mBridgeError = (TextView) findViewById(R.id.bridge_error);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!RelayrSdk.isBleSupported()) {
            Toast.makeText(this, getString(R.string.bt_not_supported), Toast.LENGTH_SHORT).show();
        } else if (!RelayrSdk.isBleAvailable()) {
            RelayrSdk.promptUserToActivateBluetooth(this);
        }
        else {
            if (!mColorStartedScanning && mColorDevice == null) {
                mColorStartedScanning = true;
                discoverColorSensor();
            }

            if (!mBridgeStartedScanning && mBridgeDevice == null) {
                mBridgeStartedScanning = true;
                discoverBridge();
            }
        }
        }

    @Override
    protected void onDestroy() {
        unSubscribeToUpdates();
        disconnectBluetooth();
        super.onDestroy();
    }

    public void discoverColorSensor() {
        // Search for WunderBar temp/humidity sensors and take first that is direct connection mode
        mColorScannerSubscription = RelayrSdk.getRelayrBleSdk()
                .scan(Arrays.asList(BleDeviceType.WunderbarLIGHT))
                .filter(new Func1<List<BleDevice>, Boolean>() {
                    @Override
                    public Boolean call(List<BleDevice> bleDevices) {
                        for (BleDevice device: bleDevices)
                        if (device.getMode() == BleDeviceMode.DIRECT_CONNECTION) {
                            // We can stop scanning, since we've found a sensora
                            Log.w("Found Color in DC", "" + device.getAddress());
                            foundColor = true;
                            return true;
                        }
                        mColorStartedScanning = false;
                        return false;
                    }
                })
                .map(new Func1<List<BleDevice>, BleDevice>() {
                    @Override
                    public BleDevice call(List<BleDevice> bleDevices) {
                        for (BleDevice device : bleDevices) {
                            if (device.getMode() == BleDeviceMode.DIRECT_CONNECTION) //&& device.getAddress().equals("D0:15:51:6C:E8:D3"))
                                return device;
                            }
                        mColorStartedScanning = false;
                        return null; // will never happen since it's been filtered out before
                        }
                })
                .take(1)
                .subscribeOn(AndroidSchedulers.mainThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Subscriber<BleDevice>() {
                    @Override
                    public void onCompleted() {
                        mColorStartedScanning = false;
                    }

                    @Override
                    public void onError(Throwable e) {
                        mColorStartedScanning = false;
                        e.printStackTrace();
                        mColorError.setText(R.string.sensor_discovery_error);
                    }

                    @Override
                    public void onNext(BleDevice device) {
                        // mColorOutput.setText("" + device.getName());
//                        if (device.getAddress() == "C2:A5:6E:8B:68:40") {
                        //Log.w("ON NEXT DEVICE", "");
                        // mDirectConnectionService.writeDownChannel(new byte[] {0x01,0x03});
                        subscribeForColorUpdates(device);
                        Log.w("On NExt Color Device","Subscribing");
                        //}
                    }
                });
    }

    private void subscribeForColorUpdates(final BleDevice device) {
        mColorDevice = device;
        mColorDeviceSubscription = device.connect()
                .flatMap(new Func1<BaseService, Observable<Reading>>() {
                    @Override
                    public Observable<Reading> call(BaseService baseService) {
                        return ((DirectConnectionService) baseService).getReadings();
                    }
                })
                .doOnUnsubscribe(new Action0() {
                    @Override
                    public void call() {
                        device.disconnect();
                    }
                })
                .subscribeOn(AndroidSchedulers.mainThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<Reading>() {
                    @Override
                    public void onCompleted() {
                        Log.w("on complete","here");
                    }

                    @Override
                    public void onError(Throwable e) {
                        e.printStackTrace();
                        mColorError.setText("COLOR SUBSCRIPTION ERROR");
                    }

                    @Override
                    public void onNext(Reading reading) {
                        if(reading.meaning.equals("color")) {
                            String delims = "[=,}]";
                            String[] tokens = reading.value.toString().split(delims);
                            Double dr=Double.parseDouble(tokens[5]);///16;
                            Double db=Double.parseDouble(tokens[1]);///16;
                            Double dg=Double.parseDouble(tokens[3]);///16;

                            dr *= 2.0/3.0;
                            Double max = Math.max(dr, Math.max(dg,db));

                            if(max>0) {
                                dr = dr / max*255.0;
                                dg = dg / max*255.0;
                                db = db / max*255.0;
                            }

                            int intR=dr.intValue();//*255; //>= 0? dr.intValue():dr.intValue()+256;
                            int intG=dg.intValue();//*255; //>= 0? dg.intValue():dr.intValue()+256;
                            int intB=db.intValue();//*255;// >= 0? db.intValue():dr.intValue()+256;

                            byte byteR=(byte) intR;
                            byte byteG=(byte) intG;
                            byte byteB=(byte) intB;

//                            byte rByte=Byte.parseByte(r);
  //                          byte gByte=Byte.parseByte(g);
    //                        byte bByte=Byte.parseByte(b);
                            //mColorError.setText("byte r ="+ int);
                            mColorOutput.setText("red: "+ intR+ " green: " +intG +" blue: " +intB);
                            mRgbByte=new byte[] {03,byteR,byteG,byteB};

                            //Log.w("RGB BYTE",""+mRgbByte[0]+" " +mRgbByte[1]+" " +mRgbByte[2]);
                            //mColorOutput.setText(""+reading.value);
                            //Log.w("READING",""+reading.value);
                            //TODO CREATE BYTE[] AND SEND TO BRIDGE MODULE
                            //set rgbs
                            //else
                            //   return;
                            if(mBridgeDevice != null) {
                                mBridgeError.setText("Bridge Found");
                                subscribeForBridgeUpdates(mBridgeDevice);
                            }
                            else mBridgeError.setText("Didn't find bridge yet");
                        }
                    }
                });
    }

    public void discoverBridge() {
        // Search for WunderBar temp/humidity sensors and take first that is direct connection mode
        Log.w("@DISCOVERBRIDGE","DISCOVER BRIDGE");
        mBridgeScannerSubscription = RelayrSdk.getRelayrBleSdk()
                .scan(Arrays.asList(BleDeviceType.WunderbarBRIDG))
                .filter(new Func1<List<BleDevice>, Boolean>() {
                    @Override
                    public Boolean call(List<BleDevice> bleDevices) {
                        for (BleDevice device: bleDevices)
                            if (device.getMode() == BleDeviceMode.DIRECT_CONNECTION) {
                                // We can stop scanning, since we've found a sensora
                                Log.w("Found Bridge in DC", "" + device.getAddress());
                                foundBridge = true;
                                return true;
                            }
                        mBridgeStartedScanning = false;
                        return false;
                    }
                })
                .map(new Func1<List<BleDevice>, BleDevice>() {
                    @Override
                    public BleDevice call(List<BleDevice> bleDevices) {
                        for (BleDevice device : bleDevices) {
                            if (device.getMode() == BleDeviceMode.DIRECT_CONNECTION)
                                return device;
                        }
                        mBridgeStartedScanning = false;
                        return null; // will never happen since it's been filtered out before
                    }
                })
                .take(1)
                .subscribeOn(AndroidSchedulers.mainThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Subscriber<BleDevice>() {
                    @Override
                    public void onCompleted() {
                        mBridgeStartedScanning = false;
                    }

                    @Override
                    public void onError(Throwable e) {
                        mBridgeStartedScanning = false;
                        e.printStackTrace();
                        mColorError.setText(R.string.sensor_discovery_error);
                    }

                    @Override
                    public void onNext(BleDevice device) {
                        // mColorOutput.setText("" + device.getName());
//                        if (device.getAddress() == "C2:A5:6E:8B:68:40") {
                        //Log.w("ON NEXT DEVICE", "");
                        // mDirectConnectionService.writeDownChannel(new byte[] {0x01,0x03});
                        mBridgeDevice = device;
                        Log.w("Bridge","Found Bridge Device, subscribe on next");
                        //return;
                        //}
                    }
                });
    }

    private void subscribeForBridgeUpdates(BleDevice device) {
    //    mBridgeDevice = device;
        mBridgeSubscription = device.connect()
                .flatMap(new Func1<BaseService, Observable<DirectConnectionService>>() {
                    @Override
                    public Observable<DirectConnectionService> call(BaseService baseService) {
                        return Observable.just((DirectConnectionService) baseService);
                    }
                })
                .subscribeOn(AndroidSchedulers.mainThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Subscriber<DirectConnectionService>() {
                    @Override
                    public void onCompleted() {
                        Log.w("BRIDGE SUBSCRIPTION","COMPLETED");
                    }

                    @Override
                    public void onError(Throwable e) {
                        e.printStackTrace();
                    }


                    @Override
                    public void onNext(DirectConnectionService directConnectionService) {
                      //  Log.w("BRIDGE SUBSCRIPTION","onNext Sending byte");
                        mDirectConnectionService=directConnectionService;
                        //if(mDirectConnectionService != null)
                        Log.w("SENDING BYTE ARRAY",""+mRgbByte);
                            mDirectConnectionService.sendCommand(mRgbByte)
                                    .subscribe(new Observer<BluetoothGattCharacteristic>() {
                                        @Override
                                        public void onCompleted() {
                                            Log.w("SENDBYTE", "SEND COMPLETE");
                                        }

                                        @Override
                                        public void onError(Throwable e) {
                                            //mRgbByte=new byte[]{3,0,0,0};
                                        }

                                        @Override
                                        public void onNext(BluetoothGattCharacteristic bluetoothGattCharacteristic) {
                                            //                    Log.w("SENDBYTE","SENDING NEW BYTE");
                                            // mDirectConnectionService.sendCommand(mRgbByte);
                                        }
                                    });

                    }
                });

                    }


    private void unSubscribeToUpdates() {
        mColorScannerSubscription.unsubscribe();
        mBridgeScannerSubscription.unsubscribe();
        mColorDeviceSubscription.unsubscribe();
        mBridgeSubscription.unsubscribe();

        if (mColorDevice != null) mColorDevice.disconnect();
        if (mBridgeDevice != null) mBridgeDevice.disconnect();
    }

    private void disconnectBluetooth() {
        BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter.isEnabled()) mBluetoothAdapter.disable();
    }
}