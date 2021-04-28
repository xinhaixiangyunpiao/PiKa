package com.example.pika;

import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import com.wildma.pictureselector.PictureBean;
import com.wildma.pictureselector.PictureSelector;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_ENABLE_LOCATION = 1;
    private ImageView image;
    private Button select;
    private Button transport;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bluetoothLeScanner;
    private final static int REQUEST_ENABLE_BT = 1;
    private boolean mScanning;
    private Handler handler = new Handler();
    private static final long SCAN_PERIOD = 10000;
    private List<BluetoothDevice> devices = new ArrayList<BluetoothDevice>();
    private BluetoothDevice nrf52840;
    private BluetoothGatt bluetoothGatt;
    private BluetoothGattService bluetoothGattService;
    private BluetoothGattCharacteristic bluetoothGattCharacteristic;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        image = findViewById(R.id.image);
        select = findViewById(R.id.select_button);
        transport = findViewById(R.id.transport_button);

        select.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                PictureSelector
                        .create(MainActivity.this, PictureSelector.SELECT_REQUEST_CODE)
                        .selectPicture(true, 200, 200, 1, 1);
            }
        });

        transport.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 向服务发送信息
                bluetoothGattCharacteristic.setValue("123456789");
            }
        });

        // 获取蓝牙适配器
        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();
        bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();

        // 确认当前设备的蓝牙是否可用,
        // 如果不可用, 弹出一个对话框, 请求打开设备的蓝牙模块
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }

        // 检查权限
        checkPermission();

        // 开始扫描ble设备
        scanLeDevice(true);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        /*结果回调*/
        if (requestCode == PictureSelector.SELECT_REQUEST_CODE) {
            if (data != null) {
                PictureBean pictureBean = data.getParcelableExtra(PictureSelector.PICTURE_RESULT);
                if (pictureBean.isCut()) {
                    image.setImageBitmap(BitmapFactory.decodeFile(pictureBean.getPath()));
                } else {
                    image.setImageURI(pictureBean.getUri());
                }
            }
        }
    }

    private void checkPermission() {
        String[] permissions = {Manifest.permission.ACCESS_FINE_LOCATION};
        if (checkSelfPermission(permissions[0]) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(permissions, REQUEST_ENABLE_LOCATION);
        }
    }

    private void scanLeDevice(final boolean enable) {
        if (enable) {
            // Stops scanning after a pre-defined scan period.
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mScanning = false;
                    bluetoothLeScanner.stopScan(scanCallback);
                }
            }, SCAN_PERIOD);
            mScanning = true;
            bluetoothLeScanner.startScan(scanCallback);
            Log.println(Log.INFO,"info","lalala");
        } else {
            mScanning = false;
            bluetoothLeScanner.stopScan(scanCallback);
        }
    }

    //startScan()回调函数
    private ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult results) {
            super.onScanResult(callbackType, results);
            BluetoothDevice device = results.getDevice();
            if (!devices.contains(device)) {  //判断是否已经添加
                devices.add(device);//也可以添加devices.getName()到列表，这里省略
                if (device.getAddress().equals("D0:4A:81:A5:A5:D3")) {
                    nrf52840 = device;
                    // 关闭ble设备扫描
                    scanLeDevice(false);
                    bluetoothGatt = nrf52840.connectGatt(MainActivity.this, false, new BluetoothGattCallback() {
                        @Override
                        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                            super.onConnectionStateChange(gatt, status, newState);
                            if(newState == BluetoothProfile.STATE_CONNECTED){
                                Toast.makeText(getApplicationContext(), "连接成功", Toast.LENGTH_SHORT).show();
                            }
                            gatt.discoverServices(); // 搜索服务
                            if(newState == BluetoothGatt.STATE_DISCONNECTED){
                                Toast.makeText(getApplicationContext(), "连接断开", Toast.LENGTH_SHORT).show();
                            }
                        }

                        @Override
                        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                            super.onServicesDiscovered(gatt, status);
                            // 接收数据
                            String service_UUID = "00001523-1212-efde-1523-785feabcd123"; //已知服务
                            String characteristic_UUID = "00001525-1212-efde-1523-785feabcd123"; //已知特征
                            bluetoothGattService = bluetoothGatt.getService(UUID.fromString(service_UUID));
                            bluetoothGattCharacteristic = bluetoothGattService.getCharacteristic(UUID.fromString(characteristic_UUID));
                            if(bluetoothGattCharacteristic != null){
                                gatt.setCharacteristicNotification(bluetoothGattCharacteristic, true); // 启用onCharacteristicChanged，用于接收数据
                                Toast.makeText(MainActivity.this, "服务建立成功", Toast.LENGTH_LONG).show();
                            }else{
                                Toast.makeText(MainActivity.this, "发现服务失败", Toast.LENGTH_LONG).show();
                                return;
                            }
                        }

                        @Override
                        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
                            super.onCharacteristicChanged(gatt, characteristic);
                            // 发现服务后的响应

                        }
                    });
                }
            }
        };
    };

}