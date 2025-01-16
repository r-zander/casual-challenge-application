package gg.casualchallenge.library.persistence;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD) // Annotation targets enum fields
public @interface DbEnumValue {
    String value(); // The database value for the enum constant
}
