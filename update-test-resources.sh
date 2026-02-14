#!/bin/sh

rm -rf src/test/resources/*
examples/bld cl co jacoco
cp -f "examples/build/main/com/example/Examples.class" src/test/resources/
cp -f "examples/build/jacoco/jacoco.exec" src/test/resources/
