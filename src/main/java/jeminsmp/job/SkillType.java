package jeminsmp.job;

public enum SkillType {
    HEAL("힐"),
    DEAL("딜"),
    FORCE("포스"),
    MOVE("이동기");

    private final String display;

    SkillType(String display) { this.display = display; }

    public String display() { return display; }

    public static SkillType fromString(String s) {
        for (SkillType t : values()) {
            if (t.name().equalsIgnoreCase(s) || t.display.equals(s)) return t;
        }
        return null;
    }
}
