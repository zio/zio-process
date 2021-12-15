#!/bin/bash
echo $$
sleep 0.2
./sample-child.sh &
./sample-child.sh &
sleep 30
echo -n "end: "
echo $$