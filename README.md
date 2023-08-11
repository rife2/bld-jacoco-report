# [Bld](https://rife2.com/bld) Extension to Code Coverage Analysis with [JaCoCo](https://www.eclemma.org/jacoco/)


[![License (3-Clause BSD)](https://img.shields.io/badge/license-BSD%203--Clause-blue.svg?style=flat-square)](http://opensource.org/licenses/BSD-3-Clause)
[![Java](https://img.shields.io/badge/java-17%2B-blue)](https://www.oracle.com/java/technologies/javase/jdk17-archive-downloads.html)
[![Release](https://flat.badgen.net/maven/v/metadata-url/repo.rife2.com/releases/com/uwyn/rife2/bld-jacoco-report/maven-metadata.xml?color=blue)](https://repo.rife2.com/#/releases/com/uwyn/rife2/bld-jacoco-report)
[![Snapshot](https://flat.badgen.net/maven/v/metadata-url/repo.rife2.com/snapshots/com/uwyn/rife2/bld-jacoco-report/maven-metadata.xml?label=snapshot)](https://repo.rife2.com/#/snapshots/com/uwyn/rife2/bld-jacoco-report)
[![GitHub CI](https://github.com/rife2/bld-jacoco-report/actions/workflows/bld.yml/badge.svg)](https://github.com/rife2/bld-jacoco-report/actions/workflows/bld.yml)

To run the tests and generate the code coverage reports:

```java
@BuildCommand(summary = "Generates Jacoco Reports")
public void jacoco() throws IOException {
    new JacocoReportOperation()
            .fromProject(this)
            .execute();
}
```

```text
./bld compile jacoco
```

- The HTML, CVS and XML reports will be automatically created in the `build/reports/jacoco/test` directory.
- The execution coverage data will be automatically recorded in the `build/jacoco/jacoco.exec` file.

Please check the [JacocoReportOperation documentation](https://rife2.github.io/bld-jacoco-report/rife/bld/extension/JacocoOperation.html#method-summary) for all available configuration options.
