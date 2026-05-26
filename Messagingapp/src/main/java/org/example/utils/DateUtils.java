package org.example.utils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class DateUtils {
    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final DateTimeFormatter SHORT =
            DateTimeFormatter.ofPattern("HH:mm");

    public static String format(LocalDateTime dt) {
        return dt != null ? dt.format(FORMATTER) : "";
    }

    public static String formatShort(LocalDateTime dt) {
        return dt != null ? dt.format(SHORT) : "";
    }

    public static String now() {
        return format(LocalDateTime.now());
    }
}