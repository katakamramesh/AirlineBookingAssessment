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
    private final Pool  pool;
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
                    int availableSeats = flightRow.getInteger("available_seats");

                    if (availableSeats <= 0) {
                        return Future.failedFuture("No available seats on this flight");
                    }

                    BigDecimal price = flightRow.getBigDecimal("price");
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
                    String status = row.getString("status");

                    if ("CANCELLED".equals(status)) {
                        return Future.failedFuture("Booking already cancelled");
                    }

                    Long flightId = row.getLong("flight_id");

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

    // Mapping methods
    private Airline mapRowToAirline(Row row) {
        return new Airline(
                row.getLong("ID"),
                row.getString("CODE"),
                row.getString("NAME"),
                row.getString("COUNTRY"),
                row.getLocalDateTime("CREATED_AT"),
                row.getLocalDateTime("UPDATED_AT")
        );
    }

    private Flight mapRowToFlight(Row row) {
        return new Flight(
                row.getLong("ID"),
                row.getString("FLIGHT_NUMBER"),
                row.getLong("AIRLINE_ID"),
                row.getString("DEPARTURE_AIRPORT"),
                row.getString("ARRIVAL_AIRPORT"),
                row.getLocalDateTime("DEPARTURE_TIME"),
                row.getLocalDateTime("ARRIVAL_TIME"),
                row.getInteger("AVAILABLE_SEATS"),
                row.getInteger("TOTAL_SEATS"),
                row.getBigDecimal("PRICE"),
                row.getString("STATUS"),
                row.getLocalDateTime("CREATED_AT"),
                row.getLocalDateTime("UPDATED_AT")
        );
    }

    private Passenger mapRowToPassenger(Row row) {
        LocalDate dob = row.getLocalDate("DATE_OF_BIRTH");
        return new Passenger(
                row.getLong("ID"),
                row.getString("FIRST_NAME"),
                row.getString("LAST_NAME"),
                row.getString("EMAIL"),
                row.getString("PHONE"),
                row.getString("PASSPORT_NUMBER"),
                dob,
                row.getLocalDateTime("CREATED_AT"),
                row.getLocalDateTime("UPDATED_AT")
        );
    }

    private Booking mapRowToBooking(Row row) {
        return new Booking(
                row.getLong("ID"),
                row.getString("BOOKING_REFERENCE"),
                row.getLong("PASSENGER_ID"),
                row.getLong("FLIGHT_ID"),
                row.getLocalDateTime("BOOKING_DATE"),
                row.getString("SEAT_NUMBER"),
                row.getString("STATUS"),
                row.getBigDecimal("TOTAL_AMOUNT"),
                row.getLocalDateTime("CREATED_AT"),
                row.getLocalDateTime("UPDATED_AT")
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