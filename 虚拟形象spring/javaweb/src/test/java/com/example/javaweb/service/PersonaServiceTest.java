package com.example.javaweb.service;

import com.example.javaweb.entity.PromptTemplate;
import com.example.javaweb.entity.StagePolicy;
import com.example.javaweb.mapper.PromptTemplateMapper;
import com.example.javaweb.mapper.StagePolicyMapper;
import com.example.javaweb.mapper.UserProfileMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PersonaServiceTest {

    @Mock
    private PromptTemplateMapper promptTemplateMapper;

    @Mock
    private StagePolicyMapper stagePolicyMapper;

    @Mock
    private UserProfileMapper userProfileMapper;

    @InjectMocks
    private PersonaService service;

    @Test
    void stylePromptOmitsVerbosityBulletAndReturnsTraits() {
        when(userProfileMapper.selectPreferences(1L))
                .thenReturn("{\"personaCode\":\"persona_default\","
                        + "\"traits\":{\"warmth\":0.5,\"humor\":0.3,\"directness\":0.5,"
                        + "\"initiative\":0.5,\"verbosity\":0.9}}");

        PromptTemplate template = new PromptTemplate();
        template.setCode("persona_default");
        template.setContent("语气自然、温和。");
        when(promptTemplateMapper.selectActive("persona_default")).thenReturn(template);

        StagePolicy policy = new StagePolicy();
        policy.setCode("S0");
        policy.setConfig("{\"tone\":\"礼貌、克制\",\"address\":\"你\"}");
        when(stagePolicyMapper.selectActive("S0")).thenReturn(policy);

        PersonaService.StylePrompt style = service.buildStylePrompt(1L, null, null);

        assertThat(style.text()).contains("语气自然、温和。");
        assertThat(style.text()).contains("阶段边界");
        // 长度指令统一交给回复风格层，避免两条互相矛盾的“话量”规则
        assertThat(style.text()).doesNotContain("话量");
        assertThat(style.text()).doesNotContain("人格只决定");
        assertThat(style.traits().verbosity()).isEqualTo(0.9);
    }
}
