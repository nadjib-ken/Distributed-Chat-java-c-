#!/bin/bash

# Compilation script for Network Project
# Works on Linux and macOS

echo "╔════════════════════════════════════════════╗"
echo "║    Network Project Compilation Script      ║"
echo "╚════════════════════════════════════════════╝"
echo ""

# Create output directories
mkdir -p bin

# Compile Java
echo "[1/2] Compiling Java sources..."
javac -d bin src/server/*.java src/client/*.java

if [ $? -eq 0 ]; then
    echo "      ✓ Java compilation successful"
else
    echo "      ✗ Java compilation failed"
    exit 1
fi

# Compile C
echo "[2/2] Compiling C client..."
gcc -Wall -Wextra -O2 -o bin/network_client src/c_client/network_client.c -lpthread

if [ $? -eq 0 ]; then
    echo "      ✓ C compilation successful"
else
    echo "      ✗ C compilation failed"
    exit 1
fi

echo ""
echo "╔════════════════════════════════════════════╗"
echo "║         Compilation Complete!              ║"
echo "╠════════════════════════════════════════════╣"
echo "║ Java classes: bin/                         ║"
echo "║ C executable: bin/network_client           ║"
echo "╚════════════════════════════════════════════╝"
echo ""
echo "Run servers with:"
echo "  java -cp bin server.CombinedServer"
echo ""
echo "Run clients with:"
echo "  java -cp bin client.JavaNetworkClient"
echo "  ./bin/network_client"
