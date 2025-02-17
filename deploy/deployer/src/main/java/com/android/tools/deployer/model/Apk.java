/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.deployer.model;

import com.android.tools.deployer.ZipUtils;
import com.google.common.collect.ImmutableList;
import java.util.HashMap;
import java.util.List;

public class Apk {
    public final String name;
    public final String checksum;
    public final String path;
    public final String packageName;
    public final List<String> processes;
    public final List<String> targetPackages;
    // TODO: This should either be in the ApkEntry loosely connected to this Apk
    //       or we change the model and have a list of ApkEntry in the APK since
    //       we spend
    public final HashMap<String, ZipUtils.ZipEntry> zipEntries;

    private Apk(
            String name,
            String checksum,
            String path,
            String packageName,
            List<String> processes,
            List<String> targetPackages,
            HashMap<String, ZipUtils.ZipEntry> zipEntries) {
        this.name = name;
        this.checksum = checksum;
        this.path = path;
        this.packageName = packageName;
        this.processes = processes;
        this.targetPackages = targetPackages;
        this.zipEntries = zipEntries;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String name;
        private String checksum;
        private String path;
        private String packageName;
        private List<String> processNames;
        private List<String> targetPackages;
        private HashMap<String, ZipUtils.ZipEntry> zipEntries;

        public Builder() {
            this.name = "";
            this.checksum = "";
            this.path = "";
            this.packageName = "";
            this.processNames = null;
            this.targetPackages = null;
            this.zipEntries = null;
        }

        public Builder setName(String name) {
            this.name = name;
            return this;
        }

        public Builder setChecksum(String checksum) {
            this.checksum = checksum;
            return this;
        }

        public Builder setPath(String path) {
            this.path = path;
            return this;
        }

        public Builder setPackageName(String packageName) {
            this.packageName = packageName;
            return this;
        }

        public Builder setProcessNames(List<String> processNames) {
            this.processNames = processNames;
            return this;
        }

        public Builder setTargetPackages(List<String> targetPackages) {
            this.targetPackages = targetPackages;
            return this;
        }

        public Builder setZipEntries(HashMap<String, ZipUtils.ZipEntry> zipEntries) {
            this.zipEntries = zipEntries;
            return this;
        }

        public Apk build() {
            processNames = processNames == null ? ImmutableList.of() : processNames;
            targetPackages = targetPackages == null ? ImmutableList.of() : targetPackages;
            zipEntries = zipEntries == null ? new HashMap<>() : zipEntries;
            return new Apk(
                    name, checksum, path, packageName, processNames, targetPackages, zipEntries);
        }
    }
}
