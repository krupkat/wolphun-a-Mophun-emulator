import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Finds ASCII M001/M002 validity-date fields; optionally patches the expiry year.
 * Field meanings are inferred from existing ROM metadata, not an official spec.
 * Encrypted/compressed metadata is not decoded; absence is not proof of no expiry.
 *
 * javac -d /tmp/mpn-dates desktop/tools/MpnDates.java
 * java -cp /tmp/mpn-dates MpnDates "games/Synergenix Rally.mpn"
 * java -cp /tmp/mpn-dates MpnDates games > dates.txt
 * java -cp /tmp/mpn-dates MpnDates --unexpire "games/Synergenix Rally.mpn"
 * --unexpire accepts one file, sets M002's year to 2999, and creates file.mpn.bak.
 * An existing backup is never overwritten. Already-patched files are unchanged.
 */
public final class MpnDates {
    private static final Pattern FIELD = Pattern.compile("M00([12])([0-9]{8})(?![0-9])");

    public static final class DateField {
        public final String tag;
        public final LocalDate date;
        public final int offset;

        DateField(String tag, LocalDate date, int offset) {
            this.tag = tag;
            this.date = date;
            this.offset = offset;
        }

        @Override public String toString() {
            return String.format(Locale.ROOT, "%s (%s) = %s [date offset 0x%X]",
                tag, tag.equals("M001") ? "likely valid from" : "likely expires",
                date, offset);
        }
    }

    public static List<DateField> findDates(byte[] bytes) {
        // ISO-8859-1 maps one byte to one character, preserving file offsets.
        Matcher matcher = FIELD.matcher(new String(bytes, StandardCharsets.ISO_8859_1));
        List<DateField> result = new ArrayList<>();
        while (matcher.find()) {
            String raw = matcher.group(2);
            try {
                LocalDate date = LocalDate.of(Integer.parseInt(raw.substring(0, 4)),
                    Integer.parseInt(raw.substring(4, 6)), Integer.parseInt(raw.substring(6, 8)));
                if (date.getYear() > 0) {
                    result.add(new DateField("M00" + matcher.group(1), date, matcher.start(2)));
                }
            } catch (DateTimeException invalidDate) {
                // Reject impossible dates rather than normalizing them.
            }
        }
        return result;
    }

    public static void unexpire(Path file) throws IOException {
        if (!Files.isRegularFile(file)) throw new IOException("Expected a regular MPN file: " + file);
        byte[] original = Files.readAllBytes(file);
        byte[] patched = original.clone();
        int count = 0;
        boolean hasExpiry = false;
        for (DateField field : findDates(original)) {
            if (!field.tag.equals("M002")) continue;
            hasExpiry = true;
            if (field.date.getYear() == 2999) continue;
            try {
                LocalDate.of(2999, field.date.getMonthValue(), field.date.getDayOfMonth());
            } catch (DateTimeException invalidDate) {
                throw new IOException("Cannot preserve month/day in year 2999: " + field.date);
            }
            byte[] year = "2999".getBytes(StandardCharsets.US_ASCII);
            System.arraycopy(year, 0, patched, field.offset, year.length);
            count++;
        }
        if (!hasExpiry) throw new IOException("No readable, valid M002 expiry field found: " + file);
        if (count == 0) {
            System.out.println("Already unexpired; no changes: " + file);
            return;
        }
        Path backup = file.resolveSibling(file.getFileName().toString() + ".bak");
        // Reuse an identical original backup, but never overwrite a different one.
        if (Files.exists(backup)) {
            if (!java.util.Arrays.equals(original, Files.readAllBytes(backup))) {
                throw new IOException("Existing backup differs from current original: " + backup);
            }
        } else {
            Files.copy(file, backup);
        }
        Files.write(file, patched);
        System.out.println("Patched " + count + " M002 year(s) to 2999: " + file);
        System.out.println("Original backup: " + backup);
    }

    public static void main(String[] args) throws IOException {
        boolean patch = args.length == 2 && args[0].equals("--unexpire");
        if ((!patch && args.length != 1) || (args.length == 1 && args[0].startsWith("--"))) {
            System.err.println("Usage: java MpnDates <file.mpn | directory>\n"
                + "       java MpnDates --unexpire <file.mpn>");
            System.exit(2);
        }
        Path input = Paths.get(args[patch ? 1 : 0]);
        if (patch) unexpire(input);
        List<Path> files = new ArrayList<>();
        if (Files.isDirectory(input)) {
            try (Stream<Path> paths = Files.walk(input)) {
                paths.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".mpn"))
                    .forEach(files::add);
            }
        } else {
            files.add(input);
        }
        files.sort(Comparator.comparing(Path::toString));
        System.out.println("MPN ASCII validity-date scan (no decryption/decompression)");
        System.out.println("M001 = likely validity start; M002 = likely expiry. Meanings inferred, not verified for every game.");
        System.out.println("No fields found does NOT prove that a game has no expiry. Offsets are zero-based byte offsets.");
        System.out.println();
        int found = 0, errors = 0;
        for (Path file : files) {
            System.out.println(file);
            try {
                List<DateField> dates = findDates(Files.readAllBytes(file));
                if (dates.isEmpty()) {
                    System.out.println("  No readable M001/M002 date fields found.");
                } else {
                    found++;
                    for (DateField field : dates) System.out.println("  " + field);
                }
            } catch (IOException error) {
                errors++;
                System.out.println("  ERROR: " + error.getMessage());
            }
            System.out.println();
        }
        System.out.printf(Locale.ROOT, "Summary: %d files; %d with date fields; %d without date fields; %d errors.%n",
            files.size(), found, files.size() - found - errors, errors);
        if (errors != 0) System.exit(1);
    }
}
