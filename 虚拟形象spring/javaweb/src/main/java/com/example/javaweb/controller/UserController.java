package com.example.javaweb.controller;

import com.example.javaweb.dto.request.ForgotPasswordCommandDTO;
import com.example.javaweb.dto.request.RefreshTokenCommandDTO;
import com.example.javaweb.dto.request.ResetPasswordCommandDTO;
import com.example.javaweb.dto.request.UpdateProfileCommandDTO;
import com.example.javaweb.dto.request.UpdateUserStatusCommandDTO;
import com.example.javaweb.dto.request.UserRegisterCommandDTO;
import com.example.javaweb.dto.response.OnlineUserResponseDTO;
import com.example.javaweb.dto.response.UserListResponseDTO;
import com.example.javaweb.dto.response.UserLoginResponseDTO;
import com.example.javaweb.security.CurrentUserResolver;
import com.example.javaweb.service.PasswordResetService;
import com.example.javaweb.service.UserService;
import com.example.javaweb.config.JWTConfig;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import com.example.javaweb.dto.request.UserLoginCommandDTO;
import com.example.javaweb.common.Result;
import com.example.javaweb.common.ResultCode;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("api/user")
public class UserController {
    @Resource
    private UserService userService;

    @Resource
    private JWTConfig jwtConfig;

    @Resource
    private CurrentUserResolver currentUserResolver;

    @Resource
    private PasswordResetService passwordResetService;

    //登录
    @PostMapping("/login")
    public Result<UserLoginResponseDTO> login(@Valid @RequestBody UserLoginCommandDTO commandDTO){
        //调用服务层方法
        return userService.login(commandDTO);
    }

    //刷新会话：用 Refresh Token 换取新的 Access + Refresh（公开端点）
    @PostMapping("/refresh")
    public Result<UserLoginResponseDTO> refresh(@Valid @RequestBody RefreshTokenCommandDTO commandDTO){
        return userService.refresh(commandDTO.getRefreshToken());
    }

    //注册
    @PostMapping("/register")
    public Result<UserLoginResponseDTO.UserDetailResponseDTO>
    register(@Valid @RequestBody UserRegisterCommandDTO commandDTO){
        return userService.register(commandDTO);
    }

    //获取用户信息：优先按 query 中的 username 查询，未传则取当前登录用户
    @GetMapping("/message")
    public Result<UserLoginResponseDTO.UserDetailResponseDTO> message(
            @RequestParam(required = false) String username,
            Authentication authentication){
        //query 里传了 username 就按这个用户名查
        if (username != null && !username.isBlank()) {
            return userService.getMessage(username.trim());
        }
        //未登录（匿名用户）时返回统一错误，避免把 anonymousUser 当用户名查库
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
            return Result.error(ResultCode.UNAUTHORIZED.getCode(), ResultCode.UNAUTHORIZED.getMsg());
        }
        //未传 username 时，返回当前登录用户信息
        return userService.getMessage(authentication.getName());
    }

    //用户列表（分页，仅管理员可访问）
    @GetMapping("/list")
    public Result<UserListResponseDTO> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String keyword){
        return userService.listUsers(page, size, keyword);
    }

    //在线用户列表（仅管理员可访问）
    @GetMapping("/online")
    public Result<List<OnlineUserResponseDTO>> online(){
        return userService.listOnlineUsers();
    }

    //当前用户编辑自己的资料：昵称、头像、手机、性别、生日
    @PutMapping("/profile")
    public Result<UserLoginResponseDTO.UserDetailResponseDTO> updateProfile(
            @Valid @RequestBody UpdateProfileCommandDTO commandDTO,
            Authentication authentication) {
        Long userId = currentUserResolver.requireUserId(authentication);
        return userService.updateProfile(userId, commandDTO);
    }

    //上传用户头像或 AI 头像：multipart/form-data，字段 file + target(user|ai)
    @PostMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<UserLoginResponseDTO.UserDetailResponseDTO> uploadAvatar(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "target", defaultValue = "user") String target,
            Authentication authentication) {
        Long userId = currentUserResolver.requireUserId(authentication);
        return userService.uploadAvatar(userId, target, file);
    }

    //管理员修改用户状态：0-禁用，1-正常
    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{id}/status")
    public Result<UserLoginResponseDTO.UserDetailResponseDTO> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserStatusCommandDTO commandDTO,
            Authentication authentication) {
        Long adminId = currentUserResolver.requireUserId(authentication);
        return userService.updateStatus(adminId, id, commandDTO);
    }

    //找回密码：发送邮箱验证码（公开接口，邮箱不存在也返回成功）
    @PostMapping("/password/forgot")
    public Result<Void> forgotPassword(@Valid @RequestBody ForgotPasswordCommandDTO commandDTO) {
        passwordResetService.requestReset(commandDTO.getEmail());
        return Result.success();
    }

    //找回密码：校验验证码并重置密码（公开接口）
    @PostMapping("/password/reset")
    public Result<Void> resetPassword(@Valid @RequestBody ResetPasswordCommandDTO commandDTO) {
        passwordResetService.reset(commandDTO.getEmail(), commandDTO.getCode(),
                commandDTO.getNewPassword(), commandDTO.getConfirmPassword());
        return Result.success();
    }

    //退出登录：撤销整段会话并让当前 token 立即从在线列表移除
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request,
                              @RequestBody(required = false) RefreshTokenCommandDTO commandDTO){
        String refreshToken = commandDTO == null ? null : commandDTO.getRefreshToken();
        String header = request.getHeader(jwtConfig.getHeader());
        if (header != null && header.startsWith(jwtConfig.getTokenPrefix())) {
            userService.logout(header.substring(jwtConfig.getTokenPrefix().length()).trim(), refreshToken);
        } else {
            userService.logout(null, refreshToken);
        }
        return Result.success();
    }

}
