package jetpack;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.*;
import org.bukkit.inventory.*;
import java.util.*;
import java.util.logging.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.*;
import org.bukkit.configuration.*;
import org.bukkit.enchantments.*;

public class Jetpack extends JavaPlugin {
    
    private Set<Player> hasJetpack = new HashSet<Player>();
    private Map<ItemStack, Double> damageOverflow = new HashMap<ItemStack, Double>();
    private long CHECK_FREQUENCY = 10L; // in ticks; 20 ticks = 1 second
    
    private double damagePerTick = 0.05;
    private Random rng = new Random();
    
    private void addJetpackType(Material armor, Material ingredient, String materialName){
        ItemStack jetpack = new ItemStack(armor);
        ItemMeta jetpackMeta = jetpack.getItemMeta();
        jetpackMeta.setDisplayName(ChatColor.WHITE + materialName + " Jetpack");
        jetpack.setItemMeta(jetpackMeta);
        ShapedRecipe jetpackRecipe = new ShapedRecipe(jetpack);
        jetpackRecipe.shape("IRI", "III", "B B");
        jetpackRecipe.setIngredient('I', ingredient);
        jetpackRecipe.setIngredient('B', Material.BLAZE_ROD);
        jetpackRecipe.setIngredient('R', Material.REDSTONE);
        getServer().addRecipe(jetpackRecipe);
    }
    
    private boolean isJetpack(ItemStack i){
        ItemMeta m = i.getItemMeta();
        if (m == null) return false;
        String name = m.getDisplayName();
        if (name == null) return false;
        return name.endsWith("Jetpack");
    }
    
    private boolean shouldDoDamage(ItemStack jetpack){
        if (!jetpack.containsEnchantment(Enchantment.DURABILITY))
            return true;
        
        int level = jetpack.getEnchantmentLevel(Enchantment.DURABILITY);
        int probability = 60 + 40/(level+1);
        return rng.nextInt(100) < probability;
    }
    
    public void onEnable(){
        Configuration config = getConfig();
        List<String> allowedTypes = (List<String>) config.get("allow-types", Arrays.asList("iron", "diamond", "chainmail", "leather", "gold"));
        
        if (allowedTypes.contains("iron"))
            addJetpackType(Material.IRON_CHESTPLATE, Material.IRON_INGOT, "Iron");
        if (allowedTypes.contains("diamond"))
            addJetpackType(Material.DIAMOND_CHESTPLATE, Material.DIAMOND, "Diamond");
        if (allowedTypes.contains("chainmail"))
            addJetpackType(Material.CHAINMAIL_CHESTPLATE, Material.FIRE, "Chainmail");
        if (allowedTypes.contains("leather"))
            addJetpackType(Material.LEATHER_CHESTPLATE, Material.LEATHER, "Leather");
        if (allowedTypes.contains("gold"))
            addJetpackType(Material.GOLD_CHESTPLATE, Material.GOLD_INGOT, "Gold");
        
        damagePerTick = config.getDouble("damage-per-tick", 0.02);
        
        getServer().getScheduler().scheduleSyncRepeatingTask(this, new Runnable() {
            public void run() {
                Player[] players = Jetpack.this.getServer().getOnlinePlayers();
                for (Player p : players) {
                    ItemStack chestplate = p.getInventory().getChestplate();
                    
                    if (chestplate != null && isJetpack(chestplate) && p.hasPermission("jetpack.use")){ // jetpack is equipped
                        hasJetpack.add(p);
                        p.setAllowFlight(true);
                        
                        if (p.isFlying() && shouldDoDamage(chestplate) && p.getGameMode() != GameMode.CREATIVE){
                            double damage = damagePerTick * CHECK_FREQUENCY;
                            if (damageOverflow.containsKey(chestplate))
                                damage += damageOverflow.get(chestplate);
                            
                            
                            short prevDura = chestplate.getDurability();
                            short dura = (short) (prevDura + Math.floor(damage));
                            chestplate.setDurability(dura);
                            
                            double overflow = damage - Math.floor(damage);
                            if (overflow > 0.0)
                                damageOverflow.put(chestplate, overflow);
                            
                            short maxDura = chestplate.getType().getMaxDurability();
                            if (dura > maxDura){
                                // break jetpack
                                p.getInventory().setChestplate(new ItemStack(Material.AIR));
                                p.playSound(p.getLocation(), Sound.ITEM_BREAK, 1, 1);
                                damageOverflow.remove(chestplate);
                                hasJetpack.remove(p);
                                p.setAllowFlight(false);
                            } else if (maxDura - dura <= 10 && maxDura - prevDura > 10){
                                p.sendRawMessage(ChatColor.RED + "Warning! Jetpack will break soon!");
                            }
                        }
                        
                    } else { // jetpack is not equipped
                        if ((hasJetpack.contains(p) || !p.hasPermission("jetpack.use")) && p.getGameMode() != GameMode.CREATIVE)
                            p.setAllowFlight(false);
                        
                        hasJetpack.remove(p);
                    }
                }
            }
        }, 0L, CHECK_FREQUENCY);
    }
}