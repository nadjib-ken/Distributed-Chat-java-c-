# Makefile for Network Project
# Supports both Java and C compilation

# Java settings
JAVAC = javac
JAVA = java
JAVA_SRC_DIR = src
JAVA_OUT_DIR = bin

# C settings
CC = gcc
CFLAGS = -Wall -Wextra -O2
C_SRC_DIR = src/c_client
C_OUT_DIR = bin

# Detect OS for C compilation
ifeq ($(OS),Windows_NT)
    C_LIBS = -lws2_32
    C_EXE = $(C_OUT_DIR)/network_client.exe
else
    C_LIBS = -lpthread
    C_EXE = $(C_OUT_DIR)/network_client
endif

# Java source files
JAVA_SOURCES = $(wildcard $(JAVA_SRC_DIR)/server/*.java) $(wildcard $(JAVA_SRC_DIR)/client/*.java)

# Targets
.PHONY: all java c clean run-tcp-server run-udp-server run-combined-server run-java-client run-c-client help

all: java c

# Create output directory
$(JAVA_OUT_DIR):
	mkdir -p $(JAVA_OUT_DIR)

$(C_OUT_DIR):
	mkdir -p $(C_OUT_DIR)

# Compile Java
java: $(JAVA_OUT_DIR)
	@echo "Compiling Java sources..."
	$(JAVAC) -d $(JAVA_OUT_DIR) $(JAVA_SOURCES)
	@echo "Java compilation complete!"

# Compile C
c: $(C_OUT_DIR)
	@echo "Compiling C client..."
	$(CC) $(CFLAGS) -o $(C_EXE) $(C_SRC_DIR)/network_client.c $(C_LIBS)
	@echo "C compilation complete!"

# Run servers
run-tcp-server: java
	@echo "Starting TCP Authentication Server..."
	$(JAVA) -cp $(JAVA_OUT_DIR) server.TCPAuthServer

run-udp-server: java
	@echo "Starting UDP Broadcast Server..."
	$(JAVA) -cp $(JAVA_OUT_DIR) server.UDPBroadcastServer

run-combined-server: java
	@echo "Starting Combined Server (TCP + UDP)..."
	$(JAVA) -cp $(JAVA_OUT_DIR) server.CombinedServer

# Run clients
run-java-client: java
	@echo "Starting Java Client..."
	$(JAVA) -cp $(JAVA_OUT_DIR) client.JavaNetworkClient

run-c-client: c
	@echo "Starting C Client..."
	./$(C_EXE)

# Clean
clean:
	rm -rf $(JAVA_OUT_DIR)
	rm -f $(C_EXE)
	@echo "Cleaned build artifacts"

# Help
help:
	@echo "Available targets:"
	@echo "  all                 - Compile both Java and C"
	@echo "  java                - Compile Java sources"
	@echo "  c                   - Compile C client"
	@echo "  run-tcp-server      - Run TCP Authentication Server"
	@echo "  run-udp-server      - Run UDP Broadcast Server"
	@echo "  run-combined-server - Run both servers together"
	@echo "  run-java-client     - Run Java client"
	@echo "  run-c-client        - Run C client"
	@echo "  clean               - Remove compiled files"
	@echo "  help                - Show this message"
