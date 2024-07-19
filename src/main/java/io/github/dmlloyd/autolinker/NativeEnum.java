package io.github.dmlloyd.autolinker;

/**
 * An interface for a class (typically an {@code enum}) which maps to integers
 * in a platform-specific manner.
 * <p>
 * Such classes may be used as function arguments.
 * To use such a class as a function return type, the class must have an accessible
 * {@code static} method named {@code fromNativeCode} which accepts an {@code int}
 * and returns a value of the appropriate type.
 * This method <em>should</em> throw an {@code IllegalArgumentException}
 * if the given value is not a valid value for the type.
 * The method <em>should</em> accept every value for which there is a corresponding
 * instance which returns that same value from {@link #nativeCode}.
 * <p>
 * Arguments or return values which map to single enumerated values should use the type
 * of the implementation directly.
 * Arguments or return values which map to bitmasks (bitwise combinations of enumerated values)
 * should be declared using the type {@code NativeEnum<T>}, where {@code T} is the
 * type of the implementation.
 *
 * @param <T> the type of this native enumeration, which is used for type safety with
 *           the bitmask {@link #and}, {@link #or}, and {@link #xor}
 *           methods
 */
@FunctionalInterface
public interface NativeEnum<T extends NativeEnum<T>> {
    /**
     * {@return the native integer value corresponding to this object}
     * It is the responsibility of the implementer to ensure that the value is correct
     * with respect to the current target platform.
     */
    int nativeCode();

    /**
     * {@return true if the bit(s) given in the argument are all set in this value}
     * @param a the other value
     */
    default boolean isSet(NativeEnum<T> a) {
        int nc = a.nativeCode();
        return (nativeCode() & nc) == nc;
    }

    /**
     * {@return true if the bit(s) given in the arguments are all set in this value}
     * @param a the first other value
     * @param b the second other value
     */
    default boolean allAreSet(NativeEnum<T> a, NativeEnum<T> b) {
        int nc = a.nativeCode() | b.nativeCode();
        return (nativeCode() & nc) == nc;
    }

    /**
     * {@return true if the bit(s) given in the arguments are all set in this value}
     * @param a the first other value
     * @param b the second other value
     * @param c the third other value
     */
    default boolean allAreSet(NativeEnum<T> a, NativeEnum<T> b, NativeEnum<T> c) {
        int nc = a.nativeCode() | b.nativeCode() | c.nativeCode();
        return (nativeCode() & nc) == nc;
    }

    /**
     * {@return true if the bit(s) given in the argument are all clear in this value}
     * @param a the other value
     */
    default boolean isClear(NativeEnum<T> a) {
        int nc = a.nativeCode();
        return (nativeCode() & nc) == 0;
    }

    /**
     * {@return true if the bit(s) given in the arguments are all clear in this value}
     * @param a the first other value
     * @param b the second other value
     */
    default boolean allAreClear(NativeEnum<T> a, NativeEnum<T> b) {
        int nc = a.nativeCode() | b.nativeCode();
        return (nativeCode() & nc) == 0;
    }

    /**
     * {@return true if the bit(s) given in the arguments are all clear in this value}
     * @param a the first other value
     * @param b the second other value
     * @param c the third other value
     */
    default boolean allAreClear(NativeEnum<T> a, NativeEnum<T> b, NativeEnum<T> c) {
        int nc = a.nativeCode() | b.nativeCode() | c.nativeCode();
        return (nativeCode() & nc) == 0;
    }

    /**
     * {@return a value representing the bitwise-AND of this value and the given argument}
     * @param other the other value
     */
    default NativeEnum<T> and(NativeEnum<T> other) {
        int a = nativeCode();
        int b = other.nativeCode();
        int nc = a & b;
        return nc == a ? this : nc == b ? other : fromNativeCode(nc);
    }

    /**
     * {@return a value representing the bitwise-OR of this value and the given argument}
     * @param other the other value
     */
    default NativeEnum<T> or(NativeEnum<T> other) {
        int a = nativeCode();
        int b = other.nativeCode();
        int nc = a | b;
        return nc == a ? this : nc == b ? other : fromNativeCode(nc);
    }

    /**
     * {@return a value representing the bitwise-XOR of this value and the given argument}
     * @param other the other value
     */
    default NativeEnum<T> xor(NativeEnum<T> other) {
        int a = nativeCode();
        int b = other.nativeCode();
        int nc = a ^ b;
        return nc == a ? this : nc == b ? other : fromNativeCode(nc);
    }

    /**
     * Get the value of this native enumeration as an instance of the implementation type.
     * If the value is already of the implementation type, it is returned as-is.
     * The range of possible values which may be represented as instances of the implementation type
     * is specific to the implementation; if a value is outside of this range, an exception may be thrown.
     *
     * @param implClass the implementation type class (must not be {@code null})
     * @return the value as the implementation type (not {@code null})
     * @throws IllegalArgumentException if the {@code fromNativeCode} factory method does not exist on {@code implClass}
     *      or is not accessible
     * @throws NullPointerException if the {@code fromNativeCode} factory method returns {@code null}
     * @throws RuntimeException if the {@code fromNativeCode} throws some other runtime exception
     */
    default T as(Class<T> implClass) {
        if (implClass.isInstance(this)) {
            return implClass.cast(this);
        } else {
            return Util.nativeEnumFactory(implClass).apply(nativeCode());
        }
    }

    /**
     * {@return a value representing the bitwise-AND of all of the given argument values}
     * @param vals the values (must not be {@code null})
     * @param <T> the enumeration type
     */
    @SafeVarargs
    static <T extends NativeEnum<T>> NativeEnum<T> andAll(NativeEnum<T>... vals) {
        return () -> {
            int length = vals.length;
            if (length == 0) {
                return 0;
            }
            int res = vals[0].nativeCode();
            for (int i = 1; i < length; i++) {
                res &= vals[i].nativeCode();
            }
            return res;
        };
    }

    /**
     * {@return a value representing the bitwise-OR of all of the given argument values}
     * @param vals the values (must not be {@code null})
     * @param <T> the enumeration type
     */
    @SafeVarargs
    static <T extends NativeEnum<T>> NativeEnum<T> orAll(NativeEnum<T>... vals) {
        return () -> {
            int length = vals.length;
            if (length == 0) {
                return 0;
            }
            int res = vals[0].nativeCode();
            for (int i = 1; i < length; i++) {
                res |= vals[i].nativeCode();
            }
            return res;
        };
    }

    /**
     * {@return a value representing the bitwise-XOR of all of the given argument values}
     * @param vals the values (must not be {@code null})
     * @param <T> the enumeration type
     */
    @SafeVarargs
    static <T extends NativeEnum<T>> NativeEnum<T> xorAll(NativeEnum<T>... vals) {
        return () -> {
            int length = vals.length;
            if (length == 0) {
                return 0;
            }
            int res = vals[0].nativeCode();
            for (int i = 1; i < length; i++) {
                res ^= vals[i].nativeCode();
            }
            return res;
        };
    }

    /**
     * Get an instance from an integer value.
     *
     * @param value the integer value
     * @return the instance (not {@code null})
     * @param <T> the type
     */
    static <T extends NativeEnum<T>> NativeEnum<T> fromNativeCode(int value) {
        return () -> value;
    }

    /**
     * Get an instance of the implementation type from an integer value.
     *
     * @param value the integer value
     * @return the instance (not {@code null})
     * @param <T> the type
     */
    static <T extends NativeEnum<T>> T fromNativeCode(Class<T> implClass, int value) {
        return Util.nativeEnumFactory(implClass).apply(value);
    }
}
