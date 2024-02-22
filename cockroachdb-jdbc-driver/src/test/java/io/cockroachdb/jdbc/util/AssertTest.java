package io.cockroachdb.jdbc.util;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;

@Tags(value = {
        @Tag("all-test"),
        @Tag("unit-test")
})
public class AssertTest {
    @Test
    public void whenAssertingConditions_expectConditionsToHold() {
        Assertions.assertDoesNotThrow(() -> Assert.notNull(1, "null!"));
        Assertions.assertDoesNotThrow(() -> Assert.isTrue(true, "!"));
        Assertions.assertDoesNotThrow(() -> Assert.hasText("a", "!"));
        Assertions.assertThrows(IllegalArgumentException.class, () -> Assert.notNull(null, "!"));
        Assertions.assertThrows(IllegalArgumentException.class, () -> Assert.isTrue(false, "!"));
        Assertions.assertThrows(IllegalArgumentException.class, () -> Assert.hasText("", "!"));
    }
}
