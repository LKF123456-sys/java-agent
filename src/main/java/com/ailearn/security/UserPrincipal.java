package com.ailearn.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;

/**
 * 用户认证主体类
 * 实现Spring Security的UserDetails接口，封装用户认证信息
 * 包含用户ID、用户名、密码、角色和权限信息
 * 提供静态工厂方法create()用于创建实例
 * 所有用户状态均为启用状态（系统不做账号锁定/禁用功能）
 *
 * @author AiLearn Platform
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPrincipal implements UserDetails {

    /**
     * 序列化版本UID
     * 用于Java序列化机制的版本控制，确保序列化和反序列化时类版本一致
     */
    private static final long serialVersionUID = 1L;

    /**
     * 用户ID
     * 数据库中的唯一用户标识
     */
    private Long userId;

    /**
     * 用户名
     * 用于登录认证的唯一标识
     */
    private String username;

    /**
     * 密码（加密后）
     * 使用BCrypt加密存储
     */
    private String password;

    /**
     * 用户角色
     * 如：USER、ADMIN等
     */
    private String role;

    /**
     * 用户权限集合
     * Spring Security用于权限控制的GrantedAuthority集合
     */
    private Collection<? extends GrantedAuthority> authorities;

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
    public static UserPrincipal create(Long userId, String username, String password, String role) {
        SimpleGrantedAuthority authority = new SimpleGrantedAuthority("ROLE_" + role.toUpperCase());
        return UserPrincipal.builder()
                .userId(userId)
                .username(username)
                .password(password)
                .role(role)
                .authorities(Collections.singletonList(authority))
                .build();
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
    public static UserPrincipal create(Long userId, String username, String role) {
        return create(userId, username, null, role);
    }

    /**
     * 获取用户权限集合
     * 实现UserDetails接口的方法，返回用户的所有权限
     *
     * @return Collection<? extends GrantedAuthority> 权限集合
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    /**
     * 获取用户密码
     * 实现UserDetails接口的方法
     *
     * @return String 加密后的密码
     */
    @Override
    public String getPassword() {
        return password;
    }

    /**
     * 获取用户名
     * 实现UserDetails接口的方法
     *
     * @return String 用户名
     */
    @Override
    public String getUsername() {
        return username;
    }

    /**
     * 账号是否未过期
     * 系统不做账号过期功能，始终返回true
     *
     * @return boolean true表示未过期
     */
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    /**
     * 账号是否未锁定
     * 系统不做账号锁定功能，始终返回true
     *
     * @return boolean true表示未锁定
     */
    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    /**
     * 凭证是否未过期
     * 系统不做凭证过期功能，始终返回true
     *
     * @return boolean true表示凭证未过期
     */
    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    /**
     * 账号是否启用
     * 系统所有用户均为启用状态，始终返回true
     *
     * @return boolean true表示启用
     */
    @Override
    public boolean isEnabled() {
        return true;
    }
}
