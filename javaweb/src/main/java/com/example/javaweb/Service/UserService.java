package com.example.javaweb.Service;


import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.javaweb.DTO.command.UserLoginCommandDTO;
import com.example.javaweb.DTO.command.UserRegisterCommandDTO;
import com.example.javaweb.DTO.response.UserLoginResponseDTO;
import com.example.javaweb.entity.user;
import com.example.javaweb.enumClass.UserStatus;
import com.example.javaweb.enumClass.UserType;
import com.example.javaweb.exception.BusinessException;
import com.example.javaweb.mapper.UserMapper;
import com.example.javaweb.util.JWTTokenUtil;
import jakarta.annotation.Resource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import com.example.javaweb.common.Result;
import com.example.javaweb.common.ResultCode;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;



@Service
public class UserService {
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Resource
    private UserMapper userMapper;

    @Resource
    private JWTTokenUtil jwtTokenUtil;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public Result<UserLoginResponseDTO> login(UserLoginCommandDTO commandDTO){
        //构建查询条件
        LambdaQueryWrapper<user> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(user::getUsername,commandDTO.getUsername())
                .or()
                .eq(user::getEmail,commandDTO.getUsername());
        //调用api查询
        user user = userMapper.selectOne(queryWrapper);

        //用户不存在
        if (user == null) {
            throw new BusinessException("用户名或密码错误");
        }
        //密码校验（BCrypt哈希比对）
        if(!passwordEncoder.matches(commandDTO.getPassword().trim(), user.getPassword())){
            throw new BusinessException("密码错误");
        }

        //检查用户状态
        if(!user.isActive()){
            throw new BusinessException("用户已被禁用，请联系管理员启用");
        }

        //登录成功，组装返回数据
        UserLoginResponseDTO dto = new UserLoginResponseDTO();
        dto.setToken(jwtTokenUtil.generateToken(user.getId().toString(), user.getUsername(), user.getUserType()));
        dto.setRoleType(resolve(user.getUserTypeDisplayName(), mapUserType(user.getUserType())));

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
        dto.setUserInfo(userInfo);

        return Result.success(dto);
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


    public Result<UserLoginResponseDTO.UserDetailResponseDTO> register(UserRegisterCommandDTO commandDTO){
        //校验参数
        System.out.println(JSONUtil.parseObj(commandDTO));
        //验证密码是否一致
        if(!commandDTO.getPassword().equals(commandDTO.getConfirmPassword())){
            throw new BusinessException("两次输入密码不一致");
        }

        //验证用户名是否存在
        LambdaQueryWrapper<user> queryNameWrapper = new LambdaQueryWrapper<>();
        queryNameWrapper.eq(user::getUsername, commandDTO.getUsername());
        if(userMapper.selectCount(queryNameWrapper) > 0){
            throw new BusinessException("用户名已存在");
        }

        //验证邮箱是否存在
        LambdaQueryWrapper<user> emailQuery = new LambdaQueryWrapper<>();
        emailQuery.eq(user::getEmail, commandDTO.getEmail());
        if(userMapper.selectCount(emailQuery) > 0){
            throw new BusinessException("邮箱已存在");
        }

        //用户类型：1-普通用户，2-管理员，3-VIP
        if(!UserType.isValid(commandDTO.getUserType())){
            throw new BusinessException("用户类型无效");
        }

        //创建用户实体
        user user = new user();
        user.setUsername(commandDTO.getUsername());
        user.setEmail(commandDTO.getEmail());
        user.setNickname(commandDTO.getNickname());
        user.setPhone(commandDTO.getPhone());
        user.setPassword(passwordEncoder.encode(commandDTO.getPassword().trim()));
        user.setGender(commandDTO.getGender());
        user.setUserType(commandDTO.getUserType());
        user.setStatus(UserStatus.ENABLED.getCode());
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());

        //入库（MyBatis-Plus 会把自增主键回填到 user.getId()）
        userMapper.insert(user);

        //组装返回数据
        UserLoginResponseDTO.UserDetailResponseDTO userInfo = new UserLoginResponseDTO.UserDetailResponseDTO();
        userInfo.setId(user.getId());
        userInfo.setUsername(user.getUsername());
        userInfo.setEmail(user.getEmail());
        userInfo.setNickname(user.getNickname());
        userInfo.setPhone(user.getPhone());
        userInfo.setGender(user.getGender());
        userInfo.setGenderDisplayName(mapGender(user.getGender()));
        userInfo.setUserType(user.getUserType());
        userInfo.setUserTypeDisplayName(mapUserType(user.getUserType()));
        userInfo.setStatus(user.getStatus());
        userInfo.setStatusDisplayName(mapStatus(user.getStatus()));
        userInfo.setDisplayName(user.getNickname());
        userInfo.setCreatedAt(user.getCreatedAt().format(DATE_TIME_FORMATTER));
        userInfo.setUpdatedAt(user.getUpdatedAt().format(DATE_TIME_FORMATTER));

        return Result.success(userInfo);
    }
}
