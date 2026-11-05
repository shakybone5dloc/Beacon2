package dev.beacon.application.stories;

import dev.beacon.contracts.stories.SaveStoryRequest;
import dev.beacon.contracts.stories.StoryResponse;
import dev.beacon.domain.stories.Story;
import dev.beacon.infrastructure.persistence.StoryRepository;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** The user's story bank: STAR stories kept once and reused across applications. */
@Service
public class StoryService {

    private final StoryRepository stories;
    private final Clock clock;

    public StoryService(StoryRepository stories, Clock clock) {
        this.stories = stories;
        this.clock = clock;
    }

    /** Most recently changed first. */
    @Transactional(readOnly = true)
    public List<StoryResponse> list(UUID ownerId) {
        return stories.findAllByOwnerIdOrderByUpdatedAtDesc(ownerId).stream().map(StoryService::toResponse).toList();
    }

    /** Every story, for writing a brief from. */
    @Transactional(readOnly = true)
    public List<Story> all(UUID ownerId) {
        return stories.findAllByOwnerIdOrderByUpdatedAtDesc(ownerId);
    }

    @Transactional(readOnly = true)
    public Optional<StoryResponse> get(UUID ownerId, UUID id) {
        return stories.findByIdAndOwnerId(id, ownerId).map(StoryService::toResponse);
    }

    @Transactional
    public StoryResponse create(UUID ownerId, SaveStoryRequest request) {
        return toResponse(stories.saveAndFlush(Story.create(ownerId, fields(request), Instant.now(clock))));
    }

    /** Empty when the story does not exist; a stale version is a conflict. */
    @Transactional
    public Optional<StoryResponse> update(UUID ownerId, UUID id, SaveStoryRequest request) {
        return stories.findByIdAndOwnerId(id, ownerId).map(story -> {
            if (!Objects.equals(story.getVersion(), request.version())) {
                throw new OptimisticLockingFailureException("This story was changed somewhere else.");
            }
            story.apply(fields(request), Instant.now(clock));
            return toResponse(stories.saveAndFlush(story));
        });
    }

    @Transactional
    public boolean delete(UUID ownerId, UUID id) {
        var story = stories.findByIdAndOwnerId(id, ownerId);
        story.ifPresent(stories::delete);
        return story.isPresent();
    }

    private static Story.Fields fields(SaveStoryRequest r) {
        return new Story.Fields(r.title(), r.situation(), r.task(), r.action(), r.result(), r.metrics(), r.tags());
    }

    static StoryResponse toResponse(Story s) {
        return new StoryResponse(s.getId(), s.getTitle(), s.getSituation(), s.getTask(), s.getAction(), s.getResult(),
                s.getMetrics(), s.getTags(), s.gaps(), s.getVersion(), s.getCreatedAt(), s.getUpdatedAt());
    }
}
