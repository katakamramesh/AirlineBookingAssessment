package com.dvtsoftware.airline.booking.handler;

import com.dvtsoftware.airline.booking.model.Airline;
import com.dvtsoftware.airline.booking.model.Booking;
import com.dvtsoftware.airline.booking.model.Flight;
import com.dvtsoftware.airline.booking.model.Passenger;
import com.dvtsoftware.airline.booking.service.DatabaseService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AirlineHandler {

    private static final Logger logger = LoggerFactory.getLogger(AirlineHandler.class);
    private final DatabaseService databaseService;
    private final ObjectMapper objectMapper;

    public AirlineHandler(DatabaseService databaseService) {
        this.databaseService = databaseService;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    // Airlines
    public void getAllAirlines(RoutingContext ctx) {
        databaseService.getAllAirlines()
                .onSuccess(airlines -> {
                    try {
                        String json = objectMapper.writeValueAsString(airlines);
                        ctx.response()
                                .putHeader("content-type", "application/json")
                                .setStatusCode(200)
                                .end(json);
                    } catch (Exception e) {
                        handleError(ctx, e);
                    }
                })
                .onFailure(err -> handleError(ctx, err));
    }

    public void createAirline(RoutingContext ctx) {
        try {
            JsonObject body = ctx.body().asJsonObject();
            Airline airline = new Airline();
            airline.setCode(body.getString("code"));
            airline.setName(body.getString("name"));
            airline.setCountry(body.getString("country"));

            databaseService.createAirline(airline)
                    .onSuccess(created -> {
                        try {
                            String json = objectMapper.writeValueAsString(created);
                            ctx.response()
                                    .putHeader("content-type", "application/json")
                                    .setStatusCode(201)
                                    .end(json);
                        } catch (Exception e) {
                            handleError(ctx, e);
                        }
                    })
                    .onFailure(err -> handleError(ctx, err));
        } catch (Exception e) {
            handleError(ctx, e);
        }
    }

    // Flights
    public void searchFlights(RoutingContext ctx) {
        String from = ctx.queryParam("from").isEmpty() ? null : ctx.queryParam("from").get(0);
        String to = ctx.queryParam("to").isEmpty() ? null : ctx.queryParam("to").get(0);

        if (from == null || to == null) {
            ctx.response()
                    .putHeader("content-type", "application/json")
                    .setStatusCode(400)
                    .end("{\"error\":\"Missing required parameters: from and to\"}");
            return;
        }

        databaseService.searchFlights(from, to)
                .onSuccess(flights -> {
                    try {
                        String json = objectMapper.writeValueAsString(flights);
                        ctx.response()
                                .putHeader("content-type", "application/json")
                                .setStatusCode(200)
                                .end(json);
                    } catch (Exception e) {
                        handleError(ctx, e);
                    }
                })
                .onFailure(err -> handleError(ctx, err));
    }

    public void getFlightById(RoutingContext ctx) {
        try {
            Long id = Long.parseLong(ctx.pathParam("id"));
            databaseService.getFlightById(id)
                    .onSuccess(flight -> {
                        try {
                            String json = objectMapper.writeValueAsString(flight);
                            ctx.response()
                                    .putHeader("content-type", "application/json")
                                    .setStatusCode(200)
                                    .end(json);
                        } catch (Exception e) {
                            handleError(ctx, e);
                        }
                    })
                    .onFailure(err -> handleError(ctx, err));
        } catch (NumberFormatException e) {
            ctx.response()
                    .putHeader("content-type", "application/json")
                    .setStatusCode(400)
                    .end("{\"error\":\"Invalid flight ID\"}");
        }
    }

    public void createFlight(RoutingContext ctx) {
        try {
            Flight flight = objectMapper.readValue(ctx.body().asString(), Flight.class);

            databaseService.createFlight(flight)
                    .onSuccess(created -> {
                        try {
                            String json = objectMapper.writeValueAsString(created);
                            ctx.response()
                                    .putHeader("content-type", "application/json")
                                    .setStatusCode(201)
                                    .end(json);
                        } catch (Exception e) {
                            handleError(ctx, e);
                        }
                    })
                    .onFailure(err -> handleError(ctx, err));
        } catch (Exception e) {
            handleError(ctx, e);
        }
    }

    // Passengers
    public void createPassenger(RoutingContext ctx) {
        try {
            Passenger passenger = objectMapper.readValue(ctx.body().asString(), Passenger.class);

            databaseService.createPassenger(passenger)
                    .onSuccess(created -> {
                        try {
                            String json = objectMapper.writeValueAsString(created);
                            ctx.response()
                                    .putHeader("content-type", "application/json")
                                    .setStatusCode(201)
                                    .end(json);
                        } catch (Exception e) {
                            handleError(ctx, e);
                        }
                    })
                    .onFailure(err -> handleError(ctx, err));
        } catch (Exception e) {
            handleError(ctx, e);
        }
    }

    // Bookings
    public void createBooking(RoutingContext ctx) {
        try {
            Booking booking = objectMapper.readValue(ctx.body().asString(), Booking.class);

            databaseService.createBooking(booking)
                    .onSuccess(created -> {
                        try {
                            String json = objectMapper.writeValueAsString(created);
                            ctx.response()
                                    .putHeader("content-type", "application/json")
                                    .setStatusCode(201)
                                    .end(json);
                        } catch (Exception e) {
                            handleError(ctx, e);
                        }
                    })
                    .onFailure(err -> handleError(ctx, err));
        } catch (Exception e) {
            handleError(ctx, e);
        }
    }

    public void getBookingById(RoutingContext ctx) {
        try {
            Long id = Long.parseLong(ctx.pathParam("id"));
            databaseService.getBookingById(id)
                    .onSuccess(booking -> {
                        try {
                            String json = objectMapper.writeValueAsString(booking);
                            ctx.response()
                                    .putHeader("content-type", "application/json")
                                    .setStatusCode(200)
                                    .end(json);
                        } catch (Exception e) {
                            handleError(ctx, e);
                        }
                    })
                    .onFailure(err -> handleError(ctx, err));
        } catch (NumberFormatException e) {
            ctx.response()
                    .putHeader("content-type", "application/json")
                    .setStatusCode(400)
                    .end("{\"error\":\"Invalid booking ID\"}");
        }
    }

    public void cancelBooking(RoutingContext ctx) {
        try {
            Long id = Long.parseLong(ctx.pathParam("id"));
            databaseService.cancelBooking(id)
                    .onSuccess(v -> {
                        ctx.response()
                                .putHeader("content-type", "application/json")
                                .setStatusCode(200)
                                .end("{\"message\":\"Booking cancelled successfully\"}");
                    })
                    .onFailure(err -> handleError(ctx, err));
        } catch (NumberFormatException e) {
            ctx.response()
                    .putHeader("content-type", "application/json")
                    .setStatusCode(400)
                    .end("{\"error\":\"Invalid booking ID\"}");
        }
    }

    public void getPassengerBookings(RoutingContext ctx) {
        try {
            Long passengerId = Long.parseLong(ctx.pathParam("id"));
            databaseService.getPassengerBookings(passengerId)
                    .onSuccess(bookings -> {
                        try {
                            String json = objectMapper.writeValueAsString(bookings);
                            ctx.response()
                                    .putHeader("content-type", "application/json")
                                    .setStatusCode(200)
                                    .end(json);
                        } catch (Exception e) {
                            handleError(ctx, e);
                        }
                    })
                    .onFailure(err -> handleError(ctx, err));
        } catch (NumberFormatException e) {
            ctx.response()
                    .putHeader("content-type", "application/json")
                    .setStatusCode(400)
                    .end("{\"error\":\"Invalid passenger ID\"}");
        }
    }

    private void handleError(RoutingContext ctx, Throwable err) {
        logger.error("Error handling request", err);

        int statusCode = 500;
        String message = err.getMessage();

        if (message != null) {
            if (message.contains("not found")) {
                statusCode = 404;
            } else if (message.contains("already cancelled") || message.contains("No available seats")) {
                statusCode = 400;
            }
        }

        ctx.response()
                .putHeader("content-type", "application/json")
                .setStatusCode(statusCode)
                .end("{\"error\":\"" + (message != null ? message : "Internal server error") + "\"}");
    }
}
