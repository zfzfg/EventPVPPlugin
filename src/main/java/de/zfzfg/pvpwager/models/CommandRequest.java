package de.zfzfg.pvpwager.models;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class CommandRequest {
    private final Player sender;
    private final Player target;
    private List<ItemStack> senderWagerItems;
    private double senderWagerMoney;
    private String arenaId;
    private String equipmentId;
    private long timestamp;
    
    // Target's response
    private List<ItemStack> targetWagerItems;
    private double targetWagerMoney;
    private String targetArenaId;  // Optional: can override
    private String targetEquipmentId;  // Optional: can override
    private boolean targetResponded = false;
    private boolean senderConfirmed = false;
    
    public CommandRequest(Player sender, Player target, List<ItemStack> wagerItems, 
                         double wagerMoney, String arenaId, String equipmentId) {
        this.sender = sender;
        this.target = target;
        this.senderWagerItems = new ArrayList<>(wagerItems);
        this.senderWagerMoney = wagerMoney;
        this.arenaId = arenaId;
        this.equipmentId = equipmentId;
        this.timestamp = System.currentTimeMillis();
        this.targetWagerItems = new ArrayList<>();
        this.targetWagerMoney = 0.0;
    }
    
    public void setTargetResponse(List<ItemStack> items, double money, String arena, String equipment) {
        this.targetWagerItems = new ArrayList<>(items);
        this.targetWagerMoney = money;
        
        // Optional overrides
        if (arena != null && !arena.isEmpty()) {
            this.targetArenaId = arena;
        }
        if (equipment != null && !equipment.isEmpty()) {
            this.targetEquipmentId = equipment;
        }
        
        this.targetResponded = true;
    }
    
    // Getters
    public Player getSender() { return sender; }
    public Player getTarget() { return target; }
    public List<ItemStack> getWagerItems() { return senderWagerItems; }
    public double getMoney() { return senderWagerMoney; }
    public String getArenaId() { return arenaId; }
    public String getEquipmentId() { return equipmentId; }
    public long getTimestamp() { return timestamp; }
    
    public List<ItemStack> getTargetWagerItems() { return targetWagerItems; }
    public double getTargetWagerMoney() { return targetWagerMoney; }
    public String getFinalArenaId() { return targetArenaId != null ? targetArenaId : arenaId; }
    public String getFinalEquipmentId() { return targetEquipmentId != null ? targetEquipmentId : equipmentId; }
    
    public boolean hasTargetResponded() { return targetResponded; }
    public boolean hasSenderConfirmed() { return senderConfirmed; }
    
    public void setSenderConfirmed(boolean confirmed) { this.senderConfirmed = confirmed; }
}