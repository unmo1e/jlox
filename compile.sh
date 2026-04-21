#!/bin/bash

# generate Stmt and Expr files
javac -d out src/com/jlox/tool/*.java
java -cp out com.jlox.tool.GenerateAst ./src/com/jlox/lox

# compile jlox
javac -d out src/com/jlox/lox/*.java
