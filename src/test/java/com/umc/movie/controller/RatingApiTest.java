package com.umc.movie.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.umc.movie.dto.CreateRatingRequest;
import com.umc.movie.entity.Member;
import com.umc.movie.repository.MemberRepository;
import com.umc.movie.repository.RatingRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional // 각 테스트가 저장한 H2 데이터는 테스트 종료 시 롤백합니다.
class RatingApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private RatingRepository ratingRepository;

    @Autowired
    private JwtUtil jwtUtil;

    private Member member;
    private String authorizationHeader;

    @BeforeEach
    void setUp() {
        member = memberRepository.save(Member.builder()
                .email("rating-api@example.com")
                .password("api-test-password-not-used") // 로그인은 생략하고 테스트용 JWT를 직접 발급합니다.
                .nickname("평점테스터")
                .build());
        authorizationHeader = "Bearer " + jwtUtil.createAccessToken(member.getMemberId(), member.getEmail());
    }

    @Test
    @DisplayName("토큰 없이 POST /ratings를 호출하면 401과 AUTH401_2를 응답하고 저장하지 않는다")
    void createRatingWithoutToken() throws Exception {
        mockMvc.perform(post("/ratings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateRatingRequest(550L, 4, "재미있게 봤습니다."))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("AUTH401_2"))
                .andExpect(jsonPath("$.result").value(nullValue()));

        assertThat(ratingRepository.findByMember_MemberIdOrderByRatingIdDesc(member.getMemberId())).isEmpty();
    }

    @Test
    @DisplayName("토큰을 담아 POST /ratings를 호출하면 201과 COMMON201을 응답하고 토큰 회원의 평점으로 저장한다")
    void createRatingWithToken() throws Exception {
        mockMvc.perform(post("/ratings")
                        .header(HttpHeaders.AUTHORIZATION, authorizationHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateRatingRequest(550L, 4, "재미있게 봤습니다."))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("COMMON201"))
                .andExpect(jsonPath("$.result.ratingId").isNumber())
                .andExpect(jsonPath("$.result.movieId").value(550))
                .andExpect(jsonPath("$.result.score").value(4))
                .andExpect(jsonPath("$.result.comment").value("재미있게 봤습니다."))
                .andExpect(jsonPath("$.result.createdAt").isString())
                .andExpect(jsonPath("$.result.updatedAt").isString());

        // 요청 Body에 memberId를 보내지 않아도 토큰의 회원에게 저장됐는지 확인합니다.
        assertThat(ratingRepository.findByMember_MemberIdOrderByRatingIdDesc(member.getMemberId()))
                .singleElement()
                .satisfies(saved -> {
                    assertThat(saved.getMember().getMemberId()).isEqualTo(member.getMemberId());
                    assertThat(saved.getMovieId()).isEqualTo(550L);
                    assertThat(saved.getScore()).isEqualTo(4);
                    assertThat(saved.getComment()).isEqualTo("재미있게 봤습니다.");
                });
    }
}
