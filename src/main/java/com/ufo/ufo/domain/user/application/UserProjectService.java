package com.ufo.ufo.domain.user.application;

import com.ufo.ufo.domain.chat.dao.ChatRoomStatusRepository;
import com.ufo.ufo.domain.chat.domain.ChatRoomStatus;
import com.ufo.ufo.domain.credit.dao.UnlockRepository;
import com.ufo.ufo.domain.credit.domain.Unlock;
import com.ufo.ufo.domain.credit.domain.UnlockType;
import com.ufo.ufo.domain.pattern.dao.PatternRepository;
import com.ufo.ufo.domain.pattern.domain.Pattern;
import com.ufo.ufo.domain.user.application.model.PurchasedProject;
import com.ufo.ufo.domain.user.domain.User;
import com.ufo.ufo.domain.user.dto.response.PurchasedProjectsResponse;
import com.ufo.ufo.domain.user.dto.response.PurchasedProjectsResponse.Project;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserProjectService {

    private static final int PAGE_SIZE = 10;

    private final UnlockRepository unlockRepository;
    private final ChatRoomStatusRepository chatRoomStatusRepository;
    private final PatternRepository patternRepository;

    public PurchasedProjectsResponse getPurchasedProjects(User user, Integer page) {
        int pageNumber = normalizePage(page);
        Map<Long, PurchasedProject> projects = new LinkedHashMap<>();

        mergeYarnPurchases(user, projects);
        mergeChatPurchases(user, projects);

        List<Project> sortedProjects = projects.values()
                .stream()
                .sorted(Comparator.comparing(PurchasedProject::latestPurchaseDate, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(PurchasedProject::getPatternId, Comparator.reverseOrder()))
                .map(PurchasedProject::toResponse)
                .toList();

        int totalPages = (int) Math.ceil((double) sortedProjects.size() / PAGE_SIZE);
        int nextPage = resolveNextPage(pageNumber, totalPages);
        int fromIndex = Math.min((pageNumber - 1) * PAGE_SIZE, sortedProjects.size());
        int toIndex = Math.min(fromIndex + PAGE_SIZE, sortedProjects.size());

        return PurchasedProjectsResponse.from(sortedProjects.subList(fromIndex, toIndex), nextPage);
    }

    private void mergeYarnPurchases(User user, Map<Long, PurchasedProject> projects) {
        List<Unlock> yarnUnlocks = unlockRepository.findAllByUser_IdAndTypeOrderByCreatedAtDescIdDesc(
                user.getId(),
                UnlockType.YARN_INFO
        );
        Map<Long, Pattern> activePatterns = findActivePatterns(yarnUnlocks.stream()
                .map(Unlock::getPatternId)
                .toList());

        for (Unlock unlock : yarnUnlocks) {
            Pattern pattern = activePatterns.get(unlock.getPatternId());
            if (pattern == null) {
                continue;
            }
            projects.computeIfAbsent(pattern.getId(), id -> new PurchasedProject(pattern))
                    .applyYarn(unlock.getCreatedAt());
        }
    }

    private void mergeChatPurchases(User user, Map<Long, PurchasedProject> projects) {
        List<ChatRoomStatus> chatStatuses = chatRoomStatusRepository
                .findAllByUser_IdAndRoom_Pattern_DeletedAtIsNullOrderByCreatedAtDescIdDesc(user.getId());

        for (ChatRoomStatus chatStatus : chatStatuses) {
            Pattern pattern = chatStatus.getRoom().getPattern();
            projects.computeIfAbsent(pattern.getId(), id -> new PurchasedProject(pattern))
                    .applyChat(chatStatus.getRoom().getId(), chatStatus.getCreatedAt());
        }
    }

    private Map<Long, Pattern> findActivePatterns(List<Long> patternIds) {
        if (patternIds.isEmpty()) {
            return Map.of();
        }
        return patternRepository.findAllById(patternIds)
                .stream()
                .filter(pattern -> pattern.getDeletedAt() == null)
                .collect(Collectors.toMap(Pattern::getId, Function.identity()));
    }

    private int normalizePage(Integer page) {
        if (page == null || page < 1) {
            return 1;
        }
        return page;
    }

    private int resolveNextPage(int currentPage, int totalPages) {
        int remainingPages = totalPages - currentPage;
        if (remainingPages <= 0) {
            return 0;
        }
        return Math.min(remainingPages, 5);
    }
}
