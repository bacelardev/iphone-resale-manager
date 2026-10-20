package io.github.bacelardev.iphoneresale.web.filter;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.web.ServerProperties.ForwardHeadersStrategy;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.filter.ForwardedHeaderFilter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoginClientAddressResolverTest {

    private static final String PEER = "10.0.0.5";
    private static final String PROXY_WRITTEN = "203.0.113.7";

    @Test
    void springForwardedHeaderFilterReportsTheClientSuppliedLeftmostAddress() throws Exception {
        HttpServletRequest wrapped = throughSpringForwardedHeaderFilter(
                request(PEER, "198.51.100.66, " + PROXY_WRITTEN));

        // Root cause: this is the value the login limiter used under the framework strategy.
        assertThat(wrapped.getRemoteAddr()).isEqualTo("198.51.100.66");
        assertThat(wrapped.getHeader(LoginClientAddressResolver.FORWARDED_FOR)).isNull();

        assertThat(resolver(1).resolve(wrapped)).isEqualTo(PROXY_WRITTEN);
        assertThat(resolver(0).resolve(wrapped)).isEqualTo(PEER);
    }

    @Test
    void springForwardedHeaderFilterPrefersTheClientSuppliedForwardedHeader() throws Exception {
        MockHttpServletRequest raw = request(PEER, PROXY_WRITTEN);
        raw.addHeader("Forwarded", "for=198.51.100.77");
        HttpServletRequest wrapped = throughSpringForwardedHeaderFilter(raw);

        assertThat(wrapped.getRemoteAddr()).isEqualTo("198.51.100.77");
        assertThat(resolver(1).resolve(wrapped)).isEqualTo(PROXY_WRITTEN);
    }

    @Test
    void zeroHopsUsesTheTcpPeerAndIgnoresEveryForwardingHeader() {
        MockHttpServletRequest raw = request(PEER, "198.51.100.1, " + PROXY_WRITTEN);
        raw.addHeader("Forwarded", "for=198.51.100.2");

        assertThat(resolver(0).resolve(raw)).isEqualTo(PEER);
    }

    @Test
    void oneHopUsesOnlyTheProxyWrittenRightmostEntry() {
        LoginClientAddressResolver resolver = resolver(1);

        assertThat(resolver.resolve(request(PEER, PROXY_WRITTEN))).isEqualTo(PROXY_WRITTEN);
        for (String spoofed : new String[]{"198.51.100.1", "198.51.100.2, 198.51.100.3", "unknown"}) {
            assertThat(resolver.resolve(request(PEER, spoofed + ", " + PROXY_WRITTEN)))
                    .isEqualTo(PROXY_WRITTEN);
        }
    }

    @Test
    void combinesRepeatedForwardedForLinesInArrivalOrder() {
        MockHttpServletRequest raw = request(PEER, "198.51.100.1, 198.51.100.2");
        raw.addHeader(LoginClientAddressResolver.FORWARDED_FOR, PROXY_WRITTEN);

        assertThat(resolver(1).resolve(raw)).isEqualTo(PROXY_WRITTEN);
        assertThat(resolver(2).resolve(raw)).isEqualTo("198.51.100.2");
    }

    @Test
    void twoHopsSkipsTheAddressWrittenByTheInnerProxy() {
        assertThat(resolver(2).resolve(request(PEER, "198.51.100.9, " + PROXY_WRITTEN + ", 10.1.2.3")))
                .isEqualTo(PROXY_WRITTEN);
    }

    @Test
    void fallsBackToThePeerWhenTheChainIsShorterThanTheTrustedHops() {
        assertThat(resolver(2).resolve(request(PEER, PROXY_WRITTEN))).isEqualTo(PEER);
        assertThat(resolver(1).resolve(request(PEER, null))).isEqualTo(PEER);
    }

    @Test
    void neverResolvesFromTheForwardedHeader() {
        MockHttpServletRequest raw = request(PEER, null);
        raw.addHeader("Forwarded", "for=198.51.100.5;proto=https");

        assertThat(resolver(1).resolve(raw)).isEqualTo(PEER);
    }

    @Test
    void fallsBackToThePeerForEntriesThatAreNotStrictIpLiterals() {
        for (String invalid : new String[]{"unknown", "evil.example", "203.0.113.7:443", "", "1.2.3",
                "001.2.3.4", "256.1.1.1", "_hidden", "::1%eth0", "[::1", "203.0.113.7 extra"}) {
            assertThat(resolver(1).resolve(request(PEER, "198.51.100.1, " + invalid)))
                    .as(invalid).isEqualTo(PEER);
        }
    }

    @Test
    void normalizesEquivalentLiteralsToOneKey() {
        LoginClientAddressResolver resolver = resolver(1);

        assertThat(resolver.resolve(request(PEER, "::ffff:203.0.113.7"))).isEqualTo(PROXY_WRITTEN);
        assertThat(resolver.resolve(request(PEER, "[2001:db8::1]")))
                .isEqualTo(resolver.resolve(request(PEER, "2001:0db8:0:0:0:0:0:1")));
    }

    @Test
    void bucketsIpv6AddressesByTheirSlash64Prefix() {
        LoginClientAddressResolver resolver = resolver(1);
        String first = resolver.resolve(request(PEER, "2001:db8:1:2::1"));

        assertThat(first).isEqualTo("2001:db8:1:2:0:0:0:0/64");
        assertThat(resolver.resolve(request(PEER, "2001:db8:1:2:ffff:abcd::9"))).isEqualTo(first);
        assertThat(resolver.resolve(request(PEER, "2001:db8:1:3::1"))).isNotEqualTo(first);
        assertThat(resolver(0).resolve(request("2001:db8:1:2::42", null))).isEqualTo(first);
    }

    @Test
    void refusesTrustedHopsWhenTomcatAlreadyConsumesForwardedHeaders() {
        assertThatThrownBy(() -> new LoginClientAddressResolver(1, ForwardHeadersStrategy.NATIVE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("login-trusted-proxy-hops");
        assertThat(new LoginClientAddressResolver(0, ForwardHeadersStrategy.NATIVE)
                .resolve(request(PEER, PROXY_WRITTEN))).isEqualTo(PEER);
    }

    private static LoginClientAddressResolver resolver(int hops) {
        return new LoginClientAddressResolver(hops, ForwardHeadersStrategy.FRAMEWORK);
    }

    private static MockHttpServletRequest request(String peer, String forwardedFor) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.setRemoteAddr(peer);
        if (forwardedFor != null) request.addHeader(LoginClientAddressResolver.FORWARDED_FOR, forwardedFor);
        return request;
    }

    private static HttpServletRequest throughSpringForwardedHeaderFilter(MockHttpServletRequest raw)
            throws Exception {
        MockFilterChain chain = new MockFilterChain();
        new ForwardedHeaderFilter().doFilter(raw, new MockHttpServletResponse(), chain);
        return (HttpServletRequest) chain.getRequest();
    }
}
