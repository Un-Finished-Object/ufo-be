package com.ufo.ufo.domain.user.application;

import com.ufo.ufo.domain.image.application.ImageService;
import com.ufo.ufo.domain.user.dao.UserProjectRepository;
import com.ufo.ufo.domain.user.dao.projection.PurchasedProjectSummary;
import com.ufo.ufo.domain.user.domain.User;
import com.ufo.ufo.domain.user.dto.response.PurchasedProjectsResponse;
import com.ufo.ufo.domain.user.dto.response.PurchasedProjectsResponse.Project;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserProjectService {

    private static final int PAGE_SIZE = 10;

    private final UserProjectRepository projectRepository;
    private final ImageService imageService;

    public PurchasedProjectsResponse getPurchasedProjects(User user, Integer page) {
        int pageNumber = normalizePage(page);
        long projectCount = projectRepository.countPurchasedProjects(user.getId());
        long offset = (long) (pageNumber - 1) * PAGE_SIZE;
        if (offset >= projectCount) {
            return PurchasedProjectsResponse.from(List.of(), 0);
        }
        List<Project> projects = projectRepository.findPurchasedProjects(user.getId(), PAGE_SIZE, offset)
                .stream()
                .map(this::toProject)
                .toList();
        long totalPages = (projectCount - 1) / PAGE_SIZE + 1;
        int nextPage = (int) Math.min(totalPages - pageNumber, 5);
        return PurchasedProjectsResponse.from(projects, nextPage);
    }

    private Project toProject(PurchasedProjectSummary project) {
        return new Project(
                project.getPatternId(),
                project.getPatternName(),
                project.getAuthor(),
                imageService.buildImageUrl(project.getThumbnailKey()),
                project.getYarnPurchaseId() != null,
                project.getPurchaseYarnDate(),
                project.getPurchaseChatId() != null,
                project.getPurchaseChatId(),
                project.getPurchaseChatDate()
        );
    }

    private int normalizePage(Integer page) {
        if (page == null || page < 1) {
            return 1;
        }
        return page;
    }
}
