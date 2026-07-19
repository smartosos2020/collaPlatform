package com.colla.platform.modules.project.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.colla.platform.modules.event.application.DomainEventWorker;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ProjectSpaceMembershipControllerIntegrationTests {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DomainEventWorker domainEventWorker;

    @Test
    void enforcesRoleCapabilitiesMemberGovernanceAndCandidateBoundary() throws Exception {
        TestUser root = root("matrix-root");
        TestUser owner = member(root.token(), "matrix-owner");
        TestUser spaceAdmin = member(root.token(), "matrix-admin");
        TestUser member = member(root.token(), "matrix-member");
        TestUser guest = member(root.token(), "matrix-guest");
        TestUser candidate = member(root.token(), "matrix-candidate");
        UUID spaceId = createSpace(owner.token(), "matrix-space", "private");

        addMember(owner.token(), spaceId, spaceAdmin.id(), "admin")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.roleKey").value("admin"));
        addMember(owner.token(), spaceId, member.id(), "member").andExpect(status().isOk());
        addMember(owner.token(), spaceId, guest.id(), "guest").andExpect(status().isOk());

        mockMvc.perform(get("/api/project-spaces/" + spaceId + "/role-capabilities")
                .header("Authorization", bearer(member.token())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.roleKey == 'owner')].capabilities[0]").exists())
            .andExpect(jsonPath("$[?(@.roleKey == 'guest')].canManageOwner").value(false));

        mockMvc.perform(get("/api/project-spaces/" + spaceId + "/members")
                .header("Authorization", bearer(guest.token())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].roleKey", hasItem("owner")))
            .andExpect(jsonPath("$[*].roleKey", hasItem("admin")))
            .andExpect(jsonPath("$[*].roleKey", hasItem("member")))
            .andExpect(jsonPath("$[*].roleKey", hasItem("guest")));

        mockMvc.perform(get("/api/project-spaces/" + spaceId + "/member-candidates?query=" + candidate.username())
                .header("Authorization", bearer(spaceAdmin.token())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].userId", hasItem(candidate.id().toString())))
            .andExpect(jsonPath("$[*].userId", not(hasItem(member.id().toString()))));

        mockMvc.perform(get("/api/project-spaces/" + spaceId + "/member-candidates")
                .header("Authorization", bearer(member.token())))
            .andExpect(status().isForbidden());
        addMember(spaceAdmin.token(), spaceId, candidate.id(), "admin").andExpect(status().isForbidden());
        UUID candidateMemberId = memberId(addMember(spaceAdmin.token(), spaceId, candidate.id(), "member"));
        addMember(spaceAdmin.token(), spaceId, candidate.id(), "member").andExpect(status().isOk());
        mockMvc.perform(patch("/api/project-spaces/" + spaceId + "/members/" + candidateMemberId + "/role")
                .header("Authorization", bearer(spaceAdmin.token()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"roleKey\":\"guest\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.roleKey").value("guest"));
        mockMvc.perform(patch("/api/project-spaces/" + spaceId + "/members/" + candidateMemberId + "/role")
                .header("Authorization", bearer(spaceAdmin.token()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"roleKey\":\"admin\"}"))
            .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/project-spaces/" + spaceId + "/members/" + candidateMemberId)
                .header("Authorization", bearer(spaceAdmin.token())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.memberStatus").value("removed"));
        mockMvc.perform(delete("/api/project-spaces/" + spaceId + "/members/" + candidateMemberId)
                .header("Authorization", bearer(spaceAdmin.token())))
            .andExpect(status().isOk());
        mockMvc.perform(get("/api/project-spaces/" + spaceId + "/members")
                .header("Authorization", bearer(owner.token())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].userId", not(hasItem(candidate.id().toString()))));
        UUID otherSpaceId = createSpace(owner.token(), "matrix-other-space", "private");
        mockMvc.perform(delete("/api/project-spaces/" + otherSpaceId + "/members/" + candidateMemberId)
                .header("Authorization", bearer(owner.token())))
            .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/project-spaces/" + spaceId + "/member-candidates")
                .header("Authorization", bearer(root.token())))
            .andExpect(status().isNotFound());

        TestUser disabled = member(root.token(), "matrix-disabled");
        mockMvc.perform(post("/api/admin/users/" + disabled.id() + "/disable")
                .header("Authorization", bearer(root.token())))
            .andExpect(status().isOk());
        addMember(owner.token(), spaceId, disabled.id(), "member").andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/project-spaces/" + spaceId + "/member-candidates?query=" + disabled.username())
                .header("Authorization", bearer(owner.token())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].userId", not(hasItem(disabled.id().toString()))));

        UUID foreignWorkspace = UUID.randomUUID();
        UUID foreignUser = UUID.randomUUID();
        jdbcTemplate.update(
            "insert into workspaces (id, name, slug, status, created_at, updated_at) values (?, 'M2 Foreign', ?, 'active', now(), now())",
            foreignWorkspace, "m2-foreign-" + suffix()
        );
        jdbcTemplate.update(
            """
                insert into users (id, workspace_id, username, password_hash, display_name, status, created_at, updated_at)
                values (?, ?, ?, 'unused', 'M2 Foreign', 'active', now(), now())
                """,
            foreignUser, foreignWorkspace, "m2foreign" + suffix()
        );
        addMember(owner.token(), spaceId, foreignUser, "member").andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/project-spaces/" + spaceId + "/settings/disable")
                .header("Authorization", bearer(owner.token())))
            .andExpect(status().isOk());
        TestUser stoppedCandidate = member(root.token(), "matrix-stopped");
        addMember(owner.token(), spaceId, stoppedCandidate.id(), "member").andExpect(status().isConflict());
        invite(owner.token(), spaceId, stoppedCandidate.id(), "member", null).andExpect(status().isConflict());
    }

    @Test
    void protectsOwnerTransferDisableAndOffboardingHandover() throws Exception {
        TestUser root = root("owner-root");
        TestUser owner = member(root.token(), "owner-source");
        TestUser target = member(root.token(), "owner-target");
        UUID spaceId = createSpace(owner.token(), "owner-transfer", "private");
        UUID targetMemberId = memberId(addMember(owner.token(), spaceId, target.id(), "member"));

        mockMvc.perform(post("/api/project-spaces/" + spaceId + "/members/" + targetMemberId + "/transfer-owner")
                .header("Authorization", bearer(owner.token()))
                .header("X-Colla-Request-Id", "owner-transfer-idempotent"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.userId == '%s')].roleKey".formatted(target.id())).value("owner"))
            .andExpect(jsonPath("$[?(@.userId == '%s')].roleKey".formatted(owner.id())).value("admin"));
        mockMvc.perform(post("/api/project-spaces/" + spaceId + "/members/" + targetMemberId + "/transfer-owner")
                .header("Authorization", bearer(owner.token()))
                .header("X-Colla-Request-Id", "owner-transfer-idempotent"))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/project-spaces/" + spaceId + "/members/leave")
                .header("Authorization", bearer(target.token())))
            .andExpect(status().isConflict());
        mockMvc.perform(post("/api/admin/users/" + target.id() + "/disable")
                .header("Authorization", bearer(root.token())))
            .andExpect(status().isConflict());

        TestUser offboardOwner = member(root.token(), "offboard-owner");
        TestUser handover = member(root.token(), "offboard-handover");
        UUID offboardSpace = createSpace(offboardOwner.token(), "offboard-space", "private");
        mockMvc.perform(post("/api/admin/users/" + offboardOwner.id() + "/offboard")
                .header("Authorization", bearer(root.token()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"handoverToUserId":"%s"}
                    """.formatted(handover.id())))
            .andExpect(status().isOk());
        assertThat(jdbcTemplate.queryForObject("select status from users where id = ?", String.class, offboardOwner.id()))
            .isEqualTo("disabled");
        assertThat(jdbcTemplate.queryForObject(
            """
                select r.role_key from project_space_members m
                join project_space_role_assignments r on r.member_id = m.id and r.revoked_at is null
                where m.space_id = ? and m.user_id = ? and m.status = 'active'
                """,
            String.class, offboardSpace, handover.id()
        )).isEqualTo("owner");
    }

    @Test
    void invitationLifecycleIsSecureIdempotentAuditedAndDeduplicated() throws Exception {
        TestUser root = root("invite-root");
        TestUser owner = member(root.token(), "invite-owner");
        TestUser invitee = member(root.token(), "invite-target");
        TestUser outsider = member(root.token(), "invite-outsider");
        UUID spaceId = createSpace(owner.token(), "invite-space", "private");

        String createResponse = invite(owner.token(), spaceId, invitee.id(), "member", "invite-create-request")
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        JsonNode created = objectMapper.readTree(createResponse);
        UUID invitationId = UUID.fromString(created.get("id").asText());
        assertThat(createResponse.toLowerCase()).doesNotContain("token", "hash");
        assertThat(jdbcTemplate.queryForObject(
            "select token_hash from project_space_invitations where id = ?", String.class, invitationId
        )).matches("[0-9a-f]{64}");

        invite(owner.token(), spaceId, invitee.id(), "member", "invite-create-repeat")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(invitationId.toString()));

        mockMvc.perform(post("/api/project-spaces/" + spaceId + "/invitations/" + invitationId + "/resend")
                .header("Authorization", bearer(owner.token()))
                .header("X-Colla-Request-Id", "invite-resend-same")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{" + "\"expiresInHours\":48}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.version").value(1));
        mockMvc.perform(post("/api/project-spaces/" + spaceId + "/invitations/" + invitationId + "/resend")
                .header("Authorization", bearer(owner.token()))
                .header("X-Colla-Request-Id", "invite-resend-same")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{" + "\"expiresInHours\":48}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.version").value(1));

        mockMvc.perform(post("/api/project-space-invitations/" + invitationId + "/accept")
                .header("Authorization", bearer(outsider.token())))
            .andExpect(status().isNotFound());

        var executor = Executors.newFixedThreadPool(2);
        Callable<String> accept = () -> mockMvc.perform(post("/api/project-space-invitations/" + invitationId + "/accept")
                .header("Authorization", bearer(invitee.token()))
                .header("X-Colla-Request-Id", "invite-accept-concurrent"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        List<String> accepted;
        try {
            accepted = executor.invokeAll(List.of(accept, accept)).stream().map(future -> {
                try {
                    return future.get(10, TimeUnit.SECONDS);
                } catch (Exception exception) {
                    throw new RuntimeException(exception);
                }
            }).toList();
        } finally {
            executor.shutdownNow();
        }
        assertThat(accepted).hasSize(2);
        assertThat(objectMapper.readTree(accepted.get(0)).get("id").asText())
            .isEqualTo(objectMapper.readTree(accepted.get(1)).get("id").asText());
        assertThat(jdbcTemplate.queryForObject(
            "select count(*) from project_space_members where space_id = ? and user_id = ? and status = 'active'",
            Long.class, spaceId, invitee.id()
        )).isEqualTo(1L);

        domainEventWorker.processPendingEvents();
        assertThat(jdbcTemplate.queryForObject(
            "select count(*) from notifications where recipient_id = ? and target_type = 'project_space' and target_id = ?",
            Long.class, invitee.id(), spaceId
        )).isEqualTo(2L);
        assertThat(jdbcTemplate.queryForObject(
            "select count(*) from domain_events where idempotency_key like 'project_space.invitation.accepted:%' and aggregate_id = ?",
            Long.class, spaceId
        )).isEqualTo(1L);

        String auditMetadata = jdbcTemplate.queryForObject(
            """
                select metadata::text from audit_logs
                where action = 'project_space.invitation.accepted' and target_id = ?
                order by created_at desc limit 1
                """,
            String.class,
            invitationId
        );
        assertThat(auditMetadata).contains("previousStatus", "pending", "currentStatus", "accepted")
            .doesNotContainIgnoringCase("token_hash");
        assertThat(jdbcTemplate.queryForObject(
            "select count(*) from domain_events where aggregate_id = ? and payload::text ilike '%token%'",
            Long.class, spaceId
        )).isZero();
    }

    @Test
    void revokedRejectedAndExpiredInvitationsCannotGrantMembership() throws Exception {
        TestUser root = root("terminal-root");
        TestUser owner = member(root.token(), "terminal-owner");
        TestUser revokedUser = member(root.token(), "terminal-revoked");
        TestUser rejectedUser = member(root.token(), "terminal-rejected");
        TestUser expiredUser = member(root.token(), "terminal-expired");
        UUID spaceId = createSpace(owner.token(), "terminal-space", "private");

        UUID revokedId = invitationId(invite(owner.token(), spaceId, revokedUser.id(), "guest", null));
        mockMvc.perform(delete("/api/project-spaces/" + spaceId + "/invitations/" + revokedId)
                .header("Authorization", bearer(owner.token())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("revoked"));
        mockMvc.perform(post("/api/project-space-invitations/" + revokedId + "/accept")
                .header("Authorization", bearer(revokedUser.token())))
            .andExpect(status().isConflict());

        UUID rejectedId = invitationId(invite(owner.token(), spaceId, rejectedUser.id(), "member", null));
        mockMvc.perform(post("/api/project-space-invitations/" + rejectedId + "/reject")
                .header("Authorization", bearer(rejectedUser.token())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("rejected"));
        mockMvc.perform(post("/api/project-space-invitations/" + rejectedId + "/reject")
                .header("Authorization", bearer(rejectedUser.token())))
            .andExpect(status().isOk());

        UUID expiredId = invitationId(invite(owner.token(), spaceId, expiredUser.id(), "member", null));
        jdbcTemplate.update("update project_space_invitations set expires_at = now() - interval '1 minute' where id = ?", expiredId);
        mockMvc.perform(post("/api/project-space-invitations/" + expiredId + "/accept")
                .header("Authorization", bearer(expiredUser.token())))
            .andExpect(status().isConflict());
        assertThat(jdbcTemplate.queryForObject(
            "select status from project_space_invitations where id = ?", String.class, expiredId
        )).isEqualTo("expired");

        mockMvc.perform(get("/api/project-spaces/" + spaceId + "/invitations")
                .header("Authorization", bearer(rejectedUser.token())))
            .andExpect(status().isNotFound());
    }

    private org.springframework.test.web.servlet.ResultActions addMember(String token, UUID spaceId, UUID userId, String role)
        throws Exception {
        return mockMvc.perform(post("/api/project-spaces/" + spaceId + "/members")
            .header("Authorization", bearer(token))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"userId":"%s","roleKey":"%s"}
                """.formatted(userId, role)));
    }

    private org.springframework.test.web.servlet.ResultActions invite(
        String token,
        UUID spaceId,
        UUID userId,
        String role,
        String requestId
    ) throws Exception {
        var request = post("/api/project-spaces/" + spaceId + "/invitations")
            .header("Authorization", bearer(token))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"userId":"%s","roleKey":"%s","expiresInHours":72}
                """.formatted(userId, role));
        if (requestId != null) {
            request.header("X-Colla-Request-Id", requestId);
        }
        return mockMvc.perform(request);
    }

    private UUID invitationId(org.springframework.test.web.servlet.ResultActions result) throws Exception {
        return UUID.fromString(objectMapper.readTree(
            result.andExpect(status().isOk()).andReturn().getResponse().getContentAsString()
        ).get("id").asText());
    }

    private UUID memberId(org.springframework.test.web.servlet.ResultActions result) throws Exception {
        return UUID.fromString(objectMapper.readTree(
            result.andExpect(status().isOk()).andReturn().getResponse().getContentAsString()
        ).get("id").asText());
    }

    private UUID createSpace(String token, String prefix, String visibility) throws Exception {
        String response = mockMvc.perform(post("/api/project-spaces")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"spaceKey":"%s-%s","name":"%s","visibility":"%s"}
                    """.formatted(prefix, suffix(), prefix, visibility)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }

    private TestUser root(String prefix) throws Exception {
        return new TestUser(
            jdbcTemplate.queryForObject("select id from users where username = 'admin'", UUID.class),
            "admin",
            login("admin", "admin123456", prefix + "-" + UUID.randomUUID())
        );
    }

    private TestUser member(String rootToken, String prefix) throws Exception {
        String username = prefix.replace("-", "") + suffix();
        String response = mockMvc.perform(post("/api/admin/users")
                .header("Authorization", bearer(rootToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "username":"%s",
                      "password":"member123456",
                      "displayName":"%s",
                      "email":"%s@example.com",
                      "roleCode":"member"
                    }
                    """.formatted(username, prefix, username)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(objectMapper.readTree(response).get("id").asText());
        return new TestUser(id, username, login(username, "member123456", prefix + "-" + UUID.randomUUID()));
    }

    private String login(String username, String password, String fingerprint) throws Exception {
        String response = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "username":"%s",
                      "password":"%s",
                      "deviceType":"web",
                      "deviceFingerprint":"%s",
                      "deviceName":"MockMvc",
                      "appVersion":"test"
                    }
                    """.formatted(username, password, fingerprint)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("accessToken").asText();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private static String suffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private record TestUser(UUID id, String username, String token) {
    }
}
