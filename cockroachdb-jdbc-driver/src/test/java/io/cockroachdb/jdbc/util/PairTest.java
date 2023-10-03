package io.cockroachdb.jdbc.util;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit-test")
public class PairTest {
    @Test
    public void whenComparingTuples_expectCorrectOutcome() {
        Pair<Integer, Integer> t = new Pair<>(1, 2);
        Assertions.assertEquals(1, t.getFirst());
        Assertions.assertEquals(2, t.getSecond());
        Assertions.assertEquals(t, new Pair<>(1, 2));
        Assertions.assertNotEquals(t, new Pair<>(2, 1));
        Assertions.assertNotSame(t, new Pair<>(1, 2));
        Assertions.assertEquals(t.hashCode(), new Pair<>(1, 2).hashCode());
    }
}
