package so.alaz.conduit.api.capability;

import org.jetbrains.annotations.ApiStatus;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Documents that a method depends on a fine-grained {@link Capability} the
 * provider must declare.
 *
 * <p>This applies only to fine-grained capabilities. Structural capabilities
 * (banking, transaction history, multi-currency) are enforced by the type
 * system through extension interfaces and must never be gated with this
 * annotation. Calling an annotated method on a provider that does not declare
 * the capability throws
 * {@link so.alaz.conduit.api.exception.CapabilityNotSupportedException}.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@ApiStatus.AvailableSince("1.0.0")
public @interface RequiresCapability {
    /**
     * @return the capability the annotated method requires
     */
    Capability value();
}
