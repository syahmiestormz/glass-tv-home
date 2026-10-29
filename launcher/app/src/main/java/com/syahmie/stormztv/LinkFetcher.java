package com.syahmie.stormztv;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * "Paste a wallpaper link" on the phone upload page: the TV fetches the picture itself. Takes either a
 * direct image link or a wallpaper page that names its picture in an og:image tag, such as an
 * Alphacoders page (wall.alphacoders.com/big.php?i=…, whose og:image is a 1920-wide version: right for
 * a 1080p TV and a fraction of the 4K original). The picture is scaled to at most 1920 wide and saved
 * as a JPEG beside the uploaded ones, so it costs the same memory as any other wallpaper.
 *
 * <p>Only http(s), only public addresses (nothing on the home network), a page of at most 2 MB and a
 * picture of at most 40 MB. Runs on the caller's thread.
 */
final class LinkFetcher {

    private static final String TAG = "TvHome.Link";
    private static final String UA = "Mozilla/5.0 (Linux; Android 14; TV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36 StormzTV";
    private static final int MAX_PAGE = 2 * 1024 * 1024, MAX_IMAGE = 40 * 1024 * 1024, MAX_WIDTH = 1920;
    private static final Pattern OG_IMAGE = Pattern.compile(
            "<meta[^>]+(?:property|name)=[\"'](?:og:image|twitter:image)[\"'][^>]*content=[\"']([^\"']+)[\"']"
                    + "|<meta[^>]+content=[\"']([^\"']+)[\"'][^>]*(?:property|name)=[\"'](?:og:image|twitter:image)[\"']",
            Pattern.CASE_INSENSITIVE);

    static final class LinkException extends Exception {
        LinkException(String m) {
            super(m);
        }
    }

    private LinkFetcher() {}

    /** Downloads the picture behind {@code link} into {@code dir}; returns the new file. */
    static File fetch(String link, File dir) throws LinkException {
        if (link == null || link.trim().isEmpty()) throw new LinkException("Paste a link first.");
        URL url = check(link.trim());
        byte[] data = get(url, MAX_IMAGE, true);
        if (data == null) {
            // Not a picture: read the page and follow its preview image.
            byte[] page = get(url, MAX_PAGE, false);
            String html = new String(page, java.nio.charset.StandardCharsets.UTF_8);
            Matcher m = OG_IMAGE.matcher(html);
            if (!m.find()) throw new LinkException("No picture found on that page. Open the wallpaper itself and copy its link.");
            String img = (m.group(1) != null ? m.group(1) : m.group(2)).replace("&amp;", "&");
            try {
                url = check(new URL(url, img).toString());
            } catch (java.net.MalformedURLException e) {
                throw new LinkException("That page's picture link is broken.");
            }
            data = get(url, MAX_IMAGE, true);
            if (data == null) throw new LinkException("The page's picture could not be downloaded.");
        }
        return save(data, dir);
    }

    private static URL check(String link) throws LinkException {
        URL url;
        try {
            url = new URL(link);
        } catch (Exception e) {
            throw new LinkException("That isn't a web link.");
        }
        String p = url.getProtocol().toLowerCase(Locale.ROOT);
        if (!p.equals("https") && !p.equals("http")) throw new LinkException("Only http and https links work.");
        try {
            for (InetAddress a : InetAddress.getAllByName(url.getHost())) {
                if (a.isLoopbackAddress() || a.isSiteLocalAddress() || a.isLinkLocalAddress() || a.isAnyLocalAddress()) {
                    throw new LinkException("Links to the home network are not allowed.");
                }
            }
        } catch (LinkException e) {
            throw e;
        } catch (Exception e) {
            throw new LinkException("Can't reach " + url.getHost() + ". Is the TV online?");
        }
        return url;
    }

    /**
     * GETs the URL. With {@code wantImage}, returns null when the answer is not a picture (so the
     * caller can read it as a page); otherwise returns the body.
     */
    private static byte[] get(URL url, int max, boolean wantImage) throws LinkException {
        HttpURLConnection c = null;
        try {
            for (int hop = 0; hop < 5; hop++) {
                c = (HttpURLConnection) url.openConnection();
                c.setInstanceFollowRedirects(false);
                c.setConnectTimeout(10000);
                c.setReadTimeout(20000);
                c.setRequestProperty("User-Agent", UA);
                int code = c.getResponseCode();
                if (code >= 300 && code < 400 && c.getHeaderField("Location") != null) {
                    url = check(new URL(url, c.getHeaderField("Location")).toString());
                    c.disconnect();
                    continue;
                }
                if (code != 200) throw new LinkException("The site answered " + code + ".");
                String type = String.valueOf(c.getContentType()).toLowerCase(Locale.ROOT);
                if (wantImage && !type.startsWith("image/")) {
                    c.disconnect();
                    return null;
                }
                long len = c.getContentLengthLong();
                if (len > max) throw new LinkException("That file is too big.");
                try (InputStream in = c.getInputStream()) {
                    ByteArrayOutputStream out = new ByteArrayOutputStream(len > 0 ? (int) len : 65536);
                    byte[] buf = new byte[16384];
                    int n, total = 0;
                    while ((n = in.read(buf)) > 0) {
                        total += n;
                        if (total > max) throw new LinkException("That file is too big.");
                        out.write(buf, 0, n);
                    }
                    return out.toByteArray();
                }
            }
            throw new LinkException("Too many redirects.");
        } catch (LinkException e) {
            throw e;
        } catch (Exception e) {
            Log.w(TAG, "get " + url + ": " + e);
            throw new LinkException("Download failed: " + e.getMessage());
        } finally {
            if (c != null) c.disconnect();
        }
    }

    /** Decodes, scales to at most 1920 wide and writes a JPEG into the uploads folder. */
    private static File save(byte[] data, File dir) throws LinkException {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, o);
        if (o.outWidth <= 0 || o.outHeight <= 0) throw new LinkException("That link isn't a picture the TV can show.");
        int sample = 1;
        while (o.outWidth / (sample * 2) >= MAX_WIDTH) sample *= 2;
        o = new BitmapFactory.Options();
        o.inSampleSize = sample;
        Bitmap bmp = BitmapFactory.decodeByteArray(data, 0, data.length, o);
        if (bmp == null) throw new LinkException("That picture could not be read.");
        if (bmp.getWidth() > MAX_WIDTH) {
            Bitmap scaled = Bitmap.createScaledBitmap(bmp, MAX_WIDTH,
                    Math.round(bmp.getHeight() * (MAX_WIDTH / (float) bmp.getWidth())), true);
            bmp.recycle();
            bmp = scaled;
        }
        if (!dir.isDirectory() && !dir.mkdirs()) throw new LinkException("No space on the TV.");
        File f = new File(dir, "custom_" + System.currentTimeMillis() + ".jpg");
        try (FileOutputStream out = new FileOutputStream(f)) {
            bmp.compress(Bitmap.CompressFormat.JPEG, 92, out);
        } catch (Exception e) {
            f.delete();
            throw new LinkException("Could not save the picture.");
        } finally {
            bmp.recycle();
        }
        return f;
    }
}
