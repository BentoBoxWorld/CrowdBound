package world.bentobox.crowdbound;


import world.bentobox.bentobox.api.addons.Addon;
import world.bentobox.bentobox.api.addons.Pladdon;


public class CrowdBoundPladdon extends Pladdon {

    private CrowdBound addon;

    @Override
    public Addon getAddon() {
        if (addon == null) {
            addon = new CrowdBound();
        }
        return addon;
    }

}
