package com.justify.firewall;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Checks the technical fields a model must never be trusted with: addresses, ports, dates.
 * Returns every problem at once so the form can show them together.
 */
public final class FirewallFormValidator {

    /** Longest temporary rule allowed without becoming "permanent". */
    public static final int MAX_TEMPORARY_DAYS = 365;

    private static final Pattern IPV4 = Pattern.compile(
            "((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)");
    private static final Pattern HOST = Pattern.compile(
            "(?=.{1,253}$)([a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)(\\.[a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)*");

    public record Problem(String field, String message) {
    }

    public List<Problem> validate(FirewallForm f, LocalDate today) {
        List<Problem> problems = new ArrayList<>();
        if (!isIpOrCidr(f.sourceIp())) {
            problems.add(new Problem("sourceIp", "must be an IPv4 address or subnet, e.g. 10.20.30.40 or 10.20.30.0/24"));
        } else if (f.sourceIp().strip().endsWith("/0")) {
            problems.add(new Problem("sourceIp", "0.0.0.0/0 (any) is not allowed as a source"));
        }
        if (present(f.sourceHostname()) && !isHostname(f.sourceHostname())) {
            problems.add(new Problem("sourceHostname", "must be a hostname like payroll-app-01"));
        }
        if (!isIpOrCidr(f.destinationIp())) {
            problems.add(new Problem("destinationIp", "must be an IPv4 address or subnet, e.g. 172.16.5.10"));
        } else if (f.destinationIp().strip().endsWith("/0")) {
            problems.add(new Problem("destinationIp", "0.0.0.0/0 (any) is not allowed as a destination"));
        }
        if (present(f.destinationHost())) {
            String dest = stripUrl(f.destinationHost());
            if (!isHostname(dest) && !isIpOrCidr(dest)) {
                problems.add(new Problem("destinationHost", "must be a hostname or URL"));
            }
        }
        String portProblem = portProblem(f.ports());
        if (portProblem != null) {
            problems.add(new Problem("ports", portProblem));
        }
        LocalDate start = f.startDate();
        if (start != null && start.isBefore(today)) {
            problems.add(new Problem("startDate", "cannot be in the past"));
        }
        if (f.durationType() == FirewallForm.DurationType.TEMPORARY) {
            LocalDate from = start == null || start.isBefore(today) ? today : start;
            if (f.endDate() == null) {
                problems.add(new Problem("endDate", "is required for temporary access"));
            } else if (!f.endDate().isAfter(from)) {
                problems.add(new Problem("endDate", "must be after the start date"));
            } else if (f.endDate().isAfter(from.plusDays(MAX_TEMPORARY_DAYS))) {
                problems.add(new Problem("endDate", "temporary access can last at most " + MAX_TEMPORARY_DAYS
                        + " days; request permanent access instead"));
            }
        }
        return problems;
    }

    private static boolean present(String s) {
        return s != null && !s.isBlank();
    }

    static boolean isHostname(String s) {
        return s != null && HOST.matcher(s.strip()).matches() && !s.strip().matches("[0-9.]+");
    }

    static boolean isIpOrCidr(String s) {
        if (s == null) {
            return false;
        }
        String v = s.strip();
        int slash = v.indexOf('/');
        if (slash < 0) {
            return IPV4.matcher(v).matches();
        }
        String mask = v.substring(slash + 1);
        return IPV4.matcher(v.substring(0, slash)).matches() && mask.matches("\\d{1,2}") && Integer.parseInt(mask) <= 32;
    }

    /** "https://api.bank.example/pay" -> "api.bank.example"; ports in host:port are dropped. */
    static String stripUrl(String s) {
        if (s == null) {
            return null;
        }
        String v = s.strip().replaceFirst("^[a-zA-Z][a-zA-Z0-9+.-]*://", "");
        int cut = v.indexOf('/');
        if (cut >= 0) {
            v = v.substring(0, cut);
        }
        int colon = v.indexOf(':');
        if (colon >= 0 && v.indexOf('/') < 0) {
            v = v.substring(0, colon);
        }
        return v;
    }

    /** Accepts "443", "443, 8443", "8000-8010". Null when valid. */
    static String portProblem(String ports) {
        if (ports == null || ports.isBlank()) {
            return "is required";
        }
        String[] parts = ports.split(",");
        if (parts.length > 20) {
            return "at most 20 ports or ranges";
        }
        for (String raw : parts) {
            String p = raw.strip();
            if (p.matches("\\d{1,5}")) {
                if (!inRange(p)) {
                    return "port " + p + " is outside 1-65535";
                }
            } else if (p.matches("\\d{1,5}-\\d{1,5}")) {
                String[] r = p.split("-");
                if (!inRange(r[0]) || !inRange(r[1]) || Integer.parseInt(r[0]) >= Integer.parseInt(r[1])) {
                    return "range " + p + " is invalid";
                }
                if (Integer.parseInt(r[1]) - Integer.parseInt(r[0]) > 1000) {
                    return "range " + p + " is wider than 1000 ports";
                }
            } else {
                return "'" + p + "' is not a port or range (use 443, 8443 or 8000-8010)";
            }
        }
        return null;
    }

    private static boolean inRange(String p) {
        int n = Integer.parseInt(p);
        return n >= 1 && n <= 65535;
    }

    /** Normalised "443, 8443" form for storage and display. */
    static String normalisePorts(String ports) {
        return String.join(", ", java.util.Arrays.stream(ports.split(",")).map(String::strip).toList());
    }
}
