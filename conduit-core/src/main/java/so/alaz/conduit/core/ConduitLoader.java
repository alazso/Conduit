package so.alaz.conduit.core;

import io.papermc.paper.plugin.loader.PluginClasspathBuilder;
import io.papermc.paper.plugin.loader.PluginLoader;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Paper plugin loader. Conduit ships no shaded runtime libraries — Paper API is
 * provided by the server and optional integrations (PlaceholderAPI, bStats) are
 * soft dependencies loaded from the server when present — so no extra classpath
 * libraries are declared.
 */
@ApiStatus.Internal
public final class ConduitLoader implements PluginLoader {

    @Override
    public void classloader(@NotNull PluginClasspathBuilder classpathBuilder) {
        // No additional runtime libraries to inject.
    }
}
