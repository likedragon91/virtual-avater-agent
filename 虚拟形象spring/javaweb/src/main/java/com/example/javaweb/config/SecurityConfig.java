package com.example.javaweb.config;

import cn.hutool.json.JSONUtil;
import com.example.javaweb.common.Result;
import com.example.javaweb.common.ResultCode;
import com.example.javaweb.security.JwtAuthenticationFilter;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;


@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {
    private static final String[] PUBLIC_PATHS = {
            "/",
            "/api/user/login",
            "/api/user/register",
            "/api/user/captcha",
            "/api/user/refresh",
            "/api/user/logout",
            "/api/user/password/forgot",
            "/api/user/password/reset",
            // 头像图片通过 img 标签直接加载，文件名是 UUID，内容经过类型/魔数校验
            "/api/files/**"
    };

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtAuthenticationFilter jwtAuthenticationFilter) throws Exception{
        http
                // 开启跨域，使用下方 corsConfigurationSource 的配置
                .cors(Customizer.withDefaults())
                // 禁用CSRF保护（api通常不需要）
                .csrf(AbstractHttpConfigurer::disable)
                //配置会话管理无状态
                .sessionManagement(session->session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                //配置请求的授权规则
                .authorizeHttpRequests(auth -> auth
                        //SSE 完成时的异步派发不再重复授权（初始请求已经过认证）
                        .dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()
                        //公开路径，无需登录即可访问
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        //用户列表、在线用户仅管理员可访问
                        .requestMatchers("/api/user/list", "/api/user/online").hasRole("ADMIN")
                        //其他路径需要登录
                        .anyRequest().authenticated()
                )
                //认证/授权失败时返回统一的 JSON，而不是空响应体
                .exceptionHandling(ex -> ex
                        //未登录访问受保护接口
                        .authenticationEntryPoint((request, response, authException) ->
                                writeJson(response, HttpServletResponse.SC_UNAUTHORIZED,
                                        ResultCode.UNAUTHORIZED.getCode(), ResultCode.UNAUTHORIZED.getMsg()))
                        //已登录但没有访问权限
                        .accessDeniedHandler((request, response, accessDeniedException) ->
                                writeJson(response, HttpServletResponse.SC_FORBIDDEN,
                                        ResultCode.FORBIDDEN.getCode(), ResultCode.FORBIDDEN.getMsg()))
                )
                //在用户名密码认证过滤器之前加入JWT过滤器
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
                return http.build();
    }

    //把错误信息以 JSON 写入响应
    private static void writeJson(HttpServletResponse response, int httpStatus, String code, String msg) throws IOException {
        response.setStatus(httpStatus);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(JSONUtil.toJsonStr(Result.error(code, msg)));
    }

    // 跨域配置：允许前端开发服务器（Vite 默认 5173 端口）访问后端接口
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        // 开发阶段放开所有来源；上线可改为前端具体地址，如 "http://localhost:5173"
        config.setAllowedOriginPatterns(Arrays.asList("*"));
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(Arrays.asList("*"));
        // 登录用 JWT（放在请求头），不依赖 Cookie，可设为 false
        config.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

}
