package com.benzaazaa.parcel.dispatch.domain;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** City -> delivery zone. Each zone is served by its own courier pool from a local hub. */
public final class Zones {

    private static final Map<String, String> CITY_TO_ZONE = Map.ofEntries(
            Map.entry("casablanca", "CASA"), Map.entry("casa", "CASA"), Map.entry("mohammedia", "CASA"),
            Map.entry("rabat", "RABAT"), Map.entry("sale", "RABAT"), Map.entry("temara", "RABAT"),
            Map.entry("kenitra", "KENITRA"),
            Map.entry("marrakech", "MARRAKECH"), Map.entry("marrakesh", "MARRAKECH"),
            Map.entry("tangier", "TANGIER"), Map.entry("tanger", "TANGIER"));

    private Zones() {
    }

    public static Optional<String> forCity(String city) {
        if (city == null) {
            return Optional.empty();
        }
        String normalised = Normalizer.normalize(city.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")  // Salé -> Sale
                .toLowerCase(Locale.ROOT);
        return Optional.ofNullable(CITY_TO_ZONE.get(normalised));
    }
}
