package com.syahmie.stormztv.installer;

import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Scans the local Wi-Fi network subnet for Android TV devices with ADB (port 5555) active.
 */
public final class TvScanner {
    public interface ScanCallback {
        void onDeviceFound(String ip);
        void onScanFinished(List<String> devices);
    }

    public static void scan(Context context, ScanCallback callback) {
        new Thread(() -> {
            Handler handler = new Handler(Looper.getMainLooper());
            List<String> foundIps = Collections.synchronizedList(new ArrayList<>());
            String localIp = getLocalIpAddress(context);

            if (localIp == null || !localIp.contains(".")) {
                handler.post(() -> callback.onScanFinished(foundIps));
                return;
            }

            String subnet = localIp.substring(0, localIp.lastIndexOf('.') + 1);
            ExecutorService pool = Executors.newFixedThreadPool(30);

            for (int i = 1; i <= 254; i++) {
                final String targetIp = subnet + i;
                pool.execute(() -> {
                    try {
                        Socket socket = new Socket();
                        socket.connect(new InetSocketAddress(targetIp, 5555), 350);
                        socket.close();
                        foundIps.add(targetIp);
                        handler.post(() -> callback.onDeviceFound(targetIp));
                    } catch (Exception ignored) {
                    }
                });
            }

            pool.shutdown();
            try {
                pool.awaitTermination(4, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
            }

            handler.post(() -> callback.onScanFinished(new ArrayList<>(foundIps)));
        }).start();
    }

    public static String getLocalIpAddress(Context context) {
        try {
            WifiManager wm = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wm != null) {
                WifiInfo wi = wm.getConnectionInfo();
                int ipInt = wi.getIpAddress();
                if (ipInt != 0) {
                    return String.format(java.util.Locale.US, "%d.%d.%d.%d",
                            (ipInt & 0xff), (ipInt >> 8 & 0xff), (ipInt >> 16 & 0xff), (ipInt >> 24 & 0xff));
                }
            }

            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface intf : interfaces) {
                List<InetAddress> addrs = Collections.list(intf.getInetAddresses());
                for (InetAddress addr : addrs) {
                    if (!addr.isLoopbackAddress() && addr.getHostAddress().indexOf(':') < 0) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return "192.168.1.100";
    }
}
