package com.example.javaweb.config;

import com.example.javaweb.filter.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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

import java.util.Arrays;


@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {
    private static final String[] PUBLIC_PATHS = {
            "/",
            "/api/user/login",
            "/api/user/register"
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
                        //公开路径，无需登录即可访问
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        //其他路径需要登录
                        .anyRequest().authenticated()
                )
                //在用户名密码认证过滤器之前加入JWT过滤器
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
                return http.build();
    }

    // 跨域配置：允许前端开发服务器（Vite 默认 5173 端口）访问后端接口
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        // 开发阶段放开所有来源；上线可改为前端具体地址，如 "http://localhost:5173"
        config.setAllowedOriginPatterns(Arrays.asList("*"));
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(Arrays.asList("*"));
        // 登录用 JWT（放在请求头），不依赖 Cookie，可设为 false
        config.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

}
