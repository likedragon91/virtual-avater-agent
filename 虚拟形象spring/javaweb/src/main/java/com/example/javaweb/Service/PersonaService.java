package com.example.javaweb.service;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.example.javaweb.dto.projection.ProfileRow;
import com.example.javaweb.dto.request.UpdatePersonaCommandDTO;
import com.example.javaweb.dto.response.PersonaCurrentResponseDTO;
import com.example.javaweb.dto.response.PersonaOptionDTO;
import com.example.javaweb.dto.response.PersonaTraitsDTO;
import com.example.javaweb.entity.ChatSession;
import com.example.javaweb.entity.PromptTemplate;
import com.example.javaweb.entity.StagePolicy;
import com.example.javaweb.exception.BusinessException;
import com.example.javaweb.mapper.PromptTemplateMapper;
import com.example.javaweb.mapper.StagePolicyMapper;
import com.example.javaweb.mapper.UserProfileMapper;
import com.example.javaweb.persona.PersonaCatalog;
import com.example.javaweb.persona.PersonaCatalog.Preset;
import com.example.javaweb.persona.PersonaCatalog.Traits;
import jakarta.annotation.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

/**
 * 人格配置与提示词片段。
 * 优先级：阶段边界 > 人格风格；用户特质只调风格强度。
 */
@Service
public class PersonaService {

    @Resource
    private PromptTemplateMapper promptTemplateMapper;

    @Resource
    private StagePolicyMapper stagePolicyMapper;

    @Resource
    private UserProfileMapper userProfileMapper;

    public List<PersonaOptionDTO> getOptions() {
        return PersonaCatalog.all().stream().map(preset -> {
            PersonaOptionDTO dto = new PersonaOptionDTO();
            dto.setCode(preset.code());
            dto.setName(preset.name());
            dto.setDescription(preset.description());
            dto.setSample(preset.sample());
            dto.setDefaultTraits(toTraitsDTO(preset.defaultTraits()));
            return dto;
        }).toList();
    }

    public PersonaCurrentResponseDTO getCurrent(Long userId) {
        JSONObject preferences = loadPreferences(userId);
        String code = PersonaCatalog.normalize(preferences.getStr("personaCode"));
        Preset preset = PersonaCatalog.find(code);
        Traits traits = readTraits(preferences, preset.defaultTraits());
        return toCurrent(preset, traits);
    }

    public PersonaCurrentResponseDTO save(Long userId, UpdatePersonaCommandDTO command) {
        String code = command.getPersonaCode() == null ? null : command.getPersonaCode().trim();
        if (!PersonaCatalog.isValid(code)) {
            throw new BusinessException("PARAM_ERROR", "不支持的人格");
        }
        Preset preset = PersonaCatalog.find(code);
        Traits traits = command.getTraits() == null
                ? preset.defaultTraits()
                : toTraits(command.getTraits(), preset.defaultTraits());

        JSONObject preferences = loadPreferences(userId);
        preferences.set("personaCode", code);
        JSONObject traitObject = new JSONObject();
        traitObject.set("warmth", traits.warmth());
        traitObject.set("humor", traits.humor());
        traitObject.set("directness", traits.directness());
        traitObject.set("initiative", traits.initiative());
        traitObject.set("verbosity", traits.verbosity());
        preferences.set("traits", traitObject);
        userProfileMapper.updatePreferences(userId, preferences.toString());
        return toCurrent(preset, traits);
    }

    /**
     * 会话显式指定的人格 > 用户默认人格 > persona_default。
     */
    public String getEffectiveCode(Long userId, ChatSession session) {
        return effectiveCode(session, loadPreferences(userId));
    }

    /**
     * 生成系统提示中的人格/阶段片段。
     */
    public String buildStylePrompt(Long userId, ChatSession session, ProfileRow profile) {
        JSONObject preferences = loadPreferences(userId);
        String code = effectiveCode(session, preferences);
        Preset preset = PersonaCatalog.find(code);
        if (preset == null) {
            preset = PersonaCatalog.find(PersonaCatalog.DEFAULT_CODE);
            code = preset.code();
        }
        Traits traits = readTraits(preferences, preset.defaultTraits());

        PromptTemplate template = promptTemplateMapper.selectActive(code);
        String style = template != null && template.getContent() != null && !template.getContent().isBlank()
                ? template.getContent().trim()
                : preset.description() + "。示例语气：" + preset.sample();

        StagePolicy policy = stagePolicyMapper.selectActive(
                profile != null && profile.getStageCode() != null ? profile.getStageCode() : "S0");

        StringBuilder prompt = new StringBuilder();
        prompt.append("人格：").append(preset.name()).append("\n");
        prompt.append("风格要求：").append(style).append("\n");
        prompt.append("示例语气：").append(preset.sample()).append("\n");
        prompt.append("风格强度：\n").append(traitSection(traits));
        prompt.append("\n").append(stageSection(policy, profile));
        prompt.append("\n人格只决定“怎么说”，阶段边界决定“能说多深”；两者冲突时以阶段边界为准。\n");
        return prompt.toString();
    }

    private String effectiveCode(ChatSession session, JSONObject preferences) {
        String sessionCode = session == null ? null : session.getPersonaCode();
        if (sessionCode != null && !sessionCode.isBlank()
                && !PersonaCatalog.SESSION_DEFAULT_CODE.equals(sessionCode)
                && PersonaCatalog.isValid(sessionCode)) {
            return sessionCode;
        }
        String userCode = preferences.getStr("personaCode");
        return PersonaCatalog.isValid(userCode) ? userCode : PersonaCatalog.DEFAULT_CODE;
    }

    private JSONObject loadPreferences(Long userId) {
        String json = userProfileMapper.selectPreferences(userId);
        if (json == null) {
            try {
                userProfileMapper.insert(userId, "{}");
            } catch (DuplicateKeyException ignored) {
                // 并发创建时忽略
            }
            json = "{}";
        }
        try {
            JSONObject object = JSONUtil.parseObj(json);
            return object == null ? new JSONObject() : object;
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    private Traits readTraits(JSONObject preferences, Traits defaults) {
        JSONObject traits = preferences.getJSONObject("traits");
        if (traits == null) {
            return defaults;
        }
        return PersonaCatalog.clamp(new Traits(
                number(traits, "warmth", defaults.warmth()),
                number(traits, "humor", defaults.humor()),
                number(traits, "directness", defaults.directness()),
                number(traits, "initiative", defaults.initiative()),
                number(traits, "verbosity", defaults.verbosity())));
    }

    private static double number(JSONObject object, String key, double defaultValue) {
        Double value = object.getDouble(key);
        return value == null ? defaultValue : value;
    }

    private static Traits toTraits(PersonaTraitsDTO dto, Traits defaults) {
        if (dto == null) {
            return defaults;
        }
        return PersonaCatalog.clamp(new Traits(
                dto.getWarmth() == null ? defaults.warmth() : dto.getWarmth(),
                dto.getHumor() == null ? defaults.humor() : dto.getHumor(),
                dto.getDirectness() == null ? defaults.directness() : dto.getDirectness(),
                dto.getInitiative() == null ? defaults.initiative() : dto.getInitiative(),
                dto.getVerbosity() == null ? defaults.verbosity() : dto.getVerbosity()));
    }

    private static PersonaTraitsDTO toTraitsDTO(Traits traits) {
        PersonaTraitsDTO dto = new PersonaTraitsDTO();
        dto.setWarmth(traits.warmth());
        dto.setHumor(traits.humor());
        dto.setDirectness(traits.directness());
        dto.setInitiative(traits.initiative());
        dto.setVerbosity(traits.verbosity());
        return dto;
    }

    private static PersonaCurrentResponseDTO toCurrent(Preset preset, Traits traits) {
        PersonaCurrentResponseDTO dto = new PersonaCurrentResponseDTO();
        dto.setCode(preset.code());
        dto.setName(preset.name());
        dto.setDescription(preset.description());
        dto.setSample(preset.sample());
        dto.setTraits(toTraitsDTO(traits));
        return dto;
    }

    private static String stageSection(StagePolicy policy, ProfileRow profile) {
        JSONObject config = new JSONObject();
        if (policy != null && policy.getConfig() != null) {
            try {
                config = JSONUtil.parseObj(policy.getConfig());
            } catch (Exception ignored) {
                // 配置损坏时使用默认边界
            }
        }
        String nickname = profile != null && profile.getNickname() != null
                ? profile.getNickname() : "你";
        String address = config.getStr("address", "你")
                .replace("{name}", nickname)
                .replace("{nickname}", nickname)
                .replace("{special}", nickname)
                .replace("{custom}", nickname);
        String topics = join(config.getJSONArray("topics"), "日常");
        String banned = join(config.getJSONArray("banned"), "想你、爱你、抱抱、亲爱的、离不开");
        Integer intensity = config.getInt("intensity", 1);
        Integer disclosure = config.getInt("disclosure", 0);
        Integer initiative = config.getInt("initiative", 0);

        return "阶段边界（优先于人格风格）：\n"
                + "- 语气：" + config.getStr("tone", "礼貌、克制") + "\n"
                + "- 称呼：" + address + "\n"
                + "- 允许话题：" + topics + "\n"
                + "- 表达强度上限：" + intensity + "\n"
                + "- 自我暴露上限：" + disclosure + "\n"
                + "- 主动性上限：" + initiative + "\n"
                + "- 禁用表达：" + banned + "\n";
    }

    private static String traitSection(Traits traits) {
        return "- 温柔度 " + fmt(traits.warmth()) + "：" + warmthHint(traits.warmth()) + "\n"
                + "- 幽默感 " + fmt(traits.humor()) + "：" + humorHint(traits.humor()) + "\n"
                + "- 直接度 " + fmt(traits.directness()) + "：" + directnessHint(traits.directness()) + "\n"
                + "- 主动度 " + fmt(traits.initiative()) + "：" + initiativeHint(traits.initiative()) + "\n"
                + "- 话量 " + fmt(traits.verbosity()) + "：" + verbosityHint(traits.verbosity()) + "\n";
    }

    private static String warmthHint(double value) {
        if (value >= 0.7) return "多表达理解和关心，允许一点柔软，但不夸张";
        if (value <= 0.3) return "保持中性，不刻意安慰";
        return "自然关心，分寸适中";
    }

    private static String humorHint(double value) {
        if (value >= 0.7) return "可以适度玩笑和调侃，但看场合";
        if (value <= 0.3) return "少开玩笑，保持轻松即可";
        return "偶尔轻松一下";
    }

    private static String directnessHint(double value) {
        if (value >= 0.7) return "先给结论，少铺垫";
        if (value <= 0.3) return "语气委婉，多留余地";
        return "直接但留余地";
    }

    private static String initiativeHint(double value) {
        if (value >= 0.7) return "可以主动追问、给建议";
        if (value <= 0.3) return "少主动扩展话题，等对方说";
        return "适度主动";
    }

    private static String verbosityHint(double value) {
        if (value >= 0.7) return "可以展开说明";
        if (value <= 0.3) return "尽量简短";
        return "长短适中";
    }

    private static String fmt(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static String join(JSONArray array, String fallback) {
        if (array == null || array.isEmpty()) {
            return fallback;
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < array.size(); i++) {
            if (i > 0) {
                builder.append("、");
            }
            builder.append(array.getStr(i));
        }
        return builder.toString();
    }
}
