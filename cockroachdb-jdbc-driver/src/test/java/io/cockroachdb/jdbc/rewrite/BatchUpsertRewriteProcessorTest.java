package io.cockroachdb.jdbc.rewrite;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;

@Tags(value = {
        @Tag("all-test"),
        @Tag("unit-test")
})
public class BatchUpsertRewriteProcessorTest {
    @Test
    public void whenUpsertWithPlaceholderBinding_expectRewrite() {
        String before = "UPSERT into product (id,inventory,price,name,sku) values (?,?,?,?,?)";

        String after = BatchRewriteProcessor.rewriteUpsertStatement(before);

        String expected = "UPSERT INTO product (id, inventory, price, name, sku) "
                + "select "
                + "unnest(?) as id, "
                + "unnest(?) as inventory, "
                + "unnest(?) as price, "
                + "unnest(?) as name, "
                + "unnest(?) as sku";

        Assertions.assertEquals(expected.toLowerCase(), after.toLowerCase());
    }

    @Test
    public void whenUpsertWithPlaceholderAndAtomBinding_expectRewrite() {
        String before = "UPSERT into product (id,inventory,price,name,sku) values (?,123,foo((500.50)),?,?)";

        String after = BatchRewriteProcessor.rewriteUpsertStatement(before);

        String expected = "UPSERT INTO product (id, inventory, price, name, sku) "
                + "select "
                + "unnest(?) as id, "
                + "123, "
                + "foo((500.50)), "
                + "unnest(?) as name, "
                + "unnest(?) as sku";

        Assertions.assertEquals(expected.toLowerCase(), after.toLowerCase());
    }
}
