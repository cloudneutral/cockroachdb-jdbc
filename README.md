# CockroachDB JDBC Driver

[![Java CI](https://github.com/kai-niemi/cockroachdb-jdbc/actions/workflows/maven-publish.yml/badge.svg?branch=main)](https://github.com/kai-niemi/cockroachdb-jdbc/actions/workflows/maven-publish.yml)
[![coverage](.github/badges/jacoco.svg)](https://github.com/kai-niemi/cockroachdb-jdbc/actions/workflows/maven-publish.yml)
[![branches coverage](.github/badges/branches.svg)](https://github.com/kai-niemi/cockroachdb-jdbc/actions/workflows/maven-publish.yml)

<img align="left" src="docs/logo.png" />

An open-source JDBC Type-4 driver for [CockroachDB](https://www.cockroachlabs.com/) that wraps the PostgreSQL
JDBC driver ([pgJDBC](https://jdbc.postgresql.org/)) and communicates in the PostgreSQL native network
wire (v3.0) protocol with CockroachDB.

## Disclaimer 

This project is not officially supported by Cockroach Labs. Use of this driver is 
entirely at your own risk and Cockroach Labs makes no guarantees or warranties 
about its operation. 

See [MIT](LICENSE.txt) for terms and conditions. 

## Features

This JDBC driver adds the following features on top of pgJDBC:

- Driver-level retries on serialization conflicts
- Driver-level retries on connection errors
- Rewriting qualified SELECT queries to use [SELECT .. FOR UPDATE](https://www.cockroachlabs.com/docs/stable/select-for-update.html)  to reduce serialization conflicts.
- Rewriting batch INSERT, UPSERT and UPDATE statements to use array un-nesting to speed up bulk operations.
- CockroachDB database metadata (version information, etc).

All features are opt-in and disabled by default. The default operational mode fo this driver
is therefore to operate in a pass-through mode and delegate most JDBC API invocations 
to the pgJDBC driver (metadata only exception).
                   
### Reducing Retries

Enabling explicit transaction retries with `SELECT .. FOR UPDATE` query rewrites _may_ reduce the need for 
application-level retry logic and enhanced compatibility with 3rd-party products that don't 
implement any retry logic. Enabling `SELECT .. FOR UPDATE` rewrites alone may reduce serialization 
conflicts from appearing and reduce retries to a bare minimum or none at all, at the expense of 
imposing locks on every qualified (read-only) SELECT operation. `SELECT FOR UPDATE` rewrites are 
scope to either connection level where all qualified `SELECT` queries are rewritten, or to transaction 
level where all qualified `SELECT` within a given transaction are rewritten.

See the [design notes](docs/DESIGN.md) of how driver-level retries works and its limitations. For more information 
about client-side retry logic, see also:

- [Transaction Contention](https://www.cockroachlabs.com/docs/stable/performance-best-practices-overview.html#transaction-contention)
- [Connection Retry Loop](https://www.cockroachlabs.com/docs/stable/node-shutdown.html#connection-retry-loop)

### Speeding up bulk operations

The driver can rewrite batch `UPDATE`, `INSERT` and `UPSERT` DML statements to use SQL array un-nesting. This will 
drastically improve performance for bulk operations that otherwise pass single statements over the wire, 
unless the pgJDBC `reWriteBatchedInserts` properties is set to `true`. The limitation of the pgJDBC rewrite however,
is that the batch size has a hard limit of 128, and it only applies to INSERT statements. 

This driver removes these 
limitations and enable full batching-over-the-wire for `INSERT`, `UPDATE` and `UPSERT` 
(separate from `INSERT .. on CONFLICT DO ..`) statements.

#### Examples
               
Assume two separate product update statements:

```sql
UPDATE product SET inventory=11, price=200.00, version=version+1, last_updated_at = with_min_timestamp(transaction_timestamp()) 
               WHERE id='00000000-0000-0000-0000-000000000000'::uuid and version=0;
UPDATE product SET inventory=21, price=300.00, version=version+1, last_updated_at = with_min_timestamp(transaction_timestamp()) 
               WHERE id='00000000-0000-0000-0000-000000000001'::uuid and version=0;
```

After rewrite and collapsed to a single update using un-nested arrays:

```sql
update product set inventory=_dt.p1, price=_dt.p2, version=product.version + 1, last_updated_at=with_min_timestamp(transaction_timestamp())
from (select unnest(ARRAY[11,21]) as p1,
             unnest(ARRAY[200.00,300.00]) as p2,
             unnest(ARRAY['00000000-0000-0000-0000-000000000000'::uuid,'00000000-0000-0000-0000-000000000001'::uuid]) as p3) 
         as _dt
where product.id=_dt.p3 and product.version=0;
```

The driver level rewrite is transparent to clients that only use the normal `PreparedStatement`'s `addBatch` 
and `executeBatch` methods.

```java
try (PreparedStatement ps = connection.prepareStatement(
        "UPDATE product SET inventory=?, price=? version=? last_updated_at = with_min_timestamp(transaction_timestamp()) " +
        "WHERE id=? and version=?")) {

    chunkOfProducts.forEach(product -> {
        ps.setInt(1, product.getInventory());
        ps.setInt(2, product.getVersion());
        ps.setBigDecimal(3, product.getPrice());
        ps.setObject(4, product.getId());
        ps.setInt(5, product.getVersion());

        ps.addBatch();
    });
    ps.executeBatch();  
} catch (SQLException ex) {
}
```

The SQL parser in the driver uses a limited [SQL grammar](cockroachdb-jdbc-driver/src/main/resources/io/cockroachdb/jdbc/rewrite/CockroachParser.g4), 
so there are limitations on what type of CockroachDB DML statements (full [grammar](https://www.cockroachlabs.com/docs/stable/sql-grammar)) 
it supports. The basic SQL elements are supported including scalar expressions, logical and binary
expressions, function calls, type casts and NULL expressions. 

If more complex expressions like window functions are used, the driver silently reverts
back to using normal pass-through batch operations (meaning singleton UPDATE and UPSERT).

## Getting Started

Example of creating a JDBC connection and executing a simple `SELECT` query in an implicit transaction
(auto-commit):

```java
try (Connection connection 
        = DriverManager.getConnection("jdbc:cockroachdb://localhost:26257/jdbc_test?sslmode=disable") {
  try (Statement statement = connection.createStatement()) {
    try (ResultSet rs = statement.executeQuery("select version()")) {
      if (rs.next()) {
        System.out.println(rs.getString(1));
      }
    }
  }
}
```

Example of executing a `SELECT` and an `UPDATE` in an explicit transaction with `FOR UPDATE` rewrites:

```java
try (Connection connection
             = DriverManager.getConnection("jdbc:cockroachdb://localhost:26257/jdbc_test?sslmode=disable")) {
    connection.setAutoCommit(false);

    try (Statement statement = connection.createStatement()) {
        statement.execute("SET implicitSelectForUpdate = true");
    }

    // Will be rewritten by the driver to include suffix "FOR UPDATE"
    try (PreparedStatement ps = connection.prepareStatement("select balance from account where id=?")) {
        ps.setLong(1, 100L);

        try (ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                BigDecimal balance = rs.getBigDecimal(1); // check
                try (PreparedStatement ps2 = connection.prepareStatement("update account set balance = balance + ? where id=?")) {
                    ps2.setBigDecimal(1, new BigDecimal("10.50"));
                    ps2.setLong(2, 100L);
                    ps2.executeUpdate(); // check
                }
            }
        }
    }
    connection.commit();
}
```

Same as above where all qualified `SELECT`s are suffixed with `FOR UPDATE`:

```java
try (Connection connection
             = DriverManager.getConnection("jdbc:cockroachdb://localhost:26257/jdbc_test?sslmode=disable&implicitSelectForUpdate=true")) {
    connection.setAutoCommit(false);
    ...
    connection.commit();
}
```

## Maven configuration

Add this dependency to your `pom.xml` file:

```xml
<dependency>
    <groupId>io.cockroachdb.jdbc</groupId>
    <artifactId>cockroachdb-jdbc-driver</artifactId>
    <version>{version}</version>
</dependency>
```

Then add the Maven repository to your `pom.xml` file (alternatively in Maven's [settings.xml](https://maven.apache.org/settings.html)):

```xml
<repository>
    <id>github</id>
    <name>Cockroach Labs Maven Packages</name>
    <url>https://maven.pkg.github.com/kai-niemi/cockroachdb-jdbc</url>
    <snapshots>
        <enabled>true</enabled>
    </snapshots>
</repository>
```

Finally, you need to authenticate to GitHub Packages by creating a personal access token (classic)
that includes the `read:packages` scope. For more information, see [Authenticating to GitHub Packages](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-apache-maven-registry#authenticating-to-github-packages).

Add your personal access token to the servers section in your [settings.xml](https://maven.apache.org/settings.html):

```xml
<server>
    <id>github</id>
    <username>your-github-name</username>
    <password>your-access-token</password>
</server>
```
Take note that the server and repository id's must match (it can be different than `github`).

Now you should be able to build your own project with the JDBC driver as a dependency:

```shell
mvn clean install
```

Alternatively, you can just clone the repository and build it locally using `mvn install`. See
the building section at the end of this page.

## Modules

### cockroachdb-jdbc-driver

The main library for the CockroachDB JDBC driver.

### cockroachdb-jdbc-demo

A standalone demo app to showcase the retry mechanism and other features.

### cockroachdb-jdbc-test

Integration and functional tests activated via Maven profiles. See build section 
further down in this page.

## Getting Help

### Reporting Issues

This driver uses [GitHub](https://github.com/kai-niemi/cockroachdb-jdbc/issues) as issue tracking system
to record bugs and feature requests. If you want to raise an issue, please follow the recommendations below:

* Before you log a bug, please search the [issue tracker](https://github.com/kai-niemi/cockroachdb-jdbc/issues)
  to see if someone has already reported the problem.
* If the issue doesn't exist already, [create a new issue](https://github.com/kai-niemi/cockroachdb-jdbc/issues).
* Please provide as much information as possible with the issue report, we like to know the version of Spring Data
  that you are using and JVM version, complete stack traces and any relevant configuration information.
* If you need to paste code, or include a stack trace format it as code using triple backtick.

### Supported CockroachDB and JDK Versions

This driver is CockroachDB version agnostic and supports any version supported by the PostgreSQL
JDBC driver v 42.6+ (pgwire protocol v3.0).

It requires Java 17 (LTS) or later for building and at runtime. For more details, see the building section.

## URL Properties

This driver uses the `jdbc:cockroachdb:` JDBC URL prefix and supports all PostgreSQL URL properties
on top of that. To configure a datasource to use this driver, you typically configure it for PostgreSQL
and only change the URL prefix and the driver class name.

The general format for a JDBC URL for connecting to a CockroachDB server:

    jdbc:cockroachdb:[//host[:port]/][database][?property1=value1[&property2=value2]...]

See [pgjdbc](https://github.com/pgjdbc/pgjdbc) for all supported driver properties
and the semantics.

In addition, this driver has the following CockroachDB specific properties:

### retryTransientErrors

(default: false)

The JDBC driver will automatically retry serialization failures
(40001 [state code](https://github.com/pgjdbc/pgjdbc/blob/master/pgjdbc/src/main/java/org/postgresql/util/PSQLState.java))
at read, write or commit time. This is done by keeping track of all statements and the results during a transaction,
and if the transaction is aborted due to a transient 40001 error, it will rollback and retry the recorded operations
on a new connection and compare the results with the initial commit attempt. If the results are different, the
driver will be forced to give up the retry attempt to preserve a serializable outcome.

Enable this option if you want to handle aborted transactions internally in the driver, preferably combined with
select-for-update locking. Leave this option disabled if you want to handle aborted transactions in your
own application.

### retryConnectionErrors

(default: false)

The CockroachDB JDBC driver will automatically retry transient connection errors with SQL state
08001, 08003, 08004, 08006, 08007, 08S01 or 57P01 at read, write or commit time.

Applicable only when `retryTransientErrors` is also true.

Disable this option if you want to handle connection errors in your own application or connection pool.

**CAUTION!** Retrying on non-serializable conflict errors (i.e anything but 40001) may produce duplicate outcomes
if the SQL statements are non-idempotent. See the [design notes](docs/DESIGN.md) for more details..

### retryListenerClassName

(default: `io.cockroachdb.jdbc.retry.LoggingRetryListener`)

Name of class that implements `io.cockroachdb.jdbc.retry.RetryListener` to be used to receive
callback events when retries occur. One instance is created for each JDBC connection.

### retryStrategyClassName

(default: `io.cockroachdb.jdbc.retry.ExponentialBackoffRetryStrategy`)

Name of class that implements `io.cockroachdb.jdbc.retry.RetryStrategy` to be used when `retryTransientErrors`
property is set to `true`. If this class also implements `io.cockroachdb.jdbc.proxy.RetryListener` it will receive
callback events when retries happen. One instance of this class is created for each JDBC connection.

The default `ExponentialBackoffRetryStrategy` will use an exponentially increasing delay
with jitter and a multiplier of 2 up to the limit set by `retryMaxBackoffTime`.

### retryMaxAttempts

(default: 15)

Maximum number of retry attempts on transient failures (connection errors / serialization conflicts).
If this limit is exceeded, the driver will throw a SQL exception with the same state code signalling
its yielding further retry attempts.

### retryMaxBackoffTime

(default: 30s)

Maximum exponential backoff time in format of a duration expression (like `12s`).
The duration applies for the total time for all retry attempts at transaction level.

Applicable only when `retryTransientErrors` is true.

### implicitSelectForUpdate

(default: false)

The driver will automatically append a `FOR UPDATE` clause to all qualified `SELECT` statements
within connection scope. This parameter can also be set in an explicit transaction as a session
variable in which case its scoped to the transaction.

The qualifying requirements include:

- Not used in a read-only connection
- No time travel clause (`as of system time`)
- No aggregate functions
- No group by or distinct operators
- Not referencing internal table schema

A `SELECT .. FOR UPDATE` will lock the rows returned by a selection query such that other transactions
trying to access those rows are forced to wait for the transaction that locked the rows to finish.
These other transactions are effectively put into a queue based on when they tried to read the value
of the locked rows. It does not eliminate the chance of serialization conflicts but greatly reduces it.

### useCockroachMetadata

(default: false)

By default, the driver will use PostgreSQL JDBC driver metadata provided in `java.sql.DatabaseMetaData`
rather than CockroachDB specific metadata. While the latter is more correct, it causes incompatibilities
with libraries that bind to PostgreSQL version details, such as Flyway and other tools.

### reWriteBatchedInserts (since 1.1)

(default: false)

Enable optimization to rewrite batch `INSERT` statements to use arrays. 

### reWriteBatchedUpserts (since 1.1)

(default: false)

Enable optimization to rewrite batch `UPSERT` statements to use arrays.

### reWriteBatchedUpdates (since 1.1)

(default: false)

Enable optimization to rewrite batch `UPDATE` statements to use arrays.

## Logging

This driver uses [SLF4J](https://www.slf4j.org/) for logging which means its agnostic to the logging
framework used by the application. The JDBC driver module does not include any logging framework
dependency transitively.

## Additional Examples

### Plain Java Example

```java
Class.forName(CockroachDriver.class.getName());

try (Connection connection 
        = DriverManager.getConnection("jdbc:cockroachdb://localhost:26257/jdbc_test?sslmode=disable&implicitSelectForUpdate=true&retryTransientErrors=true") {
  try (Statement statement = connection.createStatement()) {
    try (ResultSet rs = statement.executeQuery("select version()")) {
      if (rs.next()) {
        System.out.println(rs.getString(1));
      }
    }
  }
}
```

### Spring Boot Example

Configure the datasource in `src/main/resources/application.yml`:

```yml
spring:
  datasource:
    driver-class-name: io.cockroachdb.jdbc.CockroachDriver
    url: "jdbc:cockroachdb://localhost:26257/jdbc_test?sslmode=disable&application_name=MyTestAppe&implicitSelectForUpdate=true&retryTransientErrors=true"
    username: root
    password:
```

Optionally, configure the datasource programmatically and use the
[TTDDYY](https://github.com/jdbc-observations/datasource-proxy) datasource logging proxy:

```java
@Bean
@Primary
public DataSource dataSource() {
    return ProxyDataSourceBuilder
            .create(hikariDataSource())
            .traceMethods()
            .logQueryBySlf4j(SLF4JLogLevel.DEBUG, "io.cockroachdb.jdbc")
            .asJson()
            .multiline()
            .build();
}

@Bean
@ConfigurationProperties("spring.datasource.hikari")
public HikariDataSource hikariDataSource() {
    HikariDataSource ds = dataSourceProperties()
            .initializeDataSourceBuilder()
            .type(HikariDataSource.class)
            .build();
    ds.setAutoCommit(false);
    ds.addDataSourceProperty(PGProperty.REWRITE_BATCHED_INSERTS.getName(), "true");
    ds.addDataSourceProperty(CockroachProperty.IMPLICIT_SELECT_FOR_UPDATE.getName(), "true");
    ds.addDataSourceProperty(CockroachProperty.RETRY_TRANSIENT_ERRORS.getName(), "true");
    ds.addDataSourceProperty(CockroachProperty.RETRY_MAX_ATTEMPTS.getName(), "5");
    ds.addDataSourceProperty(CockroachProperty.RETRY_MAX_BACKOFF_TIME.getName(), "10000");
    return ds;
}
```

To configure `src/main/resources/logback-spring.xml` to capture all SQL statements and JDBC API calls:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <include resource="org/springframework/boot/logging/logback/defaults.xml"/>
    <include resource="org/springframework/boot/logging/logback/console-appender.xml" />

    <logger name="org.springframework" level="INFO"/>

    <logger name="io.cockroachdb.jdbc" level="DEBUG"/>

    <root level="INFO">
        <appender-ref ref="CONSOLE"/>
    </root>
</configuration>
```

## Versioning

This library follows [Semantic Versioning](http://semver.org/).

## Building

### Prerequisites

- JDK17+ LTS (OpenJDK compatible)
- Maven 3+ (optional, embedded wrapper available)

If you want to build with the regular `mvn` command, you will need [Maven v3.x](https://maven.apache.org/run-maven/index.html) or above.

Install the JDK (Linux):

```bash
sudo apt-get -qq install -y openjdk-17-jdk
```

Install the JDK (macOS):

```bash
brew install openjdk@17 
```

### Clone the project

```bash
git clone git@github.com:kai-niemi/cockroachdb-jdbc.git
cd cockroachdb-jdbc
```

### Build the project

```bash
chmod +x mvnw
./mvnw clean install
```

The JDBC driver jar is now found in `cockroachdb-jdbc-driver/target`.

### Run Integration Tests

The integration tests will run through a series of contended workloads to exercise the
retry mechanism and other driver features.

First start a [local](https://www.cockroachlabs.com/docs/stable/start-a-local-cluster.html) CockroachDB node or cluster.

Create the database:

```bash
cockroach sql --insecure --host=localhost -e "CREATE database jdbc_test"
```

Then activate the anomaly integration test Maven profile:

```bash
./mvnw -P test-local -Dgroups=anomaly-test clean install
```

Available test groups include:

- anomaly-test - Runs through a series of RW/WR/WW anomaly tests.
- connection-retry-test - Runs a test with connection retries enabled.
- batch-insert-test - Batch inserts load test (using different batch sizes).
- batch-update-test - Batch updates load test.
- batch-rewrite-test - Batch DML rewrite test.

See the [pom.xml](pom.xml) file for changing the database URL and other settings (under `test` profile).

## Terms of Use

See [MIT](LICENSE.txt) for terms and conditions.
