#!/bin/bash

java -jar lib/compile/org.jacoco.cli-*-nodeps.jar report 2> >(grep "^ [<-]") |
  cut -d' ' -f 2 |
  sed -e "/help/d" >"src/test/resources/jacoco-args.txt"
