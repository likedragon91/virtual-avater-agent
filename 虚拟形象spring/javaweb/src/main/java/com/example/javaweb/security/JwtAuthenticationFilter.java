package com.example.javaweb.security;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.example.javaweb.service.OnlineUserService;
import com.example.javaweb.service.TokenService;
import com.example.javaweb.config.JWTConfig;
import com.example.javaweb.security.LoginUser;
import com.example.javaweb.util.JWTTokenUtil;
import jakarta.annotation.Resource;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * JWT认证过滤器：从请求头解析token，校验通过后将用户信息放入SecurityContext
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Resource
    private JWTTokenUtil jwtTokenUtil;

    @Resource
    private JWTConfig jwtConfig;

    @Resource
    private OnlineUserService onlineUserService;

    @Resource
    private TokenService tokenService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        //从请求头获取token
        String header = request.getHeader(jwtConfig.getHeader());
        //存在token且以配置的前缀开头
        if (header != null && header.startsWith(jwtConfig.getTokenPrefix())) {
            String token = header.substring(jwtConfig.getTokenPrefix().length()).trim();
            try {
                //校验并解析token
                DecodedJWT decodedJWT = jwtTokenUtil.verifyToken(token);
                //token 必须在 Redis 白名单中（退出登录后会被撤销）
                if (!tokenService.isValid(decodedJWT.getId())) {
                    throw new IllegalStateException("token 已失效");
                }
                String username = decodedJWT.getClaim("username").asString();
                Integer roleType = decodedJWT.getClaim("roleType").asInt();
                Long userId = jwtTokenUtil.getUserId(decodedJWT);
                //每次有效请求都刷新在线状态（服务重启后也能按 token 补登记）
                onlineUserService.touch(token, userId, username, roleType);
                //根据角色构建权限
                List<SimpleGrantedAuthority> authorities = resolveAuthorities(roleType);
                //主体使用 LoginUser，Controller 可直接取 userId；getName() 仍返回用户名，兼容旧代码
                LoginUser principal = new LoginUser(userId, username, roleType);
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(principal, null, authorities);
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                //放入安全上下文
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (Exception e) {
                //token无效或过期，清空上下文，交给后续授权规则处理
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }

    //根据用户类型映射权限：2-管理员，3-VIP，其他-普通用户
    private List<SimpleGrantedAuthority> resolveAuthorities(Integer roleType) {
        String authority;
        if (roleType != null && roleType == 2) {
            authority = "ROLE_ADMIN";
        } else if (roleType != null && roleType == 3) {
            authority = "ROLE_VIP";
        } else {
            authority = "ROLE_USER";
        }
        return Collections.singletonList(new SimpleGrantedAuthority(authority));
    }
}
