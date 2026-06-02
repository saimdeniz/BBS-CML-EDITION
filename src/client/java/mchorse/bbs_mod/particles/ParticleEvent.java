package mchorse.bbs_mod.particles;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.math.molang.MolangParser;
import mchorse.bbs_mod.math.molang.expressions.MolangExpression;
import mchorse.bbs_mod.particles.emitter.Particle;
import mchorse.bbs_mod.particles.emitter.ParticleEmitter;
import mchorse.bbs_mod.resources.Link;

import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ParticleEvent
{
    public final List<Action> actions = new ArrayList<>();

    public void fromData(BaseType data, MolangParser parser)
    {
        this.actions.clear();
        if (data.isMap())
        {
            this.parseAction(data.asMap(), parser);
        }
        else if (data.isList())
        {
            for (BaseType element : data.asList())
            {
                if (element.isMap())
                {
                    this.parseAction(element.asMap(), parser);
                }
            }
        }
    }

    private void parseAction(MapType map, MolangParser parser)
    {
        if (map.has("sequence"))
        {
            BaseType seq = map.get("sequence");
            if (seq.isList())
            {
                for (BaseType element : seq.asList())
                {
                    if (element.isMap())
                    {
                        this.parseAction(element.asMap(), parser);
                    }
                }
            }
            return;
        }

        if (map.has("particle_effect"))
        {
            BaseType pe = map.get("particle_effect");
            if (pe.isMap())
            {
                MapType peMap = pe.asMap();
                String effect = peMap.getString("effect");
                String type = peMap.has("type") ? peMap.getString("type") : "emitter";
                MolangExpression preExpression = MolangParser.ZERO;
                if (peMap.has("pre_effect_expression"))
                {
                    try
                    {
                        preExpression = parser.parseDataSilently(peMap.get("pre_effect_expression"));
                    }
                    catch (Exception e)
                    {}
                }
                this.actions.add(new ParticleEffectAction(effect, type, preExpression));
            }
        }

        if (map.has("sound_effect") || map.has("play_sound"))
        {
            BaseType se = map.get(map.has("sound_effect") ? "sound_effect" : "play_sound");
            if (se.isMap())
            {
                MapType seMap = se.asMap();
                String sound = seMap.getString("sound");
                this.actions.add(new SoundEffectAction(sound));
            }
        }
    }

    public static abstract class Action
    {
        public abstract void execute(ParticleEmitter parent, Particle particle);
    }

    public static class ParticleEffectAction extends Action
    {
        public final String effect;
        public final String type;
        public final MolangExpression preExpression;

        public ParticleEffectAction(String effect, String type, MolangExpression preExpression)
        {
            this.effect = effect;
            this.type = type;
            this.preExpression = preExpression;
        }

        @Override
        public void execute(ParticleEmitter parent, Particle particle)
        {
            try
            {
                if (this.preExpression != null)
                {
                    this.preExpression.get();
                }
                ParticleScheme childScheme = BBSModClient.getParticles().load(this.effect);
                if (childScheme != null)
                {
                    ParticleEmitter childEmitter = new ParticleEmitter();
                    childEmitter.setScheme(childScheme);
                    childEmitter.setWorld(parent.world);
                    childEmitter.target = parent.target;

                    if (particle != null)
                    {
                        childEmitter.lastGlobal.set(particle.position);
                    }
                    else
                    {
                        childEmitter.lastGlobal.set(parent.lastGlobal);
                    }
                    childEmitter.rotation.set(parent.rotation);

                    childEmitter.playing = false;
                    childEmitter.start();

                    parent.childEmitters.add(childEmitter);
                }
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }
    }

    public static class SoundEffectAction extends Action
    {
        public final String sound;

        public SoundEffectAction(String sound)
        {
            this.sound = sound;
        }

        @Override
        public void execute(ParticleEmitter parent, Particle particle)
        {
            try
            {
                Link soundLink = Link.create(this.sound);
                BBSModClient.getSounds().play(soundLink);
            }
            catch (Exception e)
            {}
        }
    }
}
