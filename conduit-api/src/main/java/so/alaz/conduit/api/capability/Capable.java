package so.alaz.conduit.api.capability;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * Implemented by providers that advertise fine-grained {@link Capability} flags.
 *
 * <p>Consumers query {@link #supports(Capability)} before calling a
 * capability-gated method; calling such a method without the capability is the
 * case {@link so.alaz.conduit.api.exception.CapabilityNotSupportedException}
 * exists for.
 */
@ApiStatus.AvailableSince("1.0.0")
public interface Capable {

    /**
     * @return the immutable set of fine-grained capabilities this provider
     *         supports; never {@code null}
     */
    @NotNull Set<Capability> capabilities();

    /**
     * @param capability the capability to test
     * @return {@code true} if this provider declares {@code capability}
     */
    default boolean supports(@NotNull Capability capability) {
        return capabilities().contains(capability);
    }
}
