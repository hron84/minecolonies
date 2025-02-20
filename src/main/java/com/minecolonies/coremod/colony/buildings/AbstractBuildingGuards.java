package com.minecolonies.coremod.colony.buildings;

import com.ldtteam.blockout.views.Window;
import com.ldtteam.structurize.util.LanguageHandler;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.api.colony.buildings.HiringMode;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.IGuardBuilding;
import com.minecolonies.api.colony.buildings.views.MobEntryView;
import com.minecolonies.api.colony.guardtype.GuardType;
import com.minecolonies.api.colony.guardtype.registry.IGuardTypeDataManager;
import com.minecolonies.api.colony.guardtype.registry.IGuardTypeRegistry;
import com.minecolonies.api.colony.guardtype.registry.ModGuardTypes;
import com.minecolonies.api.colony.jobs.IJob;
import com.minecolonies.api.colony.requestsystem.location.ILocation;
import com.minecolonies.api.entity.ai.citizen.guards.GuardTask;
import com.minecolonies.api.entity.ai.statemachine.AIOneTimeEventTarget;
import com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.entity.citizen.Skill;
import com.minecolonies.api.util.BlockPosUtil;
import com.minecolonies.api.util.ItemStackUtils;
import com.minecolonies.api.util.Log;
import com.minecolonies.api.util.constant.ToolType;
import com.minecolonies.coremod.MineColonies;
import com.minecolonies.coremod.Network;
import com.minecolonies.coremod.client.gui.huts.WindowHutGuardTowerModule;
import com.minecolonies.coremod.colony.buildings.workerbuildings.BuildingMiner;
import com.minecolonies.coremod.colony.jobs.AbstractJobGuard;
import com.minecolonies.coremod.colony.jobs.JobArcherTraining;
import com.minecolonies.coremod.colony.jobs.JobCombatTraining;
import com.minecolonies.coremod.colony.requestsystem.locations.EntityLocation;
import com.minecolonies.coremod.entity.ai.citizen.guard.AbstractEntityAIGuard;
import com.minecolonies.coremod.entity.pathfinding.Pathfinding;
import com.minecolonies.coremod.entity.pathfinding.pathjobs.PathJobRandomPos;
import com.minecolonies.coremod.items.ItemBannerRallyGuards;
import com.minecolonies.coremod.network.messages.client.colony.building.guard.GuardMobAttackListMessage;
import com.minecolonies.coremod.util.AttributeModifierUtils;
import net.minecraft.entity.EntityClassification;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.EquipmentSlotType;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArrowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.network.PacketBuffer;
import net.minecraft.pathfinding.Path;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Tuple;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.Future;

import static com.minecolonies.api.research.util.ResearchConstants.*;
import static com.minecolonies.api.util.constant.CitizenConstants.*;
import static com.minecolonies.api.util.constant.ToolLevelConstants.TOOL_LEVEL_WOOD_OR_GOLD;

/**
 * Abstract class for Guard huts.
 */
@SuppressWarnings({"squid:MaximumInheritanceDepth", "squid:S1448"})
public abstract class AbstractBuildingGuards extends AbstractBuildingWorker implements IGuardBuilding
{
    ////// --------------------------- NBTConstants --------------------------- \\\\\\
    private static final String NBT_TASK           = "TASK";
    private static final String NBT_JOB            = "guardType";
    private static final String NBT_ASSIGN         = "assign";
    private static final String NBT_RETRIEVE       = "retrieve";
    private static final String NBT_PATROL         = "patrol";
    private static final String NBT_TIGHT_GROUPING = "tightGrouping";
    private static final String NBT_PATROL_TARGETS = "patrol targets";
    private static final String NBT_TARGET         = "target";
    private static final String NBT_GUARD          = "guard";
    private static final String NBT_MOBS           = "mobs";
    private static final String NBT_MOB_VIEW       = "mobview";
    private static final String NBT_RECRUIT        = "recruitTrainees";
    private static final String NBT_MINE_POS       = "minePos";

    ////// --------------------------- NBTConstants --------------------------- \\\\\\

    ////// --------------------------- GuardJob Enum --------------------------- \\\\\\

    /**
     * Base patrol range
     */
    private static final int PATROL_BASE_DIST = 50;

    /**
     * The Bonus Health for each building level
     */
    private static final int BONUS_HEALTH_PER_LEVEL = 2;

    /**
     * Vision range per building level.
     */
    private static final int VISION_RANGE_PER_LEVEL = 3;

    /**
     * Base Vision range per building level.
     */
    private static final int BASE_VISION_RANGE = 15;

    /**
     * Whether the guardType will be assigned manually.
     */
    private boolean assignManually = false;

    /**
     * Whether to retrieve the guard on low health.
     */
    private boolean retrieveOnLowHealth = true;

    /**
     * Whether to patrol manually or not.
     */
    protected boolean patrolManually = false;

    protected static final boolean canGuardMine = true;

    /**
     * The task of the guard, following the {@link GuardTask} enum.
     */
    private GuardTask task = GuardTask.PATROL;

    /**
     * The position at which the guard should guard at.
     */
    private BlockPos guardPos = this.getID();

    /**
     * The guardType of the guard, Any possible {@link GuardType}.
     */
    private GuardType job = null;

    /**
     * The list of manual patrol targets.
     */
    protected List<BlockPos> patrolTargets = new ArrayList<>();

    /**
     * Hashmap of mobs we may or may not attack.
     */
    private Map<ResourceLocation, MobEntryView> mobsToAttack = new HashMap<>();

    /**
     * The player the guard has been set to follow.
     */
    private PlayerEntity followPlayer;

    /**
     * The location the guard has been set to rally to.
     */
    private ILocation rallyLocation;

    /**
     * Indicates if in Follow mode what type of follow is use. True - tight grouping, false - lose grouping.
     */
    private boolean tightGrouping;

    /**
     * A temporary next patrol point, which gets consumed and used once
     */
    protected BlockPos tempNextPatrolPoint = null;

    /**
     * Whether or not to hire from the trainee facilities
     */
    private boolean      hireTrainees = true;

    /**
     * Pathing future for the next patrol target.
     */
    private Future<Path> pathingFuture;

    /**
     * The location of the assigned mine
     */
    private BlockPos minePos;

    /**
     * The abstract constructor of the building.
     *
     * @param c the colony
     * @param l the position
     */
    public AbstractBuildingGuards(@NotNull final IColony c, final BlockPos l)
    {
        super(c, l);

        keepX.put(itemStack -> ItemStackUtils.hasToolLevel(itemStack, ToolType.BOW, TOOL_LEVEL_WOOD_OR_GOLD, getMaxToolLevel()), new Tuple<>(1, true));
        keepX.put(itemStack -> !ItemStackUtils.isEmpty(itemStack) && ItemStackUtils.doesItemServeAsWeapon(itemStack), new Tuple<>(1, true));

        keepX.put(itemStack -> !ItemStackUtils.isEmpty(itemStack)
                                 && itemStack.getItem() instanceof ArmorItem
                                 && ((ArmorItem) itemStack.getItem()).getEquipmentSlot() == EquipmentSlotType.CHEST, new Tuple<>(1, true));
        keepX.put(itemStack -> !ItemStackUtils.isEmpty(itemStack)
                                 && itemStack.getItem() instanceof ArmorItem
                                 && ((ArmorItem) itemStack.getItem()).getEquipmentSlot() == EquipmentSlotType.HEAD, new Tuple<>(1, true));
        keepX.put(itemStack -> !ItemStackUtils.isEmpty(itemStack)
                                 && itemStack.getItem() instanceof ArmorItem
                                 && ((ArmorItem) itemStack.getItem()).getEquipmentSlot() == EquipmentSlotType.LEGS, new Tuple<>(1, true));
        keepX.put(itemStack -> !ItemStackUtils.isEmpty(itemStack)
                                 && itemStack.getItem() instanceof ArmorItem
                                 && ((ArmorItem) itemStack.getItem()).getEquipmentSlot() == EquipmentSlotType.FEET, new Tuple<>(1, true));

        keepX.put(itemStack -> {
            if (ItemStackUtils.isEmpty(itemStack) || !(itemStack.getItem() instanceof ArrowItem))
            {
                return false;
            }

            return getColony().getResearchManager().getResearchEffects().getEffectStrength(ARCHER_USE_ARROWS) > 0
                     && getGuardType() == ModGuardTypes.ranger;
        }, new Tuple<>(128, true));

        calculateMobs();
    }

    //// ---- NBT Overrides ---- \\\\

    /**
     * We use this to set possible health multipliers and give achievements.
     *
     * @param newLevel The new level.
     */
    @Override
    public void onUpgradeComplete(final int newLevel)
    {
        getGuardType();

        if (getAssignedEntities() != null)
        {
            for (final Optional<AbstractEntityCitizen> optCitizen : getAssignedEntities())
            {
                if (optCitizen.isPresent())
                {
                    final AttributeModifier healthModBuildingHP = new AttributeModifier(GUARD_HEALTH_MOD_BUILDING_NAME, getBonusHealth(), AttributeModifier.Operation.ADDITION);
                    AttributeModifierUtils.addHealthModifier(optCitizen.get(), healthModBuildingHP);
                }
            }
        }

        super.onUpgradeComplete(newLevel);
    }

    @Override
    public boolean assignCitizen(final ICitizenData citizen)
    {
        // Only change HP values if assign successful
        if (super.assignCitizen(citizen) && citizen != null)
        {
            final Optional<AbstractEntityCitizen> optCitizen = citizen.getEntity();
            if (optCitizen.isPresent())
            {
                final AbstractEntityCitizen citizenEntity = optCitizen.get();
                AttributeModifierUtils.addHealthModifier(citizenEntity,
                    new AttributeModifier(GUARD_HEALTH_MOD_BUILDING_NAME, getBonusHealth(), AttributeModifier.Operation.ADDITION));
                AttributeModifierUtils.addHealthModifier(citizenEntity,
                    new AttributeModifier(GUARD_HEALTH_MOD_CONFIG_NAME,
                        MineColonies.getConfig().getServer().guardHealthMult.get() - 1.0,
                        AttributeModifier.Operation.MULTIPLY_TOTAL));
            }

            // Set new home, since guards are housed at their workerbuilding.
            final IBuilding building = citizen.getHomeBuilding();
            if (building != null && !building.getID().equals(this.getID()))
            {
                building.removeCitizen(citizen);
            }
            citizen.setHomeBuilding(this);
            // Start timeout to not be stuck with an old patrol target
            patrolTimer = 5;

            return true;
        }
        return false;
    }

    //// ---- NBT Overrides ---- \\\\

    //// ---- Overrides ---- \\\\

    @Override
    public void deserializeNBT(final CompoundNBT compound)
    {
        super.deserializeNBT(compound);

        task = GuardTask.values()[compound.getInt(NBT_TASK)];
        final ResourceLocation jobName = new ResourceLocation(compound.getString(NBT_JOB));
        job = IGuardTypeDataManager.getInstance().getFrom(jobName);
        assignManually = compound.getBoolean(NBT_ASSIGN);
        retrieveOnLowHealth = compound.getBoolean(NBT_RETRIEVE);
        patrolManually = compound.getBoolean(NBT_PATROL);
        if(compound.contains(NBT_RECRUIT))
        {
            hireTrainees = compound.getBoolean(NBT_RECRUIT);
        }
        if (compound.keySet().contains(NBT_TIGHT_GROUPING))
        {
            tightGrouping = compound.getBoolean(NBT_TIGHT_GROUPING);
        }
        else
        {
            tightGrouping = true;
        }

        final ListNBT wayPointTagList = compound.getList(NBT_PATROL_TARGETS, Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < wayPointTagList.size(); ++i)
        {
            final CompoundNBT blockAtPos = wayPointTagList.getCompound(i);
            final BlockPos pos = BlockPosUtil.read(blockAtPos, NBT_TARGET);
            patrolTargets.add(pos);
        }

        final ListNBT mobsTagList = compound.getList(NBT_MOBS, Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < mobsTagList.size(); i++)
        {
            final CompoundNBT mobCompound = mobsTagList.getCompound(i);
            final MobEntryView mobEntry = MobEntryView.read(mobCompound, NBT_MOB_VIEW);
            if (mobEntry.getEntityEntry() != null)
            {
                mobsToAttack.put(mobEntry.getEntityEntry().getRegistryName(), mobEntry);
            }
        }

        guardPos = NBTUtil.readBlockPos(compound.getCompound(NBT_GUARD));
        if (compound.contains(NBT_MINE_POS))
        {
            minePos = NBTUtil.readBlockPos(compound.getCompound(NBT_MINE_POS));
        }
    }

    @Override
    public CompoundNBT serializeNBT()
    {
        final CompoundNBT compound = super.serializeNBT();

        compound.putInt(NBT_TASK, task.ordinal());
        compound.putString(NBT_JOB, job == null ? "" : job.getRegistryName().toString());
        compound.putBoolean(NBT_ASSIGN, assignManually);
        compound.putBoolean(NBT_RETRIEVE, retrieveOnLowHealth);
        compound.putBoolean(NBT_PATROL, patrolManually);
        compound.putBoolean(NBT_TIGHT_GROUPING, tightGrouping);
        compound.putBoolean(NBT_RECRUIT, hireTrainees);

        @NotNull final ListNBT wayPointTagList = new ListNBT();
        for (@NotNull final BlockPos pos : patrolTargets)
        {
            @NotNull final CompoundNBT wayPointCompound = new CompoundNBT();
            BlockPosUtil.write(wayPointCompound, NBT_TARGET, pos);

            wayPointTagList.add(wayPointCompound);
        }
        compound.put(NBT_PATROL_TARGETS, wayPointTagList);

        @NotNull final ListNBT mobsTagList = new ListNBT();
        for (@NotNull final MobEntryView entry : mobsToAttack.values())
        {
            @NotNull final CompoundNBT mobCompound = new CompoundNBT();
            MobEntryView.write(mobCompound, NBT_MOB_VIEW, entry);
            mobsTagList.add(mobCompound);
        }
        compound.put(NBT_MOBS, mobsTagList);

        compound.put(NBT_GUARD, NBTUtil.writeBlockPos(guardPos));
        if (minePos != null)
        {
            compound.put(NBT_MINE_POS, NBTUtil.writeBlockPos(minePos));
        }

        return compound;
    }

    @Override
    public void removeCitizen(final ICitizenData citizen)
    {
        if (citizen != null)
        {
            final Optional<AbstractEntityCitizen> optCitizen = citizen.getEntity();
            if (optCitizen.isPresent())
            {
                AttributeModifierUtils.removeAllHealthModifiers(optCitizen.get());
                optCitizen.get().setItemStackToSlot(EquipmentSlotType.CHEST, ItemStackUtils.EMPTY);
                optCitizen.get().setItemStackToSlot(EquipmentSlotType.FEET, ItemStackUtils.EMPTY);
                optCitizen.get().setItemStackToSlot(EquipmentSlotType.HEAD, ItemStackUtils.EMPTY);
                optCitizen.get().setItemStackToSlot(EquipmentSlotType.LEGS, ItemStackUtils.EMPTY);
                optCitizen.get().setItemStackToSlot(EquipmentSlotType.MAINHAND, ItemStackUtils.EMPTY);
                optCitizen.get().setItemStackToSlot(EquipmentSlotType.OFFHAND, ItemStackUtils.EMPTY);
            }
            citizen.setHomeBuilding(null);
        }
        super.removeCitizen(citizen);
    }

    @Override
    public void serializeToView(@NotNull final PacketBuffer buf)
    {
        super.serializeToView(buf);
        buf.writeBoolean(assignManually);
        buf.writeBoolean(retrieveOnLowHealth);
        buf.writeBoolean(patrolManually);
        buf.writeBoolean(tightGrouping);
        buf.writeBoolean(hireTrainees);
        buf.writeInt(task.ordinal());
        buf.writeString(job == null ? "" : job.getRegistryName().toString());
        buf.writeInt(patrolTargets.size());

        for (final BlockPos pos : patrolTargets)
        {
            buf.writeBlockPos(pos);
        }

        if (mobsToAttack.isEmpty())
        {
            calculateMobs();
        }

        buf.writeInt(mobsToAttack.size());
        for (final MobEntryView entry : mobsToAttack.values())
        {
            MobEntryView.writeToByteBuf(buf, entry);
        }

        buf.writeBlockPos(guardPos);

        buf.writeInt(this.getAssignedCitizen().size());
        for (final ICitizenData citizen : this.getAssignedCitizen())
        {
            buf.writeInt(citizen.getId());
        }

        if (minePos != null)
        {
            buf.writeBoolean(true);
            buf.writeBlockPos(minePos);
        }
        else
        {
            buf.writeBoolean(false);
        }
        buf.writeBoolean(canGuardMine);
    }

    /**
     * Get the guard's {@link GuardTask}.
     *
     * @return The task of the guard.
     */
    @Override
    public GuardTask getTask()
    {
        return this.task;
    }

    /**
     * Set the guard's {@link GuardTask}.
     *
     * @param task The task to set.
     */
    @Override
    public void setTask(final GuardTask task)
    {
        this.task = task;
        if (task != GuardTask.MINE)
        {
            this.setMinePos(null);
        }
        this.markDirty();
    }

    @Override
    @Nullable
    public PlayerEntity getPlayerToFollowOrRally()
    {
        return rallyLocation != null && rallyLocation instanceof EntityLocation ? ((EntityLocation) rallyLocation).getPlayerEntity() : followPlayer;
    }

    /**
     * The guards which arrived at the patrol positions
     */
    private final Set<AbstractEntityCitizen> arrivedAtPatrol = new HashSet<>();

    /**
     * The last patrol position
     */
    private BlockPos lastPatrolPoint;

    /**
     * The patrol waiting for others timeout
     */
    private int patrolTimer = 0;

    @Override
    public void onColonyTick(@NotNull final IColony colony)
    {
        boolean hiredFromTraining = false;

        // If we have no active worker, attempt to grab one from the appropriate trainer
        if (hireTrainees && !isFull() && ((getBuildingLevel() > 0 && isBuilt()))
              && (this.getHiringMode() == HiringMode.DEFAULT && !this.getColony().isManualHiring() || this.getHiringMode() == HiringMode.AUTO))
        {
            ICitizenData trainingCitizen = null;
            int maxSkill = 0;

            for(ICitizenData trainee:colony.getCitizenManager().getCitizens())
            {
                if((this.getGuardType() == ModGuardTypes.ranger && trainee.getJob() instanceof JobArcherTraining) || (this.getGuardType() == ModGuardTypes.knight && trainee.getJob() instanceof JobCombatTraining)
                    &&  trainee.getCitizenSkillHandler().getLevel(job.getPrimarySkill()) > maxSkill)
                {
                    maxSkill = trainee.getCitizenSkillHandler().getLevel(job.getPrimarySkill());
                    trainingCitizen = trainee;
                }
            }

            if(trainingCitizen != null )
            {
                hiredFromTraining = true;
                assignCitizen(trainingCitizen);
            }
        }

        //If we hired, we may have more than one to hire, so let's skip the superclass until next time. 
        if(!hiredFromTraining)
        {
            super.onColonyTick(colony); 
        }

        if (patrolTimer > 0 && task == GuardTask.PATROL)
        {
            patrolTimer--;
            if (patrolTimer <= 0 && !getAssignedCitizen().isEmpty())
            {
                // Next patrol point
                startPatrolNext();
            }
        }
    }

    @Override
    public boolean requiresManualTarget()
    {
        return false;
    }

    @Override
    public void arrivedAtPatrolPoint(final AbstractEntityCitizen guard)
    {
        // Start waiting timer for other guards
        if (arrivedAtPatrol.isEmpty())
        {
            patrolTimer = 1;
        }

        arrivedAtPatrol.add(guard);

        if (getAssignedCitizen().size() <= arrivedAtPatrol.size() || patrolTimer <= 0)
        {
            // Next patrol point
            startPatrolNext();
        }
    }

    /**
     * Starts the patrol to the next point
     */
    private void startPatrolNext()
    {
        getNextPatrolTarget(true);
        patrolTimer = 5;

        for (final ICitizenData curguard : getAssignedCitizen())
        {
            if (curguard.getEntity().isPresent())
            {
                if (curguard.getEntity().get().getCitizenJobHandler().getColonyJob() instanceof AbstractJobGuard)
                {
                    ((AbstractEntityAIGuard<?, ?>) curguard.getEntity().get().getCitizenJobHandler().getColonyJob().getWorkerAI()).setNextPatrolTarget(lastPatrolPoint);
                }
            }
        }
        arrivedAtPatrol.clear();
    }

    @Override
    @Nullable
    public BlockPos getNextPatrolTarget(final boolean newTarget)
    {
        if (!newTarget && lastPatrolPoint != null)
        {
            return lastPatrolPoint;
        }

        if (tempNextPatrolPoint != null)
        {
            lastPatrolPoint = tempNextPatrolPoint;
            tempNextPatrolPoint = null;
            return lastPatrolPoint;
        }

        if (lastPatrolPoint == null)
        {
            lastPatrolPoint = getAssignedCitizen().get(0).getLastPosition();
            return lastPatrolPoint;
        }

        if (!patrolManually || patrolTargets == null || patrolTargets.isEmpty())
        {
            BlockPos pos = null;
            if (this.pathingFuture != null && this.pathingFuture.isDone())
            {
                try
                {
                    pos = this.pathingFuture.get().getTarget();
                }
                catch (final Exception e)
                {
                    Log.getLogger().warn("Guard pathing interrupted", e);
                }
                this.pathingFuture = null;
            }
            else if (colony.getWorld().rand.nextBoolean() || (this.pathingFuture != null && this.pathingFuture.isCancelled()))
            {
                this.pathingFuture = Pathfinding.enqueue(new PathJobRandomPos(colony.getWorld(),lastPatrolPoint,10, 30,null));
            }
            else
            {
                pos = colony.getBuildingManager().getRandomBuilding(b -> true);
            }

            if (pos != null)
            {
                if (BlockPosUtil.getDistance2D(pos, getPosition()) > getPatrolDistance())
                {
                    lastPatrolPoint = getPosition();
                    return lastPatrolPoint;
                }
                lastPatrolPoint = pos;
            }
            return lastPatrolPoint;
        }

        if (patrolTargets.contains(lastPatrolPoint))
        {
            int index = patrolTargets.indexOf(lastPatrolPoint) + 1;

            if (index >= patrolTargets.size())
            {
                index = 0;
            }

            lastPatrolPoint = patrolTargets.get(index);
            return lastPatrolPoint;
        }
        lastPatrolPoint = patrolTargets.get(0);
        return lastPatrolPoint;
    }

    @Override
    public int getPatrolDistance()
    {
        return PATROL_BASE_DIST + this.getBuildingLevel() * PATROL_DISTANCE;
    }

    /**
     * Sets a one time consumed temporary next position to patrol towards
     *
     * @param pos Position to set
     */
    public void setTempNextPatrolPoint(final BlockPos pos)
    {
        tempNextPatrolPoint = pos;
    }

    /**
     * Return the position of the mine to guard
     * @return the position of the mine
     */
    public BlockPos getMinePos()
    {
        return minePos;
    }

    /**
     * Set the position of the mine the guard is patrolling
     * Check whether the given position is actually a mine
     * @param pos the position of the mine
     */
    public void setMinePos(BlockPos pos)
    {
        if (pos == null)
        {
            this.minePos = null;
        }
        else if (colony.getBuildingManager().getBuilding(pos) instanceof BuildingMiner)
        {
            this.minePos = pos;
        }
    }

    /**
     * The client view for the Guard building.
     */
    public static class View extends AbstractBuildingWorker.View
    {

        /**
         * Assign the guardType manually, knight, guard, or *Other* (Future usage)
         */
        private boolean assignManually = false;

        /**
         * Retrieve the guard on low health.
         */
        private boolean retrieveOnLowHealth = false;

        /**
         * Patrol manually or automatically.
         */
        private boolean patrolManually = false;

        /**
         * The {@link GuardTask} of the guard.
         */
        private GuardTask task = GuardTask.PATROL;

        /**
         * Position the guard should guard.
         */
        private BlockPos guardPos = this.getID();

        /**
         * The {@link GuardType} of the guard
         */
        private GuardType guardType = null;

        /**
         * Indicates whether tight grouping is use or lose grouping.
         */
        private boolean tightGrouping = true;

        /**
         * Indicates whether to hire from trainee facilities first
         */
        private boolean hireTrainees = true; 

        /**
         * The list of manual patrol targets.
         */
        private List<BlockPos> patrolTargets = new ArrayList<>();

        /**
         * Hashmap of mobs we may or may not attack.
         */
        private List<MobEntryView> mobsToAttack = new ArrayList<>();

        @NotNull
        private final List<Integer> guards = new ArrayList<>();

        /**
         * Location of the assigned mine
         */
        private BlockPos minePos;

        /**
         * If the building can guard mines
         */
        protected boolean canGuardMine;

        /**
         * The client view constructor for the AbstractGuardBuilding.
         *
         * @param c the colony.
         * @param l the location.
         */
        public View(final IColonyView c, @NotNull final BlockPos l)
        {
            super(c, l);
        }

        /**
         * Creates a new window for the building.
         *
         * @return a BlockOut window.
         */
        @NotNull
        @Override
        public Window getWindow()
        {
            return new WindowHutGuardTowerModule(this);
        }

        /**
         * Getter for the list of residents.
         *
         * @return an unmodifiable list.
         */
        @NotNull
        public List<Integer> getGuards()
        {
            return Collections.unmodifiableList(guards);
        }

        @Override
        public void deserialize(@NotNull final PacketBuffer buf)
        {
            super.deserialize(buf);
            assignManually = buf.readBoolean();
            retrieveOnLowHealth = buf.readBoolean();
            patrolManually = buf.readBoolean();
            tightGrouping = buf.readBoolean();
            hireTrainees = buf.readBoolean();


            task = GuardTask.values()[buf.readInt()];
            final ResourceLocation jobId = new ResourceLocation(buf.readString(32767));
            guardType = IGuardTypeRegistry.getInstance().getValue(jobId);

            final int targetSize = buf.readInt();
            patrolTargets = new ArrayList<>();

            for (int i = 0; i < targetSize; i++)
            {
                patrolTargets.add(buf.readBlockPos());
            }

            mobsToAttack.clear();
            final int mobSize = buf.readInt();
            for (int i = 0; i < mobSize; i++)
            {
                final MobEntryView mobEntry = MobEntryView.readFromByteBuf(buf);
                mobsToAttack.add(mobEntry);
            }

            guardPos = buf.readBlockPos();

            guards.clear();
            final int numResidents = buf.readInt();
            for (int i = 0; i < numResidents; ++i)
            {
                guards.add(buf.readInt());
            }

            if (buf.readBoolean())
            {
                minePos = buf.readBlockPos();
            }
            else
            {
                minePos = null;
            }

            canGuardMine = buf.readBoolean();
        }

        @NotNull
        @Override
        public Skill getPrimarySkill()
        {
            return getGuardType().getPrimarySkill();
        }

        @NotNull
        @Override
        public Skill getSecondarySkill()
        {
            return getGuardType().getSecondarySkill();
        }

        public void setAssignManually(final boolean assignManually)
        {
            this.assignManually = assignManually;
        }

        public boolean isAssignManually()
        {
            return assignManually;
        }

        public void setRetrieveOnLowHealth(final boolean retrieveOnLowHealth)
        {
            this.retrieveOnLowHealth = retrieveOnLowHealth;
        }

        public boolean isRetrieveOnLowHealth()
        {
            return retrieveOnLowHealth;
        }

        /**
         * Set whether to use tight grouping or lose grouping.
         *
         * @param tightGrouping - indicates if you are using tight grouping
         */
        public void setTightGrouping(final boolean tightGrouping)
        {
            this.tightGrouping = tightGrouping;
        }

        /**
         * Returns whether tight grouping in Follow mode is being used.
         *
         * @return whether tight grouping is being used.
         */
        public boolean isTightGrouping()
        {
            return tightGrouping;
        }

        /**
         * Is hiring from training facilities enabled
         * @return
         */
        public boolean isHireTrainees()
        {
            return hireTrainees;
        }

        /**
         * Set whether or not to hire from training facilities
         */
        public void setHireTrainees(final boolean hireTrainees)
        {
            this.hireTrainees = hireTrainees;
        }

        public void setPatrolManually(final boolean patrolManually)
        {
            this.patrolManually = patrolManually;
        }

        public void setMobsToAttack(final List<MobEntryView> mobsToAttack)
        {
            this.mobsToAttack = new ArrayList<>(mobsToAttack);
        }

        public boolean isPatrolManually()
        {
            return patrolManually;
        }

        public void setTask(final GuardTask task)
        {
            this.task = task;
            if (task != GuardTask.MINE)
            {
                this.setMinePos(null);
            }
            this.getColony().markDirty();
        }

        public GuardTask getTask()
        {
            return task;
        }

        public BlockPos getGuardPos()
        {
            return guardPos;
        }

        public GuardType getGuardType()
        {
            return guardType;
        }

        public void setGuardType(final GuardType job)
        {
            this.guardType = job;
        }

        public List<BlockPos> getPatrolTargets()
        {
            return new ArrayList<>(patrolTargets);
        }

        public List<MobEntryView> getMobsToAttack()
        {
            return new ArrayList<>(mobsToAttack);
        }

        /**
         * Return the position of the mine the guard is patrolling
         * @return the position of the mine
         */
        public BlockPos getMinePos() { return minePos; }

        /**
         * Set the position of the mine the guard is patrolling
         * @param pos the position of the mine
         */
        public void setMinePos(BlockPos pos) { this.minePos = pos; }

        public Boolean canGuardMine()
        {
            return canGuardMine;
        }
    }

    @Override
    public GuardType getGuardType()
    {
        if (job == null)
        {
            final List<GuardType> guardTypes = new ArrayList<>(IGuardTypeRegistry.getInstance().getValues());
            job = guardTypes.get(new Random().nextInt(guardTypes.size()));
        }
        return this.job;
    }

    @Override
    public void setGuardType(final GuardType job)
    {
        this.job = job;
        for (final ICitizenData citizen : getAssignedCitizen())
        {
            cancelAllRequestsOfCitizen(citizen);
            citizen.setJob(createJob(citizen));
        }
        this.markDirty();
    }

    @NotNull
    @Override
    public IJob<?> createJob(final ICitizenData citizen)
    {
        return getGuardType().getGuardJobProducer().apply(citizen);
    }

    @NotNull
    @Override
    public String getJobName()
    {
        return getGuardType().getJobTranslationKey();
    }

    @Override
    public List<BlockPos> getPatrolTargets()
    {
        return new ArrayList<>(patrolTargets);
    }

    @Override
    public boolean shallRetrieveOnLowHealth()
    {
        return retrieveOnLowHealth;
    }

    @Override
    public void setRetrieveOnLowHealth(final boolean retrieve)
    {
        this.retrieveOnLowHealth = retrieve;
    }

    @Override
    public boolean shallPatrolManually()
    {
        return patrolManually;
    }

    @Override
    public void setPatrolManually(final boolean patrolManually)
    {
        this.patrolManually = patrolManually;
    }

    @Override
    public boolean shallAssignManually()
    {
        return assignManually;
    }

    @Override
    public void setAssignManually(final boolean assignManually)
    {
        this.assignManually = assignManually;
    }

    @Override
    public boolean isTightGrouping()
    {
        return tightGrouping;
    }

    @Override
    public void setTightGrouping(final boolean tightGrouping)
    {
        this.tightGrouping = tightGrouping;
    }

    @Override
    public BlockPos getGuardPos()
    {
        return guardPos;
    }

    @Override
    public void setGuardPos(final BlockPos guardPos)
    {
        this.guardPos = guardPos;
    }

    @Override
    public Map<ResourceLocation, MobEntryView> getMobsToAttack()
    {
        return mobsToAttack;
    }

    @Override
    public void setMobsToAttack(final List<MobEntryView> list)
    {
        this.mobsToAttack = new HashMap<>();
        for (MobEntryView entry : list)
        {
            mobsToAttack.put(entry.getEntityEntry().getRegistryName(), entry);
        }
    }

    @Override
    public BlockPos getPositionToFollow()
    {
        if (task.equals(GuardTask.FOLLOW) && followPlayer != null)
        {
            return new BlockPos(followPlayer.getPositionVec());
        }

        return this.getPosition();
    }

    @Override
    @Nullable
    public ILocation getRallyLocation()
    {
        if (rallyLocation == null)
        {
            return null;
        }

        boolean outOfRange = false;
        final IColony colonyAtPosition = IColonyManager.getInstance().getColonyByPosFromDim(rallyLocation.getDimension(), rallyLocation.getInDimensionLocation());
        if (colonyAtPosition == null || colonyAtPosition.getID() != colony.getID())
        {
            outOfRange = true;
        }

        if (rallyLocation instanceof EntityLocation)
        {
            final PlayerEntity player = ((EntityLocation) rallyLocation).getPlayerEntity();
            if (player == null)
            {
                setRallyLocation(null);
                return null;
            }

            if (outOfRange)
            {
                LanguageHandler.sendPlayerMessage(player, "item.minecolonies.banner_rally_guards.outofrange");
                setRallyLocation(null);
                return null;
            }

            final int size = player.inventory.getSizeInventory();
            for (int i = 0; i < size; i++)
            {
                final ItemStack stack = player.inventory.getStackInSlot(i);
                if (stack.getItem() instanceof ItemBannerRallyGuards)
                {
                    if (((ItemBannerRallyGuards) (stack.getItem())).isActiveForGuardTower(stack, this))
                    {
                        return rallyLocation;
                    }
                }
            }
            // Note: We do not reset the rallyLocation here.
            // So, if the player doesn't properly deactivate the banner, this will cause relatively minor lag.
            // But, in exchange, the player does not have to reactivate the banner so often, and it also works
            // if the user moves the banner around in the inventory.
            return null;
        }

        return rallyLocation;
    }

    @Override
    public void setRallyLocation(final ILocation location)
    {
        boolean reduceSaturation = false;
        if (rallyLocation != null && location == null)
        {
            reduceSaturation = true;
        }

        rallyLocation = location;

        for (final ICitizenData iCitizenData : getAssignedCitizen())
        {
            if (reduceSaturation && iCitizenData.getSaturation() < LOW_SATURATION)
            {
                // In addition to the scaled saturation reduction during rallying, stopping a rally
                // will - if only LOW_SATURATION is left - set the saturation level to 0.
                iCitizenData.decreaseSaturation(LOW_SATURATION);
            }

            final AbstractJobGuard<?> job = iCitizenData.getJob(AbstractJobGuard.class);
            if (job != null && job.getWorkerAI() != null)
            {
                job.getWorkerAI().registerTarget(new AIOneTimeEventTarget(AIWorkerState.GUARD_DECIDE));
            }
        }
    }

    @Override
    public void setPlayerToFollow(final PlayerEntity player)
    {
        this.followPlayer = player;

        for (final ICitizenData iCitizenData : getAssignedCitizen())
        {
            final AbstractJobGuard<?> job = iCitizenData.getJob(AbstractJobGuard.class);
            if (job != null && job.getWorkerAI() != null)
            {
                job.getWorkerAI().registerTarget(new AIOneTimeEventTarget(AIWorkerState.DECIDE));
            }
        }
    }

    /**
     * Bonus guard hp per bulding level
     *
     * @return the bonus health.
     */
    protected int getBonusHealth()
    {
        return getBuildingLevel() * BONUS_HEALTH_PER_LEVEL;
    }

    /**
     * Adds new patrolTargets.
     *
     * @param target the target to add
     */
    @Override
    public void addPatrolTargets(final BlockPos target)
    {
        this.patrolTargets.add(target);
        this.markDirty();
    }

    /**
     * Resets the patrolTargets list.
     */
    @Override
    public void resetPatrolTargets()
    {
        this.patrolTargets = new ArrayList<>();
        this.markDirty();
    }

    @NotNull
    @Override
    public Skill getPrimarySkill()
    {
        return job.getPrimarySkill();
    }

    @NotNull
    @Override
    public Skill getSecondarySkill()
    {
        return job.getSecondarySkill();
    }

    /**
     * Get the Vision bonus range for the building level
     *
     * @return an integer for the additional range.
     */
    @Override
    public int getBonusVision()
    {
        return BASE_VISION_RANGE + getBuildingLevel() * VISION_RANGE_PER_LEVEL;
    }

    /**
     * Populates the mobs list from the ForgeRegistries.
     */
    @Override
    public void calculateMobs()
    {
        mobsToAttack = new HashMap<>();

        int i = 0;
        for (final Map.Entry<RegistryKey<EntityType<?>>, EntityType<?>> entry : ForgeRegistries.ENTITIES.getEntries())
        {
            if (entry.getValue().getClassification() == EntityClassification.MONSTER)
            {
                i++;
                mobsToAttack.put(entry.getKey().getLocation(), new MobEntryView(entry.getKey().getLocation(), true, i));
            }
            else
            {
                for (final String location : MineColonies.getConfig().getServer().guardResourceLocations.get())
                {
                    if (entry.getKey() != null && entry.getKey().getLocation().toString().equals(location))
                    {
                        i++;
                        mobsToAttack.put(entry.getKey().getLocation(), new MobEntryView(entry.getKey().getLocation(), true, i));
                    }
                }
            }
        }

        getColony().getPackageManager().getCloseSubscribers().forEach(player -> Network
                                                                                  .getNetwork()
                                                                                  .sendToPlayer(new GuardMobAttackListMessage(getColony().getID(),
                                                                                      getID(),
                                                                                      new ArrayList<>(mobsToAttack.values())),
                                                                                    player));
    }

    @Override
    public boolean canWorkDuringTheRain()
    {
        return true;
    }

    /**
     * is hiring from training facilities enabled
     * @return true if so.
     */
    public boolean isHireTrainees()
    {
        return hireTrainees;
    }

    /**
     * set hiring from training facilities
     */
    public void setHireTrainees(boolean hireTrainees)
    {
        this.hireTrainees = hireTrainees;
    }
}
