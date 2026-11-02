package com.hyeja.policyservice.cardnews.controller;

import com.hyeja.policyservice.cardnews.dto.CardNewsDetailResponseDTO;
import com.hyeja.policyservice.cardnews.dto.CardNewsResponseDTO;
import com.hyeja.policyservice.cardnews.service.CardNewsService;
import com.hyeja.policyservice.global.apiPayload.ApiResponse;
import com.hyeja.policyservice.global.auth.LoginMemberId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 카드뉴스 API. 로그인 회원 홈 카드뉴스(GET /card-news)는 user(F6) 연결 후 옮깁니다.
@RestController
@RequestMapping("/api/policies")
@RequiredArgsConstructor
public class CardNewsController {

    private final CardNewsService cardNewsService;

    // 비회원 메인 화면에 노출할 마감 임박 카드뉴스(최대 4건)
    @GetMapping("/card-news/guest")
    public ApiResponse<List<CardNewsResponseDTO>> getGuestCardNews() {
        return ApiResponse.onSuccess(cardNewsService.getGuestCardNews());
    }

    // 카드뉴스 팝업 상세. 비로그인도 볼 수 있습니다.
    // 보낸 토큰이 무효하면 Gateway가 401로 막으므로(1차 컨트롤러의 Authorization 확인 자리) 여기서는 회원 ID만 받습니다.
    @GetMapping("/card-detail/{policyId}")
    public ApiResponse<CardNewsDetailResponseDTO> getCardNewsDetail(
            @PathVariable String policyId,
            @LoginMemberId(required = false) Long memberId) {
        return ApiResponse.onSuccess(cardNewsService.getCardNewsDetail(policyId, memberId));
    }
}
