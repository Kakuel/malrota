    @Test
    void combines_categories_when_the_only_other_bus_is_later_than_the_allowed_window() {
        TagoClient client = new TagoClient(null) {
            @Override public String findTerminalId(String terminalName) { return terminalName; }

            @Override public List<BusSchedule> searchBuses(String depId, String arrId, String date) {
                return List.of(
                        schedule("CLOSE", "202608251930", 16_000), // 요청 시각 30분 전, 허용 범위
                        schedule("FAR", "202608252100", 16_000)    // 1시간 후, 허용 범위 밖
                );
            }
        };

        BusSearchService service = new BusSearchService(client);
        List<BusRecommendation> recs = service.recommend(new BusSearchRequest(
                "서울", "대전", "2026-08-25", "20:00", "ANY", "ANY", "ANY"));

        assertThat(recs).hasSize(1);
        assertThat(recs.get(0).bus().routeId()).isEqualTo("CLOSE");
        assertThat(recs.get(0).labels()).containsExactlyInAnyOrder("최저가", "가까운 시간");
    }

}
