package server;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * TCP Authentication Server
 * Handles client authentication using username/password
 * Supports multiple concurrent connections
 */
public class TCPAuthServer {
    private static final int DEFAULT_PORT = 5000;
    private final int port;
    private ServerSocket serverSocket;
    private volatile boolean running = true;
    private final ExecutorService threadPool;
    private final Map<String, String> userCredentials;
    private final Set<String> authenticatedUsers;

    public TCPAuthServer(int port) {
        this.port = port;
        this.threadPool = Executors.newCachedThreadPool();
        this.userCredentials = new ConcurrentHashMap<>();
        this.authenticatedUsers = ConcurrentHashMap.newKeySet();
        initializeUsers();
    }

    /**
     * Initialize default user credentials
     * In production, this would load from a database
     */
    private void initializeUsers() {
        // Add default users (password is hashed)
        userCredentials.put("admin", hashPassword("admin123"));
        userCredentials.put("user1", hashPassword("password1"));
        userCredentials.put("user2", hashPassword("password2"));
        userCredentials.put("guest", hashPassword("guest"));
        System.out.println("[AUTH] Initialized " + userCredentials.size() + " user accounts");
    }

    /**
     * Hash password using SHA-256
     */
    private String hashPassword(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(password.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1)
                    hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            return password; // Fallback to plain text
        }
    }

    /**
     * Start the TCP server
     */
    public void start() {
        try {
            serverSocket = new ServerSocket(port);
            System.out.println("╔════════════════════════════════════════════╗");
            System.out.println("║     TCP Authentication Server Started      ║");
            System.out.println("╠════════════════════════════════════════════╣");
            System.out.println("║ Port: " + port + "                                 ║");
            System.out.println("║ Status: LISTENING                          ║");
            System.out.println("╚════════════════════════════════════════════╝");

            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    System.out.println("[CONN] New connection from: " +
                            clientSocket.getInetAddress().getHostAddress() + ":" +
                            clientSocket.getPort());
                    threadPool.execute(new ClientHandler(clientSocket));
                } catch (SocketException e) {
                    if (running) {
                        System.err.println("[ERROR] Socket error: " + e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("[ERROR] Failed to start server: " + e.getMessage());
        }
    }

    /**
     * Stop the server gracefully
     */
    public void stop() {
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
            threadPool.shutdown();
            if (!threadPool.awaitTermination(5, TimeUnit.SECONDS)) {
                threadPool.shutdownNow();
            }
            System.out.println("[SERVER] TCP Authentication Server stopped");
        } catch (IOException | InterruptedException e) {
            System.err.println("[ERROR] Error stopping server: " + e.getMessage());
        }
    }

    /**
     * Inner class to handle individual client connections
     */
    private class ClientHandler implements Runnable {
        private final Socket socket;
        private BufferedReader in;
        private PrintWriter out;
        private String currentUser = null;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                socket.setSoTimeout(300000); // 5 minute timeout
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                // Send welcome message
                out.println("WELCOME|TCP Authentication Server v1.0");
                out.println("READY|Please authenticate using AUTH|username|password");

                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    String response = processCommand(inputLine);
                    out.println(response);

                    if (response.startsWith("GOODBYE")) {
                        break;
                    }
                }
            } catch (SocketTimeoutException e) {
                System.out.println("[TIMEOUT] Client timed out: " + socket.getInetAddress());
            } catch (IOException e) {
                System.out.println("[DISCONNECT] Client disconnected: " + socket.getInetAddress());
            } finally {
                cleanup();
            }
        }

        /**
         * Process incoming commands from client
         */
        private String processCommand(String command) {
            if (command == null || command.trim().isEmpty()) {
                return "ERROR|Empty command";
            }

            String[] parts = command.split("\\|");
            String cmd = parts[0].toUpperCase();

            System.out.println("[CMD] From " + socket.getInetAddress() + ": " + cmd);

            switch (cmd) {
                case "AUTH":
                    return handleAuth(parts);
                case "REGISTER":
                    return handleRegister(parts);
                case "STATUS":
                    return handleStatus();
                case "LIST":
                    return handleListUsers();
                case "PING":
                    return "PONG|" + System.currentTimeMillis();
                case "QUIT":
                case "EXIT":
                    return handleQuit();
                case "HELP":
                    return getHelp();
                default:
                    return "ERROR|Unknown command. Use HELP for available commands";
            }
        }

        /**
         * Handle authentication request
         */
        private String handleAuth(String[] parts) {
            if (parts.length < 3) {
                return "AUTH_FAIL|Usage: AUTH|username|password";
            }

            String username = parts[1];
            String password = parts[2];
            String hashedPassword = hashPassword(password);

            // Check if user is already connected
            if (authenticatedUsers.contains(username)) {
                System.out.println("[AUTH] User '" + username + "' is already connected");
                return "AUTH_FAIL|ERROR: This account is already connected. Please use another account.";
            }

            if (userCredentials.containsKey(username) &&
                    userCredentials.get(username).equals(hashedPassword)) {
                currentUser = username;
                authenticatedUsers.add(username);
                System.out.println("[AUTH] User '" + username + "' authenticated successfully");
                return "AUTH_OK|Welcome " + username + "! You are now authenticated.";
            } else {
                System.out.println("[AUTH] Failed authentication attempt for '" + username + "'");
                return "AUTH_FAIL|Invalid username or password";
            }
        }

        /**
         * Handle user registration
         */
        private String handleRegister(String[] parts) {
            if (parts.length < 3) {
                return "REG_FAIL|Usage: REGISTER|username|password";
            }

            String username = parts[1];
            String password = parts[2];

            if (username.length() < 3 || password.length() < 4) {
                return "REG_FAIL|Username must be 3+ chars, password 4+ chars";
            }

            if (userCredentials.containsKey(username)) {
                return "REG_FAIL|Username already exists";
            }

            userCredentials.put(username, hashPassword(password));
            System.out.println("[REG] New user registered: " + username);
            return "REG_OK|User '" + username + "' registered successfully. Please authenticate.";
        }

        /**
         * Handle status request
         */
        private String handleStatus() {
            if (currentUser != null) {
                return "STATUS|Authenticated as: " + currentUser +
                        "|Online users: " + authenticatedUsers.size();
            } else {
                return "STATUS|Not authenticated|Please use AUTH command";
            }
        }

        /**
         * Handle list users request (requires authentication)
         */
        private String handleListUsers() {
            if (currentUser == null) {
                return "ERROR|Authentication required for this command";
            }
            return "USERS|" + String.join(",", authenticatedUsers);
        }

        /**
         * Handle quit command
         */
        private String handleQuit() {
            if (currentUser != null) {
                authenticatedUsers.remove(currentUser);
            }
            return "GOODBYE|Thank you for using TCP Auth Server";
        }

        /**
         * Get help message
         */
        private String getHelp() {
            return "HELP|Commands: AUTH|user|pass, REGISTER|user|pass, STATUS, LIST, PING, QUIT, HELP";
        }

        /**
         * Clean up resources
         */
        private void cleanup() {
            try {
                if (currentUser != null) {
                    authenticatedUsers.remove(currentUser);
                    System.out.println("[LOGOUT] User '" + currentUser + "' logged out");
                }
                if (in != null)
                    in.close();
                if (out != null)
                    out.close();
                if (socket != null && !socket.isClosed())
                    socket.close();
            } catch (IOException e) {
                System.err.println("[ERROR] Cleanup error: " + e.getMessage());
            }
        }
    }

    /**
     * Main entry point
     */
    public static void main(String[] args) {
        int port = DEFAULT_PORT;

        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port number. Using default: " + DEFAULT_PORT);
            }
        }

        TCPAuthServer server = new TCPAuthServer(port);

        // Add shutdown hook for graceful shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n[SHUTDOWN] Shutting down server...");
            server.stop();
        }));

        server.start();
    }
}
