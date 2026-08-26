package com.malrota.service.nlu;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationRuleExtractorTest {

    private final ConversationRuleExtractor extractor = new ConversationRuleExtractor();
    private final LocalDateTime base = LocalDateTime.of(2026, 8, 24, 10, 0);

    @Test
    void extracts_route_relative_date_and_accessibility_preferences() {
        var result = extractor.extract("내일 오전 서울에서 대전 가는데 다리가 불편해서 앞쪽 창가로 줘", base);

        assertThat(result.departure()).isEqualTo("서울");
        assertThat(result.arrival()).isEqualTo("대전");
        assertThat(result.date()).hasToString("2026-08-25");
        assertThat(result.timePreference()).isEqualTo("MORNING");
        assertThat(result.seatPreferences()).containsExactlyInAnyOrder("FRONT", "WINDOW");
        assertThat(result.accessibilityNeeds()).containsExactly("WALKING_DIFFICULTY");
    }

    @Test
    void resolves_relative_hour_and_explicit_seat_correction() {
        var timeResult = extractor.extract("3시간 뒤 부산 가는 버스", base);
        var seatResult = extractor.extract("창가 말고 통로로 바꿔줘", base);

        assertThat(timeResult.date()).hasToString("2026-08-24");
        assertThat(timeResult.departureTime()).hasToString("13:00");
        assertThat(seatResult.seatPreferenceMentioned()).isTrue();
        assertThat(seatResult.seatPreferences()).containsExactly("AISLE");
    }

    @Test
    void resolves_next_weekday_without_guessing_route() {
        var result = extractor.extract("다음 주 토요일 첫차로 둘이 갈게", base);

        assertThat(result.departure()).isNull();
        assertThat(result.arrival()).isNull();
        assertThat(result.date()).hasToString("2026-09-05");
        assertThat(result.servicePreference()).isEqualTo("FIRST");
        assertThat(result.passengers()).isEqualTo(2);
    }

    @Test
    void extracts_unique_dialect_and_accessibility_terms_without_python_server() {
        var result = extractor.extract("글피 꼭두새벽에 할멈하고 부산행 두 장, 메스꺼우니까네 중간 창가로 줘", base);

        assertThat(result.arrival()).isEqualTo("부산");
        assertThat(result.date()).hasToString("2026-08-27");
        assertThat(result.timePreference()).isEqualTo("MORNING");
        assertThat(result.servicePreference()).isEqualTo("FIRST");
        assertThat(result.passengers()).isEqualTo(2);
        assertThat(result.seatPreferences()).containsExactlyInAnyOrder("MIDDLE", "WINDOW");
        assertThat(result.accessibilityNeeds()).containsExactlyInAnyOrder("ELDERLY_CARE", "MOTION_SICKNESS");
    }

    @Test
    void extracts_sunset_and_fast_service_dialect_terms() {
        var result = extractor.extract("해 질 녘에 영감재이랑 대전행 제일 빠른 일반 버스", base);

        assertThat(result.arrival()).isEqualTo("대전");
        assertThat(result.timePreference()).isEqualTo("EVENING");
        assertThat(result.servicePreference()).isEqualTo("FIRST");
        assertThat(result.busGradePreference()).isEqualTo("GENERAL");
        assertThat(result.passengers()).isEqualTo(2);
        assertThat(result.accessibilityNeeds()).containsExactly("ELDERLY_CARE");
    }
}
