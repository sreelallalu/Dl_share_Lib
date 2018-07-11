package com.dlsharelibapp.lalu.share.utils;

import android.content.Context;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.util.Log;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;


public class HotspotControl {

    private static final String TAG = "HotspotControl";

    /**
     * Saves the existing configuration before creating SHAREthem hotspot.  Same is used to restore the user Hotspot Config when SHAREthem hotspot is disabled
     */
    private WifiConfiguration m_original_config_backup;

    /**
     */
    private int m_shareServerListeningPort;

    private static Method getWifiApConfiguration;
    private static Method getWifiApState;
    private static Method isWifiApEnabled;
    private static Method setWifiApEnabled;
    private static Method setWifiApConfiguration;

    private WifiManager wm;
    private String deviceName;

    private static HotspotControl instance = null;
    static final String DEFAULT_DEVICE_NAME = "Unknown";

    static {
        for (Method method : WifiManager.class.getDeclaredMethods()) {
            switch (method.getName()) {
                case "getWifiApConfiguration":
                    getWifiApConfiguration = method;
                    break;
                case "getWifiApState":
                    getWifiApState = method;
                    break;
                case "isWifiApEnabled":
                    isWifiApEnabled = method;
                    break;
                case "setWifiApEnabled":
                    setWifiApEnabled = method;
                    break;
                case "setWifiApConfiguration":
                    setWifiApConfiguration = method;
                    break;
            }
        }
    }

    private HotspotControl(Context context) {
        wm = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        deviceName = WifiUtils.getDeviceName(wm);
    }

    public static HotspotControl getInstance(Context context) {
        if (instance == null) {
            instance = new HotspotControl(context);
        }
        return instance;
    }

    public boolean isEnabled() {
        Object result = invokeSilently(isWifiApEnabled, wm);
        if (result == null) {
            return false;
        }
        return (Boolean) result;
    }

    private static Object invokeSilently(Method method, Object receiver, Object... args) {
        try {


            return method.invoke(receiver, args);


        } catch (Exception e) {
            Log.e(TAG, "exception in invoking methods: " + e.getMessage());
        }
        return null;
    }

    public static boolean isSupported() {
        return (getWifiApState != null
                && isWifiApEnabled != null
                && setWifiApEnabled != null
                && getWifiApConfiguration != null);
    }

    public WifiConfiguration getConfiguration() {
        Object result = invokeSilently(getWifiApConfiguration, wm);
        if (result == null) {
            return null;
        }
        return (WifiConfiguration) result;
    }

    private boolean setHotspotEnabled(WifiConfiguration config, boolean enabled) {

        Object result = invokeSilently(setWifiApEnabled, wm, config, enabled);
        if (result == null) {
            return false;
        }
        return (Boolean) result;
    }

    private boolean setHotspotConfig(WifiConfiguration config) {
        Object result = invokeSilently(setWifiApConfiguration, wm, config);
        if (result == null) {
            return false;
        }
        return (Boolean) result;
    }


    public boolean enableShareThemHotspot(String name, int port) {
        wm.setWifiEnabled(false);

        m_shareServerListeningPort = port;

        m_original_config_backup = getConfiguration();
        WifiConfiguration config = null;
        //Create new Open Wifi Configuration
        if (Build.VERSION.SDK_INT < 24) {

            config = new WifiConfiguration();
            config.SSID = name;
            config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
        } else {
            config = new WifiConfiguration();
            config.SSID = Contants.HOTSPOT_NAME;
            config.BSSID = "";
            config.hiddenSSID = true;
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
        }


        wm.addNetwork(config);

        //save it
        wm.saveConfiguration();

        //Log.e("hotspot enabled", "" + setHotspotEnabled(config, true));
        //   return setHotspotEnabled(config, true);
        return true;
    }

    /**
     * Calls {@link WifiManager}'s method via reflection to enabled Hotspot with existing configuration
     *
     * @return
     */
    public boolean enable() {
        wm.setWifiEnabled(false);
        return setHotspotEnabled(getConfiguration(), true);
    }

    public boolean disable() {
        //restore original hotspot config if available
        if (null != m_original_config_backup)
            setHotspotConfig(m_original_config_backup);
        m_shareServerListeningPort = 0;
        return setHotspotEnabled(m_original_config_backup, false);
    }

    public int getShareServerListeningPort() {
        return m_shareServerListeningPort;
    }

    public String getHostIpAddress() {
        if (!isEnabled()) {
            return null;
        }
        Inet4Address inet4 = getInetAddress(Inet4Address.class);
        if (null != inet4)
            return inet4.toString();
        Inet6Address inet6 = getInetAddress(Inet6Address.class);
        if (null != inet6)
            return inet6.toString();
        return null;
    }

    private <T extends InetAddress> T getInetAddress(Class<T> addressType) {
        try {
            Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
            while (ifaces.hasMoreElements()) {
                NetworkInterface iface = ifaces.nextElement();

                if (!iface.getName().equals(deviceName)) {
                    continue;
                }

                Enumeration<InetAddress> addrs = iface.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress addr = addrs.nextElement();

                    if (addressType.isInstance(addr)) {
                        return addressType.cast(addr);
                    }
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "exception in fetching inet address: " + e.getMessage());
        }
        return null;
    }

    public static class WifiScanResult {
        public String ip;

        public WifiScanResult(String ipAddr) {

            this.ip = ipAddr;
        }

        @Override
        public boolean equals(Object o) {
            if (o == this)
                return true;
            if (o == null || o.getClass() != this.getClass())
                return false;
            WifiScanResult v = (WifiScanResult) o;
            return ip.equals(v.ip);
        }
    }

    public List<WifiScanResult> getConnectedWifiClients(final int timeout,
                                                        final WifiClientConnectionListener listener) {
        List<WifiScanResult> wifiScanResults = getWifiClients();
        if (wifiScanResults == null) {
            listener.onWifiClientsScanComplete();
            return null;
        }
        final CountDownLatch latch = new CountDownLatch(wifiScanResults.size());
        ExecutorService es = Executors.newCachedThreadPool();
        for (final WifiScanResult c : wifiScanResults) {
            es.submit(new Runnable() {
                public void run() {
                    try {
                        Log.e("handle recall_hotspot", "method recall_hotspot");

                        InetAddress ip = InetAddress.getByName(c.ip);
                        if (ip.isReachable(timeout)) {
                            listener.onClientConnectionAlive(c);


                            Thread.sleep(1000);
                        } else listener.onClientConnectionDead(c);
                    } catch (IOException e) {
                        Log.e(TAG, "io exception while trying to reach client, ip: " + (c.ip));
                    } catch (InterruptedException ire) {
                        Log.e(TAG, "InterruptedException: " + ire.getMessage());
                    }
                    latch.countDown();
                }
            });
        }
        new Thread() {
            public void run() {
                try {

                    latch.await();
                } catch (InterruptedException e) {
                    Log.e(TAG, "listing clients countdown interrupted", e);
                }
                listener.onWifiClientsScanComplete();
            }
        }.start();
        return wifiScanResults;
    }

    public List<WifiScanResult> getWifiClients() {
        if (!isEnabled()) {

            return null;
        }
        List<WifiScanResult> result = new ArrayList<>();

        // Basic sanity checks
        Pattern macPattern = Pattern.compile("..:..:..:..:..:..");

        BufferedReader br = null;
        try {
            br = new BufferedReader(new FileReader("/proc/net/arp"));
            String line;


            while ((line = br.readLine()) != null) {

                String[] parts = line.split(" +");

                if (parts.length < 4 || !macPattern.matcher(parts[3]).find()) {
                    continue;
                }
                String ipAddr = parts[0];
                result.add(new WifiScanResult(ipAddr));


            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (br != null) {
                    br.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return result;
    }

    public interface WifiClientConnectionListener {
        void onClientConnectionAlive(WifiScanResult c);

        void onClientConnectionDead(WifiScanResult c);

        void onWifiClientsScanComplete();
    }

}
