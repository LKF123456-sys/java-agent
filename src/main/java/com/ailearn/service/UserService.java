package com.ailearn.service; // 声明包名

import com.ailearn.common.BusinessException; // 业务异常
import com.ailearn.common.ErrorCode; // 错误码
import com.ailearn.dto.LoginRequest; // 登录请求DTO
import com.ailearn.dto.RegisterRequest; // 注册请求DTO
import com.ailearn.entity.User; // 用户实体
import com.ailearn.mapper.UserMapper; // 用户Mapper
import com.ailearn.security.JwtUtil; // JWT工具
import com.ailearn.security.UserPrincipal; // 用户主体
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper; // 条件构造器
import jakarta.validation.Valid; // 参数校验注解
import jakarta.validation.constraints.NotBlank; // 非空白校验
import jakarta.validation.constraints.NotNull; // 非空校验
import lombok.RequiredArgsConstructor; // 构造器注入
import lombok.extern.slf4j.Slf4j; // 日志
import org.springframework.security.crypto.password.PasswordEncoder; // 密码编码器
import org.springframework.stereotype.Service; // Service注解
import org.springframework.transaction.annotation.Transactional; // 事务注解
import org.springframework.validation.annotation.Validated; // 校验注解

import java.util.HashMap; // 哈希表
import java.util.Map; // Map接口

/**
 * 用户服务类
 * 提供用户注册、登录、Token刷新、用户信息查询等核心功能
 * 使用BCrypt加密密码，支持双Token机制（accessToken + refreshToken）
 *
 * @author AiLearn Platform
 */
@Slf4j // 自动注入log
@Service // 业务层Bean
@Validated // 开启方法级校验
@RequiredArgsConstructor // 构造器注入
public class UserService { // 用户服务类

    /** 用户数据访问接口 */
    private final UserMapper userMapper; // 由Spring注入

    /** 密码编码器（BCrypt） */
    private final PasswordEncoder passwordEncoder; // 由Spring注入

    /** JWT工具类 */
    private final JwtUtil jwtUtil; // 由Spring注入

    /**
     * 用户注册方法
     *
     * @param req 注册请求参数
     * @return Map 包含用户信息和双Token
     * @throws BusinessException 用户名已存在时抛出
     */
    @Transactional(rollbackFor = Exception.class) // 事务
    public Map<String, Object> register(@Valid RegisterRequest req) { // 注册方法
        log.info("用户注册请求: username={}, nickname={}", req.getUsername(), req.getNickname()); // 业务日志

        // 步骤1：检查用户名是否已存在
        User existingUser = findByUsername(req.getUsername()); // 按用户名查
        if (existingUser != null) { // 已存在
            log.warn("注册失败，用户名已存在: {}", req.getUsername()); // 警告日志
            throw new BusinessException(ErrorCode.USER_USERNAME_EXISTS); // 抛异常
        }

        // 步骤2：创建新用户对象
        User user = new User(); // 创建实体
        user.setUsername(req.getUsername()); // 用户名
        // 密码使用BCrypt加密存储，不保存明文密码
        user.setPassword(passwordEncoder.encode(req.getPassword())); // BCrypt加密
        // 昵称为空时默认使用用户名
        user.setNickname(req.getNickname() != null ? req.getNickname() : req.getUsername()); // 默认昵称
        // 设置用户角色，默认普通用户
        user.setRole(req.getRole() != null ? req.getRole() : "user"); // 默认user

        // 步骤3：保存用户到数据库
        userMapper.insert(user); // 插入
        log.info("用户注册成功: userId={}, username={}", user.getId(), user.getUsername()); // 成功日志

        // 步骤4：生成不含密码的用户认证主体
        UserPrincipal userPrincipal = UserPrincipal.create( // 创建主体（无密码）
                user.getId(), // 用户ID
                user.getUsername(), // 用户名
                user.getRole() // 角色
        );

        // 步骤5：生成双Token
        String accessToken = jwtUtil.generateAccessToken(user.getId(), user.getUsername(), user.getRole()); // 访问令牌
        String refreshToken = jwtUtil.generateRefreshToken(user.getId(), user.getUsername(), user.getRole()); // 刷新令牌
        log.debug("为新用户生成Token: userId={}", user.getId()); // 调试日志

        // 步骤6：封装返回结果
        Map<String, Object> result = new HashMap<>(); // 结果Map
        result.put("user", userPrincipal); // 用户信息
        result.put("accessToken", accessToken); // 访问令牌
        result.put("refreshToken", refreshToken); // 刷新令牌

        return result; // 返回
    }

    /**
     * 用户登录方法
     *
     * @param req 登录请求参数
     * @return Map 包含用户信息和双Token
     * @throws BusinessException 用户名不存在或密码错误时抛出
     */
    public Map<String, Object> login(@Valid LoginRequest req) { // 登录方法
        log.info("用户登录请求: username={}", req.getUsername()); // 业务日志

        // 步骤1：根据用户名查询用户
        User user = findByUsername(req.getUsername()); // 按用户名查
        if (user == null) { // 不存在
            log.warn("登录失败，用户不存在: {}", req.getUsername()); // 警告日志
            throw new BusinessException(ErrorCode.AUTH_LOGIN_FAILED); // 抛异常
        }

        // 步骤2：使用BCrypt验证密码（不使用明文比较）
        if (!passwordEncoder.matches(req.getPassword(), user.getPassword())) { // 密码不匹配
            log.warn("登录失败，密码错误: username={}", req.getUsername()); // 警告日志
            throw new BusinessException(ErrorCode.AUTH_LOGIN_FAILED); // 抛异常
        }

        // 步骤3：登录成功，生成不含密码的用户认证主体
        UserPrincipal userPrincipal = UserPrincipal.create( // 创建主体
                user.getId(), // 用户ID
                user.getUsername(), // 用户名
                user.getRole() // 角色
        );
        log.info("用户登录成功: userId={}, username={}", user.getId(), user.getUsername()); // 成功日志

        // 步骤4：生成双Token
        String accessToken = jwtUtil.generateAccessToken(user.getId(), user.getUsername(), user.getRole()); // 访问令牌
        String refreshToken = jwtUtil.generateRefreshToken(user.getId(), user.getUsername(), user.getRole()); // 刷新令牌

        // 步骤5：封装返回结果
        Map<String, Object> result = new HashMap<>(); // 结果Map
        result.put("user", userPrincipal); // 用户信息
        result.put("accessToken", accessToken); // 访问令牌
        result.put("refreshToken", refreshToken); // 刷新令牌

        return result; // 返回
    }

    /**
     * 刷新访问令牌方法
     *
     * @param refreshToken 刷新令牌字符串
     * @return Map 包含新的accessToken和refreshToken
     * @throws BusinessException Refresh Token无效时抛出
     */
    public Map<String, String> refreshToken(@NotBlank(message = "Refresh Token不能为空") String refreshToken) { // 刷新令牌
        log.info("Token刷新请求"); // 业务日志

        if (!jwtUtil.validateRefreshToken(refreshToken)) { // 刷新令牌无效
            log.warn("Token刷新失败，Refresh Token无效或已过期"); // 警告日志
            throw new BusinessException(ErrorCode.AUTH_TOKEN_INVALID); // 抛异常
        }

        String tokenType = jwtUtil.extractTokenType(refreshToken); // 取令牌类型
        if (!JwtUtil.TOKEN_TYPE_REFRESH.equals(tokenType)) { // 类型不是refresh
            log.warn("Token刷新失败，Token类型错误: {}", tokenType); // 警告日志
            throw new BusinessException(ErrorCode.AUTH_TOKEN_INVALID); // 抛异常
        }

        Long userId = jwtUtil.extractUserId(refreshToken); // 提取用户ID
        String username = jwtUtil.extractUsername(refreshToken); // 提取用户名
        String role = jwtUtil.extractRole(refreshToken); // 提取角色

        if (userId == null || username == null || role == null) { // 信息不完整
            log.error("Token刷新失败，无法从Token中提取用户信息"); // 错误日志
            throw new BusinessException(ErrorCode.AUTH_TOKEN_INVALID); // 抛异常
        }

        String newAccessToken = jwtUtil.generateAccessToken(userId, username, role); // 新访问令牌
        String newRefreshToken = jwtUtil.generateRefreshToken(userId, username, role); // 新刷新令牌
        log.info("Token刷新成功（双Token轮换）: userId={}", userId); // 成功日志

        Map<String, String> tokens = new HashMap<>(); // 结果Map
        tokens.put("accessToken", newAccessToken); // 访问令牌
        tokens.put("refreshToken", newRefreshToken); // 刷新令牌
        return tokens; // 返回
    }

    /**
     * 根据用户名查询用户
     *
     * @param username 用户名
     * @return User 用户实体，未找到返回null
     */
    public User findByUsername(@NotBlank(message = "用户名不能为空") String username) { // 按用户名查
        log.debug("根据用户名查询用户: {}", username); // 调试日志
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>(); // 条件构造器
        wrapper.eq(User::getUsername, username); // 条件：username = 参数
        return userMapper.selectOne(wrapper); // 查询单条
    }

    /**
     * 根据用户名查询用户（getUserByUsername别名方法）
     *
     * @param username 用户名
     * @return User 用户实体
     */
    public User getUserByUsername(@NotBlank(message = "用户名不能为空") String username) { // 别名方法
        return findByUsername(username); // 委托
    }

    /**
     * 根据用户ID查询用户
     *
     * @param id 用户ID
     * @return User 用户实体
     * @throws BusinessException 用户不存在时抛出
     */
    public User getUserById(@NotNull(message = "用户ID不能为空") Long id) { // 按ID查
        log.debug("根据ID查询用户: userId={}", id); // 调试日志
        User user = userMapper.selectById(id); // 按主键查
        if (user == null) { // 不存在
            log.warn("查询用户失败，用户不存在: userId={}", id); // 警告日志
            throw new BusinessException(ErrorCode.USER_NOT_FOUND); // 抛异常
        }
        return user; // 返回
    }

    /**
     * 创建用户方法（内部使用）
     *
     * @param username 用户名
     * @param nickname 昵称
     * @param password 密码
     * @return User 创建后的用户实体
     */
    @Transactional(rollbackFor = Exception.class) // 事务
    public User createUser(String username, String nickname, String password) { // 创建用户
        log.info("创建用户: username={}", username); // 业务日志
        // 查询当前用户数量，第一个注册的用户为管理员
        long count = userMapper.selectCount(null); // 统计用户数
        String role = count == 0 ? "admin" : "user"; // 第一个=admin，其余=user

        User user = new User(); // 创建实体
        user.setUsername(username); // 用户名
        user.setNickname(nickname != null ? nickname : username); // 昵称默认用户名
        user.setPassword(password); // 密码
        user.setRole(role); // 角色
        userMapper.insert(user); // 插入

        log.info("用户创建成功: userId={}, role={}", user.getId(), role); // 成功日志
        return user; // 返回
    }

    /**
     * 更新用户昵称方法
     *
     * @param userId   用户ID
     * @param nickname 新昵称
     * @return User 更新后的用户实体
     * @throws BusinessException 用户不存在时抛出
     */
    @Transactional(rollbackFor = Exception.class) // 事务
    public User updateNickname(@NotNull(message = "用户ID不能为空") Long userId, String nickname) { // 更新昵称
        log.info("更新用户昵称: userId={}, newNickname={}", userId, nickname); // 业务日志
        User user = userMapper.selectById(userId); // 按ID查
        if (user == null) { // 不存在
            log.warn("更新昵称失败，用户不存在: userId={}", userId); // 警告日志
            throw new BusinessException(ErrorCode.USER_NOT_FOUND); // 抛异常
        }

        if (nickname != null) { // 昵称非空
            user.setNickname(nickname); // 设置新昵称
            userMapper.updateById(user); // 更新
            log.info("用户昵称更新成功: userId={}", userId); // 成功日志
        }
        return user; // 返回
    }

    /**
     * 创建或更新用户方法
     *
     * @param username 用户名
     * @param nickname 昵称
     * @return User 创建或更新后的用户实体
     */
    @Transactional(rollbackFor = Exception.class) // 事务
    public User createOrUpdateUser(String username, String nickname) { // 创建或更新
        log.info("创建或更新用户: username={}", username); // 业务日志
        User user = findByUsername(username); // 按用户名查
        if (user == null) { // 不存在
            log.debug("用户不存在，创建新用户: {}", username); // 调试日志
            return createUser(username, nickname, null); // 创建新用户
        } else { // 已存在
            if (nickname != null && !nickname.equals(user.getNickname())) { // 昵称不同
                log.debug("用户已存在，更新昵称: username={}", username); // 调试日志
                user.setNickname(nickname); // 更新昵称
                userMapper.updateById(user); // 更新数据库
            }
            return user; // 返回已有用户
        }
    }
} // UserService类结束
