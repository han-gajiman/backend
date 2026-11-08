package com.hangajiman.postcard.service;

import java.time.Clock;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hangajiman.friend.repository.FriendshipRepository;
import com.hangajiman.global.exception.BusinessException;
import com.hangajiman.global.exception.ErrorCode;
import com.hangajiman.global.response.PageResponse;
import com.hangajiman.notification.domain.NotificationType;
import com.hangajiman.notification.service.NotificationService;
import com.hangajiman.postcard.domain.Postcard;
import com.hangajiman.postcard.domain.PostcardLetter;
import com.hangajiman.postcard.dto.PostcardDtos.LetterResponse;
import com.hangajiman.postcard.dto.PostcardDtos.PostcardResponse;
import com.hangajiman.postcard.dto.PostcardDtos.SendLetterRequest;
import com.hangajiman.postcard.dto.PostcardDtos.SendLetterResponse;
import com.hangajiman.postcard.repository.PostcardLetterRepository;
import com.hangajiman.postcard.repository.PostcardRepository;
import com.hangajiman.spot.domain.Spot;
import com.hangajiman.spot.repository.SpotRepository;
import com.hangajiman.user.domain.User;
import com.hangajiman.user.repository.UserRepository;

/**
 * SPOT-04 엽서. 방문 인증할 때마다 그 명소 엽서 4종 중 아직 없는 1장을 무작위로 받습니다.
 * 가진 엽서만 뒷면에 글을 써서 친구나 나에게 보낼 수 있고, 보내도 내 엽서는 그대로 남습니다. 보낸 글은 고칠 수 없습니다.
 */
@Service
public class PostcardService {

    private static final Sort LATEST_FIRST = Sort.by(Sort.Direction.DESC, "createdAt", "id");

    private final PostcardRepository postcardRepository;
    private final PostcardLetterRepository letterRepository;
    private final SpotRepository spotRepository;
    private final UserRepository userRepository;
    private final FriendshipRepository friendshipRepository;
    private final NotificationService notificationService;
    private final Clock clock;

    public PostcardService(PostcardRepository postcardRepository, PostcardLetterRepository letterRepository,
                           SpotRepository spotRepository, UserRepository userRepository,
                           FriendshipRepository friendshipRepository, NotificationService notificationService,
                           Clock clock) {
        this.postcardRepository = postcardRepository;
        this.letterRepository = letterRepository;
        this.spotRepository = spotRepository;
        this.userRepository = userRepository;
        this.friendshipRepository = friendshipRepository;
        this.notificationService = notificationService;
        this.clock = clock;
    }

    // 방문 인증 직후: 아직 없는 스타일 중 하나. 4종을 다 모았으면 null
    @Transactional
    public PostcardResponse issueRandom(Long userId, Long spotId, Long visitId) {
        Set<Integer> owned = postcardRepository.findByUserIdAndSpotId(userId, spotId).stream()
                .map(Postcard::getStyle)
                .collect(Collectors.toSet());
        List<Integer> missing = IntStream.rangeClosed(1, Postcard.STYLES_PER_SPOT).filter(s -> !owned.contains(s)).boxed().toList();
        if (missing.isEmpty()) {
            return null;
        }
        int style = missing.get(ThreadLocalRandom.current().nextInt(missing.size()));
        Postcard postcard = postcardRepository.save(new Postcard(userId, spotId, visitId, style, clock.instant()));
        return toResponse(postcard, spotNames(List.of(spotId)));
    }

    // GET /postcards 내 엽서함 (명소·스타일 순)
    @Transactional(readOnly = true)
    public List<PostcardResponse> getPostcards(Long userId) {
        List<Postcard> postcards = postcardRepository.findByUserIdOrderBySpotIdAscStyleAsc(userId);
        Map<Long, String> names = spotNames(postcards.stream().map(Postcard::getSpotId).distinct().toList());
        return postcards.stream().map(p -> toResponse(p, names)).toList();
    }

    // POST /postcards/letters
    @Transactional
    public SendLetterResponse send(Long userId, SendLetterRequest request) {
        if (!postcardRepository.existsByUserIdAndSpotIdAndStyle(userId, request.spotId(), request.style().shortValue())) {
            throw new BusinessException(ErrorCode.POSTCARD_NOT_OWNED);
        }
        Long receiverId = request.receiverId() == null ? userId : request.receiverId();
        boolean toMe = receiverId.equals(userId);
        if (!toMe && !friendshipRepository.existsByUserIdAndFriendId(userId, receiverId)) {
            throw new BusinessException(ErrorCode.FRIEND_NOT_FOUND);
        }
        PostcardLetter letter = letterRepository.save(
                new PostcardLetter(userId, receiverId, request.spotId(), request.style(), request.message().trim()));
        if (!toMe) {
            String sender = userRepository.findById(userId).map(User::getNickname).orElse("친구");
            notificationService.notify(receiverId, userId, NotificationType.POSTCARD, letter.getId(),
                    sender + "님이 엽서를 보냈어요");
        }
        return new SendLetterResponse(letter.getId());
    }

    // GET /postcards/letters/received 받은 엽서 (최신순)
    @Transactional(readOnly = true)
    public PageResponse<LetterResponse> getReceived(Long userId, int page, int size) {
        var letters = letterRepository.findByReceiverId(userId, PageResponse.pageable(page, size, LATEST_FIRST));
        List<PostcardLetter> content = letters.getContent();
        Map<Long, String> names = spotNames(content.stream().map(PostcardLetter::getSpotId).distinct().toList());
        Map<Long, String> nicknames = nicknames(content.stream()
                .flatMap(l -> Stream.of(l.getSenderId(), l.getReceiverId())).distinct().toList());
        return PageResponse.of(letters, l -> new LetterResponse(l.getId(), l.getSpotId(), names.get(l.getSpotId()),
                l.getStyle(), l.getMessage(), nicknames.get(l.getSenderId()), nicknames.get(l.getReceiverId()),
                l.getSenderId().equals(userId), l.getReadAt() != null, l.getCreatedAt()));
    }

    // PATCH /postcards/letters/{id}/read
    @Transactional
    public void markRead(Long userId, Long letterId) {
        letterRepository.findByIdAndReceiverId(letterId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.LETTER_NOT_FOUND))
                .markRead(clock.instant());
    }

    // 우체통 깃발
    @Transactional(readOnly = true)
    public boolean hasUnreadLetter(Long userId) {
        return letterRepository.existsByReceiverIdAndReadAtIsNull(userId);
    }

    private PostcardResponse toResponse(Postcard postcard, Map<Long, String> names) {
        return new PostcardResponse(postcard.getSpotId(), names.get(postcard.getSpotId()), postcard.getStyle(),
                postcard.getIssuedAt());
    }

    private Map<Long, String> spotNames(Collection<Long> spotIds) {
        return spotRepository.findAllById(spotIds).stream().collect(Collectors.toMap(Spot::getId, Spot::getName));
    }

    private Map<Long, String> nicknames(Collection<Long> userIds) {
        return userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u.getNickname() == null ? "소원지기" : u.getNickname(),
                        (a, b) -> a, HashMap::new));
    }
}
