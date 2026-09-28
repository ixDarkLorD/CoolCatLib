package net.ixdarklord.coolcatcore.api.container;

/**
 * Who may put items into a slot and take them out: players through a menu, and automation (hoppers, pipes,
 * {@link ItemTransfer}). The holder's own code ({@link SlotContainer#insert}, {@link SlotContainer#setItem}) may
 * always do both.
 */
public enum SlotRole {
    /** Anyone puts in and takes out. */
    STORAGE(true, true, true),
    /** Automation only puts in (a furnace's ingredient); players do both. */
    INPUT(true, true, false),
    /** Nobody puts in (a machine's result); anyone takes out. */
    OUTPUT(false, false, true),
    /** Players only; automation can't reach it (an upgrade slot). */
    INTERNAL(true, false, false);

    private final boolean playerInsert;
    private final boolean automationInsert;
    private final boolean automationExtract;

    SlotRole(boolean playerInsert, boolean automationInsert, boolean automationExtract) {
        this.playerInsert = playerInsert;
        this.automationInsert = automationInsert;
        this.automationExtract = automationExtract;
    }

    public boolean playerInsert() {
        return this.playerInsert;
    }

    public boolean automationInsert() {
        return this.automationInsert;
    }

    public boolean automationExtract() {
        return this.automationExtract;
    }
}
