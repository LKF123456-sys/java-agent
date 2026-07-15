package com.ailearn.service;

import com.ailearn.common.BusinessException;
import com.ailearn.common.ErrorCode;
import com.ailearn.dto.LoginRequest;
import com.ailearn.dto.RegisterRequest;
import com.ailearn.entity.User;
import com.ailearn.mapper.UserMapper;
import com.ailearn.security.JwtUtil;
import com.ailearn.security.UserPrincipal;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.HashMap;
import java.util.Map;

/**
 * 用户服务类
 * 提供用户注册、登录、Token刷新、用户信息查询等核心功能
 * 使用BCrypt加密密码，支持双Token机制（accessToken + refreshToken）
 *
 * @author AiLearn Platform
 */
@Slf4j
@Service
@Validated
@RequiredArgsConstructor
public class UserService {

    /**
     * 用户数据访问接口
     * 用于sys_user表的CRUD操作
     */
    private final UserMapper userMapper;

    /**
     * 密码编码器
     * 使用BCrypt算法进行密码加密和验证，Spring Security自动注入
     */
    private final PasswordEncoder passwordEncoder;

    /**
     * JWT工具类
     * 用于生成和验证JWT Token
     */
    private final JwtUtil jwtUtil;

    /**
     * 用户注册方法
     * 处理新用户注册逻辑，包括用户名唯一性校验、密码加密、用户信息保存和Token生成
     *
     * @param req 注册请求参数，包含用户名、密码、昵称等信息，使用@Valid注解自动校验参数
     * @return Map&lt;String, Object&gt; 包含用户信息（不含密码）和双Token的响应数据
     *         - user: UserPrincipal 用户认证主体信息（不含密码）
     *         - accessToken: String 短期访问令牌（2小时有效期）
     *         - refreshToken: String 长期刷新令牌（7天有效期）
     * @throws BusinessException 当用户名已存在时抛出USER_USERNAME_EXISTS异常
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> register(@Valid RegisterRequest req) {
        log.info("用户注册请求: username={}, nickname={}", req.getUsername(), req.getNickname());

        // 步骤1：检查用户名是否已存在
        User existingUser = findByUsername(req.getUsername());
        if (existingUser != null) {
            log.warn("注册失败，用户名已存在: {}", req.getUsername());
            throw new BusinessException(ErrorCode.USER_USERNAME_EXISTS);
        }

        // 步骤2：创建新用户对象
        User user = new User();
        user.setUsername(req.getUsername());
        // 密码使用BCrypt加密存储，不保存明文密码
        user.setPassword(passwordEncoder.encode(req.getPassword()));
        // 昵称为空时默认使用用户名
        user.setNickname(req.getNickname() != null ? req.getNickname() : req.getUsername());
        // 设置用户角色，默认使用请求中的角色或普通用户角色
        user.setRole(req.getRole() != null ? req.getRole() : "user");

        // 步骤3：保存用户到数据库
        userMapper.insert(user);
        log.info("用户注册成功: userId={}, username={}", user.getId(), user.getUsername());

        // 步骤4：生成不含密码的用户认证主体（返回给前端时不暴露密码）
        UserPrincipal userPrincipal = UserPrincipal.create(
                user.getId(),
                user.getUsername(),
                user.getRole()
        );

        // 步骤5：生成双Token
        String accessToken = jwtUtil.generateAccessToken(user.getId(), user.getUsername(), user.getRole());
        String refreshToken = jwtUtil.generateRefreshToken(user.getId(), user.getUsername(), user.getRole());
        log.debug("为新用户生成Token: userId={}", user.getId());

        // 步骤6：封装返回结果
        Map<String, Object> result = new HashMap<>();
        result.put("user", userPrincipal);
        result.put("accessToken", accessToken);
        result.put("refreshToken", refreshToken);

        return result;
    }

    /**
     * 用户登录方法
     * 处理用户登录逻辑，验证用户名和密码，生成双Token返回
     *
     * @param req 登录请求参数，包含用户名和密码，使用@Valid注解自动校验参数
     * @return Map&lt;String, Object&gt; 包含用户信息和双Token的响应数据
     *         - user: UserPrincipal 用户认证主体信息（不含密码）
     *         - accessToken: String 短期访问令牌
     *         - refreshToken: String 长期刷新令牌
     * @throws BusinessException 当用户名不存在或密码错误时抛出AUTH_LOGIN_FAILED异常
     */
    public Map<String, Object> login(@Valid LoginRequest req) {
        log.info("用户登录请求: username={}", req.getUsername());

        // 步骤1：根据用户名查询用户
        User user = findByUsername(req.getUsername());
        if (user == null) {
            log.warn("登录失败，用户不存在: {}", req.getUsername());
            throw new BusinessException(ErrorCode.AUTH_LOGIN_FAILED);
        }

        // 步骤2：使用BCrypt验证密码（不使用明文比较）
        if (!passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            log.warn("登录失败，密码错误: username={}", req.getUsername());
            throw new BusinessException(ErrorCode.AUTH_LOGIN_FAILED);
        }

        // 步骤3：登录成功，生成不含密码的用户认证主体
        UserPrincipal userPrincipal = UserPrincipal.create(
                user.getId(),
                user.getUsername(),
                user.getRole()
        );
        log.info("用户登录成功: userId={}, username={}", user.getId(), user.getUsername());

        // 步骤4：生成双Token
        String accessToken = jwtUtil.generateAccessToken(user.getId(), user.getUsername(), user.getRole());
        String refreshToken = jwtUtil.generateRefreshToken(user.getId(), user.getUsername(), user.getRole());

        // 步骤5：封装返回结果
        Map<String, Object> result = new HashMap<>();
        result.put("user", userPrincipal);
        result.put("accessToken", accessToken);
        result.put("refreshToken", refreshToken);

        return result;
    }

    /**
     * 刷新访问令牌方法
     * 使用有效的Refresh Token生成新的Access Token
     *
     * @param refreshToken 刷新令牌字符串，不能为空
     * @return String 新生成的Access Token
     * @throws BusinessException 当Refresh Token无效或类型错误时抛出AUTH_TOKEN_INVALID异常
     */
    public Map<String, String> refreshToken(@NotBlank(message = "Refresh Token不能为空") String refreshToken) {
        log.info("Token刷新请求");

        if (!jwtUtil.validateRefreshToken(refreshToken)) {
            log.warn("Token刷新失败，Refresh Token无效或已过期");
            throw new BusinessException(ErrorCode.AUTH_TOKEN_INVALID);
        }

        String tokenType = jwtUtil.extractTokenType(refreshToken);
        if (!JwtUtil.TOKEN_TYPE_REFRESH.equals(tokenType)) {
            log.warn("Token刷新失败，Token类型错误: {}", tokenType);
            throw new BusinessException(ErrorCode.AUTH_TOKEN_INVALID);
        }

        Long userId = jwtUtil.extractUserId(refreshToken);
        String username = jwtUtil.extractUsername(refreshToken);
        String role = jwtUtil.extractRole(refreshToken);

        if (userId == null || username == null || role == null) {
            log.error("Token刷新失败，无法从Token中提取用户信息");
            throw new BusinessException(ErrorCode.AUTH_TOKEN_INVALID);
        }

        String newAccessToken = jwtUtil.generateAccessToken(userId, username, role);
        String newRefreshToken = jwtUtil.generateRefreshToken(userId, username, role);
        log.info("Token刷新成功（双Token轮换）: userId={}", userId);

        Map<String, String> tokens = new HashMap<>();
        tokens.put("accessToken", newAccessToken);
        tokens.put("refreshToken", newRefreshToken);
        return tokens;
    }

    /**
     * 根据用户名查询用户
     * 使用MyBatis-Plus的Lambda查询构造器进行条件查询
     *
     * @param username 用户名，不能为空
     * @return User 用户实体对象，如果未找到返回null
     */
    public User findByUsername(@NotBlank(message = "用户名不能为空") String username) {
        log.debug("根据用户名查询用户: {}", username);
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername, username);
        return userMapper.selectOne(wrapper);
    }

    /**
     * 根据用户名查询用户（getUserByUsername别名方法）
     * 提供语义更明确的方法名用于Controller层调用
     *
     * @param username 用户名，不能为空
     * @return User 用户实体对象，如果未找到返回null
     */
    public User getUserByUsername(@NotBlank(message = "用户名不能为空") String username) {
        return findByUsername(username);
    }

    /**
     * 根据用户ID查询用户
     *
     * @param id 用户ID，不能为空
     * @return User 用户实体对象
     * @throws BusinessException 当用户不存在时抛出USER_NOT_FOUND异常
     */
    public User getUserById(@NotNull(message = "用户ID不能为空") Long id) {
        log.debug("根据ID查询用户: userId={}", id);
        User user = userMapper.selectById(id);
        if (user == null) {
            log.warn("查询用户失败，用户不存在: userId={}", id);
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        return user;
    }

    /**
     * 创建用户方法（内部使用）
     * 用于系统内部创建用户，保留原有功能逻辑
     *
     * @param username 用户名
     * @param nickname 昵称
     * @param password 密码（已加密或明文，如果为明文需外部自行加密）
     * @return User 创建后的用户实体（包含自增ID）
     */
    @Transactional(rollbackFor = Exception.class)
    public User createUser(String username, String nickname, String password) {
        log.info("创建用户: username={}", username);
        // 查询当前用户数量，第一个注册的用户为管理员
        long count = userMapper.selectCount(null);
        String role = count == 0 ? "admin" : "user";

        User user = new User();
        user.setUsername(username);
        user.setNickname(nickname != null ? nickname : username);
        user.setPassword(password);
        user.setRole(role);
        userMapper.insert(user);

        log.info("用户创建成功: userId={}, role={}", user.getId(), role);
        return user;
    }

    /**
     * 更新用户昵称方法
     * 保留原有功能逻辑，添加异常处理和日志
     *
     * @param userId   用户ID
     * @param nickname 新昵称
     * @return User 更新后的用户实体
     * @throws BusinessException 当用户不存在时抛出USER_NOT_FOUND异常
     */
    @Transactional(rollbackFor = Exception.class)
    public User updateNickname(@NotNull(message = "用户ID不能为空") Long userId, String nickname) {
        log.info("更新用户昵称: userId={}, newNickname={}", userId, nickname);
        User user = userMapper.selectById(userId);
        if (user == null) {
            log.warn("更新昵称失败，用户不存在: userId={}", userId);
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        if (nickname != null) {
            user.setNickname(nickname);
            userMapper.updateById(user);
            log.info("用户昵称更新成功: userId={}", userId);
        }
        return user;
    }

    /**
     * 创建或更新用户方法
     * 如果用户名不存在则创建新用户，存在则更新昵称
     * 保留原有功能逻辑
     *
     * @param username 用户名
     * @param nickname 昵称
     * @return User 创建或更新后的用户实体
     */
    @Transactional(rollbackFor = Exception.class)
    public User createOrUpdateUser(String username, String nickname) {
        log.info("创建或更新用户: username={}", username);
        User user = findByUsername(username);
        if (user == null) {
            log.debug("用户不存在，创建新用户: {}", username);
            return createUser(username, nickname, null);
        } else {
            if (nickname != null && !nickname.equals(user.getNickname())) {
                log.debug("用户已存在，更新昵称: username={}", username);
                user.setNickname(nickname);
                userMapper.updateById(user);
            }
            return user;
        }
    }
}
