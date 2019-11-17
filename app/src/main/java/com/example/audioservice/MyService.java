// TODO zkusit kontorlovat stav mikrofonu a nesnazit se nahravat, pokud je pouzivan

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
import android.provider.MediaStore;
import android.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import androidx.core.content.ContextCompat;

public class MyService extends Service {
    private String TAG = "AUDIO_SLUZBA";

    private String address = "10.0.2.2";
    private int port = 50005;

    AudioRecord recorder;

    private int sampleRate = 44100;//8000;//16000 ; // 44100 for music
    private int channelConfig = AudioFormat.CHANNEL_IN_MONO;//AudioFormat.CHANNEL_CONFIGURATION_MONO;
    private int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
    private int minBufSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat) * 10;
    private volatile boolean status = true;

    public MyService() {
    }

    @Override
    public void onCreate() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Thread streamThread = new Thread(new Runnable() {

            @Override
            public void run() {

                //waitForPermissions();
                waitForPermissions();

                try {

                    DatagramSocket socket = new DatagramSocket();
                    Log.d(TAG, "Socket Created");

                    byte[] buffer = new byte[minBufSize];

                    Log.d(TAG, "Buffer created of size " + minBufSize);
                    DatagramPacket packet;

                    final InetAddress destination = InetAddress.getByName(address);
                    Log.d(TAG, "Address retrieved");

                    recorder = new AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, channelConfig, audioFormat, minBufSize); //* 10);

                    while(recorder.getState() != AudioRecord.STATE_INITIALIZED){
                        Log.i(TAG, "Waiting for recorder initialization");
                        recorder = new AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, channelConfig, audioFormat, minBufSize); //* 10);
                        try {
                            TimeUnit.SECONDS.sleep(2);
                        } catch(InterruptedException e){
                            e.printStackTrace();
                        }
                    }

                    Log.d(TAG, "Recorder initialized");

                    if (AcousticEchoCanceler.isAvailable()){
                        AcousticEchoCanceler.create(recorder.getAudioSessionId());
                        Log.i(TAG, "Creating acoustic echo canceller");
                    }

                    if(NoiseSuppressor.isAvailable()){
                        NoiseSuppressor.create(recorder.getAudioSessionId());
                        Log.i(TAG, "Creating noise suppressor");
                    }

                    recorder.startRecording();

                    while (status == true) {

                        //reading data from MIC into buffer
                        int read = recorder.read(buffer, 0, buffer.length);

                        Log.i(TAG, "Read bytes: " + read);

                        //putting buffer in the packet
                        packet = new DatagramPacket(buffer, read, destination, port);

                        //Log.e(TAG, "Preparing to send packet");
                        socket.send(packet);

                        // Clear the buffer
                        Arrays.fill(buffer, (byte)0);
                    }
                    Log.e(TAG, "Status is false");
                } catch (UnknownHostException e) {
                    Log.e(TAG, "UnknownHostException");
                } catch (IOException e) {
                    e.printStackTrace();
                    Log.e(TAG, "IOException");
                } catch (IllegalArgumentException e){
                    e.printStackTrace();
                    Log.e(TAG, "Illegal argument exception!!!");
                } catch (ExceptionInInitializerError e){
                    e.printStackTrace();
                    Log.e(TAG, "Recorder not initialised properly");
                }
            }
        });

        // start the thread
        streamThread.start();

        return START_STICKY;
    }

    @Override
    public void onDestroy(){
        status = false;
        recorder.release();
        Log.d(TAG,"Recorder released");
    }

    @Override
    public void onTaskRemoved(Intent rootIntent){
        Log.d(TAG,"Task removed");
        status = false;
        recorder.stop();
        recorder.release();
    }

    /*
    public void waitForPermissions() {

        Log.e(TAG, "Waiting for permission to record audio...");
        while (ContextCompat.checkSelfPermission(MyService.this,
                Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
        }
        Log.e(TAG, "Permission granted");
    }*/

    public void waitForPermissions() {
        /*
        Wait until the user has granted us the permission to Record Audio.
        NO CONTEXT COMPAT version
         */

        Log.e(TAG, "Waiting for permission to record audio...");
        while (this.checkCallingOrSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
        }
        Log.e(TAG, "Permission granted");
    }
}
