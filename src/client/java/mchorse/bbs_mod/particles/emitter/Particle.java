package mchorse.bbs_mod.particles.emitter;

import mchorse.bbs_mod.utils.joml.Vectors;

import org.joml.Matrix3f;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

public class Particle
{
    /* Randoms */
    public float random1 = (float) Math.random();
    public float random2 = (float) Math.random();
    public float random3 = (float) Math.random();
    public float random4 = (float) Math.random();

    /* States */
    public final int index;
    public final float offset;
    public int age;
    public int lifetime;
    private boolean dead;
    public boolean relativePosition;
    public boolean relativeRotation;
    public boolean relativeVelocity;
    public boolean textureScale;
    public boolean manual;

    /* Rotation */
    public float rotation;
    public float initialRotation;
    public float prevRotation;

    public float rotationVelocity;
    public float rotationAcceleration;
    public float rotationDrag;

    /* Position */
    public Vector3d position = new Vector3d();
    public Vector3d initialPosition = new Vector3d();
    public Vector3d prevPosition = new Vector3d();
    public Matrix3f matrix = new Matrix3f();
    private boolean matrixSet;

    public Vector3f speed = new Vector3f();
    public Vector3f acceleration = new Vector3f();
    public Vector3f accelerationFactor = new Vector3f(1, 1, 1);
    public float drag = 0;
    public float dragFactor = 0;

    /* Color */
    public float r = 1;
    public float g = 1;
    public float b = 1;
    public float a = 1;

    private Vector3d global = new Vector3d();

    public Map<String, Double> localValues = new HashMap<>();

    public Particle(int index, float offset)
    {
        this.index = index;
        this.offset = offset;

        this.speed.set((float) Math.random() - 0.5F, (float) Math.random() - 0.5F, (float) Math.random() - 0.5F);
        this.speed.normalize();
    }

    public void setDead()
    {
        this.dead = true;
    }

    public boolean isDead()
    {
        return this.dead;
    }

    public double getDistanceSq(ParticleEmitter emitter)
    {
        Vector3d pos = this.getGlobalPosition(emitter);

        double dx = emitter.cX - pos.x;
        double dy = emitter.cY - pos.y;
        double dz = emitter.cZ - pos.z;

        return dx * dx + dy * dy + dz * dz;
    }

    public double getAge(float transition)
    {
        return (this.age + transition) / 20D;
    }

    public Vector3d getGlobalPosition(ParticleEmitter emitter)
    {
        return this.getGlobalPosition(emitter, this.position);
    }

    public Vector3d getGlobalPosition(ParticleEmitter emitter, Vector3d vector)
    {
        double px = vector.x;
        double py = vector.y;
        double pz = vector.z;

        if (this.relativePosition && this.relativeRotation)
        {
            Vector3f v = new Vector3f((float) px, (float) py, (float) pz);
            emitter.rotation.transform(v);

            px = v.x;
            py = v.y;
            pz = v.z;

            px += emitter.lastGlobal.x;
            py += emitter.lastGlobal.y;
            pz += emitter.lastGlobal.z;
        }

        this.global.set(px, py, pz);

        return this.global;
    }

    public void update(ParticleEmitter emitter)
    {
        this.prevRotation = this.rotation;
        this.prevPosition.set(this.position);

        this.setupMatrix(emitter);

        if (!this.manual)
        {
            float rotationAcceleration = this.rotationAcceleration / 20F - this.rotationDrag * this.rotationVelocity;

            this.rotationVelocity += rotationAcceleration / 20F;
            this.rotation = this.initialRotation + this.rotationVelocity * this.age;

            /* Position */
            Vector3f dragVec = new Vector3f(this.speed);
            dragVec.mul(-(this.drag + this.dragFactor));
            dragVec.mul(1 / 20F);

            if (this.speed.length() - dragVec.length() <= 0)
            {
                this.speed.set(0, 0, 0);
            }
            else
            {
                this.speed.add(dragVec);
            }

            Vector3f scaledAccel = new Vector3f(this.acceleration);
            scaledAccel.mul(1 / 20F);
            this.speed.add(scaledAccel);

            Vector3f vec = new Vector3f();

            if (this.relativeVelocity)
            {
                if (this.age == 0)
                {
                    this.matrix.transform(this.speed);
                }

                vec.set(this.speed);
                vec.x *= this.accelerationFactor.x;
                vec.y *= this.accelerationFactor.y;
                vec.z *= this.accelerationFactor.z;
            }
            else
            {
                vec.set(this.speed);
                vec.x *= this.accelerationFactor.x;
                vec.y *= this.accelerationFactor.y;
                vec.z *= this.accelerationFactor.z;

                this.matrix.transform(vec);
            }

            if (this.age == 0)
            {
                vec.mul(1F + this.offset);
            }

            this.position.x += vec.x / 20F;
            this.position.y += vec.y / 20F;
            this.position.z += vec.z / 20F;
        }

        if (this.lifetime >= 0 && this.age >= this.lifetime)
        {
            this.setDead();
        }

        this.age += 1;
        this.acceleration.set(0, 0, 0);
    }

    public void setupMatrix(ParticleEmitter emitter)
    {
        if (this.relativePosition)
        {
            if (this.relativeRotation)
            {
                this.matrix.identity();
            }
            else if (!this.matrixSet)
            {
                this.matrix.set(emitter.rotation);
                this.matrixSet = true;
            }
        }
        else if (this.relativeRotation)
        {
            this.matrix.set(emitter.rotation);
        }
        else if (this.relativeVelocity && !this.matrixSet)
        {
            this.matrix.set(emitter.rotation);
            this.matrixSet = true;
        }
        else if (this.textureScale && !this.matrixSet)
        {
            this.matrix.identity().scale(emitter.rotation.getRow(0, Vectors.TEMP_3F).length());
            this.matrixSet = true;
        }
    }

    public Particle softCopy(Particle to)
    {
        to.age = this.age;
        to.lifetime = this.lifetime;
        to.relativePosition = this.relativePosition;
        to.relativeRotation = this.relativeRotation;
        to.relativeVelocity = this.relativeVelocity;
        to.textureScale = this.textureScale;
        to.manual = this.manual;
        to.rotation = this.rotation;
        to.initialRotation = this.initialRotation;
        to.prevRotation = this.prevRotation;
        to.rotationVelocity = this.rotationVelocity;
        to.rotationAcceleration = this.rotationAcceleration;
        to.rotationDrag = this.rotationDrag;
        to.position.set(this.position);
        to.initialPosition.set(this.initialPosition);
        to.prevPosition.set(this.prevPosition);
        to.matrix.set(this.matrix);
        to.matrixSet = this.matrixSet;
        to.speed.set(this.speed);
        to.acceleration.set(this.acceleration);
        to.accelerationFactor.set(this.accelerationFactor);
        to.drag = this.drag;
        to.dragFactor = this.dragFactor;
        to.r = this.r;
        to.g = this.g;
        to.b = this.b;
        to.a = this.a;
        to.localValues.putAll(this.localValues);
        return to;
    }
}