#!/bin/bash
cd $HOME/Storage/Code/cloud-launch && git checkout -q "$2" && claude ${3:+--effort $3} --cloud "$(cat "$1")"
sleep 300
