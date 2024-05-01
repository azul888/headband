package com.example.headband;

import android.app.Activity;
import android.content.Context;
import android.os.AsyncTask;
import android.widget.Toast;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.ref.WeakReference;
import java.net.Socket;

public class ConnectTask extends AsyncTask<Void, Void, Boolean> {

    private final WeakReference<Activity> activityWeakReference;
    private final String serverIp;
    private final int portNumber;
    private int retryCount;
    private static final int MAX_RETRIES = 5;
    private PrintWriter writer;
    private Socket socket;

    public ConnectTask(Activity activity, String serverIp, int portNumber, int retryCount, Socket socket, PrintWriter writer) {
        this.activityWeakReference = new WeakReference<>(activity);
        this.serverIp = serverIp;
        this.portNumber = portNumber;
        this.retryCount = retryCount;
        this.socket = socket;
        this.writer = writer;
    }

    @Override
    protected Boolean doInBackground(Void... voids) {
        try {
            if (socket == null || socket.isClosed()) {
                socket = new Socket(serverIp, portNumber);
                writer = new PrintWriter(socket.getOutputStream(), true);
                return true;
            }
        } catch (IOException e) {
            return false;
        }
        return false;
    }

    @Override
    protected void onPostExecute(Boolean success) {
        Activity activity = activityWeakReference.get();
        if (activity == null || activity.isFinishing()) return; // Check if activity still exists and is not finishing

        if (success) {
            retryCount = 0; // Reset retry count on successful connection
            Toast.makeText(activity, "Connected to server.", Toast.LENGTH_SHORT).show();
        } else {
            retryCount++;
            if (retryCount < MAX_RETRIES) {
                activity.getWindow().getDecorView().postDelayed(() -> {
                    new ConnectTask(activity, serverIp, portNumber, retryCount, socket, writer).execute();
                }, (long) Math.pow(2, retryCount) * 1000);
            } else {
                Toast.makeText(activity, "Failed to connect after " + MAX_RETRIES + " attempts.", Toast.LENGTH_LONG).show();
            }
        }
    }
}
