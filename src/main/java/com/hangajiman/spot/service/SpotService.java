package com.hangajiman.spot.service;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hangajiman.spot.domain.Spot;
import com.hangajiman.spot.dto.SpotResponse;
import com.hangajiman.spot.repository.SpotRepository;

@Service
@Transactional(readOnly = true)
public class SpotService {

    private final SpotRepository spotRepository;

    public SpotService(SpotRepository spotRepository) {
        this.spotRepository = spotRepository;
    }

    // SPOT-01 명소 목록. category가 없으면 전체. 로그인 전(userId null)이면 visited는 모두 false
    public List<SpotResponse> getSpots(String category, Long userId) {
        List<Spot> spots = (category == null || category.isBlank())
                ? spotRepository.findAllByOrderByIdAsc()
                : spotRepository.findAllByCategoryOrderByIdAsc(category);
        Set<Long> visited = userId == null ? Set.of() : Set.copyOf(spotRepository.findVisitedSpotIds(userId));
        return spots.stream().map(spot -> SpotResponse.from(spot, visited.contains(spot.getId()))).toList();
    }
}
