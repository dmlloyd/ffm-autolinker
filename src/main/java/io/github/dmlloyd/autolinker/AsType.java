package io.github.dmlloyd.autolinker;

/**
 * Native types to link as.
 * If not given, then the type will match the Java type exactly in terms of bitness and signedness if possible.
 * Otherwise, it will be directly adapted if possible.
 * Objects which implement {@link NativeEnum} will be passed as C {@code int}.
 */
//
// NOTE: when modifying this enum, also update README.adoc
//
public enum AsType {
    signed_char,
    unsigned_char,
    char_,
    int_,
    unsigned_int,
    long_,
    unsigned_long,
    long_long,
    unsigned_long_long,

    int8_t,
    uint8_t,
    int16_t,
    uint16_t,
    int32_t,
    uint32_t,
    int64_t,
    uint64_t,

    char7_t,

    char8_t,
    char16_t,
    char32_t,

    ptrdiff_t,
    intptr_t,
    uintptr_t,
    size_t,
    ssize_t,

    ptr,
    void_,
    ;

    AsType() {
    }
}
