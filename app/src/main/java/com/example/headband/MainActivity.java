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
import java.util.concurrent.ScheduledExecutorService;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private SensorManager sensorManager;
    private Sensor accelerometer;
    private boolean isSoundPlaying = false;
    private Socket socket;
    private PrintWriter writer;
    private FileOutputStream fileOutputStream;
    private boolean isCollectingData = false;
    private boolean sendToServer = false; // Flag to determine data flow
    private ScheduledExecutorService scheduler;

    private final Object fileLock = new Object(); // Lock object for synchronization
    private int port_number;
    private String server_ip;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityMainBinding mainBinding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(mainBinding.getRoot());

        @SuppressLint("UseSwitchCompatOrMaterialCode")
        Switch switchButton = findViewById(R.id.toggleDataFlow);
        sendToServer = switchButton.isChecked();
        EditText portNumber = findViewById(R.id.port_number);
        EditText serverIP = findViewById(R.id.ip_address);
        switchButton.setOnCheckedChangeListener((buttonView, isChecked) -> {
            sendToServer = isChecked;
            if (isChecked) {
                switchButton.setText("Current: Server Send");
                serverIP.setVisibility(View.VISIBLE);
                portNumber.setVisibility(View.VISIBLE);

            } else {
                if (writer != null) {
                    // put try in a separate thread
                    new Thread(() -> {
                        try {
                            writer.close();
                            socket.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }).start();
                }
                switchButton.setText("Current: File Write");
                serverIP.setVisibility(View.GONE);
                portNumber.setVisibility(View.GONE);
            }
        });

        TextView textView = findViewById(R.id.debug);
        Button dataCollectionButton = findViewById(R.id.datacollection);
        dataCollectionButton.setOnClickListener(v -> {
            if (textView.getVisibility() == View.VISIBLE) {
                textView.setVisibility(View.GONE);
            }
            if (!isCollectingData) {
                if (sendToServer) {
                    if (serverIP.getText().toString().isEmpty() || portNumber.getText().toString().isEmpty()) {
                        Toast.makeText(this, "Please enter server IP and port number.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    server_ip = serverIP.getText().toString();
                    port_number = Integer.parseInt(portNumber.getText().toString());
                }
                startDataCollection();
            } else {
                stopDataCollection();
            }
        });

        // Initialize SensorManager and accelerometer sensor
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
        // Register the accelerometer sensor listener
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Unregister the sensor listener to save battery
        sensorManager.unregisterListener(this);
    }

//    @Override
//    public void onSensorChanged(SensorEvent event) {
//
//        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
//            float x = event.values[0];
//            float y = event.values[1];
//            float z = event.values[2];
//
//            if (isCollectingData) {
//                writeDataToFile(x, y, z);
//            }
//
//            // Calculate the total acceleration
//            double totalAcceleration = Math.sqrt(x * x + y * y + z * z);
//
//            if (totalAcceleration > 1) {
//                if (!isSoundPlaying) {
//                    // Start playing sound
//                    playSound();
//                }
//            } else {
//                if (isSoundPlaying) {
//                    // Stop playing sound
//                    stopSound();
//                }
//            }
//        }
//    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER && isCollectingData) {
            float x = event.values[0];
            float y = event.values[1];
            float z = event.values[2];

            if (sendToServer) {
                sendDataToServer(x, y, z);
            } else {
                writeDataToFile(x, y, z);
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

    //    private void startScheduler() {
    //        if (scheduler != null) {
    //            scheduler.shutdown();
    //        }
    //        scheduler = Executors.newSingleThreadScheduledExecutor();
    //        scheduler.scheduleAtFixedRate(new Runnable() {
    //            @Override
    //            public void run() {
    //                if (isCollectingData) {
    //                    synchronized (fileLock) {
    //                        try {
    //                            fileOutputStream.write(("Iteration Count: " + dataCount + "\n").getBytes());
    //                        } catch (IOException e) {
    //                            e.printStackTrace();
    //                        }
    //                    }
    //                }
    //            }
    //        }, 1, 1, TimeUnit.SECONDS);
    //    }

    //    private void writeDataToFile(float x, float y, float z) {
    //        synchronized (fileLock) {
    //            String data = "X: " + x + ", Y: " + y + ", Z: " + z + "\n";
    //            try {
    //                fileOutputStream.write(data.getBytes());
    //                dataCount++; // Increment data write count
    //            } catch (IOException e) {
    //                e.printStackTrace();
    //                Toast.makeText(this, "Error writing to file.", Toast.LENGTH_SHORT).show();
    //            }
    //        }
    //    }

    private void writeDataToFile(float x, float y, float z) {
        String data = "X: " + x + ", Y: " + y + ", Z: " + z + "\n";
        try {
            fileOutputStream.write(data.getBytes());
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Error writing to file.", Toast.LENGTH_SHORT).show();
        }
    }

    private void sendDataToServer(float x, float y, float z) {
        new Thread(() -> {
            if (writer != null) {
                try {
                    writer.printf("%.2f,%.2f,%.2f%n", x, y, z);
                } catch (Exception e) {
                    e.printStackTrace();
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "Error sending data to server", Toast.LENGTH_SHORT).show());
                }
            }
        }).start();
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
//    private void startDataCollection() {
//        isCollectingData = true;
//        Toast.makeText(this, "Starting data collection...", Toast.LENGTH_SHORT).show();
//
//        ContentValues values = new ContentValues();
//        values.put(MediaStore.MediaColumns.DISPLAY_NAME, "SensorData.txt"); // File name
//        values.put(MediaStore.MediaColumns.MIME_TYPE, "text/plain"); // File type
//        values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS); // Directory
//
//        Uri uri = getContentResolver().insert(MediaStore.Files.getContentUri("external"), values); // Using "external" for shared storage
//        try {
//            assert uri != null;
//            fileOutputStream = (FileOutputStream) getContentResolver().openOutputStream(uri);
//            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
//        } catch (FileNotFoundException e) {
//            e.printStackTrace();
//            Toast.makeText(this, "Failed to create file for data collection.", Toast.LENGTH_SHORT).show();
//        }
//    }

    private void startDataCollection() {
        isCollectingData = true;
        Toast.makeText(this, "Starting data collection...", Toast.LENGTH_SHORT).show();
        if (sendToServer) {
            new Thread(() -> {
                try {
                    socket = new Socket(server_ip, port_number); // Replace with your server IP and port
                    writer = new PrintWriter(socket.getOutputStream(), true);
                } catch (IOException e) {
                    // Handle exceptions related to input/output operations
                    runOnUiThread(() -> {
                        TextView textView = findViewById(R.id.debug);
                        textView.setVisibility(View.VISIBLE);
                        textView.setText(String.format("I/O error connecting to server: %s", e.getMessage()));
                        Toast.makeText(this, "I/O error connecting to server: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    });
                } catch (SecurityException e) {
                    // Handle security exceptions
                    runOnUiThread(() -> Toast.makeText(this, "Security error connecting to server: " + e.getMessage(), Toast.LENGTH_LONG).show());
                } catch (IllegalArgumentException e) {
                    // Handle wrong arguments passed to the socket
                    runOnUiThread(() -> Toast.makeText(this, "Argument error connecting to server: " + e.getMessage(), Toast.LENGTH_LONG).show());
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        TextView textView = findViewById(R.id.debug);
                        textView.setVisibility(View.VISIBLE);
                        textView.setText(String.format("Error connecting to server: %s", e.toString()));
                        Toast.makeText(this, "Error connecting to server: " + e.toString(), Toast.LENGTH_LONG).show();
                    });
                }
            }).start();
        } else {
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, "SensorData.txt");
            values.put(MediaStore.MediaColumns.MIME_TYPE, "text/plain");
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);

            Uri uri = getContentResolver().insert(MediaStore.Files.getContentUri("external"), values);
            try {
                fileOutputStream = (FileOutputStream) getContentResolver().openOutputStream(uri);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                Toast.makeText(this, "Failed to create file for data collection.", Toast.LENGTH_SHORT).show();
            }
        }
    }


    private void stopDataCollection() {
        isCollectingData = false;
        Toast.makeText(this, "Stopping data collection...", Toast.LENGTH_SHORT).show();
        try {
            sensorManager.unregisterListener(this);
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
}
