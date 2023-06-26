package ru.netology;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {
    private final Map<String, ru.netology.Handler> get = new HashMap<>();
    private final Map<String, Handler> post = new HashMap<>();

    private final List<String> validPaths = List.of("/index.html", "/spring.svg", "/spring.png",
            "/resources.html", "/styles.css", "/app.js", "/links.html", "/forms.html", "/classic.html", "/events.html",
            "/events.js");


    private final int port;
    private final ExecutorService threadPool = Executors.newFixedThreadPool(64);

    public Server(int port) {
        this.port = port;
    }

    public void start() {
        try (final var serverSocket = new ServerSocket(port)) {
            while (true) {
                connectionProcessing(serverSocket.accept());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void connectionProcessing(Socket socket) {
        Runnable logic = () -> {
            try (
                    final var in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    final var out = new BufferedOutputStream(socket.getOutputStream())
            ) {
                // read only request line for simplicity
                // must be in form GET /path HTTP/1.1
                Request request = new Request(in.readLine());

                final var path = request.getPath();
                final var method = request.getMethod();

                if (!validPaths.contains(path) && !get.containsKey(path) && post.containsKey(path)) {
                    out.write((
                            "HTTP/1.1 404 Not Found\r\n" +
                                    "Content-Length: 0\r\n" +
                                    "Connection: close\r\n" +
                                    "\r\n"
                    ).getBytes());
                    out.flush();
                    return;
                }

                switch (method) {
                    case "GET":
                        synchronized (get) {
                            if (get.containsKey(path)) {
                                final var handler = get.get(path);
                                handler.handle(request, out);
                            } else {
                                defaultCaseProcessing(request, out);
                            }
                        }
                        break;
                    case "POST":
                        synchronized (post) {
                            if (post.containsKey(path)) {
                                final var handler = post.get(path);
                                handler.handle(request, out);
                            } else {
                                defaultCaseProcessing(request, out);
                            }
                        }
                        break;
                    default:
                        defaultCaseProcessing(request, out);
                        break;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        };

        threadPool.submit(logic);
    }

    private void defaultCaseProcessing(Request request, BufferedOutputStream out) throws IOException {
        String path = request.getPath();
        final var filePath = Path.of(".", "public", path);
        final var mimeType = Files.probeContentType(filePath);

        // special case for classic
        if (path.equals("/classic.html")) {
            classicCaseProcessing(filePath, out, mimeType);
            return;
        }

        final var length = Files.size(filePath);
        out.write((
                "HTTP/1.1 200 OK\r\n" +
                        "Content-Type: " + mimeType + "\r\n" +
                        "Content-Length: " + length + "\r\n" +
                        "Connection: close\r\n" +
                        "\r\n"
        ).getBytes());
        Files.copy(filePath, out);
        out.flush();
    }

    private void classicCaseProcessing(Path filePath, BufferedOutputStream out, String mimeType) throws IOException {
        final var template = Files.readString(filePath);
        final var content = template.replace(
                "{time}",
                LocalDateTime.now().toString()
        ).getBytes();
        out.write((
                "HTTP/1.1 200 OK\r\n" +
                        "Content-Type: " + mimeType + "\r\n" +
                        "Content-Length: " + content.length + "\r\n" +
                        "Connection: close\r\n" +
                        "\r\n"
        ).getBytes());
        out.write(content);
        out.flush();
    }

    public void addHandler(String method, String path, ru.netology.Handler handler) {
        switch (method) {
            case "GET":
                synchronized (get) {
                    get.put(path, handler);
                }
                break;
            case "POST":
                synchronized (post) {
                    post.put(path, handler);
                }
                break;
            default:
                throw new RuntimeException("Incorrect argument 'method'. Bad request line!");
        }

    }
}
