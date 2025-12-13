package server;

import java.io.*;
import java.net.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

/**
 * UDP Broadcast Server
 * Broadcasts messages to all connected clients
 * Supports both broadcast and multicast modes
 */
public class UDPBroadcastServer {
    private static final int DEFAULT_PORT = 5001;
    private static final int BUFFER_SIZE = 1024;
    private static final String MULTICAST_GROUP = "239.255.255.250";
    
    private final int port;
    private DatagramSocket socket;
    private InetAddress broadcastAddress;
    private volatile boolean running = true;
    private final Set<ClientInfo> connectedClients;
    private final ScheduledExecutorService scheduler;
    private final DateTimeFormatter timeFormatter;
    
    public UDPBroadcastServer(int port) {
        this.port = port;
        this.connectedClients = ConcurrentHashMap.newKeySet();
        this.scheduler = Executors.newScheduledThreadPool(2);
        this.timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    }
    
    /**
     * Client information holder
     */
    private static class ClientInfo {
        InetAddress address;
        int port;
        long lastSeen;
        String clientId;
        
        ClientInfo(InetAddress address, int port, String clientId) {
            this.address = address;
            this.port = port;
            this.clientId = clientId;
            this.lastSeen = System.currentTimeMillis();
        }
        
        void updateLastSeen() {
            this.lastSeen = System.currentTimeMillis();
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ClientInfo that = (ClientInfo) o;
            return port == that.port && address.equals(that.address);
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(address, port);
        }
        
        @Override
        public String toString() {
            return clientId + "@" + address.getHostAddress() + ":" + port;
        }
    }
    
    /**
     * Start the UDP server
     */
    public void start() {
        try {
            socket = new DatagramSocket(port);
            socket.setBroadcast(true);
            broadcastAddress = InetAddress.getByName("255.255.255.255");
            
            System.out.println("╔════════════════════════════════════════════╗");
            System.out.println("║       UDP Broadcast Server Started         ║");
            System.out.println("╠════════════════════════════════════════════╣");
            System.out.println("║ Port: " + port + "                                 ║");
            System.out.println("║ Broadcast: " + broadcastAddress.getHostAddress() + "               ║");
            System.out.println("║ Status: ACTIVE                             ║");
            System.out.println("╚════════════════════════════════════════════╝");
            
            // Start periodic broadcast scheduler
            startPeriodicBroadcast();
            
            // Start client cleanup scheduler
            startClientCleanup();
            
            // Main receive loop
            receiveLoop();
            
        } catch (SocketException e) {
            System.err.println("[ERROR] Socket error: " + e.getMessage());
        } catch (UnknownHostException e) {
            System.err.println("[ERROR] Unknown host: " + e.getMessage());
        }
    }
    
    /**
     * Main loop to receive messages from clients
     */
    private void receiveLoop() {
        byte[] buffer = new byte[BUFFER_SIZE];
        
        while (running) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                
                String message = new String(packet.getData(), 0, packet.getLength()).trim();
                InetAddress clientAddress = packet.getAddress();
                int clientPort = packet.getPort();
                
                processMessage(message, clientAddress, clientPort);
                
            } catch (SocketException e) {
                if (running) {
                    System.err.println("[ERROR] Socket error in receive: " + e.getMessage());
                }
            } catch (IOException e) {
                if (running) {
                    System.err.println("[ERROR] IO error: " + e.getMessage());
                }
            }
        }
    }
    
    /**
     * Process incoming message from client
     */
    private void processMessage(String message, InetAddress address, int port) {
        String[] parts = message.split("\\|");
        String cmd = parts[0].toUpperCase();
        
        System.out.println("[RECV] From " + address.getHostAddress() + ":" + port + " -> " + message);
        
        switch (cmd) {
            case "REGISTER":
                handleRegister(parts, address, port);
                break;
            case "UNREGISTER":
                handleUnregister(address, port);
                break;
            case "BROADCAST":
                handleBroadcastRequest(parts, address, port);
                break;
            case "PING":
                handlePing(address, port);
                break;
            case "LIST":
                handleListClients(address, port);
                break;
            case "HEARTBEAT":
                handleHeartbeat(address, port);
                break;
            default:
                sendToClient("ERROR|Unknown command: " + cmd, address, port);
        }
    }
    
    /**
     * Handle client registration
     */
    private void handleRegister(String[] parts, InetAddress address, int port) {
        String clientId = parts.length > 1 ? parts[1] : "Client-" + System.currentTimeMillis();
        ClientInfo client = new ClientInfo(address, port, clientId);
        
        if (connectedClients.add(client)) {
            System.out.println("[REG] New client registered: " + client);
            sendToClient("REG_OK|" + clientId + "|Welcome to UDP Broadcast Server", address, port);
            broadcastToAll("NOTIFY|New client joined: " + clientId, client);
        } else {
            // Update existing client
            connectedClients.stream()
                .filter(c -> c.equals(client))
                .findFirst()
                .ifPresent(ClientInfo::updateLastSeen);
            sendToClient("REG_OK|" + clientId + "|Already registered", address, port);
        }
    }
    
    /**
     * Handle client unregistration
     */
    private void handleUnregister(InetAddress address, int port) {
        ClientInfo client = findClient(address, port);
        if (client != null) {
            connectedClients.remove(client);
            System.out.println("[UNREG] Client unregistered: " + client);
            sendToClient("UNREG_OK|Goodbye!", address, port);
            broadcastToAll("NOTIFY|Client left: " + client.clientId, null);
        }
    }
    
    /**
     * Handle broadcast request from client
     */
    private void handleBroadcastRequest(String[] parts, InetAddress address, int port) {
        if (parts.length < 2) {
            sendToClient("ERROR|Usage: BROADCAST|message", address, port);
            return;
        }
        
        ClientInfo sender = findClient(address, port);
        if (sender == null) {
            sendToClient("ERROR|Please register first using REGISTER|clientId", address, port);
            return;
        }
        
        String broadcastMessage = parts[1];
        String fullMessage = "MSG|" + sender.clientId + "|" + broadcastMessage;
        
        System.out.println("[BCAST] " + sender.clientId + ": " + broadcastMessage);
        broadcastToAll(fullMessage, null);
    }
    
    /**
     * Handle ping request
     */
    private void handlePing(InetAddress address, int port) {
        ClientInfo client = findClient(address, port);
        if (client != null) {
            client.updateLastSeen();
        }
        sendToClient("PONG|" + System.currentTimeMillis(), address, port);
    }
    
    /**
     * Handle heartbeat from client
     */
    private void handleHeartbeat(InetAddress address, int port) {
        ClientInfo client = findClient(address, port);
        if (client != null) {
            client.updateLastSeen();
            sendToClient("HEARTBEAT_ACK|" + connectedClients.size(), address, port);
        }
    }
    
    /**
     * Handle list clients request
     */
    private void handleListClients(InetAddress address, int port) {
        StringBuilder sb = new StringBuilder("CLIENTS|");
        for (ClientInfo client : connectedClients) {
            sb.append(client.clientId).append(",");
        }
        sendToClient(sb.toString(), address, port);
    }
    
    /**
     * Find client by address and port
     */
    private ClientInfo findClient(InetAddress address, int port) {
        for (ClientInfo client : connectedClients) {
            if (client.address.equals(address) && client.port == port) {
                return client;
            }
        }
        return null;
    }
    
    /**
     * Send message to specific client
     */
    private void sendToClient(String message, InetAddress address, int port) {
        try {
            byte[] data = message.getBytes();
            DatagramPacket packet = new DatagramPacket(data, data.length, address, port);
            socket.send(packet);
        } catch (IOException e) {
            System.err.println("[ERROR] Failed to send to client: " + e.getMessage());
        }
    }
    
    /**
     * Broadcast message to all connected clients
     */
    private void broadcastToAll(String message, ClientInfo exclude) {
        byte[] data = message.getBytes();
        
        for (ClientInfo client : connectedClients) {
            if (exclude != null && client.equals(exclude)) {
                continue;
            }
            try {
                DatagramPacket packet = new DatagramPacket(
                    data, data.length, client.address, client.port);
                socket.send(packet);
            } catch (IOException e) {
                System.err.println("[ERROR] Failed to broadcast to " + client + ": " + e.getMessage());
            }
        }
    }
    
    /**
     * Start periodic broadcast of server status
     */
    private void startPeriodicBroadcast() {
        scheduler.scheduleAtFixedRate(() -> {
            if (!connectedClients.isEmpty()) {
                String timestamp = LocalDateTime.now().format(timeFormatter);
                String statusMessage = "STATUS|Server Time: " + timestamp + 
                                       "|Connected Clients: " + connectedClients.size();
                broadcastToAll(statusMessage, null);
                System.out.println("[BROADCAST] Status sent to " + connectedClients.size() + " clients");
            }
        }, 30, 30, TimeUnit.SECONDS);
    }
    
    /**
     * Start client cleanup task (remove inactive clients)
     */
    private void startClientCleanup() {
        scheduler.scheduleAtFixedRate(() -> {
            long now = System.currentTimeMillis();
            long timeout = 120000; // 2 minutes timeout
            
            connectedClients.removeIf(client -> {
                if (now - client.lastSeen > timeout) {
                    System.out.println("[CLEANUP] Removing inactive client: " + client);
                    return true;
                }
                return false;
            });
        }, 60, 60, TimeUnit.SECONDS);
    }
    
    /**
     * Stop the server
     */
    public void stop() {
        running = false;
        scheduler.shutdown();
        
        // Notify all clients
        broadcastToAll("SERVER_SHUTDOWN|Server is shutting down", null);
        
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
        }
        
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
        System.out.println("[SERVER] UDP Broadcast Server stopped");
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
                System.err.println("Invalid port. Using default: " + DEFAULT_PORT);
            }
        }
        
        UDPBroadcastServer server = new UDPBroadcastServer(port);
        
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n[SHUTDOWN] Shutting down server...");
            server.stop();
        }));
        
        server.start();
    }
}
