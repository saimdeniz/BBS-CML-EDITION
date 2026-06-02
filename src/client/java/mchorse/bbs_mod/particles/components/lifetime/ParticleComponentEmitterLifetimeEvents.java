package mchorse.bbs_mod.particles.components.lifetime;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.math.molang.MolangException;
import mchorse.bbs_mod.math.molang.MolangParser;
import mchorse.bbs_mod.particles.components.IComponentEmitterInitialize;
import mchorse.bbs_mod.particles.components.IComponentEmitterUpdate;
import mchorse.bbs_mod.particles.components.ParticleComponentBase;
import mchorse.bbs_mod.particles.emitter.ParticleEmitter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ParticleComponentEmitterLifetimeEvents extends ParticleComponentBase implements IComponentEmitterInitialize, IComponentEmitterUpdate
{
    public String creationEvent = "";
    public String expirationEvent = "";
    public final List<TimelineEvent> timeline = new ArrayList<>();

    public static class TimelineEvent
    {
        public double time;
        public String eventName;

        public TimelineEvent(double time, String eventName)
        {
            this.time = time;
            this.eventName = eventName;
        }
    }

    @Override
    protected void toData(MapType data)
    {
        if (this.creationEvent != null && !this.creationEvent.isEmpty())
        {
            data.putString("creation_event", this.creationEvent);
        }
        if (this.expirationEvent != null && !this.expirationEvent.isEmpty())
        {
            data.putString("expiration_event", this.expirationEvent);
        }
    }

    @Override
    public ParticleComponentBase fromData(BaseType data, MolangParser parser) throws MolangException
    {
        if (!data.isMap())
        {
            return this;
        }

        MapType map = data.asMap();

        if (map.has("creation_event"))
        {
            this.creationEvent = map.getString("creation_event");
        }
        if (map.has("expiration_event"))
        {
            this.expirationEvent = map.getString("expiration_event");
        }
        if (map.has("timeline"))
        {
            BaseType tl = map.get("timeline");
            if (tl.isMap())
            {
                this.timeline.clear();
                for (Map.Entry<String, BaseType> entry : tl.asMap())
                {
                    try
                    {
                        double t = Double.parseDouble(entry.getKey());
                        String name = entry.getValue().asString();
                        this.timeline.add(new TimelineEvent(t, name));
                    }
                    catch (NumberFormatException e)
                    {}
                }
            }
        }

        return this;
    }

    @Override
    public void apply(ParticleEmitter emitter)
    {
        if (this.creationEvent != null && !this.creationEvent.isEmpty())
        {
            emitter.triggerEvent(this.creationEvent);
        }
    }

    @Override
    public void update(ParticleEmitter emitter)
    {
        if (!this.timeline.isEmpty())
        {
            double prevAge = (emitter.age - 1) / 20.0;
            double currentAge = emitter.age / 20.0;
            for (TimelineEvent event : this.timeline)
            {
                if (event.time > prevAge && event.time <= currentAge)
                {
                    emitter.triggerEvent(event.eventName);
                }
            }
        }
    }
}
