package jeminsmp.team.mission;

public record Mission(
    String id,
    String name,
    String description,
    MissionType type,
    long required,
    long rewardTicks,
    boolean daily
) {}
