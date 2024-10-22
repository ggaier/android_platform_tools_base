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
#include "perfd/cpu/internal_cpu_service.h"

#include "utils/log.h"
#include "utils/process_manager.h"

using grpc::ServerContext;
using grpc::Status;
using profiler::proto::CpuProfilerMode;
using profiler::proto::CpuProfilerType;
using profiler::proto::CpuTraceOperationRequest;
using profiler::proto::CpuTraceOperationResponse;
using profiler::proto::TraceInitiationType;

namespace profiler {

Status InternalCpuServiceImpl::SendTraceEvent(
    ServerContext* context, const CpuTraceOperationRequest* request,
    CpuTraceOperationResponse* response) {
  int pid = request->pid();
  std::cout << "CPU SendTraceEvent " << pid << " " << request->timestamp()
            << " " << request->detail_case();
  if (request->has_start()) {
    ProfilingApp* ongoing_capture = cache_.GetOngoingCapture(pid);
    if (ongoing_capture != nullptr) {
      response->set_start_operation_allowed(false);
      std::cout << " START request ignored" << std::endl;
      return Status::OK;
    }

    ProfilingApp capture;
    ProcessManager process_manager;

    capture.app_pkg_name = process_manager.GetCmdlineForPid(pid);
    capture.trace_path = request->start().arg_trace_path();
    capture.start_timestamp = request->timestamp();
    capture.end_timestamp = -1;
    capture.configuration.set_profiler_type(CpuProfilerType::ART);
    capture.configuration.set_profiler_mode(CpuProfilerMode::INSTRUMENTED);
    capture.initiation_type = TraceInitiationType::INITIATED_BY_API;
    int32_t trace_id = cache_.AddProfilingStart(pid, capture);
    response->set_start_operation_allowed(true);
    response->set_trace_id(trace_id);
    std::cout << " START " << request->start().method_name() << " "
              << request->start().method_signature() << " '"
              << request->start().arg_trace_path() << "'"
              << " trace_id=" << trace_id << std::endl;
  } else if (request->has_stop()) {
    const ProfilingApp* ongoing = cache_.GetOngoingCapture(pid);
    if (ongoing == nullptr) {
      Log::E("No running trace when Debug.stopMethodTracing() is called");
    } else if (ongoing->initiation_type !=
               TraceInitiationType::INITIATED_BY_API) {
      Log::E(
          "Debug.stopMethodTracing() is called but the running trace is not "
          "initiated by startMetghodTracing* APIs");
    } else if (ongoing->trace_id != request->stop().trace_id()) {
      Log::E(
          "Inconsistent Studio data when Debug.stopMethodTracing() is called");
    } else {
      cache_.AddProfilingStop(pid);
      cache_.AddTraceContent(request->pid(), request->stop().trace_id(),
                             request->stop().trace_content());
    }
    std::cout << " STOP trace_id=" << request->stop().trace_id()
              << " size=" << request->stop().trace_content().size()
              << std::endl;
  }
  return Status::OK;
}

}  // namespace profiler
