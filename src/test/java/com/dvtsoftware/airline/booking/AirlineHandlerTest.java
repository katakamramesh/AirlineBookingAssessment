package com.dvtsoftware.airline.booking;


import com.dvtsoftware.airline.booking.handler.AirlineHandler;
import com.dvtsoftware.airline.booking.service.DatabaseService;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.codec.BodyCodec;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(VertxExtension.class)
public class AirlineHandlerTest {

    private DatabaseService databaseService;
    private WebClient client;
    private int port = 8888;

    @BeforeEach
    void setUp(Vertx vertx, VertxTestContext testContext) {
        // Initialize database
        String jdbcUrl = "jdbc:h2:mem:test_airline_booking;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE";
        databaseService = new DatabaseService(vertx, jdbcUrl, "sa", "");

        databaseService.initialize()
                .compose(v -> {
                    // Setup router
                    Router router = Router.router(vertx);
                    router.route().handler(BodyHandler.create());

                    AirlineHandler handler = new AirlineHandler(databaseService);

                    // Routes
                    router.post("/airlines").handler(handler::createAirline);
                    router.get("/airlines").handler(handler::getAllAirlines);
                    router.post("/flights").handler(handler::createFlight);
                    router.get("/flights/search").handler(handler::searchFlights);
                    router.get("/flights/:id").handler(handler::getFlightById);
                    router.post("/passengers").handler(handler::createPassenger);
                    router.post("/bookings").handler(handler::createBooking);
                    router.get("/bookings/:id").handler(handler::getBookingById);
                    router.delete("/bookings/:id").handler(handler::cancelBooking);
                    router.get("/passengers/:id/bookings").handler(handler::getPassengerBookings);

                    // Start server
                    return vertx.createHttpServer()
                            .requestHandler(router)
                            .listen(port);
                })
                .onSuccess(server -> {
                    client = WebClient.create(vertx);
                    testContext.completeNow();
                })
                .onFailure(testContext::failNow);
    }

    @AfterEach
    void tearDown(Vertx vertx, VertxTestContext testContext) {
        if (client != null) {
            client.close();
        }
        if (databaseService != null) {
            databaseService.close();
        }
        testContext.completeNow();
    }

    @Test
    void testGetAllAirlines(Vertx vertx, VertxTestContext testContext) {
        client.get(port, "localhost", "/airlines")
                .as(BodyCodec.jsonArray())
                .send()
                .onComplete(testContext.succeeding(response -> testContext.verify(() -> {
                    assertThat(response.statusCode()).isEqualTo(200);
                    JsonArray airlines = response.body();
                    assertThat(airlines.size()).isGreaterThan(0);
                    testContext.completeNow();
                })));
    }

    @Test
    void testCreateAirline(Vertx vertx, VertxTestContext testContext) {
        JsonObject newAirline = new JsonObject()
                .put("code", "VS")
                .put("name", "Virgin Atlantic")
                .put("country", "United Kingdom");

        client.post(port, "localhost", "/airlines")
                .as(BodyCodec.jsonObject())
                .sendJsonObject(newAirline)
                .onComplete(testContext.succeeding(response -> testContext.verify(() -> {
                    assertThat(response.statusCode()).isEqualTo(201);
                    JsonObject created = response.body();
                    assertThat(created.getString("code")).isEqualTo("VS");
                    assertThat(created.getString("name")).isEqualTo("Virgin Atlantic");
                    assertThat(created.getLong("id")).isNotNull();
                    testContext.completeNow();
                })));
    }

    @Test
    void testSearchFlights(Vertx vertx, VertxTestContext testContext) {
        client.get(port, "localhost", "/flights/search?from=DXB&to=LHR")
                .as(BodyCodec.jsonArray())
                .send()
                .onComplete(testContext.succeeding(response -> testContext.verify(() -> {
                    assertThat(response.statusCode()).isEqualTo(200);
                    JsonArray flights = response.body();
                    assertThat(flights.size()).isGreaterThan(0);
                    testContext.completeNow();
                })));
    }

    @Test
    void testSearchFlightsMissingParams(Vertx vertx, VertxTestContext testContext) {
        client.get(port, "localhost", "/flights/search")
                .as(BodyCodec.jsonObject())
                .send()
                .onComplete(testContext.succeeding(response -> testContext.verify(() -> {
                    assertThat(response.statusCode()).isEqualTo(400);
                    testContext.completeNow();
                })));
    }

    @Test
    void testGetFlightById(Vertx vertx, VertxTestContext testContext) {
        client.get(port, "localhost", "/flights/1")
                .as(BodyCodec.jsonObject())
                .send()
                .onComplete(testContext.succeeding(response -> testContext.verify(() -> {
                    assertThat(response.statusCode()).isEqualTo(200);
                    JsonObject flight = response.body();
                    assertThat(flight.getLong("id")).isEqualTo(1);
                    assertThat(flight.getString("flightNumber")).isNotNull();
                    testContext.completeNow();
                })));
    }

    @Test
    void testGetFlightByIdNotFound(Vertx vertx, VertxTestContext testContext) {
        client.get(port, "localhost", "/flights/99999")
                .as(BodyCodec.jsonObject())
                .send()
                .onComplete(testContext.succeeding(response -> testContext.verify(() -> {
                    assertThat(response.statusCode()).isEqualTo(404);
                    testContext.completeNow();
                })));
    }

    @Test
    void testCreatePassenger(Vertx vertx, VertxTestContext testContext) {
        JsonObject newPassenger = new JsonObject()
                .put("firstName", "John")
                .put("lastName", "Doe")
                .put("email", "john.doe@test.com")
                .put("phone", "+1-555-1234")
                .put("passportNumber", "US123456")
                .put("dateOfBirth", "1990-01-15");

        client.post(port, "localhost", "/passengers")
                .as(BodyCodec.jsonObject())
                .sendJsonObject(newPassenger)
                .onComplete(testContext.succeeding(response -> testContext.verify(() -> {
                    assertThat(response.statusCode()).isEqualTo(201);
                    JsonObject created = response.body();
                    assertThat(created.getString("firstName")).isEqualTo("John");
                    assertThat(created.getString("email")).isEqualTo("john.doe@test.com");
                    testContext.completeNow();
                })));
    }

    @Test
    void testCreateBooking(Vertx vertx, VertxTestContext testContext) {
        JsonObject newBooking = new JsonObject()
                .put("passengerId", 1)
                .put("flightId", 1)
                .put("seatNumber", "25A");

        client.post(port, "localhost", "/bookings")
                .as(BodyCodec.jsonObject())
                .sendJsonObject(newBooking)
                .onComplete(testContext.succeeding(response -> testContext.verify(() -> {
                    assertThat(response.statusCode()).isEqualTo(201);
                    JsonObject created = response.body();
                    assertThat(created.getLong("passengerId")).isEqualTo(1);
                    assertThat(created.getLong("flightId")).isEqualTo(1);
                    assertThat(created.getString("status")).isEqualTo("CONFIRMED");
                    assertThat(created.getString("bookingReference")).isNotNull();
                    testContext.completeNow();
                })));
    }

    @Test
    void testGetBookingById(Vertx vertx, VertxTestContext testContext) {
        client.get(port, "localhost", "/bookings/1")
                .as(BodyCodec.jsonObject())
                .send()
                .onComplete(testContext.succeeding(response -> testContext.verify(() -> {
                    assertThat(response.statusCode()).isEqualTo(200);
                    JsonObject booking = response.body();
                    assertThat(booking.getLong("id")).isEqualTo(1);
                    testContext.completeNow();
                })));
    }

    @Test
    void testCancelBooking(Vertx vertx, VertxTestContext testContext) {
        // First create a booking
        JsonObject newBooking = new JsonObject()
                .put("passengerId", 2)
                .put("flightId", 2)
                .put("seatNumber", "15B");

        client.post(port, "localhost", "/bookings")
                .as(BodyCodec.jsonObject())
                .sendJsonObject(newBooking)
                .compose(createResponse -> {
                    Long bookingId = createResponse.body().getLong("id");
                    // Then cancel it
                    return client.delete(port, "localhost", "/bookings/" + bookingId)
                            .as(BodyCodec.jsonObject())
                            .send();
                })
                .onComplete(testContext.succeeding(response -> testContext.verify(() -> {
                    assertThat(response.statusCode()).isEqualTo(200);
                    testContext.completeNow();
                })));
    }

    @Test
    void testGetPassengerBookings(Vertx vertx, VertxTestContext testContext) {
        client.get(port, "localhost", "/passengers/1/bookings")
                .as(BodyCodec.jsonArray())
                .send()
                .onComplete(testContext.succeeding(response -> testContext.verify(() -> {
                    assertThat(response.statusCode()).isEqualTo(200);
                    JsonArray bookings = response.body();
                    assertThat(bookings.size()).isGreaterThanOrEqualTo(0);
                    testContext.completeNow();
                })));
    }

    @Test
    void testCreateFlight(Vertx vertx, VertxTestContext testContext) {
        JsonObject newFlight = new JsonObject()
                .put("flightNumber", "TEST123")
                .put("airlineId", 1)
                .put("departureAirport", "JFK")
                .put("arrivalAirport", "LAX")
                .put("departureTime", "2025-12-01T10:00:00")
                .put("arrivalTime", "2025-12-01T14:00:00")
                .put("totalSeats", 200)
                .put("availableSeats", 200)
                .put("price", 299.99)
                .put("status", "SCHEDULED");

        client.post(port, "localhost", "/flights")
                .as(BodyCodec.jsonObject())
                .sendJsonObject(newFlight)
                .onComplete(testContext.succeeding(response -> testContext.verify(() -> {
                    assertThat(response.statusCode()).isEqualTo(201);
                    JsonObject created = response.body();
                    assertThat(created.getString("flightNumber")).isEqualTo("TEST123");
                    assertThat(created.getLong("airlineId")).isEqualTo(1);
                    testContext.completeNow();
                })));
    }
}
