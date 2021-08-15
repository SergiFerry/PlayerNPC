package dev.sergiferry.playernpc.api;

/**
 * Creado por SergiFerry.
 */
public enum NPCSlot {

    HELMET(4, "HEAD"),
    CHESTPLATE(3, "CHEST"),
    LEGGINGS(2, "LEGS"),
    BOOTS(1, "FEET"),
    MAINHAND(0, "MAINHAND"),
    OFFHAND(5, "OFFHAND"),
    ;

    private final int slot;
    private final String nmsName;

    NPCSlot(int slot, String nmsName) {
        this.slot = slot;
        this.nmsName = nmsName;
    }

    public int getSlot() {
        return this.slot;
    }

    protected String getNmsName() {
        return this.nmsName;
    }

    protected  <E extends Enum<E>> E getNmsEnum(Class<E> nmsEnumClass) {
        return Enum.valueOf(nmsEnumClass, this.nmsName);
    }
}
