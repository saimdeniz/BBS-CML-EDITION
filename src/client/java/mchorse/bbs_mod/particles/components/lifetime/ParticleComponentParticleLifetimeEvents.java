package mchorse.bbs_mod.particles.components.lifetime;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.math.molang.MolangException;
import mchorse.bbs_mod.math.molang.MolangParser;
import mchorse.bbs_mod.particles.components.ParticleComponentBase;

public class ParticleComponentParticleLifetimeEvents extends ParticleComponentBase
{
    public String creationEvent = "";
    public String expirationEvent = "";

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

        return this;
    }
}
