package com.ufo.ufo.domain.chat.application;

import com.ufo.ufo.domain.chat.exception.ChatNicknameGenerationException;
import java.util.List;
import java.util.stream.IntStream;
import org.springframework.stereotype.Component;

@Component
public class ChatNicknameGenerator {

    private static final List<String> COLORS = List.of(
            "레드", "버건디", "코랄", "핑크", "오렌지", "머스타드", "옐로우", "라임", "올리브", "카키",
            "민트", "그린", "아쿠아", "하늘", "블루", "네이비", "라벤더", "보라", "베이지", "아이보리",
            "크림", "모카", "브라운", "차콜", "그레이", "화이트", "블랙", "오트밀", "밀크티", "바나나", "피치"
    );

    private static final List<String> YARNS = List.of(
            "코튼", "린넨", "울", "메리노", "램스", "셔틀랜드", "알파카", "모헤어", "캐시", "앙고라",
            "야크", "실크", "모달", "아크릴", "폴리", "라쿤", "폭스", "글리터"
    );

    private static final List<String> NICKNAMES = IntStream.range(0, COLORS.size() * YARNS.size())
            .mapToObj(index -> COLORS.get(index % COLORS.size()) + " " + YARNS.get(index % YARNS.size()))
            .toList();

    public String generate(long roomStatusCount) {
        if (roomStatusCount < 0 || roomStatusCount >= NICKNAMES.size()) {
            throw new ChatNicknameGenerationException();
        }
        return NICKNAMES.get((int) roomStatusCount);
    }
}
