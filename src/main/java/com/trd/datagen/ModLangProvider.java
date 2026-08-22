package com.trd.datagen;

import com.trd.main.ResourceRegistry;
import net.minecraft.data.PackOutput;
import net.minecraftforge.common.data.LanguageProvider;
import com.trd.main.MainRegistry;
import com.trd.block.basic.ModBlocks;
import com.trd.item.ModItems;

import javax.annotation.Nullable;

public class ModLangProvider extends LanguageProvider {

    protected final String locale;

    public ModLangProvider(PackOutput output, String locale) {
        super(output, MainRegistry.MOD_ID, locale);
        this.locale = locale;

        // !!! ВАЖНО: Инициализируем ResourceRegistry !!!
        ResourceRegistry.init();
    }

    /**
     * Универсальная регистрация перевода для жидкости, её капли и ключа fluid.*
     * @param fluidId   короткое имя жидкости (например "hydrogen_peroxide")
     * @param nameRu    перевод на русский
     * @param nameUa    перевод на украинский (можно null)
     * @param nameEn    перевод на английский (можно null, но тогда будет пропущен)
     */
    private void addFluidTranslations(String fluidId, String nameRu, @Nullable String nameUa, @Nullable String nameEn) {
        switch (locale) {
            case "ru_ru":
                add("fluid_type.trd." + fluidId, nameRu);
                add("fluid.trd." + fluidId, nameRu);
                add("item.trd.fluid_drop_" + fluidId, nameRu);
                break;
            case "en_us":
                if (nameEn != null) {
                    add("fluid_type.trd." + fluidId, nameEn);
                    add("fluid.trd." + fluidId, nameEn);
                    add("item.trd.fluid_drop_" + fluidId, nameEn);
                }
                break;
            // другие локали можно добавить аналогично
        }
    }

    @Override
    protected void addTranslations() {
        // Сначала автоматические переводы для ресурсов
        ResourceDatagenHelper.generateTranslations(this, locale);

        // Затем ручные переводы
        if (locale.equals("ru_ru")) {
            addRussian();
        } else {
            addEnglish();
        }
    }

    private void addEnglish() {
        // Death messages
        add("death.attack.crusher", "%1$s became minced meat");

        // Creative Tabs
        add("itemGroup.trd.trd_build_tab", "Building Blocks");
        add("itemGroup.trd.trd_tech_tab", "Technology");
        add("itemGroup.trd.trd_weapons_tab", "Arsenal");
        add("itemGroup.trd.trd_recourses_tab", "Resources");
        add("itemGroup.trd.trd_nature_tab", "Nature");


        // Tooltips & Messages
        add("tooltip.trd.detminer.desc", "Breaks blocks in a natural blast pattern");
        add("tooltip.trd.detminer.hardness", "Only affects blocks with hardness < 30");
        add("tooltip.trd.detminer.conglomerate", "Has a chance to extract resources from conglomerate");
        add("item.trd.fluid_identifier", "Fluid Identifier");
        add("message.trd.selected_fluid", "Selected");
        add("tooltip.trd.no_fluid", "No fluid selected");
        add("tooltip.trd.shaft_material", "Material");
        add("tooltip.trd.max_speed", "Max Speed");
        add("tooltip.trd.max_torque", "Max Torque");
        add("tooltip.trd.inertia", "Inertia");
        add("message.trd.too_far_from_support", "Unsupported span! Max distance from support for this diameter: %s blocks.");

        // Heater Tiers
        add("gui.trd.heater.tier0", "Tier 0");
        add("gui.trd.heater.tier1", "Tier I");
        add("gui.trd.heater.tier2", "Tier II");
        add("gui.trd.heater.tier3", "Tier III");
        add("gui.trd.heater.tier4", "Tier IV");
        add("gui.trd.heater.tier5", "Tier V");

        // Fluids
        addFluidTranslations("hydrogen_peroxide", "Пероксид водорода", null, "Hydrogen Peroxide");
        addFluidTranslations("sulfuric_acid", "Серная кислота", null, "Sulfuric Acid");
        addFluidTranslations("natural_gas", "Природный газ", null, "Natural Gas");
        addFluidTranslations("steam", "Пар", null, "Steam");
        addFluidTranslations("low_pressure_steam", "Пар низкого давления", null, "Low Pressure Steam");
        addFluidTranslations("water", "Вода", "Вода", "Water");
        addFluidTranslations("lava", "Лава", "Лава", "Lava");
        addFluidTranslations("mercury", "Ртуть", "Ртуть", "Mercury");

        // JEI Categories
        add("jei.category.trd.smelting", "Smelting");
        add("jei.category.trd.casting", "Casting");
        add("jei.category.trd.alloying", "Alloying");
        add("jei.category.trd.millstone", "Millstone");
        add("jei.category.trd.boiling", "Boiler");
        add("jei.category.trd.steam_engine", "Steam Engine");
        add("jei.category.trd.condensing", "Condensing");
        add("jei.category.trd.electric_furnace", "Electric Furnace");
        add("jei.category.trd.coccer_oven", "Coke Oven");
        add("jei.category.trd.chemical_plant", "Chemical Plant");
        add("jei.category.trd.vishelashivatel", "Leacher");

        // Metals
        add("metal.trd.gold", "Gold");
        add("metal.trd.iron", "Iron");
        add("metal.trd.copper", "Copper");
        add("metal.trd.netherite", "Netherite");
        add("metal.trd.steel", "Steel");
        add("metal.trd.aluminum", "Aluminum");
        add("metal.trd.bronze", "Bronze");
        add("metal.trd.tin", "Tin");
        add("metal.trd.zinc", "Zinc");
        add("metal.trd.titanium", "Titanium");
        add("metal.trd.lead", "Lead");
        add("metal.trd.beryllium", "Beryllium");
        add("metal.trd.industrial_copper", "Industrial Copper");
        add("metal.trd.tungsten", "Tungsten");
        add("metal.trd.neodymium", "Neodymium");

        // Cast Pickaxe Tooltips
        // Cast Pickaxe Tooltips
        add("item.trd.cast_pickaxe.desc.charge", "§7Hold RMB for a powerful strike");
        add("item.trd.cast_pickaxe.desc.mining_power", "§6Power: %s");
        add("item.trd.cast_pickaxe.desc.vein_miner_info", "Vein Miner: %s");
        add("item.trd.cast_pickaxe.desc.tunnel_miner", "Tunnel Miner: %s");

        // ═══ Chemistry ═══
        add("recipe.trd.hydrogen_peroxide", "Hydrogen Peroxide");
        add("recipe.trd.sulfuric_acid", "Sulfuric Acid");
        add("recipe.trd.obsidian", "Obsidian");


        // ═══ GUI: Electric Furnace ═══
        add("gui.trd.electric_furnace.energy_tooltip", "%s / %s JE");
        add("gui.trd.electric_furnace.progress_tooltip", "§6Remaining: §f%s sec");

        // ═══ GUI: Fluid Barrel / Fuel Tank (shared) ═══
        add("gui.trd.fluid_barrel.empty", "Empty");
        add("gui.trd.fluid_barrel.amount", "%s / %s mB");
        add("gui.trd.fluid_barrel.mode.title", "Mode:");
        add("gui.trd.fluid_barrel.mode.both", "§aInput / Output (Both)");
        add("gui.trd.fluid_barrel.mode.input", "§bInput Only");
        add("gui.trd.fluid_barrel.mode.output", "§6Output Only");
        add("gui.trd.fluid_barrel.mode.disabled", "§cDisabled");
        add("gui.trd.fluid_barrel.mode.unknown", "Unknown");

        // ═══ GUI: Fluid Identifier ═══
        add("gui.trd.fluid_identifier.title", "Fluid Identifier");
        add("gui.trd.fluid_identifier.unknown", "Unknown");

        // ═══ GUI: Heater ═══
        add("hud.trd.chem_heater.title", "Chemical Heater");
        add("hud.trd.chem_heater.mode", "Mode");
        add("hud.trd.chem_heater.mode.off", "Off");
        add("hud.trd.chem_heater.consumption", "Consumption");
        add("hud.trd.chem_heater.charge", "Charge");
        
        add("gui.trd.heater.fuel_tiers_title", "§6§lFuel Tiers:");
        add("gui.trd.heater.fuel_tier_format", "§8Tier %s: §f%s°C, §f%s§7s.");
        add("gui.trd.heater.temperature_format", "%s / %s °C");
        add("gui.trd.heater.burn_time_format", "§6Remaining: §f%s§7/§f%s sec");
        add("gui.trd.heater.stopped", "§7Stopped");

        // ═══ GUI: Machine Battery ═══
        add("gui.trd.battery.panel.out", "OUT: %s JE/S");
        add("gui.trd.battery.panel.in", "IN: %s JE/S");
        add("gui.trd.battery.tooltip.discharge_speed", "§cDischarge Speed: %s JE/t");
        add("gui.trd.battery.tooltip.charge_speed", "§aCharge Speed: %s JE/t");
        add("gui.trd.battery.tooltip.speed_per_second", "(%s JE/s)");

        // ═══ GUI: Small Smelter ═══
        add("gui.trd.small_smelter.fuel_tiers_title", "§6§lFuel Tiers:");
        add("gui.trd.small_smelter.fuel_tier.0", "§8Tier 0: §f1°C, §f6.25§7s.");
        add("gui.trd.small_smelter.fuel_tier.1", "§8Tier 1: §f2°C, §f12.5§7s.");
        add("gui.trd.small_smelter.fuel_tier.2", "§8Tier 2: §f3°C, §f25§7s.");
        add("gui.trd.small_smelter.fuel_tier.3", "§8Tier 3: §f4°C, §f40§7s.");
        add("gui.trd.small_smelter.fuel_tier.4", "§8Tier 4: §f6°C, §f60§7s.");
        add("gui.trd.small_smelter.fuel_tier.5", "§8Tier 5: §f8°C, §f120§7s.");
        add("gui.trd.small_smelter.temperature_format", "%s / %s °C");
        add("gui.trd.small_smelter.burn_time_format", "§6Remaining: §f%s§7/§f%s sec");
        add("gui.trd.small_smelter.stopped", "§7Stopped");
        add("gui.trd.small_smelter.progress.temperature_format", "Temperature: %d/%d °C");
        add("gui.trd.small_smelter.progress.remaining", "Remaining: %ss");
        add("gui.trd.small_smelter.metal_tank.title", "§6§lMolten Metals:");
        add("gui.trd.small_smelter.metal_tank.empty", "§7Empty");
        add("gui.trd.small_smelter.metal_tank.exact_format", "%s: %s units");
        add("gui.trd.small_smelter.metal_tank.block_abbr", "bl");
        add("gui.trd.small_smelter.metal_tank.ingot_abbr", "ing");
        add("gui.trd.small_smelter.metal_tank.nugget_abbr", "nug");
        add("gui.trd.small_smelter.metal_tank.total_exact", "§7Total: §f%s§7 units / §f%s§7 units");
        add("gui.trd.small_smelter.metal_tank.shift_hide", "§8[Shift] hide exact value");
        add("gui.trd.small_smelter.metal_tank.shift_show", "§8[Shift] exact value");

        // ═══ GUI: Smelter ═══
        add("gui.trd.smelter.temperature_format", "%d / %d °C");
        add("gui.trd.smelter.progress.temperature_format", "Temperature: %d/%d °C");
        add("gui.trd.smelter.progress.remaining", "Remaining: %ss");
        add("gui.trd.smelter.metal_tank.title", "§6§lMolten Metals:");
        add("gui.trd.smelter.metal_tank.empty", "§7Empty");
        add("gui.trd.smelter.metal_tank.block_abbr", "blocks");
        add("gui.trd.smelter.metal_tank.ingot_abbr", "ingots");
        add("gui.trd.smelter.metal_tank.nugget_abbr", "nuggets");
        add("gui.trd.smelter.metal_tank.total_exact", "§7Total: §f%d§7 units / §f%d§7 units");
        add("gui.trd.smelter.metal_tank.total_converted", "§7Total: §f%dbl, %ding, %dnug §8/ %d blocks");
        add("gui.trd.smelter.metal_tank.shift_hide", "§8[Shift] hide exact value");
        add("gui.trd.smelter.metal_tank.shift_show", "§8[Shift] exact value");

        // ═══ GRENADE TOOLTIPS ═══

// Global charge hint for all chargable grenades
        add("tooltip.trd.grenade.charge_hint", "§8Hold RMB to adjust throw impulse");

// Standard grenades (bouncing)
        add("tooltip.trd.grenade.common.line1", "§7Hand anti-personnel grenade");
        add("tooltip.trd.grenade.standard.line2", "§8Type: §fFragmentation §8| Bounces: §f3 §8| Radius: §f3.5 §8| Damage: §f20");
        add("tooltip.trd.grenade.he.line2", "§8Type: §fHigh Explosive §8| Bounces: §f3 §8| Radius: §f7.0 §8| Damage: §f40");
        add("tooltip.trd.grenade.fire.line2", "§8Type: §cIncendiary §8| Bounces: §f3 §8| Radius: §f3.0 §8| Damage: §f30");
        add("tooltip.trd.grenade.slime.line2", "§8Type: §aSticky §8| Bounces: §f4 §8| Radius: §f3.5 §8| Damage: §f30 §8[Sticks to targets]");
        add("tooltip.trd.grenade.smart.line2", "§8Type: §eSmart §8| Bounces: §f3 §8| Radius: §f3.5/7.0 §8| Damage: §f20/40 §8[Detonates on contact]");
        add("tooltip.trd.grenade.default.line2", "§8Standard fragmentation grenade");

// Impact grenades (inertial fuze)
        add("tooltip.trd.grenade_if.common.line1", "§7Impact grenade with inertial fuze");
        add("tooltip.trd.grenade_if.standard.line2", "§8Type: §fFragmentation §8| Radius: §f5.0 §8| Damage: §f45 §8| Delay: §f4s");
        add("tooltip.trd.grenade_if.he.line2", "§8Type: §fHigh Explosive §8| Radius: §f8.0 §8| Damage: §f80 §8| Delay: §f4s");
        add("tooltip.trd.grenade_if.slime.line2", "§8Type: §aSticky §8| Radius: §f6.0 §8| Damage: §f60 §8| Delay: §f4s §8[Sticks to targets]");
        add("tooltip.trd.grenade_if.fire.line2", "§8Type: §cIncendiary §8| Radius: §f6.0 §8| Damage: §f60 §8| Delay: §f4s");
        add("tooltip.trd.grenade_if.default.line2", "§8Impact fragmentation grenade");

// Gravity grenade
        add("tooltip.trd.gravity_grenade.line1", "§d§lEXPERIMENTAL §7gravity weapon");
        add("tooltip.trd.gravity_grenade.line2", "§8Creates a gravity vortex, then scatters targets");

// Nuclear (hydrogen) grenade
        add("tooltip.trd.grenade_nuc.line1", "§4§lTACTICAL HYDROGEN CHARGE");
        add("tooltip.trd.grenade_nuc.line2", "§cRadius 25 §8| Damage 200 §8| Delay 7s");
        add("tooltip.trd.grenade_nuc.line3", "§8Penetrates cover. Use with extreme caution.");

        // ═══ BATTERY TOOLTIPS (ModBatteryItem) ═══
        add("tooltip.trd.battery.stored", "§7Charge:");
        add("tooltip.trd.battery.transfer_rate", "§aInput: §f%s JE/t");
        add("tooltip.trd.battery.discharge_rate", "§cOutput: §f%s JE/t");




        // ═══ GUI: Turret (shared) ═══
        add("gui.trd.turret.boot", "SYSTEM BOOT%s");
        add("gui.trd.turret.status.online", "SYSTEM ONLINE");
        add("gui.trd.turret.status.repairing", "REPAIRING: %s%%");
        add("gui.trd.turret.status.charging", "CHARGING...");
        add("gui.trd.turret.status.standby", "STANDBY MODE");
        add("gui.trd.turret.menu.chip_control", "CHIP CONTROL");
        add("gui.trd.turret.menu.attack_mode", "ATTACK MODE");
        add("gui.trd.turret.menu.stats", "TURRET STATS");
        add("gui.trd.turret.target.hostiles", "HOSTILES");
        add("gui.trd.turret.target.neutrals", "NEUTRALS");
        add("gui.trd.turret.target.players", "PLAYERS");
        add("gui.trd.turret.toggle.on", "[V]");
        add("gui.trd.turret.toggle.off", "[X]");
        add("gui.trd.turret.stats.kills", "KILLS: %s");
        add("gui.trd.turret.stats.time", "TIME: %dh %dm");
        add("gui.trd.turret.stats.owner", "OWNER: [DATA]");
        add("gui.trd.turret.chip.empty", "EMPTY LIST");
        add("gui.trd.turret.chip.format", "%s/%s %s");
        add("gui.trd.turret.result.success", "SUCCESS");
        add("gui.trd.turret.result.error", "ERROR 404");
        add("gui.trd.turret.energy_tooltip", "%s / %s JE");

        // Light Turret
        add("gui.trd.turret.status.respawn", "RESPAWN: %ss");

        // Trombone
        add("gui.trd.turret.status.reloading", "RELOADING: %ss");
        add("gui.trd.turret.status.no_missiles", "NO MISSILES");
        add("gui.trd.turret.menu.missiles", "MISSILES");
        add("gui.trd.turret.missiles.none", "NO MISSILES!");
        add("gui.trd.turret.missiles.standard", "STD: %s");
        add("gui.trd.turret.missiles.he", "HE: %s");
        add("gui.trd.turret.missiles.fire", "FIRE: %s");
        add("gui.trd.turret.missiles.total", "TTL: %s");

        // Battery
        add("gui.trd.battery.priority.0", "Priority: Low");
        add("gui.trd.battery.priority.0.desc", "Lowest priority. Will be drained first and filled last.");
        add("gui.trd.battery.priority.1", "Priority: Normal");
        add("gui.trd.battery.priority.1.desc", "Standard priority for energy transfer.");
        add("gui.trd.battery.priority.2", "Priority: High");
        add("gui.trd.battery.priority.2.desc", "Highest priority. Will be filled first and drained last.");
        add("gui.trd.battery.priority.recommended", "(Recommended)");

        add("gui.trd.battery.mode.both", "Mode: Input & Output");
        add("gui.trd.battery.mode.both.desc", "All energy operations are allowed.");
        add("gui.trd.battery.mode.input", "Mode: Input Only");
        add("gui.trd.battery.mode.input.desc", "Only receiving energy is allowed.");
        add("gui.trd.battery.mode.output", "Mode: Output Only");
        add("gui.trd.battery.mode.output.desc", "Only sending energy is allowed.");
        add("gui.trd.battery.mode.locked", "Mode: Locked");
        add("gui.trd.battery.mode.locked.desc", "All energy operations are disabled.");

        // ═══ HUD: Temperature ═══
        add("hud.trd.temperature.format", "%.0f / %.0f °C");
        add("hud.trd.temperature.heating", "§6● §fHeating");
        add("hud.trd.temperature.smelting", "§6● §fSmelting");

        // ═══ HUD: Low Pressure Steam Condenser ═══
        add("hud.trd.condenser.steam_name", "L.P. Steam");
        add("hud.trd.condenser.water_name", "Water");
        add("hud.trd.condenser.arrow_in", "§a-> ");
        add("hud.trd.condenser.arrow_out", "§c<- ");
        add("hud.trd.condenser.amount", "§7%s/%s mB");
        add("hud.trd.condenser.status.no_water", "§cRequires waterlogging!");
        add("hud.trd.condenser.status.cooling", "§7Cooling: §b%.2fx");

        // ═══ HUD: Motor Electro ═══
        add("hud.trd.motor.title", "§e⚡ Motor §7[%s]");
        add("hud.trd.motor.status.on", "§aON");
        add("hud.trd.motor.status.off", "§cOFF");
        add("hud.trd.motor.speed", "§7Speed:    §f%s RPM");
        add("hud.trd.motor.torque", "§7Torque:      §f%s Nm");
        add("hud.trd.motor.consumption", "§7Consumption: §f%s JE/s");
        add("hud.trd.motor.charge", "§7Charge: %s%s§7/%s JE");

        // ═══ HUD: Steel Storage ═══
        add("hud.trd.storage.header", "%s/%s slots");
        add("hud.trd.storage.empty", "Empty");
        add("hud.trd.storage.item", "• %s x%s");
        add("hud.trd.storage.more", "... and %s more");

        // ═══ HUD: Tachometer ═══
        add("hud.trd.tachometer.no_shaft", "⚠ No Shaft Inserted");
        add("hud.trd.tachometer.title", "▶ Network Analyzer");
        add("hud.trd.tachometer.speed", "Speed: %s RPM");
        add("hud.trd.tachometer.torque", "Torque: %s / %s Nm");
        add("hud.trd.tachometer.inertia", "Inertia: %.2f");
        add("hud.trd.tachometer.stress", "Stress: %.1f%%");

        // ═══ HUD: Boiler ═══
        add("hud.trd.boiler.water", "Water");
        add("hud.trd.boiler.steam", "Steam");
        add("hud.trd.boiler.arrow_in", "§a-> §7");
        add("hud.trd.boiler.arrow_out", "§c<- §7");
        add("hud.trd.boiler.amount_suffix", " mB");
        add("hud.trd.boiler.temperature", "Temperature: %.1f °C");

        // ═══ HUD: Millstone ═══
        add("hud.trd.millstone.result", "✓ %s");
        add("hud.trd.millstone.result_extra", " + %s");
        add("hud.trd.millstone.take", "RMB to collect");
        add("hud.trd.millstone.progress", "%d/%d turns");
        add("hud.trd.millstone.remaining", "Remaining: %s");
        add("hud.trd.millstone.grind", "RMB to grind");
        add("hud.trd.millstone.empty", "Millstone is empty");
        add("hud.trd.millstone.insert", "Place mineral");

        // ═══ HUD: Steam Engine ═══
        add("hud.trd.engine.steam", "Steam");
        add("hud.trd.engine.lp_steam", "L.P. Steam");
        add("hud.trd.engine.arrow_in", "§a-> §7");
        add("hud.trd.engine.arrow_out", "§c<- §7");
        add("hud.trd.engine.amount_suffix", " mB");

        // ═══ HUD: Stator ═══

// English
        add("hud.trd.stator.coils_label", "Coils: ");
        add("hud.trd.stator.buffer_label", "Buffer: ");
        add("hud.trd.stator.load_label", "Load: ");
        add("hud.trd.stator.production_label", "Production: ");

        // ═══ GUI: Casting Pot ═══
        add("gui.trd.casting_pot.cannot_insert", "§cCannot insert: pot is occupied or has no mold");
        add("gui.trd.casting_pot.slag_hot", "§cSlag is hot! Use a poker.");
        add("gui.trd.casting_pot.too_hot", "§cToo hot! %d°C (%d%%) Use a poker.");
        add("gui.trd.casting_pot.too_hot_simple", "§cToo hot! (%d%%) Use a poker.");
        add("gui.trd.casting_pot.cannot_remove_mold", "§cCannot remove mold: contains metal or item");

        // ═══ GUI: Machine Battery ═══
        add("gui.trd.machine_battery.cell_extracted", "§eCell extracted from slot %s");
        add("gui.trd.machine_battery.cell_inserted", "§aCell inserted into slot %s");
        add("gui.trd.machine_battery.slot_occupied", "§cSlot %s is already occupied!");

        // ═══ Tooltip: Machine Battery ═══
        add("tooltip.trd.machine_battery.frame", "§7Energy storage frame");
        add("tooltip.trd.machine_battery.energy", "§eEnergy: %s JE");
        add("tooltip.trd.machine_battery.insert_cells", "§8Insert energy cells to increase parameters");

        // ═══ Message: Fluid Barrel ═══
        add("message.trd.fluid_barrel.filter_reset", "§eBarrel filter reset (Closed)");
        add("message.trd.fluid_barrel.filter_set", "§aBarrel Filter: §f%s");

        // ═══ Tooltip: Fluid Barrel ═══
        add("tooltip.trd.fluid_barrel.capacity", "Capacity: ");
        add("tooltip.trd.fluid_barrel.melting_point", "Melting point: ");
        add("tooltip.trd.fluid_barrel.corrosion_resistance", "Corrosion resistance: ");
        add("tooltip.trd.fluid_barrel.leaking", "⚠ Leaking: ");
        add("tooltip.trd.fluid_barrel.leak_rate_unit", "mB/sec");
        add("tooltip.trd.fluid_barrel.fluid_amount", "%s: %s/%s mB");
        add("tooltip.trd.fluid_barrel.empty", "§bFluid: §7Empty");
        add("tooltip.trd.fluid_barrel.filter", "§aFilter: §f%s");
        add("tooltip.trd.fluid_barrel.filter_closed", "§aFilter: §cClosed");

        // ═══ Tooltip: Fluid Pipe ═══
        add("tooltip.trd.fluid_pipe.max_temp", "Max temperature: ");
        add("tooltip.trd.fluid_pipe.max_corrosion", "Max corrosion: ");

        // ═══ Message: Fluid Pipe ═══
        add("message.trd.fluid_pipe.filter_line_reset", "§aPipe line filter reset. §7(%s pipes)");
        add("message.trd.fluid_pipe.filter_line_set", "§aPipe line filter set: §f%s §7(%s pipes)");
        add("message.trd.fluid_pipe.filter_reset", "§eFilter reset (Pipe accepts all)");
        add("message.trd.fluid_pipe.filter_set", "§aFilter: §f%s");

        // ═══ Tooltip: Low Pressure Steam Condenser ═══
        add("tooltip.trd.condenser.steam_in", "⬇ L.P. Steam (input): ");
        add("tooltip.trd.condenser.water_out", "⬆ Water (output): ");
        add("tooltip.trd.condenser.cooling", "❄ Cooling: ");

        // ═══ Message: Valve ═══
        add("tooltip.trd.explosion_resistance", "Blast Resistance: %s");
        add("message.trd.valve.filter_reset", "§eValve filter reset");
        add("message.trd.valve.filter_set", "§aValve filter: §f%s");

        // ═══ Tooltip: Steel Storage ═══
        add("tooltip.trd.steel_storage.empty", "Empty");
        add("tooltip.trd.steel_storage.contains", "Contains: %s/%s");
        add("tooltip.trd.steel_storage.and_more", "... and %s more");
        add("tooltip.trd.steel_storage.item", "• %s x%s");

        // ═══ Tooltip & Message: Fuel Tank (shared) ═══
        add("message.trd.fuel_tank.filter_reset", "§eFilter reset (tank closed)");
        add("message.trd.fuel_tank.filter_set", "§aFilter set: §f%s");
        add("tooltip.trd.fuel_tank.capacity", "Capacity: %s mB");
        add("tooltip.trd.fuel_tank.resistant", "Resistant to corrosion and heat");
        add("tooltip.trd.fuel_tank.fluid_amount", "%s: %s/%s mB");
        add("tooltip.trd.fuel_tank.empty", "§bFluid: §7Empty");
        add("tooltip.trd.fuel_tank.type", "§aType: §f%s");
        add("tooltip.trd.fuel_tank.type_not_set", "§aType: §cnot set");

        // ═══ Tooltip: Conglomerate ═══
        add("tooltip.trd.conglomerate.empty", "§7Empty chunk");
        add("tooltip.trd.conglomerate.contains_fractions", "§eContains fractions:");
        add("tooltip.trd.conglomerate.fraction", "%s: %d%%");
        add("tooltip.trd.conglomerate.ou", "§8OU: %d");
        add("tooltip.trd.conglomerate.vein_type", "§8Vein type: %s");

        // ═══ Tooltip: Energy Cell ═══
        add("tooltip.trd.energy_cell.energy_stored", "§eEnergy: %s / %s JE");
        add("tooltip.trd.energy_cell.empty", "§7Energy: Empty");
        add("tooltip.trd.energy_cell.capacity", "Capacity: %s JE");
        add("tooltip.trd.energy_cell.charge_speed", "Charge Speed: %s JE/t");
        add("tooltip.trd.energy_cell.discharge_speed", "Discharge Speed: %s JE/t");

        // ═══ Message: Wire Coil ═══
        add("message.trd.wire_coil.cancelled", "§eConnection cancelled.");
        add("message.trd.wire_coil.connector_full", "§cThis connector is already full!");
        add("message.trd.wire_coil.started", "§aConnection started... Click the second connector.");
        add("message.trd.wire_coil.self_connect", "§cCannot connect a connector to itself!");
        add("message.trd.wire_coil.first_destroyed", "§cThe first connector was destroyed or lost.");
        add("message.trd.wire_coil.first_full", "§cThe first connector is already full!");
        add("message.trd.wire_coil.second_full", "§cThe second connector is already full!");
        add("message.trd.wire_coil.already_connected", "§cThese connectors are already connected!");
        add("message.trd.wire_coil.too_far", "§cToo far! Maximum length: %s blocks.");
        add("message.trd.wire_coil.blocked", "§cPath blocked: %s");
        add("message.trd.wire_coil.success", "§bConnection successfully established!");

        // ═══ Message: Belt ═══
        add("message.trd.belt.pulleys_only", "§cBelt can only be stretched on pulleys!");
        add("message.trd.belt.already_connected", "§cThis pulley is already connected by a belt!");
        add("message.trd.belt.first_selected", "§aFirst pulley selected. Click the second one.");
        add("message.trd.belt.cancelled", "§eLinking cancelled.");
        add("message.trd.belt.too_far", "§cToo far! (Max. %s blocks)");
        add("message.trd.belt.first_destroyed", "§cThe first pulley was destroyed or removed.");
        add("message.trd.belt.axis_mismatch", "§cPulley axes are not parallel!");
        add("message.trd.belt.not_coplanar", "§cPulleys must lie in the same plane!");
        add("message.trd.belt.pulley_occupied", "§cOne of the pulleys is already occupied!");
        add("message.trd.belt.success", "§aBelt successfully stretched!");

        // ═══ Tooltip: Protector ═══
        add("tooltip.trd.protector.melting_point", "  +%s°C to melting point");
        add("tooltip.trd.protector.corrosion", "  +%s corrosion resistance");
        add("tooltip.trd.protector.install", "§7Install in barrel");

        // ═══ Message: Poker ═══
        add("message.trd.poker.pot_empty", "§7Pot is empty or contains liquid metal");
        add("message.trd.poker.hot_item_extracted", "§6Extracted hot item! %d°C");
        add("message.trd.poker.smelter_empty", "§7No metal in smelter");
        add("message.trd.poker.slag_dumped", "§6Dumped %d slag units");

        // ═══ Tooltip: Infinite Fluid Barrel ═══
        add("tooltip.trd.infinite_barrel.slot", "§8Place in emptying slot");
        add("tooltip.trd.infinite_barrel.tank", "§8of configured tank to");
        add("tooltip.trd.infinite_barrel.fill", "§8fill it infinitely.");
        add("tooltip.trd.infinite_barrel.source", "§dInfinite source");

        // ═══ Tooltip: Fluid Identifier ═══
        add("tooltip.trd.fluid_identifier.fluid", "Fluid: ");

        // ═══ Message: Beam Placer ═══
        add("message.trd.beam_placer.same_point", "§cPoints cannot match! Link reset.");
        add("message.trd.beam_placer.not_enough", "§cNot enough beams! Required: §e%s");
        add("message.trd.beam_placer.placed", "§aBeam placed! Spent: %s");
        add("message.trd.beam_placer.first_set", "§aFirst point (center) anchored.");

        // ═══ Message: Cast Pickaxe ═══
        add("message.trd.cast_pickaxe.cooldown", "§cCooldown...");
        add("item.trd.cast_pickaxe.warning.twohanded", "§cRequires both hands!");

        add("tooltip.trd.creative_battery_desc","Provides an infinite amount of power");
        add("tooltip.trd.creative_battery_flavor","Pure Zamaz!");

        // Sequoia
        add(ModItems.PIG_TURRET_PLACER.get(), "Oink-o-Turret");
        add(ModBlocks.SEQUOIA_BARK.get(), "Sequoia Bark");
        add(ModBlocks.SEQUOIA_HEARTWOOD.get(), "Sequoia Heartwood");
        add(ModBlocks.SEQUOIA_PLANKS.get(), "Sequoia Planks");
        add(ModBlocks.SEQUOIA_ROOTS.get(), "Sequoia Roots");
        add(ModBlocks.SEQUOIA_ROOTS_MOSSY.get(), "Mossy Sequoia Roots");
        add(ModBlocks.SEQUOIA_BARK_DARK.get(), "Dark Sequoia Bark");
        add(ModBlocks.SEQUOIA_BARK_MOSSY.get(), "Mossy Sequoia Bark");
        add(ModBlocks.SEQUOIA_BARK_LIGHT.get(), "Light Sequoia Bark");
        add(ModBlocks.SEQUOIA_DOOR.get(), "Sequoia Door");
        add(ModBlocks.SEQUOIA_TRAPDOOR.get(), "Sequoia Trapdoor");
        add(ModBlocks.SEQUOIA_BIOME_MOSS.get(), "Dark Moss");
        add(ModBlocks.SEQUOIA_LEAVES.get(), "Sequoia Leaves");
        add(ModBlocks.SEQUOIA_SLAB.get(), "Sequoia Slab");
        add(ModBlocks.SEQUOIA_STAIRS.get(), "Sequoia Stairs");


        // Smelting & Casting
        add(ModBlocks.SMALL_SMELTER.get(), "Small Smelter");
        add(ModBlocks.SMELTER.get(), "Smelter");
        add(ModBlocks.CASTING_DESCENT.get(), "Casting Trough");
        add(ModBlocks.CASTING_POT.get(), "Casting Pot");
        add(ModItems.HEATER_ITEM.get(), "Heater");
        add(ModItems.LIQUID_METAL.get(), "Liquid Metal");

        // Electronics & Energy
        add(ModItems.ENERGY_CELL_BASIC.get(), "Energy Cell");
        add(ModItems.CREATIVE_BATTERY.get(), "Creative Battery");
        add(ModItems.BATTERY.get(), "Battery");
        add(ModItems.BATTERY_ADVANCED.get(), "Advanced Battery");
        add(ModItems.BATTERY_LITHIUM.get(), "Lithium Battery");
        add(ModItems.BATTERY_TRIXITE.get(), "Trixite Battery");
        add(ModBlocks.MACHINE_BATTERY.get(), "Modular Energy Storage");
        add(ModBlocks.CONVERTER_BLOCK.get(), "Energy Converter");
        add(ModBlocks.WIRE_COATED.get(), "Coated Copper Wire");
        add(ModBlocks.PAINTABLE_WIRE.get(), "Paintable Wire");
        add(ModBlocks.MEDIUM_CONNECTOR.get(), "Medium Connector");
        add(ModBlocks.LARGE_CONNECTOR.get(), "Large Connector");
        add(ModBlocks.SWITCH.get(), "Switch");
        add(ModBlocks.VALVE.get(), "Valve");
        add(ModBlocks.TURRET_LIGHT_PLACER.get(), "Light Landing Turret \'Nagual\'");

        // Concrete Variants
        add(ModBlocks.CONCRETE.get(), "Concrete");
        add(ModBlocks.CONCRETE_SLAB.get(), "Concrete Slab");
        add(ModBlocks.CONCRETE_STAIRS.get(), "Concrete Stairs");
        add(ModBlocks.CONCRETE_RED.get(), "Red Concrete");
        add(ModBlocks.CONCRETE_RED_SLAB.get(), "Red Concrete Slab");
        add(ModBlocks.CONCRETE_RED_STAIRS.get(), "Red Concrete Stairs");
        add(ModBlocks.CONCRETE_BLUE.get(), "Blue Concrete");
        add(ModBlocks.CONCRETE_BLUE_SLAB.get(), "Blue Concrete Slab");
        add(ModBlocks.CONCRETE_BLUE_STAIRS.get(), "Blue Concrete Stairs");
        add(ModBlocks.CONCRETE_GREEN.get(), "Green Concrete");
        add(ModBlocks.CONCRETE_GREEN_SLAB.get(), "Green Concrete Slab");
        add(ModBlocks.CONCRETE_GREEN_STAIRS.get(), "Green Concrete Stairs");
        add(ModBlocks.CONCRETE_HAZARD_NEW.get(), "New Hazard Concrete");
        add(ModBlocks.CONCRETE_HAZARD_NEW_SLAB.get(), "New Hazard Concrete Slab");
        add(ModBlocks.CONCRETE_HAZARD_NEW_STAIRS.get(), "New Hazard Concrete Stairs");
        add(ModBlocks.CONCRETE_HAZARD_OLD.get(), "Old Hazard Concrete");
        add(ModBlocks.CONCRETE_HAZARD_OLD_SLAB.get(), "Old Hazard Concrete Slab");
        add(ModBlocks.CONCRETE_HAZARD_OLD_STAIRS.get(), "Old Hazard Concrete Stairs");
        add(ModBlocks.CONCRETE_TILE.get(), "Concrete Tile");
        add(ModBlocks.CONCRETE_TILE_SLAB.get(), "Concrete Tile Slab");
        add(ModBlocks.CONCRETE_TILE_STAIRS.get(), "Concrete Tile Stairs");
        add(ModBlocks.CONCRETE_TILE_ALT.get(), "Faceted Concrete Tile");
        add(ModBlocks.CONCRETE_TILE_ALT_SLAB.get(), "Faceted Concrete Tile Slab");
        add(ModBlocks.CONCRETE_TILE_ALT_STAIRS.get(), "Faceted Concrete Tile Stairs");
        add(ModBlocks.CONCRETE_TILE_ALT_BLUE.get(), "Painted Faceted Concrete Tile");
        add(ModBlocks.CONCRETE_TILE_ALT_BLUE_SLAB.get(), "Painted Faceted Concrete Tile Slab");
        add(ModBlocks.CONCRETE_TILE_ALT_BLUE_STAIRS.get(), "Painted Faceted Concrete Tile Stairs");
        add(ModBlocks.CONCRETE_STRIPPED.get(), "Light Textured Concrete");
        add(ModBlocks.CONCRETE_STRIPPED_SLAB.get(), "Light Textured Concrete Slab");
        add(ModBlocks.CONCRETE_STRIPPED_STAIRS.get(), "Light Textured Concrete Stairs");
        add(ModBlocks.CONCRETE_REINFORCED.get(), "Gray Textured Concrete");
        add(ModBlocks.CONCRETE_REINFORCED_SLAB.get(), "Gray Textured Concrete Slab");
        add(ModBlocks.CONCRETE_REINFORCED_STAIRS.get(), "Gray Textured Concrete Stairs");
        add(ModBlocks.CONCRETE_REINFORCED_HEAVY.get(), "Dark Textured Concrete");
        add(ModBlocks.CONCRETE_REINFORCED_HEAVY_SLAB.get(), "Dark Textured Concrete Slab");
        add(ModBlocks.CONCRETE_REINFORCED_HEAVY_STAIRS.get(), "Dark Textured Concrete Stairs");
        add(ModBlocks.CONCRETE_NET.get(), "Reinforced Concrete");

        // Bricks
        add(ModBlocks.FIREBRICK_BLOCK.get(), "Firebrick Block");
        add(ModBlocks.FIREBRICK_SLAB.get(), "Firebrick Slab");
        add(ModBlocks.FIREBRICK_STAIRS.get(), "Firebrick Stairs");
        add(ModBlocks.REINFORCEDBRICK_BLOCK.get(), "Dolomite Brick Block");
        add(ModBlocks.REINFORCEDBRICK_SLAB.get(), "Dolomite Brick Slab");
        add(ModBlocks.REINFORCEDBRICK_STAIRS.get(), "Dolomite Brick Stairs");
        add(ModItems.FIREBRICK.get(), "Firebrick");

        // Decorative Blocks
        add(ModBlocks.CRATE.get(), "Crate");
        add(ModBlocks.CRATE_AMMO.get(), "Ammo Crate");
        add(ModBlocks.BEAM_BLOCK.get(), "Beam Block");
        add(ModBlocks.STEEL_PROPS.get(), "Steel Props");
        add(ModBlocks.DIRT_ROUGH.get(), "Rough Dirt");
        add(ModBlocks.ROUND_LAMP.get(), "Round Lamp");
        add(ModBlocks.MORY_BLOCK.get(), "Mory Block");
        add(ModBlocks.DOLOMITE_TILE.get(), "Dolomite Tile");
        add(ModBlocks.TILE_LIGHT.get(), "Light Tile");
        add(ModBlocks.SULFUR_TILE.get(), "Sulfur Tile");
        add(ModBlocks.SULFUR_BRICKS.get(), "Sulfur Bricks");
        add(ModBlocks.NECROSIS_TEST.get(), "Necrosis Test Block");
        add(ModBlocks.NECROSIS_TEST2.get(), "Necrosis Test Block 2");
        add(ModBlocks.NECROSIS_TEST3.get(), "Necrosis Test Block 3");
        add(ModBlocks.NECROSIS_TEST4.get(), "Necrosis Test Block 4");
        add(ModBlocks.NECROSIS_PORTAL.get(), "Necrosis Portal");
        add(ModBlocks.WASTE_LOG.get(), "Waste Log");

        // Kinetic & Shafts
        add(ModBlocks.HAND_CRANK_BLOCK.get(), "Hand Crank");
        add(ModBlocks.SHAFT_LIGHT_IRON.get(), "Light Iron Shaft");
        add(ModBlocks.SHAFT_MEDIUM_IRON.get(), "Medium Iron Shaft");
        add(ModBlocks.SHAFT_HEAVY_IRON.get(), "Heavy Iron Shaft");
        add(ModBlocks.SHAFT_LIGHT_DURALUMIN.get(), "Light Duralumin Shaft");
        add(ModBlocks.SHAFT_MEDIUM_DURALUMIN.get(), "Medium Duralumin Shaft");
        add(ModBlocks.SHAFT_HEAVY_DURALUMIN.get(), "Heavy Duralumin Shaft");
        add(ModBlocks.SHAFT_LIGHT_STEEL.get(), "Light Steel Shaft");
        add(ModBlocks.SHAFT_MEDIUM_STEEL.get(), "Medium Steel Shaft");
        add(ModBlocks.SHAFT_HEAVY_STEEL.get(), "Heavy Steel Shaft");
        add(ModBlocks.SHAFT_LIGHT_TITANIUM.get(), "Light Titanium Shaft");
        add(ModBlocks.SHAFT_MEDIUM_TITANIUM.get(), "Medium Titanium Shaft");
        add(ModBlocks.SHAFT_HEAVY_TITANIUM.get(), "Heavy Titanium Shaft");
        add(ModBlocks.SHAFT_LIGHT_TUNGSTEN_CARBIDE.get(), "Light Tungsten Carbide Shaft");
        add(ModBlocks.SHAFT_MEDIUM_TUNGSTEN_CARBIDE.get(), "Medium Tungsten Carbide Shaft");
        add(ModBlocks.SHAFT_HEAVY_TUNGSTEN_CARBIDE.get(), "Heavy Tungsten Carbide Shaft");
        add(ModItems.BEVEL_GEAR.get(), "Bevel Gear");
        add(ModItems.GEAR1_STEEL.get(), "Small Steel Gear");
        add(ModItems.GEAR2_STEEL.get(), "Medium Steel Gear");
        add(ModItems.PULLEY.get(), "Pulley");
        add(ModItems.FLYWHEEL_LIGHT.get(), "Light Flywheel");
        add(ModItems.COPPER_ROTOR.get(), "Copper Rotor");
        add(ModBlocks.BEARING_BLOCK.get(), "Bearing");
        add(ModBlocks.MOTOR_ELECTRO.get(), "Electric Motor");
        add(ModBlocks.TACHOMETER.get(), "Tachometer");
        add(ModBlocks.CLUTCH.get(), "Clutch");
        add(ModItems.STEAM_ENGINE_ITEM.get(), "Steam Engine");
        add(ModBlocks.STATOR_BLOCK.get(), "Stator");

        // Barrels, Tanks & Fluids
        add(ModItems.CORRUPTED_BARREL_ITEM.get(), "Corrupted Barrel");
        add(ModItems.LEAKING_BARREL_ITEM.get(), "Leaking Barrel");
        add(ModItems.IRON_BARREL_ITEM.get(), "Iron Barrel");
        add(ModItems.STEEL_BARREL_ITEM.get(), "Steel Barrel");
        add(ModItems.LEAD_BARREL_ITEM.get(), "Lead Barrel");
        add(ModItems.INFINITE_FLUID_BARREL.get(), "Infinite Fluid Source");
        add(ModBlocks.FUEL_TANK_SMALL.get(), "Small Fuel Tank");
        add(ModBlocks.FUEL_TANK_BIG.get(), "Big Fuel Tank");
        add(ModBlocks.LOW_PRESSURE_STEAM_CONDENSER.get(), "Low Pressure Steam Condenser");

        // Conveyors & Storage
        add(ModBlocks.CONVEYOR_VSTAVSHIK.get(), "Conveyor Inserter");
        add(ModBlocks.CONVEYOR_IZVLEKATEL.get(), "Conveyor Extractor");
        add(ModBlocks.CONVEYOR.get(), "Conveyor");
        add(ModBlocks.STEEL_STORAGE.get(), "Steel Storage");

        // Weapons & Ammo
        add(ModItems.CAST_PICKAXE_IRON.get(), "Cast Iron Pickaxe");
        add(ModItems.CAST_PICKAXE_STEEL.get(), "Cast Steel Pickaxe");
        add(ModItems.GRENADIER_GOGGLES.get(), "Grenadier Goggles");
        add(ModBlocks.DET_MINER.get(), "Mining Charge");
        add(ModItems.DETONATOR.get(), "Detonator");
        add(ModItems.MULTI_DETONATOR.get(), "Multi-Detonator");
        add(ModItems.RANGE_DETONATOR.get(), "Long-Range Detonator");
        add(ModItems.TURRET_LIGHT_PORTATIVE_PLACER.get(), "Portable Light Turret");
        add(ModItems.MACHINEGUN.get(), "\'A.P. 17\'");
        add(ModItems.AMMO_TURRET.get(), "20mm Turret Round");
        add(ModItems.AMMO_TURRET_PIERCING.get(), "20mm Armor-Piercing Turret Round");
        add(ModItems.AMMO_TURRET_HOLLOW.get(), "20mm Hollow-Point Turret Round");
        add(ModItems.AMMO_TURRET_FIRE.get(), "20mm Incendiary Turret Round");
        add(ModItems.AMMO_TURRET_RADIO.get(), "20mm Turret Round with Radio Fuze");
        add(ModItems.MISSILE_100MM_HE.get(), "100mm HE Missile");
        add(ModItems.MISSILE_100MM_FIRE.get(), "100mm Incendiary Missile");

        // Resources & Materials
        add(ModItems.IRON_PLATE.get(), "Iron Plate");
        add(ModItems.TITANIUM_PLATE.get(), "Titanium Plate");
        add(ModItems.STEEL_PLATE.get(), "Steel Plate");
        add(ModItems.TUNGSTEN_PLATE.get(), "Tungsten Plate");
        add(ModItems.LEAD_PLATE.get(), "Lead Plate");
        add(ModItems.ALUMINUM_PLATE.get(), "Aluminum Plate");
        add(ModItems.INDUSTRIAL_COPPER_PLATE.get(), "Industrial Copper Plate");
        add(ModItems.GOLD_PLATE.get(), "Gold Plate");
        add(ModItems.CAST_PICKAXE_IRON_BASE.get(), "Cast Iron Pickaxe Base");
        add(ModItems.CAST_PICKAXE_STEEL_BASE.get(), "Cast Steel Pickaxe Base");
        add(ModItems.ROPE.get(), "Rope");
        add(ModItems.WOODEN_HANDLE.get(), "Wooden Handle");
        add(ModItems.FIRE_SMES.get(), "Fireproof Mixture");
        add(ModItems.DOLOMITE_SMES.get(), "Dolomite Mixture");
        add(ModItems.CONGLOMERATE_CHUNK.get(), "Conglomerate Chunk");
        add(ModItems.HARD_ROCK.get(), "Hard Rock");
        add(ModItems.DOLOMITE_CHUNK.get(), "Dolomite Chunk");
        add(ModItems.LIMESTONE_CHUNK.get(), "Limestone Chunk");
        add(ModItems.BAUXITE_CHUNK.get(), "Bauxite Chunk");
        add(ModItems.ASBESTOS.get(), "Asbestos");
        add(ModItems.CINNABAR.get(), "Cinnabar");
        add(ModItems.LIGNITE.get(), "Lignite");
        add(ModItems.FLUORITE.get(), "Fluorite");
        add(ModItems.SULFUR.get(), "Sulfur");
        add(ModItems.CONGLOMERATE_POWDER.get(), "Conglomerate Powder");
        add(ModItems.DOLOMITE_POWDER.get(), "Dolomite Powder");
        add(ModItems.LIMESTONE_POWDER.get(), "Limestone Powder");
        add(ModItems.BAUXITE_POWDER.get(), "Bauxite Powder");
        add(ModItems.FUEL_ASH.get(), "Fuel Ash");
        add(ModItems.TRASH.get(), "Trash");
        add(ModItems.SLAG.get(), "Slag");
        add(ModItems.BELT.get(), "Belt");
        add(ModItems.BEAM_PLACER.get(), "Beam Placer");
        add(ModItems.POKER.get(), "Poker");
        add(ModItems.SCREWDRIVER.get(), "Screwdriver");
        add(ModItems.CROWBAR.get(), "Crowbar");
        add(ModBlocks.LIGNITE_BLOCK.get(), "Lignite Block");

        // Ores & nature (updated)
        add(ModBlocks.ASBESOTS_ORE.get(), "Asbestos Ore");
        add(ModBlocks.LIGNITE_ORE.get(), "Lignite Ore");
        add(ModBlocks.CINNABAR_ORE.get(), "Cinnabar Ore");
        add(ModBlocks.CINNABAR_ORE_DEEPSLATE.get(), "Deepslate Cinnabar Ore");
        add(ModBlocks.FLUORITE_ORE.get(), "Fluorite Ore");
        add(ModBlocks.FLUORITE_ORE_DEEPSLATE.get(), "Deepslate Fluorite Ore");
        add(ModBlocks.SEQUESTRUM_ORE.get(), "Saltpeter Ore");

        add(ModBlocks.SEQUESTRUM_ORE_DEEPSLATE.get(), "Deepslate Sequestrum Ore");
        add(ModBlocks.SULFUR_ORE.get(), "Sulfur Ore");
        add(ModBlocks.SULFUR_ORE_DEEPSLATE.get(), "Deepslate Sulfur Ore");
        add(ModBlocks.CONGLOMERATE.get(), "Conglomerate");
        add(ModBlocks.DEPLETED_CONGLOMERATE.get(), "Depleted Conglomerate");
        add(ModBlocks.DOLOMITE.get(), "Unrefined Dolomite Deposit");
        add(ModBlocks.LIMESTONE.get(), "Unrefined Limestone Deposit");
        add(ModBlocks.SULFUR_CLUSTER.get(), "Unrefined Sulfur Deposit");
        add(ModBlocks.BAUXITE.get(), "Unrefined Bauxite Deposit");
        add(ModBlocks.MINERAL1.get(), "Sapphire-Bearing Cluster");
        add(ModBlocks.MINERAL3.get(), "Deep Sapphire-Bearing Cluster");
        add(ModBlocks.BASALT_ROUGH.get(), "Rough Basalt");

        // Spawn Eggs
        add(ModItems.DEPTH_WORM_SPAWN_EGG.get(), "Depth Worm Spawn Egg");
        add(ModItems.DEPTH_WORM_BRUTAL_SPAWN_EGG.get(), "Brutal Depth Worm Spawn Egg");
        add(ModItems.GRENADIER_ZOMBIE_SPAWN_EGG.get(), "Grenadier Zombie Spawn Egg");

        add(ModBlocks.STEEL_DOOR.get(), "Steel Door");
        add(ModBlocks.ARMORED_GLASS.get(), "Armored Glass");
        add(ModBlocks.DECO_BARREL.get(), "Leaking Barrel (decorative)");
        add(ModBlocks.ANTON_CHIGUR.get(), "Anton Chigur Block");
        add(ModBlocks.MINERAL_BLOCK2.get(), "Depth Sapphire Decorative Block");
        add(ModBlocks.MINERAL_TILE.get(), "Depth Sapphire Tile");
        add(ModBlocks.DECO_STEEL.get(), "Decorative Steel Block");
        add(ModBlocks.DECO_STEEL_DARK.get(), "Dark Decorative Steel Block");
        add(ModBlocks.DECO_STEEL_SMOG.get(), "Sooty Decorative Steel Block");
        add(ModBlocks.DECO_LEAD.get(), "Decorative Lead Block");
        add(ModBlocks.DECO_BEAM.get(), "Decorative Industrial Block");
        add(ModItems.WIRE_COIL.get(), "Copper Wire Spool");
        add(ModItems.COPPER_COIL.get(), "Stator Copper Coil");
        add(ModBlocks.CONNECTOR.get(), "Small Connector");
        add(ModBlocks.ELECTRO_FURNACE.get(), "Electric Furnace");
        add(ModItems.PROTECTOR_LEAD.get(), "Lead Internal Wall Protector");
        add(ModItems.PROTECTOR_STEEL.get(), "Steel Internal Wall Protector");
        add(ModItems.PROTECTOR_TUNGSTEN.get(), "Tungsten Internal Wall Protector");
        add(ModBlocks.DROBITEL.get(), "Ore Crusher");
        // Fluid pipes
        add(ModBlocks.BRONZE_FLUID_PIPE.get(), "Bronze Fluid Pipe");
        add(ModBlocks.STEEL_FLUID_PIPE.get(), "Steel Fluid Pipe");
        add(ModBlocks.LEAD_FLUID_PIPE.get(), "Lead Fluid Pipe");
        add(ModBlocks.TUNGSTEN_FLUID_PIPE.get(), "Tungsten Fluid Pipe");
        add(ModBlocks.PAINTABLE_PIPE.get(), "Paintable Fluid Pipe");

        // Machines
        add(ModItems.BOILER_ITEM.get(), "Copper Liquid Boiler");
        add(ModBlocks.WATER_PUMP_ITEM.get(), "Liquid Pump");

        // Casting molds
        add(ModItems.MOLD_INGOT.get(), "Ingot Casting Mold");
        add(ModItems.MOLD_PICKAXE.get(), "Pickaxe Casting Mold");
        add(ModItems.MOLD_EMPTY.get(), "Empty Casting Mold");
        add(ModItems.MOLD_NUGGET.get(), "Nugget Casting Mold");
        add(ModItems.MOLD_BLOCK.get(), "Block Casting Mold");
        add(ModItems.MOLD_PLATE.get(), "Plate Casting Mold");

        // Misc blocks & items
        add(ModBlocks.JERNOVA.get(), "Stone Millstone");
        add(ModItems.MORY_LAH.get(), "Inconceivably Suspicious Artifact Possessing the Power of a Thousand Suns");
        add(ModItems.GRENADE.get(), "Grenade");
        add(ModItems.GRENADEHE.get(), "High Explosive Grenade");
        add(ModItems.GRENADEFIRE.get(), "Incendiary Grenade");
        add(ModItems.GRENADESMART.get(), "Smart Grenade");
        add(ModItems.GRENADESLIME.get(), "Sticky Grenade");
        add(ModItems.GRENADE_IF.get(), "Impact Grenade");
        add(ModItems.GRENADE_IF_HE.get(), "HE Impact Grenade");
        add(ModItems.GRENADE_IF_SLIME.get(), "Sticky Impact Grenade");
        add(ModItems.GRENADE_IF_FIRE.get(), "Incendiary Impact Grenade");
        add(ModItems.GRENADE_NUC.get(), "Hydrogen-Cremating Grenade");
        add(ModItems.TURRET_CHIP.get(), "Turret Combat Chip");
        add(ModItems.GRAVITY_GRENADE.get(), "Gravi-Grenade");
        add(ModItems.MISSILE_100MM.get(), "100mm Missile (Small Charge)");
        add(ModBlocks.TROMBONE.get(), "Stationary Rocket Launcher 'Trombone'");
        add(ModItems.REINFORCEDBRICK.get(), "Dolomite Brick");
        add(ModItems.SEQUESTRUM.get(), "Saltpeter");


        // Necrosis
        add(ModBlocks.DEPTH_WORM_NEST.get(), "Depth Worm Hive Node");
        add(ModBlocks.HIVE_SOIL.get(), "Depth Worm Hive Flesh");
        add(ModBlocks.HIVE_ROOTS.get(), "Depth Worm Hive Nerve Endings");

        // Entities
        add("entity.trd.turret_light", "Light Turret");
        add("entity.trd.turret_light_linked", "Linked Light Turret");
        add("entity.trd.turret_bullet", "Turret Bullet");
        add("entity.trd.depth_worm", "Depth Worm");
        add("entity.trd.grenade_projectile", "Grenade");
        add("entity.trd.grenadehe_projectile", "HE Grenade");
        add("entity.trd.grenadefire_projectile", "Incendiary Grenade");
        add("entity.trd.grenadesmart_projectile", "Smart Grenade");
        add("entity.trd.grenadeslime_projectile", "Slime Grenade");
        add("entity.trd.grenade_if_projectile", "Impact Grenade");
        add("entity.trd.grenade_if_fire_projectile", "Incendiary Impact Grenade");
        add("entity.trd.grenade_if_slime_projectile", "Slime Impact Grenade");
        add("entity.trd.grenade_if_he_projectile", "HE Impact Grenade");
        add("entity.trd.grenade_nuc_projectile", "Nuclear Grenade");
        add("jei.category.trd.drobitel", "Crushing");

        add("tooltip.trd.machine.hold_shift", "Hold Shift for detailed description");
        add("tooltip.trd.machine.stator.desc", "|Stator| - the base of the generator, for the production of |JE energy|, which will be produced by the interaction of the magnetic fields of the |stator| with the magnetic fields of the |rotor|, under the action of |torque|. |JE Energy| is output through 4 ports on the aligned outer sides. For operation, it requires special |stator coils|.");
        add("tooltip.trd.machine.rotor.desc", "|Rotor| - a mandatory part of the generator, for the production of |JE energy|. For functioning, it requires a shaft fixed on a bearing. Together with the shaft, the |rotor| takes over its characteristics and adds its own |inertia| to them.");
        add("tooltip.trd.machine.stator_coil.desc", "|Stator coil| - a mandatory component of the generator, for the production of |JE energy|. It is placed exclusively on the inner walls of the stator (installation locations are highlighted). To ensure better generator operation and to avoid penalties to the required |torque|, it is recommended to place an even number of |stator coils| opposite each other.");
        add("tooltip.trd.machine.boiler.desc", "|Copper Liquid Boiler| - a unit designed for heating liquids by evenly absorbing heat from the bottom part. Ports for input fluids are located at the base of the boiler, on the lower part of the casing. All heated fluids are output through the top port on the roof. The boiler has limitations on the volume of heated fluids and temperature. When limits are exceeded, it overflows and ruptures from the inside, this process is accompanied by a small explosion.");
        add("tooltip.trd.machine.reaction_chamber.desc", "|Reaction Chamber| - a mandatory component of the |chemical plant|. All processes and reactions take place in it, at a certain speed. To carry out a chemical reaction, heat from the |chemical plant heater| is required. To supply reagents into the reaction chamber, a |chemical plant port| is required, through which liquids and materials will be supplied in certain proportions. Heat from multiple heaters connected to one chamber is summed.");
        add("tooltip.trd.machine.chem_port.desc", "|Chemical Plant Port| - a mandatory component of the chemical plant. Through it, all liquids and materials in certain quantities automatically enter the |reaction chamber|. The port must be connected to it with its frontal side, which has a narrow opening. The port can operate in two modes: inserter and extractor. In the first case, the port will try to load into the |reaction chamber| liquids and materials located in its buffers, while in the second mode it will try to take from the |reaction chamber| liquids and items from its output slots. Liquids and materials must be supplied/extracted to/from the port through inputs on other sides.");
        add("tooltip.trd.machine.chem_heater.desc", "|Chemical Plant Heater| - a mandatory component of the chemical plant. It transfers the required amount of heat into the |reaction chamber| for carrying out chemical processes. For its own operation, it requires |JE energy|, which must be supplied through the rear port. To regulate heat production, a screwdriver must be used.");
        add("tooltip.trd.machine.small_smelter.desc", "|Small Smelter| - a unit that allows smelting items into liquid metal. For its own operation, it requires flammable items as fuel. To output liquid metals from the buffer, a |casting trough| is required, connected to a |casting pot| with a |casting mold| inserted into it.");
        add("tooltip.trd.machine.smelter.desc", "|Smelter| - a unit that allows smelting items into |liquid metal| and creating various |alloys|. For its own operation, it requires heating to a certain temperature, depending on the type of metal being smelted. Heat is received through the lower central block. The top row of slots in the interface is intended for creating |alloys|, the bottom for smelting. The smelter heats all items in the rows simultaneously, the more items in the slots, the higher the heat consumption. To output |liquid metals| from the buffer, a |casting trough| is required, connected to a |casting pot| with a |casting mold| inserted into it.");
        add("tooltip.trd.machine.drobitel.desc", "|Ore Crusher| - a mechanical machine capable of processing items and blocks. It can accept items through the opening on top (not recommended to climb inside). Requires special |blades| and continuous |torque| supply through kinetic ports to operate. Insert |blades| into the crusher by right-clicking the casing with two blades. The |crusher| can process all 9 input slots at once, but |torque| consumption will increase for each additional slot used. If maximum speed is exceeded, the |crusher| will break.");
        add("tooltip.trd.machine.blade.desc", "|Ore Crusher Blades| - a mandatory component for the |ore crusher|, allowing it to function. |Blades| made from different materials have different operating speed ranges required for optimal performance. Outside the operating speed range, |blades| will work with a penalty to |torque|.");
        add("tooltip.trd.machine.steam_engine.desc", "|Steam Engine| - a mechanism capable of generating |torque| through the work of a steam piston. Steam must be supplied through the upper fluid inputs, and spent low-pressure steam removed through the lower fluid outputs. The power of multiple connected |steam engines| is summed.");
        add("tooltip.trd.machine.millstone.desc", "|Stone Millstone| - a manual mechanism for processing low-strength minerals. It performs work by grinding minerals into powder.");
        add("tooltip.trd.machine.water_pump.desc", "|Liquid Pump| - a mechanism designed for pumping fluids on a large scale. Requires continuous |torque| supply through kinetic ports to operate. The larger the fluid pool, the more efficient the pumping.");
        add("tooltip.trd.machine.condenser.desc", "|Low Pressure Steam Condenser| - a device for cooling |low pressure steam| back into liquid form. Can merge if |low pressure steam condensers| are placed with their ports adjacent to each other, forming a single row. Full submersion of the |low pressure steam condenser| in water is mandatory for operation. The cooling bonus depends entirely on the size of the water body in which it is submerged.");
        add("tooltip.trd.machine.clutch.desc", "|Clutch| - a special module capable of breaking the |torque| transmission chain using a redstone signal.");
        add("tooltip.trd.machine.beam_placer.desc", "|Beam Placer| - a special tool capable of placing rows of beam blocks using the |P2P| principle. When one component in an installed beam row is destroyed, the entire structure collapses. When placing a beam row, keep in mind that there should be no obstacles between the connection points.");
        add("tooltip.trd.machine.wire_coil.desc", "|Copper Wire Spool| - a special tool capable of connecting |connectors| to each other. |Connectors| are connected with priority given to the one where the connection was started. Before using the spool, it must be loaded with 8 |copper wires| in a crafting table, which will be consumed during subsequent connections.");
        add("tooltip.trd.machine.connector.desc", "|Connector| - a participant in the |energy network|, allowing other participants to be connected using the |P2P| method, which is noticeably more economical compared to classic methods. Connection to another connector is made via a |copper wire spool|. Connection ports are located under the |connector|. When connecting two |connectors|, the maximum connection length is determined by the |connector| of the smallest size.");
        add("tooltip.trd.machine.trombone.desc", "|Stationary Rocket Launcher 'Trombone'| - |Automatic weaponry| capable of controlling the area. Requires |100-mm missiles| and |JE energy| to operate. Fires a salvo of three missiles that launch vertically and then redirect toward the selected target, subsequently tracking it. Features a high-tech menu where you can configure the operating mode, view statistics, and manage the player whitelist (requires installing a |turret combat chip|).");
        add("tooltip.trd.machine.turret_chip.desc", "|Turret Combat Chip| - a module that allows moderating the player whitelist for |automatic weaponry|. Pressing |Shift + RMB| adds the player currently holding the chip to the whitelist.");
        add("tooltip.trd.machine.machinegun.desc", "|A.P. 17| - a small-caliber automatic turret-type cannon. Fires 20-mm turret-type rounds in a parabolic trajectory. Despite the lack of sights and significant spread, it possesses monstrous firepower.");
        add("tooltip.trd.machine.turret_light.desc", "|Light Landing Turret 'Nagual'| - |Automatic weaponry| capable of controlling the area. Requires |20-mm turret-type rounds| and |JE energy| to operate. Features a high-tech menu where you can configure the operating mode, view statistics, and manage the player whitelist (requires installing a |turret combat chip|). Press F3 to visualize the targeting process.");
        add("tooltip.trd.machine.belt.desc", "|Belt| - a consumable material required for connecting |pulleys|. Each connection consumes 1 |belt|. Connection distance limit is 16 blocks.");
        add("tooltip.trd.machine.fluid_identifier.desc", "|Fluid Identifier| - a special tool capable of setting the fluid type for elements of the |fluid system|. |RMB| assigns the fluid type. |Shift + RMB| sets the fluid type for a row of connected elements. |Shift + RMB| in the air opens the tool interface.");

        add("item.trd.hot_ingot.tooltip", "§6§lHOT! §r§7(%s%%)");
        add("item.trd.grenadier_goggles.desc.explosion_resist", "Explosion Resistance: +%s%%");

        // ═══ Coccer Oven ═══
        add(ModBlocks.COCCER_OVEN.get(), "Coking Oven");
        add("gui.trd.coccer_oven.temperature", "%s / %s °C");
        add("gui.trd.coccer_oven.required_temp", "Required: %s°C");
        add("gui.trd.coccer_oven.remaining", "Remaining: %ss");
        add("gui.trd.coccer_oven.bonus", "Speed bonus: +%s%%");
        add("gui.trd.coccer_oven.too_cold", "Too cold!");
        add("gui.trd.coccer_oven.no_recipe", "No recipe");

        // ═══ Vishelashivater (Leacher) ═══
        add(ModBlocks.VISHELASHIVATEL.get(), "Leacher");
        add("recipe.trd.leather_from_rotten_flesh", "Leather from Rotten Flesh");
        add("gui.trd.vishelashivatel.empty_tank", "Empty");
        add("gui.trd.vishelashivatel.fluid_amount", "%s / %s mB");
        add("gui.trd.vishelashivatel.fluid_req", "Fluid:");
        add("gui.trd.vishelashivatel.item_input", "Input:");
        add("gui.trd.vishelashivatel.item_output", "Output:");
        add("gui.trd.vishelashivatel.min_rpm", "Min speed: %s RPM");
        add("gui.trd.vishelashivatel.time", "Time: %ss");
        add("hud.trd.leacher.no_recipe", "No recipe");
        add("hud.trd.leacher.arrow_in", "-->");
        add("hud.trd.leacher.arrow_out", "<--");
        add("hud.trd.leacher.input", "input");
        add("hud.trd.leacher.output", "output");
        add("hud.trd.leacher.progress", "Progress: %s%%");

        // ═══ Centrifuge ═══
        add(ModBlocks.CENTRIFUGE_MOTOR.get(), "Centrifuge Motor");
        add(ModBlocks.CENTRIFUGE_CONUS.get(), "Centrifuge");
        add("recipe.trd.dirt_centrifuging", "Dirt Centrifugation");
        add("recipe.trd.gravel_centrifuging", "Gravel Centrifugation");
        add("recipe.trd.bone_block_centrifuging", "Bone Block Centrifugation");
        add("gui.trd.centrifuge.energy_tooltip", "%s / %s JE");
        add("gui.trd.centrifuge.progress_tooltip", "Remaining: ~%ss");
        add("hud.trd.centrifuge.no_attachment", "Install an attachment!");
        add("hud.trd.centrifuge.energy", "Energy: %s / %s JE");
        add("hud.trd.centrifuge.recipe", "Recipe: %s");
        add("hud.trd.centrifuge.no_recipe", "No recipe");
        add("hud.trd.centrifuge.progress", "Progress: %s%%");
        add("gui.trd.coccer_oven.empty_tank", "Empty");
        add("gui.trd.coccer_oven.fluid_amount", "%s / %s mB");
        add("gui.trd.coccer_oven.progress.temperature", "Temperature: %d/%d °C");
        add("gui.trd.coccer_oven.progress.remaining", "Remaining: %ss");
    }

    private void addRussian() {
        // Death messages
        add("death.attack.crusher", "%1$s стал фаршем");

        // ═══ Коксовая печь ═══
        add(ModBlocks.COCCER_OVEN.get(), "Коксовая печь");
        add("gui.trd.coccer_oven.temperature", "%s / %s °C");
        add("gui.trd.coccer_oven.required_temp", "Требуется: %s°C");
        add("gui.trd.coccer_oven.remaining", "Осталось: %ss");
        add("gui.trd.coccer_oven.bonus", "Бонус скорости: +%s%%");
        add("gui.trd.coccer_oven.too_cold", "Слишком холодно!");
        add("gui.trd.coccer_oven.no_recipe", "Нет рецепта");

        // ═══ Выщелащиватель ═══
        add(ModBlocks.VISHELASHIVATEL.get(), "Выщелащиватель");
        add("jei.category.trd.vishelashivatel", "Выщелащиватель");
        add("recipe.trd.leather_from_rotten_flesh", "Производство кожи");
        add("gui.trd.vishelashivatel.empty_tank", "Пусто");
        add("gui.trd.vishelashivatel.fluid_amount", "%s / %s мБ");
        add("gui.trd.vishelashivatel.fluid_req", "Жидкость:");
        add("gui.trd.vishelashivatel.item_input", "Вход:");
        add("gui.trd.vishelashivatel.item_output", "Выход:");
        add("gui.trd.vishelashivatel.min_rpm", "От %s об/мин");
        add("gui.trd.vishelashivatel.time", "Время: %sс");
        add("hud.trd.leacher.no_recipe", "Нет рецепта");
        add("hud.trd.leacher.arrow_in", "-->");
        add("hud.trd.leacher.arrow_out", "<--");
        add("hud.trd.leacher.input", "вход");
        add("hud.trd.leacher.output", "выход");
        add("hud.trd.leacher.progress", "Прогресс: %s%%");

        // ═══ Центрифуга ═══
        add(ModBlocks.CENTRIFUGE_MOTOR.get(), "Мотор центрифуги");
        add(ModBlocks.CENTRIFUGE_CONUS.get(), "Центрифуга");
        add("recipe.trd.dirt_centrifuging", "Центрифугирование земли");
        add("recipe.trd.gravel_centrifuging", "Центрифугирование гравия");
        add("recipe.trd.bone_block_centrifuging", "Центрифугирование костяного блока");
        add("gui.trd.centrifuge.energy_tooltip", "%s / %s JE");
        add("gui.trd.centrifuge.progress_tooltip", "Осталось: ~%sс");
        add("hud.trd.centrifuge.no_attachment", "Для работы требуется насадка!");
        add("hud.trd.centrifuge.energy", "Энергия: %s / %s JE");
        add("hud.trd.centrifuge.recipe", "Рецепт: %s");
        add("hud.trd.centrifuge.no_recipe", "Нет рецепта");
        add("hud.trd.centrifuge.progress", "Прогресс: %s%%");
        add("gui.trd.coccer_oven.empty_tank", "Пусто");
        add("gui.trd.coccer_oven.fluid_amount", "%s / %s мБ");
        add("gui.trd.coccer_oven.progress.temperature", "Температура: %d/%d °C");
        add("gui.trd.coccer_oven.progress.remaining", "Осталось: %sс");

        // Креативные вкладки
        add("itemGroup.trd.trd_build_tab", "Строительные блоки");
        add("itemGroup.trd.trd_tech_tab", "Технологии");
        add("itemGroup.trd.trd_weapons_tab", "Арсенал");
        add("itemGroup.trd.trd_recourses_tab", "Ресурсы");
        add("itemGroup.trd.trd_nature_tab", "Природа");

        // JEI Категории
        add("jei.category.trd.smelting", "Плавка");
        add("jei.category.trd.casting", "Литьё");
        add("jei.category.trd.drobitel", "Дробление");
        add("jei.category.trd.alloying", "Сплавление");
        add("jei.category.trd.millstone", "Жернов");
        add("jei.category.trd.boiling", "Бойлер");
        add("jei.category.trd.steam_engine", "Паровой двигатель");
        add("jei.category.trd.condensing", "Конденсация");
        add("jei.category.trd.electric_furnace", "Электропечь");
        add("jei.category.trd.coccer_oven", "Коксовая печь");
        add("jei.category.trd.chemical_plant", "Химическая установка");

        // Литые кирки

        add("item.trd.cast_pickaxe.desc.charge", "§7Зажмите ПКМ для мощного удара");
        add("item.trd.cast_pickaxe.desc.mining_power", "§6Мощность: %s");
        add("item.trd.cast_pickaxe.desc.vein_miner_info", "Жильный майнер: %s");
        add("item.trd.cast_pickaxe.desc.tunnel_miner", "Туннельный майнер: %s");

        // Гренадёр
        add(ModItems.GRENADIER_GOGGLES.get(), "Очки гренадёра");
        add(ModItems.FLYWHEEL_LIGHT.get(), "Лёгкий маховик");
        add("item.trd.grenadier_goggles.desc.explosion_resist", "Защита от взрывов: +%s%%");

        // Металлы
        add("metal.trd.gold", "Золото");
        add("metal.trd.iron", "Железо");
        add("metal.trd.copper", "Медь");
        add("metal.trd.netherite", "Незерит");
        add("metal.trd.steel", "Сталь");
        add("metal.trd.aluminum", "Алюминий");
        add("metal.trd.bronze", "Бронза");
        add("metal.trd.tin", "Олово");
        add("metal.trd.zinc", "Цинк");
        add("metal.trd.titanium", "Титан");
        add("metal.trd.lead", "Свинец");
        add("metal.trd.beryllium", "Бериллий");
        add("metal.trd.industrial_copper", "Промышленная медь");
        add("metal.trd.tungsten", "Вольфрам");
        add("metal.trd.neodymium", "Неодим");

        // Уровни нагревателя
        add("gui.trd.heater.tier0", "Уровень 0");
        add("gui.trd.heater.tier1", "Уровень I");
        add("gui.trd.heater.tier2", "Уровень II");
        add("gui.trd.heater.tier3", "Уровень III");
        add("gui.trd.heater.tier4", "Уровень IV");
        add("gui.trd.heater.tier5", "Уровень V");

        add("item.trd.hot_ingot.tooltip", "§6§lРАСКАЛЁННЫЙ! §r§7(%s%%)");

        add("hud.trd.chamber.no_recipe", "Нет рецепта");
                add("hud.trd.chamber.arrow_in", "-->");
                add( "hud.trd.chamber.arrow_out", "<--");
                add("hud.trd.chamber.input", "вход");
                add("hud.trd.chamber.output", "выход");
                add( "hud.trd.chamber.progress", "Прогресс: %s%%");
        add("block.trd.chemical_plant_reaction_chamber", "Реакционная камера");
        add("block.trd.chemical_plant_port", "Порт хим. установки");
        add("recipe.trd.hydrogen_peroxide", "Пероксид водорода");
        add("recipe.trd.sulfuric_acid", "Серная кислота");
        add("recipe.trd.obsidian", "Обсидиан");
        add(  "gui.trd.chemistry.empty", "Пусто");
        add(  "gui.trd.chemistry.inputs", "Входы:");
        add(  "gui.trd.chemistry.outputs", "Выходы:");
        add("gui.trd.chemistry.time", "Время: %s сек");

        add("tooltip.trd.machine.hold_shift", "Удерживайте Shift для подробного описания");
        add("tooltip.trd.machine.stator.desc", "|Статор| - основа генератора, для выработки |JE энергии|, которая будет вырабатываться, за счет взаимодействия магнитных полей |статора| с магнитными полями |ротора|, под действием |крутящего момента|. |Энергия JE| выводится через 4 порта на спрямленных внешних сторонах. Для работы требует наличия специальных |катушек статора|.");
        add("tooltip.trd.machine.rotor.desc", "|Ротор| - обязательная деталь генератора, для выработки |JE энергии|. Для функционирования требует вал, закрепленный на подшипнике. Вместе с валом, |ротор| перенимает его характеристики и добавляет к ним собственную |инерцию|.");
        add("tooltip.trd.machine.stator_coil.desc", "|Катушка статора| - обязательный компонент генератора, для выработки |JE энергии|. Ставится исключительно на внутренние стенки статора (места для установки подсвечиваются). Для обеспечения лучшей работы генератора и во избежание штрафов к требуемому |крутящему моменту|, рекомендуется ставить четное количество |катушек статора| друг напротив друга.");
        add("tooltip.trd.machine.boiler.desc", "|Медный жидкостный бойлер| - установка, предназначенная для нагрева жидкостей, за счет равномерного поглощения тепла с нижней части. Порты для входных жидкостей находятся у основания бойлера, на нижней части корпуса. Все нагретые жидкости выводятся через верхний порт на крыше. Бойлер имеет ограничения по объему нагретых жидкостей и температуры. При превышении лимитов, он переполняется и разрывается изнутри, данный процесс сопровождается небольшим взрывом.");
        add("tooltip.trd.machine.reaction_chamber.desc", "|Реакционная камера| - обязательный компонент |химической установки|. В ней проходят все процессы и реакции, с определённой скоростью. Для проведения химической реакции требуется тепло от |нагревателя хим. установки|. Для подачи реагентов в реакционную камеру, необходим |порт хим. установки|, через который жидкости и материалы будут поступать в определённых пропорциях. Тепло от нескольких нагревателей, подключенных к одной камере, суммируется.");
        add("tooltip.trd.machine.chem_port.desc", "|Порт хим. установки| - обязательный компонент химической установки. Через него все жидкости и материалы в определённых количествах поступают в |реакционную камеру| автоматически. Порт должен быть подключён к ней своей фронтальной стороной, имеющей узкое отверстие. Порт может работать в двух режимах: вставщик и извлекатель. В первом случае, порт будет пытаться загрузить в |реакционную камеру| жидкости и материалы, находящиеся в его буферах, в то время как во втором режиме он будет пытаться забрать из |реакционной камеры| жидкости и предметы из её выходных слотов. Жидкости и материалы необходимо подавать/извлекать в/их порта через входы с других сторон.");
        add("tooltip.trd.machine.chem_heater.desc", "|Нагреватель хим. установки| - обязательный компонент химической установки. Он передаёт необходимое количество тепла в |реакционную камеру| для проведения химических процессов. Для собственной работы требует |JE энергию|, которую необходимо подать через задний порт. Для регулирования производства тепла, необходимо использовать отвёртку.");
        add("tooltip.trd.machine.small_smelter.desc", "|Малая плавильня| - установка, позволяющая переплавлять предметы в жидкий металл. Для собственной работы требует горючие предметы в качестве топлива. Для вывода жидких металлов с буфера, требуется |литейный желоб|, подключенный к |литейному котлу| с вставленной в него |литейной формой|.");
        add("tooltip.trd.machine.smelter.desc", "|Плавильня| - установка, позволяющая переплавлять предметы в |жидкий металл| и создавать различные |сплавы|. Для собственной работы требует нагрева до определённой температуры, в зависимости от типа плавящегося металла. Тепло принимается через нижний центральный блок. Верхний ряд слотов в интерфейсе предназначен для создания |сплавов|, нижний для переплавки. Плавильня нагревает все предметы в рядах одновременно, чем больше предметов в слотах, тем выше потребление тепла. Для вывода |жидких металлов| с буфера, требуется |литейный желоб|, подключенный к |литейному котлу| с вставленной в него |литейной формой|.");  // ═══ GUI: Electric Furnace ═══
        add("tooltip.trd.machine.drobitel.desc", "|Рудный дробитель| - механическая установка, позволяющая проводить механическую обработку предметов и блоков. Способен принимать предметы через отверстие сверху (не рекомендуется залезать туда). Для работы требует специальные |лезвия| и неприрывную подачу |крутящего момента| через кинетические порты. Вставлять |лезвия| в дробитель нужно в количестве двух штук нажатием правой кнопкой мыши по корпусу. |Дробитель| способен обрабатывать все 9 слотов на входе за раз, но потребление |крутящего момента| будет расти за каждый новый задействованный слот. При превышении максимальной скорости |дробитель| сломается.");
        add("tooltip.trd.machine.blade.desc", "|Лезвия рудного дробителя| - обязательный компонент к |рудному дробителю|, позволяющий ему функционировать. |Лезвия| из разных материалов имеют разные диапозоны рабочих скоростей, необходимых для их оптимальной работы. Вне диапозона рабочей скорости, |лезвия| будут работать со штрафом к |крутящему моменту|.");
        add("tooltip.trd.machine.steam_engine.desc", "|Паровой двигатель| - механизм, позволяющий генерировать |крутящий момент| благодаря работе парового поршня. Вводить пар необходимо через верхние жидкостные входы, а отработынный пар низкого давления через нижние жидкостные выходы. Мощности нескольких соединенных друг за другом |паровых двигателей| суммируются.");
        add("tooltip.trd.machine.millstone.desc", "|Каменные жернова| - ручной механизм механической обработки минералов малой прочности. Совершает работу, измельчая минералы до порошкообразных состояний.");
        add("tooltip.trd.machine.water_pump.desc", "|Жидкостная помпа| - механизм, предназначенный для выкачки жидкостей в больших масштабах. Для работы требует неприрывную подачу |крутящего момента| через кинетические порты. Чем больше жидкостный бассейн, тем эффективнее выкачка.");
        add("tooltip.trd.machine.condenser.desc", "|Конденсатор пара низкого давления| - устройство, для охлаждения |пара низкого давления| обратно до жидкого состояния. Способен объединяться, если |конденсаторы пара низкого давления| стоят своими портами вплотную друг к другу, образуя единый ряд. Обязательным условием работы является полное погружение |конденсатора пара низкого давления| в воду. Бонус к охлаждению полностью зависит от размеров водоёма, в котором он погружен.");
        add("tooltip.trd.machine.clutch.desc", "|Сцепление| - специальный модуль, способный разрывать цепь передачи |крутящего момента| с помощью сигнала красного камня.");
        add("tooltip.trd.machine.beam_placer.desc", "|Установщик балок| - специальный инструмент, способный размещать ряды блоков балок по |P2P| принципу. При разрушении одного компонента в установленном ряде балок, разрушается вся конструкция. При размещении ряда балок, стоит учесть, что между точками соединения не должно находится каких-либо препятствий.");
        add("tooltip.trd.machine.wire_coil.desc", "|Катушка медного провода| - специальный инструмент, способный соединять между собой |коннекторы|. |Коннекторы| соединяются с приоритетом на тот, с которого началось соединение. Перед использованием катушки, на неё необходимо намотать 8 |медных проводов| в верстаке, которые будут расходоваться при последующих соединениях.");
        add("tooltip.trd.machine.connector.desc", "|Коннектор| - участник |энергосети|, позволяющий соединять другие её участки |P2P| методом, что заметно экономнее, в сравнении с классическими методами. Соединение с другим коннектором осуществляется через |катушку медного провода|. Порты соединений, находятся под |коннектором|. При соединении двух |коннекторов|, максимальная длинна соединения будет избираться по |коннектору| наименьшего размера.");
        add("tooltip.trd.machine.trombone.desc", "|Стационарная ракетная установка \"Тромбон\"| - |Автоматический вид вооружения|, позволяющий контролировать местность. Для работы требует |100-мм ракеты| и |JE энергию|. Выстреливает залпом из трёх ракет, которые вылетают вертикально и после перенаправляются на выбранную цель, с последующим её сопровождением. Обладает высотехнологичным меню, в котором можно настроить режим работы, просмотреть статистику и управлять белым списоком игроков (необходимо установить |турельный боевой чип|).");
        add("tooltip.trd.machine.turret_chip.desc", "|Турельный боевой чип| - модуль, позволяющий модерировать белый список игроков у |автоматического вида оружия|. При нажатиии |Shift + ПКМ| позволяет добавить в белый список игрока, у которого на данный момент находится чип.");
        add("tooltip.trd.machine.machinegun.desc", "|А.П. 17| - мелкокалиберная автоматическая пушка турельного типа. Стреляет 20-мм патронами турельного типа по параболической траектории. Не смотря на отсутствие прицела и значительный разброс, обладает чудовищной убойной мощью.");
        add("tooltip.trd.machine.turret_light.desc", "|Легкая десантная турель 'Нагваль'| - |Автоматический вид вооружения|, позволяющий контролировать местность. Для работы требует |20-мм патроны турельного типа| и |JE энергию|. Обладает высотехнологичным меню, в котором можно настроить режим работы, просмотреть статистику и управлять белым списоком игроков (необходимо установить |турельный боевой чип|). Для визуализации процессов наводки, нажмите клавишу F3.");
        add("tooltip.trd.machine.belt.desc", "|Ремень| - расходный материал, необходимый для соединения |шкивов|. На 1 соединение расходуется 1 |ремень|. Лимит дистанции соединения - 16 блоков.");
        add("tooltip.trd.machine.fluid_identifier.desc", "|Жидкостный идентификатор| - специальный инструмент, способный задавать элементам |жидкостной системы| тип жидкости. |ПКМ| назначает тип жидкости. |Shift + ПКМ| задаёт ряду соединённых эллементов тип жидкости. |Shift + ПКМ| по воздуху открывает интерфейс инструмента.");


        // ═══ GUI: Electric Furnace ═══
        add("gui.trd.electric_furnace.energy_tooltip", "%s / %s JE");
        add("gui.trd.electric_furnace.progress_tooltip", "§6Осталось: §f%s сек");

        // ═══ GUI: Fluid Barrel / Fuel Tank (shared) ═══
        add("tooltip.trd.explosion_resistance", "Взрывоустойчивость: %s");
        add("gui.trd.fluid_barrel.empty", "Пусто");
        add("gui.trd.fluid_barrel.amount", "%s / %s mB");
        add("gui.trd.fluid_barrel.mode.title", "Режим:");
        add("gui.trd.fluid_barrel.mode.both", "§aВход / Выход (Оба)");
        add("gui.trd.fluid_barrel.mode.input", "§bТолько Вход");
        add("gui.trd.fluid_barrel.mode.output", "§6Только Выход");
        add("gui.trd.fluid_barrel.mode.disabled", "§cОтключено");
        add("gui.trd.fluid_barrel.mode.unknown", "Неизвестно");

        // ═══ GUI: Fluid Identifier ═══
        add("gui.trd.fluid_identifier.title", "Жидкостный идентификатор");
        add("gui.trd.fluid_identifier.unknown", "Неизвестно");

        // ═══ GUI: Heater ═══
        add("hud.trd.chem_heater.title", "Хим. Нагреватель");
        add("hud.trd.chem_heater.mode", "Режим");
        add("hud.trd.chem_heater.mode.off", "Выкл");
        add("hud.trd.chem_heater.consumption", "Расход");
        add("hud.trd.chem_heater.charge", "Заряд");
        
        add("gui.trd.heater.fuel_tiers_title", "§6§lТопливные тиры:");
        add("gui.trd.heater.fuel_tier_format", "§8Тир %s: §f%s°C, §f%s§7с.");
        add("gui.trd.heater.temperature_format", "%s / %s °C");
        add("gui.trd.heater.burn_time_format", "§6Осталось: §f%s§7/§f%s сек");
        add("gui.trd.heater.stopped", "§7Остановлен");

        // ═══ GUI: Machine Battery ═══
        add("gui.trd.battery.panel.out", "OUT: %s JE/S");
        add("gui.trd.battery.panel.in", "IN: %s JE/S");
        add("gui.trd.battery.tooltip.discharge_speed", "§cСкорость разрядки: %s JE/t");
        add("gui.trd.battery.tooltip.charge_speed", "§aСкорость зарядки: %s JE/t");
        add("gui.trd.battery.tooltip.speed_per_second", "(%s JE/s)");

        // ═══ GUI: Small Smelter ═══
        add("gui.trd.small_smelter.fuel_tiers_title", "§6§lТопливные тиры:");
        add("gui.trd.small_smelter.fuel_tier.0", "§8Тир 0: §f1°C, §f6.25§7с.");
        add("gui.trd.small_smelter.fuel_tier.1", "§8Тир 1: §f2°C, §f12.5§7с.");
        add("gui.trd.small_smelter.fuel_tier.2", "§8Тир 2: §f3°C, §f25§7с.");
        add("gui.trd.small_smelter.fuel_tier.3", "§8Тир 3: §f4°C, §f40§7с.");
        add("gui.trd.small_smelter.fuel_tier.4", "§8Тир 4: §f6°C, §f60§7с.");
        add("gui.trd.small_smelter.fuel_tier.5", "§8Тир 5: §f8°C, §f120§7с.");
        add("gui.trd.small_smelter.temperature_format", "%s / %s °C");
        add("gui.trd.small_smelter.burn_time_format", "§6Осталось: §f%s§7/§f%s сек");
        add("gui.trd.small_smelter.stopped", "§7Остановлен");
        add("gui.trd.small_smelter.progress.temperature_format", "Температура: %d/%d °C");
        add("gui.trd.small_smelter.progress.remaining", "Осталось: %sс");
        add("gui.trd.small_smelter.metal_tank.title", "§6§lРасплавленные металлы:");
        add("gui.trd.small_smelter.metal_tank.empty", "§7Пусто");
        add("gui.trd.small_smelter.metal_tank.exact_format", "%s: %s ед.");
        add("gui.trd.small_smelter.metal_tank.block_abbr", "б");
        add("gui.trd.small_smelter.metal_tank.ingot_abbr", "сл");
        add("gui.trd.small_smelter.metal_tank.nugget_abbr", "см");
        add("gui.trd.small_smelter.metal_tank.total_exact", "§7Всего: §f%s§7 ед. / §f%s§7 ед.");
        add("gui.trd.small_smelter.metal_tank.shift_hide", "§8[Shift] скрыть точное значение");
        add("gui.trd.small_smelter.metal_tank.shift_show", "§8[Shift] точное значение");

        // ═══ GUI: Smelter ═══
        add("gui.trd.smelter.temperature_format", "%d / %d °C");
        add("gui.trd.smelter.progress.temperature_format", "Температура: %d/%d °C");
        add("gui.trd.smelter.progress.remaining", "Осталось: %sс");
        add("gui.trd.smelter.metal_tank.title", "§6§lРасплавленные металлы:");
        add("gui.trd.smelter.metal_tank.empty", "§7Пусто");
        add("gui.trd.smelter.metal_tank.block_abbr", "блоки");
        add("gui.trd.smelter.metal_tank.ingot_abbr", "слитки");
        add("gui.trd.smelter.metal_tank.nugget_abbr", "самородки");
        add("gui.trd.smelter.metal_tank.total_exact", "§7Всего: §f%d§7 ед. / §f%d§7 ед.");
        add("gui.trd.smelter.metal_tank.total_converted", "§7Всего: §f%dб, %dсл, %dсм §8/ %d блоков");
        add("gui.trd.smelter.metal_tank.shift_hide", "§8[Shift] скрыть точное значение");
        add("gui.trd.smelter.metal_tank.shift_show", "§8[Shift] точное значение");

        // ═══ GUI: Turret (общее для Light & Trombone) ═══
        add("gui.trd.turret.boot", "ЗАГРУЗКА%s");
        add("gui.trd.turret.status.online", "СИСТЕМА В НОРМЕ");
        add("gui.trd.turret.status.repairing", "РЕМОНТ: %s%%");
        add("gui.trd.turret.status.charging", "ЗАРЯДКА...");
        add("gui.trd.turret.status.standby", "РЕЖИМ ОЖИДАНИЯ");
        add("gui.trd.turret.menu.chip_control", "ЧИП");
        add("gui.trd.turret.menu.attack_mode", "РЕЖИМ АТАКИ");
        add("gui.trd.turret.menu.stats", "СТАТИСТИКА");
        add("gui.trd.turret.target.hostiles", "ВРАГИ");
        add("gui.trd.turret.target.neutrals", "НЕЙТРАЛЬНЫЕ");
        add("gui.trd.turret.target.players", "ИГРОКИ");
        add("gui.trd.turret.toggle.on", "[V]");
        add("gui.trd.turret.toggle.off", "[X]");
        add("gui.trd.turret.stats.kills", "УБИЙСТВА: %s");
        add("gui.trd.turret.stats.time", "ВРЕМЯ ЖИЗНИ: %dч %dм");
        add("gui.trd.turret.stats.owner", "ВЛАДЕЛЕЦ: [ДАННЫЕ]");
        add("gui.trd.turret.chip.empty", "СПИСОК ПУСТ");
        add("gui.trd.turret.chip.format", "%s/%s %s");
        add("gui.trd.turret.result.success", "УСПЕХ");
        add("gui.trd.turret.result.error", "ОШИБКА 404");
        add("gui.trd.turret.energy_tooltip", "%s / %s JE");

        // Light Turret
        add("gui.trd.turret.status.respawn", "ВОЗРОЖДЕНИЕ: %sс");

        // Trombone
        add("gui.trd.turret.status.reloading", "ПЕРЕЗАРЯДКА: %sс");
        add("gui.trd.turret.status.no_missiles", "НЕТ РАКЕТ");
        add("gui.trd.turret.menu.missiles", "РАКЕТЫ");
        add("gui.trd.turret.missiles.none", "НЕТ РАКЕТ!");
        add("gui.trd.turret.missiles.standard", "СТД: %s");
        add("gui.trd.turret.missiles.he", "ФУГ: %s");
        add("gui.trd.turret.missiles.fire", "ЗАЖ: %s");
        add("gui.trd.turret.missiles.total", "ВСЕГО: %s");

        add("gui.trd.battery.priority.0", "Приоритет: Низкий");
        add("gui.trd.battery.priority.0.desc", "Низший приоритет. Опустошается в первую очередь, заполняется в последнюю");
        add("gui.trd.battery.priority.1", "Приоритет: Нормальный");
        add("gui.trd.battery.priority.1.desc", "Стандартный приоритет для передачи энергии.");
        add("gui.trd.battery.priority.2", "Приоритет: Высокий");
        add("gui.trd.battery.priority.2.desc", "Высший приоритет. Заполняется первым, опустошается последним.");
        add("gui.trd.battery.priority.recommended", "(Рекомендуется)");

        add("gui.trd.battery.mode.both", "Режим: Приём и Передача");
        add("gui.trd.battery.mode.both.desc", "Разрешены все операции с энергией.");
        add("gui.trd.battery.mode.input", "Режим: Только Приём");
        add("gui.trd.battery.mode.input.desc", "Разрешён только приём энергии.");
        add("gui.trd.battery.mode.output", "Режим: Только Передача");
        add("gui.trd.battery.mode.output.desc", "Разрешена только отдача энергии.");
        add("gui.trd.battery.mode.locked", "Режим: Заблокировано");
        add("gui.trd.battery.mode.locked.desc", "Все операции с энергией отключены.");


        // ═══ HUD: Общая температура (Heater/Smelter/SmallSmelter) ═══
        add("hud.trd.temperature.format", "%.0f / %.0f °C");
        add("hud.trd.temperature.heating", "§6● §fНагрев");
        add("hud.trd.temperature.smelting", "§6● §fПлавка");

        // ═══ HUD: Low Pressure Steam Condenser ═══
        add("hud.trd.condenser.steam_name", "Пар Н.Д.");
        add("hud.trd.condenser.water_name", "Вода");
        add("hud.trd.condenser.arrow_in", "§a-> ");
        add("hud.trd.condenser.arrow_out", "§c<- ");
        add("hud.trd.condenser.amount", "§7%s/%s mB");
        add("hud.trd.condenser.status.no_water", "§cТребуется залить водой!");
        add("hud.trd.condenser.status.cooling", "§7Охлаждение: §b%.2fx");

        // ═══ HUD: Motor Electro ═══
        add("hud.trd.motor.title", "§e⚡ Мотор §7[%s]");
        add("hud.trd.motor.status.on", "§aON");
        add("hud.trd.motor.status.off", "§cOFF");
        add("hud.trd.motor.speed", "§7Скорость:    §f%s RPM");
        add("hud.trd.motor.torque", "§7Момент:      §f%s Нм");
        add("hud.trd.motor.consumption", "§7Потребление: §f%s JE/s");
        add("hud.trd.motor.charge", "§7Заряд: %s%s§7/%s JE");

        // ═══ HUD: Steel Storage ═══
        add("hud.trd.storage.header", "%s/%s слотов");
        add("hud.trd.storage.empty", "Пусто");
        add("hud.trd.storage.item", "• %s x%s");
        add("hud.trd.storage.more", "... и ещё %s");

        // ═══ HUD: Tachometer ═══
        add("hud.trd.tachometer.no_shaft", "⚠ Вал не вставлен");
        add("hud.trd.tachometer.title", "▶ Анализатор сети");
        add("hud.trd.tachometer.speed", "Скорость: %s RPM");
        add("hud.trd.tachometer.torque", "Момент: %s / %s Нм");
        add("hud.trd.tachometer.inertia", "Инерция: %.2f");
        add("hud.trd.tachometer.stress", "Нагрузка: %.1f%%");

        // ═══ HUD: Boiler ═══
        add("hud.trd.boiler.water", "Вода");
        add("hud.trd.boiler.steam", "Пар");
        add("hud.trd.boiler.arrow_in", "§a-> §7");
        add("hud.trd.boiler.arrow_out", "§c<- §7");
        add("hud.trd.boiler.amount_suffix", " mB");
        add("hud.trd.boiler.temperature", "Температура: %.1f °C");

        // ═══ HUD: Millstone ═══
        add("hud.trd.millstone.result", "✓ %s");
        add("hud.trd.millstone.result_extra", " + %s");
        add("hud.trd.millstone.take", "ПКМ чтобы забрать");
        add("hud.trd.millstone.progress", "%d/%d оборотов");
        add("hud.trd.millstone.remaining", "Осталось: %s");
        add("hud.trd.millstone.grind", "ПКМ для помола");
        add("hud.trd.millstone.empty", "Жернова пусты");
        add("hud.trd.millstone.insert", "Положите минерал");

        // ═══ HUD: Steam Engine ═══
        add("hud.trd.engine.steam", "Пар");
        add("hud.trd.engine.lp_steam", "Пар Н.Д.");
        add("hud.trd.engine.arrow_in", "§a-> §7");
        add("hud.trd.engine.arrow_out", "§c<- §7");
        add("hud.trd.engine.amount_suffix", " mB");

        // ═══ HUD: Stator ═══
        add("hud.trd.stator.coils_label", "Катушки: ");
        add("hud.trd.stator.buffer_label", "Буфер: ");
        add("hud.trd.stator.load_label", "Нагрузка: ");
        add("hud.trd.stator.production_label", "Производство: ");

        // ═══ GUI: Casting Pot ═══
        add("gui.trd.casting_pot.cannot_insert", "§cНельзя поместить: котёл занят или нет формы");
        add("gui.trd.casting_pot.slag_hot", "§cШлак горячий! Используйте кочергу.");
        add("gui.trd.casting_pot.too_hot", "§cСлишком горячо! %d°C (%d%%) Используйте кочергу.");
        add("gui.trd.casting_pot.too_hot_simple", "§cСлишком горячо! (%d%%) Используйте кочергу.");
        add("gui.trd.casting_pot.cannot_remove_mold", "§cНельзя извлечь форму: есть металл или предмет");

        // ═══ GUI: Machine Battery ═══
        add("gui.trd.machine_battery.cell_extracted", "§eЯчейка извлечена из слота %s");
        add("gui.trd.machine_battery.cell_inserted", "§aЯчейка вставлена в слот %s");
        add("gui.trd.machine_battery.slot_occupied", "§cСлот %s уже занят!");

        // ═══ Tooltip: Machine Battery ═══
        add("tooltip.trd.machine_battery.frame", "§7Каркас энергохранилища");
        add("tooltip.trd.machine_battery.energy", "§eЭнергия: %s JE");
        add("tooltip.trd.machine_battery.insert_cells", "§8Вставьте энергоячейки для увеличения параметров");

        // ═══ Message: Fluid Barrel ═══
        add("message.trd.fluid_barrel.filter_reset", "§eФильтр бочки сброшен (Закрыто)");
        add("message.trd.fluid_barrel.filter_set", "§aФильтр бочки: §f%s");

        // ═══ Tooltip: Fluid Barrel ═══
        add("tooltip.trd.fluid_barrel.capacity", "Ёмкость: ");
        add("tooltip.trd.fluid_barrel.melting_point", "Точка плавления: ");
        add("tooltip.trd.fluid_barrel.corrosion_resistance", "Коррозионная стойкость: ");
        add("tooltip.trd.fluid_barrel.leaking", "⚠ Протекает: ");
        add("tooltip.trd.fluid_barrel.leak_rate_unit", "мБ/сек");
        add("tooltip.trd.fluid_barrel.fluid_amount", "%s: %s/%s мБ");
        add("tooltip.trd.fluid_barrel.empty", "§bЖидкость: §7Пусто");
        add("tooltip.trd.fluid_barrel.filter", "§aФильтр: §f%s");
        add("tooltip.trd.fluid_barrel.filter_closed", "§aФильтр: §cЗакрыто");

        // ═══ Tooltip: Fluid Pipe ═══
        add("tooltip.trd.fluid_pipe.max_temp", "Макс. температура: ");
        add("tooltip.trd.fluid_pipe.max_corrosion", "Макс. коррозия: ");

        // ═══ Message: Fluid Pipe ═══
        add("message.trd.fluid_pipe.filter_line_reset", "§aФильтр линии труб сброшен. §7(%s труб)");
        add("message.trd.fluid_pipe.filter_line_set", "§aФильтр линии труб установлен: §f%s §7(%s труб)");
        add("message.trd.fluid_pipe.filter_reset", "§eФильтр сброшен (Труба принимает всё)");
        add("message.trd.fluid_pipe.filter_set", "§aФильтр: §f%s");

        // ═══ Tooltip: Low Pressure Steam Condenser ═══
        add("tooltip.trd.condenser.steam_in", "⬇ Пар Н.Д. (вход): ");
        add("tooltip.trd.condenser.water_out", "⬆ Вода (выход): ");
        add("tooltip.trd.condenser.cooling", "❄ Охлаждение: ");

        // ═══ Message: Valve ═══
        add("message.trd.valve.filter_reset", "§eФильтр клапана сброшен");
        add("message.trd.valve.filter_set", "§aФильтр клапана: §f%s");

        // ═══ Tooltip: Steel Storage ═══
        add("tooltip.trd.steel_storage.empty", "Пусто");
        add("tooltip.trd.steel_storage.contains", "Содержит: %s/%s");
        add("tooltip.trd.steel_storage.and_more", "... и ещё %s");
        add("tooltip.trd.steel_storage.item", "• %s x%s");

        // ═══ Tooltip & Message: Fuel Tank (shared) ═══
        add("message.trd.fuel_tank.filter_reset", "§eФильтр сброшен (цистерна закрыта)");
        add("message.trd.fuel_tank.filter_set", "§aФильтр установлен: §f%s");
        add("tooltip.trd.fuel_tank.capacity", "Ёмкость: %s мБ");
        add("tooltip.trd.fuel_tank.resistant", "Устойчив к коррозии и нагреву");
        add("tooltip.trd.fuel_tank.fluid_amount", "%s: %s/%s мБ");
        add("tooltip.trd.fuel_tank.empty", "§bЖидкость: §7Пусто");
        add("tooltip.trd.fuel_tank.type", "§aТип: §f%s");
        add("tooltip.trd.fuel_tank.type_not_set", "§aТип: §cне задан");

        // ═══ Tooltip: Conglomerate ═══
        add("tooltip.trd.conglomerate.empty", "§7Пустой кусок");
        add("tooltip.trd.conglomerate.contains_fractions", "§eСодержит фракции:");
        add("tooltip.trd.conglomerate.fraction", "%s: %d%%");
        add("tooltip.trd.conglomerate.ou", "§8OU: %d");
        add("tooltip.trd.conglomerate.vein_type", "§8Тип жилы: %s");

        // ═══ Tooltip: Energy Cell ═══
        add("tooltip.trd.energy_cell.energy_stored", "§eЭнергия: %s / %s JE");
        add("tooltip.trd.energy_cell.empty", "§7Энергия: Пусто");
        add("tooltip.trd.energy_cell.capacity", "Ёмкость: %s JE");
        add("tooltip.trd.energy_cell.charge_speed", "Скорость зарядки: %s JE/t");
        add("tooltip.trd.energy_cell.discharge_speed", "Скорость разрядки: %s JE/t");

        // ═══ Message: Wire Coil ═══
        add("message.trd.wire_coil.cancelled", "§eСоединение отменено.");
        add("message.trd.wire_coil.connector_full", "§cЭтот коннектор уже полностью занят!");
        add("message.trd.wire_coil.started", "§aНачато соединение... Кликните по второму коннектору.");
        add("message.trd.wire_coil.self_connect", "§cНельзя соединить коннектор с самим собой!");
        add("message.trd.wire_coil.first_destroyed", "§cПервый коннектор был разрушен или потерян.");
        add("message.trd.wire_coil.first_full", "§cПервый коннектор уже полностью занят!");
        add("message.trd.wire_coil.second_full", "§cВторой коннектор уже полностью занят!");
        add("message.trd.wire_coil.already_connected", "§cЭти коннекторы уже соединены!");
        add("message.trd.wire_coil.too_far", "§cСлишком далеко! Максимальная длина: %s блоков.");
        add("message.trd.wire_coil.blocked", "§cПуть заблокирован: %s");
        add("message.trd.wire_coil.success", "§bСоединение успешно установлено!");

        // ═══ Message: Belt ═══
        add("message.trd.belt.pulleys_only", "§cРемень можно натянуть только на шкивы!");
        add("message.trd.belt.already_connected", "§cЭтот шкив уже соединен ремнем!");
        add("message.trd.belt.first_selected", "§aПервый шкив выбран. Кликните по второму.");
        add("message.trd.belt.cancelled", "§eЛинковка отменена.");
        add("message.trd.belt.too_far", "§cСлишком далеко! (Макс. %s блоков)");
        add("message.trd.belt.first_destroyed", "§cПервый шкив был разрушен или снят.");
        add("message.trd.belt.axis_mismatch", "§cОси шкивов не параллельны!");
        add("message.trd.belt.not_coplanar", "§cШкивы должны лежать в одной плоскости!");
        add("message.trd.belt.pulley_occupied", "§cОдин из шкивов уже занят!");
        add("message.trd.belt.success", "§aРемень успешно натянут!");

        // ═══ Tooltip: Protector ═══
        add("tooltip.trd.protector.melting_point", "  +%s°C к точке плавления");
        add("tooltip.trd.protector.corrosion", "  +%s к коррозионной стойкости");
        add("tooltip.trd.protector.install", "§7Устанавливается в бочку");

        // ═══ Message: Poker ═══
        add("message.trd.poker.pot_empty", "§7Котёл пуст или содержит жидкий металл");
        add("message.trd.poker.hot_item_extracted", "§6Достали горячий предмет! %d°C");
        add("message.trd.poker.smelter_empty", "§7В плавильне нет металла");
        add("message.trd.poker.slag_dumped", "§6Сброшено %d единиц шлака");

        // ═══ Tooltip: Infinite Fluid Barrel ═══
        add("tooltip.trd.infinite_barrel.slot", "§8Поместите в слот опустошения");
        add("tooltip.trd.infinite_barrel.tank", "§8настроенной цистерны, чтобы");
        add("tooltip.trd.infinite_barrel.fill", "§8бесконечно заполнять её.");
        add("tooltip.trd.infinite_barrel.source", "§dБесконечный источник");

        // ═══ Tooltip: Fluid Identifier ═══
        add("tooltip.trd.fluid_identifier.fluid", "Жидкость: ");

        // ═══ Message: Beam Placer ═══
        add("message.trd.beam_placer.same_point", "§cТочки не могут совпадать! Сброс связи.");
        add("message.trd.beam_placer.not_enough", "§cНедостаточно балок! Требуется: §e%s");
        add("message.trd.beam_placer.placed", "§aБалка установлена! Потрачено: %s");
        add("message.trd.beam_placer.first_set", "§aПервая точка (центр) закреплена.");

        // ═══ Message: Cast Pickaxe ═══
        add("message.trd.cast_pickaxe.cooldown", "§cПерезарядка...");
        add("item.trd.cast_pickaxe.warning.twohanded", "§cНужны две руки!");

        // ═══ ТУЛТИПЫ ГРАНАТ ═══

// Общий hint для всех заряжаемых гранат
        add("tooltip.trd.grenade.charge_hint", "§8Удерживайте ПКМ для регулировки импульса броска");

// Обычные гранаты (с отскоком)
        add("tooltip.trd.grenade.common.line1", "§7Ручная противопехотная граната");
        add("tooltip.trd.grenade.standard.line2", "§8Тип: §fОсколочная §8| Отскоков: §f3 §8| Радиус: §f3.5 §8| Урон: §f20");
        add("tooltip.trd.grenade.he.line2", "§8Тип: §fФугасная §8| Отскоков: §f3 §8| Радиус: §f7.0 §8| Урон: §f40");
        add("tooltip.trd.grenade.fire.line2", "§8Тип: §cЗажигательная §8| Отскоков: §f3 §8| Радиус: §f3.0 §8| Урон: §f30");
        add("tooltip.trd.grenade.slime.line2", "§8Тип: §aЛипучка §8| Отскоков: §f4 §8| Радиус: §f3.5 §8| Урон: §f30 §8[Прилипает к целям]");
        add("tooltip.trd.grenade.smart.line2", "§8Тип: §eУмная §8| Отскоков: §f3 §8| Радиус: §f3.5/7.0 §8| Урон: §f20/40 §8[Детонация при контакте]");
        add("tooltip.trd.grenade.default.line2", "§8Стандартная осколочная граната");

// Ударные гранаты (инерционный взрыватель)
        add("tooltip.trd.grenade_if.common.line1", "§7Ударная граната с инерционным взрывателем");
        add("tooltip.trd.grenade_if.standard.line2", "§8Тип: §fОсколочная §8| Радиус: §f5.0 §8| Урон: §f45 §8| Задержка: §f4с");
        add("tooltip.trd.grenade_if.he.line2", "§8Тип: §fФугасная §8| Радиус: §f8.0 §8| Урон: §f80 §8| Задержка: §f4с");
        add("tooltip.trd.grenade_if.slime.line2", "§8Тип: §aЛипучка §8| Радиус: §f6.0 §8| Урон: §f60 §8| Задержка: §f4с §8[Прилипает к целям]");
        add("tooltip.trd.grenade_if.fire.line2", "§8Тип: §cЗажигательная §8| Радиус: §f6.0 §8| Урон: §f60 §8| Задержка: §f4с");
        add("tooltip.trd.grenade_if.default.line2", "§8Ударная осколочная граната");

// Грави-граната
        add("tooltip.trd.gravity_grenade.line1", "§d§lЭКСПЕРИМЕНТАЛЬНОЕ §7гравитационное оружие");
        add("tooltip.trd.gravity_grenade.line2", "§8Создаёт вихрь притяжения, затем разбрасывает цели");

// Ядерная (водородная) граната
        add("tooltip.trd.grenade_nuc.line1", "§4§lТАКТИЧЕСКИЙ ВОДОРОДНЫЙ ЗАРЯД");
        add("tooltip.trd.grenade_nuc.line2", "§cРадиус 25 §8| Урон 200 §8| Задержка 7с");
        add("tooltip.trd.grenade_nuc.line3", "§8Пробивает укрепления. Использовать с огромной осторожностью.");

// ═══ ТУЛТИПЫ АККУМУЛЯТОРОВ (ModBatteryItem) ═══
        add("tooltip.trd.battery.stored", "§7Заряд:");
        add("tooltip.trd.battery.transfer_rate", "§aВход: §f%s JE/t");
        add("tooltip.trd.battery.discharge_rate", "§cВыход: §f%s JE/t");

        add("tooltip.trd.creative_battery_desc","Аккумулятор прямиком из Бехлендса готов запитать любой механизм");
        add("tooltip.trd.creative_battery_flavor","Вот это замаз!");


        // Секвойя
        add(ModItems.PIG_TURRET_PLACER.get(), "Наф-Наф с турелью");
        add(ModBlocks.SEQUOIA_BARK.get(), "Кора секвойи");
        add(ModBlocks.SEQUOIA_HEARTWOOD.get(), "Бревно секвойи");
        add(ModBlocks.SEQUOIA_PLANKS.get(), "Доски из секвойи");
        add(ModBlocks.SEQUOIA_ROOTS.get(), "Корни секвойи");
        add(ModBlocks.SEQUOIA_ROOTS_MOSSY.get(), "Корни секвойи с мхом");
        add(ModBlocks.SEQUOIA_BARK_DARK.get(), "Тёмная кора секвойи");
        add(ModBlocks.SEQUOIA_BARK_MOSSY.get(), "Кора секвойи с мхом");
        add(ModBlocks.SEQUOIA_BARK_LIGHT.get(), "Светлая кора секвойи");
        add(ModBlocks.SEQUOIA_DOOR.get(), "Дверь из секвойи");
        add(ModBlocks.SEQUOIA_TRAPDOOR.get(), "Люк из секвойи");
        add(ModBlocks.SEQUOIA_BIOME_MOSS.get(), "Тёмный мох");
        add(ModBlocks.SEQUOIA_LEAVES.get(), "Листья секвойи");
        add(ModBlocks.SEQUOIA_SLAB.get(), "Плита из секвойи");
        add(ModBlocks.SEQUOIA_STAIRS.get(), "Ступени из секвойи");

        // Электроника
        add(ModItems.ENERGY_CELL_BASIC.get(), "Энергетическая ячейка");
        add(ModItems.CREATIVE_BATTERY.get(), "Бесконечный аккумулятор");
        add(ModItems.BATTERY.get(), "Аккумулятор");
        add(ModItems.BATTERY_ADVANCED.get(), "Улучшенный аккумулятор");
        add(ModItems.BATTERY_LITHIUM.get(), "Литий-ионный аккумулятор");
        add(ModItems.BATTERY_TRIXITE.get(), "Продвинутый аккумулятор");
        add(ModBlocks.MACHINE_BATTERY.get(), "Модульное энергохранилище");
        add(ModBlocks.CONVERTER_BLOCK.get(), "Энергетический конвертер");
        add(ModBlocks.WIRE_COATED.get(), "Провод из промышленной меди");
        add(ModBlocks.PAINTABLE_WIRE.get(), "Окрашиваемый провод");
        add(ModBlocks.CONNECTOR.get(), "Малый коннектор");
        add(ModBlocks.MEDIUM_CONNECTOR.get(), "Средний коннектор");
        add(ModBlocks.LARGE_CONNECTOR.get(), "Большой коннектор");
        add(ModBlocks.SWITCH.get(), "Рубильник");
        add(ModBlocks.VALVE.get(), "Жидкостный клапан");
        add(ModBlocks.ELECTRO_FURNACE.get(), "Электро-печь");
        add(ModBlocks.TURRET_LIGHT_PLACER.get(), "Лёгкая десантная турель \'Нагваль\'");
        add(ModBlocks.ARMORED_GLASS.get(), "Ударостойкое стекло");
        // Формы
        add(ModItems.MOLD_INGOT.get(), "Литейная форма слитка");
        add(ModItems.MOLD_PICKAXE.get(), "Литейная форма кирки");
        add(ModItems.MOLD_EMPTY.get(), "Пустая литейная форма");
        add(ModItems.MOLD_NUGGET.get(), "Литейная форма самородка");
        add(ModItems.MOLD_BLOCK.get(), "Литейная форма блока");
        add(ModItems.MOLD_PLATE.get(), "Литейная форма пластины");

        // Плавильные установки
        add(ModBlocks.SMALL_SMELTER.get(), "Малая плавильня");
        add(ModBlocks.DROBITEL.get(), "Рудный дробитель");
        add(ModBlocks.SMELTER.get(), "Плавильня");
        add(ModBlocks.JERNOVA.get(), "Каменные жернова");
        add(ModBlocks.CASTING_DESCENT.get(), "Литейный желоб");
        add(ModBlocks.CASTING_POT.get(), "Литейный котёл");
        add(ModItems.HEATER_ITEM.get(), "Нагреватель");
        add(ModItems.LIQUID_METAL.get(), "Жидкий металл");

        // Некроз
        add(ModBlocks.DEPTH_WORM_NEST.get(), "Узел улья глубинного червя");
        add(ModBlocks.HIVE_SOIL.get(), "Плоть улья глубинного червя");
        add(ModBlocks.HIVE_ROOTS.get(), "Нервные окончания улья глубинного червя");

        add(ModBlocks.STEEL_DOOR.get(), "Стальная дверь");
        add(ModBlocks.DECO_BARREL.get(), "Протекающая бочка (декор)");

        // Варианты бетона
        add(ModBlocks.CONCRETE.get(), "Бетон");
        add(ModBlocks.CONCRETE_SLAB.get(), "Бетонная плита");
        add(ModBlocks.CONCRETE_STAIRS.get(), "Бетонные ступени");
        add(ModBlocks.CONCRETE_RED.get(), "Красный бетон");
        add(ModBlocks.CONCRETE_RED_SLAB.get(), "Плита из красного бетона");
        add(ModBlocks.CONCRETE_RED_STAIRS.get(), "Ступени из красного бетона");
        add(ModBlocks.CONCRETE_BLUE.get(), "Синий бетон");
        add(ModBlocks.CONCRETE_BLUE_SLAB.get(), "Плита из синего бетона");
        add(ModBlocks.CONCRETE_BLUE_STAIRS.get(), "Ступени из синего бетона");
        add(ModBlocks.CONCRETE_GREEN.get(), "Зелёный бетон");
        add(ModBlocks.CONCRETE_GREEN_SLAB.get(), "Плита из зелёного бетона");
        add(ModBlocks.CONCRETE_GREEN_STAIRS.get(), "Ступени из зелёного бетона");
        add(ModBlocks.CONCRETE_HAZARD_NEW.get(), "Бетон с разметкой");
        add(ModBlocks.CONCRETE_HAZARD_NEW_SLAB.get(), "Плита из бетона с разметкой");
        add(ModBlocks.CONCRETE_HAZARD_NEW_STAIRS.get(), "Ступени из бетона с разметкой");
        add(ModBlocks.CONCRETE_HAZARD_OLD.get(), "Изношенный бетон с разметкой");
        add(ModBlocks.CONCRETE_HAZARD_OLD_SLAB.get(), "Плита из изношенного бетона с разметкой");
        add(ModBlocks.CONCRETE_HAZARD_OLD_STAIRS.get(), "Ступени из изношенного бетона с разметкой");
        add(ModBlocks.CONCRETE_TILE.get(), "Бетонная плитка");
        add(ModBlocks.CONCRETE_TILE_SLAB.get(), "Плита из бетонной плитки");
        add(ModBlocks.CONCRETE_TILE_STAIRS.get(), "Ступени из бетонной плитки");
        add(ModBlocks.CONCRETE_TILE_ALT.get(), "Гранённая бетонная плитка");
        add(ModBlocks.CONCRETE_TILE_ALT_SLAB.get(), "Плита из гранённой бетонной плитки");
        add(ModBlocks.CONCRETE_TILE_ALT_STAIRS.get(), "Ступени из гранённой бетонной плитки");
        add(ModBlocks.CONCRETE_TILE_ALT_BLUE.get(), "Окрашенная гранённая бетонная плитка");
        add(ModBlocks.CONCRETE_TILE_ALT_BLUE_SLAB.get(), "Плита из окрашенной гранённой бетонной плитки");
        add(ModBlocks.CONCRETE_TILE_ALT_BLUE_STAIRS.get(), "Ступени из окрашенной гранённой бетонной плитки");
        add(ModBlocks.CONCRETE_STRIPPED.get(), "Светлый текстурированный бетон");
        add(ModBlocks.CONCRETE_STRIPPED_SLAB.get(), "Плита из светлого текстурированного бетона");
        add(ModBlocks.CONCRETE_STRIPPED_STAIRS.get(), "Ступени из светлого текстурированного бетона");
        add(ModBlocks.CONCRETE_REINFORCED.get(), "Серый текстурированный бетон");
        add(ModBlocks.CONCRETE_REINFORCED_SLAB.get(), "Плита из серого текстурированного бетона");
        add(ModBlocks.CONCRETE_REINFORCED_STAIRS.get(), "Ступени из серого текстурированного бетона");
        add(ModBlocks.CONCRETE_REINFORCED_HEAVY.get(), "Тёмный текстурированный бетон");
        add(ModBlocks.CONCRETE_REINFORCED_HEAVY_SLAB.get(), "Плита из тёмного текстурированного бетона");
        add(ModBlocks.CONCRETE_REINFORCED_HEAVY_STAIRS.get(), "Ступени из тёмного текстурированного бетона");
        add(ModBlocks.CONCRETE_NET.get(), "Усиленный бетон");

        // Кирпичи
        add(ModBlocks.FIREBRICK_BLOCK.get(), "Блок огнеупорного кирпича");
        add(ModBlocks.FIREBRICK_SLAB.get(), "Плита из огнеупорного кирпича");
        add(ModBlocks.FIREBRICK_STAIRS.get(), "Ступени из огнеупорного кирпича");
        add(ModBlocks.REINFORCEDBRICK_BLOCK.get(), "Блок доломитового кирпича");
        add(ModBlocks.REINFORCEDBRICK_SLAB.get(), "Плита из доломитового кирпича");
        add(ModBlocks.REINFORCEDBRICK_STAIRS.get(), "Ступени из доломитового кирпича");
        add(ModItems.FIREBRICK.get(), "Огнеупорный кирпич");
        add(ModItems.REINFORCEDBRICK.get(), "Доломитовый кирпич");

        // Декоративные блоки
        add(ModBlocks.CRATE.get(), "Ящик");
        add(ModBlocks.CRATE_AMMO.get(), "Ящик с патронами");
        add(ModBlocks.BEAM_BLOCK.get(), "Балка");
        add(ModBlocks.STEEL_PROPS.get(), "Стальные подпорки");
        add(ModBlocks.DECO_STEEL.get(), "Декоративный стальной блок");
        add(ModBlocks.DECO_STEEL_DARK.get(), "Тёмный декоративный стальной блок");
        add(ModBlocks.DECO_STEEL_SMOG.get(), "Закоптелый декоративный стальной блок");
        add(ModBlocks.DECO_LEAD.get(), "Декоративный свинцовый блок");
        add(ModBlocks.DECO_BEAM.get(), "Декоративный индустриальный блок");
        add(ModBlocks.DIRT_ROUGH.get(), "Грубая земля");
        add(ModBlocks.ROUND_LAMP.get(), "Круглая лампа");
        add(ModBlocks.MORY_BLOCK.get(), "Блок Мори");
        add(ModBlocks.ANTON_CHIGUR.get(), "Блок Антона Чигура");
        add(ModBlocks.MINERAL_BLOCK2.get(), "Декоративный блок из глубинного сапфира");
        add(ModBlocks.MINERAL_TILE.get(), "Плитка из глубинного сапфира");
        add(ModBlocks.DOLOMITE_TILE.get(), "Доломитовая плитка");
        add(ModBlocks.TILE_LIGHT.get(), "Асбестовая плитка");
        add(ModBlocks.SULFUR_TILE.get(), "Серная плитка");
        add(ModBlocks.SULFUR_BRICKS.get(), "Серные кирпичи");
        add(ModBlocks.NECROSIS_TEST.get(), "Тестовый блок Некроза");
        add(ModBlocks.NECROSIS_TEST2.get(), "Тестовый блок Некроза 2");
        add(ModBlocks.NECROSIS_TEST3.get(), "Тестовый блок Некроза 3");
        add(ModBlocks.NECROSIS_TEST4.get(), "Тестовый блок Некроза 4");
        add(ModBlocks.NECROSIS_PORTAL.get(), "Портал Некроза");
        add(ModBlocks.WASTE_LOG.get(), "Обугленное бревно");

        // Кинетика и валы
        add(ModBlocks.HAND_CRANK_BLOCK.get(), "Ручной привод");
        add(ModBlocks.SHAFT_LIGHT_IRON.get(), "Лёгкий железный вал");
        add(ModBlocks.SHAFT_MEDIUM_IRON.get(), "Средний железный вал");
        add(ModBlocks.SHAFT_HEAVY_IRON.get(), "Тяжёлый железный вал");
        add(ModBlocks.SHAFT_LIGHT_DURALUMIN.get(), "Лёгкий дюралюминиевый вал");
        add(ModBlocks.SHAFT_MEDIUM_DURALUMIN.get(), "Средний дюралюминиевый вал");
        add(ModBlocks.SHAFT_HEAVY_DURALUMIN.get(), "Тяжёлый дюралюминиевый вал");
        add(ModBlocks.SHAFT_LIGHT_STEEL.get(), "Лёгкий стальной вал");
        add(ModBlocks.SHAFT_MEDIUM_STEEL.get(), "Средний стальной вал");
        add(ModBlocks.SHAFT_HEAVY_STEEL.get(), "Тяжёлый стальной вал");
        add(ModBlocks.SHAFT_LIGHT_TITANIUM.get(), "Лёгкий титановый вал");
        add(ModBlocks.SHAFT_MEDIUM_TITANIUM.get(), "Средний титановый вал");
        add(ModBlocks.SHAFT_HEAVY_TITANIUM.get(), "Тяжёлый титановый вал");
        add(ModBlocks.SHAFT_LIGHT_TUNGSTEN_CARBIDE.get(), "Лёгкий вал из карбида вольфрама");
        add(ModBlocks.SHAFT_MEDIUM_TUNGSTEN_CARBIDE.get(), "Средний вал из карбида вольфрама");
        add(ModBlocks.SHAFT_HEAVY_TUNGSTEN_CARBIDE.get(), "Тяжёлый вал из карбида вольфрама");
        add(ModItems.BEVEL_GEAR.get(), "Коническая шестерня");
        add(ModItems.GEAR1_STEEL.get(), "Стальная шестерня (малая)");
        add(ModItems.GEAR2_STEEL.get(), "Стальная шестерня (средняя)");
        add(ModItems.PULLEY.get(), "Шкив");
        add(ModItems.COPPER_ROTOR.get(), "Медный ротор");
        add(ModItems.COPPER_COIL.get(), "Медная катушка статора");
        add(ModBlocks.BEARING_BLOCK.get(), "Подшипник");
        add(ModBlocks.MOTOR_ELECTRO.get(), "Электромотор");
        add(ModBlocks.TACHOMETER.get(), "Тахометр");
        add(ModBlocks.CLUTCH.get(), "Сцепление");
        add(ModItems.STEAM_ENGINE_ITEM.get(), "Паровой двигатель");
        add(ModBlocks.STATOR_BLOCK.get(), "Статор");

        // Бочки, баки и жидкости
        add(ModItems.CORRUPTED_BARREL_ITEM.get(), "Выжженная бочка");
        add(ModItems.LEAKING_BARREL_ITEM.get(), "Протекающая бочка");
        add(ModItems.IRON_BARREL_ITEM.get(), "Железная бочка");
        add(ModItems.STEEL_BARREL_ITEM.get(), "Стальная бочка");
        add(ModItems.LEAD_BARREL_ITEM.get(), "Свинцовая бочка");
        add(ModItems.INFINITE_FLUID_BARREL.get(), "Бесконечный жидкостный источник");
        add(ModBlocks.FUEL_TANK_SMALL.get(), "Малая жидкостная цистерна");
        add(ModBlocks.FUEL_TANK_BIG.get(), "Большая жидкостная цистерна");
        add(ModItems.BOILER_ITEM.get(), "Медный жидкостный бойлер");
        add(ModBlocks.LOW_PRESSURE_STEAM_CONDENSER.get(), "Конденсатор пара низкого давления");
        add(ModBlocks.WATER_PUMP_ITEM.get(), "Жидкостная помпа");
        add(ModItems.PROTECTOR_LEAD.get(), "Свинцовый протектор внутренних стенок");
        add(ModItems.PROTECTOR_STEEL.get(), "Стальной протектор внутренних стенок");
        add(ModItems.PROTECTOR_TUNGSTEN.get(), "Вольфрамовый протектор внутренних стенок");
        add(ModBlocks.BRONZE_FLUID_PIPE.get(), "Бронзовая жидкостная труба");
        add(ModBlocks.STEEL_FLUID_PIPE.get(), "Стальная жидкостная труба");
        add(ModBlocks.LEAD_FLUID_PIPE.get(), "Свинцовая жидкостная труба");
        add(ModBlocks.TUNGSTEN_FLUID_PIPE.get(), "Вольфрамовая жидкостная труба");
        add(ModBlocks.PAINTABLE_PIPE.get(), "Окрашиваемая жидкостная труба");

        // Конвейеры и хранилища
        add(ModBlocks.CONVEYOR_VSTAVSHIK.get(), "Конвейерный вставщик");
        add(ModBlocks.CONVEYOR_IZVLEKATEL.get(), "Конвейерный извлекатель");
        add(ModBlocks.CONVEYOR.get(), "Конвейер");
        add(ModBlocks.STEEL_STORAGE.get(), "Стальное хранилище");

        // Оружие и боеприпасы
        add(ModItems.CAST_PICKAXE_IRON.get(), "Литая железная кирка");
        add(ModItems.CAST_PICKAXE_STEEL.get(), "Литая стальная кирка");
        add(ModItems.GRAVITY_GRENADE.get(), "Грави-граната");
        add(ModBlocks.DET_MINER.get(), "Шахтёрский заряд");
        add(ModItems.DETONATOR.get(), "Детонатор");
        add(ModItems.MULTI_DETONATOR.get(), "Мульти-детонатор");
        add(ModItems.RANGE_DETONATOR.get(), "Детонатор дальнего действия");
        add(ModItems.MORY_LAH.get(), "Невообразимо подозрительное изделие обладающее силой тысяч Солнц");
        add(ModItems.GRENADE.get(), "Граната");
        add(ModItems.GRENADEHE.get(), "Фугасная граната");
        add(ModItems.GRENADEFIRE.get(), "Зажигательная граната");
        add(ModItems.GRENADESMART.get(), "УМная граната");
        add(ModItems.GRENADESLIME.get(), "Липкая граната");
        add(ModItems.GRENADE_IF.get(), "Ударная граната");
        add(ModItems.GRENADE_IF_HE.get(), "Фугасная ударная граната");
        add(ModItems.GRENADE_IF_SLIME.get(), "Ударная липкая граната");
        add(ModItems.GRENADE_IF_FIRE.get(), "Зажигательная ударная граната");
        add(ModItems.GRENADE_NUC.get(), "Водород-кремирующая граната");
        add(ModItems.TURRET_CHIP.get(), "Турельный боевой чип");
        add(ModItems.TURRET_LIGHT_PORTATIVE_PLACER.get(), "Портативная лёгкая десантная турель \'Нагваль\'");
        add(ModItems.MACHINEGUN.get(), "'А.П. 17'");
        add(ModBlocks.TROMBONE.get(), "Стационарная ракетная установка 'Тромбон'");
        add(ModItems.AMMO_TURRET.get(), "20-мм турельный боеприпас");
        add(ModItems.AMMO_TURRET_PIERCING.get(), "20-мм бронебойный боеприпас для турели");
        add(ModItems.AMMO_TURRET_HOLLOW.get(), "20-мм экспансивный боеприпас для турели");
        add(ModItems.AMMO_TURRET_FIRE.get(), "20-мм зажигательный боеприпас для турели");
        add(ModItems.AMMO_TURRET_RADIO.get(), "20-мм боеприпас для турели с радиовзрывателем");
        add(ModItems.MISSILE_100MM.get(), "100-мм ракета (малый заряд)");
        add(ModItems.MISSILE_100MM_HE.get(), "100-мм фугасная ракета");
        add(ModItems.MISSILE_100MM_FIRE.get(), "100-мм зажигательная ракета");

        // Ресурсы и материалы
        add(ModItems.IRON_PLATE.get(), "Железная пластина");
        add(ModItems.TITANIUM_PLATE.get(), "Титановая пластина");
        add(ModItems.STEEL_PLATE.get(), "Стальная пластина");
        add(ModItems.TUNGSTEN_PLATE.get(), "Вольфрамовая пластина");
        add(ModItems.LEAD_PLATE.get(), "Свинцовая пластина");
        add(ModItems.ALUMINUM_PLATE.get(), "Алюминиевая пластина");
        add(ModItems.INDUSTRIAL_COPPER_PLATE.get(), "Промышленномедная пластина");
        add(ModItems.GOLD_PLATE.get(), "Золотая пластина");
        add(ModItems.CAST_PICKAXE_IRON_BASE.get(), "Основа литой железной кирки");
        add(ModItems.CAST_PICKAXE_STEEL_BASE.get(), "Основа литой стальной кирки");
        add(ModItems.ROPE.get(), "Верёвка");
        add(ModItems.WOODEN_HANDLE.get(), "Деревянная рукоять");
        add(ModItems.FIRE_SMES.get(), "Огнеупорная смесь");
        add(ModItems.DOLOMITE_SMES.get(), "Доломитовая смесь");
        add(ModItems.CONGLOMERATE_CHUNK.get(), "Кусок конгломерата");
        add(ModItems.HARD_ROCK.get(), "Твёрдая порода");
        add(ModItems.DOLOMITE_CHUNK.get(), "Кусок доломита");
        add(ModItems.LIMESTONE_CHUNK.get(), "Кусок известняка");
        add(ModItems.BAUXITE_CHUNK.get(), "Кусок боксита");
        add(ModItems.ASBESTOS.get(), "Асбест");
        add(ModItems.CINNABAR.get(), "Киноварь");
        add(ModItems.LIGNITE.get(), "Лигнит");
        add(ModItems.FLUORITE.get(), "Флюорит");
        add(ModItems.SEQUESTRUM.get(), "Селитра");
        add(ModItems.SULFUR.get(), "Сера");
        add(ModItems.CONGLOMERATE_POWDER.get(), "Порошок конгломерата");
        add(ModItems.DOLOMITE_POWDER.get(), "Порошок доломита");
        add(ModItems.LIMESTONE_POWDER.get(), "Порошок известняка");
        add(ModItems.BAUXITE_POWDER.get(), "Порошок боксита");
        add(ModItems.FUEL_ASH.get(), "Топливный пепел");
        add(ModItems.TRASH.get(), "Мусор");
        add(ModItems.SLAG.get(), "Шлак");
        add(ModItems.BELT.get(), "Ремень");
        add(ModItems.WIRE_COIL.get(), "Катушка медного провода");
        add(ModItems.BEAM_PLACER.get(), "Установщик балок");
        add(ModItems.POKER.get(), "Кочерга");
        add(ModItems.SCREWDRIVER.get(), "Отвёртка");
        add(ModItems.CROWBAR.get(), "Монтировка");
        add(ModBlocks.LIGNITE_BLOCK.get(), "Блок лигнита");

        // Природа и руды
        add(ModBlocks.ASBESOTS_ORE.get(), "Асбестовая руда");
        add(ModBlocks.LIGNITE_ORE.get(), "Лигнитовая руда");
        add(ModBlocks.CINNABAR_ORE.get(), "Киноварная руда");
        add(ModBlocks.CINNABAR_ORE_DEEPSLATE.get(), "Киновароносный глубинный сланец");
        add(ModBlocks.FLUORITE_ORE.get(), "Флюоритовая руда");
        add(ModBlocks.FLUORITE_ORE_DEEPSLATE.get(), "Флюоритоносный глубинный сланец");
        add(ModBlocks.SEQUESTRUM_ORE.get(), "Селитровая руда");
        add(ModBlocks.SEQUESTRUM_ORE_DEEPSLATE.get(), "Селитроносный глубинный сланец");
        add(ModBlocks.SULFUR_ORE.get(), "Серная руда");
        add(ModBlocks.SULFUR_ORE_DEEPSLATE.get(), "Серноносный глубинный сланец");
        add(ModBlocks.CONGLOMERATE.get(), "Конгломерат");
        add(ModBlocks.DEPLETED_CONGLOMERATE.get(), "Истощённый конгломерат");
        add(ModBlocks.DOLOMITE.get(), "Неочищенная доломитовая залежа");
        add(ModBlocks.LIMESTONE.get(), "Неочищенная известняковая залежа");
        add(ModBlocks.SULFUR_CLUSTER.get(), "Неочищенная серная залежа");
        add(ModBlocks.BAUXITE.get(), "Неочищенная бокситовая залежа");
        add(ModBlocks.MINERAL1.get(), "Сапфироносный кластер");
        add(ModBlocks.MINERAL3.get(), "Глубинный сапфироносный кластер");
        add(ModBlocks.BASALT_ROUGH.get(), "Грубый базальт");

        // Яйца призыва
        add(ModItems.DEPTH_WORM_SPAWN_EGG.get(), "Яйцо призыва глубинного червя");
        add(ModItems.DEPTH_WORM_BRUTAL_SPAWN_EGG.get(), "Яйцо призыва брутального глубинного червя");
        add(ModItems.GRENADIER_ZOMBIE_SPAWN_EGG.get(), "Яйцо призыва зомби-гренадёра");

        // Сущности
        add("entity.trd.turret_light", "Лёгкая турель");
        add("entity.trd.turret_light_linked", "Связанная лёгкая турель");
        add("entity.trd.turret_bullet", "Пуля турели");
        add("entity.trd.depth_worm", "Глубинный червь");
        add("entity.trd.grenade_projectile", "Граната");
        add("entity.trd.grenadehe_projectile", "Фугасная граната");
        add("entity.trd.grenadefire_projectile", "Зажигательная граната");
        add("entity.trd.grenadesmart_projectile", "Умная граната");
        add("entity.trd.grenadeslime_projectile", "Слизевая граната");
        add("entity.trd.grenade_if_projectile", "Ударная граната");
        add("entity.trd.grenade_if_fire_projectile", "Зажигательная ударная граната");
        add("entity.trd.grenade_if_slime_projectile", "Ударная слизевая граната");
        add("entity.trd.grenade_if_he_projectile", "Фугасная ударная граната");
        add("entity.trd.grenade_nuc_projectile", "Ядерная граната");

        // Жидкостный идентификатор
        add("item.trd.fluid_identifier", "Жидкостный идентификатор");
        add("message.trd.selected_fluid", "Выбрано");
        add("tooltip.trd.no_fluid", "Жидкость не выбрана");
        add("tooltip.trd.shaft_material", "Материал");
        add("tooltip.trd.max_speed", "Макс. скорость");
        add("tooltip.trd.max_torque", "Макс. момент");
        add("tooltip.trd.inertia", "Инерция");
        add("message.trd.too_far_from_support", "Слишком длинный пролёт! Макс. расстояние от опоры для этого диаметра: %s бл.");

        // Fluids
        addFluidTranslations("hydrogen_peroxide", "Пероксид водорода", null, "Hydrogen Peroxide");
        addFluidTranslations("sulfuric_acid", "Серная кислота", null, "Sulfuric Acid");
        addFluidTranslations("natural_gas", "Природный газ", null, "Natural Gas");
        addFluidTranslations("steam", "Пар", null, "Steam");
        addFluidTranslations("low_pressure_steam", "Пар низкого давления", null, "Low Pressure Steam");
        addFluidTranslations("water", "Вода", "Вода", "Water");
        addFluidTranslations("lava", "Лава", "Лава", "Lava");
        addFluidTranslations("mercury", "Ртуть", "Ртуть", "Mercury");
    }
}
