package io.github.dmlloyd.autolinker;

import io.smallrye.common.os.OS;

/**
 * This is a very simplistic example and should not be copied in production code.
 */
@SuppressWarnings("SpellCheckingInspection")
public enum Errno implements NativeEnum<Errno> {
    EDOM,
    ERANGE,
    EILSEQ,
    ;

    public int nativeCode() {
        return switch (this) {
            case EDOM -> 33;
            case ERANGE -> 34;
            case EILSEQ -> switch (OS.current()) {
                case MAC -> 92;
                case LINUX -> 84;
                case WINDOWS -> 42;
                default -> -1;
            };
        };
    }

    public static Errno fromNativeCode(int code) {
        return switch (code) {
            case 33 -> EDOM;
            case 34 -> ERANGE;
            case 42, 84, 92 -> EILSEQ;
            default -> null;
        };
    }
}
