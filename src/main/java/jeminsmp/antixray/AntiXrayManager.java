package jeminsmp.antixray;

import jeminsmp.JeminSMPPlugin;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class AntiXrayManager {

    private final JeminSMPPlugin plugin;

    public AntiXrayManager(JeminSMPPlugin plugin) {
        this.plugin = plugin;
        setup();
    }

    private void setup() {
        File paperConfig = new File("config/paper-world-defaults.yml");
        if (!paperConfig.exists()) {
            plugin.getLogger().warning("[AntiXray] config/paper-world-defaults.yml 없음. 서버가 Paper인지 확인하세요.");
            return;
        }

        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(paperConfig);

        boolean alreadyEnabled = cfg.getBoolean("anticheat.anti-xray.enabled", false);
        int currentMode = cfg.getInt("anticheat.anti-xray.engine-mode", 1);

        if (alreadyEnabled && currentMode == 2) {
            plugin.getLogger().info("[AntiXray] 이미 활성화되어 있습니다 (엔진 모드 2).");
            return;
        }

        // 엔진 모드 2: 클라이언트에 가짜 광물 블록을 섞어 전송 → 가장 강력한 엑스레이 차단
        cfg.set("anticheat.anti-xray.enabled", true);
        cfg.set("anticheat.anti-xray.engine-mode", 2);
        cfg.set("anticheat.anti-xray.max-block-height", 64);
        cfg.set("anticheat.anti-xray.update-radius", 2);
        cfg.set("anticheat.anti-xray.lava-obscures", false);
        cfg.set("anticheat.anti-xray.use-permission", false);

        // 숨길 광물 목록
        cfg.set("anticheat.anti-xray.hidden-blocks", List.of(
                "copper_ore", "deepslate_copper_ore", "raw_copper_block",
                "diamond_ore", "deepslate_diamond_ore",
                "gold_ore", "deepslate_gold_ore",
                "iron_ore", "deepslate_iron_ore", "raw_iron_block",
                "lapis_ore", "deepslate_lapis_ore",
                "redstone_ore", "deepslate_redstone_ore",
                "emerald_ore", "deepslate_emerald_ore",
                "ancient_debris"
        ));

        // 가짜로 보여줄 블록 목록
        cfg.set("anticheat.anti-xray.replacement-blocks", List.of(
                "stone", "deepslate", "granite", "diorite", "andesite", "tuff", "oak_planks"
        ));

        try {
            cfg.save(paperConfig);
            plugin.getLogger().info("[AntiXray] 설정 완료 (엔진 모드 2). 서버 재시작 후 적용됩니다.");
        } catch (IOException e) {
            plugin.getLogger().severe("[AntiXray] 설정 저장 실패: " + e.getMessage());
        }
    }
}
