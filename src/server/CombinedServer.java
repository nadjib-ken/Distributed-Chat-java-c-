package server;

/**
 * Combined Server Launcher
 * Starts both TCP Authentication and UDP Broadcast servers
 */
public class CombinedServer {
    private static final int TCP_PORT = 5000;
    private static final int UDP_PORT = 5001;
    
    public static void main(String[] args) {
        int tcpPort = TCP_PORT;
        int udpPort = UDP_PORT;
        
        // Parse command line arguments
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--tcp-port":
                    if (i + 1 < args.length) {
                        tcpPort = Integer.parseInt(args[++i]);
                    }
                    break;
                case "--udp-port":
                    if (i + 1 < args.length) {
                        udpPort = Integer.parseInt(args[++i]);
                    }
                    break;
                case "--help":
                    printHelp();
                    return;
            }
        }
        
        System.out.println("╔════════════════════════════════════════════════════╗");
        System.out.println("║         Combined Network Server Launcher           ║");
        System.out.println("╠════════════════════════════════════════════════════╣");
        System.out.println("║ TCP Authentication Port: " + tcpPort + "                    ║");
        System.out.println("║ UDP Broadcast Port: " + udpPort + "                         ║");
        System.out.println("╚════════════════════════════════════════════════════╝");
        System.out.println();
        
        // Create servers
        TCPAuthServer tcpServer = new TCPAuthServer(tcpPort);
        UDPBroadcastServer udpServer = new UDPBroadcastServer(udpPort);
        
        // Start TCP server in separate thread
        Thread tcpThread = new Thread(() -> {
            tcpServer.start();
        }, "TCP-Server-Thread");
        
        // Start UDP server in separate thread
        Thread udpThread = new Thread(() -> {
            udpServer.start();
        }, "UDP-Server-Thread");
        
        // Add shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n[SHUTDOWN] Shutting down all servers...");
            tcpServer.stop();
            udpServer.stop();
        }));
        
        // Start both servers
        tcpThread.start();
        udpThread.start();
        
        // Wait for both to complete
        try {
            tcpThread.join();
            udpThread.join();
        } catch (InterruptedException e) {
            System.err.println("[ERROR] Server interrupted: " + e.getMessage());
        }
    }
    
    private static void printHelp() {
        System.out.println("Usage: java CombinedServer [options]");
        System.out.println("Options:");
        System.out.println("  --tcp-port <port>  TCP server port (default: 5000)");
        System.out.println("  --udp-port <port>  UDP server port (default: 5001)");
        System.out.println("  --help             Show this help message");
    }
}
