package com.colla.platform.config.runtime;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("colla.runtime")
public class RuntimeRoleProperties {
    private String role;
    private String instanceId = "unknown";
    private String version = "0.1.0";
    private String commit = "unknown";

    public RuntimeRole role() {
        return RuntimeRole.parse(role);
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getCommit() {
        return commit;
    }

    public void setCommit(String commit) {
        this.commit = commit;
    }
}
