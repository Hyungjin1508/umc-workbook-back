# 영화 평점 서비스 API 명세

API 명세는 요청할 주소·HTTP 메서드·입력값·응답·실패 조건을 정리한 사용 설명서입니다.
아래 예시는 실제 응답 캡처가 아니며 ID는 DB에서 부여된 실제 값으로 바꿔야 합니다.

- Base URL: `http://localhost:8080`
- POST/PATCH 요청: `Content-Type: application/json`
- 현재 로그인/JWT 기능은 없습니다. URL의 `memberId`를 요청 회원 번호로 사용합니다.
- 수정·삭제는 URL의 회원 번호와 DB 평점 작성자 번호를 비교합니다.

## API 목록

| 기능 | 메서드 | 경로 | 정상 응답 |
|---|---|---|---|
| 회원가입 | POST | `/members` | 201 + 회원 정보 |
| 닉네임 중복 확인 | GET | `/members/nickname/{nickname}` | 200 + available |
| 이메일 중복 확인 | GET | `/members/email/{email}` | 200 + available |
| 회원 평점 조회 | GET | `/members/{memberId}/ratings` | 200 + 평점 배열 |
| 평점 등록 | POST | `/members/{memberId}/ratings` | 201 + 평점 ID |
| 평점 수정 | PATCH | `/members/{memberId}/ratings/{ratingId}` | 200 + 성공 메시지 |
| 평점 삭제 | DELETE | `/members/{memberId}/ratings/{ratingId}` | 204, 본문 없음 |

## 1. 회원가입

`POST /members`

| 필드 | 타입 | 조건 |
|---|---|---|
| email | string | 필수, 공백 불가, 이메일 형식, 중복 불가 |
| password | string | 필수, 8자 이상, 영문 대문자·소문자·숫자·`!@#$%^&*` 중 한 문자 각각 포함 |
| nickname | string | 필수, 공백 불가, 2~12자, 중복 불가 |

```json
{
  "email": "mission@example.com",
  "password": "TestPass1!",
  "nickname": "미션회원"
}
```

응답: `201 Created`

```json
{
  "isSuccess": true,
  "code": "COMMON201",
  "message": "회원가입에 성공했습니다.",
  "result": {
    "memberId": 1,
    "email": "mission@example.com",
    "nickname": "미션회원"
  }
}
```

비밀번호는 BCrypt 해시로 저장하고 응답 DTO에는 포함하지 않습니다.
이메일/닉네임 중복은 409, 입력 검증 실패는 400입니다.

## 2. 평점 등록

`POST /members/{memberId}/ratings`

| 필드 | 타입 | 조건 |
|---|---|---|
| movieId | integer (Long) | 필수, 양수 |
| score | integer | 필수, 1~5 |
| comment | string 또는 null | 선택, 최대 500자 |

```json
{
  "movieId": 98765,
  "score": 4,
  "comment": "재미있게 감상했습니다."
}
```

응답: `201 Created`. `result`의 숫자가 새 `ratingId`입니다.

```json
{
  "isSuccess": true,
  "code": "COMMON201",
  "message": "평점 등록에 성공했습니다.",
  "result": 10
}
```

같은 회원·영화 조합은 409입니다. 존재하지 않는 회원은 404입니다.
별도 Movie 엔티티가 없어 현재 코드는 영화 ID의 실제 존재 여부를 조회하지 않습니다.

## 3. 평점 수정

`PATCH /members/{memberId}/ratings/{ratingId}`

```json
{
  "score": 5,
  "comment": "다시 보니 더 좋았습니다."
}
```

별점은 필수이며 1~5, 후기는 선택이며 최대 500자입니다.
현재 구현은 PATCH에서도 별점을 반드시 받고, 후기를 생략하면 null로 변경합니다.

응답: `200 OK`

```json
{
  "isSuccess": true,
  "code": "COMMON200",
  "message": "평점 수정에 성공했습니다.",
  "result": null
}
```

다른 작성자의 평점이면 403, 평점이 없으면 404입니다.

## 4. 평점 삭제

`DELETE /members/{memberId}/ratings/{ratingId}`

요청 본문은 없습니다. 정상 응답은 **204 No Content이며 응답 본문도 없습니다.**
다른 작성자의 평점이면 403, 평점이 없으면 404입니다.
삭제 후 같은 ID로 다시 삭제하면 404입니다.

## 5. 조회 API

중복 확인 GET은 다음처럼 응답합니다. true는 사용 가능, false는 이미 사용 중입니다.

```json
{"available": true}
```

`GET /members/{memberId}/ratings`는 최신 평점 ID 순으로 배열을 반환합니다.

```json
[
  {
    "ratingId": 10,
    "movieId": 98765,
    "score": 5,
    "comment": "다시 보니 더 좋았습니다.",
    "createdAt": "2026-11-02T19:00:00",
    "updatedAt": "2026-11-02T19:05:00"
  }
]
```

평점이 없는 회원은 `200 []`, 회원이 없으면 404입니다.
기존 GET은 공통 응답 래퍼 없이 DTO/배열을 바로 반환합니다.

## 6. 과제에서 확인할 공통 오류 응답

```json
{
  "isSuccess": false,
  "code": "COMMON4001",
  "message": "별점은 5점 이하여야 합니다.",
  "result": null
}
```

| HTTP 상태 | code | 조건 |
|---|---|---|
| 400 | COMMON4001 | 이메일/비밀번호/닉네임/별점 등 DTO 입력 검증 실패 |
| 409 | COMMON4092 | 서비스에서 확인한 이메일·닉네임·평점 중복 |
| 409 | COMMON4091 | DB 제약조건 위반 |
| 403 | COMMON403 | 평점 작성자와 요청 회원 번호 불일치 |
| 404 | COMMON404 | 회원 또는 평점이 존재하지 않음 |

검증 오류가 여러 개면 첫 번째 필드 오류 메시지 하나를 반환합니다.
모든 종류의 서버 오류가 이 형식으로 변환되는 것은 아니며 위의 예외들이 처리 대상입니다.
