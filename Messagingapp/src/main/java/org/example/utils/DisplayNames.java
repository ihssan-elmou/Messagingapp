package org.example.utils;

/** Affichage des noms (comptes internes type {@code laila#2} → {@code laila}). */
public final class DisplayNames {

    private DisplayNames() {}

    public static String toDisplay(String username) {
        if (username == null || username.isBlank()) return "";
        int hash = username.indexOf('#');
        return hash > 0 ? username.substring(0, hash) : username;
    }
}
