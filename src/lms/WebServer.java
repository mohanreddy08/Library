package lms;

import com.sun.net.httpserver.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Built-in Web Server for the Library Management System.
 * Serves the Single-Page Application (SPA) and REST API.
 */
public class WebServer {
    private final Library lib;
    private final Path dataDir;
    private final Path webDir;
    private final int port;
    private HttpServer server;

    public WebServer(Library lib, Path dataDir, Path webDir, int port) {
        this.lib = lib;
        this.dataDir = dataDir;
        this.webDir = webDir;
        this.port = port;
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());

        server.createContext("/api/stats", exchange -> handleStats(exchange));
        server.createContext("/api/books", exchange -> handleBooks(exchange));
        server.createContext("/api/members", exchange -> handleMembers(exchange));
        server.createContext("/api/issue", exchange -> handleIssue(exchange));
        server.createContext("/api/return", exchange -> handleReturn(exchange));
        server.createContext("/api/reserve", exchange -> handleReserve(exchange));
        server.createContext("/api/reservations", exchange -> handleReservations(exchange));
        server.createContext("/api/overdue", exchange -> handleOverdue(exchange));
        server.createContext("/api/pay-fine", exchange -> handlePayFine(exchange));
        server.createContext("/api/undo", exchange -> handleUndo(exchange));
        server.createContext("/api/date", exchange -> handleDate(exchange));
        server.createContext("/api/save", exchange -> handleSave(exchange));
        server.createContext("/api/history", exchange -> handleHistory(exchange));
        server.createContext("/", exchange -> handleStatic(exchange));

        server.start();
        System.out.println("=================================================");
        System.out.println(" Library Management System Web Server Started!");
        System.out.println(" Web UI URL: http://localhost:" + port);
        System.out.println(" Data path:  " + dataDir.toAbsolutePath());
        System.out.println(" Web assets: " + webDir.toAbsolutePath());
        System.out.println("=================================================");
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    // -------------------------------------------------------------
    // API Handlers
    // -------------------------------------------------------------

    private void handleStats(HttpExchange ex) throws IOException {
        if (handleCors(ex)) return;
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
            sendJson(ex, 405, "{\"success\":false,\"error\":\"Method not allowed\"}");
            return;
        }
        Map<String, Object> stats = lib.getStatistics();
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"success\":true,");
        sb.append("\"today\":\"").append(lib.getToday()).append("\",");
        sb.append("\"stats\":{");
        int count = 0;
        for (Map.Entry<String, Object> e : stats.entrySet()) {
            if (count++ > 0) sb.append(",");
            sb.append("\"").append(e.getKey()).append("\":").append(e.getValue());
        }
        sb.append("}}");
        sendJson(ex, 200, sb.toString());
    }

    private void handleBooks(HttpExchange ex) throws IOException {
        if (handleCors(ex)) return;
        String method = ex.getRequestMethod().toUpperCase();
        Map<String, String> query = parseQuery(ex.getRequestURI().getRawQuery());

        if ("GET".equals(method)) {
            String prefix = query.get("prefix");
            String author = query.get("author");
            String category = query.get("category");
            String sort = query.getOrDefault("sort", "id");

            List<Book> list;
            if (prefix != null && !prefix.isBlank()) {
                list = lib.searchByTitlePrefix(prefix);
            } else if (author != null && !author.isBlank()) {
                list = lib.searchByAuthor(author);
            } else if (category != null && !category.isBlank()) {
                list = lib.searchByCategory(category);
            } else if ("title".equalsIgnoreCase(sort)) {
                list = lib.booksSortedByTitle();
            } else if ("author".equalsIgnoreCase(sort)) {
                list = lib.booksSortedByAuthor();
            } else {
                list = lib.booksSortedById();
            }

            StringBuilder sb = new StringBuilder("{\"success\":true,\"books\":[");
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(bookToJson(list.get(i)));
            }
            sb.append("]}");
            sendJson(ex, 200, sb.toString());
            return;
        }

        if ("POST".equals(method)) {
            String body = readBody(ex);
            try {
                int id = getJsonInt(body, "id", 0);
                String title = getJsonString(body, "title");
                String aut = getJsonString(body, "author");
                String cat = getJsonString(body, "category");
                int copies = getJsonInt(body, "copies", 0);
                if (copies <= 0) copies = getJsonInt(body, "totalCopies", 1);

                if (id <= 0 || title == null || aut == null) {
                    sendJson(ex, 400, "{\"success\":false,\"error\":\"Invalid book data. id, title, author are required.\"}");
                    return;
                }

                Book b = new Book(id, title, aut, cat, copies);
                boolean added = lib.addBook(b);
                if (added) {
                    sendJson(ex, 201, "{\"success\":true,\"message\":\"Book added successfully\",\"book\":" + bookToJson(b) + "}");
                } else {
                    sendJson(ex, 400, "{\"success\":false,\"error\":\"A book with ID " + id + " already exists.\"}");
                }
            } catch (Exception e) {
                sendJson(ex, 400, "{\"success\":false,\"error\":" + quote(e.getMessage()) + "}");
            }
            return;
        }

        if ("DELETE".equals(method)) {
            int id = 0;
            if (query.containsKey("id")) {
                try { id = Integer.parseInt(query.get("id")); } catch (Exception ignored) {}
            }
            if (id <= 0) {
                String body = readBody(ex);
                id = getJsonInt(body, "id", 0);
            }
            if (id <= 0) {
                sendJson(ex, 400, "{\"success\":false,\"error\":\"Missing or invalid book id\"}");
                return;
            }

            boolean removed = lib.removeBook(id);
            if (removed) {
                sendJson(ex, 200, "{\"success\":true,\"message\":\"Book " + id + " removed successfully.\"}");
            } else {
                sendJson(ex, 400, "{\"success\":false,\"error\":\"Cannot remove book: Not found or copies are on loan.\"}");
            }
            return;
        }

        sendJson(ex, 405, "{\"success\":false,\"error\":\"Method not allowed\"}");
    }

    private void handleMembers(HttpExchange ex) throws IOException {
        if (handleCors(ex)) return;
        String method = ex.getRequestMethod().toUpperCase();
        Map<String, String> query = parseQuery(ex.getRequestURI().getRawQuery());

        if ("GET".equals(method)) {
            String path = ex.getRequestURI().getPath();
            if (path.endsWith("/loans") || query.containsKey("id")) {
                int id = 0;
                try {
                    id = Integer.parseInt(query.getOrDefault("id", "0"));
                } catch (Exception ignored) {}
                Member m = lib.getMember(id);
                if (m == null) {
                    sendJson(ex, 404, "{\"success\":false,\"error\":\"Member not found\"}");
                    return;
                }
                List<Transaction> activeLoans = lib.activeLoans(id);
                StringBuilder sb = new StringBuilder("{\"success\":true,\"member\":");
                sb.append(memberToJson(m)).append(",\"activeLoans\":[");
                for (int i = 0; i < activeLoans.size(); i++) {
                    if (i > 0) sb.append(",");
                    sb.append(txnToJson(activeLoans.get(i)));
                }
                sb.append("]}");
                sendJson(ex, 200, sb.toString());
                return;
            }

            String sort = query.getOrDefault("sort", "id");
            List<Member> list = "name".equalsIgnoreCase(sort) ? lib.membersSortedByName() : lib.membersSortedById();
            StringBuilder sb = new StringBuilder("{\"success\":true,\"members\":[");
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(memberToJson(list.get(i)));
            }
            sb.append("]}");
            sendJson(ex, 200, sb.toString());
            return;
        }

        if ("POST".equals(method)) {
            String body = readBody(ex);
            try {
                int id = getJsonInt(body, "id", 0);
                String name = getJsonString(body, "name");
                if (id <= 0 || name == null || name.isBlank()) {
                    sendJson(ex, 400, "{\"success\":false,\"error\":\"Valid member id and name are required.\"}");
                    return;
                }
                Member m = new Member(id, name);
                boolean added = lib.addMember(m);
                if (added) {
                    sendJson(ex, 201, "{\"success\":true,\"message\":\"Member added successfully\",\"member\":" + memberToJson(m) + "}");
                } else {
                    sendJson(ex, 400, "{\"success\":false,\"error\":\"A member with ID " + id + " already exists.\"}");
                }
            } catch (Exception e) {
                sendJson(ex, 400, "{\"success\":false,\"error\":" + quote(e.getMessage()) + "}");
            }
            return;
        }

        sendJson(ex, 405, "{\"success\":false,\"error\":\"Method not allowed\"}");
    }

    private void handleIssue(HttpExchange ex) throws IOException {
        if (handleCors(ex)) return;
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            sendJson(ex, 405, "{\"success\":false,\"error\":\"Method not allowed\"}");
            return;
        }
        String body = readBody(ex);
        int memberId = getJsonInt(body, "memberId", 0);
        int bookId = getJsonInt(body, "bookId", 0);

        if (memberId <= 0 || bookId <= 0) {
            sendJson(ex, 400, "{\"success\":false,\"error\":\"memberId and bookId are required\"}");
            return;
        }

        Library.IssueResult res = lib.issueBook(memberId, bookId);
        if (res == Library.IssueResult.ISSUED) {
            LocalDate due = lib.getToday().plusDays(Library.LOAN_DAYS);
            sendJson(ex, 200, String.format("{\"success\":true,\"message\":\"Book issued successfully! Due date: %s\",\"dueDate\":\"%s\"}", due, due));
        } else {
            String msg = switch (res) {
                case MEMBER_NOT_FOUND -> "Member with ID " + memberId + " not found.";
                case BOOK_NOT_FOUND -> "Book with ID " + bookId + " not found.";
                case LIMIT_REACHED -> "Borrowing limit of " + Library.MAX_BOOKS + " books reached for this member.";
                case FINE_PENDING -> "Member has pending unpaid fines. Please clear fines first.";
                case ALREADY_HAS_COPY -> "Member already holds a copy of this book.";
                case NO_COPY_AVAILABLE -> "No copy available right now. You can reserve this book.";
                case RESERVED_FOR_OTHER -> "Available copy is reserved for another member in the queue.";
                default -> "Could not issue book: " + res;
            };
            sendJson(ex, 400, "{\"success\":false,\"error\":" + quote(msg) + ",\"code\":\"" + res + "\"}");
        }
    }

    private void handleReturn(HttpExchange ex) throws IOException {
        if (handleCors(ex)) return;
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            sendJson(ex, 405, "{\"success\":false,\"error\":\"Method not allowed\"}");
            return;
        }
        String body = readBody(ex);
        int memberId = getJsonInt(body, "memberId", 0);
        int bookId = getJsonInt(body, "bookId", 0);

        Transaction t = lib.returnBook(memberId, bookId);
        if (t == null) {
            sendJson(ex, 400, "{\"success\":false,\"error\":\"No active loan found for member " + memberId + " and book " + bookId + ".\"}");
        } else {
            String msg = t.getFine() > 0 
                ? "Book returned late! Fine of Rs. " + t.getFine() + " added to member account."
                : "Book returned on time. No fine.";
            sendJson(ex, 200, "{\"success\":true,\"message\":" + quote(msg) + ",\"fine\":" + t.getFine() + ",\"transaction\":" + txnToJson(t) + "}");
        }
    }

    private void handleReserve(HttpExchange ex) throws IOException {
        if (handleCors(ex)) return;
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            sendJson(ex, 405, "{\"success\":false,\"error\":\"Method not allowed\"}");
            return;
        }
        String body = readBody(ex);
        int memberId = getJsonInt(body, "memberId", 0);
        int bookId = getJsonInt(body, "bookId", 0);

        int res = lib.reserveBook(memberId, bookId);
        if (res > 0) {
            sendJson(ex, 200, "{\"success\":true,\"message\":\"Book reserved successfully! Queue position: #" + res + "\",\"position\":" + res + "}");
        } else {
            String err = switch (res) {
                case Library.RES_NOT_FOUND -> "Member or Book not found.";
                case Library.RES_AVAILABLE -> "A copy is currently available on shelf; issue it directly instead of reserving.";
                case Library.RES_DUPLICATE -> "Member is already in the reservation queue for this book.";
                case Library.RES_HOLDS_COPY -> "Member already holds an active borrowed copy of this book.";
                default -> "Could not reserve book.";
            };
            sendJson(ex, 400, "{\"success\":false,\"error\":" + quote(err) + ",\"code\":" + res + "}");
        }
    }

    private void handleReservations(HttpExchange ex) throws IOException {
        if (handleCors(ex)) return;
        Map<String, String> q = parseQuery(ex.getRequestURI().getRawQuery());
        int bookId = Integer.parseInt(q.getOrDefault("bookId", "0"));
        List<Integer> queue = lib.reservationQueue(bookId);
        StringBuilder sb = new StringBuilder("{\"success\":true,\"bookId\":" + bookId + ",\"queue\":[");
        for (int i = 0; i < queue.size(); i++) {
            if (i > 0) sb.append(",");
            int mId = queue.get(i);
            Member m = lib.getMember(mId);
            sb.append("{\"position\":").append(i + 1).append(",\"memberId\":").append(mId)
              .append(",\"memberName\":").append(m != null ? quote(m.getName()) : "\"Unknown\"").append("}");
        }
        sb.append("]}");
        sendJson(ex, 200, sb.toString());
    }

    private void handleOverdue(HttpExchange ex) throws IOException {
        if (handleCors(ex)) return;
        List<Transaction> list = lib.overdueReport();
        StringBuilder sb = new StringBuilder("{\"success\":true,\"overdue\":[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(txnToJson(list.get(i)));
        }
        sb.append("]}");
        sendJson(ex, 200, sb.toString());
    }

    private void handlePayFine(HttpExchange ex) throws IOException {
        if (handleCors(ex)) return;
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            sendJson(ex, 405, "{\"success\":false,\"error\":\"Method not allowed\"}");
            return;
        }
        String body = readBody(ex);
        int memberId = getJsonInt(body, "memberId", 0);
        int amount = getJsonInt(body, "amount", 0);

        boolean ok = lib.payFine(memberId, amount);
        if (ok) {
            Member m = lib.getMember(memberId);
            sendJson(ex, 200, "{\"success\":true,\"message\":\"Payment of Rs. " + amount + " recorded. Remaining fine: Rs. " + m.getFineDue() + "\",\"fineDue\":" + m.getFineDue() + "}");
        } else {
            Member m = lib.getMember(memberId);
            String err = m == null ? "Member not found" : "Invalid payment amount (amount must be > 0 and <= Rs. " + m.getFineDue() + ")";
            sendJson(ex, 400, "{\"success\":false,\"error\":" + quote(err) + "}");
        }
    }

    private void handleUndo(HttpExchange ex) throws IOException {
        if (handleCors(ex)) return;
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            sendJson(ex, 405, "{\"success\":false,\"error\":\"Method not allowed\"}");
            return;
        }
        String res = lib.undoLast();
        if (res != null) {
            sendJson(ex, 200, "{\"success\":true,\"message\":" + quote(res) + "}");
        } else {
            sendJson(ex, 400, "{\"success\":false,\"error\":\"Undo stack is empty. Nothing to undo.\"}");
        }
    }

    private void handleDate(HttpExchange ex) throws IOException {
        if (handleCors(ex)) return;
        if ("GET".equalsIgnoreCase(ex.getRequestMethod())) {
            sendJson(ex, 200, "{\"success\":true,\"today\":\"" + lib.getToday() + "\"}");
            return;
        }
        if ("POST".equalsIgnoreCase(ex.getRequestMethod())) {
            String body = readBody(ex);
            String dateStr = getJsonString(body, "date");
            int days = getJsonInt(body, "days", 0);

            try {
                if (days != 0) {
                    lib.setToday(lib.getToday().plusDays(days));
                } else if (dateStr != null && !dateStr.isBlank()) {
                    lib.setToday(LocalDate.parse(dateStr.trim()));
                } else {
                    lib.setToday(LocalDate.now());
                }
                sendJson(ex, 200, "{\"success\":true,\"message\":\"System date updated to " + lib.getToday() + "\",\"today\":\"" + lib.getToday() + "\"}");
            } catch (DateTimeParseException e) {
                sendJson(ex, 400, "{\"success\":false,\"error\":\"Invalid date format. Expected YYYY-MM-DD.\"}");
            }
            return;
        }
        sendJson(ex, 405, "{\"success\":false,\"error\":\"Method not allowed\"}");
    }

    private void handleSave(HttpExchange ex) throws IOException {
        if (handleCors(ex)) return;
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            sendJson(ex, 405, "{\"success\":false,\"error\":\"Method not allowed\"}");
            return;
        }
        try {
            lib.save(dataDir);
            sendJson(ex, 200, "{\"success\":true,\"message\":\"Library data successfully saved to " + dataDir.toString() + "\"}");
        } catch (Exception e) {
            sendJson(ex, 500, "{\"success\":false,\"error\":" + quote("Failed to save: " + e.getMessage()) + "}");
        }
    }

    private void handleHistory(HttpExchange ex) throws IOException {
        if (handleCors(ex)) return;
        List<Transaction> hist = lib.getHistory();
        StringBuilder sb = new StringBuilder("{\"success\":true,\"history\":[");
        for (int i = 0; i < hist.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(txnToJson(hist.get(i)));
        }
        sb.append("]}");
        sendJson(ex, 200, sb.toString());
    }

    // -------------------------------------------------------------
    // Static File Serving
    // -------------------------------------------------------------

    private void handleStatic(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        if (path == null || path.equals("/") || path.isBlank()) {
            path = "/index.html";
        }
        if (path.startsWith("/")) path = path.substring(1);

        Path file = webDir.resolve(path).normalize();
        if (!file.startsWith(webDir) || !Files.exists(file) || Files.isDirectory(file)) {
            // Fallback to index.html for SPA routes
            file = webDir.resolve("index.html");
        }

        if (Files.exists(file) && !Files.isDirectory(file)) {
            String mime = getMimeType(file.getFileName().toString());
            byte[] bytes = Files.readAllBytes(file);
            ex.getResponseHeaders().set("Content-Type", mime + "; charset=utf-8");
            ex.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(bytes);
            }
        } else {
            String notFound = "<h1>404 Not Found</h1><p>Web resources directory: " + webDir.toAbsolutePath() + "</p>";
            byte[] bytes = notFound.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            ex.sendResponseHeaders(404, bytes.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    // -------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------

    private boolean handleCors(HttpExchange ex) throws IOException {
        Headers h = ex.getResponseHeaders();
        h.set("Access-Control-Allow-Origin", "*");
        h.set("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS");
        h.set("Access-Control-Allow-Headers", "Content-Type, Authorization");
        if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
            ex.sendResponseHeaders(204, -1);
            return true;
        }
        return false;
    }

    private void sendJson(HttpExchange ex, int code, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        Headers h = ex.getResponseHeaders();
        h.set("Content-Type", "application/json; charset=utf-8");
        h.set("Access-Control-Allow-Origin", "*");
        ex.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private String readBody(HttpExchange ex) throws IOException {
        try (InputStream is = ex.getRequestBody()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> map = new HashMap<>();
        if (rawQuery == null || rawQuery.isBlank()) return map;
        for (String pair : rawQuery.split("&")) {
            int idx = pair.indexOf('=');
            if (idx > 0) {
                String k = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8);
                String v = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8);
                map.put(k, v);
            } else if (!pair.isBlank()) {
                map.put(URLDecoder.decode(pair, StandardCharsets.UTF_8), "");
            }
        }
        return map;
    }

    private String getMimeType(String filename) {
        String f = filename.toLowerCase();
        if (f.endsWith(".html") || f.endsWith(".htm")) return "text/html";
        if (f.endsWith(".css")) return "text/css";
        if (f.endsWith(".js")) return "application/javascript";
        if (f.endsWith(".json")) return "application/json";
        if (f.endsWith(".png")) return "image/png";
        if (f.endsWith(".svg")) return "image/svg+xml";
        if (f.endsWith(".ico")) return "image/x-icon";
        return "text/plain";
    }

    private String bookToJson(Book b) {
        return String.format("{\"id\":%d,\"title\":%s,\"author\":%s,\"category\":%s,\"totalCopies\":%d,\"availableCopies\":%d}",
                b.getId(), quote(b.getTitle()), quote(b.getAuthor()), quote(b.getCategory()), b.getTotalCopies(), b.getAvailableCopies());
    }

    private String memberToJson(Member m) {
        return String.format("{\"id\":%d,\"name\":%s,\"fineDue\":%d}",
                m.getId(), quote(m.getName()), m.getFineDue());
    }

    private String txnToJson(Transaction t) {
        Book b = lib.getBook(t.getBookId());
        Member m = lib.getMember(t.getMemberId());
        String bTitle = b != null ? b.getTitle() : "Unknown";
        String mName = m != null ? m.getName() : "Unknown";
        return String.format("{\"id\":%d,\"memberId\":%d,\"memberName\":%s,\"bookId\":%d,\"bookTitle\":%s,\"issueDate\":\"%s\",\"dueDate\":\"%s\",\"returnDate\":%s,\"fine\":%d,\"active\":%b}",
                t.getId(), t.getMemberId(), quote(mName), t.getBookId(), quote(bTitle),
                t.getIssueDate(), t.getDueDate(),
                t.getReturnDate() == null ? "null" : "\"" + t.getReturnDate() + "\"",
                t.getFine(), t.isActive());
    }

    private static String quote(String s) {
        if (s == null) return "null";
        return "\"" + s.replace("\\", "\\\\")
                       .replace("\"", "\\\"")
                       .replace("\n", "\\n")
                       .replace("\r", "\\r")
                       .replace("\t", "\\t") + "\"";
    }

    private static String getJsonString(String json, String key) {
        if (json == null) return null;
        Pattern p = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"([^\"]*)\"");
        Matcher m = p.matcher(json);
        if (m.find()) return m.group(1);
        return null;
    }

    private static int getJsonInt(String json, String key, int def) {
        if (json == null) return def;
        Pattern p = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(\\d+)");
        Matcher m = p.matcher(json);
        if (m.find()) {
            try { return Integer.parseInt(m.group(1)); } catch (Exception ignored) {}
        }
        return def;
    }

    // -------------------------------------------------------------
    // Entry Point
    // -------------------------------------------------------------

    public static void main(String[] args) throws IOException {
        Path dataDir = Paths.get(args.length > 0 ? args[0] : "demo_data");
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 8080;
        Path webDir = Paths.get("web");

        Library lib = new Library();
        if (lib.load(dataDir)) {
            System.out.println("Loaded " + lib.bookCount() + " books and " + lib.memberCount() + " members from " + dataDir);
        } else {
            lib = SampleData.create();
            System.out.println("No existing data found in " + dataDir + ". Sample dataset created.");
        }

        WebServer ws = new WebServer(lib, dataDir, webDir, port);
        ws.start();
    }
}
