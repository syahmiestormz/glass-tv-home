package com.syahmie.stormztv.installer;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import javax.crypto.Cipher;

/**
 * Pure Java Wireless ADB client for installing Stormz TV directly
 * to any Android TV / Google TV over local Wi-Fi without needing a PC.
 */
public final class AdbInstaller {
    private static final int A_CNXN = 0x4e584e43;
    private static final int A_AUTH = 0x48545541;
    private static final int A_OPEN = 0x4e45504f;
    private static final int A_OKAY = 0x59414b4f;
    private static final int A_CLSE = 0x45534c43;
    private static final int A_WRTE = 0x45545257;

    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface InstallCallback {
        void onProgress(int percent, String message);
        void onSuccess(String message);
        void onError(String error);
    }

    public static void install(Context context, String ip, int port, InstallCallback callback) {
        new Thread(() -> {
            Socket socket = null;
            try {
                postProgress(callback, 5, "Menyambung ke Android TV (" + ip + ":" + port + ")...");
                socket = new Socket();
                socket.connect(new InetSocketAddress(ip, port), 6000);
                socket.setSoTimeout(35000);

                InputStream in = socket.getInputStream();
                OutputStream out = socket.getOutputStream();

                // 1. Send CNXN
                sendMessage(out, A_CNXN, 0x01000000, 256 * 1024, "host::stormz-tv-installer\0".getBytes("UTF-8"));
                Message resp = readMessage(in);

                // 2. Handle Authentication
                if (resp.cmd == A_AUTH && resp.arg0 == 1) { // Token
                    postProgress(callback, 15, "Menentusahkan kunci keselamatan ADB...");
                    PrivateKey privKey = loadPrivateKey(context);
                    if (privKey == null) {
                        throw new IOException("Kunci ADB telefon ini tidak dapat dicipta.");
                    }

                    byte[] asn1Token = new byte[35];
                    byte[] prefix = new byte[]{0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e, 0x03, 0x02, 0x1a, 0x05, 0x00, 0x04, 0x14};
                    System.arraycopy(prefix, 0, asn1Token, 0, prefix.length);
                    System.arraycopy(resp.data, 0, asn1Token, prefix.length, 20);

                    Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
                    cipher.init(Cipher.ENCRYPT_MODE, privKey);
                    byte[] signature = cipher.doFinal(asn1Token);

                    sendMessage(out, A_AUTH, 2, 0, signature);
                    resp = readMessage(in);

                    if (resp.cmd != A_CNXN) {
                        // Send Public key to prompt TV
                        postProgress(callback, 20, "Sila tekan 'Allow' pada skrin TV anda...");
                        String pubKey = loadPublicKey(context);
                        if (pubKey != null) {
                            sendMessage(out, A_AUTH, 3, 0, (pubKey + " stormz-tv-installer\0").getBytes("UTF-8"));
                            resp = readMessage(in);
                        }
                    }
                }

                if (resp.cmd != A_CNXN) {
                    throw new IOException("Sambungan ditolak oleh TV. Sila benarkan 'USB Debugging' pada skrin TV.");
                }

                postProgress(callback, 30, "Sambungan TV disahkan! Membuka aliran pemasangan...");

                // Read bundled APK from assets
                InputStream apkStream = context.getAssets().open("stormz-tv.apk");
                long apkSize = context.getAssets().openFd("stormz-tv.apk").getLength();

                // Open install stream: exec:pm install -r -d -S <size>
                int localId = 1;
                String cmd = "exec:pm install -r -d -S " + apkSize + "\0";
                sendMessage(out, A_OPEN, localId, 0, cmd.getBytes("UTF-8"));

                Message ack = readMessage(in);
                if (ack.cmd != A_OKAY) {
                    apkStream.close();
                    throw new IOException("Gagal membuka aliran PackageManager di TV.");
                }
                int remoteId = ack.arg0;

                // Stream APK chunks to TV
                byte[] buffer = new byte[64 * 1024];
                int read;
                long totalSent = 0;

                while ((read = apkStream.read(buffer)) > 0) {
                    byte[] chunk = new byte[read];
                    System.arraycopy(buffer, 0, chunk, 0, read);
                    sendMessage(out, A_WRTE, localId, remoteId, chunk);
                    totalSent += read;

                    int percent = (int) (30 + (totalSent * 55 / apkSize));
                    postProgress(callback, percent, "Memindahkan Stormz TV (" + (totalSent / 1024) + " KB / " + (apkSize / 1024) + " KB)...");

                    Message streamAck = readMessage(in);
                    if (streamAck.cmd != A_OKAY) {
                        break;
                    }
                }
                apkStream.close();

                postProgress(callback, 90, "Memasang fail APK pada sistem Android TV...");

                // Read PM result
                StringBuilder pmOutput = new StringBuilder();
                while (true) {
                    Message m = readMessage(in);
                    if (m.cmd == A_WRTE) {
                        pmOutput.append(new String(m.data));
                        sendMessage(out, A_OKAY, localId, remoteId, null);
                    } else if (m.cmd == A_CLSE) {
                        sendMessage(out, A_CLSE, localId, remoteId, null);
                        break;
                    }
                }

                String res = pmOutput.toString().trim();
                if (res.contains("Success")) {
                    postProgress(callback, 100, "Pemasangan Berjaya!");
                    postSuccess(callback, "✅ Stormz TV berjaya dipasang ke TV (" + ip + ")!");
                } else {
                    throw new IOException("TV PM Ralat: " + (res.isEmpty() ? "Tidak diketahui" : res));
                }

            } catch (Exception e) {
                postError(callback, "Ralat: " + e.getMessage());
            } finally {
                if (socket != null) {
                    try {
                        socket.close();
                    } catch (Exception ignored) {
                    }
                }
            }
        }).start();
    }

    public static void launchTvHome(Context context, String ip, int port, InstallCallback callback) {
        new Thread(() -> {
            Socket socket = null;
            try {
                postProgress(callback, 10, "Menyambung ke TV untuk melancarkan launcher...");
                socket = new Socket();
                socket.connect(new InetSocketAddress(ip, port), 5000);
                socket.setSoTimeout(10000);

                InputStream in = socket.getInputStream();
                OutputStream out = socket.getOutputStream();

                sendMessage(out, A_CNXN, 0x01000000, 256 * 1024, "host::stormz-tv-installer\0".getBytes("UTF-8"));
                Message resp = readMessage(in);

                if (resp.cmd == A_AUTH && resp.arg0 == 1) {
                    PrivateKey privKey = loadPrivateKey(context);
                    byte[] asn1Token = new byte[35];
                    byte[] prefix = new byte[]{0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e, 0x03, 0x02, 0x1a, 0x05, 0x00, 0x04, 0x14};
                    System.arraycopy(prefix, 0, asn1Token, 0, prefix.length);
                    System.arraycopy(resp.data, 0, asn1Token, prefix.length, 20);

                    Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
                    cipher.init(Cipher.ENCRYPT_MODE, privKey);
                    byte[] signature = cipher.doFinal(asn1Token);

                    sendMessage(out, A_AUTH, 2, 0, signature);
                    resp = readMessage(in);
                }

                if (resp.cmd != A_CNXN) {
                    throw new IOException("Kebenaran ADB ditolak oleh TV.");
                }

                // Send am start
                int localId = 2;
                String cmd = "exec:am start -n com.syahmie.stormztv/.MainActivity\0";
                sendMessage(out, A_OPEN, localId, 0, cmd.getBytes("UTF-8"));

                Message ack = readMessage(in);
                if (ack.cmd == A_OKAY) {
                    int remoteId = ack.arg0;
                    while (true) {
                        Message m = readMessage(in);
                        if (m.cmd == A_WRTE) {
                            sendMessage(out, A_OKAY, localId, remoteId, null);
                        } else if (m.cmd == A_CLSE) {
                            sendMessage(out, A_CLSE, localId, remoteId, null);
                            break;
                        }
                    }
                }
                postSuccess(callback, "🚀 Stormz TV berjaya dibuka pada skrin TV!");
            } catch (Exception e) {
                postError(callback, "Gagal melancarkan launcher: " + e.getMessage());
            } finally {
                if (socket != null) {
                    try {
                        socket.close();
                    } catch (Exception ignored) {
                    }
                }
            }
        }).start();
    }

    private static void sendMessage(OutputStream out, int cmd, int arg0, int arg1, byte[] data) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN);
        int crc = 0;
        if (data != null) {
            for (byte b : data) crc += (b & 0xFF);
        }
        buf.putInt(cmd);
        buf.putInt(arg0);
        buf.putInt(arg1);
        buf.putInt(data != null ? data.length : 0);
        buf.putInt(crc);
        buf.putInt(cmd ^ 0xFFFFFFFF);
        out.write(buf.array());
        if (data != null && data.length > 0) {
            out.write(data);
        }
        out.flush();
    }

    private static Message readMessage(InputStream in) throws IOException {
        byte[] hdr = new byte[24];
        int read = 0;
        while (read < 24) {
            int r = in.read(hdr, read, 24 - read);
            if (r < 0) throw new EOFException();
            read += r;
        }
        ByteBuffer buf = ByteBuffer.wrap(hdr).order(ByteOrder.LITTLE_ENDIAN);
        int cmd = buf.getInt();
        int arg0 = buf.getInt();
        int arg1 = buf.getInt();
        int len = buf.getInt();
        int crc = buf.getInt();
        int magic = buf.getInt();

        byte[] data = new byte[len];
        read = 0;
        while (read < len) {
            int r = in.read(data, read, len - read);
            if (r < 0) throw new EOFException();
            read += r;
        }
        return new Message(cmd, arg0, arg1, data);
    }

    /*
     * This phone's own ADB key. Each install makes its own RSA key pair on first use and keeps it in the
     * app's private storage. (The original installer shipped one fixed key inside the APK, so anyone with
     * that public APK could reuse the key and connect to every TV that had ticked "Always allow" for it.)
     */
    private static final String KEY_FILE = "adbkey", PUB_FILE = "adbkey.pub";

    private static synchronized void ensureKeys(Context context) throws Exception {
        java.io.File priv = new java.io.File(context.getFilesDir(), KEY_FILE);
        java.io.File pub = new java.io.File(context.getFilesDir(), PUB_FILE);
        if (priv.length() > 0 && pub.length() > 0) return;
        java.security.KeyPairGenerator gen = java.security.KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        java.security.KeyPair pair = gen.generateKeyPair();
        writeText(priv, Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded()));
        writeText(pub, androidPublicKey((java.security.interfaces.RSAPublicKey) pair.getPublic()));
    }

    private static void writeText(java.io.File f, String text) throws IOException {
        try (java.io.FileOutputStream o = new java.io.FileOutputStream(f)) {
            o.write(text.getBytes("UTF-8"));
        }
    }

    private static String readText(java.io.File f) throws IOException {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new java.io.FileInputStream(f), "UTF-8"))) {
            String line = br.readLine();
            return line == null ? "" : line.trim();
        }
    }

    /** The public key in ADB's own format: Android's RSAPublicKey struct (2048-bit, little-endian words), base64. */
    private static String androidPublicKey(java.security.interfaces.RSAPublicKey key) {
        final int words = 2048 / 32;
        java.math.BigInteger r32 = java.math.BigInteger.ZERO.setBit(32);
        java.math.BigInteger n = key.getModulus();
        java.math.BigInteger rr = java.math.BigInteger.ZERO.setBit(32 * words).modPow(java.math.BigInteger.valueOf(2), n);
        java.math.BigInteger n0inv = n.remainder(r32).modInverse(r32);
        ByteBuffer bb = ByteBuffer.allocate(4 + 4 + words * 4 + words * 4 + 4).order(ByteOrder.LITTLE_ENDIAN);
        bb.putInt(words);
        bb.putInt(n0inv.negate().intValue());   // -1 / n[0] mod 2^32
        for (int i = 0; i < words; i++) {
            java.math.BigInteger[] qr = n.divideAndRemainder(r32);
            n = qr[0];
            bb.putInt(qr[1].intValue());
        }
        for (int i = 0; i < words; i++) {
            java.math.BigInteger[] qr = rr.divideAndRemainder(r32);
            rr = qr[0];
            bb.putInt(qr[1].intValue());
        }
        bb.putInt(key.getPublicExponent().intValue());
        return Base64.getEncoder().encodeToString(bb.array());
    }

    private static PrivateKey loadPrivateKey(Context context) {
        try {
            ensureKeys(context);
            byte[] keyBytes = Base64.getDecoder().decode(readText(new java.io.File(context.getFilesDir(), KEY_FILE)));
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
        } catch (Exception e) {
            return null;
        }
    }

    private static String loadPublicKey(Context context) {
        try {
            ensureKeys(context);
            String key = readText(new java.io.File(context.getFilesDir(), PUB_FILE));
            return key.isEmpty() ? null : key;
        } catch (Exception e) {
            return null;
        }
    }

    private static void postProgress(InstallCallback callback, int percent, String msg) {
        mainHandler.post(() -> {
            if (callback != null) callback.onProgress(percent, msg);
        });
    }

    private static void postSuccess(InstallCallback callback, String msg) {
        mainHandler.post(() -> {
            if (callback != null) callback.onSuccess(msg);
        });
    }

    private static void postError(InstallCallback callback, String err) {
        mainHandler.post(() -> {
            if (callback != null) callback.onError(err);
        });
    }

    private static class Message {
        int cmd, arg0, arg1;
        byte[] data;
        Message(int cmd, int arg0, int arg1, byte[] data) {
            this.cmd = cmd;
            this.arg0 = arg0;
            this.arg1 = arg1;
            this.data = data;
        }
    }
}
