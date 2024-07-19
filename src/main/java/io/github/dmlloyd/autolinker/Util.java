package io.github.dmlloyd.autolinker;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.function.IntFunction;

/**
 * Utilities.
 */
final class Util {
    private Util() {}

    private static final ClassValue<IntFunction<? extends NativeEnum<?>>> nativeEnumFactories = new ClassValue<IntFunction<? extends NativeEnum<?>>>() {
        protected IntFunction<? extends NativeEnum<?>> computeValue(final Class<?> type) {
            MethodHandle handle;
            try {
                handle = MethodHandles.publicLookup()
                    .findStatic(type, "fromNativeCode", MethodType.methodType(type, int.class))
                    .asType(MethodType.methodType(NativeEnum.class, int.class));
            } catch (NoSuchMethodException e) {
                throw new IllegalArgumentException("No static fromNativeCode method found on " + type, e);
            } catch (IllegalAccessException e) {
                throw new IllegalArgumentException("Cannot access static fromNativeCode method on " + type, e);
            }
            return code -> {
                NativeEnum<?> result;
                try {
                    result = (NativeEnum<?>) handle.invokeExact(code);
                } catch (RuntimeException | Error e) {
                    throw e;
                } catch (Throwable e) {
                    throw new UndeclaredThrowableException(e);
                }
                if (result == null) {
                    throw new NullPointerException("Null return value from " + type.getName() + "#fromNativeCode(" + code + ")");
                }
                return result;
            };
        }
    };

    @SuppressWarnings("unchecked")
    static <T> IntFunction<T> nativeEnumFactory(final Class<T> implClass) {
        return (IntFunction<T>) nativeEnumFactories.get(implClass);
    }
}
