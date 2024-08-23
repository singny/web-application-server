package full.server;

import full.util.ServletMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class Misson3And4 {

    private Map<String, ServerConfig.HostConfig> virtualHosts;
    private String indexFileName;
    private ServerSocket serverSocket;

    @BeforeEach
    public void setUp() throws IOException {
        virtualHosts = new HashMap<>();

        // Initialize virtual hosts and configurations
        ServerConfig.HostConfig hostAConfig = new ServerConfig.HostConfig();
        hostAConfig.rootDirectory = "src/test/resources/a_com_root";
        hostAConfig.errorPages = Map.of(
                403, "src/test/resources/error_pages/403.html",
                404, "src/test/resources/error_pages/404.html",
                500, "src/test/resources/error_pages/500.html"
        );

        virtualHosts.put("a.com", hostAConfig);
        virtualHosts.put("default", hostAConfig);  // default host

        indexFileName = "index.html";

        // Create a server socket for testing
        serverSocket = new ServerSocket(0);  // Bind to an available port
    }

    @Test
    public void test403ForDirectoryTraversal() throws IOException {
        testErrorResponse("/../../../../etc/passwd", 403, "src/test/resources/error_pages/403.html");
    }

    @Test
    public void test403ForExeFileRequest() throws IOException {
        testErrorResponse("/file.exe", 403, "src/test/resources/error_pages/403.html");
    }

    @Test
    public void test404ForFileNotFound() throws IOException {
        testErrorResponse("/nonexistentfile.html", 404, "src/test/resources/error_pages/404.html");
    }

    @Test
    public void test500ForServletClassNotFound() throws IOException {
        // Add a mapping for a non-existent servlet class
        ServletMapper.addMapping("/test", "full.servlets.NonExistentServlet");
        testErrorResponse("/test", 500, "src/test/resources/error_pages/500.html");
    }

    private void testErrorResponse(String requestPath, int expectedStatusCode, String expectedErrorPagePath) throws IOException {
        // Start a thread to handle the request
        Thread serverThread = new Thread(() -> {
            try {
                Socket clientSocket = serverSocket.accept();
                RequestProcessor processor = new RequestProcessor(virtualHosts, indexFileName, clientSocket);
                processor.run();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        serverThread.start();

        // Wait for the server thread to start
        try {
            Thread.sleep(100);  // Adjust sleep time if necessary
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // Create a client socket and send a request
        try (Socket clientSocket = new Socket("localhost", serverSocket.getLocalPort())) {
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()));
            BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

            // Send a request
            writer.write("GET " + requestPath + " HTTP/1.1\r\n");
            writer.write("Host: a.com\r\n");
            writer.write("\r\n");
            writer.flush();

            // Read the response
            String responseLine = reader.readLine();
            assertNotNull(responseLine);
            assertTrue(responseLine.startsWith("HTTP/1.1 " + expectedStatusCode));

            // Read the rest of the response to get the body
            StringBuilder responseBody = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                responseBody.append(line).append("\n");
            }

            // Validate error page content
            File errorPageFile = new File(expectedErrorPagePath);
            String expectedContent = new String(Files.readAllBytes(errorPageFile.toPath()));

            assertTrue(responseBody.toString().contains(expectedContent));
        } finally {
            // Close the server socket after the test
            serverSocket.close();
        }
    }
}