package jeminsmp.sleep;

import jeminsmp.JeminSMPPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class SleepManager {

    private final JeminSMPPlugin plugin;

    private boolean voteActive = false;
    private boolean resolving  = false; // resolve() 중엔 BedLeave 무시
    private UUID   sleeperId;
    private World  voteWorld;

    private final Map<UUID, Boolean> votes = new LinkedHashMap<>();
    private BukkitTask timeoutTask;

    private static final int TIMEOUT_SECONDS = 30;
    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private LocalDate lastSkipDate;

    public SleepManager(JeminSMPPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isVoteActive() { return voteActive; }

    // ── 투표 시작 ──
    public void startVote(Player sleeper, World world) {
        if (voteActive) return;

        // 낮이면 무시 (12541 = 밤 시작, 23458 = 밤 끝)
        long time = world.getTime();
        if (time < 12541 || time > 23458) return;

        // 오늘 이미 밤을 건너뛰었으면 재투표 불가
        if (lastSkipDate != null && lastSkipDate.equals(LocalDate.now(ZONE))) {
            sleeper.sendMessage(Component.text("§7오늘은 이미 밤을 건너뛰었습니다. 내일 다시 시도해주세요."));
            return;
        }

        voteActive = true;
        resolving  = false;
        voteWorld  = world;
        sleeperId  = sleeper.getUniqueId();
        votes.clear();
        votes.put(sleeper.getUniqueId(), true); // 잠자는 사람은 자동 찬성

        // 투표 메시지
        Component msg = Component.text("🌙 ", NamedTextColor.AQUA)
                .append(Component.text(sleeper.getName(), NamedTextColor.GOLD, TextDecoration.BOLD))
                .append(Component.text("님이 잠을 자려 합니다. 밤을 건너뛰겠습니까?  ", NamedTextColor.YELLOW))
                .append(Component.text("[ 찬성 ]", NamedTextColor.GREEN, TextDecoration.BOLD)
                        .clickEvent(ClickEvent.runCommand("/sleepvote yes"))
                        .hoverEvent(HoverEvent.showText(Component.text("클릭하여 찬성"))))
                .append(Component.text("  ", NamedTextColor.WHITE))
                .append(Component.text("[ 반대 ]", NamedTextColor.RED, TextDecoration.BOLD)
                        .clickEvent(ClickEvent.runCommand("/sleepvote no"))
                        .hoverEvent(HoverEvent.showText(Component.text("클릭하여 반대"))));
        Bukkit.broadcast(msg);
        Bukkit.broadcast(Component.text(
                String.format("§8(%d초 안에 투표하세요 | %d명 중 1명 찬성)", TIMEOUT_SECONDS, Bukkit.getOnlinePlayers().size())));

        // 타임아웃
        timeoutTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (voteActive) resolve();
        }, TIMEOUT_SECONDS * 20L);

        checkResult();
    }

    // ── 투표 ──
    public VoteResult vote(Player voter, boolean yes) {
        if (!voteActive) return VoteResult.NO_VOTE;
        if (votes.containsKey(voter.getUniqueId())) return VoteResult.DUPLICATE;

        votes.put(voter.getUniqueId(), yes);

        long yesCount = yesCount();
        long noCount  = noCount();
        int  total    = Bukkit.getOnlinePlayers().size();

        Bukkit.broadcast(Component.text(
                String.format("  %s님이 %s 했습니다.  (찬성 %d / 반대 %d / 전체 %d명)",
                        voter.getName(),
                        yes ? "§a§l찬성§r§7" : "§c§l반대§r§7",
                        yesCount, noCount, total),
                NamedTextColor.GRAY));

        checkResult();
        return VoteResult.OK;
    }

    // ── 퇴장 시 투표 제거 ──
    public void removeVoter(UUID uuid) {
        if (!voteActive) return;
        votes.remove(uuid);
        // 잠자던 본인이 나가면 취소
        if (uuid.equals(sleeperId)) { cancelVote("§7잠자던 플레이어가 서버를 떠났습니다. 투표가 취소됩니다."); return; }
        checkResult();
    }

    // ── 잠자던 본인이 침대에서 나왔을 때 ──
    public void onSleeperWakeup(UUID uuid) {
        if (!voteActive || resolving) return;
        if (uuid.equals(sleeperId)) cancelVote("§7잠자던 플레이어가 일어났습니다. 투표가 취소됩니다.");
    }

    // ── 결과 판정 ──
    private void checkResult() {
        int  total    = Bukkit.getOnlinePlayers().size();
        long yesCount = yesCount();
        long noCount  = noCount();

        // 모두 투표 완료
        if (votes.size() >= total) { resolve(); return; }
        // 과반수 찬성 확정
        if (yesCount > total / 2.0) { resolve(); return; }
        // 과반수 반대 확정 (나머지가 전부 찬성해도 안 됨)
        if (noCount > total / 2.0) { resolve(); }
    }

    private void resolve() {
        if (!voteActive) return;
        resolving  = true;
        voteActive = false;
        if (timeoutTask != null) { timeoutTask.cancel(); timeoutTask = null; }

        long yesCount = yesCount();
        long noCount  = noCount();

        if (yesCount > noCount) {
            lastSkipDate = LocalDate.now(ZONE);
            voteWorld.setTime(0);
            // 자고 있는 플레이어 깨우기
            for (Player p : voteWorld.getPlayers()) {
                if (p.isSleeping()) p.wakeup(false);
            }
            Bukkit.broadcast(Component.text("☀ 투표 통과! 밤을 건너뜁니다. (찬성 " + yesCount + " / 반대 " + noCount + ")",
                    NamedTextColor.YELLOW, TextDecoration.BOLD));
        } else {
            Bukkit.broadcast(Component.text("🌙 투표 부결. 밤이 계속됩니다. (찬성 " + yesCount + " / 반대 " + noCount + ")",
                    NamedTextColor.GRAY));
        }

        reset();
    }

    private void cancelVote(String reason) {
        if (!voteActive) return;
        voteActive = false;
        if (timeoutTask != null) { timeoutTask.cancel(); timeoutTask = null; }
        Bukkit.broadcast(Component.text(reason, NamedTextColor.GRAY));
        reset();
    }

    private void reset() {
        voteWorld = null;
        sleeperId = null;
        votes.clear();
        resolving = false;
    }

    private long yesCount() { return votes.values().stream().filter(v -> v).count(); }
    private long noCount()  { return votes.values().stream().filter(v -> !v).count(); }

    public enum VoteResult { OK, DUPLICATE, NO_VOTE }
}
