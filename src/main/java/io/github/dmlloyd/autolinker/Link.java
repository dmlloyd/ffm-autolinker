package io.github.dmlloyd.autolinker;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.foreign.Linker;

/**
 * Autolink the annotated method.
 * Only non-{@code default}, non-{@code static} methods may be autolinked; other methods will be ignored.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Link {

    /**
     * {@return the name of the function to link (defaults to the method name)}
     */
    String name() default "";

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.PARAMETER, ElementType.METHOD})
    @interface as {
        AsType value();
    }

    /**
     * The direction of a parameter.
     * This applies in cases where the value must be copied to or from the argument into a buffer.
     * In cases where a copy is not needed, all value parameters are effectively {@link Direction#in in}
     * and all pointer parameters are effectively {@link Direction#in_out in_out}.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.PARAMETER)
    @interface dir {
        /**
         * {@return the direction of this parameter}
         */
        Direction value();
    }

    /**
     * Mark the first variadic argument of a variadic function call.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.PARAMETER)
    @interface va_start {}

    /**
     * Indicate that the method is {@linkplain Linker.Option#critical(boolean) critical}
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface critical {
        boolean heap() default false;
    }

    /**
     * Indicate that the method should {@linkplain Linker.Option#captureCallState(String...) capture a call state value}.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @Repeatable(capture.list.class)
    @interface capture {
        /**
         * {@return the call state value name}
         */
        String value();

        /**
         * The carrier type for multiple occurrences of {@link capture}.
         */
        @Retention(RetentionPolicy.RUNTIME)
        @Target(ElementType.METHOD)
        @interface list {
            /**
             * {@return the list}
             */
            capture[] value();
        }
    }
}
