package jeminsmp.mathquiz;

import jeminsmp.JeminSMPPlugin;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.Random;

public class MathQuizManager {

    public enum QuizType {
        ADD, SUB, MUL, DIV, SQUARE, MODULO,
        ADD_MUL,   // (a + b) × c
        MUL_ADD,   // a × b + c
        EQUATION,  // a×x + b = c → x = ?
        CHAIN,     // a + b - c × d
        // ── 고1 경우의 수 ──
        ADD_RULE,  // 합의 법칙
        MUL_RULE   // 곱의 법칙
    }

    public record Quiz(String question, int answer) {}

    private final JeminSMPPlugin plugin;
    private final Random random = new Random();

    private Quiz currentQuiz = null;
    private boolean quizActive = false;
    private BukkitTask intervalTask;
    private BukkitTask timeoutTask;

    private int intervalMinutes = 60;
    private int timeoutSeconds  = 60;
    private static final int PENALTY_SECONDS = 20;

    public MathQuizManager(JeminSMPPlugin plugin) {
        this.plugin = plugin;
        this.intervalMinutes = plugin.getConfig().getInt("mathquiz.interval-minutes", 60);
        this.timeoutSeconds  = plugin.getConfig().getInt("mathquiz.timeout-seconds", 60);
        startScheduler();
    }

    // ── 스케줄러 ──

    private void startScheduler() {
        long intervalTicks = (long) intervalMinutes * 60 * 20;
        intervalTask = Bukkit.getScheduler().runTaskTimer(plugin, this::postQuiz, intervalTicks, intervalTicks);
    }

    public void reload() {
        this.intervalMinutes = plugin.getConfig().getInt("mathquiz.interval-minutes", 60);
        this.timeoutSeconds  = plugin.getConfig().getInt("mathquiz.timeout-seconds", 60);
        if (intervalTask != null) intervalTask.cancel();
        startScheduler();
    }

    public void shutdown() {
        if (intervalTask != null) intervalTask.cancel();
        if (timeoutTask  != null) timeoutTask.cancel();
    }

    // ── 문제 출제 ──

    public void postQuiz() {
        if (Bukkit.getOnlinePlayers().isEmpty()) return;
        if (quizActive) return;
        forcePostQuiz();
    }

    public void forcePostQuiz() {
        currentQuiz = generateQuiz();
        quizActive  = true;

        Bukkit.broadcast(Component.text("§8§l━━━━━━━━━━━━━━━━━━━━━━━━"));
        Bukkit.broadcast(Component.text("§e§l🧮 수학 퀴즈! §f채팅에 숫자만 입력하세요."));
        Bukkit.broadcast(Component.text("§f  " + currentQuiz.question()));
        Bukkit.broadcast(Component.text("§7  (" + timeoutSeconds + "초 안에 맞추면 §6다이아몬드 1개§7 지급!)"));
        Bukkit.broadcast(Component.text("§c  ⚠ 못 맞추면 §f" + PENALTY_SECONDS + "초 §c이동 속도 감소 패널티!"));
        Bukkit.broadcast(Component.text("§8§l━━━━━━━━━━━━━━━━━━━━━━━━"));

        if (timeoutTask != null) timeoutTask.cancel();
        timeoutTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!quizActive) return;
            quizActive  = false;
            currentQuiz = null;
            Bukkit.broadcast(Component.text("§c⏰ 시간 초과! 아무도 맞추지 못했습니다."));
            Bukkit.broadcast(Component.text("§c모든 플레이어에게 " + PENALTY_SECONDS + "초 이동 속도 감소 패널티!"));
            applySlownessPenalty();
        }, (long) timeoutSeconds * 20);
    }

    // ── 패널티 ──

    private void applySlownessPenalty() {
        int ticks = PENALTY_SECONDS * 20;
        PotionEffect slowness = new PotionEffect(PotionEffectType.SLOWNESS, ticks, 1, false, true, true);
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.addPotionEffect(slowness);
            p.sendMessage("§c🐢 이동 속도 감소! (" + PENALTY_SECONDS + "초)");
        }
    }

    // ── 정답 확인 ──

    public boolean checkAnswer(Player player, String input) {
        if (!quizActive || currentQuiz == null) return false;
        int parsed;
        try {
            parsed = Integer.parseInt(input.trim());
        } catch (NumberFormatException e) {
            return false;
        }
        if (parsed != currentQuiz.answer()) return false;

        quizActive  = false;
        currentQuiz = null;
        if (timeoutTask != null) timeoutTask.cancel();

        ItemStack diamond = new ItemStack(Material.DIAMOND, 1);
        if (player.getInventory().firstEmpty() == -1) {
            player.getWorld().dropItemNaturally(player.getLocation(), diamond);
        } else {
            player.getInventory().addItem(diamond);
        }

        Bukkit.broadcast(Component.text("§a§l🎉 정답! §f" + player.getName() + "§a 님이 맞췄습니다! §6다이아몬드 1개 지급!"));
        return true;
    }

    public void forceStop() {
        quizActive  = false;
        currentQuiz = null;
        if (timeoutTask != null) timeoutTask.cancel();
    }

    public boolean isActive() { return quizActive; }
    public Quiz getCurrentQuiz() { return currentQuiz; }
    public int getIntervalMinutes() { return intervalMinutes; }
    public int getTimeoutSeconds() { return timeoutSeconds; }

    // ── 문제 생성 ──

    private Quiz generateQuiz() {
        QuizType type = QuizType.values()[random.nextInt(QuizType.values().length)];
        return switch (type) {
            case ADD        -> makeAdd();
            case SUB        -> makeSub();
            case MUL        -> makeMul();
            case DIV        -> makeDiv();
            case SQUARE     -> makeSquare();
            case MODULO     -> makeModulo();
            case ADD_MUL    -> makeAddMul();
            case MUL_ADD    -> makeMulAdd();
            case EQUATION   -> makeEquation();
            case CHAIN      -> makeChain();
            case ADD_RULE   -> makeAddRule();
            case MUL_RULE   -> makeMulRule();
        };
    }

    // 기본
    private Quiz makeAdd() {
        int a = rand(500, 2000);
        int b = rand(500, 2000);
        return new Quiz(a + " + " + b + " = ?", a + b);
    }

    private Quiz makeSub() {
        int a = rand(1000, 5000);
        int b = rand(100, a - 1);
        return new Quiz(a + " - " + b + " = ?", a - b);
    }

    private Quiz makeMul() {
        int a = rand(20, 99);
        int b = rand(20, 99);
        return new Quiz(a + " × " + b + " = ?", a * b);
    }

    private Quiz makeDiv() {
        int b = rand(7, 50);
        int q = rand(10, 100);
        return new Quiz((b * q) + " ÷ " + b + " = ?", q);
    }

    private Quiz makeSquare() {
        int a = rand(15, 40);
        return new Quiz(a + "² = ?", a * a);
    }

    private Quiz makeModulo() {
        int b = rand(7, 30);
        int a = rand(b + 1, 500);
        return new Quiz(a + " mod " + b + " = ?", a % b);
    }

    // 심화
    /** (a + b) × c */
    private Quiz makeAddMul() {
        int a = rand(10, 80);
        int b = rand(10, 80);
        int c = rand(3, 15);
        return new Quiz("(" + a + " + " + b + ") × " + c + " = ?", (a + b) * c);
    }

    /** a × b + c */
    private Quiz makeMulAdd() {
        int a = rand(15, 50);
        int b = rand(15, 50);
        int c = rand(100, 500);
        return new Quiz(a + " × " + b + " + " + c + " = ?", a * b + c);
    }

    /** a × x + b = c  →  x = ? */
    private Quiz makeEquation() {
        int a = rand(3, 15);
        int x = rand(5, 30);
        int b = rand(10, 100);
        int c = a * x + b;
        return new Quiz(a + "x + " + b + " = " + c + "  (x = ?)", x);
    }

    /** a + b × c - d */
    private Quiz makeChain() {
        int a = rand(50, 300);
        int b = rand(5, 30);
        int c = rand(5, 30);
        int d = rand(10, 100);
        // 연산자 우선순위: a + (b × c) - d
        return new Quiz(a + " + " + b + " × " + c + " - " + d + " = ?", a + b * c - d);
    }

    private int rand(int min, int max) {
        return random.nextInt(max - min + 1) + min;
    }

    // ── 경우의 수 ──

    /** 합의 법칙 서술형 */
    private Quiz makeAddRule() {
        String[][] templates = {
            {"{a}개의 자음과 {b}개의 모음 중 1개를 고르는 경우의 수는?", "add"},
            {"A 노선 버스 {a}대, B 노선 버스 {b}대 중 1대를 타는 경우의 수는?", "add"},
            {"빨간 구슬 {a}개와 파란 구슬 {b}개 중 1개를 꺼내는 경우의 수는?", "add"},
            {"{a}종류의 과자와 {b}종류의 음료 중 한 가지를 고르는 경우의 수는?", "add"},
        };
        String[] t = templates[random.nextInt(templates.length)];
        int a = rand(2, 8), b = rand(2, 8);
        return new Quiz(t[0].replace("{a}", String.valueOf(a)).replace("{b}", String.valueOf(b)), a + b);
    }

    /** 곱의 법칙 서술형 */
    private Quiz makeMulRule() {
        String[][] templates = {
            {"{a}가지 상의와 {b}가지 하의를 입는 경우의 수는?", "mul"},
            {"동전을 {a}번 던질 때 나오는 경우의 수는?", "pow2"},
            {"{a}개의 자음과 {b}개의 모음 중 자음 1개, 모음 1개를 고르는 경우의 수는?", "mul"},
            {"A 도시에서 B까지 {a}개의 길, B에서 C까지 {b}개의 길이 있을 때 A→C 경우의 수는?", "mul"},
            {"주사위 {a}개를 동시에 던질 때 나오는 경우의 수는?", "pow6"},
        };
        int idx = random.nextInt(templates.length);
        String tmpl = templates[idx][0];
        String type = templates[idx][1];
        int a = rand(2, 6), b = rand(2, 6);
        int answer = switch (type) {
            case "mul"  -> a * b;
            case "pow2" -> (int) Math.pow(2, a);
            case "pow6" -> (int) Math.pow(6, a);
            default     -> a * b;
        };
        String q = tmpl.replace("{a}", String.valueOf(a)).replace("{b}", String.valueOf(b));
        return new Quiz(q, answer);
    }

}
