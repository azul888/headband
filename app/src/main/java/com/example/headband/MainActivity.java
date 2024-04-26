package com.example.headband;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.headband.databinding.ActivityMainBinding;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private SensorManager sensorManager;
    private Sensor accelerometer;
    private PrintWriter writer;
    private FileOutputStream fileOutputStream;
    private Socket socket;

    private boolean isCollectingData = false;
    private boolean sendToServer = false;
    private boolean isSoundPlaying = false;

    private String serverIp;
    private int portNumber;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityMainBinding mainBinding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(mainBinding.getRoot());
        setupUIComponents();
    }

    private void setupUIComponents() {
        Switch switchButton = findViewById(R.id.toggleDataFlow);
        EditText portNumberEdit = findViewById(R.id.port_number);
        EditText serverIPEdit = findViewById(R.id.ip_address);

        setupSwitchListener(switchButton, serverIPEdit, portNumberEdit);
        setupDataCollectionButton(findViewById(R.id.datacollection), findViewById(R.id.debug));

        initializeSensors();
    }

    private void setupSwitchListener(Switch switchButton, EditText serverIPEdit, EditText portNumberEdit) {
        switchButton.setOnCheckedChangeListener((buttonView, isChecked) -> {
            sendToServer = isChecked;
            updateUIForDataFlowSwitch(isChecked, serverIPEdit, portNumberEdit, switchButton);
        });
    }

    private void updateUIForDataFlowSwitch(boolean isChecked, EditText serverIpEdit, EditText portNumberEdit, Switch switchButton) {
        if (isChecked) {
            switchButton.setText("Current: Server Send");
            serverIpEdit.setVisibility(View.VISIBLE);
            portNumberEdit.setVisibility(View.VISIBLE);
        } else {
            closeConnection();
            switchButton.setText("Current: File Write");
            serverIpEdit.setVisibility(View.GONE);
            portNumberEdit.setVisibility(View.GONE);
        }
    }

    private void setupDataCollectionButton(Button button, TextView debugText) {
        button.setOnClickListener(v -> {
            if (debugText.getVisibility() == View.VISIBLE) {
                debugText.setVisibility(View.GONE);
            }
            toggleDataCollection();
        });
    }

    private void initializeSensors() {
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (accelerometer == null) {
            Toast.makeText(this, "Accelerometer sensor not available.", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER && isCollectingData) {
            float x = event.values[0];
            float y = event.values[1];
            float z = event.values[2];
            handleData(x, y, z);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Not used in this implementation
    }

    private void handleData(float x, float y, float z) {
        if (sendToServer) {
            sendDataToServer(x, y, z);
        } else {
            writeDataToFile(x, y, z);
        }
    }

    private void toggleDataCollection() {
        if (!isCollectingData) {
            startDataCollection();
        } else {
            stopDataCollection();
        }
    }

    private void startDataCollection() {
        isCollectingData = true;
        if (sendToServer) {
            establishServerConnection();
        } else {
            createDataFile();
        }
    }

    private void establishServerConnection() {
        new Thread(() -> {
            try {
                socket = new Socket(serverIp, portNumber);
                writer = new PrintWriter(socket.getOutputStream(), true);
            } catch (Exception e) {
                handleConnectionError(e);
            }
        }).start();
    }

    private void createDataFile() {
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, "SensorData.txt");
        values.put(MediaStore.MediaColumns.MIME_TYPE, "text/plain");
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);

        Uri uri = getContentResolver().insert(MediaStore.Files.getContentUri("external"), values);
        try {
            fileOutputStream = (FileOutputStream) getContentResolver().openOutputStream(uri);
        } catch (FileNotFoundException e) {
            Toast.makeText(this, "Failed to create file for data collection.", Toast.LENGTH_SHORT).show();
        }
    }

    private void stopDataCollection() {
        isCollectingData = false;
        unregisterSensors();
        closeResources();
    }

    private void unregisterSensors() {
        sensorManager.unregisterListener(this);
    }

    private void closeResources() {
        try {
            if (sendToServer && writer != null) {
                writer.close();
                socket.close();
            }
            if (!sendToServer && fileOutputStream != null) {
                fileOutputStream.close();
            }
        } catch (IOException e) {
            Toast.makeText(this, "Error closing resources: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
    private void closeConnection() {
        if (sendToServer && writer != null && socket != null) {
            new Thread(() -> {
                try {
                    writer.close();
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "Error closing server connection", Toast.LENGTH_SHORT).show());
                }
            }).start();
        }
    }


    private void writeDataToFile(float x, float y, float z) {
        String data = String.format("X: %f, Y: %f, Z: %f%n", x, y, z);
        try {
            fileOutputStream.write(data.getBytes());
        } catch (IOException e) {
            Toast.makeText(this, "Error writing to file.", Toast.LENGTH_SHORT).show();
        }
    }

    private void sendDataToServer(float x, float y, float z) {
        new Thread(() -> {
            if (writer != null) {
                try {
                    writer.printf("%.2f,%.2f,%.2f%n", x, y, z);
                } catch (Exception e) {
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "Error sending data to server", Toast.LENGTH_SHORT).show());
                }
            }
        }).start();
    }

    private void handleConnectionError(Exception e) {
        runOnUiThread(() -> {
            TextView debugText = findViewById(R.id.debug);
            debugText.setVisibility(View.VISIBLE);
            debugText.setText(String.format("Connection error: %s", e.getMessage()));
            Toast.makeText(this, "Connection error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        });
    }
}
