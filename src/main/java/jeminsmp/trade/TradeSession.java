package jeminsmp.trade;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

public class TradeSession {

    public enum State { OPEN, CONFIRMED_A, CONFIRMED_B, DONE }

    public final Player playerA;
    public final Player playerB;
    public Inventory guiA;
    public Inventory guiB;
    public State state = State.OPEN;

    // 아이템 올린 뒤 확정 여부
    public boolean lockedA = false;
    public boolean lockedB = false;

    public TradeSession(Player a, Player b) {
        this.playerA = a;
        this.playerB = b;
    }

    public Player getOpponent(Player p) {
        return p.getUniqueId().equals(playerA.getUniqueId()) ? playerB : playerA;
    }

    public boolean isA(Player p) {
        return p.getUniqueId().equals(playerA.getUniqueId());
    }

    public boolean isLockedBy(Player p) {
        return isA(p) ? lockedA : lockedB;
    }

    public void setLocked(Player p, boolean v) {
        if (isA(p)) lockedA = v; else lockedB = v;
    }

    public Inventory getGui(Player p) {
        return isA(p) ? guiA : guiB;
    }

    public Inventory getOpponentGui(Player p) {
        return isA(p) ? guiB : guiA;
    }

    public boolean bothLocked() {
        return lockedA && lockedB;
    }
}
