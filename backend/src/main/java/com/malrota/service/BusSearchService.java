package com.malrota.service;

import com.malrota.client.TagoClient;
import com.malrota.dto.request.BusSearchRequest;
import com.malrota.dto.response.BusRecommendation;
import com.malrota.dto.response.BusSchedule;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class BusSearchService {

    private final TagoClient tagoClient;

    public BusSearchService(TagoClient tagoClient) {
        this.tagoClient = tagoClient;
    }

    public List<BusSchedule> search(BusSearchRequest request) {
        // 1. 필수값(출발지, 도착지, 날짜) null 체크 방어 (14시 버스 에러 방지)
        if (request == null || !hasText(request.departure()) || !hasText(request.arrival()) || !hasText(request.date())) {
            return List.of();
        }

        // 2. 출발지·도착지 이름 → 터미널ID 변환
        String depId = tagoClient.findTerminalId(request.departure());
        String arrId = tagoClient.findTerminalId(request.arrival());
        if (depId == null || arrId == null) {
            return List.of();
        }

        // 3. 날짜 포맷 변환 (2026-08-24 → 20260824)
        String date = request.date().replace("-", "");

        // 4. 운행편 조회 후 등급 필터링 및 시간 조건 정렬
        List<BusSchedule> schedules = tagoClient.searchBuses(depId, arrId, date);
        return schedules.stream()
                .filter(schedule -> matchesGrade(schedule, request.busGradePreference()))
                .sorted(scheduleComparator(request))
                .toList();
    }
        /** 버스 3개 추천 (가장 가까운 시각 / 가장 저렴 / 근처 시각) */
    public List<BusRecommendation> recommend(BusSearchRequest request) {
        List<BusSchedule> schedules = search(request); // 조회+정렬 재활용
        List<BusRecommendation> result = new ArrayList<>();
        if (schedules.isEmpty()) return result;

        // 1. 가장 맞는 시각 (정렬 결과 첫 번째)
        BusSchedule best = schedules.get(0);
        result.add(new BusRecommendation(best, "말씀하신 시간과 가장 가까운 버스입니다.", "추천 시간"));

        // 2. 사용자가 특정 시각을 말했으면, 그 시각 이전 3시간 안에서만 최저가를 고른다.
        //    예: 21:00 요청 -> 18:00~21:00 출발편 중 최저가. 너무 이른 버스를 "최저가"로
        //    추천해 사용자의 출발 의도와 어긋나는 것을 막는다.
        List<BusSchedule> cheapestCandidates = cheapestCandidates(schedules, request);
        BusSchedule cheapest = cheapestCandidates.stream()
                .min(Comparator.comparingInt(BusSchedule::charge))
                .orElse(null);
        if (cheapest != null && !isSameBus(cheapest, best)) {
            String reason = hasText(request.departureTime())
                    ? "요청하신 시각 이전 3시간 안에서 가장 저렴한 버스입니다."
                    : "가장 저렴한 버스입니다.";
            result.add(new BusRecommendation(cheapest, reason, "최저가"));
        }

        // 3. 근처 시각 (1,2와 겹치지 않는 다음 버스)
        for (BusSchedule s : schedules) {
            if (!isSameBus(s, best) && (cheapest == null || !isSameBus(s, cheapest))) {
                result.add(new BusRecommendation(s, "비슷한 시간대의 다른 버스입니다.", "다른 시간"));
                break;
            }
        }
        return result;
    }

    private List<BusSchedule> cheapestCandidates(List<BusSchedule> schedules, BusSearchRequest request) {
        if (!hasText(request.departureTime())) return schedules;

        LocalTime requested = parseTime(request.departureTime());
        if (requested == null) return schedules;

        // 조회는 하루 단위이므로 자정을 넘는 이전 날짜 운행편까지는 포함하지 않는다.
        LocalTime start = requested.getHour() < 3 ? LocalTime.MIDNIGHT : requested.minusHours(3);
        return schedules.stream()
                .filter(schedule -> {
                    LocalTime departure = departureTime(schedule);
                    return !departure.isBefore(start) && !departure.isAfter(requested);
                })
                .toList();
    }

    private boolean isSameBus(BusSchedule a, BusSchedule b) {
        return a.routeId() != null && a.routeId().equals(b.routeId())
                && a.departureTime() != null && a.departureTime().equals(b.departureTime());
    }

    private Comparator<BusSchedule> scheduleComparator(BusSearchRequest request) {
        Comparator<BusSchedule> byDepartureTime = Comparator.comparing(this::departureTime);

        // 특정 시각(예: 12:00, 15:00, 16:00)이 지정된 경우
        if (hasText(request.departureTime())) {
            LocalTime requested = parseTime(request.departureTime());
            if (requested != null) {
                return Comparator.comparingInt((BusSchedule schedule) -> {
                    LocalTime dep = departureTime(schedule);
                    // 요청 시각 이전(예: 15:00 이전) 버스는 무조건 뒤로 보냄 (rank 1)
                    return dep.isBefore(requested) ? 1 : 0;
                }).thenComparing(byDepartureTime); // 15:00 이후 버스 중 가장 빠른 순서 정렬
            }
        }

        // 시간대(MORNING, AFTERNOON 등)가 지정된 경우
        Comparator<BusSchedule> byTimePreference = Comparator.comparingInt(
            (BusSchedule schedule) -> timePreferenceRank(departureTime(schedule), request.timePreference())
        );

        // 첫차 / 막차 정렬
        boolean isLast = "LAST".equalsIgnoreCase(request.servicePreference());
        Comparator<BusSchedule> secondarySort = isLast ? byDepartureTime.reversed() : byDepartureTime;

        return byTimePreference.thenComparing(secondarySort);
    }

    private boolean matchesGrade(BusSchedule schedule, String preference) {
        if (!hasText(preference) || "ANY".equalsIgnoreCase(preference)) return true;
        String grade = schedule.grade() == null ? "" : schedule.grade();
        return switch (preference.toUpperCase()) {
            case "EXCELLENT" -> grade.contains("우등");
            case "PREMIUM" -> grade.contains("프리미엄");
            case "GENERAL" -> grade.contains("일반") || grade.contains("고속");
            default -> true;
        };
    }

    private int timePreferenceRank(LocalTime departure, String preference) {
        if (!hasText(preference) || "ANY".equalsIgnoreCase(preference)) return 0;
        
        // 시간대 정의:
        // - MORNING   : 06:00 ~ 12:00 (새벽 심야 제외)
        // - AFTERNOON : 12:00 ~ 17:00
        // - EVENING   : 17:00 ~ 21:00
        // - NIGHT     : 21:00 ~ 24:00 또는 00:00 ~ 06:00
        boolean matches = switch (preference.toUpperCase()) {
            case "MORNING" -> !departure.isBefore(LocalTime.of(6, 0)) && departure.isBefore(LocalTime.NOON);
            case "AFTERNOON" -> !departure.isBefore(LocalTime.NOON) && departure.isBefore(LocalTime.of(17, 0));
            case "EVENING" -> !departure.isBefore(LocalTime.of(17, 0)) && departure.isBefore(LocalTime.of(21, 0));
            case "NIGHT" -> departure.isBefore(LocalTime.of(6, 0)) || !departure.isBefore(LocalTime.of(21, 0));
            default -> true;
        };
        return matches ? 0 : 1;
    }

    private LocalTime departureTime(BusSchedule schedule) {
        String value = schedule.departureTime();
        if (value == null || value.length() < 4) return LocalTime.MAX;
        
        // HH:mm 또는 HHmm 형식 모두 지원
        if (value.contains(":")) {
            LocalTime parsed = parseTime(value);
            return parsed == null ? LocalTime.MAX : parsed;
        }
        
        String clean = value.replaceAll("[^0-9]", "");
        if (clean.length() >= 4) {
            String timeStr = clean.substring(clean.length() - 4, clean.length() - 2) + ":" + clean.substring(clean.length() - 2);
            LocalTime parsed = parseTime(timeStr);
            return parsed == null ? LocalTime.MAX : parsed;
        }
        return LocalTime.MAX;
    }

    private LocalTime parseTime(String value) {
        try {
            return LocalTime.parse(value);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
