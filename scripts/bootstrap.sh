#!/usr/bin/env bash

readonly SCRIPT_NAME=$(basename ${0})
readonly SCRIPT_DIRECTORY=$(dirname ${0})

${SCRIPT_DIRECTORY}/download_opennlp_models.sh

exit 0
