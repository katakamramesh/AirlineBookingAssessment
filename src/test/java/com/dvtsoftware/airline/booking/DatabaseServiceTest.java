package com.dvtsoftware.airline.booking;

import com.dvtsoftware.airline.booking.model.Airline;
import com.dvtsoftware.airline.booking.model.Booking;
import com.dvtsoftware.airline.booking.model.Flight;
import com.dvtsoftware.airline.booking.model.Passenger;
import com.dvtsoftware.airline.booking.service.DatabaseService;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(VertxExtension.class)
public class DatabaseServiceTest {

    private DatabaseService databaseService;

    @BeforeEach
    void setUp(Vertx vertx, VertxTestContext testContext) {
        String jdbcUrl = "jdbc:h2:mem:test_db;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE";
        databaseService = new DatabaseService(vertx, jdbcUrl, "sa", "");
        
        databaseService.initialize()
                .onSuccess(v -> testContext.completeNow())
                .onFailure(testContext::failNow);
    }

    @AfterEach
    void tearDown(VertxTestContext testContext) {
        if (databaseService != null) {
            databaseService.close();
        }
        testContext.completeNow();
    }

    @Test
    void testGetAllAirlines(VertxTestContext testContext) {
        databaseService.getAllAirlines()
                .onComplete(testContext.succeeding(airlines -> testContext.verify(() -> {
                    assertThat(airlines).isNotNull();
                    assertThat(airlines.size()).isGreaterThan(0);
                    
                    Airline firstAirline = airlines.get(0);
                    assertThat(firstAirline.getId()).isNotNull();
                    assertThat(firstAirline.getCode()).isNotNull();
                    assertThat(firstAirline.getName()).isNotNull();
                    assertThat(firstAirline.getCountry()).isNotNull();
                    
                    testContext.completeNow();
                })));
    }

    @Test
    void testCreateAirline(VertxTestContext testContext) {
        Airline newAirline = new Airline();
        newAirline.setCode("TEST");
        newAirline.setName("Test Airlines");
        newAirline.setCountry("Test Country");

        databaseService.createAirline(newAirline)
                .onComplete(testContext.succeeding(created -> testContext.verify(() -> {
                    assertThat(created.getId()).isNotNull();
                    assertThat(created.getCode()).isEqualTo("TEST");
                    assertThat(created.getName()).isEqualTo("Test Airlines");
                    assertThat(created.getCountry()).isEqualTo("Test Country");
                    assertThat(created.getCreatedAt()).isNotNull();
                    
                    testContext.completeNow();
                })));
    }

    @Test
    void testGetAirlineById(VertxTestContext testContext) {
        databaseService.getAirlineById(1L)
                .onComplete(testContext.succeeding(airline -> testContext.verify(() -> {
                    assertThat(airline).isNotNull();
                    assertThat(airline.getId()).isEqualTo(1L);
                    assertThat(airline.getCode()).isNotNull();
                    
                    testContext.completeNow();
                })));
    }

    @Test
    void testGetAirlineByIdNotFound(VertxTestContext testContext) {
        databaseService.getAirlineById(99999L)
                .onComplete(testContext.failing(err -> testContext.verify(() -> {
                    assertThat(err.getMessage()).contains("Airline not found");
                    testContext.completeNow();
                })));
    }

    @Test
    void testSearchFlights(VertxTestContext testContext) {
        databaseService.searchFlights("DXB", "LHR")
                .onComplete(testContext.succeeding(flights -> testContext.verify(() -> {
                    assertThat(flights).isNotNull();
                    assertThat(flights.size()).isGreaterThan(0);
                    
                    Flight flight = flights.get(0);
                    assertThat(flight.getDepartureAirport()).isEqualTo("DXB");
                    assertThat(flight.getArrivalAirport()).isEqualTo("LHR");
                    
                    testContext.completeNow();
                })));
    }

    @Test
    void testSearchFlightsNoResults(VertxTestContext testContext) {
        databaseService.searchFlights("XXX", "YYY")
                .onComplete(testContext.succeeding(flights -> testContext.verify(() -> {
                    assertThat(flights).isNotNull();
                    assertThat(flights).isEmpty();
                    
                    testContext.completeNow();
                })));
    }

    @Test
    void testGetFlightById(VertxTestContext testContext) {
        databaseService.getFlightById(1L)
                .onComplete(testContext.succeeding(flight -> testContext.verify(() -> {
                    assertThat(flight).isNotNull();
                    assertThat(flight.getId()).isEqualTo(1L);
                    assertThat(flight.getFlightNumber()).isNotNull();
                    assertThat(flight.getPrice()).isGreaterThan(BigDecimal.ZERO);
                    
                    testContext.completeNow();
                })));
    }

    @Test
    void testCreateFlight(VertxTestContext testContext) {
        Flight newFlight = new Flight();
        newFlight.setFlightNumber("TEST001");
        newFlight.setAirlineId(1L);
        newFlight.setDepartureAirport("JFK");
        newFlight.setArrivalAirport("LAX");
        newFlight.setDepartureTime(LocalDateTime.now().plusDays(1));
        newFlight.setArrivalTime(LocalDateTime.now().plusDays(1).plusHours(5));
        newFlight.setTotalSeats(200);
        newFlight.setAvailableSeats(200);
        newFlight.setPrice(new BigDecimal("399.99"));
        newFlight.setStatus("SCHEDULED");

        databaseService.createFlight(newFlight)
                .onComplete(testContext.succeeding(created -> testContext.verify(() -> {
                    assertThat(created.getId()).isNotNull();
                    assertThat(created.getFlightNumber()).isEqualTo("TEST001");
                    assertThat(created.getAirlineId()).isEqualTo(1L);
                    assertThat(created.getPrice()).isEqualByComparingTo(new BigDecimal("399.99"));
                    
                    testContext.completeNow();
                })));
    }

    @Test
    void testCreatePassenger(VertxTestContext testContext) {
        Passenger newPassenger = new Passenger();
        newPassenger.setFirstName("Jane");
        newPassenger.setLastName("Doe");
        newPassenger.setEmail("jane.doe@test.com");
        newPassenger.setPhone("+1-555-9999");
        newPassenger.setPassportNumber("TEST12345");
        newPassenger.setDateOfBirth(LocalDate.of(1995, 5, 15));

        databaseService.createPassenger(newPassenger)
                .onComplete(testContext.succeeding(created -> testContext.verify(() -> {
                    assertThat(created.getId()).isNotNull();
                    assertThat(created.getFirstName()).isEqualTo("Jane");
                    assertThat(created.getLastName()).isEqualTo("Doe");
                    assertThat(created.getEmail()).isEqualTo("jane.doe@test.com");
                    assertThat(created.getDateOfBirth()).isEqualTo(LocalDate.of(1995, 5, 15));
                    
                    testContext.completeNow();
                })));
    }

    @Test
    void testGetPassengerById(VertxTestContext testContext) {
        databaseService.getPassengerById(1L)
                .onComplete(testContext.succeeding(passenger -> testContext.verify(() -> {
                    assertThat(passenger).isNotNull();
                    assertThat(passenger.getId()).isEqualTo(1L);
                    assertThat(passenger.getFirstName()).isNotNull();
                    assertThat(passenger.getEmail()).isNotNull();
                    
                    testContext.completeNow();
                })));
    }

    @Test
    void testCreateBooking(VertxTestContext testContext) {
        Booking newBooking = new Booking();
        newBooking.setPassengerId(1L);
        newBooking.setFlightId(1L);
        newBooking.setSeatNumber("30A");

        databaseService.createBooking(newBooking)
                .onComplete(testContext.succeeding(created -> testContext.verify(() -> {
                    assertThat(created.getId()).isNotNull();
                    assertThat(created.getPassengerId()).isEqualTo(1L);
                    assertThat(created.getFlightId()).isEqualTo(1L);
                    assertThat(created.getSeatNumber()).isEqualTo("30A");
                    assertThat(created.getStatus()).isEqualTo("CONFIRMED");
                    assertThat(created.getBookingReference()).isNotNull();
                    assertThat(created.getTotalAmount()).isGreaterThan(BigDecimal.ZERO);
                    
                    testContext.completeNow();
                })));
    }

    @Test
    void testCreateBookingInvalidFlight(VertxTestContext testContext) {
        Booking newBooking = new Booking();
        newBooking.setPassengerId(1L);
        newBooking.setFlightId(99999L);
        newBooking.setSeatNumber("1A");

        databaseService.createBooking(newBooking)
                .onComplete(testContext.failing(err -> testContext.verify(() -> {
                    assertThat(err.getMessage()).contains("Flight not found");
                    testContext.completeNow();
                })));
    }

    @Test
    void testGetBookingById(VertxTestContext testContext) {
        databaseService.getBookingById(1L)
                .onComplete(testContext.succeeding(booking -> testContext.verify(() -> {
                    assertThat(booking).isNotNull();
                    assertThat(booking.getId()).isEqualTo(1L);
                    assertThat(booking.getBookingReference()).isNotNull();
                    assertThat(booking.getStatus()).isNotNull();
                    
                    testContext.completeNow();
                })));
    }

    @Test
    void testCancelBooking(VertxTestContext testContext) {
        // First create a booking
        Booking newBooking = new Booking();
        newBooking.setPassengerId(2L);
        newBooking.setFlightId(2L);
        newBooking.setSeatNumber("25B");

        databaseService.createBooking(newBooking)
                .compose(created -> {
                    // Then cancel it
                    return databaseService.cancelBooking(created.getId())
                            .compose(v -> databaseService.getBookingById(created.getId()));
                })
                .onComplete(testContext.succeeding(booking -> testContext.verify(() -> {
                    assertThat(booking.getStatus()).isEqualTo("CANCELLED");
                    testContext.completeNow();
                })));
    }

    @Test
    void testCancelBookingAlreadyCancelled(VertxTestContext testContext) {
        // Create and cancel a booking
        Booking newBooking = new Booking();
        newBooking.setPassengerId(3L);
        newBooking.setFlightId(3L);
        newBooking.setSeatNumber("10C");

        databaseService.createBooking(newBooking)
                .compose(created -> databaseService.cancelBooking(created.getId())
                        .compose(v -> databaseService.cancelBooking(created.getId())))
                .onComplete(testContext.failing(err -> testContext.verify(() -> {
                    assertThat(err.getMessage()).contains("already cancelled");
                    testContext.completeNow();
                })));
    }

    @Test
    void testGetPassengerBookings(VertxTestContext testContext) {
        databaseService.getPassengerBookings(1L)
                .onComplete(testContext.succeeding(bookings -> testContext.verify(() -> {
                    assertThat(bookings).isNotNull();
                    
                    for (Booking booking : bookings) {
                        assertThat(booking.getPassengerId()).isEqualTo(1L);
                    }
                    
                    testContext.completeNow();
                })));
    }

    @Test
    void testGetPassengerBookingsEmpty(VertxTestContext testContext) {
        databaseService.getPassengerBookings(99999L)
                .onComplete(testContext.succeeding(bookings -> testContext.verify(() -> {
                    assertThat(bookings).isNotNull();
                    assertThat(bookings).isEmpty();
                    
                    testContext.completeNow();
                })));
    }
}
