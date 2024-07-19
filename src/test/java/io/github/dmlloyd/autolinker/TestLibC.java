package io.github.dmlloyd.autolinker;

import static io.github.dmlloyd.autolinker.AsType.*;
import static io.github.dmlloyd.autolinker.Link.*;
import static org.junit.jupiter.api.Assertions.*;

import java.lang.foreign.Arena;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandles;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Test some libc functions using autolinker.
 */
public final class TestLibC {

    private final AutoLinker autoLinker = new AutoLinker(MethodHandles.lookup());

    @Test
    public void testIntPassing() {
        LibCStuff x = autoLinker.autoLink(LibCStuff.class);
        // use rand/srand to verify that int passing works
        x.srand(12345);
        int first = x.rand();
        x.srand(12345);
        int second = x.rand();
        assertEquals(first, second);
        x.srand(50);
        first = x.rand();
        assertNotEquals(second, first);
        x.srand(50);
        second = x.rand();
        assertEquals(first, second);
    }

    @Test
    public void testLongPassing() {
        LibCStuff x = autoLinker.autoLink(LibCStuff.class);
        assertEquals(1234, x.abs(1234));
        assertEquals(1234, x.abs(-1234));
        assertEquals((short)1234, x.abs((short)-1234));
        // it wraps around
        assertEquals((short)-65535, x.abs((short)-65535));
        assertEquals(9293L, x.labs(9293L));
        assertEquals(9293L, x.labs(-9293L));

        assertEquals(939959739L, x.llabs(939959739L));
        assertEquals(939959739L, x.llabs(-939959739L));
    }

    @Test
    public void testBytes() {
        LibCStuff x = autoLinker.autoLink(LibCStuff.class);
        assertEquals(1234, x.atoi("1234\0".getBytes(StandardCharsets.US_ASCII)));
        assertEquals(0, x.atoi("nonsense\0".getBytes(StandardCharsets.US_ASCII)));
        // treat as unsigned
        assertEquals(4294967295L, x.atoi_as_unsigned("4294967295\0".getBytes(StandardCharsets.US_ASCII), true));
    }

    @Test
    public void testString() {
        LibCStuff x = autoLinker.autoLink(LibCStuff.class);
        assertEquals(1234, x.atoi("1234"));
        assertEquals(0, x.atoi("nonsense"));
        assertEquals(1234, x.atoi_crit("1234"));
        assertEquals(0, x.atoi_crit("nonsense"));
    }

    @Test
    @Disabled("FFM presently disallows critical+capture")
    public void testCriticalWithCaptureErrno() {
        LibCStuff x = autoLinker.autoLink(LibCStuff.class);
        try (Arena arena = Arena.ofConfined()) {
            x.sin(arena.allocate(Linker.Option.captureStateLayout()), 1.0);
        }
    }

    @Test
    public void testMissing() {
        LibCStuff x = autoLinker.autoLink(LibCStuff.class);
        assertThrows(UnsatisfiedLinkError.class, x::non_existent);
        // make sure it does it every time
        assertThrows(UnsatisfiedLinkError.class, x::non_existent);
    }

    @Test
    public void testSomePointerStuff() {
        LibCStuff x = autoLinker.autoLink(LibCStuff.class);
        byte[] expected = new byte[100];
        byte[] segArray = new byte[100];
        MemorySegment seg = MemorySegment.ofArray(segArray);
        x.memset(seg, 5, 100);
        Arrays.fill(expected, (byte) 5);
        assertArrayEquals(expected, segArray);
        x.memset(seg, 0xaa, 100);
        Arrays.fill(expected, (byte) 0xaa);
        assertArrayEquals(expected, segArray);
    }

    @Test
    public void testNativeEnums() {
        LibCStuff x = autoLinker.autoLink(LibCStuff.class);
        // this weird test just passes the value through
        assertEquals(Errno.EDOM, x.abs(Errno.EDOM));
        assertEquals(Errno.EDOM, x.abs((NativeEnum<Errno>) Errno.EDOM).as(Errno.class));
        assertEquals(Errno.ERANGE, x.abs(Errno.ERANGE));
        assertEquals(Errno.ERANGE, x.abs((NativeEnum<Errno>) Errno.ERANGE).as(Errno.class));
    }

    @SuppressWarnings("SpellCheckingInspection")
    interface LibCStuff {
        // useful for debugging
        @Link
        @critical(heap = true)
        void printf(byte[] buf, @va_start int value);

        @Link
        void srand(@as(unsigned_int) int seed);

        @Link
        int rand();

        @Link
        @critical
        int abs(int n);

        @Link
        @critical
        // another bad example, just here to test stuff
        Errno abs(Errno n);

        @Link
        @critical
        // another bad example, just here to test stuff
        NativeEnum<Errno> abs(NativeEnum<Errno> n);

        @Link
        @as(int_) short abs(@as(int_) short n);

        @Link
        @critical // why not
        @as(long_) long labs(@as(long_) long n);

        @Link
        @as(long_long) long llabs(@as(long_long) long n);

        @Link
        @critical(heap = true)
        int atoi(byte[] str);

        @Link(name = "atoi")
        @critical(heap = true)
        @as(unsigned_int) long atoi_as_unsigned(byte[] str, @as(void_) boolean ignore);

        @Link
        int atoi(String str);

        @Link(name = "atoi")
        @critical(heap = true)
        int atoi_crit(String str);

        @Link
        @critical
        @capture("errno")
        double sin(MemorySegment buf, double arg);

        @Link
        void non_existent();

        @Link
        @critical(heap = true)
        @as(ptr) void memset(MemorySegment dest, int ch, @as(size_t) int count);
    }
}
