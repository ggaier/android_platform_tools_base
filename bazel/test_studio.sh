#!/bin/bash -x
# Invoked by Android Build Launchcontrol for continuous builds.

# Expected arguments:
readonly out_dir="$1"
readonly dist_dir="$2"
readonly build_number="$3"

readonly script_dir="$(dirname "$0")"

build_tag_filters=-no_linux
test_tag_filters=-no_linux,-no_test_linux,-qa_sanity,-qa_fast,-qa_unreliable

# If the build number starts with a 'P', this is a pre-submit builder.
if [[ "${build_number:0:1}" == "P" ]]; then
  test_tag_filters="${test_tag_filters},-no_psq"
  config_options="--config=presubmit --config=remote"
else
  config_options="--config=postsubmit --config=remote"
fi

# Conditionally add --auth_credentials option for BYOB machines.
if [[ -r "${HOME}/.android-studio-alphasource.json" ]]; then
  config_options="${config_options} --auth_credentials=${HOME}/.android-studio-alphasource.json"
fi

# Grab the location of the command_log file for bazel daemon so we can search it later.
readonly command_log="$("${script_dir}"/bazel info ${config_options} command_log)"

# Run Bazel
"${script_dir}/bazel" \
  --max_idle_secs=60 \
  test \
  ${config_options} \
  --build_tag_filters=${build_tag_filters} \
  --test_tag_filters=${test_tag_filters} \
  -- \
  $(< "${script_dir}/targets") \
  //tools/base/bazel/foundry:test

readonly bazel_status=$?

if [[ -d "${dist_dir}" ]]; then
  # Grab the upsalite_id from the stdout of the bazel command.  This is captured in command.log
  readonly upsalite_id="$(sed -n 's/\r$//;s/^.* invocation_id: //p' "${command_log}")"
  echo "<meta http-equiv=\"refresh\" content=\"0; URL='https://source.cloud.google.com/results/invocations/${upsalite_id}'\" />" > "${dist_dir}"/upsalite_test_results.html

  # on AB/ATP, put JUnit XML in place for junit-xml-forwarding
  readonly testlogs_dir="$("${script_dir}/bazel" info bazel-testlogs ${config_options})"
  mkdir "${dist_dir}"/bazel-testlogs
  (cd "${testlogs_dir}" && zip -R "${dist_dir}"/bazel-testlogs/xml_files.zip "*.xml")
fi

exit $bazel_status
