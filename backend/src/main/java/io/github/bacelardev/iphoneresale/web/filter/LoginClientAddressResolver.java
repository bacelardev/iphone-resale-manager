package io.github.bacelardev.iphoneresale.web.filter;

import io.github.bacelardev.iphoneresale.config.properties.AuthProperties;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletRequestWrapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.stereotype.Component;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Resolves the client address that keys the login rate limit.
 *
 * <p>Forwarded headers are client-controlled unless a trusted proxy wrote them. With {@code N}
 * trusted proxy hops, each proxy appends the address it accepted the connection from, so only the
 * {@code N}th {@code X-Forwarded-For} entry counted from the right is proxy-written; every entry to
 * its left, and the RFC 7239 {@code Forwarded} header, may come from the client and are ignored.
 * With zero hops the TCP peer is used and forwarded headers are ignored entirely.
 *
 * <p>The container request is read directly, past any request wrapper: under
 * {@code server.forward-headers-strategy=framework}, Spring's {@code ForwardedHeaderFilter} wraps
 * the request so that {@code getRemoteAddr()} reports the leftmost, client-supplied address.
 *
 * <p>IPv6 addresses are keyed by their /64 prefix, the smallest block a single subscriber normally
 * controls, so rotating addresses within it does not create fresh buckets.
 */
@Component
public class LoginClientAddressResolver {

    static final String FORWARDED_FOR = "X-Forwarded-For";

    private static final String OCTET = "(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)";
    private static final Pattern IPV4 = Pattern.compile("^" + OCTET + "(\\." + OCTET + "){3}$");
    private static final Pattern IPV6 = Pattern.compile("^[0-9A-Fa-f:.]{2,45}$");

    private final int trustedProxyHops;

    @Autowired
    public LoginClientAddressResolver(AuthProperties properties, ServerProperties server) {
        this(properties.loginTrustedProxyHops(), server.getForwardHeadersStrategy());
    }

    LoginClientAddressResolver(int trustedProxyHops, ServerProperties.ForwardHeadersStrategy strategy) {
        if (trustedProxyHops > 0 && strategy == ServerProperties.ForwardHeadersStrategy.NATIVE) {
            // Tomcat's RemoteIpValve would already have consumed X-Forwarded-For.
            throw new IllegalStateException("app.auth.login-trusted-proxy-hops requires "
                    + "server.forward-headers-strategy none or framework");
        }
        this.trustedProxyHops = trustedProxyHops;
    }

    public String resolve(HttpServletRequest request) {
        HttpServletRequest container = containerRequest(request);
        String peer = container.getRemoteAddr();
        if (trustedProxyHops > 0) {
            String proxyWritten = key(proxyWrittenEntry(container));
            if (proxyWritten != null) return proxyWritten;
        }
        String peerKey = key(peer);
        return peerKey != null ? peerKey : peer;
    }

    private String proxyWrittenEntry(HttpServletRequest request) {
        List<String> entries = new ArrayList<>();
        Enumeration<String> values = request.getHeaders(FORWARDED_FOR);
        while (values != null && values.hasMoreElements()) {
            for (String entry : values.nextElement().split(",", -1)) {
                entries.add(entry.trim());
            }
        }
        // Fewer entries than trusted hops: the request did not traverse the expected proxy chain.
        return entries.size() < trustedProxyHops ? null : entries.get(entries.size() - trustedProxyHops);
    }

    static String key(String literal) {
        if (literal == null) return null;
        String value = literal.trim();
        if (value.length() > 2 && value.startsWith("[") && value.endsWith("]")) {
            value = value.substring(1, value.length() - 1);
        }
        boolean ipv4 = IPV4.matcher(value).matches();
        boolean ipv6 = !ipv4 && value.indexOf(':') >= 0 && IPV6.matcher(value).matches();
        if (!ipv4 && !ipv6) return null;
        try {
            // Only validated literals reach this point, so no DNS lookup can be triggered.
            InetAddress address = InetAddress.getByName(value);
            if (address instanceof Inet6Address) {
                byte[] prefix = address.getAddress();
                Arrays.fill(prefix, 8, 16, (byte) 0);
                return InetAddress.getByAddress(prefix).getHostAddress() + "/64";
            }
            return address.getHostAddress();
        } catch (UnknownHostException exception) {
            return null;
        }
    }

    private static HttpServletRequest containerRequest(HttpServletRequest request) {
        ServletRequest current = request;
        while (current instanceof ServletRequestWrapper wrapper) {
            current = wrapper.getRequest();
        }
        return current instanceof HttpServletRequest container ? container : request;
    }
}
