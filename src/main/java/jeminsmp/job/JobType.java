package jeminsmp.job;

public enum JobType {
    MINER("광부", "⛏", "채굴의 여정", "광물 채굴"),
    FARMER("농부", "🌾", "수확의 여정", "다 자란 작물 수확"),
    WARRIOR("전사", "⚔", "전투의 여정", "몹/플레이어 처치"),
    FISHER("어부", "🎣", "낚시의 여정", "물고기 낚기");

    private final String display;
    private final String icon;
    private final String questName;
    private final String questAction;

    JobType(String display, String icon, String questName, String questAction) {
        this.display = display;
        this.icon = icon;
        this.questName = questName;
        this.questAction = questAction;
    }

    public String display() { return display; }
    public String icon() { return icon; }
    public String questName() { return questName; }
    public String questAction() { return questAction; }

    public static JobType fromString(String s) {
        for (JobType t : values()) {
            if (t.name().equalsIgnoreCase(s) || t.display.equals(s)) return t;
        }
        return null;
    }
}
