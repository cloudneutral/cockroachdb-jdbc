# CockroachDB JDBC Driver

[![Java CI](https://github.com/kai-niemi/cockroachdb-jdbc/actions/workflows/maven-publish.yml/badge.svg?branch=main)](https://github.com/kai-niemi/cockroachdb-jdbc/actions/workflows/maven-publish.yml)
[![coverage](.github/badges/jacoco.svg)](https://github.com/kai-niemi/cockroachdb-jdbc/actions/workflows/maven-publish.yml)
[![branches coverage](.github/badges/branches.svg)](https://github.com/kai-niemi/cockroachdb-jdbc/actions/workflows/maven-publish.yml)

<img align="left" src="docs/logo.png" />

An open-source JDBC Type-4 driver for [CockroachDB](https://www.cockroachlabs.com/) that wraps the PostgreSQL
JDBC driver ([pgjdbc](https://jdbc.postgresql.org/)) and communicates in the PostgreSQL native network
wire (v3.0) protocol with CockroachDB.

## Disclaimer

This driver is an experimental prototype and not officially supported by Cockroach Labs. Use of this driver
is entirely at your own risk and Cockroach Labs makes no guarantees or warranties about its operation.

See [MIT](LICENSE.txt) license for terms and conditions.

## Features

This JDBC driver adds the following features that are relevant to CockroachDB on top of the pgJDBC driver.

- Internal retries on serialization conflicts.
- Internal retries on connection errors.
- Rewriting qualified SQL queries to use [SELECT FOR UPDATE](https://www.cockroachlabs.com/docs/stable/select-for-update.html)
  to reduce serialization conflicts.
- CockroachDB specific database metadata (version etc).

All these features are disabled by default, which means the driver is operating in a pass-through mode
delegating all JDBC API invocations to the pgJDBC driver.

Enabling retries _may_ reduce the need for application-level retry logic and thereby enhance compatibility
with 3rd-party products that don't implement any transaction retries. Enabling `SELECT FOR UPDATE` rewrites
may reduce serialization conflicts from appearing in the first place and thereby reduce retries to a
bare minimum or none at all, at the expense of imposing locks on every read operation.

`SELECT FOR UPDATE` rewrites can be scope to connection level where all qualified `SELECT` queries are rewritten,
or to transaction level where all qualified `SELECT` within a given transaction are rewritten.

See the [design notes](docs/DESIGN.md) of how driver-level retries works and its limitations.

For more information about client-side retry logic, see also:

- [Transaction Contention](https://www.cockroachlabs.com/docs/stable/performance-best-practices-overview.html#transaction-contention)
- [Connection Retry Loop](https://www.cockroachlabs.com/docs/stable/node-shutdown.html#connection-retry-loop)

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

### cockroachdb-jdbc-it

Integration tests and functional tests activated via a Maven profiles.
See build section further down in this page.

### cockroachdb-jdbc-demo

A standalone demo app to showcase the retry mechanism and other features.

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
JDBC driver v 42.5+ (pgwire protocol v3.0).

It's build for Java 8 at language source and target level but requires Java 17 LTS for building.
For more details, see the building section.

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

The CockroachDB JDBC driver requires Java 17 (or later) LTS but is compiled to
on any platform for which there is a Java 8 runtime.

### Prerequisites

- JDK17+ LTS for building (OpenJDK compatible)
- Maven 3+ (optional, embedded)

If you want to build with the regular `mvn` command,
you will need [Maven v3.x](https://maven.apache.org/run-maven/index.html) or above.

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

Then activate the integration test Maven profile:

```bash
./mvnw -P it -Dgroups=anomaly-test clean install
```

Test groups include:

- anomaly-test - Runs through a series of RW/WR/WW anomaly tests.
- connection-retry-test - Runs a test with connection retries enabled.
- batch-insert-test - Batch inserts load test.
- batch-update-test - Batch updates load test.

See the [pom.xml](pom.xml) file for changing the database URL and other settings (under `ìt` profile).

## Terms of Use

See [MIT](LICENSE.txt) for terms and conditions.
