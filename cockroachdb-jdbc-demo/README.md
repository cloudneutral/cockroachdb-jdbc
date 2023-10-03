# CockroachDB JDBC Driver Demo

This project provides a simple high-contention workload using plain JDBC to 
showcase the effects of driver-level retries and select-for-update. It also 
provides an option to do client side retries.

To build the executable JAR, you need to enable the `demo-executable` maven profile:

## Building

```shell
../mvnw -P demo-executable clean install
```

## Running

The CLI accepts a few options, use `--help` for guidance. 

```shell
java -jar target/cockroachdb-jdbc-demo.jar --help  
```

## Demo Workload

The workload consists of reads and updates on a single `bank_account` table. There are two 
types of accounts: system and user accounts. The system accounts have an initial 
balance which is distributed to the user accounts concurrently. There's heavy 
contention on the system accounts and less on the user accounts.

If the workload runs to completion without any errors, the system account balances
will be zero.

SQL statements part of the `transferFunds` method that run concurrently:

```sql
-- for each system account 
-- for each user account 
BEGIN;
SELECT balance FROM bank_account WHERE id = :system_id;
-- Compute: balance - 100.00 as new_balance
UPDATE bank_account SET balance = :new_balance, updated_at=clock_timestamp() WHERE id = :system_id;
SELECT balance FROM bank_account WHERE id = :user_id;
-- Compute balance + 100.00 as new_balance
UPDATE bank_account SET balance = :new_balance, updated_at=clock_timestamp() WHERE id = :user_id;
COMMIT;
```

See the full schema [here](src/main/resources/db/create.sql) (created automatically).