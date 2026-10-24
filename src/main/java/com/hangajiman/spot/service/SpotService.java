package com.hangajiman.spot.service;

import java.util.List;

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

    // SPOT-01 명소 목록. category가 없으면 전체
    public List<SpotResponse> getSpots(String category) {
        List<Spot> spots = (category == null || category.isBlank())
                ? spotRepository.findAllByOrderByIdAsc()
                : spotRepository.findAllByCategoryOrderByIdAsc(category);
        return spots.stream().map(SpotResponse::from).toList();
    }
}
