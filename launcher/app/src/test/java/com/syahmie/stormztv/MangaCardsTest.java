package com.syahmie.stormztv;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.io.File;

public class MangaCardsTest {

    @Test
    public void numbersInKanji() {
        assertEquals("五", MangaCards.kanji(5));
        assertEquals("十", MangaCards.kanji(10));
        assertEquals("十二", MangaCards.kanji(12));
        assertEquals("二十五", MangaCards.kanji(25));
        assertEquals("百", MangaCards.kanji(100));
        assertEquals("三百七", MangaCards.kanji(307));
    }

    @Test
    public void wallpaperNumberIsTheEpisode() {
        assertEquals("第五話  ·  EPISODE 05", MangaCards.episode(new File("05_violet_flame.jpg")));
        assertEquals("第十二話  ·  EPISODE 12", MangaCards.episode(new File("12-red-moon.jpg")));
        assertEquals("壁紙  ·  MY WALLPAPER", MangaCards.episode(new File("red_moon.jpg")));
    }

    @Test
    public void weekdays() {
        assertEquals("木曜日", MangaCards.weekdayKanji(4));
        assertEquals("日曜日", MangaCards.weekdayKanji(0));
    }
}
