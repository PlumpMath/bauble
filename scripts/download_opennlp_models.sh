#!/usr/bin/env bash

readonly SCRIPT_NAME=$(basename ${0})
readonly SCRIPT_DIRECTORY=$(dirname ${0})

readonly MODELS_DIRECTORY_PATH="${SCRIPT_DIRECTORY}/../lib/opennlp/models"
readonly HOST="http://maven.tamingtext.com"
readonly HOST_MODELS_DIRECTORY_PATH="/opennlp-models/models-1.5"
readonly HOST_MODELS_URL="${HOST}${HOST_MODELS_DIRECTORY_PATH}"

if ! [[ -d ${MODELS_DIRECTORY_PATH} ]]; then
  mkdir -p ${MODELS_DIRECTORY_PATH}
fi

if ! [[ -f ${MODELS_DIRECTORY_PATH}/english-detokenizer.xml ]]; then
  echo "Downloading english-detokenizer.xml"
  curl -L \
       -s \
       -o ${MODELS_DIRECTORY_PATH}/english-detokenizer.xml \
       https://raw.githubusercontent.com/dakrone/clojure-opennlp/master/models/english-detokenizer.xml
fi

for model in              \
  en-sent.bin             \
  en-token.bin            \
  en-pos-maxent.bin       \
  en-chunker.bin          \
  en-ner-date.bin         \
  en-ner-location         \
  en-ner-money            \
  en-ner-organization     \
  en-ner-percentage       \
  en-ner-person           \
  en-ner-time
do
  if ! [[ -f ${MODELS_DIRECTORY_PATH}/${model} ]]; then
    EN_BIN=${model}
    EN_BIN_URL=${HOST_MODELS_URL}/${EN_BIN}
    echo "Downloading ${EN_BIN_URL}"
    curl -L \
         -s \
         -o ${MODELS_DIRECTORY_PATH}/${EN_BIN} \
         ${EN_BIN_URL}
  fi
done

exit 0
