package io.cockroachdb.jdbc.demo;

import java.math.BigDecimal;

public class AccountLeg {
    private final Long id;

    private final BigDecimal amount;

    public AccountLeg(Long id, BigDecimal amount) {
        this.id = id;
        this.amount = amount;
    }

    public Long getId() {
        return id;
    }

    public BigDecimal getAmount() {
        return amount;
    }
}
