package io.github.dmlloyd.autolinker;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

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

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.PARAMETER)
    @interface va_start {}

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface critical {
        boolean heap() default false;
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @Repeatable(capture.list.class)
    @interface capture {
        String value();

        @Retention(RetentionPolicy.RUNTIME)
        @Target(ElementType.METHOD)
        @interface list {
            capture[] value();
        }
    }
}
