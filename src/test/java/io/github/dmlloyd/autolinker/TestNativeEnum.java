package io.github.dmlloyd.autolinker;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

public class TestNativeEnum {

    @Test
    public void testMapping() {
        assertEquals(Bit.BIT_0, NativeEnum.fromNativeCode(Bit.class, 0));
        assertEquals(Bit.BIT_1, NativeEnum.fromNativeCode(Bit.class, 1));
        assertEquals(Bit.BIT_2, NativeEnum.fromNativeCode(Bit.class, 2));
        assertEquals(Bit.BIT_4, NativeEnum.fromNativeCode(Bit.class, 4));
        assertEquals(Bit.BIT_8, NativeEnum.fromNativeCode(Bit.class, 8));
        assertThrows(IllegalArgumentException.class, () -> NativeEnum.fromNativeCode(Bit.class, 7));
        NativeEnum<Bit> seven = NativeEnum.fromNativeCode(7);
        assertThrows(IllegalArgumentException.class, () -> seven.as(Bit.class));
        assertTrue(seven.isSet(Bit.BIT_1));
        assertTrue(seven.isSet(Bit.BIT_2));
        assertTrue(seven.isSet(Bit.BIT_4));
        assertFalse(seven.isSet(Bit.BIT_8));
        assertFalse(seven.isClear(Bit.BIT_1));
        assertFalse(seven.isClear(Bit.BIT_2));
        assertFalse(seven.isClear(Bit.BIT_4));
        assertTrue(seven.isClear(Bit.BIT_8));
        assertTrue(seven.allAreSet(Bit.BIT_1, Bit.BIT_2, Bit.BIT_4));
        assertFalse(seven.allAreSet(Bit.BIT_1, Bit.BIT_2, Bit.BIT_8));
        assertTrue(seven.allAreClear(Bit.BIT_8, Bit.BIT_8));
        assertFalse(seven.allAreClear(Bit.BIT_4, Bit.BIT_8));
        NativeEnum<Bit> four = NativeEnum.fromNativeCode(4);
        assertEquals(Bit.BIT_4, four.as(Bit.class));
    }

    @Test
    public void testOr() {
        NativeEnum<Bit> bit7 = Bit.BIT_0.or(Bit.BIT_1).or(Bit.BIT_2).or(Bit.BIT_4);
        assertEquals(7, bit7.nativeCode());
        assertThrows(IllegalArgumentException.class, () -> bit7.as(Bit.class));
        assertEquals(11, NativeEnum.orAll(Bit.BIT_1, Bit.BIT_2, Bit.BIT_8).nativeCode());
        assertEquals(Bit.BIT_1, Bit.BIT_0.or(Bit.BIT_1));
        assertEquals(Bit.BIT_1, Bit.BIT_1.or(Bit.BIT_0));
        assertEquals(Bit.BIT_1, Bit.BIT_1.or(Bit.BIT_1));
    }

    @Test
    public void testAnd() {
        NativeEnum<Bit> bit6 = NativeEnum.orAll(Bit.BIT_1, Bit.BIT_2, Bit.BIT_4).and(NativeEnum.orAll(Bit.BIT_2, Bit.BIT_4, Bit.BIT_8));
        assertEquals(6, bit6.nativeCode());
        assertThrows(IllegalArgumentException.class, () -> bit6.as(Bit.class));
        assertEquals(2, NativeEnum.andAll(bit6, Bit.BIT_2).nativeCode());
        assertEquals(Bit.BIT_0, Bit.BIT_0.and(Bit.BIT_1));
        assertEquals(Bit.BIT_0, Bit.BIT_1.and(Bit.BIT_0));
        assertEquals(Bit.BIT_1, Bit.BIT_1.or(Bit.BIT_1));
    }

    @Test
    public void testXor() {
        NativeEnum<Bit> bit15 = NativeEnum.orAll(Bit.BIT_1, Bit.BIT_2, Bit.BIT_4, Bit.BIT_8);
        NativeEnum<Bit> bit10 = NativeEnum.orAll(Bit.BIT_2, Bit.BIT_8);
        NativeEnum<Bit> bit5 = NativeEnum.orAll(Bit.BIT_1, Bit.BIT_4);
        assertEquals(5, bit15.xor(bit10).nativeCode());
        assertEquals(0, bit15.xor(bit10).xor(bit5).nativeCode());
        assertEquals(10, bit15.xor(bit5).nativeCode());
        assertEquals(0, NativeEnum.xorAll(bit15, bit10, bit5).nativeCode());
        assertEquals(Bit.BIT_8, Bit.BIT_8.xor(Bit.BIT_0));
        assertEquals(Bit.BIT_8, Bit.BIT_0.xor(Bit.BIT_8));
    }
}
