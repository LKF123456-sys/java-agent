package com.ailearn.security; // 声明包名，security包存放认证授权相关类

import lombok.AllArgsConstructor; // Lombok全参构造器
import lombok.Builder; // Lombok建造者
import lombok.Data; // Lombok getter/setter等
import lombok.NoArgsConstructor; // Lombok无参构造器
import org.springframework.security.core.GrantedAuthority; // Spring Security权限接口
import org.springframework.security.core.authority.SimpleGrantedAuthority; // 权限简单实现
import org.springframework.security.core.userdetails.UserDetails; // 用户详情接口（Spring Security核心）

import java.util.Collection; // 集合接口
import java.util.Collections; // 集合工具类

/**
 * 用户认证主体类
 * 实现Spring Security的UserDetails接口，封装用户认证信息
 * 包含用户ID、用户名、密码、角色和权限信息
 * 提供静态工厂方法create()用于创建实例
 * 所有用户状态均为启用状态（系统不做账号锁定/禁用功能）
 *
 * @author AiLearn Platform
 */
@Data // 自动生成getter/setter
@Builder // 启用建造者模式
@NoArgsConstructor // 无参构造器
@AllArgsConstructor // 全参构造器
public class UserPrincipal implements UserDetails { // 实现UserDetails接口

    /**
     * 序列化版本UID
     * 用于Java序列化机制的版本控制，确保序列化和反序列化时类版本一致
     */
    private static final long serialVersionUID = 1L; // 序列化版本号

    /**
     * 用户ID
     * 数据库中的唯一用户标识
     */
    private Long userId; // 用户ID

    /**
     * 用户名
     * 用于登录认证的唯一标识
     */
    private String username; // 用户名

    /**
     * 密码（加密后）
     * 使用BCrypt加密存储
     */
    private String password; // 加密密码

    /**
     * 用户角色
     * 如：USER、ADMIN等
     */
    private String role; // 角色

    /**
     * 用户权限集合
     * Spring Security用于权限控制的GrantedAuthority集合
     */
    private Collection<? extends GrantedAuthority> authorities; // 权限集合

    /**
     * 静态工厂方法：创建UserPrincipal实例
     * 根据用户ID、用户名、密码、角色构建用户认证主体
     * 自动根据角色生成对应的GrantedAuthority（前缀ROLE_）
     *
     * @param userId   用户ID
     * @param username 用户名
     * @param password 加密后的密码
     * @param role     用户角色
     * @return UserPrincipal 用户认证主体实例
     */
    public static UserPrincipal create(Long userId, String username, String password, String role) { // 静态工厂（全参数）
        SimpleGrantedAuthority authority = new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()); // 构造权限，加ROLE_前缀
        return UserPrincipal.builder() // 启动建造者
                .userId(userId) // 用户ID
                .username(username) // 用户名
                .password(password) // 密码
                .role(role) // 角色
                .authorities(Collections.singletonList(authority)) // 单元素不可变权限集合
                .build(); // 构建
    }

    /**
     * 静态工厂方法：创建UserPrincipal实例（无密码版本）
     * 适用于从JWT Token解析后构建用户信息（此时不需要密码）
     *
     * @param userId   用户ID
     * @param username 用户名
     * @param role     用户角色
     * @return UserPrincipal 用户认证主体实例
     */
    public static UserPrincipal create(Long userId, String username, String role) { // 静态工厂（无密码）
        return create(userId, username, null, role); // 委托全参工厂，密码传null
    }

    /**
     * 获取用户权限集合
     * 实现UserDetails接口的方法，返回用户的所有权限
     *
     * @return Collection<? extends GrantedAuthority> 权限集合
     */
    @Override // 接口方法实现
    public Collection<? extends GrantedAuthority> getAuthorities() { // 获取权限集合
        return authorities; // 返回权限集合
    }

    /**
     * 获取用户密码
     * 实现UserDetails接口的方法
     *
     * @return String 加密后的密码
     */
    @Override // 接口方法实现
    public String getPassword() { // 获取密码
        return password; // 返回密码
    }

    /**
     * 获取用户名
     * 实现UserDetails接口的方法
     *
     * @return String 用户名
     */
    @Override // 接口方法实现
    public String getUsername() { // 获取用户名
        return username; // 返回用户名
    }

    /**
     * 账号是否未过期
     * 系统不做账号过期功能，始终返回true
     *
     * @return boolean true表示未过期
     */
    @Override // 接口方法实现
    public boolean isAccountNonExpired() { // 账号未过期
        return true; // 始终true
    }

    /**
     * 账号是否未锁定
     * 系统不做账号锁定功能，始终返回true
     *
     * @return boolean true表示未锁定
     */
    @Override // 接口方法实现
    public boolean isAccountNonLocked() { // 账号未锁定
        return true; // 始终true
    }

    /**
     * 凭证是否未过期
     * 系统不做凭证过期功能，始终返回true
     *
     * @return boolean true表示凭证未过期
     */
    @Override // 接口方法实现
    public boolean isCredentialsNonExpired() { // 凭证未过期
        return true; // 始终true
    }

    /**
     * 账号是否启用
     * 系统所有用户均为启用状态，始终返回true
     *
     * @return boolean true表示启用
     */
    @Override // 接口方法实现
    public boolean isEnabled() { // 账号启用
        return true; // 始终true
    }
} // UserPrincipal类结束
