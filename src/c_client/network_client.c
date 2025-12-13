/**
 * C Network Client
 * Supports both TCP authentication and UDP broadcast communication
 * 
 * Compile with: gcc -o network_client network_client.c -lpthread
 * (On Windows with MinGW: gcc -o network_client.exe network_client.c -lws2_32 -lpthread)
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdbool.h>
#include <time.h>

#ifdef _WIN32
    #include <winsock2.h>
    #include <ws2tcpip.h>
    #pragma comment(lib, "ws2_32.lib")
    typedef int socklen_t;
    #define close closesocket
    #define sleep(x) Sleep((x) * 1000)
    #include <windows.h>
#else
    #include <unistd.h>
    #include <sys/socket.h>
    #include <netinet/in.h>
    #include <arpa/inet.h>
    #include <netdb.h>
    #include <pthread.h>
    #include <errno.h>
    #include <sys/select.h>
#endif

#define BUFFER_SIZE 1024
#define DEFAULT_HOST "127.0.0.1"
#define DEFAULT_TCP_PORT 5000
#define DEFAULT_UDP_PORT 5001

// Global state
typedef struct {
    // TCP state
    int tcp_socket;
    bool tcp_connected;
    bool tcp_authenticated;
    
    // UDP state
    int udp_socket;
    struct sockaddr_in udp_server_addr;
    bool udp_connected;
    volatile bool udp_listening;
    
    // Server info
    char server_host[256];
    int tcp_port;
    int udp_port;
    char client_id[64];
    
    // Thread for UDP listener
    #ifdef _WIN32
    HANDLE udp_thread;
    #else
    pthread_t udp_thread;
    #endif
} ClientState;

// Function prototypes
bool init_networking(void);
void cleanup_networking(void);
bool connect_tcp(ClientState *state);
void disconnect_tcp(ClientState *state);
bool authenticate(ClientState *state, const char *username, const char *password);
char* send_tcp_command(ClientState *state, const char *command, char *response, size_t resp_size);
bool connect_udp(ClientState *state);
void disconnect_udp(ClientState *state);
void send_udp(ClientState *state, const char *message);
void broadcast_message(ClientState *state, const char *message);
void* udp_listener_thread(void *arg);
void print_menu(void);
void print_status(ClientState *state);
void run_interactive(ClientState *state);
char* trim(char *str);

/**
 * Initialize networking (Windows specific)
 */
bool init_networking(void) {
#ifdef _WIN32
    WSADATA wsa_data;
    if (WSAStartup(MAKEWORD(2, 2), &wsa_data) != 0) {
        fprintf(stderr, "[ERROR] WSAStartup failed\n");
        return false;
    }
#endif
    return true;
}

/**
 * Cleanup networking
 */
void cleanup_networking(void) {
#ifdef _WIN32
    WSACleanup();
#endif
}

/**
 * Connect to TCP server
 */
bool connect_tcp(ClientState *state) {
    struct sockaddr_in server_addr;
    struct hostent *server;
    
    // Create socket
    state->tcp_socket = socket(AF_INET, SOCK_STREAM, 0);
    if (state->tcp_socket < 0) {
        fprintf(stderr, "[TCP] Failed to create socket\n");
        return false;
    }
    
    // Resolve hostname
    server = gethostbyname(state->server_host);
    if (server == NULL) {
        fprintf(stderr, "[TCP] Unknown host: %s\n", state->server_host);
        close(state->tcp_socket);
        return false;
    }
    
    // Setup server address
    memset(&server_addr, 0, sizeof(server_addr));
    server_addr.sin_family = AF_INET;
    memcpy(&server_addr.sin_addr.s_addr, server->h_addr, server->h_length);
    server_addr.sin_port = htons(state->tcp_port);
    
    // Set socket timeout
    struct timeval timeout;
    timeout.tv_sec = 5;
    timeout.tv_usec = 0;
    setsockopt(state->tcp_socket, SOL_SOCKET, SO_RCVTIMEO, (char*)&timeout, sizeof(timeout));
    
    // Connect
    if (connect(state->tcp_socket, (struct sockaddr*)&server_addr, sizeof(server_addr)) < 0) {
        fprintf(stderr, "[TCP] Connection failed to %s:%d\n", state->server_host, state->tcp_port);
        close(state->tcp_socket);
        return false;
    }
    
    state->tcp_connected = true;
    printf("[TCP] Connected to %s:%d\n", state->server_host, state->tcp_port);
    
    // Read welcome messages
    char buffer[BUFFER_SIZE];
    int bytes_read;
    
    bytes_read = recv(state->tcp_socket, buffer, BUFFER_SIZE - 1, 0);
    if (bytes_read > 0) {
        buffer[bytes_read] = '\0';
        printf("[TCP] Server: %s", buffer);
    }
    
    bytes_read = recv(state->tcp_socket, buffer, BUFFER_SIZE - 1, 0);
    if (bytes_read > 0) {
        buffer[bytes_read] = '\0';
        printf("[TCP] Server: %s", buffer);
    }
    
    return true;
}

/**
 * Disconnect from TCP server
 */
void disconnect_tcp(ClientState *state) {
    if (state->tcp_connected) {
        send(state->tcp_socket, "QUIT\n", 5, 0);
        close(state->tcp_socket);
        state->tcp_connected = false;
        state->tcp_authenticated = false;
        printf("[TCP] Disconnected\n");
    }
}

/**
 * Authenticate with TCP server
 */
bool authenticate(ClientState *state, const char *username, const char *password) {
    if (!state->tcp_connected) {
        fprintf(stderr, "[TCP] Not connected to server\n");
        return false;
    }
    
    char command[BUFFER_SIZE];
    char response[BUFFER_SIZE];
    
    snprintf(command, BUFFER_SIZE, "AUTH|%s|%s\n", username, password);
    
    if (send(state->tcp_socket, command, strlen(command), 0) < 0) {
        fprintf(stderr, "[TCP] Failed to send auth command\n");
        return false;
    }
    
    int bytes_read = recv(state->tcp_socket, response, BUFFER_SIZE - 1, 0);
    if (bytes_read > 0) {
        response[bytes_read] = '\0';
        printf("[TCP] Auth response: %s", response);
        
        if (strncmp(response, "AUTH_OK", 7) == 0) {
            state->tcp_authenticated = true;
            return true;
        }
    }
    
    return false;
}

/**
 * Send TCP command and receive response
 */
char* send_tcp_command(ClientState *state, const char *command, char *response, size_t resp_size) {
    if (!state->tcp_connected) {
        snprintf(response, resp_size, "ERROR|Not connected");
        return response;
    }
    
    char cmd_with_newline[BUFFER_SIZE];
    snprintf(cmd_with_newline, BUFFER_SIZE, "%s\n", command);
    
    if (send(state->tcp_socket, cmd_with_newline, strlen(cmd_with_newline), 0) < 0) {
        snprintf(response, resp_size, "ERROR|Send failed");
        return response;
    }
    
    int bytes_read = recv(state->tcp_socket, response, resp_size - 1, 0);
    if (bytes_read > 0) {
        response[bytes_read] = '\0';
    } else {
        snprintf(response, resp_size, "ERROR|No response");
    }
    
    return response;
}

/**
 * UDP listener thread function
 */
void* udp_listener_thread(void *arg) {
    ClientState *state = (ClientState*)arg;
    char buffer[BUFFER_SIZE];
    struct sockaddr_in sender_addr;
    socklen_t sender_len = sizeof(sender_addr);
    
    // Set socket timeout for non-blocking receive
    struct timeval timeout;
    timeout.tv_sec = 1;
    timeout.tv_usec = 0;
    setsockopt(state->udp_socket, SOL_SOCKET, SO_RCVTIMEO, (char*)&timeout, sizeof(timeout));
    
    while (state->udp_listening && state->udp_connected) {
        int bytes_received = recvfrom(state->udp_socket, buffer, BUFFER_SIZE - 1, 0,
                                       (struct sockaddr*)&sender_addr, &sender_len);
        
        if (bytes_received > 0) {
            buffer[bytes_received] = '\0';
            
            // Parse and display message
            if (strncmp(buffer, "MSG|", 4) == 0) {
                char *sender = strtok(buffer + 4, "|");
                char *msg = strtok(NULL, "|");
                if (sender && msg) {
                    printf("\n[BROADCAST] %s: %s\n> ", sender, msg);
                    fflush(stdout);
                }
            } else if (strncmp(buffer, "STATUS|", 7) == 0) {
                printf("\n[STATUS] %s\n> ", buffer + 7);
                fflush(stdout);
            } else if (strncmp(buffer, "NOTIFY|", 7) == 0) {
                printf("\n[NOTIFY] %s\n> ", buffer + 7);
                fflush(stdout);
            } else if (strncmp(buffer, "PONG|", 5) == 0) {
                long server_time = atol(buffer + 5);
                printf("[PONG] Server responded\n> ");
                fflush(stdout);
            } else if (strncmp(buffer, "CLIENTS|", 8) == 0) {
                printf("[CLIENTS] %s\n> ", buffer + 8);
                fflush(stdout);
            } else if (strncmp(buffer, "SERVER_SHUTDOWN", 15) == 0) {
                printf("\n[WARNING] Server is shutting down!\n> ");
                fflush(stdout);
            } else {
                printf("\n[UDP] %s\n> ", buffer);
                fflush(stdout);
            }
        }
    }
    
    return NULL;
}

/**
 * Connect to UDP server
 */
bool connect_udp(ClientState *state) {
    // Create UDP socket
    state->udp_socket = socket(AF_INET, SOCK_DGRAM, 0);
    if (state->udp_socket < 0) {
        fprintf(stderr, "[UDP] Failed to create socket\n");
        return false;
    }
    
    // Setup server address
    memset(&state->udp_server_addr, 0, sizeof(state->udp_server_addr));
    state->udp_server_addr.sin_family = AF_INET;
    state->udp_server_addr.sin_port = htons(state->udp_port);
    
    if (inet_pton(AF_INET, state->server_host, &state->udp_server_addr.sin_addr) <= 0) {
        struct hostent *server = gethostbyname(state->server_host);
        if (server == NULL) {
            fprintf(stderr, "[UDP] Unknown host: %s\n", state->server_host);
            close(state->udp_socket);
            return false;
        }
        memcpy(&state->udp_server_addr.sin_addr.s_addr, server->h_addr, server->h_length);
    }
    
    // Register with server
    char register_msg[BUFFER_SIZE];
    snprintf(register_msg, BUFFER_SIZE, "REGISTER|%s", state->client_id);
    send_udp(state, register_msg);
    
    // Wait for registration response
    char buffer[BUFFER_SIZE];
    struct sockaddr_in sender_addr;
    socklen_t sender_len = sizeof(sender_addr);
    
    struct timeval timeout;
    timeout.tv_sec = 5;
    timeout.tv_usec = 0;
    setsockopt(state->udp_socket, SOL_SOCKET, SO_RCVTIMEO, (char*)&timeout, sizeof(timeout));
    
    int bytes_received = recvfrom(state->udp_socket, buffer, BUFFER_SIZE - 1, 0,
                                   (struct sockaddr*)&sender_addr, &sender_len);
    
    if (bytes_received > 0) {
        buffer[bytes_received] = '\0';
        if (strncmp(buffer, "REG_OK", 6) == 0) {
            state->udp_connected = true;
            state->udp_listening = true;
            printf("[UDP] %s\n", buffer);
            
            // Start listener thread
            #ifdef _WIN32
            state->udp_thread = CreateThread(NULL, 0, 
                (LPTHREAD_START_ROUTINE)udp_listener_thread, state, 0, NULL);
            #else
            pthread_create(&state->udp_thread, NULL, udp_listener_thread, state);
            #endif
            
            return true;
        }
    }
    
    fprintf(stderr, "[UDP] Registration failed\n");
    close(state->udp_socket);
    return false;
}

/**
 * Send UDP message
 */
void send_udp(ClientState *state, const char *message) {
    if (state->udp_socket < 0) return;
    
    sendto(state->udp_socket, message, strlen(message), 0,
           (struct sockaddr*)&state->udp_server_addr, sizeof(state->udp_server_addr));
}

/**
 * Broadcast message via UDP
 */
void broadcast_message(ClientState *state, const char *message) {
    if (!state->udp_connected) {
        fprintf(stderr, "[UDP] Not connected to broadcast server\n");
        return;
    }
    
    char broadcast_msg[BUFFER_SIZE];
    snprintf(broadcast_msg, BUFFER_SIZE, "BROADCAST|%s", message);
    send_udp(state, broadcast_msg);
}

/**
 * Disconnect from UDP server
 */
void disconnect_udp(ClientState *state) {
    if (state->udp_connected) {
        state->udp_listening = false;
        send_udp(state, "UNREGISTER");
        
        // Wait for listener thread to finish
        #ifdef _WIN32
        WaitForSingleObject(state->udp_thread, 2000);
        CloseHandle(state->udp_thread);
        #else
        pthread_join(state->udp_thread, NULL);
        #endif
        
        close(state->udp_socket);
        state->udp_connected = false;
        printf("[UDP] Disconnected\n");
    }
}

/**
 * Trim whitespace from string
 */
char* trim(char *str) {
    char *end;
    
    // Trim leading space
    while (*str == ' ' || *str == '\t' || *str == '\n' || *str == '\r') str++;
    
    if (*str == 0) return str;
    
    // Trim trailing space
    end = str + strlen(str) - 1;
    while (end > str && (*end == ' ' || *end == '\t' || *end == '\n' || *end == '\r')) end--;
    
    *(end + 1) = 0;
    
    return str;
}

/**
 * Print menu
 */
void print_menu(void) {
    printf("\n");
    printf("╔══════════════════════════════════════════════════════╗\n");
    printf("║             C Network Client - Commands              ║\n");
    printf("╠══════════════════════════════════════════════════════╣\n");
    printf("║ CONNECTION:                                          ║\n");
    printf("║   tcp-connect      - Connect to TCP auth server      ║\n");
    printf("║   tcp-disconnect   - Disconnect from TCP server      ║\n");
    printf("║   udp-connect      - Connect to UDP broadcast server ║\n");
    printf("║   udp-disconnect   - Disconnect from UDP server      ║\n");
    printf("╠══════════════════════════════════════════════════════╣\n");
    printf("║ TCP COMMANDS:                                        ║\n");
    printf("║   auth <user> <pass>     - Authenticate with server  ║\n");
    printf("║   register <user> <pass> - Register new account      ║\n");
    printf("║   tcp <command>          - Send raw TCP command      ║\n");
    printf("╠══════════════════════════════════════════════════════╣\n");
    printf("║ UDP COMMANDS:                                        ║\n");
    printf("║   broadcast <msg> / bc   - Broadcast message to all  ║\n");
    printf("║   ping                   - Ping UDP server           ║\n");
    printf("║   list                   - List connected clients    ║\n");
    printf("╠══════════════════════════════════════════════════════╣\n");
    printf("║ GENERAL:                                             ║\n");
    printf("║   status   - Show connection status                  ║\n");
    printf("║   help     - Show this help menu                     ║\n");
    printf("║   quit     - Exit the client                         ║\n");
    printf("╚══════════════════════════════════════════════════════╝\n");
    printf("\n");
}

/**
 * Print status
 */
void print_status(ClientState *state) {
    printf("╔════════════════════════════════════════╗\n");
    printf("║           Connection Status            ║\n");
    printf("╠════════════════════════════════════════╣\n");
    printf("║ Client ID: %-26s ║\n", state->client_id);
    printf("║ Server: %-29s ║\n", state->server_host);
    printf("╠════════════════════════════════════════╣\n");
    printf("║ TCP Port: %-27d ║\n", state->tcp_port);
    printf("║ TCP Connected: %-22s ║\n", state->tcp_connected ? "true" : "false");
    printf("║ TCP Authenticated: %-18s ║\n", state->tcp_authenticated ? "true" : "false");
    printf("╠════════════════════════════════════════╣\n");
    printf("║ UDP Port: %-27d ║\n", state->udp_port);
    printf("║ UDP Connected: %-22s ║\n", state->udp_connected ? "true" : "false");
    printf("╚════════════════════════════════════════╝\n");
}

/**
 * Run interactive client
 */
void run_interactive(ClientState *state) {
    char input[BUFFER_SIZE];
    char response[BUFFER_SIZE];
    
    print_menu();
    
    while (1) {
        printf("> ");
        fflush(stdout);
        
        if (fgets(input, BUFFER_SIZE, stdin) == NULL) {
            break;
        }
        
        char *trimmed = trim(input);
        if (strlen(trimmed) == 0) continue;
        
        // Parse command
        char command[64] = {0};
        char args[BUFFER_SIZE] = {0};
        
        char *space = strchr(trimmed, ' ');
        if (space) {
            strncpy(command, trimmed, space - trimmed);
            strcpy(args, trim(space + 1));
        } else {
            strcpy(command, trimmed);
        }
        
        // Convert command to lowercase
        for (int i = 0; command[i]; i++) {
            if (command[i] >= 'A' && command[i] <= 'Z') {
                command[i] = command[i] + 32;
            }
        }
        
        // Process commands
        if (strcmp(command, "tcp-connect") == 0) {
            connect_tcp(state);
        }
        else if (strcmp(command, "tcp-disconnect") == 0) {
            disconnect_tcp(state);
        }
        else if (strcmp(command, "udp-connect") == 0) {
            connect_udp(state);
        }
        else if (strcmp(command, "udp-disconnect") == 0) {
            disconnect_udp(state);
        }
        else if (strcmp(command, "auth") == 0) {
            char username[64], password[64];
            if (sscanf(args, "%63s %63s", username, password) == 2) {
                authenticate(state, username, password);
            } else {
                printf("Usage: auth <username> <password>\n");
            }
        }
        else if (strcmp(command, "register") == 0) {
            char username[64], password[64];
            if (sscanf(args, "%63s %63s", username, password) == 2) {
                char reg_cmd[BUFFER_SIZE];
                snprintf(reg_cmd, BUFFER_SIZE, "REGISTER|%s|%s", username, password);
                send_tcp_command(state, reg_cmd, response, BUFFER_SIZE);
                printf("[TCP] %s", response);
            } else {
                printf("Usage: register <username> <password>\n");
            }
        }
        else if (strcmp(command, "tcp") == 0) {
            // Convert args to uppercase for TCP commands
            for (int i = 0; args[i]; i++) {
                if (args[i] >= 'a' && args[i] <= 'z') {
                    args[i] = args[i] - 32;
                }
            }
            send_tcp_command(state, args, response, BUFFER_SIZE);
            printf("[TCP] %s", response);
        }
        else if (strcmp(command, "broadcast") == 0 || strcmp(command, "bc") == 0) {
            if (strlen(args) > 0) {
                broadcast_message(state, args);
            } else {
                printf("Usage: broadcast <message>\n");
            }
        }
        else if (strcmp(command, "ping") == 0) {
            send_udp(state, "PING");
        }
        else if (strcmp(command, "list") == 0) {
            send_udp(state, "LIST");
        }
        else if (strcmp(command, "status") == 0) {
            print_status(state);
        }
        else if (strcmp(command, "help") == 0) {
            print_menu();
        }
        else if (strcmp(command, "quit") == 0 || strcmp(command, "exit") == 0) {
            disconnect_tcp(state);
            disconnect_udp(state);
            printf("Goodbye!\n");
            break;
        }
        else {
            printf("Unknown command. Type 'help' for available commands.\n");
        }
    }
}

/**
 * Print usage
 */
void print_usage(const char *program) {
    printf("Usage: %s [options]\n", program);
    printf("Options:\n");
    printf("  -h, --host <host>      Server hostname (default: %s)\n", DEFAULT_HOST);
    printf("  -t, --tcp-port <port>  TCP port (default: %d)\n", DEFAULT_TCP_PORT);
    printf("  -u, --udp-port <port>  UDP port (default: %d)\n", DEFAULT_UDP_PORT);
    printf("  --help                 Show this help message\n");
}

/**
 * Main entry point
 */
int main(int argc, char *argv[]) {
    ClientState state = {0};
    strcpy(state.server_host, DEFAULT_HOST);
    state.tcp_port = DEFAULT_TCP_PORT;
    state.udp_port = DEFAULT_UDP_PORT;
    
    // Generate client ID
    srand(time(NULL));
    snprintf(state.client_id, sizeof(state.client_id), "CClient-%d", rand() % 10000);
    
    // Parse arguments
    for (int i = 1; i < argc; i++) {
        if ((strcmp(argv[i], "-h") == 0 || strcmp(argv[i], "--host") == 0) && i + 1 < argc) {
            strncpy(state.server_host, argv[++i], sizeof(state.server_host) - 1);
        }
        else if ((strcmp(argv[i], "-t") == 0 || strcmp(argv[i], "--tcp-port") == 0) && i + 1 < argc) {
            state.tcp_port = atoi(argv[++i]);
        }
        else if ((strcmp(argv[i], "-u") == 0 || strcmp(argv[i], "--udp-port") == 0) && i + 1 < argc) {
            state.udp_port = atoi(argv[++i]);
        }
        else if (strcmp(argv[i], "--help") == 0) {
            print_usage(argv[0]);
            return 0;
        }
    }
    
    // Initialize networking
    if (!init_networking()) {
        return 1;
    }
    
    printf("╔════════════════════════════════════════╗\n");
    printf("║         C Network Client v1.0          ║\n");
    printf("╠════════════════════════════════════════╣\n");
    printf("║ Server: %-29s ║\n", state.server_host);
    printf("║ TCP Port: %-27d ║\n", state.tcp_port);
    printf("║ UDP Port: %-27d ║\n", state.udp_port);
    printf("╚════════════════════════════════════════╝\n");
    
    run_interactive(&state);
    
    cleanup_networking();
    return 0;
}
