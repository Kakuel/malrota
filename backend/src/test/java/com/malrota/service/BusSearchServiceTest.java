package com.malrota.service;

import com.malrota.client.TagoClient;
import com.malrota.dto.request.BusSearchRequest;
import com.malrota.dto.response.BusRecommendation;
import com.malrota.dto.response.BusSchedule;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BusSearchServiceTest {

    @Test
    void places_the_bus_closest_to_requested_departure_time_first() {
        TagoClient client = new TagoClient(null) {
            @Override
            public String findTerminalId(String terminalName) {
                return terminalName;
            }

            @Override
            public List<BusSchedule> searchBuses(String depId, String arrId, String date) {
                return List.of(
                        schedule("R01", "202608250600"),
                        schedule("R02", "202608251900"),
                        schedule("R03", "202608252100")
                );
            }
        };

        BusSearchService service = new BusSearchService(client);
        List<BusSchedule> result = service.search(new BusSearchRequest(
                "서울", "대전", "2026-08-25", "21:00", "NIGHT", "ANY", "ANY"));

        assertThat(result).first().extracting(BusSchedule::departureTime).isEqualTo("202608252100");
    }

    @Test
    void recommends_cheapest_bus_only_within_three_hours_before_requested_time() {
        TagoClient client = new TagoClient(null) {
            @Override
            public String findTerminalId(String terminalName) {
                return terminalName;
            }

            @Override
            public List<BusSchedule> searchBuses(String depId, String arrId, String date) {
                return List.of(
                        schedule("R01", "202608250600", 9000),
                        schedule("R02", "202608251800", 17000),
                        schedule("R03", "202608251930", 18000),
                        schedule("R04", "202608252100", 20000),
                        schedule("R05", "202608252200", 8000)
                );
            }
        };

        BusSearchService service = new BusSearchService(client);
        List<BusRecommendation> result = service.recommend(new BusSearchRequest(
                "서울", "대전", "2026-08-25", "21:00", "NIGHT", "ANY", "ANY"));

        assertThat(result).filteredOn(recommendation -> "최저가".equals(recommendation.label()))
                .singleElement()
                .extracting(recommendation -> recommendation.bus().departureTime())
                .isEqualTo("202608251800");
    }

    private static BusSchedule schedule(String routeId, String departureTime) {
        return schedule(routeId, departureTime, 16000);
    }

    private static BusSchedule schedule(String routeId, String departureTime, int charge) {
        return new BusSchedule(routeId, "우등", "서울", "대전", departureTime, departureTime, charge);
    }
}
