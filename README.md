# Network Project - TCP Authentication & UDP Broadcast

A complete networking implementation featuring TCP-based authentication and UDP-based broadcast messaging, with clients in both Java and C.

## Project Structure

\`\`\`
├── src/
│   ├── server/
│   │   ├── TCPAuthServer.java      # TCP authentication server
│   │   ├── UDPBroadcastServer.java # UDP broadcast server
│   │   └── CombinedServer.java     # Launcher for both servers
│   ├── client/
│   │   └── JavaNetworkClient.java  # Java client implementation
│   └── c_client/
│       └── network_client.c        # C client implementation
├── scripts/
│   ├── compile.sh                  # Linux/macOS compilation script
│   └── compile.bat                 # Windows compilation script
├── Makefile                        # Build automation
└── README.md                       # This file
\`\`\`

## Features

### TCP Authentication Server (Port 5000)
- User authentication with SHA-256 password hashing
- User registration
- Multi-threaded connection handling
- Session management
- Command-based protocol

### UDP Broadcast Server (Port 5001)
- Client registration and tracking
- Message broadcasting to all clients
- Periodic status broadcasts
- Heartbeat monitoring
- Automatic client cleanup

### Clients (Java & C)
- Interactive command-line interface
- TCP and UDP connection support
- Real-time message receiving
- Cross-platform compatibility

---

## Ubuntu Setup Instructions

### Step 1: Install Prerequisites

\`\`\`bash
# Update package lists
sudo apt update

# Install Java JDK (OpenJDK 17 recommended)
sudo apt install openjdk-17-jdk -y

# Install GCC compiler and build tools
sudo apt install build-essential -y

# Verify installations
java -version
javac -version
gcc --version
\`\`\`

### Step 2: Compile the Code

\`\`\`bash
# Create output directory
mkdir -p bin

# Compile Java servers and client
javac -d bin src/server/*.java src/client/*.java

# Compile C client
gcc -Wall -O2 -o bin/network_client src/c_client/network_client.c -lpthread
\`\`\`

### Step 3: Test Locally (Single Computer)

**Terminal 1 - Start Server:**
\`\`\`bash
java -cp bin server.CombinedServer
\`\`\`

**Terminal 2 - Run Java Client:**
\`\`\`bash
java -cp bin client.JavaNetworkClient
\`\`\`

**Terminal 3 - Run C Client:**
\`\`\`bash
./bin/network_client
\`\`\`

### Step 4: Test Commands in Client

\`\`\`
> tcp-connect
[TCP] Connected to 127.0.0.1:5000

> auth admin admin123
[TCP] Auth response: AUTH_OK|Welcome admin!

> udp-connect
[UDP] REG_OK|JavaClient-1234|Welcome to UDP Broadcast Server

> broadcast Hello everyone!

> ping

> list

> status

> quit
\`\`\`

---

## Configure Ubuntu as Server (Multi-Computer Setup)

### Step 1: Find Your Server IP Address

\`\`\`bash
# Method 1: Using hostname
hostname -I

# Method 2: Using ip command
ip addr show | grep "inet " | grep -v 127.0.0.1

# Method 3: Using ifconfig (may need net-tools)
sudo apt install net-tools -y
ifconfig | grep "inet " | grep -v 127.0.0.1
\`\`\`

Example output: `192.168.1.105`

### Step 2: Configure Firewall (UFW)

\`\`\`bash
# Check if UFW is active
sudo ufw status

# If inactive, enable it
sudo ufw enable

# Allow TCP port 5000 for authentication
sudo ufw allow 5000/tcp

# Allow UDP port 5001 for broadcast
sudo ufw allow 5001/udp

# Verify rules
sudo ufw status numbered
\`\`\`

### Step 3: Start Server (Binds to All Interfaces)

\`\`\`bash
java -cp bin server.CombinedServer
\`\`\`

The server will now accept connections from any computer on your network.

---

## Connect from Second Computer

### Option A: Second Computer is Ubuntu/Linux

Copy the compiled files or compile on that machine:

\`\`\`bash
# If files are copied, run:
java -cp bin client.JavaNetworkClient -h 192.168.1.105

# Or C client:
./bin/network_client -h 192.168.1.105
\`\`\`

Replace `192.168.1.105` with your server's actual IP.

### Option B: Second Computer is Windows

Transfer the compiled Java files and run:

\`\`\`cmd
java -cp bin client.JavaNetworkClient -h 192.168.1.105
\`\`\`

### Option C: Second Computer is macOS

\`\`\`bash
java -cp bin client.JavaNetworkClient -h 192.168.1.105
\`\`\`

---

## Complete Testing Walkthrough

### Test 1: TCP Authentication (Local)

\`\`\`
Terminal 1 (Server):
$ java -cp bin server.CombinedServer

Terminal 2 (Client):
$ java -cp bin client.JavaNetworkClient
> tcp-connect
> auth admin admin123
> tcp STATUS
> tcp LIST
> tcp PING
> tcp-disconnect
\`\`\`

### Test 2: UDP Broadcasting (Local - Two Clients)

\`\`\`
Terminal 1 (Server):
$ java -cp bin server.CombinedServer

Terminal 2 (Java Client):
$ java -cp bin client.JavaNetworkClient
> udp-connect

Terminal 3 (C Client):
$ ./bin/network_client
> udp-connect

Now in Terminal 2:
> broadcast Hello from Java!

You should see "Hello from Java!" appear in Terminal 3.

In Terminal 3:
> broadcast Hello from C!

You should see "Hello from C!" appear in Terminal 2.
\`\`\`

### Test 3: Multi-Computer Test

\`\`\`
Computer A (Server - IP: 192.168.1.105):
$ java -cp bin server.CombinedServer

Computer B (Client):
$ java -cp bin client.JavaNetworkClient -h 192.168.1.105
> tcp-connect
> auth admin admin123
> udp-connect
> broadcast Hello from Computer B!
> quit
\`\`\`

---

## Command Reference

### Client Commands

| Command | Description |
|---------|-------------|
| `tcp-connect` | Connect to TCP authentication server |
| `tcp-disconnect` | Disconnect from TCP server |
| `udp-connect` | Connect to UDP broadcast server |
| `udp-disconnect` | Disconnect from UDP server |
| `auth <user> <pass>` | Authenticate with TCP server |
| `register <user> <pass>` | Register new user account |
| `tcp <command>` | Send raw TCP command |
| `broadcast <msg>` / `bc <msg>` | Broadcast message via UDP |
| `ping` | Ping UDP server |
| `list` | List connected UDP clients |
| `status` | Show connection status |
| `help` | Show help menu |
| `quit` | Exit client |

### Default Credentials

| Username | Password |
|----------|----------|
| admin | admin123 |
| user1 | password1 |
| user2 | password2 |
| guest | guest |

---

## Troubleshooting

### "Connection refused" Error
\`\`\`bash
# Ensure server is running
ps aux | grep java

# Check if ports are listening
sudo netstat -tlnp | grep 5000
sudo ss -tlnp | grep 5000
\`\`\`

### "Address already in use" Error
\`\`\`bash
# Find process using the port
sudo lsof -i :5000
sudo lsof -i :5001

# Kill the process
sudo kill -9 <PID>
\`\`\`

### Can't Connect from Other Computer
\`\`\`bash
# Test connectivity from client machine
ping 192.168.1.105
telnet 192.168.1.105 5000

# Verify firewall on server
sudo ufw status
\`\`\`

### Java Not Found
\`\`\`bash
sudo apt install openjdk-17-jdk -y
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export PATH=$JAVA_HOME/bin:$PATH
\`\`\`

### GCC Compilation Errors
\`\`\`bash
sudo apt install build-essential -y
\`\`\`

---

## Quick Reference Card

\`\`\`bash
# ===== COMPILATION =====
mkdir -p bin
javac -d bin src/server/*.java src/client/*.java
gcc -Wall -O2 -o bin/network_client src/c_client/network_client.c -lpthread

# ===== RUN SERVER =====
java -cp bin server.CombinedServer

# ===== RUN CLIENTS (LOCAL) =====
java -cp bin client.JavaNetworkClient
./bin/network_client

# ===== RUN CLIENTS (REMOTE) =====
java -cp bin client.JavaNetworkClient -h <SERVER_IP>
./bin/network_client -h <SERVER_IP>

# ===== FIREWALL SETUP =====
sudo ufw allow 5000/tcp
sudo ufw allow 5001/udp

# ===== FIND YOUR IP =====
hostname -I
