package com.hyeja.engagementservice.favorite.service;

import com.hyeja.engagementservice.client.policy.PolicySnapshotReader;
import com.hyeja.engagementservice.favorite.converter.FavoriteConverter;
import com.hyeja.engagementservice.favorite.dto.FavoriteResponseDTO.FavoriteItemDTO;
import com.hyeja.engagementservice.favorite.dto.FavoriteResponseDTO.FavoriteListDTO;
import com.hyeja.engagementservice.favorite.entity.Favorite;
import com.hyeja.engagementservice.favorite.repository.FavoriteRepository;

import com.hyeja.engagementservice.policy.repository.PolicyRepository;
import com.hyeja.engagementservice.global.apiPayload.status.ErrorStatus;
import com.hyeja.engagementservice.global.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FavoriteService {

    private final PolicyRepository policyRepository;
    private final FavoriteRepository favoriteRepository;
    private final PolicySnapshotReader policySnapshotReader;
    private final FavoriteRegistrationService registrationService;

    public FavoriteItemDTO createFavorite(Long memberId, String policyId) {
        return registrationService.register(memberId, policySnapshotReader.getPolicy(policyId));
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

    @Transactional(readOnly = true)
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
