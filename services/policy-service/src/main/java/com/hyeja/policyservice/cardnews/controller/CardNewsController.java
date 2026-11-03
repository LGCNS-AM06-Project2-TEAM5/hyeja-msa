package com.hyeja.policyservice.cardnews.controller;

import com.hyeja.policyservice.cardnews.dto.CardNewsDetailResponseDTO;
import com.hyeja.policyservice.cardnews.dto.CardNewsResponseDTO;
import com.hyeja.policyservice.cardnews.dto.MemberCardNewsResponseDTO;
import com.hyeja.policyservice.cardnews.service.CardNewsService;
import com.hyeja.policyservice.global.apiPayload.ApiResponse;
import com.hyeja.policyservice.global.auth.LoginMemberId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 카드뉴스 API.
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

    // 로그인 회원 메인 화면 카드뉴스(최대 4건). 회원 지역(F6)에 맞는 정책만 보여 줍니다.
    // 토큰이 없으면 Gateway가 먼저 401로 막습니다(GET /api/policies/card-news는 인증 필요).
    @GetMapping("/card-news")
    public ApiResponse<List<MemberCardNewsResponseDTO>> getMemberCardNews(@LoginMemberId Long memberId) {
        return ApiResponse.onSuccess(cardNewsService.getMemberCardNews(memberId));
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
