package com.malrota.service;

import com.malrota.dto.request.SeatRecommendRequest;
import com.malrota.recommendation.MockSeatGenerator;
import com.malrota.recommendation.Seat;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SeatRecommendServiceTest {

    @Test
    void honors_explicit_back_seat_preference() {
        MockSeatGenerator generator = new MockSeatGenerator() {
            @Override
            public List<Seat> generate(String grade) {
                return List.of(
                        new Seat("1A", 1, 1, "FRONT", "WINDOW", true),
                        new Seat("8B", 8, 2, "BACK", "AISLE", true)
                );
            }
        };
        SeatRecommendService service = new SeatRecommendService(generator);

        var result = service.recommend(new SeatRecommendRequest("우등", List.of("BACK"), List.of(), 1));

        assertThat(result.bestSeat().seatNo()).isEqualTo("8B");
        assertThat(result.reasons()).contains("뒷좌석을 선호하셔서 뒤쪽 좌석입니다.");
    }

    @Test
    void keeps_explicit_back_preference_over_walking_difficulty_front_bonus() {
        MockSeatGenerator generator = new MockSeatGenerator() {
            @Override
            public List<Seat> generate(String grade) {
                return List.of(
                        new Seat("1B", 1, 2, "FRONT", "AISLE", true),
                        new Seat("8A", 8, 1, "BACK", "WINDOW", true)
                );
            }
        };
        SeatRecommendService service = new SeatRecommendService(generator);

        var result = service.recommend(new SeatRecommendRequest(
                "우등", List.of("BACK"), List.of("WALKING_DIFFICULTY"), 1));

        assertThat(result.bestSeat().seatNo()).isEqualTo("8A");
    }

    @Test
    void recommends_adjacent_back_seats_for_two_passengers() {
        MockSeatGenerator generator = new MockSeatGenerator() {
            @Override
            public List<Seat> generate(String grade) {
                return List.of(
                        new Seat("1A", 1, 1, "FRONT", "WINDOW", true),
                        new Seat("1B", 1, 2, "FRONT", "AISLE", true),
                        new Seat("8A", 8, 1, "BACK", "WINDOW", true),
                        new Seat("8B", 8, 2, "BACK", "AISLE", true)
                );
            }
        };
        SeatRecommendService service = new SeatRecommendService(generator);

        var result = service.recommend(new SeatRecommendRequest("우등", List.of("BACK"), List.of(), 2));

        assertThat(result.bestSeat().seatNo()).isEqualTo("8A, 8B");
        assertThat(result.reasons()).contains("두 분이 함께 앉을 수 있도록 붙어 있는 좌석입니다.");
    }
}
