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

#include "tools/base/deploy/installer/workspace.h"

#include <fcntl.h>

#include "tools/base/deploy/common/utils.h"

namespace deploy {

namespace {
constexpr int kDirectoryMode = (S_IRWXG | S_IRWXU | S_IRWXO);
}

Workspace::Workspace(const std::string& executable_path, Executor& executor)
    : executor_(executor), output_pipe_(dup(STDOUT_FILENO)) {
  base_ = RetrieveBase(executable_path) + "/";

  // Create all directory that may be used.
  tmp_ = base_ + "/tmp/";
  mkdir(tmp_.c_str(), kDirectoryMode);

  // Close all file descriptor which could potentially mess up with
  // our protobuffer output and install a data sink instead.
  close(STDERR_FILENO);
  close(STDOUT_FILENO);
  open("/dev/null", 0);
  open("/dev/null", 0);
}

std::string Workspace::RetrieveBase(const std::string& path) noexcept {
  // Retrieves the base folder which is expected to be ".studio" somewhere in
  // the path.e.g: /data/local/tmp/.studio/bin base is /data/local/tmp/.studio.
  char* directory_cursor = const_cast<char*>(path.c_str());
  // Search for ".studio" folder.
  while (directory_cursor[0] != '/' || directory_cursor[1] != 0) {
    directory_cursor = dirname(directory_cursor);
    if (!strcmp(kBasename, basename(directory_cursor))) {
      return directory_cursor;
    }
  }
  std::cerr << "Unable to find '" << kBasename << "' base folder in '" << path
            << "'" << std::endl;
  return "";
}

void Workspace::SendResponse() noexcept {
  std::unique_ptr<std::vector<Event>> events = ConsumeEvents();
  for (Event& event : *events) {
    ConvertEventToProtoEvent(event, response_.add_events());
  }
  std::string responseString;
  response_.SerializeToString(&responseString);
  output_pipe_.Write(responseString);
}

}  // namespace deploy