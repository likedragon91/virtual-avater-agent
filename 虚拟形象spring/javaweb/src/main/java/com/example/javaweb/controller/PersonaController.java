package com.example.javaweb.controller;

import com.example.javaweb.dto.request.UpdatePersonaCommandDTO;
import com.example.javaweb.dto.response.PersonaCurrentResponseDTO;
import com.example.javaweb.dto.response.PersonaOptionDTO;
import com.example.javaweb.service.PersonaService;
import com.example.javaweb.common.Result;
import com.example.javaweb.security.CurrentUserResolver;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 人格预设、用户默认人格与风格特质。
 */
@RestController
@RequestMapping("/api/persona")
public class PersonaController {

    @Resource
    private PersonaService personaService;

    @Resource
    private CurrentUserResolver currentUserResolver;

    @GetMapping("/options")
    public Result<List<PersonaOptionDTO>> options() {
        return Result.success(personaService.getOptions());
    }

    @GetMapping
    public Result<PersonaCurrentResponseDTO> current(Authentication authentication) {
        Long userId = currentUserResolver.requireUserId(authentication);
        return Result.success(personaService.getCurrent(userId));
    }

    @PutMapping
    public Result<PersonaCurrentResponseDTO> save(
            @Valid @RequestBody UpdatePersonaCommandDTO command,
            Authentication authentication) {
        Long userId = currentUserResolver.requireUserId(authentication);
        return Result.success(personaService.save(userId, command));
    }
}
