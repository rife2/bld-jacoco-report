/*
 *  Copyright 2023 the original author or authors.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package rife.bld.extension;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class JacocoReportOperationTest {
    final Path tempDir;

    JacocoReportOperationTest() throws IOException {
        tempDir = Files.createTempDirectory("jacoco-test");
    }

    JacocoReportOperation newJacocoReportOperation() {
        var o = new JacocoReportOperation();
        o.csv(new File(tempDir.toFile(), "jacoco.csv"));
        return o;
    }

    @Test
    void executeTest() {
        assertThat(true).isTrue(); // TODO
    }
}