package com.lhstack.env.service;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.core.incrementer.DefaultIdentifierGenerator;
import com.baomidou.mybatisplus.core.injector.DefaultSqlInjector;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;

public class RuntimeEnvironmentService extends ServiceImpl<RuntimeEnvironmentMapper, RuntimeEnvironment> {

    private static volatile HikariDataSource dataSource = null;
    private static volatile MybatisConfiguration mybatisConfiguration = null;
    private static volatile SqlSessionFactory sqlSessionFactory = null;
    private static final AtomicBoolean initialized = new AtomicBoolean(false);
    private static final AtomicBoolean destroyed = new AtomicBoolean(false);
    private static final Object lock = new Object();
    private final RuntimeEnvironmentActiveMapper runtimeEnvironmentActiveMapper;

    public RuntimeEnvironmentService(RuntimeEnvironmentMapper runtimeEnvironmentMapper, RuntimeEnvironmentActiveMapper runtimeEnvironmentActiveMapper) {
        this.baseMapper = runtimeEnvironmentMapper;
        this.runtimeEnvironmentActiveMapper = runtimeEnvironmentActiveMapper;
    }

    public static void init() {
        if (initialized.compareAndSet(false, true)) {
            synchronized (lock) {
                try {
                    destroyed.set(false);
                    initDataSource();
                    initMybatisConfiguration();
                    initGlobalConfig();
                    initSqlSessionFactory();
                } catch (Throwable e) {
                    initialized.set(false);
                    throw new RuntimeException("Failed to initialize RuntimeEnvironmentService", e);
                }
            }
        }
    }

    private static void initSqlSessionFactory() {
        sqlSessionFactory = new MybatisSqlSessionFactoryBuilder()
                .build(mybatisConfiguration);
    }

    private static void initGlobalConfig() {
        GlobalConfig globalConfig = GlobalConfigUtils.getGlobalConfig(mybatisConfiguration);
        globalConfig.setSqlInjector(new DefaultSqlInjector());
        globalConfig.setIdentifierGenerator(new DefaultIdentifierGenerator());
        globalConfig.setMetaObjectHandler(new MetaObjectHandler() {
            @Override
            public void insertFill(MetaObject metaObject) {
                setFieldValByName("created", LocalDateTime.now(), metaObject);
                setFieldValByName("updated", LocalDateTime.now(), metaObject);
            }

            @Override
            public void updateFill(MetaObject metaObject) {
                setFieldValByName("updated", LocalDateTime.now(), metaObject);
            }
        });
    }

    private static void initMybatisConfiguration() {
        mybatisConfiguration = new MybatisConfiguration();
        MybatisPlusInterceptor mybatisPlusInterceptor = new MybatisPlusInterceptor();
        PaginationInnerInterceptor paginationInnerInterceptor = new PaginationInnerInterceptor();
        paginationInnerInterceptor.setDbType(DbType.SQLITE);
        paginationInnerInterceptor.setOptimizeJoin(true);
        mybatisConfiguration.setEnvironment(new Environment("1", new JdbcTransactionFactory(), dataSource));
        mybatisPlusInterceptor.addInnerInterceptor(paginationInnerInterceptor);
        mybatisConfiguration.addInterceptor(mybatisPlusInterceptor);
        mybatisConfiguration.setMapUnderscoreToCamelCase(true);
        mybatisConfiguration.setUseGeneratedKeys(true);
        mybatisConfiguration.addMapper(RuntimeEnvironmentMapper.class);
        mybatisConfiguration.addMapper(RuntimeEnvironmentActiveMapper.class);
    }

    private static void initDataSource() {
        dataSource = new HikariDataSource();
        String userHome = System.getProperty("user.home");
        File dir = new File(userHome, ".jtools/jtools-runtime-environment");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        dataSource.setDriverClassName("org.sqlite.JDBC");
        // 使用正确的路径分隔符
        String dbPath = new File(dir, "data.db").getAbsolutePath().replace("\\", "/");
        dataSource.setJdbcUrl("jdbc:sqlite:" + dbPath);
        dataSource.setAutoCommit(false);
        dataSource.setMinimumIdle(1);
        dataSource.setMaximumPoolSize(5);
        dataSource.setMaxLifetime(60000);
        dataSource.setIdleTimeout(30000);
        
        try (Connection connection = dataSource.getConnection()) {
            PreparedStatement preparedStatement = connection.prepareStatement("CREATE TABLE IF NOT EXISTS runtime_environment(\n" +
                    "    id INTEGER PRIMARY KEY NOT NULL,\n" +
                    "    project_hash TEXT NOT NULL,\n" +
                    "    project_path TEXT NOT NULL,\n" +
                    "    project_name TEXT NOT NULL,\n" +
                    "    module TEXT NOT NULL,\n" +
                    "    name TEXT NOT NULL,\n" +
                    "    remark TEXT,\n" +
                    "    args_value TEXT,\n" +
                    "    env_value TEXT,\n" +
                    "    vm_value TEXT,\n" +
                    "    is_default INTEGER,\n" +
                    "    created DATETIME NOT NULL,\n" +
                    "    updated DATETIME NOT NULL\n" +
                    ");");
            preparedStatement.execute();
            preparedStatement.close();
            
            preparedStatement = connection.prepareStatement("CREATE INDEX IF NOT EXISTS i_p_m ON runtime_environment (project_hash, module);");
            preparedStatement.execute();
            preparedStatement.close();

            preparedStatement = connection.prepareStatement(
                    "CREATE TABLE IF NOT EXISTS runtime_environment_active(\n" +
                    "id INTEGER PRIMARY KEY NOT NULL,\n" +
                    "project_hash TEXT,\n" +
                    "module TEXT,\n" +
                    "enabled INTEGER NOT NULL DEFAULT 0,\n" +
                    "created DATETIME NOT NULL,\n" +
                    "updated DATETIME NOT NULL,\n" +
                    "env_id INTEGER\n" +
                    ");");
            preparedStatement.execute();
            preparedStatement.close();
            
            preparedStatement = connection.prepareStatement("CREATE INDEX IF NOT EXISTS i_p_m_2 ON runtime_environment_active (project_hash, module);");
            preparedStatement.execute();
            preparedStatement.close();
            
            connection.commit();
        } catch (Throwable e) {
            throw new RuntimeException("Failed to initialize database", e);
        }
    }

    public static void getService(Consumer<RuntimeEnvironmentService> serviceConsumer) {
        if (!initialized.get() || destroyed.get() || sqlSessionFactory == null) {
            return;
        }
        try (SqlSession sqlSession = sqlSessionFactory.openSession(false)) {
            RuntimeEnvironmentMapper mapper = sqlSession.getMapper(RuntimeEnvironmentMapper.class);
            serviceConsumer.accept(new RuntimeEnvironmentService(mapper, sqlSession.getMapper(RuntimeEnvironmentActiveMapper.class)));
            sqlSession.commit();
        } catch (Throwable e) {
            // Log error but don't crash
            e.printStackTrace();
        }
    }

    public static <T> T execute(Function<RuntimeEnvironmentService, T> function) {
        if (!initialized.get() || destroyed.get() || sqlSessionFactory == null) {
            return null;
        }
        try (SqlSession sqlSession = sqlSessionFactory.openSession(false)) {
            RuntimeEnvironmentMapper mapper = sqlSession.getMapper(RuntimeEnvironmentMapper.class);
            T result = function.apply(new RuntimeEnvironmentService(mapper, sqlSession.getMapper(RuntimeEnvironmentActiveMapper.class)));
            sqlSession.commit();
            return result;
        } catch (Throwable e) {
            e.printStackTrace();
            return null;
        }
    }

    public static void destroy() {
        if (destroyed.compareAndSet(false, true)) {
            synchronized (lock) {
                try {
                    if (dataSource != null && !dataSource.isClosed()) {
                        dataSource.close();
                    }
                } catch (Throwable e) {
                    // Ignore close errors
                } finally {
                    dataSource = null;
                    sqlSessionFactory = null;
                    mybatisConfiguration = null;
                    initialized.set(false);
                }
            }
        }
    }

    public Boolean globalEnvironmentActive(){
        return getGlobalEnvironment().getIsDefault() == 1;
    }

    public void globalEnvironmentUpdateActive(){
        RuntimeEnvironment globalEnvironment = getGlobalEnvironment();
        globalEnvironment.setIsDefault(globalEnvironment.getIsDefault() == 0 ? 1 : 0);
        this.updateById(globalEnvironment);
    }

    public RuntimeEnvironment getGlobalEnvironment(){
        RuntimeEnvironment environment = this.lambdaQuery()
                .eq(RuntimeEnvironment::getId, -1)
                .one();
        if(environment == null){
            environment = new RuntimeEnvironment();
            environment.setId(-1)
                    .setName("Global")
                    .setModule("Global")
                    .setProjectPath("Global")
                    .setProjectName("Global")
                    .setProjectHash("Global")
                    .setIsDefault(0);
            this.save(environment);
        }
        return environment;
    }

    public List<RuntimeEnvironment> getRuntimeEnvironments(Project project, Module module) {
        List<RuntimeEnvironment> list = this.lambdaQuery()
                .eq(RuntimeEnvironment::getProjectHash, project.getLocationHash())
                .eq(RuntimeEnvironment::getModule, module.toString())
                .orderByAsc(RuntimeEnvironment::getId)
                .list();
        if (list.isEmpty()) {
            list = RuntimeEnvironment.buildInitList(project, module);
            for (RuntimeEnvironment runtimeEnvironment : list) {
                this.save(runtimeEnvironment);
            }
        }
        return list;
    }


    public Boolean isActive(Project project, Module module) {
        return runtimeEnvironmentActiveMapper.selectCount(new LambdaQueryWrapper<RuntimeEnvironmentActive>()
                .eq(RuntimeEnvironmentActive::getProjectHash, project.getLocationHash())
                .eq(RuntimeEnvironmentActive::getModule, module.toString())
                .eq(RuntimeEnvironmentActive::getEnabled, 1)
        ) > 0;
    }

    public void updateActive(RuntimeEnvironment runtimeEnvironment, boolean enabled) {
        RuntimeEnvironmentActive runtimeEnvironmentActive = runtimeEnvironmentActiveMapper.selectOne(new LambdaQueryWrapper<RuntimeEnvironmentActive>()
                .eq(RuntimeEnvironmentActive::getProjectHash, runtimeEnvironment.getProjectHash())
                .eq(RuntimeEnvironmentActive::getModule, runtimeEnvironment.getModule())
        );
        if(runtimeEnvironmentActive == null) {
            runtimeEnvironmentActive = new RuntimeEnvironmentActive();
            runtimeEnvironmentActive.setProjectHash(runtimeEnvironment.getProjectHash());
            runtimeEnvironmentActive.setModule(runtimeEnvironment.getModule());
        }
        runtimeEnvironmentActive.setEnabled(enabled?1:0);
        runtimeEnvironmentActive.setEnvId(runtimeEnvironment.getId());
        if(runtimeEnvironmentActive.getId() == null){
            runtimeEnvironmentActiveMapper.insert(runtimeEnvironmentActive);
        }else {
            runtimeEnvironmentActiveMapper.updateById(runtimeEnvironmentActive);
        }
    }

    public Integer getSelectEnvId(Project project, Module module) {
        RuntimeEnvironmentActive runtimeEnvironmentActive = runtimeEnvironmentActiveMapper.selectOne(new LambdaQueryWrapper<RuntimeEnvironmentActive>()
                .eq(RuntimeEnvironmentActive::getProjectHash, project.getLocationHash())
                .eq(RuntimeEnvironmentActive::getModule, module.toString())
        );
        return runtimeEnvironmentActive != null ? runtimeEnvironmentActive.getEnvId() : null;
    }


    public void updateSelectEnv(Integer runtimeEnvironmentId) {
        RuntimeEnvironment runtimeEnvironment = this.getById(runtimeEnvironmentId);
        RuntimeEnvironmentActive runtimeEnvironmentActive = runtimeEnvironmentActiveMapper.selectOne(new LambdaQueryWrapper<RuntimeEnvironmentActive>()
                .eq(RuntimeEnvironmentActive::getProjectHash, runtimeEnvironment.getProjectHash())
                .eq(RuntimeEnvironmentActive::getModule, runtimeEnvironment.getModule())
        );
        if (runtimeEnvironmentActive == null) {
            runtimeEnvironmentActive = new RuntimeEnvironmentActive();
            runtimeEnvironmentActive.setProjectHash(runtimeEnvironment.getProjectHash());
            runtimeEnvironmentActive.setModule(runtimeEnvironment.getModule());
            runtimeEnvironmentActive.setEnvId(runtimeEnvironment.getId());
            this.runtimeEnvironmentActiveMapper.insert(runtimeEnvironmentActive);
        } else {
            runtimeEnvironmentActive.setEnvId(runtimeEnvironment.getId());
            this.runtimeEnvironmentActiveMapper.updateById(runtimeEnvironmentActive);
        }
    }

    public static void main(String[] args) {
        init();
        getService(service -> {
            service.save(new RuntimeEnvironment()
                    .setArgsValue("a=b")
                    .setEnvValue("A=B")
                    .setModule("test")
                    .setProjectHash("1")
                    .setProjectName("test")
                    .setProjectPath("aaa")
                    .setName("dev")
                    .setRemark(""));
            System.out.println(service.list());
        });
    }
}
