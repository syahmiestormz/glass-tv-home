package com.syahmie.stormztv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.io.File;

public class WallpaperImportTest {

    @Test
    public void namedPicturesGetATitle() {
        assertEquals("Violet Flame", WallpaperImport.title(new File("05_violet_flame.jpg")));
        assertEquals("Red Moon", WallpaperImport.title(new File("/x/custom_wallpapers/red-moon.png")));
        assertEquals("Crimson Shrine", WallpaperImport.title(new File("07_crimson_shrine.jpg")));
    }

    @Test
    public void uploadsAndLinksGetNone() {
        assertNull(WallpaperImport.title(new File("custom_1730000000000.jpg")));
        assertNull(WallpaperImport.title(new File("1730000000000.jpg")));
        assertNull(WallpaperImport.title(new File("IMG_2034.jpg")));
        assertNull(WallpaperImport.title(null));
    }

    @Test
    public void safeNamesKeepLettersAndDigits() {
        assertEquals("05_violet_flame", WallpaperImport.safeName("05 Violet Flame.PNG"));
        assertEquals("red_moon", WallpaperImport.safeName("--Red  Moon!.jpeg"));
    }
}
