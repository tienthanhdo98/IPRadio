package app.tek4tv.ipradio.utils.su;

import android.util.Log;

import java.util.ArrayList;
import java.util.List;

public class EthernetStaticIpUtils {

    private static final String TAG = "EthernetStaticIpUtils";

    public static boolean forceStaticIp(String ip, String gateway, String dns1, String dns2) {
        final String iface = "eth0";
        final int prefixLength = 24;
        final String subnet = buildSubnet(ip, prefixLength);

        List<String> commands = new ArrayList<>();

        // 0) Lưu config phụ vào settings/global nếu ROM có đọc
        commands.add("settings put global ethernet_static_ip " + ip);
        commands.add("settings put global ethernet_static_netmask 255.255.255.0");
        commands.add("settings put global ethernet_static_gateway " + gateway);
        commands.add("settings put global ethernet_static_dns1 " + dns1);
        if (dns2 != null && !dns2.trim().isEmpty()) {
            commands.add("settings put global ethernet_static_dns2 " + dns2);
        }
        commands.add("settings put global ethernet_use_static_ip 1");

        // 1) Bring up interface
        commands.add("ip link set " + iface + " up");

        // 2) Xóa IP IPv4 cũ
        commands.add("ip -4 addr flush dev " + iface);

        // 3) Flush route các table Android hay dùng trên box này
        commands.add("ip route flush table main || true");
        commands.add("ip route flush table legacy_network || true");
        commands.add("ip route flush table " + iface + " || true");
        commands.add("ip route flush table local_network || true");

        // 4) Add IP mới
        commands.add("ip addr add " + ip + "/" + prefixLength + " dev " + iface);

        // 5) Main table
        commands.add("ip route replace " + subnet + " dev " + iface + " src " + ip);
        commands.add("ip route replace default via " + gateway + " dev " + iface);

        // 6) Android policy tables
        commands.add("ip route replace " + subnet + " dev " + iface + " src " + ip + " table legacy_network");
        commands.add("ip route replace default via " + gateway + " dev " + iface + " table legacy_network");

        commands.add("ip route replace " + subnet + " dev " + iface + " src " + ip + " table " + iface);
        commands.add("ip route replace default via " + gateway + " dev " + iface + " table " + iface);

        commands.add("ip route replace " + subnet + " dev " + iface + " src " + ip + " table local_network");

        // 7) DNS properties
        if (dns1 != null && !dns1.trim().isEmpty()) {
            commands.add("setprop net.dns1 " + dns1);
            commands.add("setprop net." + iface + ".dns1 " + dns1);
        }
        if (dns2 != null && !dns2.trim().isEmpty()) {
            commands.add("setprop net.dns2 " + dns2);
            commands.add("setprop net." + iface + ".dns2 " + dns2);
        }
        commands.add("setprop net." + iface + ".gw " + gateway);

        // 8) Dump để debug
        commands.add("ip address show dev " + iface);
        commands.add("ip rule");
        commands.add("ip route show table all");
        commands.add("getprop | grep net.dns || true");
        commands.add("getprop | grep net." + iface + " || true");
        commands.add("ping -c 1 " + gateway + " || true");
        commands.add("ping -c 1 8.8.8.8 || true");

        for (String cmd : commands) {
            Log.d(TAG, "CMD: " + cmd);
        }

        boolean success = NetworkShellUtils.executeSuCommands(commands.toArray(new String[0]));

        if (success) {
            Log.d(TAG, "forceStaticIp: success");
        } else {
            Log.e(TAG, "forceStaticIp: failed");
        }

        return success;
    }

    private static String buildSubnet(String ip, int prefixLength) {
        String[] parts = ip.split("\\.");
        if (parts.length != 4) {
            throw new IllegalArgumentException("IP không hợp lệ: " + ip);
        }

        int a = Integer.parseInt(parts[0]);
        int b = Integer.parseInt(parts[1]);
        int c = Integer.parseInt(parts[2]);
        int d = Integer.parseInt(parts[3]);

        if (a < 0 || a > 255 || b < 0 || b > 255 || c < 0 || c > 255 || d < 0 || d > 255) {
            throw new IllegalArgumentException("IP không hợp lệ: " + ip);
        }

        if (prefixLength < 0 || prefixLength > 32) {
            throw new IllegalArgumentException("Prefix không hợp lệ: " + prefixLength);
        }

        int ipInt = (a << 24) | (b << 16) | (c << 8) | d;
        int mask = prefixLength == 0 ? 0 : (int) (0xFFFFFFFFL << (32 - prefixLength));
        int network = ipInt & mask;

        return ((network >> 24) & 0xFF) + "."
                + ((network >> 16) & 0xFF) + "."
                + ((network >> 8) & 0xFF) + "."
                + (network & 0xFF)
                + "/" + prefixLength;
    }
}