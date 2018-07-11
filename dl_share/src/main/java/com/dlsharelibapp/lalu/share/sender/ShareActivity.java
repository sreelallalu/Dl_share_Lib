/*
 * Copyright 2017 Srihari Yachamaneni
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.dlsharelibapp.lalu.share.sender;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.net.wifi.WifiConfiguration;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SwitchCompat;
import android.text.Html;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toolbar;

import com.dlsharelibapp.lalu.R;
import com.dlsharelibapp.lalu.share.ripple.RippleBackground;
import com.dlsharelibapp.lalu.share.utils.DividerItemDecoration;
import com.dlsharelibapp.lalu.share.utils.HotspotControl;
import com.dlsharelibapp.lalu.share.utils.RecyclerViewArrayAdapter;
import com.dlsharelibapp.lalu.share.utils.Utils;
import com.dlsharelibapp.lalu.share.utils.WifiUtils;

import org.json.JSONArray;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.dlsharelibapp.lalu.share.sender.ShareActivity.ShareUIHandler.LIST_API_CLIENTS;
import static com.dlsharelibapp.lalu.share.sender.ShareActivity.ShareUIHandler.UPDATE_AP_STATUS;
import static com.dlsharelibapp.lalu.share.utils.Utils.getRandomColor;


/**
 * Controls Hotspot service to share files passed through intent.<br>
 * Displays sender IP and name for receiver to connect to when turned on
 * <p>
 * Created by Sri on 18/12/16.
 */
public class ShareActivity extends AppCompatActivity {

    public static final String TAG = "TAG";
    public static final String PREFERENCES_KEY_SHARED_FILE_PATHS = "sharethem_shared_file_paths";
    public static final String PREFERENCES_KEY_DATA_WARNING_SKIP = "sharethem_data_warning_skip";
    private static final int REQUEST_WRITE_SETTINGS = 1;


    private ReceiversListingAdapter m_receiversListAdapter;
    private CompoundButton.OnCheckedChangeListener m_sender_ap_switch_listener;

    private ShareUIHandler m_uiUpdateHandler;
    private BroadcastReceiver m_p2pServerUpdatesListener;

    private HotspotControl hotspotControl;
    private boolean isApEnabled = false;
    private boolean shouldAutoConnect = true;

    private String[] m_sharedFilePaths = null;

    private boolean checkfocusview;
    private static AlertDialog.Builder alertdialog_write;
    private AlertDialog alertDialog;
    private static AlertDialog alertdialog_write_show;
    private LinearLayout screen_mainview;
    private Toolbar toolbar;
    private SwitchCompat sender_switch;
    private TextView wifi_hint;
    private TextView item_label;
    private RelativeLayout screen_down;
    private RecyclerView recycler_view;
    private TextView receiver_txt;
    private LinearLayout screen_searching;
    private RippleBackground ripple_content;
    private ImageView centerImage;
    private ImageView foundDevice;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.p_p_sender);


        screen_mainview = findViewById(R.id.screen_mainview);

        sender_switch = findViewById(R.id.sender_switch);
        wifi_hint = findViewById(R.id.wifi_hint);
        item_label = findViewById(R.id.item_label);
        screen_down = findViewById(R.id.screen_down);
        recycler_view = findViewById(R.id.recycler_view);
        receiver_txt = findViewById(R.id.receiver_txt);
        screen_searching = findViewById(R.id.screen_searching);
        ripple_content = findViewById(R.id.ripple_content);
        centerImage = findViewById(R.id.centerImage);
        foundDevice = findViewById(R.id.foundDevice);



 /*       getSupportActionBar().setDisplayShowTitleEnabled(true);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);*/
        // TODO: 18/5/18 hotspot control


        hotspotControl = HotspotControl.getInstance(getApplicationContext());

        recycler_view.setLayoutManager(new LinearLayoutManager(getApplicationContext()));
        recycler_view.addItemDecoration(new DividerItemDecoration(getResources().getDrawable(R.drawable.p_p_list_driver)));
        dialog_Write_Setting_Show();
        //if fie paths are found, save'em into preferences. OR find them in prefs
        if (null != getIntent() && getIntent().hasExtra(ShareService.EXTRA_FILE_PATHS))
            m_sharedFilePaths = getIntent().getStringArrayExtra(ShareService.EXTRA_FILE_PATHS);
        SharedPreferences prefs = getSharedPreferences(getPackageName(), Context.MODE_PRIVATE);
        // TODO: 18/5/1 data save to SharedPreferennce


        if (null == m_sharedFilePaths)
            m_sharedFilePaths = Utils.toStringArray(prefs.getString(PREFERENCES_KEY_SHARED_FILE_PATHS, null));
        else
            prefs.edit().putString(PREFERENCES_KEY_SHARED_FILE_PATHS, new JSONArray(Arrays.asList(m_sharedFilePaths)).toString()).commit();


        m_receiversListAdapter = new ReceiversListingAdapter(new ArrayList<HotspotControl.WifiScanResult>(), m_sharedFilePaths);
        recycler_view.setAdapter(m_receiversListAdapter);


        m_sender_ap_switch_listener = new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                shouldAutoConnect = isChecked;

                switchHotspotEnable(isChecked);

            }
        };
        sender_switch.setOnCheckedChangeListener(m_sender_ap_switch_listener);


        // TODO: 18/5/18 listen What listing .....
        m_p2pServerUpdatesListener = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
//listing..ipsss
                if (isFinishing() || null == intent)
                    return;
                int intentType = intent.getIntExtra(ShareService.ShareIntents.TYPE, 0);
                if (intentType == ShareService.ShareIntents.Types.FILE_TRANSFER_STATUS) {
                    String fileName = intent.getStringExtra(ShareService.ShareIntents.SHARE_SERVER_UPDATE_FILE_NAME);

                    updateReceiverListItem(intent.getStringExtra(ShareService.ShareIntents.SHARE_CLIENT_IP), intent.getIntExtra(ShareService.ShareIntents.SHARE_TRANSFER_PROGRESS, -1), intent.getStringExtra(ShareService.ShareIntents.SHARE_SERVER_UPDATE_TEXT), fileName);
                } else if (intentType == ShareService.ShareIntents.Types.AP_DISABLED_ACKNOWLEDGEMENT) {
                    shouldAutoConnect = false;
                    resetSenderUi(false);
                }
            }
        };
        registerReceiver(m_p2pServerUpdatesListener, new IntentFilter(ShareService.ShareIntents.SHARE_SERVER_UPDATES_INTENT_ACTION));
    }


    private void switchHotspotEnable(boolean isChecked) {

        if (isChecked) {

            if (Build.VERSION.SDK_INT >= 23 &&
                    Utils.getTargetSDKVersion(getApplicationContext()) >= 23 && !Settings.System.canWrite(ShareActivity.this))

            {


                changeApControlCheckedStatus(false);
                showdialog_turn_on_hotspot();

            } else if (!getSharedPreferences(getPackageName(), Context.MODE_PRIVATE).getBoolean(PREFERENCES_KEY_DATA_WARNING_SKIP, false) && Utils.isMobileDataEnabled(getApplicationContext())) {
                changeApControlCheckedStatus(false);
                enableAp();

                return;
            } else {
                changeApControlCheckedStatus(false);
                enableAp();

            }

        } else {
            turn_off_hotspot();

        }


    }

    private void turn_off_hotspot() {

        changeApControlCheckedStatus(true);
        showMessageDialogWithListner(getString(R.string.p2p_sender_close_warning), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                Log.d(TAG, "sending intent to service to stop p2p..");
                dialog.dismiss();
                resetSenderUi(true);
            }
        }, true, false);
    }

    private void showdialog_turn_on_hotspot() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.System.canWrite(ShareActivity.this))

            {

                if (alertdialog_write_show != null) {
                    alertdialog_write_show.show();
                }

            } else {
                if (alertdialog_write_show != null) {
                    alertdialog_write_show.dismiss();
                }
            }

        }

    }

    public void dialog_Write_Setting_Show() {
        alertdialog_write = new AlertDialog.Builder(this, R.style.AppCompatAlertDialogStyle);
        alertdialog_write.setCancelable(true);
        alertdialog_write.setMessage(Html.fromHtml(getString(R.string.p2p_sender_system_settings_permission_prompt)));
        alertdialog_write.setPositiveButton(getString(R.string.Action_Ok), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                Intent intent = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS);
                intent.setData(Uri.parse("package:" + getPackageName()));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivityForResult(intent, REQUEST_WRITE_SETTINGS);
            }
        });

        alertdialog_write_show = alertdialog_write.create();


    }

    @Override
    protected void onResumeFragments() {
        super.onResumeFragments();
    }


    @Override
    protected void onPostResume() {
        super.onPostResume();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            if (requestCode == REQUEST_WRITE_SETTINGS) {
                Log.e("succ", "call succ");
                switchHotspotEnable(true);

            } else {
                Log.e("succ", "call backfail");
            }
        }


    }


    @Override
    protected void onResume() {
        super.onResume();
        if (alertdialog_write_show != null) {
            alertdialog_write_show.dismiss();
        }

        //If service is already running, change UI and display info for receiver
        if (Utils.isShareServiceRunning(getApplicationContext())) {
            if (!sender_switch.isChecked()) {
                changeApControlCheckedStatus(true);
                Log.e("resume", "resume1");

            }
            Log.e("resume", "resume2");

            refreshApData();

            screen_down.setVisibility(View.VISIBLE);
        } else if (sender_switch.isChecked()) {
            changeApControlCheckedStatus(false);
            resetSenderUi(false);
            Log.e("resume", "resume3");

        }
        //switch on sender mode if not already
        else if (shouldAutoConnect) {
            Log.e("resume", "checking");
            sender_switch.setChecked(true);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }


    @Override
    public void onBackPressed() {
        new AlertDialog.Builder(this)

                .setIcon(android.R.drawable.ic_dialog_alert)
                .setTitle("sending service to stop ")
                .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {

                        resetUionBack(true);
                    }
                })
                .setNegativeButton("No", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        dialogInterface.cancel();
                        listApClients();
                    }
                })
                .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (null != m_p2pServerUpdatesListener)
            unregisterReceiver(m_p2pServerUpdatesListener);
        if (null != m_uiUpdateHandler)
            m_uiUpdateHandler.removeCallbacksAndMessages(null);
        m_uiUpdateHandler = null;
    }

    public void showMessageDialogWithListner(String message,
                                             DialogInterface.OnClickListener listner, boolean showNegavtive,
                                             final boolean finishCurrentActivity) {
        if (isFinishing())
            return;


        final AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.AppCompatAlertDialogStyle);
        builder.setCancelable(true);
        builder.setMessage(Html.fromHtml(message));
        builder.setPositiveButton(getString(R.string.Action_Ok), listner);
        if (showNegavtive)
            builder.setNegativeButton(getString(R.string.Action_cancel),
                    new DialogInterface.OnClickListener() {

                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            if (finishCurrentActivity)
                                finish();
                            else dialog.dismiss();
                        }
                    });
        builder.show();
    }


    // TODO: 3/3/18  shows the mobile data off
    public void showDataWarningDialog() {
        if (isFinishing())
            return;
        final AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.AppCompatAlertDialogStyle);
        builder.setCancelable(false);
        builder.setMessage(getString(R.string.sender_data_on_warning));
        builder.setPositiveButton(getString(R.string.label_settings), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                startActivity(new Intent(
                        Settings.ACTION_SETTINGS));
            }
        });
        builder.setNegativeButton(getString(R.string.label_thats_ok),
                new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        changeApControlCheckedStatus(true);
                        // m_sender_wifi_info.setText(getString(R.string.p2p_sender_hint_connecting));
                        enableAp();
                    }
                });
        builder.setNeutralButton(getString(R.string.label_dont_ask), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                SharedPreferences prefs = getSharedPreferences(getPackageName(), Context.MODE_PRIVATE);
                prefs.edit().putBoolean(PREFERENCES_KEY_DATA_WARNING_SKIP, true).apply();
                changeApControlCheckedStatus(true);
                //   m_sender_wifi_info.setText(getString(R.string.p2p_sender_hint_connecting));
                enableAp();
            }
        });
        builder.show();
    }

    /**
     * Shows shared File urls
     */
    void showSharedFilesDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Shared Files");
        builder.setItems(m_sharedFilePaths, null);
        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                dialogInterface.dismiss();
            }
        });
        builder.show();
    }
    //endregion: Activity Methods


    // TODO: 3/3/18 region: Hotspot Control

    private void enableAp() {
        ShowScreen();
        startP2pSenderWatchService();
        refreshApData();

        screen_down.setVisibility(View.VISIBLE);
    }

    private void ShowScreen() {

        screen_searching.setVisibility(View.VISIBLE);
        //   binding.screenMainview.getBackground().setAlpha(165);
        ripple_content.startRippleAnimation();
    }

    private void ScreenOff() {
        //binding.screenMainview.getBackground().setAlpha(255);
        ripple_content.stopRippleAnimation();
        screen_searching.setVisibility(View.GONE);

    }


    private void disableAp() {
        //Send STOP action to service
        Intent p2pServiceIntent = new Intent(getApplicationContext(), ShareService.class);
        p2pServiceIntent.setAction(ShareService.WIFI_AP_ACTION_STOP);
        startService(p2pServiceIntent);
        isApEnabled = false;
    }

    /**
     * Starts {@link ShareService} with intent action {@link ShareService#WIFI_AP_ACTION_START} to enableShareThemHotspot Hotspot and start {@link ShareServer}.
     */
    private void startP2pSenderWatchService() {
        Intent p2pServiceIntent = new Intent(getApplicationContext(), ShareService.class);
        p2pServiceIntent.putExtra(ShareService.EXTRA_FILE_PATHS, m_sharedFilePaths);
        if (null != getIntent()) {
            p2pServiceIntent.putExtra(ShareService.EXTRA_PORT, getIntent().getIntExtra(ShareService.EXTRA_PORT, 0));
            p2pServiceIntent.putExtra(ShareService.EXTRA_SENDER_NAME, getIntent().getStringExtra(ShareService.EXTRA_SENDER_NAME));
        }
        p2pServiceIntent.setAction(ShareService.WIFI_AP_ACTION_START);
        startService(p2pServiceIntent);
    }

    /**
     * Starts {@link ShareService} with intent action {@link ShareService#WIFI_AP_ACTION_START_CHECK} to make {@link ShareService} constantly check for Hotspot status. (Sometimes Hotspot tend to stop if stayed idle for long enough. So this check makes sure {@link ShareService} is only alive if Hostspot is enaled.)
     */
    private void startHostspotCheckOnService() {
        Intent p2pServiceIntent = new Intent(getApplicationContext(), ShareService.class);
        p2pServiceIntent.setAction(ShareService.WIFI_AP_ACTION_START_CHECK);
        startService(p2pServiceIntent);
    }

    /**
     * Calls methods - {@link ShareActivity#updateApStatus()} & {@link ShareActivity#listApClients()} which are responsible for displaying Hotpot information and Listing connected clients to the same
     */
    private void refreshApData() {
        if (null == m_uiUpdateHandler)
            m_uiUpdateHandler = new ShareUIHandler(this);
        updateApStatus();
        listApClients();
    }

    /**
     * Updates Hotspot configuration info like Name, IP if enabled.<br> Posts a message to {@link ShareUIHandler} to call itself every 1500ms
     */
    private void updateApStatus() {
        if (!HotspotControl.isSupported()) {
            wifi_hint.setText("Warning: Hotspot mode not supported!\n");
        }
        if (hotspotControl.isEnabled()) {
            if (!isApEnabled) {
                isApEnabled = true;
                startHostspotCheckOnService();
            }
            WifiConfiguration config = hotspotControl.getConfiguration();
            String ip = Build.VERSION.SDK_INT >= 23 ? WifiUtils.getHostIpAddress() : hotspotControl.getHostIpAddress();
            if (TextUtils.isEmpty(ip))
                ip = "";
            else
                ip = ip.replace("/", "");
            //     m_toolbar.setSubtitle(getString(R.string.p2p_sender_subtitle));
            //m_sender_wifi_info.setText(getString(R.string.p2p_sender_hint_wifi_connected, config.SSID, "http://" + ip + ":" + hotspotControl.getShareServerListeningPort()));
            if (item_label.getVisibility() == View.GONE) {
                item_label.append(String.valueOf(m_sharedFilePaths.length));
                item_label.setVisibility(View.VISIBLE);
            }
        }
        if (null != m_uiUpdateHandler) {
            m_uiUpdateHandler.removeMessages(UPDATE_AP_STATUS);
            m_uiUpdateHandler.sendEmptyMessageDelayed(UPDATE_AP_STATUS, 1500);
        }
    }

    // TODO: 18/5/18 Client listner --->
    private synchronized void listApClients() {
        if (hotspotControl == null) {
            return;
        }
        hotspotControl.getConnectedWifiClients(2000,
                new HotspotControl.WifiClientConnectionListener() {
                    public void onClientConnectionAlive(final HotspotControl.WifiScanResult wifiScanResult) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {


                                addReceiverListItem(wifiScanResult);
                            }
                        });
                    }

                    @Override
                    public void onClientConnectionDead(final HotspotControl.WifiScanResult c) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {


                                onReceiverDisconnected(c.ip);
                            }
                        });
                    }

                    public void onWifiClientsScanComplete() {

                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (null != m_uiUpdateHandler) {
                                    m_uiUpdateHandler.removeMessages(LIST_API_CLIENTS);
                                    m_uiUpdateHandler.sendEmptyMessageDelayed(LIST_API_CLIENTS, 1000);

                                }
                            }
                        });
                    }
                }

        );
    }

    // TODO: 18/5/18 reset Sender Ui
    private void resetUionBack(boolean disableAP) {
        try {

            m_uiUpdateHandler.removeCallbacksAndMessages(null);
            screen_down.setVisibility(View.GONE);
            item_label.setVisibility(View.GONE);
            // m_toolbar.setSubtitle("");
            if (disableAP)
                disableAp();
            else {
                changeApControlCheckedStatus(false);
            }
            if (null != m_receiversListAdapter)
                m_receiversListAdapter.clear();
            receiver_txt.setVisibility(View.VISIBLE);

            super.onBackPressed();
        } catch (Exception e) {
            super.onBackPressed();
            e.printStackTrace();
        }

    }


    private void resetSenderUi(boolean disableAP) {
        m_uiUpdateHandler.removeCallbacksAndMessages(null);
        screen_down.setVisibility(View.GONE);
        item_label.setVisibility(View.GONE);
        // m_toolbar.setSubtitle("");
        if (disableAP)
            disableAp();
        else {
            changeApControlCheckedStatus(false);
        }
        if (null != m_receiversListAdapter)
            m_receiversListAdapter.clear();
        receiver_txt.setVisibility(View.VISIBLE);
    }

    /**
     * Changes checked status without invoking listener. Removes @{@link CompoundButton.OnCheckedChangeListener} on @{@link SwitchCompat} button before changing checked status
     *
     * @param checked if <code>true</code>, sets @{@link SwitchCompat} checked.
     */
    private void changeApControlCheckedStatus(boolean checked) {
        sender_switch.setOnCheckedChangeListener(null);
        sender_switch.setChecked(checked);
        sender_switch.setOnCheckedChangeListener(m_sender_ap_switch_listener);
    }


    // todo Update information
    private void updateReceiverListItem(String ip, int progress, String updatetext, String
            fileName) {
        View taskListItem = recycler_view.findViewWithTag(ip);
        if (null != taskListItem) {
            ReceiversListItemHolder holder = new ReceiversListItemHolder(taskListItem);
            if (updatetext.contains("Error in file transfer")) {
                holder.resetTransferInfo(fileName);
                return;
            }
            holder.update(fileName, updatetext, progress);
        } else {
            Log.e(TAG, "no list item found with this IP******");
        }
    }


    private void addReceiverListItem(HotspotControl.WifiScanResult wifiScanResult) {
        List<HotspotControl.WifiScanResult> wifiScanResults = m_receiversListAdapter.getObjects();
        if (null != wifiScanResults && wifiScanResults.indexOf(wifiScanResult) != -1) {
            Log.e(TAG, "duplicate client, try updating connection status");
            View taskListItem = recycler_view.findViewWithTag(wifiScanResult.ip);

            ReceiversListItemHolder holder = new ReceiversListItemHolder(taskListItem);


            if (holder.isDisconnected()) {
                Log.d(TAG, "disconnect: " + wifiScanResult.ip);

                holder.setConnectedUi(wifiScanResult);
            }
        } else {

            m_receiversListAdapter.add(wifiScanResult);
            if (receiver_txt.getVisibility() == View.VISIBLE)
                receiver_txt.setVisibility(View.GONE);
        }
    }

    private void onReceiverDisconnected(String ip) {

        List<HotspotControl.WifiScanResult> wifiScanResults = m_receiversListAdapter.getObjects();

        View taskListItem = recycler_view.findViewWithTag(ip);
        if (null != taskListItem) {
            ReceiversListItemHolder holder = new ReceiversListItemHolder(taskListItem);
            if (!holder.isDisconnected())
                if (wifiScanResults.size() == 1) {

                }
            holder.setDisconnectedUi();
//            m_receiversListAdapter.remove(new WifiApControl.Client(ip, null, null));
        }
        if (m_receiversListAdapter.getItemCount() == 0)
            receiver_txt.setVisibility(View.VISIBLE);
    }

    static class ReceiversListItemHolder extends RecyclerView.ViewHolder {
        TextView title, connection_status;

        ReceiversListItemHolder(View itemView) {
            super(itemView);
            title = (TextView) itemView.findViewById(R.id.p2p_receiver_title);
            connection_status = (TextView) itemView.findViewById(R.id.p2p_receiver_connection_status);
        }

        void setConnectedUi(HotspotControl.WifiScanResult wifiScanResult) {
            title.setText(wifiScanResult.ip);
            connection_status.setText("Connected");
            connection_status.setTextColor(Color.GREEN);
        }

        void resetTransferInfo(String fileName) {
            View v = itemView.findViewWithTag(fileName);
            if (null == v) {
                Log.e(TAG, "resetTransferInfo - no view found with file name tag!!");
                return;
            }
            ((TextView) v).setText("");
        }

        void update(String fileName, String transferData, int progress) {
            View v = itemView.findViewWithTag(fileName);
            if (null == v) {
                Log.e(TAG, "update - no view found with file name tag!!");
                return;
            }
            if (v.getVisibility() == View.GONE)
                v.setVisibility(View.VISIBLE);
            ((TextView) v).setText(transferData);
        }

        void setDisconnectedUi() {
            connection_status.setText("Disconnected");
            connection_status.setTextColor(Color.RED);

        }

        boolean isDisconnected() {
            return "Disconnected".equalsIgnoreCase(connection_status.getText().toString());
        }
    }

    private class ReceiversListingAdapter extends RecyclerViewArrayAdapter<HotspotControl.WifiScanResult, ReceiversListItemHolder> {
        String[] sharedFiles;

        ReceiversListingAdapter(List<HotspotControl.WifiScanResult> objects, String[] sharedFiles) {
            super(objects);
            this.sharedFiles = sharedFiles;
        }

        @Override
        public ReceiversListItemHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            LinearLayout itemView = (LinearLayout) LayoutInflater.from(parent.getContext()).
                    inflate(R.layout.p_p_sender_one_item, parent, false);
            //Add at least those many textviews of shared files list size so that if a receiver decides to download them all at once, list item can manage to show status of all file downloads
            if (null != sharedFiles && sharedFiles.length > 0)
                for (int i = 0; i < sharedFiles.length; i++) {
                    TextView statusView = (TextView) LayoutInflater.from(parent.getContext()).
                            inflate(R.layout.p_p_sender_include_one, parent, false);
                    statusView.setTag(sharedFiles[i].substring(sharedFiles[i].lastIndexOf('/') + 1, sharedFiles[i].length()));
                    statusView.setVisibility(View.GONE);
                    statusView.setTextColor(getRandomColor());
                    itemView.addView(statusView);
                }
            return new ReceiversListItemHolder(itemView);
        }

        @Override
        public void onBindViewHolder(ReceiversListItemHolder holder, int position) {
            HotspotControl.WifiScanResult receiver = mObjects.get(position);
            if (null == receiver)
                return;
            holder.itemView.setTag(receiver.ip);
            holder.setConnectedUi(receiver);
        }

        @Override
        public int getItemCount() {
            try {
                if (mObjects.size() == 0) {

                    ShowScreen();
                } else

                {
                    ScreenOff();

                }

            } catch (Exception e) {
                ScreenOff();
                e.printStackTrace();
            }

            return super.getItemCount();
        }
    }
    //endregion: Wifi Clients Listing

    //region: UI Handler
    static class ShareUIHandler extends Handler {
        WeakReference<ShareActivity> mActivity;

        static final int LIST_API_CLIENTS = 100;
        static final int UPDATE_AP_STATUS = 101;

        ShareUIHandler(ShareActivity activity) {
            mActivity = new WeakReference<>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            ShareActivity activity = mActivity.get();
            if (null == activity || msg == null) {
                return;
            }
            if (msg.what == LIST_API_CLIENTS) {

                activity.listApClients();
            } else if (msg.what == UPDATE_AP_STATUS) {
                activity.updateApStatus();
            }
        }
    }
    //endregion: UI Handler

}
