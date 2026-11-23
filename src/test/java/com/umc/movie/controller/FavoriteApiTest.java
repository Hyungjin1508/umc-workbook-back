// src/test/java/com/umc/movie/controller/FavoriteApiTest.java
package com.umc.movie.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.umc.movie.dto.CreateFavoriteRequest;
import com.umc.movie.entity.Favorite;
import com.umc.movie.entity.Member;
import com.umc.movie.repository.FavoriteRepository;
import com.umc.movie.repository.MemberRepository;
import com.umc.movie.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest        // 진짜 서버를 띄우듯 모든 Bean을 올립니다
@AutoConfigureMockMvc  // HTTP 요청을 흉내 내는 MockMvc를 준비합니다
@Transactional         // 테스트가 끝나면 저장한 데이터를 되돌립니다
class FavoriteApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper; // 객체를 JSON 문자열로 바꿔줍니다

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private FavoriteRepository favoriteRepository;

    @Autowired
    private JwtUtil jwtUtil;

    private Member member;
    private String authorizationHeader;

    @BeforeEach
    void setUp() {
        // 로그인한 회원이 있어야 하므로 회원을 한 명 저장하고 토큰을 직접 발급합니다.
        member = memberRepository.save(Member.builder()
                .email("challenger@umc.com")
                .password("$2a$10$abcdefghijklmnopqrstuv") // 로그인 API를 거치지 않으므로 아무 값이나 괜찮습니다
                .nickname("영화조아")
                .build());

        authorizationHeader = "Bearer " + jwtUtil.createAccessToken(member.getMemberId(), member.getEmail());
    }

    @Test
    @DisplayName("토큰 없이 즐겨찾기를 등록하면 401과 AUTH401_2를 응답한다")
    void createFavoriteWithoutToken() throws Exception {
        mockMvc.perform(post("/favorites")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateFavoriteRequest(550L))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("AUTH401_2"));
    }

    @Test
    @DisplayName("토큰을 담아 즐겨찾기를 등록하면 201과 저장된 영화 ID를 응답한다")
    void createFavorite() throws Exception {
        mockMvc.perform(post("/favorites")
                        .header(HttpHeaders.AUTHORIZATION, authorizationHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateFavoriteRequest(550L))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("COMMON201"))
                .andExpect(jsonPath("$.result.movieId").value(550));
    }

    @Test
    @DisplayName("이미 즐겨찾기한 영화를 다시 등록하면 409와 FAVORITE409_1을 응답한다")
    void createFavoriteWhenDuplicated() throws Exception {
        favoriteRepository.save(Favorite.builder().member(member).movieId(550L).build());

        mockMvc.perform(post("/favorites")
                        .header(HttpHeaders.AUTHORIZATION, authorizationHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateFavoriteRequest(550L))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FAVORITE409_1"));
    }

    @Test
    @DisplayName("즐겨찾기하지 않은 영화의 여부를 조회하면 200과 isFavorite false를 응답한다")
    void getFavoriteStatus() throws Exception {
        mockMvc.perform(get("/favorites/550")
                        .header(HttpHeaders.AUTHORIZATION, authorizationHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.isFavorite").value(false));
    }

    @Test
    @DisplayName("Swagger 문서는 공개되어 있고 필수 API의 설명과 토큰 필요 여부, 실패 코드가 표시된다")
    void apiDocsIsPublic() throws Exception {
        String document = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode paths = objectMapper.readTree(document).path("paths");

        String[][] securedEndpoints = {
                {"/ratings", "post"},
                {"/ratings/{ratingId}", "patch"},
                {"/ratings/{ratingId}", "delete"},
                {"/members/me", "get"},
                {"/members/me", "patch"},
                {"/favorites", "post"},
                {"/favorites", "get"},
                {"/favorites/{movieId}", "get"},
                {"/favorites/{movieId}", "delete"},
                {"/movie-records", "post"}
        };
        for (String[] endpoint : securedEndpoints) {
            JsonNode operation = paths.path(endpoint[0]).path(endpoint[1]);
            assertThat(operation.path("summary").asText()).as("%s %s 설명", endpoint[1], endpoint[0]).isNotBlank();
            assertThat(operation.path("description").asText()).contains("토큰");
            assertThat(operation.path("security").toString()).contains("bearerAuth");
            // 토큰에서 꺼내는 내부 회원 ID를 사용자가 입력하도록 노출하지 않습니다.
            for (JsonNode parameter : operation.path("parameters")) {
                assertThat(parameter.path("name").asText()).isNotEqualTo("memberId");
            }
            assertThat(operation.path("responses").path("401").path("description").asText())
                    .contains("AUTH401_2", "AUTH401_3", "AUTH401_4");
        }

        JsonNode publicRatings = paths.path("/members/{memberId}/ratings").path("get");
        assertThat(publicRatings.path("summary").asText()).isNotBlank();
        assertThat(publicRatings.path("description").asText()).contains("토큰 없이");
        assertThat(publicRatings.path("security").toString()).doesNotContain("bearerAuth");
        assertThat(paths.path("/members").path("post").path("summary").asText()).isEqualTo("회원가입");
        assertThat(paths.path("/members/me").path("get").path("summary").asText()).isEqualTo("내 정보 조회");
        assertThat(paths.path("/ratings").path("post").path("responses").path("409").path("description").asText())
                .contains("RATING409_1");
        assertThat(paths.path("/ratings/{ratingId}").path("patch").path("responses").path("403").path("description").asText())
                .contains("RATING403_1");
        assertThat(paths.path("/ratings/{ratingId}").path("delete").path("responses").path("403").path("description").asText())
                .contains("RATING403_1");
        assertThat(paths.path("/favorites/{movieId}").path("delete").path("responses").path("404").path("description").asText())
                .contains("FAVORITE404_1");
    }
}
