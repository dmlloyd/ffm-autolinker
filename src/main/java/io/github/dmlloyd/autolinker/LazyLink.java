package io.github.dmlloyd.autolinker;

/**
 *
 */
final class LazyLink {
    static final Class<?> ARENA;
    static final Class<?> MEMORY_SEGMENT;

    private LazyLink() {}

    static {
        try {
            ARENA = Class.forName("java.lang.foreign.Arena");
            MEMORY_SEGMENT = Class.forName("java.lang.foreign.MemorySegment");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }
}
