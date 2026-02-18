package me.nebula.orbit.mechanic

import me.nebula.ether.utils.logging.logger
import me.nebula.ether.utils.module.ModuleRegistry
import me.nebula.orbit.mechanic.anvil.AnvilModule
import me.nebula.orbit.mechanic.armor.ArmorModule
import me.nebula.orbit.mechanic.beacon.BeaconModule
import me.nebula.orbit.mechanic.bed.BedModule
import me.nebula.orbit.mechanic.block.BlockModule
import me.nebula.orbit.mechanic.breeding.BreedingModule
import me.nebula.orbit.mechanic.combat.CombatModule
import me.nebula.orbit.mechanic.composter.ComposterModule
import me.nebula.orbit.mechanic.container.ContainerModule
import me.nebula.orbit.mechanic.crafting.CraftingModule
import me.nebula.orbit.mechanic.crop.CropModule
import me.nebula.orbit.mechanic.crossbow.CrossbowModule
import me.nebula.orbit.mechanic.daynight.DayNightModule
import me.nebula.orbit.mechanic.death.DeathModule
import me.nebula.orbit.mechanic.door.DoorModule
import me.nebula.orbit.mechanic.drowning.DrowningModule
import me.nebula.orbit.mechanic.elytra.ElytraModule
import me.nebula.orbit.mechanic.enchanting.EnchantingModule
import me.nebula.orbit.mechanic.enderpearl.EnderPearlModule
import me.nebula.orbit.mechanic.experience.ExperienceModule
import me.nebula.orbit.mechanic.explosion.ExplosionModule
import me.nebula.orbit.mechanic.falldamage.FallDamageModule
import me.nebula.orbit.mechanic.fire.FireDamageModule
import me.nebula.orbit.mechanic.fishing.FishingModule
import me.nebula.orbit.mechanic.food.FoodModule
import me.nebula.orbit.mechanic.furnacesmelting.FurnaceSmeltingModule
import me.nebula.orbit.mechanic.itemdrop.ItemDropModule
import me.nebula.orbit.mechanic.lever.LeverModule
import me.nebula.orbit.mechanic.naturalspawn.NaturalSpawnModule
import me.nebula.orbit.mechanic.noteblock.NoteBlockModule
import me.nebula.orbit.mechanic.potion.PotionModule
import me.nebula.orbit.mechanic.projectile.ProjectileModule
import me.nebula.orbit.mechanic.respawnanchor.RespawnAnchorModule
import me.nebula.orbit.mechanic.shield.ShieldModule
import me.nebula.orbit.mechanic.sign.SignModule
import me.nebula.orbit.mechanic.snowball.SnowballModule
import me.nebula.orbit.mechanic.sprint.SprintModule
import me.nebula.orbit.mechanic.trading.TradingModule
import me.nebula.orbit.mechanic.trident.TridentModule
import me.nebula.orbit.mechanic.voiddamage.VoidDamageModule
import me.nebula.orbit.mechanic.armorstand.ArmorStandModule
import me.nebula.orbit.mechanic.beehive.BeehiveModule
import me.nebula.orbit.mechanic.bell.BellModule
import me.nebula.orbit.mechanic.boat.BoatModule
import me.nebula.orbit.mechanic.campfire.CampfireModule
import me.nebula.orbit.mechanic.candle.CandleModule
import me.nebula.orbit.mechanic.cartographytable.CartographyTableModule
import me.nebula.orbit.mechanic.cauldron.CauldronModule
import me.nebula.orbit.mechanic.conduit.ConduitModule
import me.nebula.orbit.mechanic.copperoxidation.CopperOxidationModule
import me.nebula.orbit.mechanic.dispenser.DispenserModule
import me.nebula.orbit.mechanic.enderchest.EnderChestModule
import me.nebula.orbit.mechanic.grindstone.GrindstoneModule
import me.nebula.orbit.mechanic.hopper.HopperModule
import me.nebula.orbit.mechanic.itemframe.ItemFrameModule
import me.nebula.orbit.mechanic.jukebox.JukeboxModule
import me.nebula.orbit.mechanic.ladder.LadderModule
import me.nebula.orbit.mechanic.lectern.LecternModule
import me.nebula.orbit.mechanic.lightningrod.LightningRodModule
import me.nebula.orbit.mechanic.loom.LoomModule
import me.nebula.orbit.mechanic.painting.PaintingModule
import me.nebula.orbit.mechanic.scaffolding.ScaffoldingModule
import me.nebula.orbit.mechanic.shulkerbox.ShulkerBoxModule
import me.nebula.orbit.mechanic.smithingtable.SmithingTableModule
import me.nebula.orbit.mechanic.stonecutter.StonecutterModule
import me.nebula.orbit.mechanic.turtleegg.TurtleEggModule
import me.nebula.orbit.mechanic.amethyst.AmethystModule
import me.nebula.orbit.mechanic.armortrim.ArmorTrimModule
import me.nebula.orbit.mechanic.banner.BannerModule
import me.nebula.orbit.mechanic.barrel.BarrelModule
import me.nebula.orbit.mechanic.blastfurnace.BlastFurnaceModule
import me.nebula.orbit.mechanic.brewingstand.BrewingStandModule
import me.nebula.orbit.mechanic.chain.ChainModule
import me.nebula.orbit.mechanic.chiseledbookshelf.ChiseledBookshelfModule
import me.nebula.orbit.mechanic.chorusflower.ChorusFlowerGrowthModule
import me.nebula.orbit.mechanic.decoratedpot.DecoratedPotModule
import me.nebula.orbit.mechanic.dropper.DropperModule
import me.nebula.orbit.mechanic.chorus.ChorusModule
import me.nebula.orbit.mechanic.depthstrider.DepthStriderModule
import me.nebula.orbit.mechanic.endcrystal.EndCrystalModule
import me.nebula.orbit.mechanic.endportal.EndPortalModule
import me.nebula.orbit.mechanic.endrod.EndRodModule
import me.nebula.orbit.mechanic.fletchingtable.FletchingTableModule
import me.nebula.orbit.mechanic.glasspane.GlassPaneModule
import me.nebula.orbit.mechanic.glowlichen.GlowLichenModule
import me.nebula.orbit.mechanic.hangingsign.HangingSignModule
import me.nebula.orbit.mechanic.honeyblock.HoneyBlockModule
import me.nebula.orbit.mechanic.irongolem.IronGolemModule
import me.nebula.orbit.mechanic.kelp.KelpModule
import me.nebula.orbit.mechanic.lantern.LanternModule
import me.nebula.orbit.mechanic.lilypad.LilyPadModule
import me.nebula.orbit.mechanic.magmablock.MagmaBlockModule
import me.nebula.orbit.mechanic.moss.MossModule
import me.nebula.orbit.mechanic.observer.ObserverModule
import me.nebula.orbit.mechanic.pointeddripstonewater.PointedDripstoneWaterModule
import me.nebula.orbit.mechanic.rail.RailModule
import me.nebula.orbit.mechanic.repeater.RepeaterModule
import me.nebula.orbit.mechanic.respiration.RespirationModule
import me.nebula.orbit.mechanic.sculkshrieker.SculkShriekerModule
import me.nebula.orbit.mechanic.sealantern.SeaLanternModule
import me.nebula.orbit.mechanic.slab.SlabModule
import me.nebula.orbit.mechanic.smoker.SmokerModule
import me.nebula.orbit.mechanic.soulspeed.SoulSpeedModule
import me.nebula.orbit.mechanic.spawner.SpawnerModule
import me.nebula.orbit.mechanic.sponge.SpongeModule
import me.nebula.orbit.mechanic.stairs.StairsModule
import me.nebula.orbit.mechanic.suspicioussand.SuspiciousSandModule
import me.nebula.orbit.mechanic.tintedglass.TintedGlassModule
import me.nebula.orbit.mechanic.torch.TorchModule
import me.nebula.orbit.mechanic.trapdoor.TrapdoorModule
import me.nebula.orbit.mechanic.witherrose.WitherRoseModule
import me.nebula.orbit.mechanic.bamboo.BambooModule
import me.nebula.orbit.mechanic.cake.CakeModule
import me.nebula.orbit.mechanic.bonemeal.BoneMealModule
import me.nebula.orbit.mechanic.cactus.CactusModule
import me.nebula.orbit.mechanic.coral.CoralModule
import me.nebula.orbit.mechanic.daylightdetector.DaylightDetectorModule
import me.nebula.orbit.mechanic.dragonbreath.DragonBreathModule
import me.nebula.orbit.mechanic.dripstone.DripstoneModule
import me.nebula.orbit.mechanic.fence.FenceModule
import me.nebula.orbit.mechanic.firework.FireworkModule
import me.nebula.orbit.mechanic.flowerpot.FlowerPotModule
import me.nebula.orbit.mechanic.frostwalker.FrostWalkerModule
import me.nebula.orbit.mechanic.gravity.GravityModule
import me.nebula.orbit.mechanic.ice.IceModule
import me.nebula.orbit.mechanic.itemrepair.ItemRepairModule
import me.nebula.orbit.mechanic.map.MapModule
import me.nebula.orbit.mechanic.minecart.MinecartModule
import me.nebula.orbit.mechanic.mushroom.MushroomModule
import me.nebula.orbit.mechanic.netherportal.NetherPortalModule
import me.nebula.orbit.mechanic.piston.PistonModule
import me.nebula.orbit.mechanic.powdersnow.PowderSnowModule
import me.nebula.orbit.mechanic.redstonewire.RedstoneWireModule
import me.nebula.orbit.mechanic.skulksensor.SkulkSensorModule
import me.nebula.orbit.mechanic.snowlayer.SnowLayerModule
import me.nebula.orbit.mechanic.sugarcane.SugarCaneModule
import me.nebula.orbit.mechanic.sweetberry.SweetBerryModule
import me.nebula.orbit.mechanic.targetblock.TargetBlockModule
import me.nebula.orbit.mechanic.tripwire.TripwireModule
import me.nebula.orbit.mechanic.vinegrowth.VineGrowthModule
import me.nebula.orbit.mechanic.totem.TotemModule
import me.nebula.orbit.mechanic.weather.WeatherModule
import me.nebula.orbit.mechanic.waterflow.WaterFlowModule
import me.nebula.orbit.mechanic.lavaflow.LavaFlowModule
import me.nebula.orbit.mechanic.bubblecolumn.BubbleColumnModule
import me.nebula.orbit.mechanic.itemdurability.ItemDurabilityModule
import me.nebula.orbit.mechanic.doublechest.DoubleChestModule
import me.nebula.orbit.mechanic.cobweb.CobwebModule
import me.nebula.orbit.mechanic.hoetilling.HoeTillingModule
import me.nebula.orbit.mechanic.axestripping.AxeStrippingModule
import me.nebula.orbit.mechanic.wither.WitherModule
import me.nebula.orbit.mechanic.shovelpathing.ShovelPathingModule
import me.nebula.orbit.mechanic.protectionenchant.ProtectionEnchantModule
import me.nebula.orbit.mechanic.fireaspect.FireAspectModule
import me.nebula.orbit.mechanic.thorns.ThornsModule
import me.nebula.orbit.mechanic.mending.MendingModule
import me.nebula.orbit.mechanic.silktouch.SilkTouchModule
import me.nebula.orbit.mechanic.sweeping.SweepingModule
import me.nebula.orbit.mechanic.sharpness.SharpnessModule
import me.nebula.orbit.mechanic.knockbackenchant.KnockbackEnchantModule
import me.nebula.orbit.mechanic.power.PowerModule
import me.nebula.orbit.mechanic.flame.FlameModule
import me.nebula.orbit.mechanic.infinity.InfinityModule
import me.nebula.orbit.mechanic.fortune.FortuneModule
import me.nebula.orbit.mechanic.efficiency.EfficiencyModule
import me.nebula.orbit.mechanic.unbreaking.UnbreakingModule
import me.nebula.orbit.mechanic.looting.LootingModule
import me.nebula.orbit.mechanic.loyalty.LoyaltyModule
import me.nebula.orbit.mechanic.channeling.ChannelingModule
import me.nebula.orbit.mechanic.quickcharge.QuickChargeModule
import me.nebula.orbit.mechanic.windcharge.WindChargeModule
import me.nebula.orbit.mechanic.mace.MaceModule
import me.nebula.orbit.mechanic.trialspawner.TrialSpawnerModule
import me.nebula.orbit.mechanic.sculkcatalyst.SculkCatalystModule
import me.nebula.orbit.mechanic.copperbulb.CopperBulbModule
import me.nebula.orbit.mechanic.crafter.CrafterModule
import me.nebula.orbit.mechanic.vault.VaultModule
import me.nebula.orbit.mechanic.ominousbottle.OminousBottleModule
import me.nebula.orbit.mechanic.itemframerotation.ItemFrameRotationModule
import me.nebula.orbit.mechanic.suspiciousstew.SuspiciousStewModule
import me.nebula.orbit.mechanic.sculkvein.SculkVeinModule
import me.nebula.orbit.mechanic.breeze.BreezeModule
import me.nebula.orbit.mechanic.sporeblossom.SporeBlossomModule
import me.nebula.orbit.mechanic.sculksensorcalibrated.SculkSensorCalibratedModule
import me.nebula.orbit.mechanic.brushable.BrushableModule
import me.nebula.orbit.mechanic.frog.FrogModule
import me.nebula.orbit.mechanic.allay.AllayModule
import me.nebula.orbit.mechanic.warden.WardenModule
import me.nebula.orbit.mechanic.smeltingdrop.SmeltingDropModule
import me.nebula.orbit.mechanic.netherwart.NetherWartModule
import me.nebula.orbit.mechanic.sniffer.SnifferModule
import me.nebula.orbit.mechanic.camel.CamelModule
import me.nebula.orbit.mechanic.hangingblock.HangingBlockModule
import me.nebula.orbit.mechanic.mud.MudModule
import me.nebula.orbit.mechanic.mangrove.MangroveModule
import me.nebula.orbit.mechanic.cherry.CherryModule
import me.nebula.orbit.mechanic.pitcherplant.PitcherPlantModule
import me.nebula.orbit.mechanic.torchflower.TorchflowerModule
import me.nebula.orbit.mechanic.wax.WaxModule
import me.nebula.orbit.mechanic.azalea.AzaleaModule
import me.nebula.orbit.mechanic.bamboomosaic.BambooMosaicModule
import me.nebula.orbit.mechanic.recoverycompass.RecoveryCompassModule
import me.nebula.orbit.mechanic.echoshard.EchoShardModule
import me.nebula.orbit.mechanic.slimeblockbounce.SlimeBlockBounceModule
import me.nebula.orbit.mechanic.noteblockinstrument.NoteBlockInstrumentModule
import me.nebula.orbit.mechanic.dragonegg.DragonEggModule
import me.nebula.orbit.mechanic.respawnmodule.RespawnModule
import me.nebula.orbit.mechanic.headdrop.HeadDropModule
import me.nebula.orbit.mechanic.compasstracking.CompassTrackingModule
import me.nebula.orbit.mechanic.spyglass.SpyglassModule
import me.nebula.orbit.mechanic.goathorn.GoatHornModule
import me.nebula.orbit.mechanic.bundle.BundleModule
import me.nebula.orbit.mechanic.amethystgrowth.AmethystGrowthModule
import me.nebula.orbit.mechanic.lightblock.LightBlockModule
import me.nebula.orbit.mechanic.structurevoid.StructureVoidModule
import me.nebula.orbit.mechanic.barrierblock.BarrierBlockModule
import me.nebula.orbit.mechanic.commandblock.CommandBlockModule
import me.nebula.orbit.mechanic.skull.SkullModule
import me.nebula.orbit.mechanic.netheritedamage.NetheriteDamageModule
import me.nebula.orbit.mechanic.sweetberrybush.SweetBerryBushModule
import me.nebula.orbit.mechanic.powdersnowfreezing.PowderSnowFreezingModule
import me.nebula.orbit.mechanic.drownedconversion.DrownedConversionModule
import me.nebula.orbit.mechanic.piglinbarter.PiglinBarterModule
import me.nebula.orbit.mechanic.endermanpickup.EndermanPickupModule
import me.nebula.orbit.mechanic.creeperexplosion.CreeperExplosionModule
import me.nebula.orbit.mechanic.skeletonshoot.SkeletonShootModule
import me.nebula.orbit.mechanic.zombieattack.ZombieAttackModule
import me.nebula.orbit.mechanic.witherskeleton.WitherSkeletonModule
import me.nebula.orbit.mechanic.blazebehavior.BlazeBehaviorModule
import me.nebula.orbit.mechanic.ghastbehavior.GhastBehaviorModule
import me.nebula.orbit.mechanic.slimesplit.SlimeSplitModule
import me.nebula.orbit.mechanic.phantom.PhantomModule
import me.nebula.orbit.mechanic.vex.VexModule
import me.nebula.orbit.mechanic.spiderclimb.SpiderClimbModule
import me.nebula.orbit.mechanic.guardianbeam.GuardianBeamModule
import me.nebula.orbit.mechanic.shulkerbullet.ShulkerBulletModule
import me.nebula.orbit.mechanic.witchpotion.WitchPotionModule
import me.nebula.orbit.mechanic.evokerfangs.EvokerFangsModule
import me.nebula.orbit.mechanic.ravager.RavagerModule
import me.nebula.orbit.mechanic.pillagercrossbow.PillagerCrossbowModule
import me.nebula.orbit.mechanic.snowgolem.SnowGolemModule
import me.nebula.orbit.mechanic.foxsleep.FoxSleepModule
import me.nebula.orbit.mechanic.parrotdance.ParrotDanceModule
import me.nebula.orbit.mechanic.dolphingrace.DolphinGraceModule
import me.nebula.orbit.mechanic.beepollination.BeePollinationModule
import me.nebula.orbit.mechanic.silverfishburrow.SilverfishBurrowModule
import me.nebula.orbit.mechanic.catcreeper.CatCreeperModule
import me.nebula.orbit.mechanic.turtlescute.TurtleScuteModule
import me.nebula.orbit.mechanic.villagerprofession.VillagerProfessionModule
import me.nebula.orbit.mechanic.wanderingtrader.WanderingTraderModule
import me.nebula.orbit.mechanic.raidsystem.RaidSystemModule
import me.nebula.orbit.mechanic.elderguardian.ElderGuardianModule
import me.nebula.orbit.mechanic.zombiesiege.ZombieSiegeModule
import me.nebula.orbit.mechanic.piglinaggro.PiglinAggroModule
import me.nebula.orbit.mechanic.hoglinbehavior.HoglinBehaviorModule
import me.nebula.orbit.mechanic.striderbehavior.StriderBehaviorModule
import me.nebula.orbit.mechanic.wolftaming.WolfTamingModule
import me.nebula.orbit.mechanic.cattaming.CatTamingModule
import me.nebula.orbit.mechanic.horsetaming.HorseTamingModule
import me.nebula.orbit.mechanic.llamabehavior.LlamaBehaviorModule
import me.nebula.orbit.mechanic.pandabehavior.PandaBehaviorModule
import me.nebula.orbit.mechanic.polarbear.PolarBearModule
import me.nebula.orbit.mechanic.axolotlbehavior.AxolotlBehaviorModule
import me.nebula.orbit.module.OrbitModule

object MechanicLoader {

    private val logger = logger("MechanicLoader")

    private val ALL_MECHANICS: Map<String, () -> OrbitModule> = mapOf(
        "combat" to ::CombatModule,
        "fall-damage" to ::FallDamageModule,
        "block" to ::BlockModule,
        "food" to ::FoodModule,
        "item-drop" to ::ItemDropModule,
        "death" to ::DeathModule,
        "experience" to ::ExperienceModule,
        "armor" to ::ArmorModule,
        "projectile" to ::ProjectileModule,
        "void-damage" to ::VoidDamageModule,
        "fire" to ::FireDamageModule,
        "drowning" to ::DrowningModule,
        "sprint" to ::SprintModule,
        "weather" to ::WeatherModule,
        "day-night" to ::DayNightModule,
        "bed" to ::BedModule,
        "explosion" to ::ExplosionModule,
        "natural-spawn" to ::NaturalSpawnModule,
        "container" to ::ContainerModule,
        "crafting" to ::CraftingModule,
        "potion" to ::PotionModule,
        "crop" to ::CropModule,
        "fishing" to ::FishingModule,
        "anvil" to ::AnvilModule,
        "breeding" to ::BreedingModule,
        "beacon" to ::BeaconModule,
        "enchanting" to ::EnchantingModule,
        "respawn-anchor" to ::RespawnAnchorModule,
        "trading" to ::TradingModule,
        "door" to ::DoorModule,
        "shield" to ::ShieldModule,
        "enderpearl" to ::EnderPearlModule,
        "snowball" to ::SnowballModule,
        "elytra" to ::ElytraModule,
        "trident" to ::TridentModule,
        "crossbow" to ::CrossbowModule,
        "lever" to ::LeverModule,
        "noteblock" to ::NoteBlockModule,
        "composter" to ::ComposterModule,
        "sign" to ::SignModule,
        "furnace-smelting" to ::FurnaceSmeltingModule,
        "armor-stand" to ::ArmorStandModule,
        "painting" to ::PaintingModule,
        "lectern" to ::LecternModule,
        "jukebox" to ::JukeboxModule,
        "cauldron" to ::CauldronModule,
        "item-frame" to ::ItemFrameModule,
        "campfire" to ::CampfireModule,
        "grindstone" to ::GrindstoneModule,
        "stonecutter" to ::StonecutterModule,
        "loom" to ::LoomModule,
        "cartography-table" to ::CartographyTableModule,
        "smithing-table" to ::SmithingTableModule,
        "ender-chest" to ::EnderChestModule,
        "bell" to ::BellModule,
        "candle" to ::CandleModule,
        "shulker-box" to ::ShulkerBoxModule,
        "scaffolding" to ::ScaffoldingModule,
        "boat" to ::BoatModule,
        "ladder" to ::LadderModule,
        "dispenser" to ::DispenserModule,
        "hopper" to ::HopperModule,
        "conduit" to ::ConduitModule,
        "copper-oxidation" to ::CopperOxidationModule,
        "turtle-egg" to ::TurtleEggModule,
        "beehive" to ::BeehiveModule,
        "lightning-rod" to ::LightningRodModule,
        "gravity" to ::GravityModule,
        "bonemeal" to ::BoneMealModule,
        "piston" to ::PistonModule,
        "redstone-wire" to ::RedstoneWireModule,
        "minecart" to ::MinecartModule,
        "armor-trim" to ::ArmorTrimModule,
        "firework" to ::FireworkModule,
        "snow-layer" to ::SnowLayerModule,
        "ice" to ::IceModule,
        "cactus" to ::CactusModule,
        "vine-growth" to ::VineGrowthModule,
        "bamboo" to ::BambooModule,
        "sugarcane" to ::SugarCaneModule,
        "mushroom" to ::MushroomModule,
        "coral" to ::CoralModule,
        "dragon-breath" to ::DragonBreathModule,
        "wither" to ::WitherModule,
        "cake" to ::CakeModule,
        "totem" to ::TotemModule,
        "flower-pot" to ::FlowerPotModule,
        "frost-walker" to ::FrostWalkerModule,
        "nether-portal" to ::NetherPortalModule,
        "fence" to ::FenceModule,
        "item-repair" to ::ItemRepairModule,
        "map" to ::MapModule,
        "daylight-detector" to ::DaylightDetectorModule,
        "target-block" to ::TargetBlockModule,
        "skulk-sensor" to ::SkulkSensorModule,
        "dripstone" to ::DripstoneModule,
        "powder-snow" to ::PowderSnowModule,
        "sweet-berry" to ::SweetBerryModule,
        "tripwire" to ::TripwireModule,
        "amethyst" to ::AmethystModule,
        "end-portal" to ::EndPortalModule,
        "brewing-stand" to ::BrewingStandModule,
        "banner" to ::BannerModule,
        "spawner" to ::SpawnerModule,
        "chorus" to ::ChorusModule,
        "honey-block" to ::HoneyBlockModule,
        "magma-block" to ::MagmaBlockModule,
        "sponge" to ::SpongeModule,
        "observer" to ::ObserverModule,
        "repeater" to ::RepeaterModule,
        "soul-speed" to ::SoulSpeedModule,
        "depth-strider" to ::DepthStriderModule,
        "kelp" to ::KelpModule,
        "end-crystal" to ::EndCrystalModule,
        "iron-golem" to ::IronGolemModule,
        "slab" to ::SlabModule,
        "stairs" to ::StairsModule,
        "trapdoor" to ::TrapdoorModule,
        "glass-pane" to ::GlassPaneModule,
        "rail" to ::RailModule,
        "torch" to ::TorchModule,
        "end-rod" to ::EndRodModule,
        "chain" to ::ChainModule,
        "barrel" to ::BarrelModule,
        "smoker" to ::SmokerModule,
        "blast-furnace" to ::BlastFurnaceModule,
        "fletching-table" to ::FletchingTableModule,
        "lantern" to ::LanternModule,
        "chorus-flower-growth" to ::ChorusFlowerGrowthModule,
        "wither-rose" to ::WitherRoseModule,
        "dropper" to ::DropperModule,
        "lily-pad" to ::LilyPadModule,
        "sea-lantern" to ::SeaLanternModule,
        "glow-lichen" to ::GlowLichenModule,
        "moss" to ::MossModule,
        "sculk-shrieker" to ::SculkShriekerModule,
        "decorated-pot" to ::DecoratedPotModule,
        "chiseled-bookshelf" to ::ChiseledBookshelfModule,
        "hanging-sign" to ::HangingSignModule,
        "suspicious-sand" to ::SuspiciousSandModule,
        "pointed-dripstone-water" to ::PointedDripstoneWaterModule,
        "respiration" to ::RespirationModule,
        "tinted-glass" to ::TintedGlassModule,
        "water-flow" to ::WaterFlowModule,
        "lava-flow" to ::LavaFlowModule,
        "bubble-column" to ::BubbleColumnModule,
        "item-durability" to ::ItemDurabilityModule,
        "double-chest" to ::DoubleChestModule,
        "cobweb" to ::CobwebModule,
        "hoe-tilling" to ::HoeTillingModule,
        "axe-stripping" to ::AxeStrippingModule,
        "shovel-pathing" to ::ShovelPathingModule,
        "protection-enchant" to ::ProtectionEnchantModule,
        "fire-aspect" to ::FireAspectModule,
        "thorns" to ::ThornsModule,
        "mending" to ::MendingModule,
        "silk-touch" to ::SilkTouchModule,
        "sweeping" to ::SweepingModule,
        "sharpness" to ::SharpnessModule,
        "knockback-enchant" to ::KnockbackEnchantModule,
        "power" to ::PowerModule,
        "flame" to ::FlameModule,
        "infinity" to ::InfinityModule,
        "fortune" to ::FortuneModule,
        "efficiency" to ::EfficiencyModule,
        "unbreaking" to ::UnbreakingModule,
        "looting" to ::LootingModule,
        "loyalty" to ::LoyaltyModule,
        "channeling" to ::ChannelingModule,
        "quick-charge" to ::QuickChargeModule,
        "wind-charge" to ::WindChargeModule,
        "mace" to ::MaceModule,
        "trial-spawner" to ::TrialSpawnerModule,
        "sculk-catalyst" to ::SculkCatalystModule,
        "copper-bulb" to ::CopperBulbModule,
        "crafter" to ::CrafterModule,
        "vault" to ::VaultModule,
        "ominous-bottle" to ::OminousBottleModule,
        "item-frame-rotation" to ::ItemFrameRotationModule,
        "suspicious-stew" to ::SuspiciousStewModule,
        "sculk-vein" to ::SculkVeinModule,
        "breeze" to ::BreezeModule,
        "spore-blossom" to ::SporeBlossomModule,
        "sculk-sensor-calibrated" to ::SculkSensorCalibratedModule,
        "brushable" to ::BrushableModule,
        "frog" to ::FrogModule,
        "allay" to ::AllayModule,
        "warden" to ::WardenModule,
        "smelting-drop" to ::SmeltingDropModule,
        "nether-wart" to ::NetherWartModule,
        "sniffer" to ::SnifferModule,
        "camel" to ::CamelModule,
        "hanging-block" to ::HangingBlockModule,
        "mud" to ::MudModule,
        "mangrove" to ::MangroveModule,
        "cherry" to ::CherryModule,
        "pitcher-plant" to ::PitcherPlantModule,
        "torchflower" to ::TorchflowerModule,
        "wax" to ::WaxModule,
        "azalea" to ::AzaleaModule,
        "bamboo-mosaic" to ::BambooMosaicModule,
        "recovery-compass" to ::RecoveryCompassModule,
        "echo-shard" to ::EchoShardModule,
        "netherite-damage" to ::NetheriteDamageModule,
        "sweet-berry-bush" to ::SweetBerryBushModule,
        "powder-snow-freezing" to ::PowderSnowFreezingModule,
        "drowned-conversion" to ::DrownedConversionModule,
        "piglin-barter" to ::PiglinBarterModule,
        "enderman-pickup" to ::EndermanPickupModule,
        "creeper-explosion" to ::CreeperExplosionModule,
        "skeleton-shoot" to ::SkeletonShootModule,
        "zombie-attack" to ::ZombieAttackModule,
        "wither-skeleton" to ::WitherSkeletonModule,
        "blaze-behavior" to ::BlazeBehaviorModule,
        "ghast-behavior" to ::GhastBehaviorModule,
        "slime-split" to ::SlimeSplitModule,
        "phantom" to ::PhantomModule,
        "vex" to ::VexModule,
        "spider-climb" to ::SpiderClimbModule,
        "guardian-beam" to ::GuardianBeamModule,
        "shulker-bullet" to ::ShulkerBulletModule,
        "witch-potion" to ::WitchPotionModule,
        "evoker-fangs" to ::EvokerFangsModule,
        "ravager" to ::RavagerModule,
        "pillager-crossbow" to ::PillagerCrossbowModule,
        "snow-golem" to ::SnowGolemModule,
        "fox-sleep" to ::FoxSleepModule,
        "parrot-dance" to ::ParrotDanceModule,
        "dolphin-grace" to ::DolphinGraceModule,
        "bee-pollination" to ::BeePollinationModule,
        "silverfish-burrow" to ::SilverfishBurrowModule,
        "cat-creeper" to ::CatCreeperModule,
        "turtle-scute" to ::TurtleScuteModule,
        "villager-profession" to ::VillagerProfessionModule,
        "wandering-trader" to ::WanderingTraderModule,
        "raid-system" to ::RaidSystemModule,
        "elder-guardian" to ::ElderGuardianModule,
        "zombie-siege" to ::ZombieSiegeModule,
        "piglin-aggro" to ::PiglinAggroModule,
        "hoglin-behavior" to ::HoglinBehaviorModule,
        "strider-behavior" to ::StriderBehaviorModule,
        "wolf-taming" to ::WolfTamingModule,
        "cat-taming" to ::CatTamingModule,
        "horse-taming" to ::HorseTamingModule,
        "llama-behavior" to ::LlamaBehaviorModule,
        "panda-behavior" to ::PandaBehaviorModule,
        "polar-bear" to ::PolarBearModule,
        "axolotl-behavior" to ::AxolotlBehaviorModule,
    )

    fun load(modules: ModuleRegistry, gameMode: String?, mechanics: String?) {
        val names = resolveMechanicNames(gameMode, mechanics)
        if (names.isEmpty()) {
            logger.info { "No mechanics to load" }
            return
        }
        logger.info { "Loading mechanics: ${names.joinToString()}" }
        names.forEach { name ->
            modules.register(requireNotNull(ALL_MECHANICS[name]) { "Unknown mechanic: $name" }())
        }
        names.forEach { name -> modules.enable(name) }
    }

    private fun resolveMechanicNames(gameMode: String?, mechanics: String?): List<String> {
        if (mechanics != null) return mechanics.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        return if (gameMode != null) ALL_MECHANICS.keys.toList() else emptyList()
    }
}
