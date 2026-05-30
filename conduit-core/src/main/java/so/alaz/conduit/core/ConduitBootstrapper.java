package so.alaz.conduit.core;

import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Paper plugin bootstrapper. Conduit needs no special bootstrap-phase wiring;
 * runtime services are constructed in {@link ConduitPlugin#onEnable()}. The
 * class exists so the {@code paper-plugin.yml} bootstrapper declaration resolves
 * and to provide a hook for future bootstrap-phase registration.
 */
@ApiStatus.Internal
public final class ConduitBootstrapper implements PluginBootstrap {

    @Override
    public void bootstrap(@NotNull BootstrapContext context) {
        // No bootstrap-phase services required.
    }
}
