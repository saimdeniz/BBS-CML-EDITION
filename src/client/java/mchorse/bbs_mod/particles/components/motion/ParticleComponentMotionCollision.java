package mchorse.bbs_mod.particles.components.motion;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.math.Operation;
import mchorse.bbs_mod.math.molang.MolangException;
import mchorse.bbs_mod.math.molang.MolangParser;
import mchorse.bbs_mod.math.molang.expressions.MolangExpression;
import mchorse.bbs_mod.particles.components.IComponentParticleUpdate;
import mchorse.bbs_mod.particles.components.ParticleComponentBase;
import mchorse.bbs_mod.particles.emitter.Particle;
import mchorse.bbs_mod.particles.emitter.ParticleEmitter;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import org.joml.Vector3d;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class ParticleComponentMotionCollision extends ParticleComponentBase implements IComponentParticleUpdate
{
    public MolangExpression enabled = MolangParser.ONE;
    public float collisionDrag = 0;
    public float bounciness = 1.0F;
    public float bouncinessRandomness = 0;
    public float radius = 0.01F;
    public boolean expireOnImpact;
    public int splitCount = 0;
    public float splitSpeedThreshold = 0;

    /* Runtime options */
    private Vector3d previous = new Vector3d();
    private Vector3d current = new Vector3d();

    @Override
    public BaseType toData()
    {
        MapType object = new MapType();

        if (MolangExpression.isZero(this.enabled))
        {
            return object;
        }

        if (!MolangExpression.isOne(this.enabled)) object.put("enabled", this.enabled.toData());
        if (this.collisionDrag != 0) object.putFloat("collision_drag", this.collisionDrag);
        if (this.bounciness != 1.0F)
        {
            object.putFloat("coefficient_of_restitution", this.bounciness);
        }
        if (this.bouncinessRandomness != 0) object.putFloat("bounciness_randomness", this.bouncinessRandomness);
        if (this.radius != 0.01F) object.putFloat("collision_radius", this.radius);
        if (this.expireOnImpact) object.putBool("expire_on_contact", true);
        if (this.splitCount != 0) object.putInt("split_particle_count", this.splitCount);
        if (this.splitSpeedThreshold != 0) object.putFloat("split_particle_speedThreshold", this.splitSpeedThreshold);

        return object;
    }

    @Override
    public ParticleComponentBase fromData(BaseType data, MolangParser parser) throws MolangException
    {
        if (!data.isMap())
        {
            return super.fromData(data, parser);
        }

        MapType map = data.asMap();

        if (map.has("enabled")) this.enabled = parser.parseDataSilently(map.get("enabled"));
        if (map.has("collision_drag")) this.collisionDrag = map.getFloat("collision_drag");
        if (map.has("coefficient_of_restitution"))
        {
            this.bounciness = map.getFloat("coefficient_of_restitution");
        }
        if (map.has("bounciness_randomness")) this.bouncinessRandomness = map.getFloat("bounciness_randomness");
        if (map.has("collision_radius")) this.radius = map.getFloat("collision_radius");
        if (map.has("expire_on_contact")) this.expireOnImpact = map.getBool("expire_on_contact");
        if (map.has("split_particle_count")) this.splitCount = map.getInt("split_particle_count");
        if (map.has("split_particle_speedThreshold")) this.splitSpeedThreshold = map.getFloat("split_particle_speedThreshold");

        return super.fromData(map, parser);
    }

    @Override
    public void update(ParticleEmitter emitter, Particle particle)
    {
        if (emitter.world == null)
        {
            return;
        }

        if (!particle.manual && Operation.equals(this.enabled.get(), 1))
        {
            float r = this.radius;

            this.previous.set(particle.getGlobalPosition(emitter, particle.prevPosition));
            this.current.set(particle.getGlobalPosition(emitter));

            Vector3d prev = this.previous;
            Vector3d now = this.current;

            double x = now.x - prev.x;
            double y = now.y - prev.y;
            double z = now.z - prev.z;
            boolean veryBig = Math.abs(x) > 10 || Math.abs(y) > 10 || Math.abs(z) > 10;

            if (veryBig)
            {
                return;
            }

            Box box = new Box(prev.x - r, prev.y - r, prev.z - r, prev.x + r, prev.y + r, prev.z + r);
            Vec3d vec = Entity.adjustMovementForCollisions(null, new Vec3d(x, y, z), box, emitter.world, Collections.emptyList());

            double blockDistSq = vec.lengthSquared();
            boolean hitBlock = (vec.x != x || vec.y != y || vec.z != z);

            double minX = Math.min(prev.x, now.x) - r;
            double minY = Math.min(prev.y, now.y) - r;
            double minZ = Math.min(prev.z, now.z) - r;
            double maxX = Math.max(prev.x, now.x) + r;
            double maxY = Math.max(prev.y, now.y) + r;
            double maxZ = Math.max(prev.z, now.z) + r;
            Box collisionBox = new Box(minX, minY, minZ, maxX, maxY, maxZ);

            List<Entity> entities = emitter.world.getOtherEntities(null, collisionBox);

            boolean hitEntity = false;
            double hitX = x;
            double hitY = y;
            double hitZ = z;
            Entity closestEntity = null;
            double closestDistanceSq = Double.MAX_VALUE;
            Vec3d start = new Vec3d(prev.x, prev.y, prev.z);
            Vec3d end = new Vec3d(now.x, now.y, now.z);

            for (Entity entity : entities)
            {
                if (entity == emitter.target)
                {
                    continue;
                }

                Box entityBox = entity.getBoundingBox();
                Box expandedBox = entityBox.expand(r);
                Optional<Vec3d> hitOpt = expandedBox.raycast(start, end);
                if (hitOpt.isPresent())
                {
                    Vec3d hitPoint = hitOpt.get();
                    double distSq = start.squaredDistanceTo(hitPoint);
                    if (distSq < closestDistanceSq)
                    {
                        closestDistanceSq = distSq;
                        closestEntity = entity;
                        hitX = hitPoint.x - prev.x;
                        hitY = hitPoint.y - prev.y;
                        hitZ = hitPoint.z - prev.z;
                        hitEntity = true;
                    }
                }
            }

            if (hitEntity && (!hitBlock || closestDistanceSq < blockDistSq))
            {
                if (this.expireOnImpact)
                {
                    particle.setDead();

                    return;
                }

                if (particle.relativePosition)
                {
                    particle.relativePosition = false;
                    particle.prevPosition.set(prev);
                }

                now.set(prev).add(hitX, hitY, hitZ);

                Box entityBox = closestEntity.getBoundingBox().expand(r);
                double distMinX = Math.abs(now.x - entityBox.minX);
                double distMaxX = Math.abs(now.x - entityBox.maxX);
                double distMinY = Math.abs(now.y - entityBox.minY);
                double distMaxY = Math.abs(now.y - entityBox.maxY);
                double distMinZ = Math.abs(now.z - entityBox.minZ);
                double distMaxZ = Math.abs(now.z - entityBox.maxZ);

                double minDistance = Math.min(Math.min(Math.min(distMinX, distMaxX), Math.min(distMinY, distMaxY)), Math.min(distMinZ, distMaxZ));

                float b = this.bounciness;
                if (this.bouncinessRandomness != 0)
                {
                    b += (float) (Math.random() * 2 - 1) * this.bouncinessRandomness;
                    b = Math.max(0, b);
                }

                if (this.splitCount > 0)
                {
                    int axis = 2;
                    if (minDistance == distMinX || minDistance == distMaxX) axis = 0;
                    else if (minDistance == distMinY || minDistance == distMaxY) axis = 1;

                    this.splitParticle(emitter, particle, axis, now, prev, b);
                    if (particle.isDead())
                    {
                        return;
                    }
                }

                if (minDistance == distMinX || minDistance == distMaxX)
                {
                    particle.accelerationFactor.x *= -b;
                }
                else if (minDistance == distMinY || minDistance == distMaxY)
                {
                    particle.accelerationFactor.y *= -b;
                }
                else
                {
                    particle.accelerationFactor.z *= -b;
                }

                particle.position.set(now);
                particle.dragFactor += this.collisionDrag;
            }
            else if (hitBlock)
            {
                if (this.expireOnImpact)
                {
                    particle.setDead();

                    return;
                }

                if (particle.relativePosition)
                {
                    particle.relativePosition = false;
                    particle.prevPosition.set(prev);
                }

                now.set(prev).add(vec.x, vec.y, vec.z);

                float b = this.bounciness;
                if (this.bouncinessRandomness != 0)
                {
                    b += (float) (Math.random() * 2 - 1) * this.bouncinessRandomness;
                    b = Math.max(0, b);
                }

                if (this.splitCount > 0)
                {
                    int axis = 2;
                    if (vec.x != x) axis = 0;
                    else if (vec.y != y) axis = 1;

                    this.splitParticle(emitter, particle, axis, now, prev, b);
                    if (particle.isDead())
                    {
                        return;
                    }
                }

                if (vec.y != y)
                {
                    particle.accelerationFactor.y *= -b;
                }

                if (vec.x != x)
                {
                    particle.accelerationFactor.x *= -b;
                }

                if (vec.z != z)
                {
                    particle.accelerationFactor.z *= -b;
                }

                particle.position.set(now);
                particle.dragFactor += this.collisionDrag;
            }
        }
    }

    private void splitParticle(ParticleEmitter emitter, Particle particle, int axis, Vector3d now, Vector3d prev, float bounceCoeff)
    {
        float speed = 0.0F;
        if (axis == 0) speed = particle.speed.x;
        else if (axis == 1) speed = particle.speed.y;
        else if (axis == 2) speed = particle.speed.z;

        if (Math.abs(speed) <= Math.abs(this.splitSpeedThreshold))
        {
            return;
        }

        for (int i = 0; i < this.splitCount; i++)
        {
            Particle splitParticle = emitter.createParticle(0.0F);
            particle.softCopy(splitParticle);

            splitParticle.position.set(now);
            splitParticle.prevPosition.set(prev);

            org.joml.Vector3f splitSpeed = new org.joml.Vector3f(particle.speed);
            if (axis == 0)
            {
                splitSpeed.x *= -bounceCoeff;
                splitSpeed.y += (float) (Math.random() - 0.5) * 2.0F;
                splitSpeed.z += (float) (Math.random() - 0.5) * 2.0F;
            }
            else if (axis == 1)
            {
                splitSpeed.y *= -bounceCoeff;
                splitSpeed.x += (float) (Math.random() - 0.5) * 2.0F;
                splitSpeed.z += (float) (Math.random() - 0.5) * 2.0F;
            }
            else
            {
                splitSpeed.z *= -bounceCoeff;
                splitSpeed.x += (float) (Math.random() - 0.5) * 2.0F;
                splitSpeed.y += (float) (Math.random() - 0.5) * 2.0F;
            }
            splitSpeed.mul(1.0F / this.splitCount);
            splitParticle.speed.set(splitSpeed);

            emitter.splitParticles.add(splitParticle);
        }

        particle.setDead();
    }

    @Override
    public int getSortingIndex()
    {
        return 50;
    }
}