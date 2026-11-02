package com.umc.movie;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.umc.movie.entity.Member;
import com.umc.movie.entity.Rating;
import com.umc.movie.repository.MemberRepository;
import com.umc.movie.repository.RatingRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// 실제 Controller/Service/Repository를 사용하되, DB만 메모리 H2로 대체합니다.
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:standard_mission;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
@Transactional
class StandardMissionIntegrationTests {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private MemberRepository members;
    @Autowired private RatingRepository ratings;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    void signUpReturns201WithoutPasswordAndStoresBcryptHash() throws Exception {
        JsonNode response = json(signUp("first@example.com", "첫번째회원")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.result.password").doesNotExist()));

        Member saved = members.findById(response.at("/result/memberId").asLong()).orElseThrow();
        assertThat(saved.getPassword()).isNotEqualTo("TestPass1!").startsWith("$2");
        assertThat(passwordEncoder.matches("TestPass1!", saved.getPassword())).isTrue();
    }

    @Test
    void duplicateEmailAndNicknameReturn409WithoutSavingExtraMembers() throws Exception {
        signUp("first@example.com", "첫번째회원").andExpect(status().isCreated());
        signUp("first@example.com", "다른닉네임")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COMMON4092"))
                .andExpect(jsonPath("$.isSuccess").value(false));
        signUp("second@example.com", "첫번째회원")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COMMON4092"))
                .andExpect(jsonPath("$.isSuccess").value(false));
        assertThat(members.count()).isEqualTo(1);
    }

    @Test
    void invalidEmailPasswordAndNicknameReturnConsistent400() throws Exception {
        for (Map<String, Object> body : java.util.List.of(
                Map.<String, Object>of("email", "invalid-email", "password", "TestPass1!", "nickname", "테스트회원"),
                Map.<String, Object>of("email", "test@example.com", "password", "weak", "nickname", "테스트회원"),
                Map.<String, Object>of("email", "test@example.com", "password", "TestPass1!", "nickname", "한"))) {
            mvc.perform(post("/members").contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(body)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.isSuccess").value(false))
                    .andExpect(jsonPath("$.code").value("COMMON4001"))
                    .andExpect(jsonPath("$.message").isString());
        }
        assertThat(members.count()).isZero();
    }

    @Test
    void createUpdateAndDeleteWorkThroughHttpAndDatabase() throws Exception {
        long memberId = newMember("first@example.com", "첫번째회원");
        long ratingId = createRating(memberId);
        assertThat(ratings.findById(ratingId).orElseThrow().getScore()).isEqualTo(4);

        mvc.perform(patch(ratingPath(memberId, ratingId)).contentType(MediaType.APPLICATION_JSON)
                        .content(""" 
                                {"score":5,"comment":"수정한 후기"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true));
        mvc.perform(get("/members/{id}/ratings", memberId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ratingId").value(ratingId))
                .andExpect(jsonPath("$[0].score").value(5))
                .andExpect(jsonPath("$[0].comment").value("수정한 후기"));

        mvc.perform(delete(ratingPath(memberId, ratingId)))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        assertThat(ratings.existsById(ratingId)).isFalse();
        mvc.perform(get("/members/{id}/ratings", memberId))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    @Test
    void duplicateRatingReturns409AndOnlyOneRowExists() throws Exception {
        long memberId = newMember("first@example.com", "첫번째회원");
        createRating(memberId);
        postRating(memberId, 4)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COMMON4092"))
                .andExpect(jsonPath("$.isSuccess").value(false));
        assertThat(ratings.findByMember_MemberIdOrderByRatingIdDesc(memberId)).hasSize(1);
    }

    @Test
    void databaseUniqueConstraintRejectsDuplicateEvenWithoutServiceCheck() throws Exception {
        long memberId = newMember("first@example.com", "첫번째회원");
        createRating(memberId);
        Member member = members.findById(memberId).orElseThrow();
        assertThatThrownBy(() -> ratings.saveAndFlush(new Rating(member, 98765L, 3, "중복")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void outOfRangeScoresReturn400AndDoNotSave() throws Exception {
        long memberId = newMember("first@example.com", "첫번째회원");
        for (int score : new int[]{0, 6}) {
            postRating(memberId, score)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.isSuccess").value(false))
                    .andExpect(jsonPath("$.code").value("COMMON4001"));
        }
        assertThat(ratings.count()).isZero();
    }

    @Test
    void otherMembersCannotUpdateOrDeleteAndOriginalDataStays() throws Exception {
        long ownerId = newMember("first@example.com", "첫번째회원");
        long otherId = newMember("second@example.com", "두번째회원");
        long ratingId = createRating(ownerId);
        mvc.perform(patch(ratingPath(otherId, ratingId)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"score\":1,\"comment\":\"변조 시도\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COMMON403"));
        mvc.perform(delete(ratingPath(otherId, ratingId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COMMON403"));
        assertThat(ratings.findById(ratingId).orElseThrow().getScore()).isEqualTo(4);
    }

    @Test
    void missingRatingReturns404ForBothUpdateAndDelete() throws Exception {
        long memberId = newMember("first@example.com", "첫번째회원");
        mvc.perform(patch(ratingPath(memberId, Long.MAX_VALUE)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"score\":3,\"comment\":\"없는 평점\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COMMON404"));
        mvc.perform(delete(ratingPath(memberId, Long.MAX_VALUE)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COMMON404"));
    }

    private ResultActions signUp(String email, String nickname) throws Exception {
        return mvc.perform(post("/members").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of(
                        "email", email, "password", "TestPass1!", "nickname", nickname))));
    }

    private long newMember(String email, String nickname) throws Exception {
        return json(signUp(email, nickname).andExpect(status().isCreated()))
                .at("/result/memberId").asLong();
    }

    private ResultActions postRating(long memberId, int score) throws Exception {
        return mvc.perform(post("/members/{id}/ratings", memberId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of(
                        "movieId", 98765, "score", score, "comment", "등록한 후기"))));
    }

    private long createRating(long memberId) throws Exception {
        return json(postRating(memberId, 4).andExpect(status().isCreated())).path("result").asLong();
    }

    private JsonNode json(ResultActions result) throws Exception {
        return mapper.readTree(result.andReturn().getResponse().getContentAsByteArray());
    }

    private String ratingPath(long memberId, long ratingId) {
        return "/members/" + memberId + "/ratings/" + ratingId;
    }
}
