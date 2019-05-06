package github.scarsz.bin;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import github.scarsz.bin.route.GetBinRoute;
import github.scarsz.bin.route.IndexRoute;
import github.scarsz.bin.route.api.v1.ApiV1GetBinRoute;
import github.scarsz.bin.route.api.v1.ApiV1PostRoute;
import org.apache.commons.lang.StringUtils;
import spark.ModelAndView;
import spark.template.velocity.VelocityTemplateEngine;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static spark.Spark.*;

public class Server {

    public static String version = Server.class.getPackage().getImplementationVersion();
    static {
        if (version == null) version = "DEV";
    }
    private static Server instance;

    private final Configuration config;
    private final Connection connection;
    private final Gson gson = new GsonBuilder().disableHtmlEscaping().create();
    private final Gson gsonPretty = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();

    public Server(int port) throws SQLException {
        instance = this;
        config = new Configuration()
                .setMaximumExpiration(TimeUnit.DAYS, 365)
                .setDefaultExpiration(TimeUnit.DAYS, 30)
                .setPort(port);
        connection = DriverManager.getConnection("jdbc:h2:" + new File("bin").getAbsolutePath());

        connection.prepareStatement("create table if not exists bins" +
                "(" +
                "    id uuid default random_uuid() not null," +
                "    hits int default 0 not null," +
                "    time bigint default DATEDIFF('SECOND', DATE '1970-01-01', CURRENT_TIMESTAMP()) * 1000 not null," +
                "    expiration bigint default null," +
                "    description clob default null," +
                "    constraint bins_pk primary key (id)" +
                ");").executeUpdate();
        connection.prepareStatement("create table if not exists files" +
                "(" +
                "    id uuid default random_uuid() not null," +
                "    bin uuid not null," +
                "    type varchar(100) default null," +
                "    name blob not null," +
                "    description varchar(100) default null," +
                "    content blob not null," +
                "    constraint files_pk primary key (id)" +
                ");").executeUpdate();

        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                try {
                    connection.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        });

        staticFileLocation("/static/");
        port(port);
        after((request, response) -> response.header("Content-Encoding", "gzip"));

        // logging
        afterAfter((request, response) -> {
            String ip = StringUtils.isNotBlank(request.headers("X-Forwarded-For")) ? request.headers("X-Forwarded-For") : request.ip();
            String method = request.requestMethod();
            String location = request.url() + (StringUtils.isNotBlank(request.queryString()) ? "?" + request.queryString() : "");
//            if (location.contains(".")) return;
            System.out.println(ip + ":" + request.raw().getRemotePort() + " " + method + " " + location + " -> " + response.status());
        });

        get("/", new IndexRoute());
        get("/:id", new GetBinRoute());
        before("/api/*", (request, response) -> {
            // default to API v1 routes when the client wants latest promoted version
            response.redirect(request.pathInfo().replace("/api/", "/v1/"));
            halt(302);
        });
        path("/v1", () -> {
            get("/:id", new ApiV1GetBinRoute());
            post("/post", new ApiV1PostRoute());
        });
        exception(Exception.class, (exception, request, response) -> {
            switch (exception.getClass().getSimpleName()) {
                case "IllegalArgumentException":
                case "JsonSyntaxException":
                case "NotImplementedException": response.status(400); break;
                case "BinNotFoundException": response.status(404); break;
                default: response.status(500); {
                    exception.printStackTrace();
                    break;
                }
            }

            Map<String, Object> error = new HashMap<>();
            error.put("type", exception.getClass().getSimpleName());
            error.put("message", exception.getMessage());

            Map<String, Object> payload = new HashMap<>();
            payload.put("status", "error");
            payload.put("error", error);
            response.body(Server.getInstance().getGsonPretty().toJson(payload));
        });
    }

    public static String render(Map<String, Object> model, String templatePath) {
        return new VelocityTemplateEngine().render(new ModelAndView(model, templatePath));
    }

    public static Server getInstance() {
        return instance;
    }
    public static long getUptime() {
        return ManagementFactory.getRuntimeMXBean().getUptime();
    }
    public Connection getConnection() {
        return connection;
    }
    public Configuration getConfig() {
        return config;
    }
    public Gson getGson() {
        return gson;
    }
    public Gson getGsonPretty() {
        return gsonPretty;
    }
}
