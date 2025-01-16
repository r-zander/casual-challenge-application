package gg.casualchallenge.library.persistence;

import jakarta.persistence.AttributeConverter;
import org.springframework.core.annotation.AnnotationUtils;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@SuppressWarnings("ConverterNotAnnotatedInspection")
public class EnumValueConverter<T extends Enum<T>> implements AttributeConverter<T, String> {

    private final Class<T> enumType;
    private final Map<String, T> dbValueToEnum = new HashMap<>();
    private final Map<T, String> enumToDbValue = new HashMap<>();

    public EnumValueConverter(Class<T> enumType) {
        this.enumType = enumType;
        initializeMapping();
    }

    private void initializeMapping() {
        // Use reflection to inspect enum constants and their @DbEnumValue annotations
        for (T constant : enumType.getEnumConstants()) {
            try {
                Field field = enumType.getField(constant.name());
                DbEnumValue annotation = AnnotationUtils.findAnnotation(field, DbEnumValue.class);
                if (annotation != null) {
                    String dbValue = annotation.value();
                    dbValueToEnum.put(dbValue, constant);
                    enumToDbValue.put(constant, dbValue);
                }
            } catch (NoSuchFieldException e) {
                throw new IllegalStateException("Failed to inspect enum field: " + constant.name(), e);
            }
        }
    }

    @Override
    public String convertToDatabaseColumn(T attribute) {
        if (attribute == null) {
            return null;
        }
        return enumToDbValue.get(attribute); // Lookup the database value
    }

    @Override
    public T convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        return dbValueToEnum.get(dbData); // Lookup the enum constant
    }
}
