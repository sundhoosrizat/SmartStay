import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;

public class SmartStay {
    private static final int ADVANCE = 10_000;
    private static final ZoneId HOTEL_ZONE = ZoneId.of("Asia/Colombo");

    enum RoomType {
        STANDARD(6000, 1000, 10, 2, false),
        DELUXE(9000, 1500, 12, 2, true),
        SUITE(14000, 2000, 15, 3, true),
        FAMILY_SUITE(18000, 2500, 15, 4, true);

        final int rate;
        final int extraCharge;
        final int tax;
        final int capacity;
        final boolean breakfast;

        RoomType(int rate, int extraCharge, int tax, int capacity, boolean breakfast) {
            this.rate = rate;
            this.extraCharge = extraCharge;
            this.tax = tax;
            this.capacity = capacity;
            this.breakfast = breakfast;
        }
    }

    enum BedType { SINGLE, DOUBLE, QUEEN, KING }
    enum Status { AVAILABLE, RESERVED, UNDER_MAINTENANCE }

    record Room(String id, RoomType type, BedType bed, Status status) {
        @Override
        public String toString() {
            return "%s | %s | %s | LKR %,d/night | Capacity: %d | %s | Breakfast: %s"
                    .formatted(id, type, bed, type.rate, type.capacity, status,
                            type.breakfast ? "Included" : "No");
        }
    }

    record Guest(String id, String name, String phone, String email) {}

    static class Booking {
        final String id;
        final Guest guest;
        final Room room;
        final LocalDate arrival;
        final LocalDate departure;
        final int guests;
        private boolean cancelled;

        Booking(String id, Guest guest, Room room, LocalDate arrival,
                LocalDate departure, int guests) {
            this.id = id;
            this.guest = guest;
            this.room = room;
            this.arrival = arrival;
            this.departure = departure;
            this.guests = guests;
        }

        boolean isActive() {
            return !cancelled;
        }

        // The checkout date is available for the next guest's check-in.
        boolean overlaps(LocalDate start, LocalDate end) {
            return isActive() && arrival.isBefore(end) && start.isBefore(departure);
        }

        void checkChangeAllowed() {
            check(isActive(), "Booking is already cancelled.");
            ZonedDateTime deadline = arrival.atStartOfDay(HOTEL_ZONE).minusHours(24);
            check(!ZonedDateTime.now(HOTEL_ZONE).isAfter(deadline),
                    "The deadline for changing this booking has passed.");
        }

        void cancel() {
            checkChangeAllowed();
            cancelled = true;
        }

        @Override
        public String toString() {
            return "%s | %s | Room %s | Check-in: %s | Check-out: %s | Guests: %d | %s"
                    .formatted(id, guest.name(), room.id(), arrival, departure,
                            guests, cancelled ? "CANCELLED" : "CONFIRMED");
        }
    }

    private final Scanner input = new Scanner(System.in);
    private final ArrayList<Room> rooms = new ArrayList<>();
    private final ArrayList<Guest> guests = new ArrayList<>();
    private final ArrayList<Booking> bookings = new ArrayList<>();

    public static void main(String[] args) {
        new SmartStay().run();
    }

    private void run() {
        rooms.add(new Room("101", RoomType.STANDARD, BedType.DOUBLE, Status.AVAILABLE));
        rooms.add(new Room("201", RoomType.DELUXE, BedType.QUEEN, Status.AVAILABLE));

        while (true) {
            System.out.println("""

                    SMARTSTAY HOTEL BOOKING
                    1. List rooms
                    2. Add room
                    3. Update room status
                    4. Remove room
                    5. Register guest
                    6. Search available rooms
                    7. Make booking
                    8. Search bookings by ID or guest name
                    9. View bookings by check-in date
                    10. Cancel booking
                    11. Modify booking
                    12. Generate invoice
                    13. Edit room details
                    0. Exit""");
            System.out.print("Choose an option: ");

            if (!input.hasNextLine()) {
                return;
            }

            try {
                switch (input.nextLine().strip()) {
                    case "0" -> { return; }
                    case "1" -> show(rooms);
                    case "2" -> addRoom();
                    case "3" -> updateRoomStatus();
                    case "4" -> removeRoom();
                    case "5" -> register();
                    case "6" -> searchRooms();
                    case "7" -> book();
                    case "8" -> searchBookings();
                    case "9" -> viewBookingsByCheckInDate();
                    case "10" -> cancelBooking();
                    case "11" -> modifyBooking();
                    case "12" -> generateInvoice();
                    case "13" -> editRoomDetails();
                    default -> System.out.println("Choose a menu number from 0 to 13.");
                }
            } catch (IllegalArgumentException | java.time.DateTimeException ex) {
                System.out.println("Unable to continue. " + ex.getMessage());
            } catch (java.util.NoSuchElementException ex) {
                return;
            }
        }
    }

    private String ask(String prompt) {
        System.out.print(prompt + ": ");
        String answer = input.nextLine().strip();
        check(!answer.isEmpty(), "Enter a value for " + prompt + ".");
        return answer;
    }

    private LocalDate date(String prompt) {
        try {
            return LocalDate.parse(ask(prompt + " (YYYY-MM-DD)"));
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("Enter a valid date in YYYY-MM-DD format.");
        }
    }

    private <T extends Enum<T>> T choose(Class<T> type) {
        T[] options = type.getEnumConstants();
        for (int i = 0; i < options.length; i++) {
            System.out.printf("%d. %s%n", i + 1, options[i]);
        }

        String value = ask(type.getSimpleName())
                .toUpperCase(Locale.ROOT).replace(' ', '_');

        for (int i = 0; i < options.length; i++) {
            if (value.equals(String.valueOf(i + 1)) || value.equals(options[i].name())) {
                return options[i];
            }
        }
        throw new IllegalArgumentException("Choose one of the displayed values.");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    private static void show(List<?> items) {
        if (items.isEmpty()) {
            System.out.println("No matching records.");
        }
        items.forEach(System.out::println);
    }

    private Room findRoom(String id) {
        return rooms.stream()
                .filter(room -> room.id().equalsIgnoreCase(id))
                .findFirst().orElse(null);
    }

    private Room requireRoom() {
        Room room = findRoom(ask("Room ID"));
        check(room != null, "Room not found.");
        return room;
    }

    private void addRoom() {
        String id = ask("Room ID");
        check(findRoom(id) == null, "Room ID already exists.");

        RoomType type = choose(RoomType.class);
        BedType bed = choose(BedType.class);
        rooms.add(new Room(id, type, bed, Status.AVAILABLE));
        System.out.println("Room " + id + " added.");
    }

    private void updateRoomStatus() {
        Room room = requireRoom();
        Status status = choose(Status.class);

        rooms.set(rooms.indexOf(room), new Room(room.id(), room.type(), room.bed(), status));
        System.out.println("Room " + room.id() + " status updated to " + status + ".");
    }

    private void editRoomDetails() {
        Room room = requireRoom();
        System.out.println("Current details: " + room);

        System.out.println("Select the room type:");
        RoomType type = choose(RoomType.class);
        System.out.println("Select the bed type:");
        BedType bed = choose(BedType.class);

        // Existing bookings retain the room details recorded at reservation time.
        Room updated = new Room(room.id(), type, bed, room.status());
        rooms.set(rooms.indexOf(room), updated);
        System.out.println("Room updated: " + updated);
    }

    private void removeRoom() {
        Room room = requireRoom();
        LocalDate today = LocalDate.now(HOTEL_ZONE);

        boolean hasReservation = bookings.stream().anyMatch(booking ->
                booking.isActive() && booking.room.id().equals(room.id())
                        && booking.departure.isAfter(today));

        check(!hasReservation, "Room has an active or upcoming reservation.");
        rooms.remove(room);
        System.out.println("Room removed.");
    }

    private Guest findGuest(String id) {
        return guests.stream()
                .filter(guest -> guest.id().equalsIgnoreCase(id))
                .findFirst().orElse(null);
    }

    private void register() {
        String id = ask("NIC or passport");
        check(findGuest(id) == null, "Guest already registered.");

        String name = ask("Full name");
        String phone = ask("Contact number");
        String email = ask("Email address");

        check(phone.matches("\\+?[0-9][0-9 ()-]*")
                        && phone.replaceAll("\\D", "").length() >= 7,
                "Enter a valid contact number.");
        check(email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"),
                "Enter a valid email address.");

        guests.add(new Guest(id, name, phone, email));
        System.out.println("Guest registered.");
    }

    private List<Room> availableRooms(RoomType type, LocalDate start, LocalDate end,
                                      Booking bookingBeingChanged) {
        return rooms.stream()
                .filter(room -> room.type() == type && room.status() == Status.AVAILABLE)
                .filter(room -> bookings.stream().noneMatch(booking ->
                        booking != bookingBeingChanged
                                && booking.room.id().equals(room.id())
                                && booking.overlaps(start, end)))
                .toList();
    }

    private void searchRooms() {
        RoomType type = choose(RoomType.class);
        LocalDate start = date("Check-in");
        LocalDate end = date("Check-out");

        check(end.isAfter(start), "Check-out must follow check-in.");
        show(availableRooms(type, start, end, null));
    }

    private Booking readBookingDetails(String id, Guest guest, Booking existing) {
        RoomType type = choose(RoomType.class);

        int count;
        try {
            count = Integer.parseInt(ask("Number of guests"));
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Enter a whole number for the guest count.");
        }
        check(count > 0, "Enter at least one guest.");

        LocalDate start = date("Check-in");
        LocalDate end = date("Check-out");
        check(end.isAfter(start), "Check-out must follow check-in.");

        if (existing == null || !start.equals(existing.arrival)) {
            check(!start.isBefore(LocalDate.now(HOTEL_ZONE).plusDays(2)),
                    "Book at least two days in advance.");
        }

        List<Room> matches = availableRooms(type, start, end, existing);
        check(!matches.isEmpty(), "No available rooms for that type and date range.");
        show(matches);

        String roomId = ask("Room ID");
        Room room = matches.stream()
                .filter(match -> match.id().equalsIgnoreCase(roomId))
                .findFirst().orElse(null);
        check(room != null, "Choose an available room from the list.");

        return new Booking(id, guest, room, start, end, count);
    }

    private void book() {
        Guest guest = findGuest(ask("Guest NIC or passport"));
        check(guest != null, "Register the guest first.");

        String id = "B" + (bookings.size() + 1);
        Booking booking = readBookingDetails(id, guest, null);
        bookings.add(booking);
        System.out.printf("Booking %s confirmed. LKR %,d advance recorded.%n", id, ADVANCE);
    }

    private Booking requireBooking() {
        String id = ask("Booking ID");
        return bookings.stream()
                .filter(booking -> booking.id.equalsIgnoreCase(id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Booking ID not found."));
    }

    private void searchBookings() {
        String query = ask("Booking ID or guest name").toLowerCase(Locale.ROOT);

        List<Booking> matches = bookings.stream()
                .filter(booking -> booking.id.equalsIgnoreCase(query)
                        || booking.guest.name().toLowerCase(Locale.ROOT).contains(query))
                .toList();
        show(matches);
    }

    private void viewBookingsByCheckInDate() {
        LocalDate checkIn = date("Check-in");

        List<Booking> arrivals = bookings.stream()
                .filter(booking -> booking.isActive() && booking.arrival.equals(checkIn))
                .toList();
        show(arrivals);
    }

    private void cancelBooking() {
        Booking booking = requireBooking();
        booking.cancel();
        System.out.printf("Booking %s cancelled. Advance refund due: LKR %,d.%n",
                booking.id, ADVANCE);
    }

    private void modifyBooking() {
        Booking existing = requireBooking();
        existing.checkChangeAllowed();
        System.out.println("Current booking: " + existing);
        System.out.println("Enter the updated booking details.");

        Booking updated = readBookingDetails(existing.id, existing.guest, existing);
        existing.checkChangeAllowed();
        bookings.set(bookings.indexOf(existing), updated);
        System.out.println("Booking updated: " + updated);
    }

    private void generateInvoice() {
        Booking booking = requireBooking();
        check(booking.isActive(), "This booking was cancelled.");

        Room room = booking.room;
        RoomType type = room.type();
        Guest guest = booking.guest;

        long nights = ChronoUnit.DAYS.between(booking.arrival, booking.departure);
        int extraGuests = Math.max(0, booking.guests - type.capacity);

        BigDecimal base = BigDecimal.valueOf(nights * type.rate);
        BigDecimal extra = BigDecimal.valueOf((long) extraGuests * type.extraCharge);

        BigDecimal discount = BigDecimal.ZERO;
        if (nights >= 5) {
            discount = base.multiply(new BigDecimal("0.12"));
        }

        BigDecimal taxableAmount = base.subtract(discount).add(extra);
        BigDecimal tax = taxableAmount.multiply(BigDecimal.valueOf(type.tax, 2));
        BigDecimal total = taxableAmount.add(tax);
        BigDecimal advance = BigDecimal.valueOf(ADVANCE);
        BigDecimal balance = total.subtract(advance);

        System.out.println("\n=== SMARTSTAY INVOICE ===");
        System.out.println("Booking ID: " + booking.id);
        System.out.println("Guest: " + guest.name());
        System.out.println("NIC/Passport: " + guest.id());
        System.out.println("Contact: " + guest.phone() + " | Email: " + guest.email());
        System.out.printf("Room: %s | Type: %s | Bed: %s%n", room.id(), type, room.bed());
        System.out.printf("Daily rate: LKR %,d%n", type.rate);
        System.out.printf("Check-in: %s | Check-out: %s%n", booking.arrival, booking.departure);
        System.out.printf("Nights: %d | Guests: %d | Extra guests: %d%n",
                nights, booking.guests, extraGuests);

        printAmount("Base room charge", base);
        printAmount("Extra guest charge", extra);
        printAmount("Discount deducted", discount);
        printAmount("Tax (" + type.tax + "%)", tax);
        printAmount("Total before advance", total);
        printAmount("Advance deducted", advance);
        printAmount("Final payable", balance.max(BigDecimal.ZERO));

        if (balance.signum() < 0) {
            printAmount("Advance refund due", balance.negate());
        }
    }

    private static void printAmount(String label, BigDecimal amount) {
        System.out.printf(Locale.US, "%-23s LKR %,.2f%n", label, amount);
    }
}