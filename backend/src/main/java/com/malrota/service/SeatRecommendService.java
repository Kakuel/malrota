package com.malrota.service;

import com.malrota.dto.request.SeatRecommendRequest;
import com.malrota.recommendation.MockSeatGenerator;
import com.malrota.recommendation.Seat;
import com.malrota.recommendation.SeatRecommendation;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class SeatRecommendService {

    private final MockSeatGenerator seatGenerator;

    public SeatRecommendService(MockSeatGenerator seatGenerator) {
        this.seatGenerator = seatGenerator;
    }

    public SeatRecommendation recommend(SeatRecommendRequest request) {
        List<Seat> seats = seatGenerator.generate(request.busGrade());

        List<String> access = request.accessibilityNeeds() != null
                ? request.accessibilityNeeds() : List.of();
        List<String> prefs = request.seatPreferences() != null
                ? request.seatPreferences() : List.of();
        boolean hasExplicitPosition = prefs.stream()
                .anyMatch(preference -> List.of("FRONT", "MIDDLE", "BACK").contains(preference));
        boolean allowsFrontAccessibilityBoost = !hasExplicitPosition || prefs.contains("FRONT");

        if (request.passengers() == 2) {
            SeatRecommendation pairRecommendation = recommendAdjacentPair(seats, prefs, access, allowsFrontAccessibilityBoost);
            if (pairRecommendation != null) return pairRecommendation;
        }

        int bestScore = -1;
        List<Seat> bestSeats = new ArrayList<>();   // 최고 점수 좌석들 (동률 포함)
        List<String> bestReasons = new ArrayList<>();

        // 좌석은 생성 순서(1A,1B,...앞줄·왼쪽 우선)대로 검사
        for (Seat seat : seats) {
            if (!seat.available()) {
                continue; // 예약된 좌석 제외
            }

            int score = 0;
            List<String> reasons = new ArrayList<>();

            // 사용자가 직접 말한 위치 선호를 우선한다.
            if (prefs.contains("FRONT") && seat.position().equals("FRONT")) {
                score += 6;
                reasons.add("앞쪽 좌석을 선호하셔서 앞쪽 좌석입니다.");
            }
            if (prefs.contains("MIDDLE") && seat.position().equals("MIDDLE")) {
                score += 6;
                reasons.add("중간 좌석을 선호하셔서 중간 좌석입니다.");
            }
            if (prefs.contains("BACK") && seat.position().equals("BACK")) {
                score += 6;
                reasons.add("뒷좌석을 선호하셔서 뒤쪽 좌석입니다.");
            }
            if (prefs.contains("AISLE") && seat.side().equals("AISLE")) {
                score += 4;
                reasons.add("통로를 선호하셔서 이동이 편한 통로 쪽 좌석입니다.");
            }
            if (prefs.contains("WINDOW") && seat.side().equals("WINDOW")) {
                score += 4;
                reasons.add("창가를 선호하셔서 창가 좌석입니다.");
            }

            // 교통약자·안전 관련 조건
            // 사용자가 앞·중간·뒤 위치를 직접 정했다면, 자동 접근성 규칙이 반대 위치를 이기지 않게 한다.
            if (access.contains("WALKING_DIFFICULTY") && allowsFrontAccessibilityBoost && seat.position().equals("FRONT")) {
                score += 5;
                reasons.add("다리가 불편하셔서 타고 내리기 쉬운 앞쪽 좌석입니다.");
            }
            if (access.contains("WALKING_DIFFICULTY") && seat.side().equals("AISLE")) {
                score += 3;
                reasons.add("이동이 편한 통로 쪽 좌석입니다.");
            }
            if (access.contains("MOTION_SICKNESS") && allowsFrontAccessibilityBoost && seat.position().equals("FRONT")) {
                score += 4;
                reasons.add("멀미가 있으셔서 흔들림이 적은 앞쪽 좌석입니다.");
            }
            if (access.contains("ELDERLY_CARE") && allowsFrontAccessibilityBoost && seat.position().equals("FRONT")) {
                score += 4;
                reasons.add("어르신이 이용하기 편하도록 앞쪽 좌석을 우선합니다.");
            }

            if (score > bestScore) {
                // 더 높은 점수 발견 → 새로 시작
                bestScore = score;
                bestSeats = new ArrayList<>();
                bestSeats.add(seat);
                bestReasons = reasons;
            } else if (score == bestScore) {
                // 동점 → 목록에 추가
                bestSeats.add(seat);
            }
        }

        // 예약 가능한 좌석이 아예 없는 경우
        if (bestSeats.isEmpty()) {
            return new SeatRecommendation(null, 0, List.of("예약 가능한 좌석이 없습니다."), List.of(), List.of());
        }

        // 첫 번째 = 대표 추천 (앞줄·왼쪽 우선), 나머지 = 동률 대안
        Seat bestSeat = bestSeats.get(0);
        List<Seat> alternatives = bestSeats.subList(1, bestSeats.size());

        if (bestReasons.isEmpty()) {
            bestReasons = new ArrayList<>();
            bestReasons.add("예약 가능한 좌석입니다.");
        }

        return new SeatRecommendation(bestSeat, bestScore, bestReasons, alternatives, seats);
    }

    private SeatRecommendation recommendAdjacentPair(List<Seat> seats, List<String> prefs, List<String> access,
                                                      boolean allowsFrontAccessibilityBoost) {
        int bestScore = -1;
        Seat first = null;
        Seat second = null;

        for (Seat left : seats) {
            if (!left.available()) continue;
            for (Seat right : seats) {
                if (!right.available() || left.row() != right.row() || right.column() != left.column() + 1) continue;

                int pairScore = scoreForPair(left, prefs, access, allowsFrontAccessibilityBoost)
                        + scoreForPair(right, prefs, access, allowsFrontAccessibilityBoost);
                if (pairScore > bestScore) {
                    bestScore = pairScore;
                    first = left;
                    second = right;
                }
            }
        }

        if (first == null || second == null) return null;

        Seat pairSeat = new Seat(first.seatNo() + ", " + second.seatNo(), first.row(), first.column(),
                first.position(), first.side(), true);
        List<String> reasons = new ArrayList<>();
        reasons.add("두 분이 함께 앉을 수 있도록 붙어 있는 좌석입니다.");
        if (prefs.contains("BACK") && first.position().equals("BACK")) {
            reasons.add("뒷좌석 선호를 반영했습니다.");
        } else if (prefs.contains("FRONT") && first.position().equals("FRONT")) {
            reasons.add("앞쪽 좌석 선호를 반영했습니다.");
        }
        return new SeatRecommendation(pairSeat, bestScore, reasons, List.of(), seats);
    }

    private int scoreForPair(Seat seat, List<String> prefs, List<String> access,
                             boolean allowsFrontAccessibilityBoost) {
        int score = 0;
        if (prefs.contains(seat.position())) score += 6;
        if (prefs.contains("AISLE") && seat.side().equals("AISLE")) score += 4;
        if (prefs.contains("WINDOW") && seat.side().equals("WINDOW")) score += 4;
        if (access.contains("WALKING_DIFFICULTY") && allowsFrontAccessibilityBoost && seat.position().equals("FRONT")) score += 5;
        if (access.contains("WALKING_DIFFICULTY") && seat.side().equals("AISLE")) score += 3;
        if (access.contains("MOTION_SICKNESS") && allowsFrontAccessibilityBoost && seat.position().equals("FRONT")) score += 4;
        if (access.contains("ELDERLY_CARE") && allowsFrontAccessibilityBoost && seat.position().equals("FRONT")) score += 4;
        return score;
    }
}
