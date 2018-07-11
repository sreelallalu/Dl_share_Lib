package com.dlsharelibapp.lalu;

import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.dlsharelibapp.lalu.share.receiver.ReceiverActivity;
import com.dlsharelibapp.lalu.share.sender.ShareActivity;
import com.dlsharelibapp.lalu.share.sender.ShareService;
import com.dlsharelibapp.lalu.share.utils.Contants;
import com.github.angads25.filepicker.controller.DialogSelectionListener;
import com.github.angads25.filepicker.model.DialogConfigs;
import com.github.angads25.filepicker.model.DialogProperties;
import com.github.angads25.filepicker.view.FilePickerDialog;

import java.io.File;
import java.lang.reflect.Method;

public class MainActivity extends AppCompatActivity {

    private WifiManager wm;
    private static Method setWifiApEnabled;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

    }


    public void receive(View view) {
        startActivity(new Intent(this, ReceiverActivity.class));

    }

    public void send(View view) {

        shareFile();


    }

    public void shareFile() {

        try {
            DialogProperties properties = new DialogProperties();
            properties.selection_mode = DialogConfigs.MULTI_MODE;
            properties.selection_type = DialogConfigs.FILE_SELECT;
            // TODO: 3/3/18 Root directory
            properties.root = FileSave.contentFile();
            properties.error_dir = new File(DialogConfigs.DEFAULT_DIR);
            properties.extensions = null;

            FilePickerDialog dialog = new FilePickerDialog(this, properties);
            dialog.setTitle("Select files to share");


            dialog.setDialogSelectionListener(new DialogSelectionListener() {
                @Override
                public void onSelectedFilePaths(String[] files) {
                    if (null == files || files.length == 0) {
                        Toast.makeText(MainActivity.this, "Select at least one file to start Share Mode", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Intent intent = new Intent(getApplicationContext(), ShareActivity.class);
                    intent.putExtra(ShareService.EXTRA_FILE_PATHS, files);
                    intent.putExtra(ShareService.EXTRA_PORT, Contants.PORT);
                    intent.putExtra(ShareService.EXTRA_SENDER_NAME, Contants.SENDER);
                    startActivity(intent);
                }
            });
            dialog.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public void hotspotstart(View view) {
        wm = (WifiManager) this.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
      /*  WifiConfiguration wifiConf = new WifiConfiguration();
        wifiConf.SSID = Contants.HOTSPOT_NAME;*/
       // wifiConf.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);

        WifiConfiguration config = new WifiConfiguration();
        config.SSID = Contants.HOTSPOT_NAME;
        config.BSSID =  "";
        config.hiddenSSID = false;
        //the config, we do not do this.
        config.preSharedKey = "testwpa2key";
        config.allowedProtocols.set(WifiConfiguration.Protocol.RSN);
        //config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
        config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
        config.status = WifiConfiguration.Status.ENABLED;
        config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);
        config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);
        config.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP);
        config.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP);
        config.allowedProtocols.set(WifiConfiguration.Protocol.RSN);
        config.allowedProtocols.set(WifiConfiguration.Protocol.WPA);

        wm.addNetwork(config);


        //save it
        wm.saveConfiguration();
        try {
            setWifiApEnabled= wm.getClass().getMethod("setWifiApEnabled", WifiConfiguration.class, boolean.class);
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }


        Intent tetherSettings = new Intent();
        tetherSettings.setClassName("com.android.settings", "com.android.settings.TetherSettings");
        startActivity(tetherSettings);



      //
        //
        //
        //
        // setHotspotEnabled(config,true);


    }

    private boolean setHotspotEnabled(WifiConfiguration config, boolean enabled) {

        Object result = invokeSilently(setWifiApEnabled, wm, config, enabled);
        Log.e("result_object",result.toString());

        if (result == null) {
            return false;
        }
        return (Boolean) result;
    }
    private static Object invokeSilently(Method method, Object receiver, Object... args) {
        try {


            return method.invoke(receiver, args);


        } catch (Exception  e) {
            Log.e("exeption", "exception in invoking methods: " + e.getMessage());
        }
        return null;
    }
}
