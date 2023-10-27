# CockroachDB JDBC Driver Demo

A simple high-contention workload using plain JDBC to showcase the effects 
of driver-level retries and select-for-update vs client-level retries. 

To build the executable JAR, you need to enable the `demo-jar` maven profile:

## Building

```shell
(from project-root)
../mvnw -P demo-jar clean install
```

## Running

The CLI accepts a few options, use `--help` for guidance. 

```shell
java -jar target/cockroachdb-jdbc-demo.jar --help  
```

## Workload Description

The workload consists of reads and updates on a single `bank_account` table. 
There are two types of accounts: system and user accounts. The system accounts 
have an initial balance which is distributed to the user accounts concurrently. 
Thus, the main contention point is on the system accounts and not so much on 
the user accounts. This particular anomaly is called P4 lost update, which is
a write-write conflict prevented in 1SR but allowed in RC.

If the workload runs to completion without any errors, the system account balances 
will be drained to zero.

The SQL statements part of the `transferFunds` method executed concurrently:

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