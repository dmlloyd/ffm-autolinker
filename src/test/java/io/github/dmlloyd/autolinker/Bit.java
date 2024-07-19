package io.github.dmlloyd.autolinker;

/**
 *
 */
public enum Bit implements NativeEnum<Bit> {
    BIT_0(0),
    BIT_1(1),
    BIT_2(2),
    BIT_4(4),
    BIT_8(8),
    ;
    private final int bit;

    Bit(final int bit) {
        this.bit = bit;
    }

    public int nativeCode() {
        return bit;
    }

    public static Bit fromNativeCode(int code) {
        return switch (code) {
            case 0 -> BIT_0;
            case 1 -> BIT_1;
            case 2 -> BIT_2;
            case 4 -> BIT_4;
            case 8 -> BIT_8;
            default -> throw new IllegalArgumentException("No constant for " + code);
        };
    }
}
