package com.colla.platform.modules.project.application;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
@ConfigurationProperties(prefix = "colla.project-space-experience")
public class ProjectSpaceExperienceRolloutProperties {
    private boolean enabled;
    private boolean killSwitch = true;
    @NotBlank
    @Size(max = 80)
    private String policyVersion = "s21-m7-v1";
    @Min(0)
    @Max(10_000)
    private int rolloutBasisPoints;
    @NotNull
    @Size(max = 10_000)
    private Set<UUID> includedWorkspaceIds = new LinkedHashSet<>();
    @NotNull
    @Size(max = 10_000)
    private Set<UUID> excludedWorkspaceIds = new LinkedHashSet<>();
    @NotNull
    @Size(max = 10_000)
    private Set<UUID> includedSpaceIds = new LinkedHashSet<>();
    @NotNull
    @Size(max = 10_000)
    private Set<UUID> excludedSpaceIds = new LinkedHashSet<>();
    @NotNull
    @Size(max = 10_000)
    private Set<UUID> includedUserIds = new LinkedHashSet<>();
    @NotNull
    @Size(max = 10_000)
    private Set<UUID> excludedUserIds = new LinkedHashSet<>();
    @NotBlank
    @Size(min = 8, max = 160)
    private String evaluationSalt = "s21-m7-disabled";
    @Min(0)
    @Max(3_600)
    private int cacheMaxAgeSeconds = 30;
    @Valid
    @NotNull
    private Telemetry telemetry = new Telemetry();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isKillSwitch() {
        return killSwitch;
    }

    public void setKillSwitch(boolean killSwitch) {
        this.killSwitch = killSwitch;
    }

    public String getPolicyVersion() {
        return policyVersion;
    }

    public void setPolicyVersion(String policyVersion) {
        this.policyVersion = policyVersion;
    }

    public int getRolloutBasisPoints() {
        return rolloutBasisPoints;
    }

    public void setRolloutBasisPoints(int rolloutBasisPoints) {
        this.rolloutBasisPoints = rolloutBasisPoints;
    }

    public Set<UUID> getIncludedWorkspaceIds() {
        return includedWorkspaceIds;
    }

    public void setIncludedWorkspaceIds(Set<UUID> includedWorkspaceIds) {
        this.includedWorkspaceIds = includedWorkspaceIds;
    }

    public Set<UUID> getExcludedWorkspaceIds() {
        return excludedWorkspaceIds;
    }

    public void setExcludedWorkspaceIds(Set<UUID> excludedWorkspaceIds) {
        this.excludedWorkspaceIds = excludedWorkspaceIds;
    }

    public Set<UUID> getIncludedSpaceIds() {
        return includedSpaceIds;
    }

    public void setIncludedSpaceIds(Set<UUID> includedSpaceIds) {
        this.includedSpaceIds = includedSpaceIds;
    }

    public Set<UUID> getExcludedSpaceIds() {
        return excludedSpaceIds;
    }

    public void setExcludedSpaceIds(Set<UUID> excludedSpaceIds) {
        this.excludedSpaceIds = excludedSpaceIds;
    }

    public Set<UUID> getIncludedUserIds() {
        return includedUserIds;
    }

    public void setIncludedUserIds(Set<UUID> includedUserIds) {
        this.includedUserIds = includedUserIds;
    }

    public Set<UUID> getExcludedUserIds() {
        return excludedUserIds;
    }

    public void setExcludedUserIds(Set<UUID> excludedUserIds) {
        this.excludedUserIds = excludedUserIds;
    }

    public String getEvaluationSalt() {
        return evaluationSalt;
    }

    public void setEvaluationSalt(String evaluationSalt) {
        this.evaluationSalt = evaluationSalt;
    }

    public int getCacheMaxAgeSeconds() {
        return cacheMaxAgeSeconds;
    }

    public void setCacheMaxAgeSeconds(int cacheMaxAgeSeconds) {
        this.cacheMaxAgeSeconds = cacheMaxAgeSeconds;
    }

    public Telemetry getTelemetry() {
        return telemetry;
    }

    public void setTelemetry(Telemetry telemetry) {
        this.telemetry = telemetry;
    }

    public static class Telemetry {
        private boolean enabled;
        @Min(0)
        @Max(10_000)
        private int sampleBasisPoints;
        @Min(1)
        @Max(20)
        private int maxBatchSize = 20;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getSampleBasisPoints() {
            return sampleBasisPoints;
        }

        public void setSampleBasisPoints(int sampleBasisPoints) {
            this.sampleBasisPoints = sampleBasisPoints;
        }

        public int getMaxBatchSize() {
            return maxBatchSize;
        }

        public void setMaxBatchSize(int maxBatchSize) {
            this.maxBatchSize = maxBatchSize;
        }
    }
}
