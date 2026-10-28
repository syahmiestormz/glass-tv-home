package com.syahmie.stormztv.installer;

import android.content.Context;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Super lightweight built-in HTTP server to serve the APK directly
 * from the phone to any Android TV web browser or Downloader app.
 */
public final class LocalApkServer {
    private static ServerSocket serverSocket;
    private static Thread serverThread;
    private static boolean running = false;

    public interface ServerStateCallback {
        void onStarted(String url);
        void onStopped();
    }

    public static synchronized void start(Context context, int port, ServerStateCallback callback) {
        if (running) return;
        running = true;

        serverThread = new Thread(() -> {
            try {
                serverSocket = new ServerSocket(port);
                String localIp = TvScanner.getLocalIpAddress(context);
                String serverUrl = "http://" + localIp + ":" + port + "/stormz-tv.apk";
                if (callback != null) callback.onStarted(serverUrl);

                while (running && !serverSocket.isClosed()) {
                    Socket client = serverSocket.accept();
                    new Thread(() -> handleClient(context, client)).start();
                }
            } catch (Exception e) {
                stop();
                if (callback != null) callback.onStopped();
            }
        });
        serverThread.start();
    }

    public static synchronized void stop() {
        running = false;
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (Exception ignored) {
        }
    }

    public static boolean isRunning() {
        return running;
    }

    private static void handleClient(Context context, Socket client) {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream()));
            String line = reader.readLine();
            if (line == null) return;

            OutputStream out = client.getOutputStream();

            if (line.contains("GET /stormz-tv.apk")) {
                InputStream is = context.getAssets().open("stormz-tv.apk");
                long size = context.getAssets().openFd("stormz-tv.apk").getLength();

                String header = "HTTP/1.1 200 OK\r\n"
                        + "Content-Type: application/vnd.android.package-archive\r\n"
                        + "Content-Length: " + size + "\r\n"
                        + "Content-Disposition: attachment; filename=\"stormz-tv.apk\"\r\n"
                        + "Connection: close\r\n\r\n";
                out.write(header.getBytes("UTF-8"));

                byte[] buf = new byte[16 * 1024];
                int r;
                while ((r = is.read(buf)) > 0) {
                    out.write(buf, 0, r);
                }
                is.close();
                out.flush();
            } else {
                // Landing web page for browser
                String html = "<!DOCTYPE html><html><head><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'>"
                        + "<title>Stormz TV Installer</title>"
                        + "<style>body{background:#0c0e14;color:#fff;font-family:sans-serif;text-align:center;padding:40px 20px}"
                        + "h1{font-size:28px;margin-bottom:10px}p{color:#aaa;margin-bottom:30px}"
                        + ".btn{background:#fff;color:#000;padding:16px 32px;font-size:18px;font-weight:bold;text-decoration:none;border-radius:12px;display:inline-block;box-shadow:0 4px 15px rgba(255,255,255,0.2)}"
                        + "</style></head><body>"
                        + "<h1>📺 Stormz TV</h1>"
                        + "<p>Muat turun fail pemasangan APK terus ke Android TV anda.</p>"
                        + "<a href='/stormz-tv.apk' class='btn'>📥 Muat Turun APK Stormz TV</a>"
                        + "</body></html>";
                byte[] htmlBytes = html.getBytes("UTF-8");
                String header = "HTTP/1.1 200 OK\r\nContent-Type: text/html\r\nContent-Length: " + htmlBytes.length + "\r\nConnection: close\r\n\r\n";
                out.write(header.getBytes("UTF-8"));
                out.write(htmlBytes);
                out.flush();
            }
            client.close();
        } catch (Exception ignored) {
        }
    }
}
