import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;


public class HTTPServer {
    private static final int PORT = 8000;
    private static final Path Directory_Path = Paths.get("/Users/phyll/Documents/info314-assignments/HTTPServer");

    private static final Map<String, String> MIME_TYPES = new HashMap<>() {{
        put("txt", "text/plain");
        put("html", "text/html");
        put("json", "text/json");
    }};


    public static void main(String[] args) throws IOException {
        ServerSocket serverSocket = null;
        try {
            serverSocket = new ServerSocket(PORT);
            while (true) {
                Socket socket = serverSocket.accept();
                HttpRequestHandler handler = new HttpRequestHandler(socket);
                handler.start();
        }
    } finally {
        if (serverSocket != null) {
            serverSocket.close();
        }
    }
}

    private static class HttpRequestHandler extends Thread {
        private final Socket socket;

        public HttpRequestHandler (Socket socket) {
            this.socket = socket;
        }

        public void run() {
            try {
                BufferedReader input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                DataOutputStream output = new DataOutputStream(socket.getOutputStream());
                String requestLine = input.readLine();

                if (requestLine == null) {
                    sendErrorResponse(output, 400, "Bad Request");
                    return;
                }

                String[] requestLineParts = requestLine.split(" ");
                String method = requestLineParts[0];
                String path = requestLineParts[1];
                String httpVersion = requestLineParts[2];
    
                if (!httpVersion.equals("HTTP/1.1")) {
                    sendErrorResponse(output, 505, "HTTP Version Not Supported");
                    return;
                }

                switch (method) {
                    case "GET":
                        handleGet(output, path);
                        break;
                    case "POST":
                        handlePost(input, output, path);
                        break;
                    case "PUT":
                        handlePut(input, output, path);
                        break;
                    case "DELETE":
                        handleDelete(output, path);
                        break;
                    case "OPTIONS":
                        handleOptions(output);
                        break;
                    case "HEAD":
                        handleHead(output, path);
                        break;
                    default:
                        sendErrorResponse(output, 405, "Method Not Allowed");
                        break;
                }                
                input.close();
                output.close();
                socket.close();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
        

        private void handlePost(BufferedReader input, DataOutputStream output, String path) throws IOException {
            if (path.endsWith(".txt")) {
                StringBuilder requestBody = new StringBuilder();
                String line;
                while ((line = input.readLine()) != null && !line.isEmpty()) {
                    requestBody.append(line).append("\n");
                }
          
                File file = new File(Directory_Path + path);  // Base path : "./"
                if (!file.exists()) file.createNewFile();

                if (file.exists() && !file.isDirectory()) {
                    Files.write(file.toPath(), requestBody.toString().getBytes(), StandardOpenOption.APPEND);
                    output.writeBytes("HTTP/1.1 200 OK\r\n");
                    output.writeBytes("\r\n");
                } else {
                    sendErrorResponse(output, 404, "Not Found");
                }
            }
        }  
      
        private void handlePut(BufferedReader input, DataOutputStream output, String path) throws IOException {
            if (path.endsWith(".txt")) {
                StringBuilder requestBody = new StringBuilder();
                String line;
                while ((line = input.readLine()) != null && !line.isEmpty()) {
                    requestBody.append(line).append("\n");
                }

                File file = new File(Directory_Path + path);
                if (!file.exists()) file.createNewFile();

                if (file.exists() && !file.isDirectory()) {
                    Files.write(file.toPath(), requestBody.toString().getBytes());
                    output.writeBytes("HTTP/1.1 200 OK\r\n");
                    output.writeBytes("\r\n");
                } else {
                    sendErrorResponse(output, 404, "Not Found");
                }
            } else {
                sendErrorResponse(output, 415);
            }
        }

        private void handleGet(DataOutputStream output, String path) throws IOException {
            File file = new File(Directory_Path + path);
      
            if (file.exists() && !file.isDirectory()) {
                String[] parts = file.getName().split("\\.");
                String extension = parts[parts.length - 1];
                String mimeType = MIME_TYPES.getOrDefault(extension, "application/octet-stream");
                output.writeBytes("HTTP/1.1 200 OK\r\n");
                output.writeBytes("Content-Type: " + mimeType + "\r\n");
                output.writeBytes("Content-Length: " + file.length() + "\r\n");
                output.writeBytes("\r\n");

                FileInputStream fileIS = new FileInputStream(file);
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = fileIS.read(buffer)) != -1) {
                    output.write(buffer, 0, bytesRead);
                }
                fileIS.close();
            } else {
                sendErrorResponse(output, 404, "Not Found");
            }
        }
      
        private void handleDelete(DataOutputStream output, String path) throws IOException {
            File file = new File(Directory_Path + path);

            if (file.exists() && !file.isDirectory()) {
                if (file.delete()) {
                    output.writeBytes("HTTP/1.1 200 OK\r\n");
                    output.writeBytes("\r\n");
                } else {
                    sendErrorResponse(output, 500, "Internal Server Error");
                }
            } else {
                sendErrorResponse(output, 404, "Not Found");
            }
        }
      
        private void handleOptions(DataOutputStream output) throws IOException {
            output.writeBytes("HTTP/1.1 200 OK\r\n");
            output.writeBytes("Allow: GET, POST, PUT, DELETE, OPTIONS, HEAD\r\n");
            output.writeBytes("\r\n");
        }

        
        private void handleHead(DataOutputStream output, String path) throws IOException {
            File file = new File(Directory_Path + path);
            if (file.exists() && !file.isDirectory()) {
                String[] parts = file.getName().split("\\.");
                String extension = parts[parts.length - 1];
                String mimeType = MIME_TYPES.getOrDefault(extension, "application/octet-stream");
            
                output.writeBytes("HTTP/1.1 200 OK\r\n");
                output.writeBytes("Content-Type: " + mimeType + "\r\n");
                output.writeBytes("Content-Length: " + file.length() + "\r\n");
                output.writeBytes("\r\n");
            } else {
                sendErrorResponse(output, 404, "Not Found");
            }
        }
          
        private void sendErrorResponse(DataOutputStream output, int statusCode) throws IOException {
            sendErrorResponse(output, statusCode, null);
        }
    
        private void sendErrorResponse(DataOutputStream output, int statusCode, String message) throws IOException {
            String reason;
            switch (statusCode) {
                case 400:
                    reason = "Bad Request";
                    break;
                case 404:
                    reason = "Not Found";
                    break;
                case 405:
                    reason = "Method Not Allowed";
                    break;
                case 500:
                    reason = "Internal Server Error";
                    break;
                case 505:
                    reason = "HTTP Version Not Supported";
                    break;
                default:
                    throw new IllegalArgumentException("Invalid status code: " + statusCode);
            }
    
            output.writeBytes("HTTP/1.1 " + statusCode + " " + reason + "\r\n");
            output.writeBytes("\r\n");
            if (message != null) {
                output.writeBytes(message + "\r\n");
            }
        }
    }
}