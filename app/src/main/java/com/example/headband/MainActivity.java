package com.example.headband;

import android.content.ContentValues;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private SensorManager sensorManager;
    private Sensor rotationVectorSensor;
    private PrintWriter writer;
    private FileOutputStream fileOutputStream;
    private Socket socket;

    private boolean isCollectingData = false;
    private boolean sendToServer = false;

    private String serverIp;
    private int portNumber;
    private int retryCount = 0;
    private static final int MAX_RETRIES = 5;
    private ExecutorService networkExecutor;

    private TextView logTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initializeSensors();
        setupUIComponents();
        //checkNetworkConnection();

        networkExecutor = Executors.newSingleThreadExecutor();
    }

    private void checkNetworkConnection() {
        final ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

        NetworkRequest request = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build();

        ConnectivityManager.NetworkCallback networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onLost(@NonNull Network network) {
                // Network is lost
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "No network connection available.", Toast.LENGTH_LONG).show());
            }
        };

        connectivityManager.registerNetworkCallback(request, networkCallback);
    }


    private void setupUIComponents() {
        SwitchCompat switchButton = findViewById(R.id.toggleDataFlow);
        EditText portNumberEdit = findViewById(R.id.port_number);
        EditText serverIPEdit = findViewById(R.id.ip_address);
        logTextView = findViewById(R.id.debug); // Assuming this TextView is added to your layout to show logs

        switchButton.setOnCheckedChangeListener((buttonView, isChecked) -> {
            sendToServer = isChecked;
            // Removed automatic scanning for devices
            updateUIForDataFlowSwitch(isChecked, serverIPEdit, portNumberEdit, switchButton);
        });
        Button button = findViewById(R.id.datacollection);
        button.setOnClickListener(v -> {
            serverIp = serverIPEdit.getText().toString();
            portNumber = Integer.parseInt(portNumberEdit.getText().toString());
            toggleDataCollection();
        });
    }

    private void updateUIForDataFlowSwitch(boolean isChecked, EditText serverIpEdit, EditText portNumberEdit, SwitchCompat switchButton) {
        if (isChecked) {
            switchButton.setText(R.string.start_data_collecting);
            serverIpEdit.setVisibility(View.VISIBLE);
            portNumberEdit.setVisibility(View.VISIBLE);
        } else {
            closeConnection();
            switchButton.setText(R.string.current_file_write);
            serverIpEdit.setVisibility(View.GONE);
            portNumberEdit.setVisibility(View.GONE);
        }
    }

    private void initializeSensors() {
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);

        if (rotationVectorSensor == null) {
            Toast.makeText(this, "Rotation vector sensor not available.", Toast.LENGTH_SHORT).show();
            finish();
        } else {
            sensorManager.registerListener(this, rotationVectorSensor, SensorManager.SENSOR_DELAY_FASTEST);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (isCollectingData) {
            sensorManager.registerListener(this, rotationVectorSensor, SensorManager.SENSOR_DELAY_FASTEST);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this);
        closeResources();  // Close resources when the activity is no longer visible
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        networkExecutor.shutdownNow(); // Properly shutdown the executor
        closeResources();  // Make sure to close all resources when the activity is destroyed
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (!isCollectingData) return;

        if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
            float[] quaternion = new float[4];
            SensorManager.getQuaternionFromVector(quaternion, event.values);
            handleQuaternionData(quaternion);
        }
    }

    private void handleQuaternionData(float[] quaternion) {
        if (sendToServer) {
            sendDataToServer(quaternion);
        } else {
            writeDataToFile(quaternion);
        }
    }

    private void establishServerConnection() {
        networkExecutor.execute(() -> {
            try {
                if (socket == null || socket.isClosed()) {
                    socket = new Socket(serverIp, portNumber);
                    writer = new PrintWriter(socket.getOutputStream(), true);
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "Connected to server.", Toast.LENGTH_SHORT).show());
                }
            } catch (IOException e) {
                runOnUiThread(() -> {
                    if (retryCount < MAX_RETRIES) {
                        retryCount++;
                        establishServerConnection(); // Retry the connection
                    } else {
                        Toast.makeText(MainActivity.this, "Failed to connect after " + MAX_RETRIES + " attempts.", Toast.LENGTH_LONG).show();
                        retryCount = 0; // Reset retry count
                    }
                });
            }
        });
    }

    private void sendDataToServer(float[] quaternion) {
        networkExecutor.execute(() -> {
            if (socket != null && socket.isConnected() && writer != null) {
                writer.printf(Locale.US, "%.4f,%.4f,%.4f,%.4f%n",
                        quaternion[0], quaternion[1], quaternion[2], quaternion[3]);
            } else {
                if (retryCount < MAX_RETRIES) {
                    retryCount++;
                    establishServerConnection(); // Attempt to reconnect
                } else {
                    retryCount = 0;
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "Connection error. Please retry later.", Toast.LENGTH_SHORT).show());
                }
            }
        });
    }

    private InetAddress getLocalIpAddress() throws SocketException {
        Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
        while (networkInterfaces.hasMoreElements()) {
            NetworkInterface networkInterface = networkInterfaces.nextElement();
            Enumeration<InetAddress> inetAddresses = networkInterface.getInetAddresses();
            while (inetAddresses.hasMoreElements()) {
                InetAddress inetAddress = inetAddresses.nextElement();
                if (!inetAddress.isLoopbackAddress() && inetAddress instanceof Inet4Address) {
                    return inetAddress;
                }
            }
        }
        return null; // No suitable address found
    }


    private void scanForDevicesOnNetwork() {
        networkExecutor.execute(() -> {
            try {
                InetAddress localInetAddress = getLocalIpAddress();
                assert localInetAddress != null;
                byte[] ip = localInetAddress.getAddress();

                // Modify the last byte of the IP address to scan the whole subnet
                for (int i = 1; i < 255; i++) {
                    ip[3] = (byte) i;
                    InetAddress address = InetAddress.getByAddress(ip);
                    if (!address.equals(localInetAddress) && address.isReachable(500)) { // Check if the address is not the local address and is reachable
                        String hostAddress = address.getHostAddress();
                        runOnUiThread(() -> {
                            EditText ipEditText = findViewById(R.id.ip_address);
                            ipEditText.setText(hostAddress);
                            updateLog("Device found at: " + hostAddress);
                        });
                    }
                }
            } catch (Exception e) {
                runOnUiThread(() -> updateLog("Network scanning error: " + e.getMessage()));
            }
        });
    }

    private void writeDataToFile(float[] quaternion) {
        String formattedData = String.format(Locale.US, "Quaternion: w=%f, x=%f, y=%f, z=%f%n",
                quaternion[0], quaternion[1], quaternion[2], quaternion[3]);
        try {
            if (fileOutputStream != null) {
                fileOutputStream.write(formattedData.getBytes());
            }
        } catch (IOException e) {
            Toast.makeText(this, "Error writing to file.", Toast.LENGTH_SHORT).show();
        }
    }

    private void toggleDataCollection() {
        if (!isCollectingData) {
            isCollectingData = true;
            if (sendToServer) {
                establishServerConnection();
            } else {
                createDataFile();
            }
        } else {
            isCollectingData = false;
            closeResources();  // Ensure resources are closed when stopping data collection
        }
    }

    private void createDataFile() {
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, "QuaternionData.txt");
        values.put(MediaStore.MediaColumns.MIME_TYPE, "text/plain");
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);

        Uri uri = getContentResolver().insert(MediaStore.Files.getContentUri("external"), values);
        try {
            assert uri != null;
            fileOutputStream = (FileOutputStream) getContentResolver().openOutputStream(uri);
        } catch (FileNotFoundException e) {
            Toast.makeText(this, "Failed to create file for data collection.", Toast.LENGTH_SHORT).show();
        }
    }

    private void closeResources() {
        try {
            if (writer != null) {
                writer.close();
            }
            if (socket != null) {
                socket.close();
            }
            if (fileOutputStream != null) {
                fileOutputStream.close();
            }
        } catch (IOException e) {
            Toast.makeText(this, "Error closing resources: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void closeConnection() {
        if (socket != null) {
            try {
                socket.close();
                writer = null;
                socket = null;
            } catch (IOException e) {
                Toast.makeText(this, "Error closing server connection", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Optional: Handle sensor accuracy changes
    }

    private void updateLog(String message) {
        logTextView.setText(message);
    }
}
