package com.lhstack.env.service;

import java.time.LocalDateTime;

public class RuntimeEnvironmentActive {

       private Integer id;

    private String projectHash;

    private String module;

    /**
     * 1：启用
     */
    private Integer enabled;

    /**
     * 存在则激活,不存在则没有
     */
    private Integer envId;

    /**
     * 创建时间
     */
       private LocalDateTime created;

    /**
     * 更新时间
     */
       private LocalDateTime updated;

    public LocalDateTime getCreated() {
        return created;
    }

    public RuntimeEnvironmentActive setCreated(LocalDateTime created) {
        this.created = created;
        return this;
    }

    public LocalDateTime getUpdated() {
        return updated;
    }

    public RuntimeEnvironmentActive setUpdated(LocalDateTime updated) {
        this.updated = updated;
        return this;
    }

    public Integer getEnabled() {
        return enabled;
    }

    public RuntimeEnvironmentActive setEnabled(Integer enabled) {
        this.enabled = enabled;
        return this;
    }

    public Integer getId() {
        return id;
    }

    public RuntimeEnvironmentActive setId(Integer id) {
        this.id = id;
        return this;
    }

    public String getProjectHash() {
        return projectHash;
    }

    public RuntimeEnvironmentActive setProjectHash(String projectHash) {
        this.projectHash = projectHash;
        return this;
    }

    public String getModule() {
        return module;
    }

    public RuntimeEnvironmentActive setModule(String module) {
        this.module = module;
        return this;
    }

    public Integer getEnvId() {
        return envId;
    }

    public RuntimeEnvironmentActive setEnvId(Integer envId) {
        this.envId = envId;
        return this;
    }
}
