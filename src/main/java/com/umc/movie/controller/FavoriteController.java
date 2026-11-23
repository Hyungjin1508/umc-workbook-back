// src/main/java/com/umc/movie/controller/FavoriteController.java
package com.umc.movie.controller;

import com.umc.movie.config.SwaggerConfig;
import com.umc.movie.dto.ApiResponse;
import com.umc.movie.dto.CreateFavoriteRequest;
import com.umc.movie.dto.FavoriteResponse;
import com.umc.movie.dto.FavoriteStatusResponse;
import com.umc.movie.interceptor.AuthInterceptor;
import com.umc.movie.service.FavoriteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "즐겨찾기", description = "로그인한 회원의 영화 즐겨찾기 API")
@SecurityRequirement(name = SwaggerConfig.BEARER_AUTH) // 이 컨트롤러의 모든 API가 토큰을 요구합니다
public class FavoriteController {

    private final FavoriteService favoriteService;

    @Operation(
            summary = "즐겨찾기 등록",
            description = "Bearer 토큰이 필요합니다. 토큰의 주인인 회원이 TMDB 영화 ID 하나를 즐겨찾기에 추가합니다."
    )
    // 우리가 만든 공통 응답 DTO 이름도 ApiResponse라서, Swagger 쪽 애노테이션은 풀네임으로 적습니다.
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = "COMMON4001: 영화 ID는 필수이며 양수이어야 합니다."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "MEMBER404_1: 해당 회원을 찾을 수 없습니다."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201", description = "즐겨찾기 등록 성공",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {
                              "isSuccess": true,
                              "code": "COMMON201",
                              "message": "즐겨찾기를 등록했습니다.",
                              "result": { "favoriteId": 1, "movieId": 550, "createdAt": "2026-09-13T21:00:00" }
                            }
                            """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "AUTH401_2: 토큰 누락 / AUTH401_3: 유효하지 않은 토큰 / AUTH401_4: 만료된 토큰",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {
                              "isSuccess": false,
                              "code": "AUTH401_2",
                              "message": "로그인이 필요한 요청입니다.",
                              "result": null
                            }
                            """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409", description = "FAVORITE409_1: 이미 즐겨찾기한 영화. 동시 등록이 DB 제약에서 차단되면 COMMON4091을 반환할 수 있습니다.",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {
                              "isSuccess": false,
                              "code": "FAVORITE409_1",
                              "message": "이미 이 영화를 즐겨찾기하셨습니다.",
                              "result": null
                            }
                            """)))
    })
    @PostMapping("/favorites")
    public ResponseEntity<ApiResponse<FavoriteResponse>> create(
            @Parameter(hidden = true)
            @RequestAttribute(AuthInterceptor.MEMBER_ID) Long memberId,
            @RequestBody @Valid CreateFavoriteRequest request
    ) {
        FavoriteResponse response = favoriteService.create(memberId, request);
        return ResponseEntity.status(201)
                .body(ApiResponse.created("즐겨찾기를 등록했습니다.", response));
    }

    @Operation(summary = "내 즐겨찾기 조회", description = "Bearer 토큰이 필요합니다. 내 즐겨찾기를 favoriteId 내림차순으로 모두 조회합니다. 페이지네이션은 없으며, 목록이 없으면 result는 빈 배열입니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "COMMON200: 내 즐겨찾기 목록을 조회했습니다."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "AUTH401_2: 토큰 누락 / AUTH401_3: 유효하지 않은 토큰 / AUTH401_4: 만료된 토큰")
    })
    @GetMapping("/favorites")
    public ResponseEntity<ApiResponse<List<FavoriteResponse>>> getMyFavorites(
            @Parameter(hidden = true)
            @RequestAttribute(AuthInterceptor.MEMBER_ID) Long memberId
    ) {
        List<FavoriteResponse> response = favoriteService.getMyFavorites(memberId);
        return ResponseEntity.ok(ApiResponse.ok("내 즐겨찾기 목록을 조회했습니다.", response));
    }

    @Operation(summary = "즐겨찾기 여부 조회", description = "Bearer 토큰이 필요합니다. 해당 영화를 내가 즐겨찾기했는지 조회합니다. 등록하지 않은 영화도 200과 isFavorite=false를 반환합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "COMMON200: 즐겨찾기 여부를 조회했습니다. result에는 isFavorite만 포함합니다."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "AUTH401_2: 토큰 누락 / AUTH401_3: 유효하지 않은 토큰 / AUTH401_4: 만료된 토큰")
    })
    @GetMapping("/favorites/{movieId}")
    public ResponseEntity<ApiResponse<FavoriteStatusResponse>> getStatus(
            @Parameter(hidden = true)
            @RequestAttribute(AuthInterceptor.MEMBER_ID) Long memberId,
            @PathVariable Long movieId
    ) {
        FavoriteStatusResponse response = favoriteService.getStatus(memberId, movieId);
        return ResponseEntity.ok(ApiResponse.ok("즐겨찾기 여부를 조회했습니다.", response));
    }

    @Operation(summary = "즐겨찾기 해제", description = "Bearer 토큰이 필요합니다. movieId에 해당하는 내 즐겨찾기를 해제하고 200과 result가 null인 공통 응답을 반환합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "COMMON200: 즐겨찾기를 해제했습니다. result는 null입니다."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "AUTH401_2: 토큰 누락 / AUTH401_3: 유효하지 않은 토큰 / AUTH401_4: 만료된 토큰"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "FAVORITE404_1: 즐겨찾기한 영화를 찾을 수 없습니다.")
    })
    @DeleteMapping("/favorites/{movieId}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @Parameter(hidden = true)
            @RequestAttribute(AuthInterceptor.MEMBER_ID) Long memberId,
            @PathVariable Long movieId
    ) {
        favoriteService.delete(memberId, movieId);
        return ResponseEntity.ok(ApiResponse.ok("즐겨찾기를 해제했습니다.", null));
    }
}
