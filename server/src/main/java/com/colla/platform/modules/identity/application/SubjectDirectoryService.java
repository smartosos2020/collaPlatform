package com.colla.platform.modules.identity.application;

import com.colla.platform.modules.identity.contract.SubjectDirectory;
import com.colla.platform.modules.identity.infrastructure.IdentityRepository;
import com.colla.platform.modules.identity.infrastructure.OrganizationRepository;
import com.colla.platform.modules.identity.infrastructure.UserGroupRepository;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class SubjectDirectoryService implements SubjectDirectory {
    private final IdentityRepository identityRepository;
    private final OrganizationRepository organizationRepository;
    private final UserGroupRepository userGroupRepository;

    public SubjectDirectoryService(
        IdentityRepository identityRepository,
        OrganizationRepository organizationRepository,
        UserGroupRepository userGroupRepository
    ) {
        this.identityRepository = identityRepository;
        this.organizationRepository = organizationRepository;
        this.userGroupRepository = userGroupRepository;
    }

    @Override
    public Map<SubjectRef, SubjectSnapshot> resolve(
        UUID workspaceId,
        UUID actorId,
        Collection<SubjectRef> subjects
    ) {
        Map<SubjectRef, SubjectSnapshot> result = new LinkedHashMap<>();
        for (SubjectRef subject : subjects) {
            SubjectSnapshot snapshot = switch (subject.type()) {
                case MEMBER -> identityRepository.findUserById(subject.id())
                    .filter(value -> workspaceId.equals(value.workspaceId()))
                    .map(value -> new SubjectSnapshot(
                        subject,
                        "active".equals(value.status()) ? SubjectState.ACTIVE : SubjectState.DISABLED,
                        value.displayName()
                    ))
                    .orElse(new SubjectSnapshot(subject, SubjectState.HIDDEN, null));
                case DEPARTMENT -> organizationRepository.findDepartment(workspaceId, subject.id())
                    .map(value -> new SubjectSnapshot(
                        subject,
                        "active".equals(value.status()) ? SubjectState.ACTIVE : SubjectState.DISABLED,
                        value.name()
                    ))
                    .orElse(new SubjectSnapshot(subject, SubjectState.HIDDEN, null));
                case USER_GROUP -> userGroupRepository.findGroup(workspaceId, subject.id())
                    .map(value -> new SubjectSnapshot(
                        subject,
                        "active".equals(value.status()) ? SubjectState.ACTIVE : SubjectState.DISABLED,
                        value.name()
                    ))
                    .orElse(new SubjectSnapshot(subject, SubjectState.HIDDEN, null));
            };
            result.put(subject, snapshot);
        }
        return Map.copyOf(result);
    }

    @Override
    public Set<UUID> expandActiveMembers(
        UUID workspaceId,
        UUID actorId,
        Collection<SubjectRef> subjects
    ) {
        Set<UUID> result = new LinkedHashSet<>();
        for (SubjectRef subject : subjects) {
            SubjectSnapshot snapshot = resolve(workspaceId, actorId, Set.of(subject)).get(subject);
            if (snapshot.state() != SubjectState.ACTIVE) {
                continue;
            }
            switch (subject.type()) {
                case MEMBER -> result.add(subject.id());
                case DEPARTMENT -> organizationRepository.listDepartmentMembers(workspaceId, subject.id()).stream()
                    .filter(member -> "active".equals(member.status()) && member.endedAt() == null)
                    .map(member -> member.userId())
                    .forEach(result::add);
                case USER_GROUP -> userGroupRepository.listExpandedMembers(workspaceId, subject.id()).stream()
                    .filter(member -> "active".equals(member.status()))
                    .map(member -> member.userId())
                    .forEach(result::add);
            }
        }
        return Set.copyOf(result);
    }

    @Override
    public List<MemberProfile> listActiveMembers(UUID workspaceId, UUID actorId) {
        return identityRepository.listMembers(workspaceId).stream()
            .filter(member -> "active".equals(member.status()))
            .map(member -> new MemberProfile(
                member.id(),
                member.username(),
                member.displayName(),
                member.avatarFileId(),
                member.email(),
                member.departments().stream().map(department -> department.departmentName()).toList()
            ))
            .toList();
    }

    @Override
    public Optional<MemberProfile> findActiveMember(UUID workspaceId, UUID actorId, UUID memberId) {
        return identityRepository.findUserById(memberId)
            .filter(member -> workspaceId.equals(member.workspaceId()) && "active".equals(member.status()))
            .map(member -> new MemberProfile(
                member.id(),
                member.username(),
                member.displayName(),
                member.avatarFileId(),
                member.email(),
                List.of()
            ));
    }
}
