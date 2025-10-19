package com.dvtsoftware.airline.booking;

import com.dvtsoftware.airline.booking.handler.AirlineHandler;
import com.dvtsoftware.airline.booking.service.DatabaseService;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class MainVerticle extends AbstractVerticle {

    private static final Logger logger = LoggerFactory.getLogger(MainVerticle.class);
    private DatabaseService databaseService;

    @Override
    public void start(Promise<Void> startPromise) {
        // Load configuration
        JsonObject config = loadConfig();

        // Initialize database service
        String jdbcUrl = config.getJsonObject("database").getString("url");
        String user = config.getJsonObject("database").getString("user");
        String password = config.getJsonObject("database").getString("password");

        databaseService = new DatabaseService(vertx, jdbcUrl, user, password);

        // Initialize database schema and data
        databaseService.initialize()
                .compose(v -> setupHttpServer(config))
                .onSuccess(v -> {
                    logger.info("Application started successfully");
                    startPromise.complete();
                })
                .onFailure(err -> {
                    logger.error("Failed to start application", err);
                    startPromise.fail(err);
                });
    }

    private JsonObject loadConfig() {
        try {
            InputStream is = getClass().getClassLoader().getResourceAsStream("application.json");
            if (is == null) {
                throw new RuntimeException("application.json not found");
            }
            Scanner scanner = new Scanner(is, StandardCharsets.UTF_8).useDelimiter("\\A");
            String content = scanner.hasNext() ? scanner.next() : "{}";
            return new JsonObject(content);
        } catch (Exception e) {
            logger.error("Failed to load configuration", e);
            return new JsonObject()
                    .put("server", new JsonObject()
                            .put("port", 8080)
                            .put("host", "localhost"))
                    .put("database", new JsonObject()
                            .put("url", "jdbc:h2:mem:airline_booking;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
                            .put("user", "sa")
                            .put("password", ""));
        }
    }

    private Future<Void> setupHttpServer(JsonObject config) {
        Router router = Router.router(vertx);

        // Add body handler for POST/PUT requests
        router.route().handler(BodyHandler.create());

        // Initialize handler
        AirlineHandler handler = new AirlineHandler(databaseService);

        // Airlines routes
        router.post("/airlines").handler(handler::createAirline);
        router.get("/airlines").handler(handler::getAllAirlines);

        // Flights routes
        router.post("/flights").handler(handler::createFlight);
        router.get("/flights/search").handler(handler::searchFlights);
        router.get("/flights/:id").handler(handler::getFlightById);

        // Passengers routes
        router.post("/passengers").handler(handler::createPassenger);

        // Bookings routes
        router.post("/bookings").handler(handler::createBooking);
        router.get("/bookings/:id").handler(handler::getBookingById);
        router.delete("/bookings/:id").handler(handler::cancelBooking);
        router.get("/passengers/:id/bookings").handler(handler::getPassengerBookings);

        // Health check endpoint
        router.get("/health").handler(ctx -> {
            ctx.response()
                    .putHeader("content-type", "application/json")
                    .end("{\"status\":\"UP\"}");
        });

        // Error handlers
        router.errorHandler(404, ctx -> {
            ctx.response()
                    .putHeader("content-type", "application/json")
                    .setStatusCode(404)
                    .end("{\"error\":\"Not Found\"}");
        });

        router.errorHandler(500, ctx -> {
            ctx.response()
                    .putHeader("content-type", "application/json")
                    .setStatusCode(500)
                    .end("{\"error\":\"Internal Server Error\"}");
        });

        // Create HTTP server
        int port = config.getJsonObject("server").getInteger("port");
        String host = config.getJsonObject("server").getString("host");

        return vertx.createHttpServer()
                .requestHandler(router)
                .listen(port, "0.0.0.0")
                .map(server -> {
                    logger.info("HTTP server started on {}:{}", host, port);
                    return null;
                });
    }

    @Override
    public void stop(Promise<Void> stopPromise) {
        if (databaseService != null) {
            databaseService.close();
        }
        logger.info("Application stopped");
        stopPromise.complete();
    }

    public static void main(String[] args) {
        io.vertx.core.Vertx vertx = io.vertx.core.Vertx.vertx();
        vertx.deployVerticle(new MainVerticle())
                .onSuccess(id -> logger.info("MainVerticle deployed successfully with ID: {}", id))
                .onFailure(err -> {
                    logger.error("Failed to deploy MainVerticle", err);
                    System.exit(1);
                });
    }
}