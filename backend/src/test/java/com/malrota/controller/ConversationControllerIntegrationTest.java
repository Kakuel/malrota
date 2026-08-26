package com.malrota.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.containsInAnyOrder;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "watsonx.enabled=false")
class ConversationControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void accumulates_conditions_across_requests_and_returns_clarification() throws Exception {
        String sessionId = "nlu-integration-test";

        mockMvc.perform(post("/api/conversation/parse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"서울경부에서 대전복합 가고 싶어요\",\"sessionId\":\"" + sessionId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("COLLECTING_CONDITIONS"))
                .andExpect(jsonPath("$.departure").value("서울경부"))
                .andExpect(jsonPath("$.arrival").value("대전복합"))
                .andExpect(jsonPath("$.clarificationPrompt").value("언제 출발하시나요? 날짜와 함께 '오전 9시', '오후 3시'처럼 정확한 출발 시각을 말씀해 주세요."));

        mockMvc.perform(post("/api/conversation/parse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"내일 오전에 다리가 불편해서 앞쪽 통로로 갈게요\",\"sessionId\":\"" + sessionId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("COLLECTING_CONDITIONS"))
                .andExpect(jsonPath("$.date").isNotEmpty())
                .andExpect(jsonPath("$.timePreference").value("MORNING"))
                .andExpect(jsonPath("$.seatPreferences").value(containsInAnyOrder("FRONT", "AISLE")))
                .andExpect(jsonPath("$.accessibilityNeeds[0]").value("WALKING_DIFFICULTY"))
                .andExpect(jsonPath("$.clarificationPrompt").value("정확히 몇 시에 출발하시나요? '오전 9시', '오후 3시 30분'처럼 시각을 말씀해 주세요."));

        mockMvc.perform(post("/api/conversation/parse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"오전 9시에 탈게요\",\"sessionId\":\"" + sessionId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("COLLECTING_CONDITIONS"))
                .andExpect(jsonPath("$.departureTime").value("09:00"))
                .andExpect(jsonPath("$.clarificationPrompt").value(org.hamcrest.Matchers.containsString("몇 분")));

        mockMvc.perform(post("/api/conversation/parse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"혼자 갈게요\",\"sessionId\":\"" + sessionId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("READY_TO_SEARCH"))
                .andExpect(jsonPath("$.passengers").value(1));

        mockMvc.perform(post("/api/conversation/parse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"뒷좌석으로 바꿔줘\",\"sessionId\":\"" + sessionId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seatPreferences").value(containsInAnyOrder("BACK")));
    }

    @Test
    void asks_for_a_specific_terminal_in_multi_terminal_city_and_accepts_follow_up() throws Exception {
        String sessionId = "terminal-selection-test";

        mockMvc.perform(post("/api/conversation/parse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"서울에서 대전복합 가고 싶어요 내일 오전 9시 혼자\",\"sessionId\":\"" + sessionId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("COLLECTING_CONDITIONS"))
                .andExpect(jsonPath("$.clarificationPrompt").value(org.hamcrest.Matchers.containsString("서울경부")));

        mockMvc.perform(post("/api/conversation/parse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"강남\",\"sessionId\":\"" + sessionId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.departure").value("서울경부"))
                .andExpect(jsonPath("$.state").value("READY_TO_SEARCH"))
                .andExpect(jsonPath("$.clarificationPrompt").value(org.hamcrest.Matchers.nullValue()));
    }
}
