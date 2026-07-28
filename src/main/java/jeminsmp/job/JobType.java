package jeminsmp.job;

public enum JobType {
    MINER("광부", "⛏"),
    FARMER("농부", "🌾"),
    WARRIOR("전사", "⚔"),
    FISHER("어부", "🎣");

    private final String display;
    private final String icon;

    JobType(String display, String icon) {
        this.display = display;
        this.icon = icon;
    }

    public String display() { return display; }
    public String icon() { return icon; }

    public static JobType fromString(String s) {
        for (JobType t : values()) {
            if (t.name().equalsIgnoreCase(s) || t.display.equals(s)) return t;
        }
        return null;
    }
}
