package io.github.dmlloyd.autolinker;

/**
 * An interface for a class (typically an {@code enum}) which maps to integers
 * in a platform-specific manner.
 * Such classes may be used as function arguments.
 * To use such a class as a function return type, the class must have an accessible
 * {@code static} method named {@code fromNativeCode} which accepts an {@code int}
 * and returns a value of the appropriate type.
 */
public interface NativeEnum {
    /**
     * {@return the native integer value corresponding to this object}
     * It is the responsibility of the implementer to ensure that the value is correct
     * with respect to the current target platform.
     */
    int nativeCode();
}
