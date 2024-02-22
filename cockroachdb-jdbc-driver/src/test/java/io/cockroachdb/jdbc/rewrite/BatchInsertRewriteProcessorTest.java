package io.cockroachdb.jdbc.rewrite;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;

@Tags(value = {
        @Tag("all-test"),
        @Tag("unit-test")
})
public class BatchInsertRewriteProcessorTest {
    @Test
    public void whenInsertWithPlaceholderBinding_expectRewrite() {
        String before = "INSERT into product (id,inventory,price,name,sku) values (?,?,?,?,?)";

        String after = BatchRewriteProcessor.rewriteInsertStatement(before);

        String expected = "INSERT INTO product (id, inventory, price, name, sku) "
                + "select "
                + "unnest(?) as id, "
                + "unnest(?) as inventory, "
                + "unnest(?) as price, "
                + "unnest(?) as name, "
                + "unnest(?) as sku";

        Assertions.assertEquals(expected.toLowerCase(), after.toLowerCase());
    }

    @Test
    public void whenInsertWithPlaceholderAndAtomBinding_expectRewrite() {
        String before = "INSERT into product (id,inventory,price,name,sku) values (?,123,foo((500.50)),?,?)";

        String after = BatchRewriteProcessor.rewriteInsertStatement(before);

        String expected = "INSERT INTO product (id, inventory, price, name, sku) "
                + "select "
                + "unnest(?) as id, "
                + "123, "
                + "foo((500.50)), "
                + "unnest(?) as name, "
                + "unnest(?) as sku";

        Assertions.assertEquals(expected.toLowerCase(), after.toLowerCase());
    }

    @Test
    public void whenInsertWithPlaceholderBindingOnConflictDoNothing_expectRewrite() {
        String before = "INSERT into product (id,inventory,price,name,sku) values (?,?,?,?,?) on conflict (id) do nothing";

        String after = BatchRewriteProcessor.rewriteInsertStatement(before);

        String expected = "INSERT INTO product (id, inventory, price, name, sku) "
                + "select "
                + "unnest(?) as id, "
                + "unnest(?) as inventory, "
                + "unnest(?) as price, "
                + "unnest(?) as name, "
                + "unnest(?) as sku "
                + "on conflict (id) do nothing";

        Assertions.assertEquals(expected.toLowerCase(), after.toLowerCase());
    }

    @Test
    public void whenInsertWithPlaceholderBindingOnConflictOnConstraintDoNothing_expectRewrite() {
        String before = "INSERT into product (id,inventory,price,name,sku) values (?,?,?,?,?) on conflict on constraint x do nothing";

        String after = BatchRewriteProcessor.rewriteInsertStatement(before);

        String expected = "INSERT INTO product (id, inventory, price, name, sku) "
                + "select "
                + "unnest(?) as id, "
                + "unnest(?) as inventory, "
                + "unnest(?) as price, "
                + "unnest(?) as name, "
                + "unnest(?) as sku "
                + "on conflict on constraint x do nothing";

        Assertions.assertEquals(expected.toLowerCase(), after.toLowerCase());
    }

}
