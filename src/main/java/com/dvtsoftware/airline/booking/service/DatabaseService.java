package com.dvtsoftware.airline.booking.service;

import com.dvtsoftware.airline.booking.model.Airline;
import com.dvtsoftware.airline.booking.model.Booking;
import com.dvtsoftware.airline.booking.model.Flight;
import com.dvtsoftware.airline.booking.model.Passenger;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.jdbcclient.JDBCConnectOptions;
import io.vertx.jdbcclient.JDBCPool;
import io.vertx.sqlclient.PoolOptions;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.SqlConnection;
import io.vertx.sqlclient.Tuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.vertx.sqlclient.Pool;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

public class DatabaseService {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseService.class);
    private final Pool pool;
    private final Random random = new Random();

    public DatabaseService(Vertx vertx, String jdbcUrl, String user, String password) {
        JDBCConnectOptions connectOptions = new JDBCConnectOptions()
                .setJdbcUrl(jdbcUrl)
                .setUser(user)
                .setPassword(password);

        PoolOptions poolOptions = new PoolOptions()
                .setMaxSize(10);

        this.pool = JDBCPool.pool(vertx, connectOptions, poolOptions);
    }

    public Future<Void> initialize() {
        return executeSqlFile("schema.sql")
                .compose(v -> executeSqlFile("data.sql"))
                .onSuccess(v -> logger.info("Database initialized successfully"))
                .onFailure(err -> logger.error("Database initialization failed", err));
    }

    private Future<Void> executeSqlFile(String fileName) {
        return Future.future(promise -> {
            try {
                InputStream inputStream = getClass().getClassLoader().getResourceAsStream(fileName);
                if (inputStream == null) {
                    promise.fail("SQL file not found: " + fileName);
                    return;
                }

                String sql = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))
                        .lines()
                        .collect(Collectors.joining("\n"));

                pool.query(sql).execute()
                        .onSuccess(rows -> {
                            logger.info("Executed SQL file: {}", fileName);
                            promise.complete();
                        })
                        .onFailure(promise::fail);
            } catch (Exception e) {
                promise.fail(e);
            }
        });
    }

    // Airlines
    public Future<List<Airline>> getAllAirlines() {
        return pool.query("SELECT * FROM airlines ORDER BY name")
                .execute()
                .map(rows -> {
                    List<Airline> airlines = new ArrayList<>();
                    for (Row row : rows) {
                        airlines.add(mapRowToAirline(row));
                    }
                    return airlines;
                });
    }

    public Future<Airline> createAirline(Airline airline) {
        String sql = "INSERT INTO airlines (code, name, country) VALUES (?, ?, ?)";
        return pool.preparedQuery(sql)
                .execute(Tuple.of(airline.getCode(), airline.getName(), airline.getCountry()))
                .compose(rows -> {
                    Long id = rows.property(JDBCPool.GENERATED_KEYS).getLong(0);
                    return getAirlineById(id);
                });
    }

    public Future<Airline> getAirlineById(Long id) {
        return pool.preparedQuery("SELECT * FROM airlines WHERE id = ?")
                .execute(Tuple.of(id))
                .map(rows -> {
                    if (rows.size() == 0) {
                        throw new RuntimeException("Airline not found with id: " + id);
                    }
                    return mapRowToAirline(rows.iterator().next());
                });
    }

    // Flights
    public Future<List<Flight>> searchFlights(String from, String to) {
        String sql = "SELECT * FROM flights WHERE departure_airport = ? AND arrival_airport = ? AND status = 'SCHEDULED' ORDER BY departure_time";
        return pool.preparedQuery(sql)
                .execute(Tuple.of(from, to))
                .map(rows -> {
                    List<Flight> flights = new ArrayList<>();
                    for (Row row : rows) {
                        flights.add(mapRowToFlight(row));
                    }
                    return flights;
                });
    }

    public Future<Flight> getFlightById(Long id) {
        return pool.preparedQuery("SELECT * FROM flights WHERE id = ?")
                .execute(Tuple.of(id))
                .map(rows -> {
                    if (rows.size() == 0) {
                        throw new RuntimeException("Flight not found with id: " + id);
                    }
                    return mapRowToFlight(rows.iterator().next());
                });
    }

    public Future<Flight> createFlight(Flight flight) {
        String sql = "INSERT INTO flights (flight_number, airline_id, departure_airport, arrival_airport, " +
                "departure_time, arrival_time, available_seats, total_seats, price, status) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        return pool.preparedQuery(sql)
                .execute(Tuple.of(
                        flight.getFlightNumber(),
                        flight.getAirlineId(),
                        flight.getDepartureAirport(),
                        flight.getArrivalAirport(),
                        flight.getDepartureTime(),
                        flight.getArrivalTime(),
                        flight.getAvailableSeats(),
                        flight.getTotalSeats(),
                        flight.getPrice(),
                        flight.getStatus() != null ? flight.getStatus() : "SCHEDULED"
                ))
                .compose(rows -> {
                    Long id = rows.property(JDBCPool.GENERATED_KEYS).getLong(0);
                    return getFlightById(id);
                });
    }

    // Passengers
    public Future<Passenger> createPassenger(Passenger passenger) {
        String sql = "INSERT INTO passengers (first_name, last_name, email, phone, passport_number, date_of_birth) " +
                "VALUES (?, ?, ?, ?, ?, ?)";

        return pool.preparedQuery(sql)
                .execute(Tuple.of(
                        passenger.getFirstName(),
                        passenger.getLastName(),
                        passenger.getEmail(),
                        passenger.getPhone(),
                        passenger.getPassportNumber(),
                        passenger.getDateOfBirth()
                ))
                .compose(rows -> {
                    Long id = rows.property(JDBCPool.GENERATED_KEYS).getLong(0);
                    return getPassengerById(id);
                });
    }

    public Future<Passenger> getPassengerById(Long id) {
        return pool.preparedQuery("SELECT * FROM passengers WHERE id = ?")
                .execute(Tuple.of(id))
                .map(rows -> {
                    if (rows.size() == 0) {
                        throw new RuntimeException("Passenger not found with id: " + id);
                    }
                    return mapRowToPassenger(rows.iterator().next());
                });
    }

    // Bookings
    public Future<Booking> createBooking(Booking booking) {
        return pool.getConnection()
                .compose(conn -> createBookingWithConnection(conn, booking)
                        .onComplete(ar -> conn.close()));
    }

    private Future<Booking> createBookingWithConnection(SqlConnection conn, Booking booking) {
        return conn.preparedQuery("SELECT price, available_seats FROM flights WHERE id = ? FOR UPDATE")
                .execute(Tuple.of(booking.getFlightId()))
                .compose(rows -> {
                    if (rows.size() == 0) {
                        return Future.failedFuture("Flight not found");
                    }
                    Row flightRow = rows.iterator().next();

                    // Use column name or index based on what works
                    Integer availableSeats;
                    BigDecimal price;

                    try {
                        availableSeats = flightRow.getInteger("available_seats");
                        price = flightRow.getBigDecimal("price");
                    } catch (Exception e) {
                        // Try uppercase if lowercase fails
                        try {
                            availableSeats = flightRow.getInteger("AVAILABLE_SEATS");
                            price = flightRow.getBigDecimal("PRICE");
                        } catch (Exception e2) {
                            // Try by index as last resort
                            price = flightRow.getBigDecimal(0);
                            availableSeats = flightRow.getInteger(1);
                        }
                    }

                    if (availableSeats <= 0) {
                        return Future.failedFuture("No available seats on this flight");
                    }

                    String bookingRef = generateBookingReference();

                    String insertSql = "INSERT INTO bookings (booking_reference, passenger_id, flight_id, " +
                            "seat_number, status, total_amount) VALUES (?, ?, ?, ?, ?, ?)";

                    return conn.preparedQuery(insertSql)
                            .execute(Tuple.of(
                                    bookingRef,
                                    booking.getPassengerId(),
                                    booking.getFlightId(),
                                    booking.getSeatNumber(),
                                    "CONFIRMED",
                                    price
                            ))
                            .compose(insertResult -> {
                                Long bookingId = insertResult.property(JDBCPool.GENERATED_KEYS).getLong(0);

                                return conn.preparedQuery("UPDATE flights SET available_seats = available_seats - 1 WHERE id = ?")
                                        .execute(Tuple.of(booking.getFlightId()))
                                        .compose(v -> getBookingByIdWithConnection(conn, bookingId));
                            });
                });
    }

    public Future<Booking> getBookingById(Long id) {
        return pool.preparedQuery("SELECT * FROM bookings WHERE id = ?")
                .execute(Tuple.of(id))
                .map(rows -> {
                    if (rows.size() == 0) {
                        throw new RuntimeException("Booking not found with id: " + id);
                    }
                    return mapRowToBooking(rows.iterator().next());
                });
    }

    private Future<Booking> getBookingByIdWithConnection(SqlConnection conn, Long id) {
        return conn.preparedQuery("SELECT * FROM bookings WHERE id = ?")
                .execute(Tuple.of(id))
                .map(rows -> {
                    if (rows.size() == 0) {
                        throw new RuntimeException("Booking not found with id: " + id);
                    }
                    return mapRowToBooking(rows.iterator().next());
                });
    }

    public Future<Void> cancelBooking(Long id) {
        return pool.getConnection()
                .compose(conn -> cancelBookingWithConnection(conn, id)
                        .onComplete(ar -> conn.close()));
    }

    private Future<Void> cancelBookingWithConnection(SqlConnection conn, Long id) {
        return conn.preparedQuery("SELECT flight_id, status FROM bookings WHERE id = ? FOR UPDATE")
                .execute(Tuple.of(id))
                .compose(rows -> {
                    if (rows.size() == 0) {
                        return Future.failedFuture("Booking not found");
                    }

                    Row row = rows.iterator().next();
                    String status = getStringValue(row, "status", "STATUS");

                    if ("CANCELLED".equals(status)) {
                        return Future.failedFuture("Booking already cancelled");
                    }

                    Long flightId = getLongValue(row, "flight_id", "FLIGHT_ID");

                    return conn.preparedQuery("UPDATE bookings SET status = 'CANCELLED' WHERE id = ?")
                            .execute(Tuple.of(id))
                            .compose(v -> conn.preparedQuery("UPDATE flights SET available_seats = available_seats + 1 WHERE id = ?")
                                    .execute(Tuple.of(flightId)))
                            .mapEmpty();
                });
    }

    public Future<List<Booking>> getPassengerBookings(Long passengerId) {
        String sql = "SELECT * FROM bookings WHERE passenger_id = ? ORDER BY booking_date DESC";
        return pool.preparedQuery(sql)
                .execute(Tuple.of(passengerId))
                .map(rows -> {
                    List<Booking> bookings = new ArrayList<>();
                    for (Row row : rows) {
                        bookings.add(mapRowToBooking(row));
                    }
                    return bookings;
                });
    }

    // Helper methods for column name handling
    private String getStringValue(Row row, String lowerCase, String upperCase) {
        try {
            return row.getString(lowerCase);
        } catch (Exception e) {
            return row.getString(upperCase);
        }
    }

    private Long getLongValue(Row row, String lowerCase, String upperCase) {
        try {
            return row.getLong(lowerCase);
        } catch (Exception e) {
            return row.getLong(upperCase);
        }
    }

    private Integer getIntegerValue(Row row, String lowerCase, String upperCase) {
        try {
            return row.getInteger(lowerCase);
        } catch (Exception e) {
            return row.getInteger(upperCase);
        }
    }

    private BigDecimal getBigDecimalValue(Row row, String lowerCase, String upperCase) {
        try {
            return row.getBigDecimal(lowerCase);
        } catch (Exception e) {
            return row.getBigDecimal(upperCase);
        }
    }

    private LocalDateTime getLocalDateTimeValue(Row row, String lowerCase, String upperCase) {
        try {
            return row.getLocalDateTime(lowerCase);
        } catch (Exception e) {
            return row.getLocalDateTime(upperCase);
        }
    }

    private LocalDate getLocalDateValue(Row row, String lowerCase, String upperCase) {
        try {
            return row.getLocalDate(lowerCase);
        } catch (Exception e) {
            return row.getLocalDate(upperCase);
        }
    }

    // Mapping methods with case-insensitive column names
    private Airline mapRowToAirline(Row row) {
        return new Airline(
                getLongValue(row, "id", "ID"),
                getStringValue(row, "code", "CODE"),
                getStringValue(row, "name", "NAME"),
                getStringValue(row, "country", "COUNTRY"),
                getLocalDateTimeValue(row, "created_at", "CREATED_AT"),
                getLocalDateTimeValue(row, "updated_at", "UPDATED_AT")
        );
    }

    private Flight mapRowToFlight(Row row) {
        return new Flight(
                getLongValue(row, "id", "ID"),
                getStringValue(row, "flight_number", "FLIGHT_NUMBER"),
                getLongValue(row, "airline_id", "AIRLINE_ID"),
                getStringValue(row, "departure_airport", "DEPARTURE_AIRPORT"),
                getStringValue(row, "arrival_airport", "ARRIVAL_AIRPORT"),
                getLocalDateTimeValue(row, "departure_time", "DEPARTURE_TIME"),
                getLocalDateTimeValue(row, "arrival_time", "ARRIVAL_TIME"),
                getIntegerValue(row, "available_seats", "AVAILABLE_SEATS"),
                getIntegerValue(row, "total_seats", "TOTAL_SEATS"),
                getBigDecimalValue(row, "price", "PRICE"),
                getStringValue(row, "status", "STATUS"),
                getLocalDateTimeValue(row, "created_at", "CREATED_AT"),
                getLocalDateTimeValue(row, "updated_at", "UPDATED_AT")
        );
    }

    private Passenger mapRowToPassenger(Row row) {
        return new Passenger(
                getLongValue(row, "id", "ID"),
                getStringValue(row, "first_name", "FIRST_NAME"),
                getStringValue(row, "last_name", "LAST_NAME"),
                getStringValue(row, "email", "EMAIL"),
                getStringValue(row, "phone", "PHONE"),
                getStringValue(row, "passport_number", "PASSPORT_NUMBER"),
                getLocalDateValue(row, "date_of_birth", "DATE_OF_BIRTH"),
                getLocalDateTimeValue(row, "created_at", "CREATED_AT"),
                getLocalDateTimeValue(row, "updated_at", "UPDATED_AT")
        );
    }

    private Booking mapRowToBooking(Row row) {
        return new Booking(
                getLongValue(row, "id", "ID"),
                getStringValue(row, "booking_reference", "BOOKING_REFERENCE"),
                getLongValue(row, "passenger_id", "PASSENGER_ID"),
                getLongValue(row, "flight_id", "FLIGHT_ID"),
                getLocalDateTimeValue(row, "booking_date", "BOOKING_DATE"),
                getStringValue(row, "seat_number", "SEAT_NUMBER"),
                getStringValue(row, "status", "STATUS"),
                getBigDecimalValue(row, "total_amount", "TOTAL_AMOUNT"),
                getLocalDateTimeValue(row, "created_at", "CREATED_AT"),
                getLocalDateTimeValue(row, "updated_at", "UPDATED_AT")
        );
    }

    private String generateBookingReference() {
        String letters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        StringBuilder ref = new StringBuilder();
        for (int i = 0; i < 2; i++) {
            ref.append(letters.charAt(random.nextInt(letters.length())));
        }
        for (int i = 0; i < 8; i++) {
            ref.append(random.nextInt(10));
        }
        return ref.toString();
    }

    public void close() {
        pool.close();
    }
}