package org.opentripplanner.model.fare;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.opentripplanner.ext.fares.model.FareTransferRule;

class SerializationTest {

  static Stream<Class<?>> cases() {
    return Stream.of(
      FareProduct.class,
      FareTransferRule.class,
      FareMedium.class,
      RiderCategory.class
    );
  }

  @ParameterizedTest
  @MethodSource("cases")
  void testFareProductSerializable(Class<?> clazz) {
    assertInstanceOf(Serializable.class, clazz);

    for (Field field : clazz.getDeclaredFields()) {
      field.setAccessible(true);
      Class<?> fieldType = field.getType();
      if (
        !fieldType.isPrimitive() &&
        !fieldType.isRecord() &&
        !fieldType.isAssignableFrom(Collection.class)
      ) {
        assertTrue(
          Serializable.class.isAssignableFrom(fieldType),
          "Field " + field.getName() + " must be serializable"
        );
      }
    }
  }
}
