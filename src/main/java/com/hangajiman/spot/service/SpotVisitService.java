package com.hangajiman.spot.service;

import java.time.Clock;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hangajiman.global.exception.BusinessException;
import com.hangajiman.global.exception.ErrorCode;
import com.hangajiman.spot.domain.SpotVisit;
import com.hangajiman.spot.dto.VisitResponse;
import com.hangajiman.spot.repository.SpotRepository;
import com.hangajiman.spot.repository.SpotVisitRepository;
import com.hangajiman.wish.domain.Wish;
import com.hangajiman.wish.repository.WishRepository;

/**
 * SPOT-03 방문 인증. 앱이 기기 안에서 명소 반경을 확인하고 결과만 보냅니다.
 * 서버는 좌표·거리를 받지도 저장하지도 않으므로 진위를 검증하지 못합니다.
 */
@Service
public class SpotVisitService {

    private final SpotRepository spotRepository;
    private final SpotVisitRepository visitRepository;
    private final WishRepository wishRepository;
    private final Clock clock;

    public SpotVisitService(SpotRepository spotRepository, SpotVisitRepository visitRepository,
                            WishRepository wishRepository, Clock clock) {
        this.spotRepository = spotRepository;
        this.visitRepository = visitRepository;
        this.wishRepository = wishRepository;
        this.clock = clock;
    }

    // 이미 인증한 명소면 기존 기록을 돌려줍니다. 진행 중인 돌탑의 명소면 그 돌탑에 연결합니다.
    @Transactional
    public VisitResponse visit(Long userId, Long spotId) {
        if (!spotRepository.existsById(spotId)) {
            throw new BusinessException(ErrorCode.SPOT_NOT_FOUND);
        }
        Optional<SpotVisit> existing = visitRepository.findByUserIdAndSpotId(userId, spotId);
        if (existing.isPresent()) {
            return new VisitResponse(existing.get().getId(), false);
        }

        Long wishId = wishRepository.findFirstByUserIdAndStatusIn(userId, WishRepository.OPEN_STATUSES)
                .filter(wish -> wish.getSpotId().equals(spotId))
                .map(Wish::getId)
                .orElse(null);
        SpotVisit visit = visitRepository.save(new SpotVisit(userId, spotId, wishId, clock.instant()));
        return new VisitResponse(visit.getId(), true);
    }
}
