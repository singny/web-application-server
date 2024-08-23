package full.server;

import full.interfaces.HttpRequest;
import full.interfaces.HttpResponse;
import full.interfaces.SimpleServlet;
import full.util.ServletMapper;

import java.io.*;
import java.net.Socket;
import java.net.URLConnection;
import java.nio.file.Files;
import java.util.Date;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RequestProcessor implements Runnable {
	private static final Logger logger = LoggerFactory.getLogger(RequestProcessor.class);
    private final Map<String, ServerConfig.HostConfig> virtualHosts;
    private final String indexFileName;
    private final Socket socket;

    public RequestProcessor(Map<String, ServerConfig.HostConfig> virtualHosts, String indexFileName, Socket socket) {
        this.virtualHosts = virtualHosts;
        this.indexFileName = indexFileName;
        this.socket = socket;
    }

    @Override
    public void run() {
        try (OutputStream rawOut = socket.getOutputStream()) {	
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            HttpRequestImpl request = new HttpRequestImpl(reader);
            HttpResponseImpl response = new HttpResponseImpl(rawOut);

            String host = request.getHeaders().get("Host");
            
            if (host == null) {
                logger.error("Host header is missing");
                sendErrorPage(response, 400, "HTTP/1.1 400 Bad Request", "Bad Request");
                return;
            }
            if (host.contains(":")) {
                host = host.split(":")[0];
            }

            ServerConfig.HostConfig hostConfig = virtualHosts.getOrDefault(host, virtualHosts.get("default"));
            if (hostConfig == null) {
                logger.error("No configuration found for host: {}", host);
                sendErrorPage(response, 500, "HTTP/1.1 500 Internal Server Error", "Internal Server Error");
                return;
            }
            
            File rootDirectory = new File(hostConfig.rootDirectory);
            String path = request.getPath();
            File requestedFile = new File(rootDirectory, path.substring(1));
            
            // 보안 규칙: 상위 디렉터리 접근 및 .exe 파일 요청 차단
            if (path.contains("..") || path.endsWith(".exe")) {
                logger.warn("Forbidden request: {}", path);
                sendErrorPage(response, 403, "HTTP/1.1 403 Forbidden", hostConfig.errorPages.get(403));
                return;
            }

            // URL 서블릿 매핑
            String className = ServletMapper.mapUrlToClass(path);
            if (className != null) {
                try {
                    SimpleServlet servlet = (SimpleServlet) Class.forName(className).getDeclaredConstructor().newInstance();
                    servlet.service(request, response);
                    return;
                } catch (ClassNotFoundException e) {
                    logger.error("Servlet class not found: {}", className, e);
                    sendErrorPage(response, 404, "HTTP/1.1 404 Not Found", hostConfig.errorPages.get(404));
                } catch (Exception e) {
                    logger.error("Error processing servlet: {}", className, e);
                    sendErrorPage(response, 500, "HTTP/1.1 500 Internal Server Error", hostConfig.errorPages.get(500));
                }
            }

            if (requestedFile.isDirectory()) {
                requestedFile = new File(requestedFile, indexFileName);
            }
            
            if (!requestedFile.exists() || !requestedFile.canRead()) {
                logger.warn("File not found or not readable: {}", requestedFile.getPath());
                sendErrorPage(response, 404, "HTTP/1.1 404 Not Found", hostConfig.errorPages.get(404));
                return;
            }

            String contentType = URLConnection.getFileNameMap().getContentTypeFor(requestedFile.getName());
            byte[] fileData = Files.readAllBytes(requestedFile.toPath());

            response.setStatus(200);
            response.setHeader("Content-Type", contentType);
            response.setHeader("Content-Length", String.valueOf(fileData.length));
            response.htmlWrite(fileData);
            response.sendResponse();
        	
//            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
//            OutputStreamWriter writer = new OutputStreamWriter(rawOut);
//
//            String host = null;
//            String line;
//            
//            String requestLine = reader.readLine();
//            logger.info("Request received: {}", requestLine);
//
//            String[] tokens = requestLine.split("\\s+");
//            String method = tokens[0];
//            String version = tokens.length > 2 ? tokens[2] : "";
//
//            while (!(line = reader.readLine()).isEmpty()) {
//                if (line.startsWith("Host:")) {
//                    host = line.split(" ")[1].trim();
//                    if (host.contains(":")) {
//                        host = host.split(":")[0];
//                    }
//                    break;
//                }
//            }
//
//            ServerConfig.HostConfig hostConfig = virtualHosts.getOrDefault(host, virtualHosts.get("default"));
//
//            if (hostConfig == null || hostConfig.rootDirectory == null) {
//            	logger.error("Host configuration or root directory not found for host: {}", host);
//                sendErrorPage(writer, rawOut, 500, version, hostConfig);
//                return;
//            }
//
//            File rootDirectory = new File(hostConfig.rootDirectory);
//            String fileName = tokens[1];
//            if (fileName.endsWith("/")) fileName += indexFileName;
//            File requestedFile = new File(rootDirectory, fileName.substring(1));
//
//            // .exe 확장자 파일 요청 차단
//            if (fileName.endsWith(".exe")) {
//                sendErrorPage(writer, rawOut, 403, version, hostConfig);
//                return;
//            }
//
//            if (method.equals("GET")) {
//                if (fileName.endsWith("/")) fileName += indexFileName;
//                String contentType = URLConnection.getFileNameMap().getContentTypeFor(fileName);
//
//                File theFile = new File(rootDirectory, fileName.substring(1));
//
//                if (theFile.canRead() && theFile.exists()) {                	
//                	// 상위 디렉터리 접근 시도 차단
//                	if (requestedFile.getCanonicalPath().startsWith(rootDirectory.getCanonicalPath())) {
//                		String userInput = requestedFile.getCanonicalPath().replace(rootDirectory.getCanonicalPath(), "").replace("\\", "").replace("index.html", "");
//                		
//                		if(!userInput.isEmpty()) {
//                			sendErrorPage(writer, rawOut, 403, version, hostConfig);
//                            return;
//                		}
//                	}
//                    byte[] theData = Files.readAllBytes(theFile.toPath());
//                    if (version.startsWith("HTTP/")) {
//                        sendHeader(writer, "HTTP/1.1 200 OK", contentType, theData.length);
//                    }
//                    rawOut.write(theData);
//                    rawOut.flush();
//                } else {
//                	logger.warn("Requested file not found: {}", requestedFile.getCanonicalPath());
//                    sendErrorPage(writer, rawOut, 404, version, hostConfig);
//                }
//            } else {
//                sendErrorPage(writer, rawOut, 500, version, hostConfig);
//            }
        } catch (IOException ex) {
        	logger.error("Error processing request", ex);
        } finally {
            try {
                socket.close();
            } catch (IOException ex) {
            	logger.error("Error closing socket", ex);
            }
        }
    }
//    private void sendHeader(Writer out, String responseCode, String contentType, int length) throws IOException {
//        out.write(responseCode + "\r\n");
//        Date now = new Date();
//        out.write("Date: " + now + "\r\n");
//        out.write("Server: JHTTP 2.0\r\n");
//        out.write("Content-length: " + length + "\r\n");
//        out.write("Content-type: " + contentType + "\r\n\r\n");
//        out.flush();
//    }
    
//    private void sendErrorPage(Writer writer, OutputStream rawOut, int errorCode, String version, ServerConfig.HostConfig hostConfig) throws IOException {
//        String errorPageFileName = hostConfig.errorPages.getOrDefault(errorCode, "src/main/resources/error_pages/500.html");
//        File errorFile = new File(errorPageFileName);
//        
//        if (errorFile.exists() && errorFile.canRead()) {
//            byte[] errorData = Files.readAllBytes(errorFile.toPath());
//            if (version.startsWith("HTTP/")) {
//                sendHeader(writer, "HTTP/1.1 " + errorCode + " Error", "text/html", errorData.length);
//            }
//            rawOut.write(errorData);
//            rawOut.flush();
//        } else {
//            String body = "<HTML><HEAD><TITLE>Error " + errorCode + "</TITLE></HEAD><BODY><H1>HTTP Error " + errorCode + "</H1></BODY></HTML>";
//            if (version.startsWith("HTTP/")) {
//                sendHeader(writer, "HTTP/1.1 " + errorCode + " Error", "text/html", body.length());
//            }
//            writer.write(body);
//            writer.flush();
//        }
//    }
    
    private void sendErrorPage(HttpResponseImpl response, int errorCode, String statusLine, String errorPagePath) throws IOException {
        File errorPage = new File(errorPagePath);
        if (errorPage.exists() && errorPage.canRead()) {
            byte[] errorData = Files.readAllBytes(errorPage.toPath());
            response.setStatus(errorCode);
            response.setHeader("Content-Type", "text/html");
            response.setHeader("Content-Length", String.valueOf(errorData.length));
            response.htmlWrite(errorData);
        } else {
            response.setStatus(errorCode);
            response.getWriter().write("<html><body><h1>" + errorCode + " Error</h1></body></html>");
        }
        response.sendResponse();
    }
}