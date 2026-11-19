package ua.edu.kntu.jobs;

import com.google.gson.*;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import ua.edu.kntu.jobs.Models.*;

public final class JobPortalServer implements AutoCloseable {
    private static final int MAX_BODY = 65536;
    private final Gson gson = new GsonBuilder().setStrictness(Strictness.STRICT).disableHtmlEscaping().create();
    private final JobPortalService service;
    private final SQLiteRepository repository;
    private HttpServer server;
    private ExecutorService executor;
    private String baseUrl;
    public JobPortalServer(Path database) { repository = new SQLiteRepository(database); service = new JobPortalService(repository); }
    public JobPortalService service() { return service; }
    public synchronized JobPortalServer start(int port) throws IOException {
        if (server != null) throw new IllegalStateException("Server is already started");
        server = HttpServer.create(new InetSocketAddress(System.getenv().getOrDefault("IPZ_BIND_HOST", "127.0.0.1"), port), 0);
        baseUrl = System.getenv().getOrDefault("IPZ_PUBLIC_URL", "http://127.0.0.1:" + server.getAddress().getPort());
        executor = Executors.newFixedThreadPool(8);
        server.setExecutor(executor); server.createContext("/", this::handle); server.start(); return this;
    }
    public String baseUrl() { if (baseUrl == null) throw new IllegalStateException("Not started"); return baseUrl; }
    private void handle(HttpExchange exchange) throws IOException {
        int status = 500;
        try {
            String path = exchange.getRequestURI().getPath(), method = exchange.getRequestMethod();
            securityHeaders(exchange);
            if (!method.equals("GET") && !method.equals("HEAD")) {
                String origin = exchange.getRequestHeaders().getFirst("Origin");
                if (origin != null && !origin.equals(baseUrl)) throw new DomainException(403, "ORIGIN_NOT_ALLOWED", "Сторонній Origin не дозволено.");
            }
            if (path.equals("/") || path.equals("/index.html")) { require(method, "GET"); status = 200; resource(exchange, "index.html", "text/html; charset=utf-8"); }
            else if (path.equals("/app.js")) { require(method, "GET"); status = 200; resource(exchange, "app.js", "text/javascript; charset=utf-8"); }
            else if (path.equals("/style.css")) { require(method, "GET"); status = 200; resource(exchange, "style.css", "text/css; charset=utf-8"); }
            else if (path.equals("/favicon.ico")) { require(method, "GET"); status = 204; exchange.sendResponseHeaders(204, -1); }
            else if (path.equals("/api/health")) { require(method, "GET"); status = json(exchange, 200, Map.of("status", "ok", "variant", 26)); }
            else if (path.equals("/api/auth/register")) {
                require(method, "POST"); JsonObject b = body(exchange);
                User user = service.register(string(b,"name",true),string(b,"email",true),string(b,"password",true),string(b,"role",true));
                status = json(exchange, 201, Map.of("user", user));
            } else if (path.equals("/api/auth/login")) {
                require(method, "POST"); JsonObject b = body(exchange); Login login = service.login(string(b,"email",true),string(b,"password",true));
                exchange.getResponseHeaders().add("Set-Cookie", "JSESSION=" + login.token() + "; Path=/; HttpOnly; SameSite=Strict");
                status = json(exchange, 200, Map.of("user", login.user()));
            } else if (path.equals("/api/auth/logout")) {
                require(method, "POST"); service.logout(token(exchange));
                exchange.getResponseHeaders().add("Set-Cookie", "JSESSION=; Path=/; HttpOnly; SameSite=Strict; Max-Age=0");
                status = json(exchange, 200, Map.of("ok", true));
            } else if (path.equals("/api/me")) { require(method, "GET"); status = json(exchange, 200, Map.of("user", user(exchange))); }
            else if (path.equals("/api/me/resume")) {
                require(method, "GET", "PUT"); User user = user(exchange);
                if (method.equals("GET")) status = json(exchange, 200, Map.of("resume", service.resume(user)));
                else { JsonObject b = body(exchange); status = json(exchange, 200, Map.of("resume", service.saveResume(user,string(b,"title",true),string(b,"text",true),string(b,"skills",false)))); }
            } else if (path.equals("/api/me/applications")) {
                require(method, "GET"); List<Application> applications = service.applications(user(exchange)); status = json(exchange, 200, Map.of("applications", applications, "count", applications.size()));
            } else if (path.equals("/api/jobs")) {
                require(method, "GET", "POST");
                if (method.equals("GET")) { List<Job> jobs = service.jobs(JobFilter.from(query(exchange))); status = json(exchange, 200, Map.of("jobs", jobs, "count", jobs.size())); }
                else {
                    User user = user(exchange); JobPortalService.requireRole(user, Role.EMPLOYER); JsonObject b = body(exchange);
                    Job job = service.createJob(user,string(b,"title",true),string(b,"company",true),string(b,"city",true),integer(b,"salary"),bool(b,"remote"),string(b,"description",true));
                    exchange.getResponseHeaders().add("Location", "/api/jobs/" + job.id()); status = json(exchange, 201, Map.of("job", job));
                }
            } else if (path.startsWith("/api/jobs/")) {
                String[] parts = path.substring("/api/jobs/".length()).split("/", -1);
                if (parts.length == 1) {
                    require(method, "GET", "PATCH"); long id = Validation.id(parts[0]);
                    if (method.equals("GET")) status = json(exchange, 200, Map.of("job", service.job(id)));
                    else { User user = user(exchange); JobPortalService.requireRole(user, Role.EMPLOYER); JsonObject b = body(exchange); status = json(exchange, 200, Map.of("job", service.changeJobStatus(user,id,string(b,"status",true)))); }
                } else if (parts.length == 2 && parts[1].equals("applications")) {
                    require(method, "POST"); User user = user(exchange); JobPortalService.requireRole(user, Role.SEEKER); long id = Validation.id(parts[0]); JsonObject b = body(exchange);
                    status = json(exchange, 201, Map.of("application", service.apply(user,id,string(b,"coverLetter",false))));
                } else throw new DomainException(404, "NOT_FOUND", "Маршрут не знайдено.");
            } else throw new DomainException(404, "NOT_FOUND", "Маршрут не знайдено.");
        } catch (DomainException e) { status = json(exchange, e.status(), Map.of("error", Map.of("code",e.code(),"message",e.getMessage()))); }
        catch (Exception e) {
            // Log class only: do not print request payloads, passwords, cookies or SQL values.
            System.err.println("Request failed: " + e.getClass().getSimpleName());
            status = json(exchange, 500, Map.of("error", Map.of("code","INTERNAL_ERROR","message","Внутрішня помилка сервера.")));
        } finally {
            System.out.printf("%s %s %s -> %d%n", Instant.now(), exchange.getRequestMethod(), exchange.getRequestURI().getPath(), status);
            exchange.close();
        }
    }
    private void securityHeaders(HttpExchange e) {
        e.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        e.getResponseHeaders().set("X-Frame-Options", "DENY");
        e.getResponseHeaders().set("Referrer-Policy", "no-referrer");
        e.getResponseHeaders().set("Content-Security-Policy", "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self'; connect-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'");
        e.getResponseHeaders().set("Cache-Control", "no-store");
    }
    private User user(HttpExchange e) { return service.authenticate(token(e)); }
    private String token(HttpExchange e) {
        List<String> headers = e.getRequestHeaders().get("Cookie"); if (headers == null) return null;
        for (String header : headers) for (String part : header.split(";")) { String item = part.trim(); if (item.startsWith("JSESSION=")) return item.substring(9); }
        return null;
    }
    private JsonObject body(HttpExchange e) throws IOException {
        String type = e.getRequestHeaders().getFirst("Content-Type");
        if (type == null || !type.split(";",2)[0].trim().equalsIgnoreCase("application/json")) throw new DomainException(415,"UNSUPPORTED_MEDIA_TYPE","Очікується application/json.");
        byte[] bytes = e.getRequestBody().readNBytes(MAX_BODY + 1);
        if (bytes.length > MAX_BODY) throw new DomainException(413,"BODY_TOO_LARGE","Тіло запиту перевищує 65536 байтів.");
        try {
            String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            JsonElement element = gson.fromJson(text, JsonElement.class);
            if (element == null || !element.isJsonObject()) throw new JsonParseException("Object required");
            return element.getAsJsonObject();
        } catch (JsonParseException | CharacterCodingException e1) { throw new DomainException(400,"BAD_JSON","Очікується коректний JSON-об'єкт UTF-8."); }
    }
    private String string(JsonObject b, String field, boolean required) {
        JsonElement value = b.get(field);
        if (value == null) { if (required) throw DomainException.validation("Поле «" + field + "» обов'язкове."); return null; }
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw DomainException.validation("Поле «" + field + "» повинно бути рядком.");
        return value.getAsString();
    }
    private int integer(JsonObject b, String field) {
        JsonElement value = b.get(field);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber() || !value.toString().matches("-?[0-9]+")) throw DomainException.validation("Поле «" + field + "» повинно бути цілим числом.");
        try { return value.getAsBigDecimal().intValueExact(); } catch (ArithmeticException e) { throw DomainException.validation("Число «" + field + "» завелике."); }
    }
    private boolean bool(JsonObject b, String field) {
        JsonElement value = b.get(field);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) throw DomainException.validation("Поле «" + field + "» повинно бути boolean.");
        return value.getAsBoolean();
    }
    private Map<String,String> query(HttpExchange e) {
        Map<String,String> map = new HashMap<>(); String raw = e.getRequestURI().getRawQuery(); if (raw == null || raw.isEmpty()) return map;
        try {
            for (String part : raw.split("&")) {
                String[] pair = part.split("=",2); String key = URLDecoder.decode(pair[0],StandardCharsets.UTF_8); String value = URLDecoder.decode(pair.length == 2 ? pair[1] : "",StandardCharsets.UTF_8);
                if (map.putIfAbsent(key,value) != null) throw DomainException.validation("Параметр «" + key + "» повторюється.");
            }
        } catch (IllegalArgumentException ex) { throw DomainException.validation("Некоректне кодування параметрів."); }
        Set<String> allowed = Set.of("query","city","minSalary","remote","status");
        if (!allowed.containsAll(map.keySet())) throw DomainException.validation("Невідомий параметр пошуку.");
        return map;
    }
    private void require(String actual, String... allowed) { if (!Arrays.asList(allowed).contains(actual)) throw new DomainException(405,"METHOD_NOT_ALLOWED","Метод не підтримується."); }
    private int json(HttpExchange e, int status, Object object) throws IOException {
        byte[] bytes = gson.toJson(object).getBytes(StandardCharsets.UTF_8); e.getResponseHeaders().set("Content-Type","application/json; charset=utf-8"); e.sendResponseHeaders(status,bytes.length); e.getResponseBody().write(bytes); return status;
    }
    private void resource(HttpExchange e, String name, String type) throws IOException {
        try (var stream = JobPortalServer.class.getResourceAsStream("/" + name)) {
            if (stream == null) throw new IOException("Missing resource " + name);
            byte[] bytes = stream.readAllBytes(); e.getResponseHeaders().set("Content-Type",type); e.sendResponseHeaders(200,bytes.length); e.getResponseBody().write(bytes);
        }
    }
    @Override public synchronized void close() { if (server != null) { server.stop(0); server = null; } if (executor != null) executor.shutdownNow(); repository.close(); }
    public static void main(String[] args) throws Exception {
        int port = 18726; Path db = Path.of("data","jobs.sqlite"); boolean seed = false;
        for (int i=0;i<args.length;i++) switch(args[i]) { case "--port" -> port = Integer.parseInt(args[++i]); case "--db" -> db = Path.of(args[++i]); case "--seed" -> seed = true; default -> throw new IllegalArgumentException("Usage: --port <0..65535> --db <file> --seed"); }
        JobPortalServer portal = new JobPortalServer(db); if (seed) portal.service.seedDemo(); portal.start(port);
        Runtime.getRuntime().addShutdownHook(new Thread(portal::close));
        System.out.println("JobPortal IPZ ready: " + portal.baseUrl());
        System.out.println("SQLite: " + db.toAbsolutePath().normalize());
        System.out.println("Only synthetic local data. Press Ctrl+C to stop.");
        new CountDownLatch(1).await();
    }
}
