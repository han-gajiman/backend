package com.hangajiman.friend;

import java.time.Clock;
import java.time.LocalDate;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import com.hangajiman.friend.domain.Friendship;
import com.hangajiman.friend.repository.FriendshipRepository;
import com.hangajiman.global.security.JwtProvider;
import com.hangajiman.stone.domain.Stone;
import com.hangajiman.stone.repository.StoneRepository;
import com.hangajiman.user.domain.SocialProvider;
import com.hangajiman.user.domain.User;
import com.hangajiman.user.repository.UserRepository;
import com.hangajiman.wish.domain.Visibility;
import com.hangajiman.wish.domain.Wish;
import com.hangajiman.wish.repository.WishRepository;

/**
 * 친구 API 테스트 공통 준비물. 실제 DB(hangajiman_test)를 쓰고 테스트마다 롤백됩니다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public abstract class FriendApiTestSupport {

    protected static final long SPOT_ID = 1L; // V2 시드 명소

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JwtProvider jwtProvider;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected FriendshipRepository friendshipRepository;

    @Autowired
    protected WishRepository wishRepository;

    @Autowired
    protected StoneRepository stoneRepository;

    @Autowired
    protected Clock clock;

    // 온보딩을 마친 회원. 닉네임은 회원끼리 겹치면 안 돼서, 같은 테스트에서 이미 쓴 이름이면 뒤에 번호를 붙입니다
    protected User user(String nickname) {
        User user = User.create(SocialProvider.KAKAO, "kakao-" + System.nanoTime(), null);
        String unique = userRepository.existsByNickname(nickname) ? nickname + System.nanoTime() % 1_000_000 : nickname;
        user.completeOnboarding(unique, clock.instant());
        return userRepository.saveAndFlush(user);
    }

    protected void befriend(User a, User b) {
        friendshipRepository.save(new Friendship(a.getId(), b.getId()));
        friendshipRepository.saveAndFlush(new Friendship(b.getId(), a.getId()));
    }

    // 오늘 시작한 진행 중 돌탑
    protected Wish activeWish(User owner, Visibility visibility) {
        return wishRepository.saveAndFlush(Wish.create(owner.getId(), SPOT_ID, owner.getNickname() + "의 소원",
                "매일 단어 20개", visibility, today(), 1));
    }

    protected void stone(Wish wish, LocalDate date) {
        stoneRepository.saveAndFlush(Stone.of(wish.getId(), date, null));
    }

    protected LocalDate today() {
        return LocalDate.now(clock);
    }

    protected MockHttpServletRequestBuilder as(User user, MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtProvider.createAccessToken(user.getId()))
                .contentType(MediaType.APPLICATION_JSON);
    }
}
