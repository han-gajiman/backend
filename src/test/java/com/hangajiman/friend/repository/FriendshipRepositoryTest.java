package com.hangajiman.friend.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.hangajiman.friend.FriendApiTestSupport;
import com.hangajiman.stone.domain.Stone;
import com.hangajiman.user.domain.User;
import com.hangajiman.wish.domain.Visibility;
import com.hangajiman.wish.domain.Wish;
import com.hangajiman.wish.domain.WishStatus;

// FRIEND-02·04에서 쓰는 돌탑·돌 일괄 조회
class FriendshipRepositoryTest extends FriendApiTestSupport {

    @Test
    void wishesOfManyUsers() {
        User a = user("A");
        User b = user("B");
        User c = user("C");
        Wish wishA = activeWish(a, Visibility.FRIENDS);
        Wish wishB = activeWish(b, Visibility.PRIVATE);

        List<Wish> wishes = friendshipRepository.findWishesOf(List.of(a.getId(), b.getId(), c.getId()),
                WishStatus.ACTIVE);

        assertThat(wishes).extracting(Wish::getId).containsExactlyInAnyOrder(wishA.getId(), wishB.getId());
    }

    @Test
    void stoneCountsAndStonedToday() {
        Wish first = activeWish(user("A"), Visibility.FRIENDS);
        Wish second = activeWish(user("B"), Visibility.FRIENDS);
        stoneRepository.save(Stone.of(first.getId(), today().minusDays(2), null));
        stoneRepository.save(Stone.of(first.getId(), today().minusDays(1), "단어 20개"));
        stoneRepository.save(Stone.of(first.getId(), today(), null));
        stoneRepository.saveAndFlush(Stone.of(second.getId(), today().minusDays(1), null));

        Map<Long, Long> counts = friendshipRepository.countStones(List.of(first.getId(), second.getId())).stream()
                .collect(Collectors.toMap(FriendshipRepository.WishStoneCount::getWishId,
                        FriendshipRepository.WishStoneCount::getStoneCount));

        assertThat(counts).containsEntry(first.getId(), 3L).containsEntry(second.getId(), 1L);
        assertThat(friendshipRepository.findWishIdsStonedOn(List.of(first.getId(), second.getId()), today()))
                .containsExactly(first.getId());
    }
}
