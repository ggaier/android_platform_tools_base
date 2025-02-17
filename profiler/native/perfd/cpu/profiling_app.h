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
#ifndef PERFD_CPU_PROFILING_APP_H_
#define PERFD_CPU_PROFILING_APP_H_

#include "proto/cpu.grpc.pb.h"

namespace profiler {

// Represents the metadata of a profiling.
//
// The name of this struct comes from the RPC name StartProfilingApp().
// However, through our code and public doc, trace/tracing, recording, and
// capture mean the same thing as profiling and they are (almost)
// interchangeable.
struct ProfilingApp {
  std::string app_pkg_name;
  // Absolute on-device path to the trace file. Activity manager or simpleperf
  // determines the path and populate the file with trace data.
  std::string trace_path;
  // The timestamp when the last start profiling request was processed
  // successfully.
  int64_t start_timestamp;
  // The timestamp when the profiling ends.
  // |end_timestamp| being -1 means the trace is in progress.
  int64_t end_timestamp;
  // The last start profiling requests processed successfully.
  profiler::proto::CpuProfilerConfiguration configuration;
  profiler::proto::TraceInitiationType initiation_type;
  int32_t trace_id;
  bool is_startup_profiling = false;
};

}  // namespace profiler

#endif  // PERFD_CPU_PROFILING_APP_H_
