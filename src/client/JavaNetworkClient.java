package client;

import java.io.*;
import java.net.*;
import java.util.Scanner;
import java.util.concurrent.*;

/**
 * Java Network Client
 * Supports both TCP authentication and UDP broadcast communication
 */
public class JavaNetworkClient {
    // Default server settings
    private static final String DEFAULT_HOST = "127.0.0.1";
    private static final int DEFAULT_TCP_PORT = 5000;
    private static final int DEFAULT_UDP_PORT = 5001;

    // TCP connection
    private Socket tcpSocket;
    private BufferedReader tcpIn;
    private PrintWriter tcpOut;
    private boolean tcpConnected = false;
    private boolean tcpAuthenticated = false;

    // UDP connection
    private DatagramSocket udpSocket;
    private InetAddress serverAddress;
    private int udpServerPort;
    private boolean udpConnected = false;
    private volatile boolean udpListening = true;
    private ExecutorService udpListener;

    // Client info
    private String clientId;
    private String username;
    private String serverHost;
    private int tcpPort;
    private int udpPort;

    public JavaNetworkClient(String host, int tcpPort, int udpPort) {
        this.serverHost = host;
        this.tcpPort = tcpPort;
        this.udpPort = udpPort;
        this.clientId = "JavaClient-" + System.currentTimeMillis() % 10000;
        this.username = null;
    }

    /**
     * Connect to TCP Authentication Server
     */
    public boolean connectTCP() {
        try {
            tcpSocket = new Socket();
            tcpSocket.connect(new InetSocketAddress(serverHost, tcpPort), 5000);
            tcpSocket.setSoTimeout(30000);

            tcpIn = new BufferedReader(new InputStreamReader(tcpSocket.getInputStream()));
            tcpOut = new PrintWriter(tcpSocket.getOutputStream(), true);

            tcpConnected = true;
            System.out.println("[TCP] Connected to " + serverHost + ":" + tcpPort);

            // Read welcome messages
            String welcome = tcpIn.readLine();
            String ready = tcpIn.readLine();
            System.out.println("[TCP] Server: " + welcome);
            System.out.println("[TCP] Server: " + ready);

            return true;
        } catch (IOException e) {
            System.err.println("[TCP] Connection failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Authenticate with TCP server
     */
    public boolean authenticate(String username, String password) {
        if (!tcpConnected) {
            System.err.println("[TCP] Not connected to server");
            return false;
        }

        try {
            tcpOut.println("AUTH|" + username + "|" + password);
            String response = tcpIn.readLine();

            if (response != null && response.startsWith("AUTH_OK")) {
                System.out.println("[TCP] ✓ " + response.substring(8));
                tcpAuthenticated = true;
                this.username = username;
                return true;
            } else if (response != null && response.startsWith("AUTH_FAIL")) {
                String errorMsg = response.substring(10);
                System.err.println("[TCP] ✗ Authentication failed: " + errorMsg);
                if (errorMsg.contains("already connected")) {
                    System.out.println("[TCP] Please try with a different account.");
                }
                return false;
            } else {
                System.err.println("[TCP] Unexpected response: " + response);
                return false;
            }
        } catch (IOException e) {
            System.err.println("[TCP] Authentication error: " + e.getMessage());
            return false;
        }
    }

    /**
     * Send TCP command and get response
     */
    public String sendTCPCommand(String command) {
        if (!tcpConnected) {
            return "ERROR|Not connected";
        }

        try {
            tcpOut.println(command);
            return tcpIn.readLine();
        } catch (IOException e) {
            return "ERROR|" + e.getMessage();
        }
    }

    /**
     * Disconnect from TCP server
     */
    public void disconnectTCP() {
        if (tcpConnected) {
            try {
                tcpOut.println("QUIT");
                tcpIn.readLine(); // Read goodbye
                tcpIn.close();
                tcpOut.close();
                tcpSocket.close();
            } catch (IOException e) {
                // Ignore
            }
            tcpConnected = false;
            tcpAuthenticated = false;
            System.out.println("[TCP] Disconnected");
        }
    }

    /**
     * Connect to UDP Broadcast Server
     */
    public boolean connectUDP() {
        try {
            udpSocket = new DatagramSocket();
            udpSocket.setSoTimeout(1000); // 1 second timeout for receive
            serverAddress = InetAddress.getByName(serverHost);
            udpServerPort = udpPort;

            // Register with server using username if authenticated, otherwise use clientId
            String identifier = (username != null) ? username : clientId;
            System.out.println("[UDP] Registering as: " + identifier + " (username=" + username + ")");
            sendUDP("REGISTER|" + identifier);

            // Wait for registration confirmation
            String response = receiveUDP(5000);
            if (response != null && response.startsWith("REG_OK")) {
                udpConnected = true;
                System.out.println("[UDP] " + response);

                // Start background listener
                startUDPListener();
                return true;
            }

            System.err.println("[UDP] Registration failed");
            return false;
        } catch (SocketException e) {
            System.err.println("[UDP] Socket error: " + e.getMessage());
            return false;
        } catch (UnknownHostException e) {
            System.err.println("[UDP] Unknown host: " + e.getMessage());
            return false;
        }
    }

    /**
     * Start background UDP listener
     */
    private void startUDPListener() {
        udpListener = Executors.newSingleThreadExecutor();
        udpListener.execute(() -> {
            byte[] buffer = new byte[1024];

            while (udpListening && udpConnected) {
                try {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    udpSocket.receive(packet);

                    String message = new String(packet.getData(), 0, packet.getLength());
                    handleUDPMessage(message);
                } catch (SocketTimeoutException e) {
                    // Continue listening
                } catch (IOException e) {
                    if (udpListening) {
                        System.err.println("[UDP] Receive error: " + e.getMessage());
                    }
                }
            }
        });
    }

    /**
     * Handle incoming UDP message
     */
    private void handleUDPMessage(String message) {
        String[] parts = message.split("\\|");
        String type = parts[0];

        switch (type) {
            case "MSG":
                if (parts.length >= 3) {
                    System.out.println("\n[BROADCAST] " + parts[1] + ": " + parts[2]);
                }
                break;
            case "STATUS":
                System.out.println("\n[STATUS] " + message.substring(7));
                break;
            case "NOTIFY":
                System.out.println("\n[NOTIFY] " + parts[1]);
                break;
            case "PONG":
                System.out.println("[PONG] Response time: " +
                        (System.currentTimeMillis() - Long.parseLong(parts[1])) + "ms");
                break;
            case "CLIENTS":
                System.out.println("[CLIENTS] " + (parts.length > 1 ? parts[1] : "None"));
                break;
            case "SERVER_SHUTDOWN":
                System.out.println("\n[WARNING] Server is shutting down!");
                break;
            default:
                System.out.println("[UDP] " + message);
        }
        System.out.print("> ");
    }

    /**
     * Send UDP message to server
     */
    public void sendUDP(String message) {
        if (udpSocket == null)
            return;

        try {
            byte[] data = message.getBytes();
            DatagramPacket packet = new DatagramPacket(
                    data, data.length, serverAddress, udpServerPort);
            udpSocket.send(packet);
        } catch (IOException e) {
            System.err.println("[UDP] Send error: " + e.getMessage());
        }
    }

    /**
     * Receive UDP message with timeout
     */
    private String receiveUDP(int timeoutMs) {
        try {
            udpSocket.setSoTimeout(timeoutMs);
            byte[] buffer = new byte[1024];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            udpSocket.receive(packet);
            udpSocket.setSoTimeout(1000); // Reset to normal timeout
            return new String(packet.getData(), 0, packet.getLength());
        } catch (SocketTimeoutException e) {
            return null;
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Broadcast message via UDP
     */
    public void broadcast(String message) {
        if (!udpConnected) {
            System.err.println("[UDP] Not connected to broadcast server");
            return;
        }
        sendUDP("BROADCAST|" + message);
    }

    /**
     * Disconnect from UDP server
     */
    public void disconnectUDP() {
        if (udpConnected) {
            udpListening = false;
            sendUDP("UNREGISTER");

            if (udpListener != null) {
                udpListener.shutdown();
                try {
                    udpListener.awaitTermination(2, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    udpListener.shutdownNow();
                }
            }

            if (udpSocket != null && !udpSocket.isClosed()) {
                udpSocket.close();
            }
            udpConnected = false;
            System.out.println("[UDP] Disconnected");
        }
    }

    /**
     * Interactive client menu
     */
    public void runInteractive() {
        Scanner scanner = new Scanner(System.in);
        printMenu();

        while (true) {
            System.out.print("> ");
            String input = scanner.nextLine().trim();

            if (input.isEmpty())
                continue;

            String[] parts = input.split("\\s+", 2);
            String command = parts[0].toLowerCase();
            String args = parts.length > 1 ? parts[1] : "";

            try {
                switch (command) {
                    // Connection commands
                    case "tcp-connect":
                        connectTCP();
                        break;
                    case "tcp-disconnect":
                        disconnectTCP();
                        break;
                    case "udp-connect":
                        connectUDP();
                        break;
                    case "udp-disconnect":
                        disconnectUDP();
                        break;

                    // TCP commands
                    case "auth":
                        String[] creds = args.split("\\s+");
                        if (creds.length >= 2) {
                            authenticate(creds[0], creds[1]);
                        } else {
                            System.out.println("Usage: auth <username> <password>");
                        }
                        break;
                    case "register":
                        String[] regCreds = args.split("\\s+");
                        if (regCreds.length >= 2) {
                            System.out.println(sendTCPCommand("REGISTER|" + regCreds[0] + "|" + regCreds[1]));
                        } else {
                            System.out.println("Usage: register <username> <password>");
                        }
                        break;
                    case "tcp":
                        System.out.println(sendTCPCommand(args.toUpperCase()));
                        break;

                    // UDP commands
                    case "broadcast":
                    case "bc":
                        broadcast(args);
                        break;
                    case "ping":
                        sendUDP("PING");
                        break;
                    case "list":
                        sendUDP("LIST");
                        break;

                    // General commands
                    case "status":
                        printStatus();
                        break;
                    case "help":
                        printMenu();
                        break;
                    case "quit":
                    case "exit":
                        disconnectTCP();
                        disconnectUDP();
                        System.out.println("Goodbye!");
                        scanner.close();
                        return;

                    default:
                        System.out.println("Unknown command. Type 'help' for available commands.");
                }
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
            }
        }
    }

    /**
     * Print current connection status
     */
    private void printStatus() {
        System.out.println("╔════════════════════════════════════════╗");
        System.out.println("║           Connection Status            ║");
        System.out.println("╠════════════════════════════════════════╣");
        System.out.println("║ Client ID: " + padRight(clientId, 26) + " ║");
        System.out.println("║ Server: " + padRight(serverHost, 29) + " ║");
        System.out.println("╠════════════════════════════════════════╣");
        System.out.println("║ TCP Port: " + padRight(String.valueOf(tcpPort), 27) + " ║");
        System.out.println("║ TCP Connected: " + padRight(String.valueOf(tcpConnected), 22) + " ║");
        System.out.println("║ TCP Authenticated: " + padRight(String.valueOf(tcpAuthenticated), 18) + " ║");
        System.out.println("╠════════════════════════════════════════╣");
        System.out.println("║ UDP Port: " + padRight(String.valueOf(udpPort), 27) + " ║");
        System.out.println("║ UDP Connected: " + padRight(String.valueOf(udpConnected), 22) + " ║");
        System.out.println("╚════════════════════════════════════════╝");
    }

    private String padRight(String s, int n) {
        return String.format("%-" + n + "s", s);
    }

    /**
     * Print help menu
     */
    private void printMenu() {
        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════╗");
        System.out.println("║           Java Network Client - Commands             ║");
        System.out.println("╠══════════════════════════════════════════════════════╣");
        System.out.println("║ CONNECTION:                                          ║");
        System.out.println("║   tcp-connect      - Connect to TCP auth server      ║");
        System.out.println("║   tcp-disconnect   - Disconnect from TCP server      ║");
        System.out.println("║   udp-connect      - Connect to UDP broadcast server ║");
        System.out.println("║   udp-disconnect   - Disconnect from UDP server      ║");
        System.out.println("╠══════════════════════════════════════════════════════╣");
        System.out.println("║ TCP COMMANDS:                                        ║");
        System.out.println("║   auth <user> <pass>     - Authenticate with server  ║");
        System.out.println("║   register <user> <pass> - Register new account      ║");
        System.out.println("║   tcp <command>          - Send raw TCP command      ║");
        System.out.println("╠══════════════════════════════════════════════════════╣");
        System.out.println("║ UDP COMMANDS:                                        ║");
        System.out.println("║   broadcast <msg> / bc   - Broadcast message to all  ║");
        System.out.println("║   ping                   - Ping UDP server           ║");
        System.out.println("║   list                   - List connected clients    ║");
        System.out.println("╠══════════════════════════════════════════════════════╣");
        System.out.println("║ GENERAL:                                             ║");
        System.out.println("║   status   - Show connection status                  ║");
        System.out.println("║   help     - Show this help menu                     ║");
        System.out.println("║   quit     - Exit the client                         ║");
        System.out.println("╚══════════════════════════════════════════════════════╝");
        System.out.println();
    }

    /**
     * Main entry point
     */
    public static void main(String[] args) {
        String host = DEFAULT_HOST;
        int tcpPort = DEFAULT_TCP_PORT;
        int udpPort = DEFAULT_UDP_PORT;

        // Parse command line arguments
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-h":
                case "--host":
                    if (i + 1 < args.length)
                        host = args[++i];
                    break;
                case "-t":
                case "--tcp-port":
                    if (i + 1 < args.length)
                        tcpPort = Integer.parseInt(args[++i]);
                    break;
                case "-u":
                case "--udp-port":
                    if (i + 1 < args.length)
                        udpPort = Integer.parseInt(args[++i]);
                    break;
                case "--help":
                    System.out.println("Usage: java JavaNetworkClient [options]");
                    System.out.println("  -h, --host <host>      Server hostname (default: 127.0.0.1)");
                    System.out.println("  -t, --tcp-port <port>  TCP port (default: 5000)");
                    System.out.println("  -u, --udp-port <port>  UDP port (default: 5001)");
                    return;
            }
        }

        System.out.println("╔════════════════════════════════════════╗");
        System.out.println("║       Java Network Client v1.0         ║");
        System.out.println("╠════════════════════════════════════════╣");
        System.out.println("║ Server: " + padRightStatic(host, 29) + " ║");
        System.out.println("║ TCP Port: " + padRightStatic(String.valueOf(tcpPort), 27) + " ║");
        System.out.println("║ UDP Port: " + padRightStatic(String.valueOf(udpPort), 27) + " ║");
        System.out.println("╚════════════════════════════════════════╝");

        JavaNetworkClient client = new JavaNetworkClient(host, tcpPort, udpPort);
        client.runInteractive();
    }

    private static String padRightStatic(String s, int n) {
        return String.format("%-" + n + "s", s);
    }
}
