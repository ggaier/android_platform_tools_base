/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.testutils.truth;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.google.common.base.Joiner;
import com.google.common.truth.FailureStrategy;
import com.google.common.truth.Subject;
import com.google.common.truth.SubjectFactory;
import com.google.common.truth.Truth;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Arrays;
import java.util.List;

/**
 * Truth support for validating java.nio.file.Path.
 */
@SuppressWarnings("NonBooleanMethodNameMayNotStartWithQuestion")  // Functions do not return.
public class PathSubject extends Subject<PathSubject, Path> {

    public static final SubjectFactory<PathSubject, Path> FACTORY =
            new SubjectFactory<PathSubject, Path>() {
                @Override
                public PathSubject getSubject(FailureStrategy fs, Path that) {
                    return new PathSubject(fs, that);
                }
            };

    public PathSubject(FailureStrategy failureStrategy, Path subject) {
        super(failureStrategy, subject);
    }

    public static PathSubject assertThat(@Nullable Path path) {
        return Truth.assert_().about(PathSubject.FACTORY).that(path);
    }

    public static PathSubject assertThat(@Nullable java.io.File file) {
        return assertThat(file == null ? null : file.toPath());
    }

    public void hasName(String name) {
        check().that(getSubject().getFileName().toString())
                .named(getDisplaySubject()).isEqualTo(name);
    }

    public void exists() {
        if (!Files.exists(getSubject())) {
            fail("exists");
        }
    }

    public void doesNotExist() {
        if (Files.exists(getSubject())) {
            fail("does not exist");
        }
    }

    public void isFile() {
        if (!Files.isRegularFile(getSubject())) {
            fail("is a file");
        }
    }

    public void isDirectory() {
        if (!Files.isDirectory(getSubject())) {
            fail("is a directory");
        }
    }

    public void isExecutable() {
        if (!Files.isExecutable(getSubject())) {
            fail("is not executable");
        }
    }

    public void hasContents(byte[] expectedContents) throws IOException {
        exists();
        try {
            byte[] contents = Files.readAllBytes(getSubject());
            if (!Arrays.equals(contents, expectedContents)) {
                failWithBadResults(
                        "contains",
                        "byte[" + expectedContents.length + "]",
                        "is",
                        "byte[" + contents.length + "]");
            }
        } catch (IOException e) {
            failWithRawMessage("Unable to read %s", getSubject());
        }
    }

    public void hasContents(String... expectedContents) throws IOException {
        exists();
        try {
            List<String> contents = Files.readAllLines(getSubject());
            if (!Arrays.asList(expectedContents).equals(contents)) {
                failWithBadResults(
                        "contains",
                        Joiner.on('\n').join(expectedContents),
                        "is",
                        Joiner.on('\n').join(contents));
            }
        } catch (IOException e) {
            failWithRawMessage("Unable to read %s", getSubject());
        }
    }

    public void wasModifiedAt(@NonNull FileTime expectedTime) throws IOException {
        FileTime actualTime = Files.getLastModifiedTime(getSubject());
        if (!actualTime.equals(expectedTime)) {
            failWithBadResults(
                    "was last modified at", expectedTime, "was last modified at", actualTime);
        }
    }

    public void isNewerThan(@NonNull FileTime expectedTime) throws IOException {
        FileTime actualTime = Files.getLastModifiedTime(getSubject());
        if (actualTime.compareTo(expectedTime) <= 0) {
            failWithBadResults(
                    "was modified after", expectedTime, "was last modified at", actualTime);
        }
    }
}
