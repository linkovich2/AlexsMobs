package com.github.alexthe666.alexsmobs.entity;

import com.github.alexthe666.alexsmobs.AlexsMobs;
import com.github.alexthe666.alexsmobs.entity.util.AnacondaPartIndex;
import com.github.alexthe666.alexsmobs.message.MessageHurtMultipart;
import com.google.common.collect.ImmutableList;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class EntityAnacondaPart extends LivingEntity implements IHurtableMultipart {
    private static final EntityDataAccessor<Integer> BODYINDEX = SynchedEntityData.defineId(EntityAnacondaPart.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> BODY_TYPE = SynchedEntityData.defineId(EntityAnacondaPart.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> TARGET_YAW = SynchedEntityData.defineId(EntityAnacondaPart.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Optional<UUID>> CHILD_UUID = SynchedEntityData.defineId(EntityAnacondaPart.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Optional<UUID>> PARENT_UUID = SynchedEntityData.defineId(EntityAnacondaPart.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Float> SWELL = SynchedEntityData.defineId(EntityAnacondaPart.class, EntityDataSerializers.FLOAT);
    public EntityDimensions multipartSize;
    private float strangleProgess;
    private float prevSwell;
    private float prevStrangleProgess;
    private int headEntityId = -1;
    public Vec3[] stranglePosition = new Vec3[]{
            new Vec3(0.5, 0, 0),
            new Vec3(-0.5, 0, 0),
            new Vec3(-1, 0, 0),
            new Vec3(0, 0, 0),
            new Vec3(1, 0, 0),
            new Vec3(0, 0, 0),
            new Vec3(-1, 0, 0),
    };
    private static final EntityDataAccessor<Boolean> YELLOW = SynchedEntityData.defineId(EntityAnacondaPart.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> SHEDDING = SynchedEntityData.defineId(EntityAnacondaPart.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> BABY = SynchedEntityData.defineId(EntityAnacondaPart.class, EntityDataSerializers.BOOLEAN);

    public EntityAnacondaPart(EntityType t, Level world) {
        super(t, world);
        multipartSize = t.getDimensions();
    }

    public EntityAnacondaPart(EntityType t, LivingEntity parent) {
        super(t, parent.level);
        this.setParent(parent);
    }

    @Override
    public InteractionResult interact(Player p_19978_, InteractionHand p_19979_) {
        return this.getParent() == null ? super.interact(p_19978_, p_19979_) : this.getParent().interact(p_19978_, p_19979_);
    }

    public static AttributeSupplier.Builder bakeAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 10.0D).add(Attributes.MOVEMENT_SPEED, 0.15F);
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return source == DamageSource.IN_WALL || source == DamageSource.FALLING_BLOCK || super.isInvulnerableTo(source);
    }

    public boolean isNoGravity() {
        return false;
    }


    @Override
    public void tick() {
        super.tick();

        prevStrangleProgess = strangleProgess;
        prevSwell = this.getSwell();
        isInsidePortal = false;
        this.setDeltaMovement(Vec3.ZERO);
        if (this.tickCount > 1) {
            Entity parent = getParent();
            refreshDimensions();
            if (parent != null && !level.isClientSide) {
                if (parent instanceof LivingEntity) {
                    if (!level.isClientSide && (((LivingEntity) parent).hurtTime > 0 || ((LivingEntity) parent).deathTime > 0)) {
                        AlexsMobs.sendMSGToAll(new MessageHurtMultipart(this.getId(), parent.getId(), 0));
                        this.hurtTime = ((LivingEntity) parent).hurtTime;
                        this.deathTime = ((LivingEntity) parent).deathTime;
                    }
                }
                if (parent.isRemoved() && !level.isClientSide) {
                    this.remove(RemovalReason.DISCARDED);
                }
            } else if (tickCount > 20 && !level.isClientSide) {
                remove(RemovalReason.DISCARDED);
            }
            if (this.getSwell() > 0 && !level.isClientSide) {
                float swellInc = 0.25F;
                if (parent instanceof EntityAnaconda || parent instanceof EntityAnacondaPart && ((EntityAnacondaPart) parent).getSwell() == 0) {
                    if (this.getChild() != null) {
                        EntityAnacondaPart child = (EntityAnacondaPart) this.getChild();
                        if (child.getPartType() == AnacondaPartIndex.TAIL){
                            if(this.getSwell() == swellInc){
                                this.feedAnaconda();
                            }
                        }else{
                            child.setSwell(child.getSwell() + swellInc);
                        }
                    }
                    this.setSwell(this.getSwell() - swellInc);
                }
            }
        }
    }

    private void feedAnaconda() {
        Entity e = this.getParent();
        while(e != null && e instanceof EntityAnacondaPart){
            e = ((EntityAnacondaPart) e).getParent();
        }
        if(e instanceof EntityAnaconda){
            ((EntityAnaconda) e).feed();
        }
    }

    public Vec3 tickMultipartPosition(int headId, AnacondaPartIndex parentIndex, Vec3 parentPosition, float parentXRot, float parentYRot, float ourYRot, boolean doHeight) {
        Vec3 parentButt = parentPosition.add(calcOffsetVec(-parentIndex.getBackOffset() * this.getScale(), parentXRot, parentYRot));
        Vec3 ourButt = parentButt.add(calcOffsetVec((-this.getPartType().getBackOffset() - 0.5F * this.getBbWidth()) * this.getScale(), this.getXRot(), ourYRot));
        Vec3 avg = new Vec3((parentButt.x + ourButt.x) / 2F, (parentButt.y + ourButt.y) / 2F, (parentButt.z + ourButt.z) / 2F);
        double d0 = parentButt.x - ourButt.x;
        double d1 = parentButt.y - ourButt.y;
        double d2 = parentButt.z - ourButt.z;
        double d3 = Math.sqrt(d0 * d0 + d2 * d2);
        double hgt = doHeight ? (getLowPartHeight(parentButt.x, parentButt.y, parentButt.z) + getHighPartHeight(ourButt.x, ourButt.y, ourButt.z)) : 0;

        double partYDest = Mth.clamp(this.getScale() * 1.5F * hgt, -0.35F, 1);
        float f = (float) (Mth.atan2(d2, d0) * 57.2957763671875D) - 90.0F;
        float rawAngle = Mth.wrapDegrees((float) (-(Mth.atan2(partYDest, d3) * (double) (180F / (float) Math.PI))));
        float f2 = this.limitAngle(this.getXRot(), rawAngle, 10F);
        this.setXRot(f2);
        this.setYRot(f);
        this.yHeadRot = f;
        this.moveTo(avg.x, avg.y, avg.z, f, f2);
        headEntityId = headId;
        return avg;
    }

    public double getLowPartHeight(double x, double yIn, double z) {
        if(isFluidAt(x, yIn, z)){
            return 0.0;
        }
        double checkAt = 0D;
        while (checkAt > -3D && !isOpaqueBlockAt(x,yIn + checkAt, z)) {
            checkAt -= 0.2D;
        }
        return checkAt;
    }

    public double getHighPartHeight(double x, double yIn, double z) {
        if(isFluidAt(x, yIn, z)){
            return 0.0;
        }
        double checkAt = 3D;
        while (checkAt > 0) {
            if(isOpaqueBlockAt(x, yIn + checkAt, z)) {
                break;
            }
            checkAt -= 0.2D;
        }
        return checkAt;
    }


    public boolean isOpaqueBlockAt(double x, double y, double z) {
        if (this.noPhysics) {
            return false;
        } else {
            float f = 1F;
            Vec3 vec1 = new Vec3(x, y, z);
            AABB aabb = AABB.ofSize(vec1, f, 0.1F, f);
            return this.level.getBlockCollisions(this, aabb, (p_20129_, p_20130_) -> {
                return p_20129_.isSuffocating(this.level, p_20130_);
            }).findAny().isPresent();
        }
    }

    public boolean canBreatheUnderwater() {
        return true;
    }

    public boolean isPushedByFluid() {
        return false;
    }

    public boolean isFluidAt(double x, double y, double z) {
        if (this.noPhysics) {
            return false;
        } else {
            return !level.getFluidState(new BlockPos(x, y, z)).isEmpty();
        }
    }

    public boolean hurtHeadId(DamageSource source, float f){
        if(headEntityId != -1){
            Entity e = level.getEntity(headEntityId);
            if(e instanceof EntityAnaconda){
               return e.hurt(source, f);
            }
        }
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float damage) {
        Entity parent = getParent();
        boolean prev = hurtHeadId(source, damage);
        return prev;
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(CHILD_UUID, Optional.empty());
        this.entityData.define(PARENT_UUID, Optional.empty());
        this.entityData.define(BODYINDEX, 0);
        this.entityData.define(BODY_TYPE, AnacondaPartIndex.NECK.ordinal());
        this.entityData.define(TARGET_YAW, 0F);
        this.entityData.define(SWELL, 0F);
        this.entityData.define(YELLOW, false);
        this.entityData.define(SHEDDING, false);
        this.entityData.define(BABY, false);
    }


    public void pushEntities() {
        List<Entity> entities = this.level.getEntities(this, this.getBoundingBox().expandTowards(0.20000000298023224D, 0.0D, 0.20000000298023224D));
        Entity parent = this.getParent();
        if (parent != null) {
            entities.stream().filter(entity -> !entity.is(parent) && !(entity instanceof EntityAnacondaPart || entity instanceof EntityAnaconda) && entity.isPushable()).forEach(entity -> entity.push(parent));
        }
    }

    @Override
    public Iterable<ItemStack> getArmorSlots() {
        return ImmutableList.of();
    }

    @Override
    public ItemStack getItemBySlot(EquipmentSlot slotIn) {
        return ItemStack.EMPTY;
    }

    @Override
    public void setItemSlot(EquipmentSlot p_21036_, ItemStack p_21037_) {

    }

    @Override
    public HumanoidArm getMainArm() {
        return HumanoidArm.RIGHT;
    }

    @Override
    public void onAttackedFromServer(LivingEntity parent, float damage) {
        if (parent.deathTime > 0) {
            this.deathTime = parent.deathTime;
        }
        if (parent.hurtTime > 0) {
            this.hurtTime = parent.hurtTime;
        }
    }

    public Entity getParent() {
        UUID id = getParentId();
        if (id != null && !level.isClientSide) {
            return ((ServerLevel) level).getEntity(id);
        }
        return null;
    }

    public void setParent(Entity entity) {
        this.setParentId(entity.getUUID());
    }

    @Nullable
    public UUID getParentId() {
        return this.entityData.get(PARENT_UUID).orElse(null);
    }

    public void setParentId(@Nullable UUID uniqueId) {
        this.entityData.set(PARENT_UUID, Optional.ofNullable(uniqueId));
    }

    public Entity getChild() {
        UUID id = getChildId();
        if (id != null && !level.isClientSide) {
            return ((ServerLevel) level).getEntity(id);
        }
        return null;
    }

    @Nullable
    public UUID getChildId() {
        return this.entityData.get(CHILD_UUID).orElse(null);
    }

    public void setChildId(@Nullable UUID uniqueId) {
        this.entityData.set(CHILD_UUID, Optional.ofNullable(uniqueId));
    }

    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        if (this.getParentId() != null) {
            compound.putUUID("ParentUUID", this.getParentId());
        }
        if (this.getChildId() != null) {
            compound.putUUID("ChildUUID", this.getChildId());
        }
        compound.putInt("BodyModel", getPartType().ordinal());
        compound.putInt("BodyIndex", getBodyIndex());
    }

    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        if (compound.hasUUID("ParentUUID")) {
            this.setParentId(compound.getUUID("ParentUUID"));
        }
        if (compound.hasUUID("ChildUUID")) {
            this.setChildId(compound.getUUID("ChildUUID"));
        }
        this.setPartType(AnacondaPartIndex.fromOrdinal(compound.getInt("BodyModel")));
        this.setBodyIndex(compound.getInt("BodyIndex"));
    }

    @Override
    public boolean is(net.minecraft.world.entity.Entity entity) {
        return this == entity || this.getParent() == entity;
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    public int getBodyIndex() {
        return this.entityData.get(BODYINDEX);
    }

    public void setBodyIndex(int index) {
        this.entityData.set(BODYINDEX, index);
    }

    public AnacondaPartIndex getPartType() {
        return AnacondaPartIndex.fromOrdinal(this.entityData.get(BODY_TYPE));
    }

    public void setPartType(AnacondaPartIndex index) {
        this.entityData.set(BODY_TYPE, index.ordinal());
    }

    public void setTargetYaw(float f) {
        this.entityData.set(TARGET_YAW, f);
    }

    public void setSwell(float f) {
        this.entityData.set(SWELL, f);
    }

    public float getSwell(){
        return Math.min(this.entityData.get(SWELL), 5);
    }


    public float getSwellLerp(float partialTick) {
        return this.prevSwell + (Math.max(this.getSwell(), 0) - this.prevSwell) * partialTick;
    }


    @Override
    public float getYRot() {
        return super.getYRot();
    }

    public void setStrangleProgress(float f){
        this.strangleProgess = f;
    }

    public float getStrangleProgress(float partialTick){
        return this.prevStrangleProgess + (this.strangleProgess - this.prevStrangleProgess) * partialTick;
    }

    public void copyDataFrom(EntityAnaconda anaconda) {
        this.entityData.set(YELLOW, anaconda.isYellow());
        this.entityData.set(SHEDDING, anaconda.isShedding());
        this.entityData.set(BABY, anaconda.isBaby());
    }

    public boolean isYellow(){
        return this.entityData.get(YELLOW);
    }

    public boolean isShedding(){
        return this.entityData.get(SHEDDING);
    }

    @Override
    public boolean isBaby(){
        return this.entityData.get(BABY);
    }
}