package com.hyeja.engagementservice.favorite.service;

import com.hyeja.engagementservice.client.policy.dto.PolicySnapshotDTO;
import com.hyeja.engagementservice.favorite.converter.FavoriteConverter;
import com.hyeja.engagementservice.favorite.dto.FavoriteResponseDTO.FavoriteItemDTO;
import com.hyeja.engagementservice.favorite.entity.Favorite;
import com.hyeja.engagementservice.favorite.repository.FavoriteRepository;
import com.hyeja.engagementservice.global.apiPayload.status.ErrorStatus;
import com.hyeja.engagementservice.global.exception.GeneralException;
import com.hyeja.engagementservice.policy.service.PolicySnapshotUpdater;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** F5 응답을 받은 후에만 DB 트랜잭션을 시작합니다. */
@Service
@RequiredArgsConstructor
public class FavoriteRegistrationService {
    private final PolicySnapshotUpdater snapshotUpdater;
    private final FavoriteRepository favoriteRepository;

    @Transactional
    public FavoriteItemDTO register(Long memberId, PolicySnapshotDTO snapshot) {
        var policy = snapshotUpdater.upsert(snapshot.policyId(), snapshot.policyName(), snapshot.categories(),
                snapshot.supportContent(), snapshot.applyEndDate(), snapshot.applyPeriodCode(),
                snapshot.applyUrl(), snapshot.activeYn());
        policy.restore(); // F5 성공 응답의 deleted=false도 읽기 모델에 반영합니다.
        if (favoriteRepository.existsByMemberIdAndPolicyPolicyId(memberId, snapshot.policyId())) {
            throw new GeneralException(ErrorStatus.FAVORITE_ALREADY_EXISTS);
        }
        try {
            Favorite favorite = favoriteRepository.saveAndFlush(
                    Favorite.builder().memberId(memberId).policy(policy).build());
            return FavoriteConverter.toFavoriteItemDTO(favorite);
        } catch (DataIntegrityViolationException exception) {
            throw new GeneralException(ErrorStatus.FAVORITE_ALREADY_EXISTS);
        }
    }
}
