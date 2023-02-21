#!/bin/bash
echo $$
./sample-child.sh &
./sample-child.sh &
sleep 30
echo -n "end: "
echo $$