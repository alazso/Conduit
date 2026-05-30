package so.alaz.conduit.api.caller;

import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.assertj.core.api.Assertions.assertThat;

class CallerTokenTest {

    private static Plugin stubPlugin(String name) {
        return (Plugin) Proxy.newProxyInstance(
                CallerTokenTest.class.getClassLoader(),
                new Class<?>[]{Plugin.class},
                (proxy, method, args) -> "getName".equals(method.getName()) ? name : null);
    }

    @Test
    void current_is_anonymous_outside_any_scope() {
        assertThat(CallerToken.current()).isSameAs(CallerToken.ANONYMOUS);
    }

    @Test
    void runWith_binds_token_for_the_scope_and_restores_after() {
        CallerToken token = CallerToken.create(stubPlugin("Shop"), "Shop");

        CallerToken.runWith(token, () -> assertThat(CallerToken.current()).isSameAs(token));

        assertThat(CallerToken.current()).isSameAs(CallerToken.ANONYMOUS);
    }

    @Test
    void callWith_returns_value_with_token_bound() throws Exception {
        CallerToken token = CallerToken.create(stubPlugin("Shop"), "Shop");

        String name = CallerToken.callWith(token, () -> CallerToken.current().pluginName());

        assertThat(name).isEqualTo("Shop");
        assertThat(token.tokenId()).isNotNull();
    }
}
