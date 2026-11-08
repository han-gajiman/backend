package com.hangajiman.spot.domain;

import java.math.BigDecimal;

import com.hangajiman.global.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 명소 (ERD spots). 시드 데이터는 db/migration/V2__seed_spots.sql
 */
@Entity
@Table(name = "spots")
public class Spot extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false, length = 30)
    private String region;

    // 🟡 카테고리 기준 미정 → 문자열로 둡니다
    @Column(nullable = false, length = 20)
    private String category;

    @Column(length = 300)
    private String description;

    @Column(length = 500)
    private String story; // SPOT-05 명소 이야기 (없으면 앱이 스토리보기를 숨김)

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "radius_m", nullable = false)
    private int radiusM;

    @Column(name = "background_image_url", length = 500)
    private String backgroundImageUrl;

    @Column(name = "postcard_image_url", length = 500)
    private String postcardImageUrl;

    protected Spot() {
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getRegion() {
        return region;
    }

    public String getCategory() {
        return category;
    }

    public String getDescription() {
        return description;
    }

    public String getStory() {
        return story;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }

    public int getRadiusM() {
        return radiusM;
    }

    public String getBackgroundImageUrl() {
        return backgroundImageUrl;
    }

    public String getPostcardImageUrl() {
        return postcardImageUrl;
    }
}
