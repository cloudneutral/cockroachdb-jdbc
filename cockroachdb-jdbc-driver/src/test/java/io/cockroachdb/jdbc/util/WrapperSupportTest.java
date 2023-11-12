package io.cockroachdb.jdbc.util;

import java.sql.SQLException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit-test")
public class WrapperSupportTest {
    interface Foo {
        void foo() throws SQLException;
    }

    class Bar implements Foo {
        @Override
        public void foo() throws SQLException {
        }
    }

    class FooBar extends WrapperSupport<Foo> implements Foo {
        public FooBar(Foo delegate) {
            super(delegate);
        }

        @Override
        public void foo() throws SQLException {
            getDelegate().foo();
        }
    }

    @Test
    public void whenWrapping_expectInverseUnwrapToWork() throws SQLException {
        FooBar fooBar = new FooBar(new Bar());

        Assertions.assertTrue(fooBar.isWrapperFor(Foo.class));
        Assertions.assertInstanceOf(Foo.class, fooBar.unwrap(Foo.class));

        Assertions.assertTrue(fooBar.isWrapperFor(Bar.class));
        Assertions.assertInstanceOf(Bar.class, fooBar.unwrap(Bar.class));

        Assertions.assertFalse(fooBar.isWrapperFor(String.class));
        Assertions.assertThrows(SQLException.class, () -> fooBar.unwrap(String.class));
    }
}
