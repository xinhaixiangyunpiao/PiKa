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
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import com.wildma.pictureselector.PictureBean;
import com.wildma.pictureselector.PictureSelector;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.logging.Logger;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_ENABLE_LOCATION = 1;
    private ImageView image;
    private Button select;
    private Button transport;
    private BluetoothManager bluetoothManager;
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
    private Timer timer;

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
                if(bluetoothGattCharacteristic != null){
                    byte[] value = new byte[10];
                    value[0] = (byte)0x30;
                    value[1] = (byte)0x31;
                    value[2] = (byte)0x32;
                    value[3] = (byte)0x33;
                    value[4] = (byte)0x34;
                    value[5] = (byte)0x35;
                    value[6] = (byte)0x36;
                    value[7] = (byte)0x37;
                    value[8] = (byte)0x38;
                    value[9] = (byte)0x39;
                    bluetoothGattCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
                    bluetoothGattCharacteristic.setValue(value);
                    bluetoothGatt.writeCharacteristic(bluetoothGattCharacteristic);
                    Toast.makeText(getApplicationContext(), "已发送", Toast.LENGTH_SHORT).show();
                } else
                    Toast.makeText(getApplicationContext(), "设备未连接，请先连接设备", Toast.LENGTH_SHORT).show();
            }
        });

        // 确认当前设备的蓝牙是否可用,
        // 如果不可用, 弹出一个对话框, 请求打开设备的蓝牙模块
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }

        // 检查权限
        checkPermission();
    }

    @Override
    protected void onResume() {
        super.onResume();
        //第一步，初始化各工具
        init();
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

    private void init(){
        if(bluetoothManager == null)
            bluetoothManager = (BluetoothManager)getSystemService(Context.BLUETOOTH_SERVICE);
        if(bluetoothAdapter == null)
            bluetoothAdapter = bluetoothManager.getAdapter();
        if(bluetoothLeScanner == null)
            bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
        // 开始扫描设备
        scanLeDevice(true);
    }

    private void scanLeDevice(final boolean enable) {
        if (enable) {
            // Stops scanning after a pre-defined scan period.
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    bluetoothLeScanner.stopScan(scanCallback);
                }
            }, SCAN_PERIOD);
            bluetoothLeScanner.startScan(scanCallback);
        } else {
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
                    Toast.makeText(getApplicationContext(), "发现设备", Toast.LENGTH_SHORT).show();
                    nrf52840 = device;
                    bluetoothGatt = nrf52840.connectGatt(MainActivity.this, false, mGattCallback);
                    // 关闭ble设备扫描
                    scanLeDevice(false);
                }
            }
        };
    };

    private BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            if(newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i("msg","连接成功");
                gatt.discoverServices(); // 搜索服务
            }else if(newState == BluetoothProfile.STATE_DISCONNECTED){
                Log.i("msg","连接断开");
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
            // 接收数据
            String service_UUID = "00001523-1212-efde-1523-785feabcd123"; //已知服务
            String characteristic_UUID = "00001525-1212-efde-1523-785feabcd123"; //已知特征
            bluetoothGattService = gatt.getService(UUID.fromString(service_UUID));
            bluetoothGattCharacteristic = bluetoothGattService.getCharacteristic(UUID.fromString(characteristic_UUID));
            if(bluetoothGattCharacteristic != null){
                gatt.setCharacteristicNotification(bluetoothGattCharacteristic, true); // 启用onCharacteristicChanged，用于接收数据
                Looper.prepare();
                Toast.makeText(getApplicationContext(), "服务建立成功", Toast.LENGTH_SHORT).show();
                Looper.loop();
                Log.i("msg","服务建立成功");
            }else{
                Log.i("msg","服务建立失败");
                return;
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicRead(gatt, characteristic, status);
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt,characteristic,status);
            if(status == BluetoothGatt.GATT_SUCCESS){
                Log.i("msg","写入成功");
            }else if(status == BluetoothGatt.GATT_FAILURE){
                Log.i("msg","写入失败");
            }else if(status == BluetoothGatt.GATT_WRITE_NOT_PERMITTED){
                Log.i("msg","没有写入权限");
            }
        }
    };

}