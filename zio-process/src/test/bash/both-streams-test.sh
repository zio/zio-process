#!/bin/bash

echoerr() { echo "$@" 1>&2; }

echo "stdout1"
echoerr "stderr1"

echo "stdout2"
echoerr "stderr2"