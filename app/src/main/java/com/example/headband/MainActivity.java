package com.example.headband;

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
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.headband.databinding.ActivityMainBinding;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private SensorManager sensorManager;
    private Sensor accelerometer;
    private boolean isSoundPlaying = false;
    //    private File dataFile;
    private FileOutputStream fileOutputStream;
    private boolean isCollectingData = false;
    private int dataCount = 0; // Counter for data writes
    private ScheduledExecutorService scheduler;
    private final Object fileLock = new Object(); // Lock object for synchronization


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ActivityMainBinding mainBinding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(mainBinding.getRoot());
        Button button = findViewById(R.id.datacollection);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isCollectingData) {
                    startDataCollection();
                    startScheduler();
                } else {
                    stopDataCollection();
                }
            }
        });

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

//    private void startDataCollection() {
//        isCollectingData = true;
//        Toast.makeText(this, "Starting data collection...", Toast.LENGTH_SHORT).show();
//        dataCount = 0; // Reset the data count
//
//        ContentValues values = new ContentValues();
//        values.put(MediaStore.MediaColumns.DISPLAY_NAME, "SensorData.txt");
//        values.put(MediaStore.MediaColumns.MIME_TYPE, "text/plain");
//        values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
//
//        Uri uri = getContentResolver().insert(MediaStore.Files.getContentUri("external"), values);
//        try {
//            fileOutputStream = (FileOutputStream) getContentResolver().openOutputStream(uri);
//            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
//            startScheduler(); // Start the scheduler for writing iteration count to the file
//        } catch (FileNotFoundException e) {
//            e.printStackTrace();
//            Toast.makeText(this, "Failed to create file for data collection.", Toast.LENGTH_SHORT).show();
//        }
//    }

    private void startScheduler() {
        if (scheduler != null) {
            scheduler.shutdown();
        }
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                if (isCollectingData) {
                    synchronized (fileLock) {
                        try {
                            fileOutputStream.write(("Iteration Count: " + dataCount + "\n").getBytes());
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }, 1, 1, TimeUnit.SECONDS);
    }

    private void writeDataToFile(float x, float y, float z) {
        synchronized (fileLock) {
            String data = "X: " + x + ", Y: " + y + ", Z: " + z + "\n";
            try {
                fileOutputStream.write(data.getBytes());
                dataCount++; // Increment data write count
            } catch (IOException e) {
                e.printStackTrace();
                Toast.makeText(this, "Error writing to file.", Toast.LENGTH_SHORT).show();
            }
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
    private void startDataCollection() {
        isCollectingData = true;
        Toast.makeText(this, "Starting data collection...", Toast.LENGTH_SHORT).show();

        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, "SensorData.txt"); // File name
        values.put(MediaStore.MediaColumns.MIME_TYPE, "text/plain"); // File type
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS); // Directory

        Uri uri = getContentResolver().insert(MediaStore.Files.getContentUri("external"), values); // Using "external" for shared storage
        try {
            assert uri != null;
            fileOutputStream = (FileOutputStream) getContentResolver().openOutputStream(uri);
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to create file for data collection.", Toast.LENGTH_SHORT).show();
        }
    }
    private void stopDataCollection() {
        isCollectingData = false;
        Toast.makeText(this, "Stopping data collection and saving file...", Toast.LENGTH_SHORT).show();
        try {
            sensorManager.unregisterListener(this);
            if (fileOutputStream != null) {
                fileOutputStream.close();
            }
            if (scheduler != null) {
                scheduler.shutdown();
            }
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Error closing file.", Toast.LENGTH_SHORT).show();
        }
    }
}
