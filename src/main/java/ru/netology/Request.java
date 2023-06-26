package ru.netology;

public class Request {
    private final String method;
    private final String path;
    private final String[] parts;

    public String[] getParts() {
        return parts;
    }

    public String getMethod() {
        return method;
    }

    public String getPath() {
        return path;
    }

    public Request(String requestLine) {
        parts = requestLine.split(" ");

        if (parts.length != 3) {
            throw new IllegalArgumentException("Bad request");
        }

        method = parts[0];
        path = parts[1];
    }
}
