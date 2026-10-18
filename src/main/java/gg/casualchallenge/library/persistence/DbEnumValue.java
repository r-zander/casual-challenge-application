package gg.casualchallenge.library.persistence;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Annotation targets enum fields. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface DbEnumValue {
    /** The database value for the enum constant */
    String value();
}
