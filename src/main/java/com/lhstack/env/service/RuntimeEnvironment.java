package com.lhstack.env.service;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import kotlin.Pair;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class RuntimeEnvironment {

       private Integer id;

    /**
     * 项目hash
     */
    private String projectHash;

    /**
     * 项目地址
     */
    private String projectPath;

    /**
     * 项目名称
     */
    private String projectName;

    /**
     * 模块
     */
    private String module;

    /**
     * 环境名称
     */
    private String name;

    /**
     * 环境描述
     */
    private String remark;

    /**
     * args值
     */
    private String argsValue;

    /**
     * env值
     */
    private String envValue;

    /**
     * vm值
     */
    private String vmValue;

    /**
     * 1: 默认
     */
    private Integer isDefault;

    /**
     * 创建时间
     */
       private LocalDateTime created;

    /**
     * 更新时间
     */
       private LocalDateTime updated;

    public Integer getIsDefault() {
        return isDefault;
    }

    public RuntimeEnvironment setIsDefault(Integer isDefault) {
        this.isDefault = isDefault;
        return this;
    }

    public static List<RuntimeEnvironment> buildInitList(Project project, Module module) {
        return Stream.of(new Pair<String, String>("dev", "开发环境"), new Pair<>("test", "测试环境"), new Pair<String, String>("prod", "生产环境"))
                .map(item -> {
                    RuntimeEnvironment runtimeEnvironment = new RuntimeEnvironment();
                    runtimeEnvironment.setProjectHash(project.getLocationHash())
                            .setProjectName(project.getName())
                            .setProjectPath(project.getBasePath())
                            .setIsDefault(1)
                            .setModule(module.toString())
                            .setName(item.component1())
                            .setRemark(item.component2());
                    return runtimeEnvironment;
                }).collect(Collectors.toList());
    }

    public String getVmValue() {
        return vmValue;
    }

    public RuntimeEnvironment setVmValue(String vmValue) {
        this.vmValue = vmValue;
        return this;
    }

    public String getName() {
        return name;
    }

    public RuntimeEnvironment setName(String name) {
        this.name = name;
        return this;
    }

    public Integer getId() {
        return id;
    }

    public RuntimeEnvironment setId(Integer id) {
        this.id = id;
        return this;
    }

    public String getProjectHash() {
        return projectHash;
    }

    public RuntimeEnvironment setProjectHash(String projectHash) {
        this.projectHash = projectHash;
        return this;
    }

    public String getProjectPath() {
        return projectPath;
    }

    public RuntimeEnvironment setProjectPath(String projectPath) {
        this.projectPath = projectPath;
        return this;
    }

    public String getProjectName() {
        return projectName;
    }

    public RuntimeEnvironment setProjectName(String projectName) {
        this.projectName = projectName;
        return this;
    }

    public String getModule() {
        return module;
    }

    public RuntimeEnvironment setModule(String module) {
        this.module = module;
        return this;
    }

    public String getRemark() {
        return remark;
    }

    public RuntimeEnvironment setRemark(String remark) {
        this.remark = remark;
        return this;
    }

    public String getArgsValue() {
        return argsValue;
    }

    public RuntimeEnvironment setArgsValue(String argsValue) {
        this.argsValue = argsValue;
        return this;
    }

    public String getEnvValue() {
        return envValue;
    }

    public RuntimeEnvironment setEnvValue(String envValue) {
        this.envValue = envValue;
        return this;
    }

    public LocalDateTime getCreated() {
        return created;
    }

    public RuntimeEnvironment setCreated(LocalDateTime created) {
        this.created = created;
        return this;
    }

    public LocalDateTime getUpdated() {
        return updated;
    }

    public RuntimeEnvironment setUpdated(LocalDateTime updated) {
        this.updated = updated;
        return this;
    }

    @Override
    public String toString() {
        return "RuntimeEnvironment{" +
                "id=" + id +
                ", projectHash='" + projectHash + '\'' +
                ", projectPath='" + projectPath + '\'' +
                ", projectName='" + projectName + '\'' +
                ", module='" + module + '\'' +
                ", name='" + name + '\'' +
                ", remark='" + remark + '\'' +
                ", argsValue='" + argsValue + '\'' +
                ", envValue='" + envValue + '\'' +
                ", created=" + created +
                ", updated=" + updated +
                '}';
    }
}
