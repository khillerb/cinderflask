package dev.cinderflask.screen;

import dev.cinderflask.Cinderflask;
import dev.cinderflask.item.CinderflaskItem;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;

public class CinderflaskScreenHandler extends ScreenHandler {
    /** Player-inventory slot index of the offhand, as used by {@link SlotActionType#SWAP}. */
    private static final int OFFHAND_SWAP_BUTTON = 40;

    public static final int INTAKE_SLOT_X = 80;
    public static final int INTAKE_SLOT_Y = 26;

    private final PlayerEntity player;
    private final Hand hand;
    private final CinderflaskInventory intake;

    /** Client-side constructor, fed by {@link CinderflaskScreenHandlerFactory}. */
    public CinderflaskScreenHandler(int syncId, PlayerInventory playerInventory, PacketByteBuf buf) {
        this(syncId, playerInventory, buf.readByte() == 0 ? Hand.MAIN_HAND : Hand.OFF_HAND);
    }

    public CinderflaskScreenHandler(int syncId, PlayerInventory playerInventory, Hand hand) {
        super(Cinderflask.SCREEN_HANDLER, syncId);

        this.player = playerInventory.player;
        this.hand = hand;
        this.intake = new CinderflaskInventory(getFlask(), !player.getWorld().isClient);

        addSlot(new IntakeSlot(intake, 0, INTAKE_SLOT_X, INTAKE_SLOT_Y));

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(playerInventory, column + row * 9 + 9, 8 + column * 18, 84 + row * 18));
            }
        }

        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(playerInventory, column, 8 + column * 18, 142));
        }
    }

    /** The live stack in the player's hand, not a copy, so NBT is always current. */
    public ItemStack getFlask() {
        return player.getStackInHand(hand);
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return player.getStackInHand(hand).getItem() instanceof CinderflaskItem;
    }

    /**
     * Blocks any click that would move the flask out from under its own screen: picking it up,
     * shift-clicking it, or hotbar/offhand-swapping it away.
     */
    @Override
    public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
        if (slotIndex >= 0 && slotIndex < slots.size() && slots.get(slotIndex).getStack() == getFlask()) {
            return;
        }

        if (actionType == SlotActionType.SWAP) {
            boolean swappingHeldHand = hand == Hand.MAIN_HAND
                    ? button == player.getInventory().selectedSlot
                    : button == OFFHAND_SWAP_BUTTON;

            if (swappingHeldHand) {
                return;
            }
        }

        super.onSlotClick(slotIndex, button, actionType, player);
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int index) {
        Slot slot = slots.get(index);

        if (!slot.hasStack()) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = slot.getStack();

        if (index == 0) {
            if (!insertItem(stack, 1, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!insertItem(stack, 0, 1, false)) {
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) {
            slot.setStack(ItemStack.EMPTY);
        } else {
            slot.markDirty();
        }

        return ItemStack.EMPTY;
    }

    @Override
    public void onClosed(PlayerEntity player) {
        super.onClosed(player);

        ItemStack leftover = intake.removeStack(0);

        if (!leftover.isEmpty() && !player.getWorld().isClient && !player.getInventory().insertStack(leftover)) {
            player.dropItem(leftover, false);
        }
    }

    /** Rejects anything the flask will not swallow, so the click simply does not happen. */
    private static class IntakeSlot extends Slot {
        IntakeSlot(CinderflaskInventory inventory, int index, int x, int y) {
            super(inventory, index, x, y);
        }

        @Override
        public boolean canInsert(ItemStack stack) {
            return CinderflaskItem.isValidFuel(stack);
        }
    }
}
