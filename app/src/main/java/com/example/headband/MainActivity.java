package com.example.headband;

import android.app.Activity;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;
import com.example.headband.binding.ActivityMainBinding;

import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private SensorManager sensorManager;
    private Sensor accelerometer;
    private boolean isSoundPlaying = false;
    private File dataFile;
    private FileOutputStream fileOutputStream;
    private boolean isCollectingData = false; // Flag to track data collection state

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ActivityMainBinding mainBinding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(mainBinding.getRoot());
        Button button = findViewById(R.id.datacollection);

        // Initialize SensorManager and accelerometer sensor
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (accelerometer == null) {
            Toast.makeText(this, "Accelerometer sensor not available.", Toast.LENGTH_SHORT).show();
            finish(); // Close the activity if accelerometer is not available
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Register the accelerometer sensor listener
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Unregister the sensor listener to save battery
        sensorManager.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {

        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            float x = event.values[0];
            float y = event.values[1];
            float z = event.values[2];

            if (isCollectingData) {
                writeDataToFile(x, y, z);
            }

            // Calculate the total acceleration
            double totalAcceleration = Math.sqrt(x * x + y * y + z * z);

            if (totalAcceleration > 1) {
                if (!isSoundPlaying) {
                    // Start playing sound
                    playSound();
                }
            } else {
                if (isSoundPlaying) {
                    // Stop playing sound
                    stopSound();
                }
            }
        }
    }
    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Not used in this implementation
    }

    private void writeDataToFile(float x, float y, float z) {
        String data = "X: " + x + ", Y: " + y + ", Z: " + z + "\n";
        try {
            fileOutputStream.write(data.getBytes());
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Error writing to file.", Toast.LENGTH_SHORT).show();
        }
    }

    private void playSound() {
        // Implement your logic to play the sound
        isSoundPlaying = true;
        Toast.makeText(this, "Sound playing", Toast.LENGTH_SHORT).show();
    }

    private void stopSound() {
        // Implement your logic to stop the sound
        isSoundPlaying = false;
        Toast.makeText(this, "Sound stopped", Toast.LENGTH_SHORT).show();
    }
}
