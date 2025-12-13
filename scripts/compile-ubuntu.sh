#!/bin/bash

# Ubuntu Compilation Script for Network Project
# Run with: chmod +x scripts/compile-ubuntu.sh && ./scripts/compile-ubuntu.sh

set -e

echo "=========================================="
echo "  Network Project - Ubuntu Compiler"
echo "=========================================="
echo ""

# Check for Java
if ! command -v javac &> /dev/null; then
    echo "[ERROR] Java compiler (javac) not found!"
    echo "Install with: sudo apt install openjdk-17-jdk"
    exit 1
fi

# Check for GCC
if ! command -v gcc &> /dev/null; then
    echo "[ERROR] GCC compiler not found!"
    echo "Install with: sudo apt install build-essential"
    exit 1
fi

# Show versions
echo "[INFO] Java version:"
java -version 2>&1 | head -1
echo ""
echo "[INFO] GCC version:"
gcc --version | head -1
echo ""

# Create bin directory
echo "[STEP 1] Creating bin directory..."
mkdir -p bin

# Compile Java
echo "[STEP 2] Compiling Java files..."
javac -d bin src/server/*.java src/client/*.java
echo "         Java compilation successful!"

# Compile C
echo "[STEP 3] Compiling C client..."
gcc -Wall -O2 -o bin/network_client src/c_client/network_client.c -lpthread
echo "         C compilation successful!"

# Set executable permissions
chmod +x bin/network_client

echo ""
echo "=========================================="
echo "  Compilation Complete!"
echo "=========================================="
echo ""
echo "To start the server:"
echo "  java -cp bin server.CombinedServer"
echo ""
echo "To run Java client:"
echo "  java -cp bin client.JavaNetworkClient"
echo ""
echo "To run C client:"
echo "  ./bin/network_client"
echo ""
