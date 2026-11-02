package com.hyeja.engagementservice.favorite.service;

import com.hyeja.engagementservice.favorite.converter.FavoriteConverter;
import com.hyeja.engagementservice.favorite.dto.FavoriteResponseDTO.FavoriteItemDTO;
import com.hyeja.engagementservice.favorite.dto.FavoriteResponseDTO.FavoriteListDTO;
import com.hyeja.engagementservice.favorite.entity.Favorite;
import com.hyeja.engagementservice.favorite.repository.FavoriteRepository;

import com.hyeja.engagementservice.policy.entity.Policy;
import com.hyeja.engagementservice.policy.repository.PolicyRepository;
import com.hyeja.engagementservice.global.apiPayload.status.ErrorStatus;
import com.hyeja.engagementservice.global.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FavoriteService {

    private final PolicyRepository policyRepository;
    private final FavoriteRepository favoriteRepository;

    @Transactional
    public FavoriteItemDTO createFavorite(Long memberId, String policyId) {
        Policy policy = policyRepository.findById(policyId)
                .filter(value -> !value.isDeleted() && Boolean.TRUE.equals(value.getActiveYn()))
                .orElseThrow(() -> new GeneralException(ErrorStatus.POLICY_NOT_FOUND));

        if (favoriteRepository.existsByMemberIdAndPolicyPolicyId(memberId, policyId)) {
            throw new GeneralException(ErrorStatus.FAVORITE_ALREADY_EXISTS);
        }

        try {
            Favorite favorite = favoriteRepository.saveAndFlush(
                    Favorite.builder()
                            .memberId(memberId)
                            .policy(policy)
                            .build()
            );
            return FavoriteConverter.toFavoriteItemDTO(favorite);
        } catch (DataIntegrityViolationException exception) {
            throw new GeneralException(ErrorStatus.FAVORITE_ALREADY_EXISTS);
        }
    }

    @Transactional
    public void deleteFavorite(Long memberId, String policyId) {

        if (!policyRepository.existsById(policyId)) {
            throw new GeneralException(ErrorStatus.POLICY_NOT_FOUND);
        }

        Favorite favorite = favoriteRepository
                .findByMemberIdAndPolicyPolicyIdAndDeletedAtIsNull(memberId, policyId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.FAVORITE_NOT_FOUND));

        favoriteRepository.delete(favorite);
    }

    public FavoriteListDTO getMyFavorites(Long memberId, String keyword, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size);
        String normalizedKeyword = normalizeKeyword(keyword);
        Page<Favorite> favorites = normalizedKeyword == null
                ? favoriteRepository.findAllActiveByMemberId(memberId, pageRequest)
                : favoriteRepository.searchAllActiveByMemberIdAndKeyword(
                        memberId, normalizedKeyword, pageRequest);
        return FavoriteConverter.toFavoriteListDTO(favorites);
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return keyword.trim()
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
    }
}
