package com.umc.movie.service;

import com.umc.movie.dto.CreateRatingRequest;
import com.umc.movie.dto.UpdateRatingRequest;
import com.umc.movie.entity.Member;
import com.umc.movie.entity.Rating;
import com.umc.movie.exception.ApiException;
import com.umc.movie.repository.MemberRepository;
import com.umc.movie.repository.RatingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RatingServiceTest {

    @Mock
    private RatingRepository ratingRepository;

    @Mock
    private MemberRepository memberRepository;

    @InjectMocks
    private RatingService ratingService;

    private Member member;

    @BeforeEach
    void setUp() {
        member = Member.builder()
                .email("rating-unit@example.com")
                .password("unit-test-password-not-used")
                .nickname("평점작성자")
                .build();
        // DB를 사용하지 않는 단위 테스트이므로 회원 ID를 직접 지정합니다.
        ReflectionTestUtils.setField(member, "memberId", 1L);
    }

    @Test
    @DisplayName("같은 영화에 평점을 중복 등록하면 409와 RATING409_1 예외를 던지고 저장하지 않는다")
    void createRatingWhenDuplicated() {
        // given: 회원이 같은 영화에 이미 평점을 남겼습니다.
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));
        given(ratingRepository.existsByMember_MemberIdAndMovieId(1L, 550L)).willReturn(true);

        // when & then: 상태 코드와 업무 오류 코드를 함께 확인합니다.
        assertThatThrownBy(() -> ratingService.create(
                1L, new CreateRatingRequest(550L, 4, "재미있게 봤습니다.")))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(exception.getCode()).isEqualTo("RATING409_1");
                })
                .hasMessage("이미 이 영화에 평점을 남기셨습니다.");

        verify(ratingRepository, never()).save(any(Rating.class));
    }

    @Test
    @DisplayName("다른 회원의 평점을 수정하면 403과 RATING403_1 예외를 던지고 원본을 유지한다")
    void updateRatingWhenNotOwner() {
        // given: 1번 회원이 작성한 평점입니다.
        Rating rating = Rating.builder()
                .member(member)
                .movieId(550L)
                .score(4)
                .comment("원래 후기입니다.")
                .build();
        given(ratingRepository.findById(71L)).willReturn(Optional.of(rating));

        // when & then: 2번 회원이 수정을 요청하면 차단합니다.
        assertThatThrownBy(() -> ratingService.update(
                2L, 71L, new UpdateRatingRequest(1, "다른 회원이 바꾸려는 후기입니다.")))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(exception.getCode()).isEqualTo("RATING403_1");
                })
                .hasMessage("본인이 작성한 평점만 수정하거나 삭제할 수 있습니다.");

        assertThat(rating.getScore()).isEqualTo(4);
        assertThat(rating.getComment()).isEqualTo("원래 후기입니다.");
        verify(ratingRepository, never()).save(any(Rating.class));
    }
}
