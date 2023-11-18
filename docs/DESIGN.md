# CockroachDB JDBC Driver Design Notes

This is a JDBC Type-4 driver for [CockroachDB](https://www.cockroachlabs.com/) that wraps the PostgreSQL JDBC driver ([pgjdbc](https://jdbc.postgresql.org/)) 
that must also be on the app's classpath. There are no other dependencies besides [SLF4J](https://www.slf4j.org/) for which 
any supported logging framework can be used like logback.

The driver accepts a unique JDBC URL prefix `jdbc:cockroachdb` to separate itself from `jdbc:postgressql`. 
When the driver is asked to open a connection (typically by a connection pool), it passes the call forward 
to pgJDBC and then wraps the connection in a proxy with a custom interceptor (invocation handler) capable
of enhancing SQL queries and performing retries. The driver delegates all calls to the underlying 
pgJDBC driver and does not interact directly with the database itself at any point over 
pg-wire or http.

Conceptual diagram:

<img alt="overview" height="512" src="overview.jpg" width="614"/>

## Internal Retries

One of driver features include internal retries. In contrast to application or client side retries which is 
the more typical choice. It works by the driver wrapping each JDBC connection, statement and result set in a 
dynamic proxy and interceptors capable of detecting and retrying aborted transactions. Warranted that 
the SQL exceptions are of a qualified type. The qualifying exception types are of two main categories: 
_Serialization Conflicts_ and _Connection Errors_.

### Serialization Conflicts

If enabled, the JDBC driver can perform internal retries of failed transactions due to serialization conflicts
denoted by the `40001` [state code](https://github.com/pgjdbc/pgjdbc/blob/master/pgjdbc/src/main/java/org/postgresql/util/PSQLState.java).  Serialization conflict errors with that code are safe to retry 
by the client, or in this case, the driver. Safe, in terms of not producing duplicate side effects when
repeated. These errors are more likely to manifest in any database running with serializable 
transaction isolation (1SR), in particular for contended workloads subject to read/write, write/read
and write/write conflicts.

There are however limitations with using driver level retries. See the implementation section below 
for more details.

### Connection Errors

If enabled, the JDBC driver can perform internal retries on connection or communication errors denoted by any of 
the `08001, 08003, 08004, 08006, 08007, 08S01 or 57P01` [state codes](https://github.com/pgjdbc/pgjdbc/blob/master/pgjdbc/src/main/java/org/postgresql/util/PSQLState.java).

Connection errors during in-flight transactions are generally safe to retry, but there is a potential 
for duplicate side effects if the SQL operations performed are non-idempotent. Like INSERTs or UPDATEs 
with increment operations (`UPDATE x set y=y-1`). This could happen for example if a transaction commit 
was successful but the response back to the client was lost due to a connection link failure. In that case, 
the result of the transaction is _ambiguous_ and the driver cant tell if the transaction was successfully 
committed or rolled back. This principle apply for both driver and client-side retries.

### Implementation Notes

Transaction conflicts and connection errors can surface at read, write and commit time, which means there 
are retry interceptors wrapped around the following JDBC API artifacts:

- `java.sql.Connection`
    - implemented by `CockroachConnection`
    - proxied by `ConnectionRetryInterceptor`, retries on `commit()`
- `java.sql.Statement`
    - implemented by `CockroachStatement`
    - proxied by `StatementRetryInterceptor`, retries on write operations
- `java.sql.PreparedStatement`
    - implemented by `CockroachPreparedStatement`
    - proxied by `PreparedStatementRetryInterceptor`, retries on write operations
- `java.sql.ResultSet`
    - implemented by `CockroachResultSet`
    - proxied by `ResultSetRetryInterceptor`, retries on read operations

Retries are made possible by recording most JDBC operations during an _explicit_ transaction (when `autoCommit` set to `false`). 
If a transaction is aborted due to a transient error, it will be rolled back and the connection is closed. 
The recorded operations are then repeated on a new connection delegate while comparing the last results against 
the results of the initial failed transaction attempt. If the results observed by the application client are 
in any way different (determined by SHA-256 checksums), the driver is forced to give up the retry attempt 
to preserve a serializable outcome towards the application, which is still awaiting completion. 

To illustrate:

```java
try (Connection connection 
        = DriverManager.getConnection("jdbc:cockroachdb://localhost:26257/jdbc_test?sslmode=disable") {
  try (PreparedStatement ps = connection.prepareStatement("update table set x = ? where id = ?")) {
        ps.setObject(1, x);
        ps.setObject(2, y);
        ps.executeUpdate();
  }
}
```

In the above ^^ example, assume the `executeUpdate()` method throws a SQLException with state code `40001`
due to a write-write conflict. This exception is caught by the retry interceptor which will rollback and 
close the current connection in the background, then repeat the recorded operations on a new connection 
delegate and hope for a different interleaving of other concurrent transactions that would allow for 
the transaction to complete. 

From the applications perspective, the `executeUpdate()` operation will block until this process is 
either successful or considered futile, in which case a separate SQLException is thrown with the 
same state code.

### Limitations of driver level retries

When using application-level retries you would typically apply logic something like the following:

```java       
int numCalls=1;
do {
  try {
      // Must begin and commit/rollback transactions
      return businessService.someTransactionBoundaryOperation(); 
  } catch (SQLException sqlException) { // Catch r/w and commit time exceptions
      // 40001 is the only state code we are looking for in terms of safe retries
      if (PSQLState.SERIALIZATION_FAILURE.getState().equals(sqlException.getSQLState())) {
          // handle by logging and waiting with an exponentially increasing delay
      } else {
          throw sqlException; // Some other error, re-throw instantly
      }
  }
} while (numCalls < MAX_RETRY_ATTEMPTS);
```

This type of retry loops fits quite well into an [AOP aspect](https://docs.spring.io/spring-framework/docs/current/reference/html/core.html#aop) with an [around advice](https://docs.spring.io/spring-framework/docs/current/reference/html/core.html#aop-ataspectj-around-advice) (interceptor), 
weaved in between the caller and transaction boundary (typically a [service facade](https://en.wikipedia.org/wiki/Facade_pattern), [service activator](https://www.enterpriseintegrationpatterns.com/MessagingAdapter.html), 
or web/api controller).   

Application-level retries always have a higher chance of success over driver-level because the application logic
is applied in each repeat cycle. For example, if you are checking for negative account balance in the app-code, 
then it may cancel out additional writes based on the read value when the operation is repeated. Neither the JDBC 
driver or database have any visibility to the application logic, which means that a retry attempt can only succeed 
if all previously observed outcomes are identical to the new ones.

The practical use of driver-level retries is therefore more narrow for common read/write and write/read conflicts,
in which case client-side retries is the preferred approach.
     
## Implicit SELECT FOR UPDATE rewrites

The JDBC driver can optionally append a `FOR UPDATE` clause to qualified SELECT statements. 
A SELECT query qualifies for a rewrite when:

- It's not part of a read-only connection
- There are no aggregate functions
- There are no distinct or GROUP BY operators
- There are no internal CockroachDB schema references

A `SELECT .. FOR UPDATE` will lock the rows returned by a selection query such that other transactions trying to 
access those rows are forced to wait for the transaction that locked the rows to finish. These other transactions 
are effectively put into a queue based on when they tried to read the value of the locked rows. 

Notice that this does not eliminate the chance of serialization conflicts (which can also be due to 
[time uncertainty](https://www.cockroachlabs.com/docs/stable/architecture/transaction-layer.html#transaction-conflicts)) 
but will greatly reduce it. Combined with driver-level retries, this can eliminate the need for app-level 
retry logic for some workloads.

The following example shows a write skew (G2-item) scenario which is prevented by 
CockroachDB serializable isolation:

| T1                                       | T2                                         |
|------------------------------------------|--------------------------------------------|
| begin;                                   | begin;                                     |
| select * from test where id in (1,2);    |                                            |
|                                          | select * from test where id in (1,2);      |
| update test set value = 11 where id = 1; | (reads 10,20)                              |
|                                          | update test set value = 21 where id = 2;   |
| commit;                                  |                                            |
|                                          | commit; --- "ERROR: restart transaction.." |

Running the same sequence with FOR UPDATE:

| T1                                               | T2                                               |
|--------------------------------------------------|--------------------------------------------------|
| begin;                                           | begin;                                           |
| select * from test where id in (1,2) FOR UPDATE; |                                                  |
|                                                  | select * from test where id in (1,2) FOR UPDATE; |
|                                                  | -- blocks on T1                                  |
| update test set value = 11 where id = 1;         |                                                  |
| commit;                                          | -- unblocked, reads 11,20                        |
|                                                  | update test set value = 21 where id = 2;         |
|                                                  | commit;                                          |
                                          
The initial read in T1 will lock the rows and T2 is forced to wait for T1 to finish. When T1 has finished 
with a commit, the read in T2 is reflecting the write of T1 and not that of T2 at the initial read 
timestamp. The T2 read is effectively pushed into the future with the desired effect of these 
operations resulting in a serializable transaction ordering, allowing for both to commit.

## Limitations of bulk operation rewrites

The driver's SQL parser uses a limited [SQL grammar](../cockroachdb-jdbc-driver/src/main/resources/io/cockroachdb/jdbc/rewrite/CockroachParser.g4) 
for rewrites, so there are some limitations on what type of CockroachDB DML statements (full [grammar](https://www.cockroachlabs.com/docs/stable/sql-grammar)) it supports.

The basic SQL DML elements are supported including scalar expressions, logical and binary expressions, 
nested function calls, type casts and NULL expressions. That should cover most ORM generated DML statements 
and a bit more. For more complex expression with sub-queries, window functions etc., the driver silently 
reverts to using normal pass-through batch operations. The application doesn't see this switch and it 
does not impose any additional statement creation.

In addition to grammar limitations, certain [PreparedStatement](https://docs.oracle.com/en/java/javase/17/docs/api/java.sql/java/sql/PreparedStatement.html)
methods also invalidate batch rewrites when its obvious it's not a DML statements but a SELECT query, 
or otherwise does not qualify for SQL arrays (like when using streams).

Supported `PreparedStatement` setter methods include:

- void setNull(int parameterIndex, int sqlType)
- void setBoolean(int parameterIndex, boolean x)
- void setByte(int parameterIndex, byte x)
- void setShort(int parameterIndex, short x)
- void setInt(int parameterIndex, int x)
- void setLong(int parameterIndex, long x)
- void setFloat(int parameterIndex, float x)
- void setDouble(int parameterIndex, double x)
- void setBigDecimal(int parameterIndex, BigDecimal x)
- void setString(int parameterIndex, String x)
- void setBytes(int parameterIndex, byte[] x)
- void setDate(int parameterIndex, Date x)
- void setTime(int parameterIndex, Time x)
- void setTimestamp(int parameterIndex, Timestamp x)
- void setObject(int parameterIndex, Object x, int targetSqlType)
- void setObject(int parameterIndex, Object x)
- void setRef(int parameterIndex, Ref x)
- void setArray(int parameterIndex, Array x)
- void setURL(int parameterIndex, URL x)
- void setNString(int parameterIndex, String value)
- void setSQLXML(int parameterIndex, SQLXML xmlObject)

## Appendix: Sequence Diagrams

The driver concepts are illustrated with sequence diagrams using [https://www.websequencediagrams.com](https://www.websequencediagrams.com).

### Happy Path 

This diagram illustrates executing a single update with happy outcome, equivalent to:

```java
try (Connection connection 
        = DriverManager.getConnection("jdbc:cockroachdb://localhost:26257/jdbc_test?sslmode=disable") {
  try (PreparedStatement ps = connection.prepareStatement("update table set x = ? where id = ?")) {
        ps.setObject(1, x);
        ps.setObject(2, y);
        ps.executeUpdate();
  }
}
```

(Connection pooling and parameter bindings omitted)

- [Diagram Script](happy-wsd.txt)

<img alt="overview" height="512" src="happy-wsd.png" width="614"/>

### Unhappy Path

This diagram illustrates executing the same single block with an unhappy outcome, equivalent to:

- [Diagram Script](unhappy-wsd.txt)

<img alt="overview" height="512" src="unhappy-wsd.png" width="614"/>

