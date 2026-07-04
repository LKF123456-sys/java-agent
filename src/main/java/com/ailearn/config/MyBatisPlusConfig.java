package com.ailearn.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.reflection.MetaObject;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus配置类
 * 负责配置MyBatis-Plus的核心功能，包括：
 * 1. 分页插件：支持MySQL数据库的物理分页查询
 * 2. 自动填充处理器：插入/更新时自动填充createdAt、updatedAt时间戳字段
 * 3. Mapper扫描：通过@MapperScan自动扫描Mapper接口
 *
 * @author AiLearn Platform
 */
@Slf4j
@Configuration
@MapperScan("com.ailearn.mapper")
public class MyBatisPlusConfig {

    /**
     * 配置MyBatis-Plus核心插件拦截器
     * 目前添加了分页内部拦截器（PaginationInnerInterceptor），后续可按需添加其他插件：
     * - 乐观锁插件：OptimisticLockerInnerInterceptor
     * - 防全表更新与删除插件：BlockAttackInnerInterceptor
     * - 性能分析插件：ProfileInnerInterceptor（开发环境用）
     *
     * 分页插件说明：
     * - 使用物理分页（LIMIT语句），而非内存分页，性能更好
     * - DbType.MYSQL指定数据库类型为MySQL，生成正确的分页SQL方言
     * - 如果使用PostgreSQL，将DbType改为POSTGRESQL即可
     *
     * @return MybatisPlusInterceptor MyBatis-Plus插件拦截器实例
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        // 创建MyBatis-Plus插件主体拦截器
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 创建分页内部拦截器，指定数据库类型为MySQL
        // 该拦截器会在SQL执行前自动拦截并添加LIMIT分页语句
        PaginationInnerInterceptor paginationInterceptor = new PaginationInnerInterceptor(DbType.MYSQL);
        // 设置单页分页条数限制，-1表示不限制（默认500）
        // 防止一次查询过多数据导致内存溢出
        paginationInterceptor.setMaxLimit(1000L);
        // 设置溢出总页数后是否进行处理：true表示回到首页，false表示继续请求（默认false）
        paginationInterceptor.setOverflow(false);
        // 将分页拦截器添加到插件链中
        interceptor.addInnerInterceptor(paginationInterceptor);
        // 记录分页插件配置完成日志
        log.info("MyBatis-Plus分页插件已配置（数据库类型：MySQL，单页最大条数：1000）");
        return interceptor;
    }

    /**
     * 配置MyBatis-Plus自动填充处理器
     * 实现MetaObjectHandler接口，在插入和更新操作时自动填充指定字段
     * 需要在实体类字段上使用@TableField注解的fill属性指定填充策略：
     * - @TableField(fill = FieldFill.INSERT)：插入时填充
     * - @TableField(fill = FieldFill.INSERT_UPDATE)：插入和更新时都填充
     *
     * 支持的自动填充字段：
     * - createdAt：记录创建时间，插入时自动填充
     * - updatedAt：记录更新时间，插入和更新时都自动填充
     *
     * 使用strictInsertFill和strictUpdateFill为严格填充模式：
     * - 只有当字段值为null时才会填充，不会覆盖已有值
     * - 如果实体类中没有该字段，不会报错
     *
     * @return MetaObjectHandler 元对象字段填充处理器
     */
    @Bean
    public MetaObjectHandler metaObjectHandler() {
        return new MetaObjectHandler() {

            /**
             * 插入操作时的自动填充策略
             * 在执行INSERT语句前被调用，自动填充创建时间和更新时间
             *
             * @param metaObject MyBatis的元对象，包含当前操作的实体对象信息
             */
            @Override
            public void insertFill(MetaObject metaObject) {
                // 严格模式填充createdAt字段为当前时间
                // 参数：元对象、字段名、字段类型、填充值
                this.strictInsertFill(metaObject, "createdAt", LocalDateTime.class, LocalDateTime.now());
                // 严格模式填充updatedAt字段为当前时间（插入时更新时间与创建时间相同）
                this.strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
            }

            /**
             * 更新操作时的自动填充策略
             * 在执行UPDATE语句前被调用，自动更新更新时间字段
             *
             * @param metaObject MyBatis的元对象，包含当前操作的实体对象信息
             */
            @Override
            public void updateFill(MetaObject metaObject) {
                // 严格模式填充updatedAt字段为当前时间
                // 注意：更新时不修改createdAt字段，保持创建时间不变
                this.strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
            }
        };
    }
}
