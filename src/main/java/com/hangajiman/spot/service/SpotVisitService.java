package com.hangajiman.spot.service;

import java.time.Clock;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hangajiman.global.exception.BusinessException;
import com.hangajiman.global.exception.ErrorCode;
import com.hangajiman.postcard.service.PostcardService;
import com.hangajiman.spot.domain.SpotVisit;
import com.hangajiman.spot.dto.VisitResponse;
import com.hangajiman.spot.repository.SpotRepository;
import com.hangajiman.spot.repository.SpotVisitRepository;
import com.hangajiman.wish.domain.Wish;
import com.hangajiman.wish.repository.WishRepository;

/**
 * SPOT-03 방문 인증. 앱이 기기 안에서 명소 반경을 확인하고 결과만 보냅니다.
 * 서버는 좌표·거리를 받지도 저장하지도 않으므로 진위를 검증하지 못합니다.
 * 같은 명소는 돌탑마다 한 번 인증할 수 있고, 인증할 때마다 엽서를 받습니다 (11/8).
 */
@Service
public class SpotVisitService {

    private final SpotRepository spotRepository;
    private final SpotVisitRepository visitRepository;
    private final WishRepository wishRepository;
    private final PostcardService postcardService;
    private final Clock clock;

    public SpotVisitService(SpotRepository spotRepository, SpotVisitRepository visitRepository,
                            WishRepository wishRepository, PostcardService postcardService, Clock clock) {
        this.spotRepository = spotRepository;
        this.visitRepository = visitRepository;
        this.wishRepository = wishRepository;
        this.postcardService = postcardService;
        this.clock = clock;
    }

    // 같은 명소는 돌탑(진행 중 소원)마다 한 번. 지금 돌탑에서 이미 인증했으면 기존 기록을 돌려줍니다.
    // 새로 인증하면 그 명소 엽서 4종 중 아직 없는 1장을 받습니다.
    @Transactional
    public VisitResponse visit(Long userId, Long spotId) {
        if (!spotRepository.existsById(spotId)) {
            throw new BusinessException(ErrorCode.SPOT_NOT_FOUND);
        }
        Long towerId = currentTowerId(userId);
        Optional<SpotVisit> existing = findInTower(userId, spotId, towerId);
        if (existing.isPresent()) {
            return new VisitResponse(existing.get().getId(), false, null);
        }

        SpotVisit visit;
        try {
            visit = visitRepository.saveAndFlush(new SpotVisit(userId, spotId, towerId, clock.instant()));
        } catch (DataIntegrityViolationException e) {
            // 동시에 두 번 인증한 경우 부분 유니크 인덱스가 막습니다.
            return new VisitResponse(findInTower(userId, spotId, towerId).map(SpotVisit::getId).orElse(null), false, null);
        }
        return new VisitResponse(visit.getId(), true, postcardService.issueRandom(userId, spotId, visit.getId()));
    }

    // 진행 중인 돌탑 id (없으면 null)
    public Long currentTowerId(Long userId) {
        return wishRepository.findFirstByUserIdAndStatusIn(userId, WishRepository.OPEN_STATUSES)
                .map(Wish::getId)
                .orElse(null);
    }

    private Optional<SpotVisit> findInTower(Long userId, Long spotId, Long towerId) {
        return towerId == null
                ? visitRepository.findFirstByUserIdAndSpotIdAndWishIdIsNull(userId, spotId)
                : visitRepository.findFirstByUserIdAndSpotIdAndWishId(userId, spotId, towerId);
    }
}
