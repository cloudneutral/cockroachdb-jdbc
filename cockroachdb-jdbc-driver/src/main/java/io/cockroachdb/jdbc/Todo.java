package io.cockroachdb.jdbc;

import java.lang.annotation.*;

@Documented
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Todo {
    String value() default "(wip)";

    String dueVersion() default "(undefined)";
}
