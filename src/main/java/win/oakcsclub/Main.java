package win.oakcsclub;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.JdbiException;
import org.jdbi.v3.sqlite3.SQLitePlugin;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

import static spark.Spark.*;
import static win.oakcsclub.Util.*;


public class Main {
    public final static String INDEX = unexception(() -> Files.readString(Path.of("./src/main/resources/index.html")));
    public final static String FORGET = unexception(() -> Files.readString(Path.of("./src/main/resources/forget_username.html")));
    public final static String HELP = unexception(() -> Files.readString(Path.of("./src/main/resources/help.html")));
    
    public final static String FILE = "database.db";

    public final static List<String> usernames = unexception(() -> Files.readAllLines(Path.of("./src/main/resources/users.txt")));

    public final static Jdbi jdbi = Jdbi.create("jdbc:sqlite:" + FILE)
            .installPlugin(new SQLitePlugin());
    
    public static void main(String[] args) {
        port(4444);
        File file = new File(FILE);
        file.delete();
        jdbi.useHandle(h -> {
            AtomicInteger id = new AtomicInteger(10);
            h.execute("CREATE TABLE users (id INT, username VARCHAR, password VARCHAR)");
            usernames.forEach(u -> {
                String[] split = u.split(":");
                h.execute("INSERT INTO users (id, username, password) VALUES (?, ?, ?)", id.getAndIncrement(), split[1], split[0]);
            });
        });
        file.setWritable(false);
        if(file.canWrite()) {
            throw new RuntimeException("unable to set read-only perms to file");
        }
        System.out.println("DB init");

        get("/", (req, res) -> {
            Function<String, String> makeWarning = (x) -> INDEX.replace("WARNING_DISPLAY","")
                    .replace("WARNING_MESSAGE","Warning: " + x);
            if(req.queryParams("name") == null && req.queryParams("password") == null) {
                return INDEX.replace("WARNING_DISPLAY","display: none");
            }
            if(req.queryParams("name") == null) return makeWarning.apply("Missing name");
            if(req.queryParams("password") == null) return makeWarning.apply("Missing password");
            // make the request
            String sql = "SELECT id FROM users WHERE username = '" + req.queryParams("name") + "' AND password = '" + req.queryParams("password") + "'";

            @Nullable Integer id;
            try {
                id = jdbi.withHandle(h -> h.createQuery(sql).mapTo(Integer.class).findFirst()).orElse(null);
            } catch(JdbiException e) {
                return makeWarning.apply(e.getMessage());
            }
            if(id == null) return makeWarning.apply("User does not exist or password is incorrect");
            return INDEX.replace("WARNING_DISPLAY","")
                    .replace("class=\"warning\"","class=\"warning good\"")
                    .replace("WARNING_MESSAGE","Welcome, " + req.queryParams("name") + "  (your id: " + id + ")");
        });
        get("/forget", (req, res) -> {
            Function<String, String> makeWarning = (x) -> FORGET.replace("WARNING_DISPLAY","")
                    .replace("WARNING_MESSAGE",x)
                    .replace("INSERT_RESULTS","");

            String query = req.queryParams("query");
            if(query == null || query.trim().equals("")) {
                return FORGET.replace("WARNING_DISPLAY","display: none")
                        .replace("INSERT_RESULTS","");
            }
            String sql = "SELECT username FROM users WHERE username LIKE '" + query + "'";
            try {
                var result = jdbi.withHandle(h -> h.createQuery(sql).mapTo(String.class).stream()
                        .map(x -> "<li>" + x + "</li>")
                        .reduce(String::concat)
                        .map(x -> "<ul>" + x + "</ul>")
                        .orElse("<span>No results found</span>")
                );
                return FORGET.replace("WARNING_DISPLAY", "display: none")
                        .replace("INSERT_RESULTS", result);
            } catch(JdbiException e) {
                return makeWarning.apply(e.getMessage());
            }
        });

        get("/help", (req, res) -> HELP);

    }
}
