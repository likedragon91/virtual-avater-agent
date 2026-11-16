package com.example.javaweb.service;


import cn.hutool.core.util.IdUtil;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.javaweb.dto.request.UpdateProfileCommandDTO;
import com.example.javaweb.dto.request.UpdateUserStatusCommandDTO;
import com.example.javaweb.dto.request.UserLoginCommandDTO;
import com.example.javaweb.dto.request.UserRegisterCommandDTO;
import com.example.javaweb.dto.response.OnlineUserResponseDTO;
import com.example.javaweb.dto.response.UserListResponseDTO;
import com.example.javaweb.dto.response.UserLoginResponseDTO;
import com.example.javaweb.config.JWTConfig;
import com.example.javaweb.entity.User;
import com.example.javaweb.enums.ClientType;
import com.example.javaweb.enums.UserStatus;
import com.example.javaweb.enums.UserType;
import com.example.javaweb.exception.BusinessException;
import com.example.javaweb.mapper.UserMapper;
import com.example.javaweb.util.JWTTokenUtil;
import com.example.javaweb.util.Sm3Util;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import com.example.javaweb.common.Result;
import com.example.javaweb.common.ResultCode;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;



@Service
public class UserService {
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Resource
    private UserMapper userMapper;

    @Resource
    private JWTTokenUtil jwtTokenUtil;

    @Resource
    private JWTConfig jwtConfig;

    @Resource
    private CaptchaService captchaService;

    @Resource
    private OnlineUserService onlineUserService;

    @Resource
    private TokenService tokenService;

    @Resource
    private UserAuthStateService userAuthStateService;

    public Result<UserLoginResponseDTO> login(UserLoginCommandDTO commandDTO){
        //校验验证码（放在最前面，防止密码被暴力枚举）
        captchaService.verify(commandDTO.getCaptchaId(), commandDTO.getCaptchaCode());

        //构建查询条件
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getUsername,commandDTO.getUsername())
                .or()
                .eq(User::getEmail,commandDTO.getUsername());
        //调用api查询
        User user = userMapper.selectOne(queryWrapper);

        //用户不存在
        if (user == null) {
            throw new BusinessException("用户名或密码错误");
        }
        //密码校验：前端密文 + 该用户的盐 再做一次 SM3，与库中哈希比对
        String salt = user.getSalt();
        if (salt == null || salt.isBlank()) {
            //历史账号（BCrypt 存储）无法用前端 SM3 密文校验，需要重置密码
            throw new BusinessException("账号密码体系已升级，请联系管理员重置密码");
        }
        String passwordHash = Sm3Util.backendEncrypt(commandDTO.getPassword().trim(), salt);
        if (!Sm3Util.constantTimeEquals(passwordHash, user.getPassword())) {
            throw new BusinessException("密码错误");
        }

        //检查用户状态
        if(!user.isActive()){
            throw new BusinessException("用户已被禁用，请联系管理员启用");
        }

        //登录成功：签发 Access + Refresh，写入 Redis 白名单并登记在线会话
        ClientType clientType = ClientType.from(commandDTO.getClientType());
        long accessTtl = jwtConfig.accessExpiration(clientType);
        long refreshTtl = jwtConfig.refreshExpiration(clientType);
        String sid = IdUtil.fastSimpleUUID();
        String accessJti = IdUtil.fastSimpleUUID();
        String refreshJti = IdUtil.fastSimpleUUID();
        String token = jwtTokenUtil.generateAccessToken(
                user.getId().toString(), user.getUsername(), user.getUserType(),
                sid, clientType, accessJti);
        String refreshToken = jwtTokenUtil.generateRefreshToken(
                user.getId().toString(), user.getUsername(), user.getUserType(),
                sid, clientType, refreshJti);
        tokenService.saveSession(sid, accessJti, refreshJti, user.getId(),
                clientType.name(), accessTtl, refreshTtl);
        onlineUserService.register(token, user.getId(), user.getUsername(), user.getUserType());

        //组装返回数据
        UserLoginResponseDTO dto = new UserLoginResponseDTO();
        dto.setToken(token);
        dto.setRefreshToken(refreshToken);
        dto.setExpiresIn(accessTtl / 1000L);
        dto.setClientType(clientType.name());
        dto.setRoleType(resolve(user.getUserTypeDisplayName(), mapUserType(user.getUserType())));

        dto.setUserInfo(toUserDetail(user));

        return Result.success(dto);
    }

    //获取当前登录用户信息
    public Result<UserLoginResponseDTO.UserDetailResponseDTO> getMessage(String username){
        //根据用户名查询用户
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getUsername, username);
        User user = userMapper.selectOne(queryWrapper);

        //用户不存在
        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        return Result.success(toUserDetail(user));
    }

    //用户列表（分页，支持按账号/昵称模糊搜索）
    public Result<UserListResponseDTO> listUsers(long page, long size, String keyword){
        //分页参数兜底，避免非法值
        long currentPage = Math.max(page, 1);
        long pageSize = Math.min(Math.max(size, 1), 100);

        //构建查询条件
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            queryWrapper.and(wrapper -> wrapper
                    .like(User::getUsername, kw)
                    .or()
                    .like(User::getNickname, kw));
        }

        //先查总记录数
        Long total = userMapper.selectCount(queryWrapper);

        //再查当前页数据（pageSize、offset 均为数值，不存在 SQL 注入）
        long offset = (currentPage - 1) * pageSize;
        queryWrapper.orderByDesc(User::getId)
                .last("LIMIT " + pageSize + " OFFSET " + offset);
        List<User> users = userMapper.selectList(queryWrapper);

        //实体转响应 DTO
        List<UserLoginResponseDTO.UserDetailResponseDTO> records = users.stream()
                .map(UserService::toUserDetail)
                .collect(Collectors.toList());

        UserListResponseDTO result = new UserListResponseDTO();
        result.setRecords(records);
        result.setTotal(total);
        result.setPage(currentPage);
        result.setSize(pageSize);
        return Result.success(result);
    }

    //在线用户列表（仅管理员）
    public Result<List<OnlineUserResponseDTO>> listOnlineUsers(){
        List<OnlineUserService.OnlineSession> sessions = onlineUserService.listActive();

        //批量查询用户资料，补充昵称、头像等展示字段
        Set<Long> userIds = sessions.stream()
                .map(OnlineUserService.OnlineSession::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, User> userMap = userIds.isEmpty()
                ? Collections.emptyMap()
                : userMapper.selectBatchIds(userIds).stream()
                        .collect(Collectors.toMap(User::getId, Function.identity()));

        //会话转响应 DTO
        List<OnlineUserResponseDTO> records = sessions.stream()
                .map(session -> {
                    OnlineUserResponseDTO dto = new OnlineUserResponseDTO();
                    dto.setUserId(session.getUserId());
                    dto.setUsername(session.getUsername());
                    dto.setLoginTime(session.getLoginTime().format(DATE_TIME_FORMATTER));
                    dto.setLastActiveTime(session.getLastActiveTime().format(DATE_TIME_FORMATTER));

                    User user = userMap.get(session.getUserId());
                    if (user != null) {
                        dto.setNickname(user.getNickname());
                        dto.setAvatar(user.getAvatar());
                        dto.setUserType(user.getUserType());
                        dto.setUserTypeDisplayName(resolve(user.getUserTypeDisplayName(), mapUserType(user.getUserType())));
                    } else {
                        dto.setUserType(session.getUserType());
                        dto.setUserTypeDisplayName(mapUserType(session.getUserType()));
                    }
                    return dto;
                })
                .collect(Collectors.toList());

        return Result.success(records);
    }

    //退出登录：撤销整段会话（Access + Refresh），并移除在线会话
    public void logout(String token, String refreshToken){
        if (token != null && !token.isBlank()) {
            try {
                DecodedJWT decodedJWT = jwtTokenUtil.verifyToken(token);
                tokenService.revokeSessionByAccessJti(decodedJWT.getId());
                onlineUserService.remove(
                        jwtTokenUtil.getUserId(decodedJWT),
                        decodedJWT.getClaim("username").asString(),
                        token);
            } catch (Exception ignored) {
                //token 无效或已过期，无需撤销
            }
        }
        //Access Token 已失效时，仍可按 Refresh Token 找到 sid 并撤销会话
        if (refreshToken != null && !refreshToken.isBlank()) {
            try {
                DecodedJWT decodedJWT = jwtTokenUtil.verifyToken(refreshToken);
                if (jwtTokenUtil.isRefreshToken(decodedJWT)) {
                    tokenService.revokeSession(jwtTokenUtil.getSid(decodedJWT));
                }
            } catch (Exception ignored) {
                //刷新凭证无效或已过期，同样无需处理
            }
        }
    }

    /**
     * 刷新会话：校验 Refresh Token，轮换 Refresh，签发新的 Access。
     * 检测到已轮换的 Refresh 被重放时，撤销该用户全部会话。
     */
    public Result<UserLoginResponseDTO> refresh(String refreshToken){
        DecodedJWT decodedJWT;
        try {
            decodedJWT = jwtTokenUtil.verifyToken(refreshToken);
        } catch (Exception e) {
            throw new BusinessException(ResultCode.UNAUTHORIZED.getCode(), "登录已过期，请重新登录");
        }
        if (!jwtTokenUtil.isRefreshToken(decodedJWT)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED.getCode(), "刷新凭证无效");
        }

        String refreshJti = decodedJWT.getId();
        if (tokenService.isRefreshReused(refreshJti)) {
            // 重放：该 Refresh 已经被使用过，立即撤销整段会话
            Long reusedUserId = tokenService.getUserIdByUsedRefresh(refreshJti);
            tokenService.revokeAllForUser(reusedUserId);
            throw new BusinessException(ResultCode.UNAUTHORIZED.getCode(), "刷新凭证已失效，请重新登录");
        }
        if (!tokenService.isRefreshValid(refreshJti)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED.getCode(), "登录已过期，请重新登录");
        }

        String sid = jwtTokenUtil.getSid(decodedJWT);
        TokenService.SessionRecord session = tokenService.getSession(sid);
        if (session == null || session.userId() == null) {
            tokenService.revokeSession(sid);
            throw new BusinessException(ResultCode.UNAUTHORIZED.getCode(), "登录已过期，请重新登录");
        }

        User user = userMapper.selectById(session.userId());
        if (user == null || !user.isActive()) {
            tokenService.revokeAllForUser(session.userId());
            throw new BusinessException(ResultCode.UNAUTHORIZED.getCode(), "登录状态已失效");
        }

        ClientType clientType = ClientType.from(session.clientType());
        long accessTtl = jwtConfig.accessExpiration(clientType);
        long refreshTtl = jwtConfig.refreshExpiration(clientType);
        String newAccessJti = IdUtil.fastSimpleUUID();
        String newRefreshJti = IdUtil.fastSimpleUUID();
        String newToken = jwtTokenUtil.generateAccessToken(
                user.getId().toString(), user.getUsername(), user.getUserType(),
                sid, clientType, newAccessJti);
        String newRefreshToken = jwtTokenUtil.generateRefreshToken(
                user.getId().toString(), user.getUsername(), user.getUserType(),
                sid, clientType, newRefreshJti);
        tokenService.rotate(sid, refreshJti, newAccessJti, newRefreshJti,
                user.getId(), clientType.name(), accessTtl, refreshTtl);

        UserLoginResponseDTO dto = new UserLoginResponseDTO();
        dto.setToken(newToken);
        dto.setRefreshToken(newRefreshToken);
        dto.setExpiresIn(accessTtl / 1000L);
        dto.setClientType(clientType.name());
        dto.setRoleType(resolve(user.getUserTypeDisplayName(), mapUserType(user.getUserType())));
        dto.setUserInfo(toUserDetail(user));
        return Result.success(dto);
    }

    //把用户实体转换成响应数据（login / register / message 共用）
    private static UserLoginResponseDTO.UserDetailResponseDTO toUserDetail(User user) {
        UserLoginResponseDTO.UserDetailResponseDTO userInfo = new UserLoginResponseDTO.UserDetailResponseDTO();
        userInfo.setId(user.getId());
        userInfo.setUsername(user.getUsername());
        userInfo.setEmail(user.getEmail());
        userInfo.setNickname(user.getNickname());
        userInfo.setAvatar(user.getAvatar());
        userInfo.setPhone(user.getPhone());
        userInfo.setGender(user.getGender());
        userInfo.setGenderDisplayName(resolve(user.getGenderDisplayName(), mapGender(user.getGender())));
        userInfo.setBirthday(user.getBirthday() != null ? user.getBirthday().toString() : null);
        userInfo.setUserType(user.getUserType());
        userInfo.setUserTypeDisplayName(resolve(user.getUserTypeDisplayName(), mapUserType(user.getUserType())));
        userInfo.setStatus(user.getStatus());
        userInfo.setStatusDisplayName(resolve(user.getStatusDisplayName(), mapStatus(user.getStatus())));
        userInfo.setDisplayName(user.getNickname());
        userInfo.setCreatedAt(user.getCreatedAt() != null ? user.getCreatedAt().format(DATE_TIME_FORMATTER) : null);
        userInfo.setUpdatedAt(user.getUpdatedAt() != null ? user.getUpdatedAt().format(DATE_TIME_FORMATTER) : null);
        return userInfo;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    //表里的 display_name 列优先，为空时按编码映射兜底
    private static String resolve(String displayName, String fallback) {
        return (displayName != null && !displayName.isEmpty()) ? displayName : fallback;
    }

    //性别：0-未知，1-男，2-女
    private static String mapGender(Integer gender) {
        if (gender == null) return null;
        switch (gender) {
            case 1: return "男";
            case 2: return "女";
            default: return "未知";
        }
    }

    //用户类型：1-普通用户，2-管理员，3-VIP
    private static String mapUserType(Integer userType) {
        if (userType == null) return null;
        switch (userType) {
            case 2: return "管理员";
            case 3: return "VIP";
            default: return "普通用户";
        }
    }

    //状态：0-禁用，1-正常，2-锁定
    private static String mapStatus(Integer status) {
        if (status == null) return null;
        switch (status) {
            case 0: return "禁用";
            case 2: return "锁定";
            default: return "正常";
        }
    }


    /**
     * 用户自助编辑资料。只更新白名单字段，userType/status/username/email/password 不在此接口范围内。
     */
    public Result<UserLoginResponseDTO.UserDetailResponseDTO> updateProfile(
            Long userId, UpdateProfileCommandDTO commandDTO) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        if (commandDTO.getNickname() != null) {
            user.setNickname(trimToNull(commandDTO.getNickname()));
        }
        if (commandDTO.getAvatar() != null) {
            String avatar = trimToNull(commandDTO.getAvatar());
            if (avatar != null && !avatar.startsWith("http://")
                    && !avatar.startsWith("https://") && !avatar.startsWith("/")) {
                throw new BusinessException("PARAM_ERROR", "头像地址格式不正确");
            }
            user.setAvatar(avatar);
        }
        if (commandDTO.getPhone() != null) {
            user.setPhone(trimToNull(commandDTO.getPhone()));
        }
        if (commandDTO.getGender() != null) {
            user.setGender(commandDTO.getGender());
            user.setGenderDisplayName(mapGender(commandDTO.getGender()));
        }
        if (commandDTO.getBirthday() != null) {
            String birthday = trimToNull(commandDTO.getBirthday());
            if (birthday == null) {
                user.setBirthday(null);
            } else {
                try {
                    user.setBirthday(LocalDate.parse(birthday));
                } catch (Exception e) {
                    throw new BusinessException("PARAM_ERROR", "生日格式应为 yyyy-MM-dd");
                }
            }
        }

        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
        userAuthStateService.evict(userId);
        return Result.success(toUserDetail(user));
    }

    /**
     * 管理员修改用户状态。0-禁用，1-正常。
     * 不允许禁用自己，也不允许禁用最后一个可用管理员。
     */
    public Result<UserLoginResponseDTO.UserDetailResponseDTO> updateStatus(
            Long adminId, Long targetId, UpdateUserStatusCommandDTO commandDTO) {
        User target = userMapper.selectById(targetId);
        if (target == null) {
            throw new BusinessException("NOT_FOUND", "用户不存在");
        }
        int status = commandDTO.getStatus();
        if (status == 0 && targetId.equals(adminId)) {
            throw new BusinessException("FORBIDDEN", "不能禁用当前登录账号");
        }
        if (status == 0 && Integer.valueOf(2).equals(target.getUserType())) {
            Long otherActiveAdmins = userMapper.selectCount(new LambdaQueryWrapper<User>()
                    .eq(User::getUserType, UserType.ADMIN.getCode())
                    .eq(User::getStatus, UserStatus.ENABLED.getCode())
                    .ne(User::getId, targetId));
            if (otherActiveAdmins == null || otherActiveAdmins == 0) {
                throw new BusinessException("FORBIDDEN", "至少需要保留一个可用的管理员账号");
            }
        }

        target.setStatus(status);
        target.setStatusDisplayName(status == 1 ? "正常" : "禁用");
        target.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(target);

        userAuthStateService.evict(targetId);
        if (status == 0) {
            tokenService.revokeAllForUser(targetId);
            onlineUserService.removeUser(targetId);
        }
        return Result.success(toUserDetail(target));
    }

    public Result<UserLoginResponseDTO.UserDetailResponseDTO> register(UserRegisterCommandDTO commandDTO){
        //验证密码是否一致
        if(!commandDTO.getPassword().equals(commandDTO.getConfirmPassword())){
            throw new BusinessException("两次输入密码不一致");
        }

        //验证用户名是否存在
        LambdaQueryWrapper<User> queryNameWrapper = new LambdaQueryWrapper<>();
        queryNameWrapper.eq(User::getUsername, commandDTO.getUsername());
        if(userMapper.selectCount(queryNameWrapper) > 0){
            throw new BusinessException("用户名已存在");
        }

        //验证邮箱是否存在
        LambdaQueryWrapper<User> emailQuery = new LambdaQueryWrapper<>();
        emailQuery.eq(User::getEmail, commandDTO.getEmail());
        if(userMapper.selectCount(emailQuery) > 0){
            throw new BusinessException("邮箱已存在");
        }

        //创建用户实体
        User user = new User();
        user.setUsername(commandDTO.getUsername());
        user.setEmail(commandDTO.getEmail());
        user.setNickname(commandDTO.getNickname());
        user.setPhone(commandDTO.getPhone());
        //后端第二次加密：随机盐 + 前端 SM3 密文再做 SM3，盐随用户单独保存
        String salt = Sm3Util.generateSalt();
        user.setSalt(salt);
        user.setPassword(Sm3Util.backendEncrypt(commandDTO.getPassword().trim(), salt));
        user.setGender(commandDTO.getGender());
        //公开注册一律为普通用户，避免客户端传 userType 自封管理员
        user.setUserType(UserType.NORMAL.getCode());
        user.setStatus(UserStatus.ENABLED.getCode());
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());

        //入库（MyBatis-Plus 会把自增主键回填到 user.getId()）
        userMapper.insert(user);

        //组装返回数据
        return Result.success(toUserDetail(user));
    }
}
