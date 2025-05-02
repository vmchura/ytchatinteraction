#!/bin/bash
# Local development environment launcher script

# Run the Play application with local config
sbt "run -Dconfig.file=conf/local.conf"
