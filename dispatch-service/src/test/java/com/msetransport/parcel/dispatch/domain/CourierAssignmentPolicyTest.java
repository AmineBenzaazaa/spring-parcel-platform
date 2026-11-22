package com.msetransport.parcel.dispatch.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CourierAssignmentPolicyTest {

    private static final Courier CASA_1 = new Courier(UUID.fromString("c0000000-0000-0000-0000-000000000001"), "Youssef", "p", "CASA", true);
    private static final Courier CASA_2 = new Courier(UUID.fromString("c0000000-0000-0000-0000-000000000002"), "Hamza", "p", "CASA", true);
    private static final Courier CASA_OFF = new Courier(UUID.fromString("c0000000-0000-0000-0000-000000000009"), "Off", "p", "CASA", false);
    private static final Courier RABAT = new Courier(UUID.fromString("c0000000-0000-0000-0000-000000000003"), "Imane", "p", "RABAT", true);
    private static final List<Courier> ALL = List.of(CASA_2, CASA_1, CASA_OFF, RABAT);

    @Test
    void picksTheLeastLoadedCourierInTheZone() {
        var chosen = CourierAssignmentPolicy.choose("CASA", ALL, Map.of(CASA_1.getId(), 5L, CASA_2.getId(), 2L));

        assertThat(chosen).contains(CASA_2);
    }

    @Test
    void tiesAreBrokenByIdForDeterminism() {
        var chosen = CourierAssignmentPolicy.choose("CASA", ALL, Map.of());

        assertThat(chosen).contains(CASA_1);
    }

    @Test
    void inactiveCouriersAreNeverChosen() {
        var chosen = CourierAssignmentPolicy.choose("CASA", List.of(CASA_OFF), Map.of());

        assertThat(chosen).isEmpty();
    }

    @Test
    void unservedZoneYieldsNoCourier() {
        assertThat(CourierAssignmentPolicy.choose("AGADIR", ALL, Map.of())).isEmpty();
        assertThat(CourierAssignmentPolicy.choose(null, ALL, Map.of())).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"Casablanca, CASA", "casa, CASA", "Mohammedia, CASA", "Salé, RABAT", " Rabat , RABAT",
            "Tanger, TANGIER", "Marrakech, MARRAKECH", "Kénitra, KENITRA"})
    void citiesResolveToZones(String city, String zone) {
        assertThat(Zones.forCity(city)).contains(zone);
    }

    @Test
    void unknownCityHasNoZone() {
        assertThat(Zones.forCity("Agadir")).isEmpty();
        assertThat(Zones.forCity(null)).isEmpty();
    }
}
