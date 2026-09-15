package com.onigiri.keycue.song.sky

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class SkyKeyMapperTest {

    private lateinit var mapper: SkyKeyMapper

    @Before
    fun setUp() {
        mapper = DefaultSkyKeyMapper()
    }

    @Test
    fun map_standardKeyPattern_mapsCorrectly() {
        // 0..14 の 1Key 形式
        assertEquals(0, mapper.map("1Key0"))
        assertEquals(7, mapper.map("1Key7"))
        assertEquals(11, mapper.map("1Key11"))
        assertEquals(14, mapper.map("1Key14"))

        // トラック番号違い (2Key, 0Key, トラックなしKey)
        assertEquals(7, mapper.map("2Key7"))
        assertEquals(0, mapper.map("0Key0"))
        assertEquals(14, mapper.map("Key14"))

        // 小文字 key
        assertEquals(5, mapper.map("1key5"))
    }

    @Test
    fun map_gridPattern_mapsCorrectly() {
        // 上段 A1..A5 -> 0..4
        assertEquals(0, mapper.map("A1"))
        assertEquals(1, mapper.map("A2"))
        assertEquals(2, mapper.map("A3"))
        assertEquals(3, mapper.map("A4"))
        assertEquals(4, mapper.map("A5"))

        // 中段 B1..B5 -> 5..9
        assertEquals(5, mapper.map("B1"))
        assertEquals(6, mapper.map("B2"))
        assertEquals(7, mapper.map("B3"))
        assertEquals(8, mapper.map("B4"))
        assertEquals(9, mapper.map("B5"))

        // 下段 C1..C5 -> 10..14
        assertEquals(10, mapper.map("C1"))
        assertEquals(11, mapper.map("C2"))
        assertEquals(12, mapper.map("C3"))
        assertEquals(13, mapper.map("C4"))
        assertEquals(14, mapper.map("C5"))

        // 小文字
        assertEquals(0, mapper.map("a1"))
        assertEquals(7, mapper.map("b3"))
        assertEquals(14, mapper.map("c5"))

        // トラック付き A1..C5
        assertEquals(0, mapper.map("1A1"))
        assertEquals(14, mapper.map("2C5"))
    }

    @Test
    fun map_numericPattern_mapsCorrectly() {
        assertEquals(0, mapper.map("0"))
        assertEquals(7, mapper.map("7"))
        assertEquals(14, mapper.map("14"))
    }

    @Test
    fun map_invalidOrOutOfBounds_returnsNull() {
        assertNull(mapper.map("1Key15"))
        assertNull(mapper.map("1Key99"))
        assertNull(mapper.map("1Key-1"))
        assertNull(mapper.map("D1"))
        assertNull(mapper.map("A6"))
        assertNull(mapper.map("C0"))
        assertNull(mapper.map("15"))
        assertNull(mapper.map("-1"))
        assertNull(mapper.map("invalid"))
        assertNull(mapper.map(""))
        assertNull(mapper.map("   "))
    }

    @Test
    fun extractTrack_extractsCorrectly() {
        assertEquals(1, mapper.extractTrack("1Key7"))
        assertEquals(2, mapper.extractTrack("2Key0"))
        assertEquals(1, mapper.extractTrack("1A1"))
        assertNull(mapper.extractTrack("Key7"))
        assertNull(mapper.extractTrack("A1"))
        assertNull(mapper.extractTrack("invalid"))
    }
}
