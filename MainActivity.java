package com.example.ledapptwo01;
import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.pm.PackageManager;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;

import android.util.Log;
import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothSocket bluetoothSocket;
    private OutputStream outputStream;

    private static final int REQUEST_ENABLE_BT = 1;
    private static final int PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 2;
    private static final int PERMISSIONS_REQUEST_ACCESS_COARSE_LOCATION = 3;
    private static final int PERMISSIONS_REQUEST_BLUETOOTH_CONNECT = 4;
    private static final String ESP32_DEVICE_NAME = "Nitro_Display";
    private static final String TAG = "MainActivity";
    private static final String LOG_FILE_NAME = "app_log.txt";
    private static final UUID ESP32_SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize Bluetooth Adapter
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth не поддерживается", Toast.LENGTH_LONG).show();
            return;
        }

        // Request Bluetooth permissions
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION);
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, PERMISSIONS_REQUEST_ACCESS_COARSE_LOCATION);
        }

        // Setup buttons
        ImageButton buttonConnect = findViewById(R.id.icon_bluetooth_button);
        ImageButton buttonSound = findViewById(R.id.light_music_button);
        ImageButton buttonLight = findViewById(R.id.image_button);

        buttonConnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                connectToESP32();
            }
        });

        buttonSound.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendDataToESP32("3");
            }
        });

        buttonLight.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendDataToESP32("5");
            }

        });
    }

    public void goToDrawPage(View view) { Intent intent = new Intent(this, DrawActivity.class);
        startActivity(intent); }
    public void goToCameraPage(View view) { Intent intent = new Intent(this, CameraActivity.class);
        startActivity(intent); }
    private void connectToESP32() {
        // Check if Bluetooth is enabled
        if (!bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BLUETOOTH_CONNECT}, PERMISSIONS_REQUEST_BLUETOOTH_CONNECT);
            }
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        } else {
            // Find the ESP32 device
            Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
            if (pairedDevices.size() > 0) {
                for (BluetoothDevice device : pairedDevices) {
                    if (device.getName().equals(ESP32_DEVICE_NAME)) {
                        // Attempt to connect to the device
                        try {
                            bluetoothSocket = device.createRfcommSocketToServiceRecord(ESP32_SPP_UUID);
                            bluetoothSocket.connect();
                            outputStream = bluetoothSocket.getOutputStream();
                            Toast.makeText(this, "Подключено к ESP32", Toast.LENGTH_SHORT).show();
                        } catch (IOException e) {
                            logToFile("Ошибка подключения: " + e.getMessage());
                            Toast.makeText(this, "Ошибка подключения: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                        break;
                    }
                }
            } else {
                Toast.makeText(this, "ESP32 не найден. Проверьте пару с устройством.", Toast.LENGTH_LONG).show();
            }
        }
    }
    private void sendDataToESP32(String data) {
        if (outputStream != null) {
            try {
                outputStream.write((data + "\n").getBytes());
            } catch (IOException e) {
                logToFile("Ошибка отправки данных: " + e.getMessage());
                Toast.makeText(this, "Ошибка отправки данных: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "Не подключено к ESP32", Toast.LENGTH_SHORT).show();
        }
    }

    private void logToFile(String message) {
        String timeStamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
        String logMessage = timeStamp + ": " + message + "\n";
        File logFile = new File(getExternalFilesDir(null), LOG_FILE_NAME);
        try {
            FileOutputStream fos = new FileOutputStream(logFile, true);
            fos.write(logMessage.getBytes());
            fos.close();
        } catch (IOException e) {
            Log.e(TAG, "Ошибка записи в файл лога", e);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (bluetoothSocket != null) {
            try {
                bluetoothSocket.close();
            } catch (IOException e) {
                // Handle the exception
            }
        }
    }
}
