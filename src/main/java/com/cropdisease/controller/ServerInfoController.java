package com.cropdisease.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.*;

/**
 * Tells the QR page (qr.html) which Wi-Fi address a phone should open,
 * e.g. http://192.168.31.236:8081. Detected every time, so it follows IP changes.
 */
@RestController
@RequestMapping("/api/server-info")
public class ServerInfoController {

    @Value("${server.port:8081}")
    private int port;

    @GetMapping
    public Map<String, Object> serverInfo() throws Exception {
        String cloud = cloudUrl();
        if (cloud != null) {
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("urls", List.of(cloud));
            info.put("publicUrl", cloud);
            info.put("cloud", true);
            info.put("port", port);
            return info;
        }
        List<String> preferred = new ArrayList<>();
        List<String> others = new ArrayList<>();
        for (NetworkInterface nic : Collections.list(NetworkInterface.getNetworkInterfaces())) {
            if (!nic.isUp() || nic.isLoopback() || nic.isVirtual()) continue;
            String name = (nic.getDisplayName() + " " + nic.getName()).toLowerCase(Locale.ROOT);
            if (name.matches(".*(virtual|vmware|vbox|hyper-v|wsl|loopback|bluetooth|tap|tunnel|vpn).*")) continue;
            for (InetAddress a : Collections.list(nic.getInetAddresses())) {
                if (a instanceof Inet4Address && a.isSiteLocalAddress()) {
                    String url = "http://" + a.getHostAddress() + ":" + port;
                    if (name.contains("wi-fi") || name.contains("wireless") || name.contains("wlan")) preferred.add(url);
                    else others.add(url);
                }
            }
        }
        List<String> urls = new ArrayList<>(preferred);
        urls.addAll(others);
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("urls", urls);
        info.put("port", port);
        return info;
    }

    /** Permanent public URL when running on a cloud platform (set by the platform or by APP_PUBLIC_URL). */
    private static String cloudUrl() {
        String explicit = System.getenv("APP_PUBLIC_URL");
        if (explicit != null && explicit.startsWith("https://")) return explicit.replaceAll("/+$", "");
        String render = System.getenv("RENDER_EXTERNAL_URL");
        if (render != null && render.startsWith("https://")) return render.replaceAll("/+$", "");
        String railway = System.getenv("RAILWAY_PUBLIC_DOMAIN");
        if (railway != null && !railway.isBlank()) return "https://" + railway;
        return null;
    }
}
