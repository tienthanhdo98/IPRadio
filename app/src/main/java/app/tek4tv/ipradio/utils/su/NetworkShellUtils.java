package app.tek4tv.ipradio.utils.su;

import android.util.Log;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class NetworkShellUtils {
    private static final String TAG = "ShellLog";

    /**
     * Hàm thực thi nhiều dòng lệnh shell dưới quyền root.
     *
     * @param commands Mảng các câu lệnh cần thực thi
     * @return true nếu thực thi thành công, false nếu thất bại
     */
    public static boolean executeSuCommands(String[] commands) {
        Process process = null;
        DataOutputStream os = null;
        BufferedReader successReader = null;
        BufferedReader errorReader = null;

        try {
            process = Runtime.getRuntime().exec("su");
            os = new DataOutputStream(process.getOutputStream());
            successReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));

            for (String command : commands) {
                Log.d(TAG, "Executing: " + command); // In ra lệnh đang chạy
                os.writeBytes(command + "\n");
                os.flush();
            }

            os.writeBytes("exit\n");
            os.flush();

            // Đọc kết quả trả về từ Shell
            String line;
            Log.d(TAG, "--- KẾT QUẢ OUTPUT ---");
            while ((line = successReader.readLine()) != null) {
                Log.d(TAG, line);
            }

            // ĐỌC LỖI TỪ SHELL (Quan trọng nhất)
            Log.e(TAG, "--- LỖI TỪ KERNEL ---");
            boolean hasError = false;
            while ((line = errorReader.readLine()) != null) {
                Log.e(TAG, line);
                hasError = true;
            }

            int exitValue = process.waitFor();
            Log.d(TAG, "Shell exit code: " + exitValue);

            // Nếu có dòng lỗi nào in ra, ta tạm coi là có lệnh thất bại
            return exitValue == 0 && !hasError;

        } catch (Exception e) {
            Log.e(TAG, "Exception khi chạy Shell", e);
            return false;
        } finally {
            try {
                if (os != null) os.close();
                if (successReader != null) successReader.close();
                if (errorReader != null) errorReader.close();
                if (process != null) process.destroy();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    //For both (Test)
    public static void setUniversalStaticIp(
            String ip,
            String subnetInput,
            String gateway,
            String dns1,
            String dns2
    ) {
        final String iface = "eth0";

        // 1. Bỏ qua nếu IP đã được cấu hình đúng
        if (isIpAlreadyConfigured(iface, ip)) {
            Log.d(TAG, "Interface " + iface + " already has IP " + ip + ", skipping configuration.");
            return;
        }

        // subnetInput có thể là "255.255.255.0", "/24", hoặc "24"
        final int prefixLength = parsePrefixLength(subnetInput);

        // netmask dùng cho settings global
        final String netmask = prefixToNetmask(prefixLength);

        // routeSubnet dùng cho ip route, ví dụ: 192.168.0.0/24
        final String routeSubnet = buildSubnet(ip, prefixLength);

        List<String> commands = new ArrayList<>();

        // 2. Android Framework
        commands.add("settings put global ethernet_static_ip " + ip);
        commands.add("settings put global ethernet_static_netmask " + netmask);
        commands.add("settings put global ethernet_static_gateway " + gateway);
        commands.add("settings put global ethernet_static_dns1 " + dns1);

        if (dns2 != null && !dns2.trim().isEmpty()) {
            commands.add("settings put global ethernet_static_dns2 " + dns2);
        }

        commands.add("settings put global ethernet_use_static_ip 1");

        // 3. Chuẩn bị card mạng
        commands.add("ip link set " + iface + " up");
        commands.add("ip -4 addr flush dev " + iface);

        // 4. Dọn route cũ
        commands.add("ip route flush dev " + iface + " table main || true");
        commands.add("ip route flush dev " + iface + " table legacy_network || true");
        commands.add("ip route flush dev " + iface + " table " + iface + " || true");
        commands.add("ip route flush dev " + iface + " table local_network || true");

        // 5. Áp dụng IP mới
        commands.add("ip addr add " + ip + "/" + prefixLength + " dev " + iface);

        // 6. Thiết lập routing
        commands.add("ip route replace " + routeSubnet + " dev " + iface + " src " + ip);
        commands.add("ip route replace " + routeSubnet + " dev " + iface + " src " + ip + " table legacy_network");
        commands.add("ip route replace " + routeSubnet + " dev " + iface + " src " + ip + " table " + iface);
        commands.add("ip route replace " + routeSubnet + " dev " + iface + " src " + ip + " table local_network");

        if (gateway != null && !gateway.trim().isEmpty() && !gateway.equals("0.0.0.0")) {
            commands.add("ip route replace default via " + gateway + " dev " + iface);
            commands.add("ip route replace default via " + gateway + " dev " + iface + " table legacy_network");
            commands.add("ip route replace default via " + gateway + " dev " + iface + " table " + iface);
        }

        // 7. DNS/Gateway cho Android cũ
        if (dns1 != null && !dns1.trim().isEmpty()) {
            commands.add("setprop net.dns1 " + dns1);
            commands.add("setprop net." + iface + ".dns1 " + dns1);
        }

        if (dns2 != null && !dns2.trim().isEmpty()) {
            commands.add("setprop net.dns2 " + dns2);
            commands.add("setprop net." + iface + ".dns2 " + dns2);
        }

        if (gateway != null && !gateway.trim().isEmpty() && !gateway.equals("0.0.0.0")) {
            commands.add("setprop net." + iface + ".gw " + gateway);
        }

        // 8. Log command
        for (String cmd : commands) {
            Log.d(TAG, "CMD: " + cmd);
        }

        boolean success = NetworkShellUtils.executeSuCommands(commands.toArray(new String[0]));

        if (success) {
            Log.d(TAG, "setUniversalStaticIp: Configuration applied successfully.");
        } else {
            Log.e(TAG, "setUniversalStaticIp: Configuration failed.");
        }
    }

    //Cho Android 7
    public static void setStaticIp(
            String ip,
            int prefixLength,
            String gateway,
            String dns1,
            String dns2
    ) {
        String interfaceName = "eth0";

        if (isIpAlreadyConfigured(interfaceName, ip)) {
            Log.d(TAG, "Interface " + interfaceName + " already has IP " + ip + ", skipping setStaticIp.");
            return;
        }

        String subnet = buildSubnet(ip, prefixLength);

        List<String> commands = new ArrayList<>();

        // 1) Bring interface up và xóa IP cũ
        commands.add("ip link set " + interfaceName + " up");
        commands.add("ip addr flush dev " + interfaceName);

        // 2) Set IP mới
        commands.add("ip addr add " + ip + "/" + prefixLength + " dev " + interfaceName);

        // 3) Main table
        commands.add("ip route replace " + subnet + " dev " + interfaceName + " src " + ip);
        commands.add("ip route replace " + subnet + " dev " + interfaceName + " src " + ip + " table legacy_network");
        commands.add("ip route replace " + subnet + " dev " + interfaceName + " src " + ip + " table eth0");
        commands.add("ip route replace " + subnet + " dev " + interfaceName + " src " + ip + " table local_network");

        if (gateway != null && !gateway.trim().isEmpty() && !gateway.equals("0.0.0.0")) {
            commands.add("ip route replace default via " + gateway + " dev " + interfaceName);
            commands.add("ip route replace default via " + gateway + " dev " + interfaceName + " table legacy_network");
            commands.add("ip route replace default via " + gateway + " dev " + interfaceName + " table eth0");
            commands.add("setprop net." + interfaceName + ".gw " + gateway);
        }

        if (dns1 != null && !dns1.trim().isEmpty()) {
            commands.add("setprop net.dns1 " + dns1);
            commands.add("setprop net." + interfaceName + ".dns1 " + dns1);
        }

        if (dns2 != null && !dns2.trim().isEmpty()) {
            commands.add("setprop net.dns2 " + dns2);
            commands.add("setprop net." + interfaceName + ".dns2 " + dns2);
        }

        // 6) In log để debug
        for (String cmd : commands) {
            Log.d(TAG, "CMD: " + cmd);
        }

        boolean success = NetworkShellUtils.executeSuCommands(commands.toArray(new String[0]));

        if (success) {
            Log.d(TAG, "Set Static IP thành công");
        } else {
            Log.e(TAG, "Set Static IP thất bại");
        }
    }


    //Cho Android 9
    public static void forceStaticIp(String ip, String gateway, String dns1, String dns2) {
        final String iface = "eth0";
        final int prefixLength = 24;

        if (isIpAlreadyConfigured(iface, ip)) {
            Log.d(TAG, "Interface " + iface + " already has IP " + ip + ", skipping setStaticIp.");
            return;
        }

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
        commands.add("ip route flush dev " + iface + " table main || true");
        commands.add("ip route flush dev " + iface + " table legacy_network || true");
        commands.add("ip route flush dev " + iface + " table " + iface + " || true");
        commands.add("ip route flush dev " + iface + " table local_network || true");

        // 4) Add IP mới
        commands.add("ip addr add " + ip + "/" + prefixLength + " dev " + iface);

        // 5) Main table
        commands.add("ip route replace " + subnet + " dev " + iface + " src " + ip);
        commands.add("ip route replace " + subnet + " dev " + iface + " src " + ip + " table legacy_network");
        commands.add("ip route replace " + subnet + " dev " + iface + " src " + ip + " table " + iface);
        commands.add("ip route replace " + subnet + " dev " + iface + " src " + ip + " table local_network");

        if (gateway != null && !gateway.trim().isEmpty() && !gateway.equals("0.0.0.0")) {
            commands.add("ip route replace default via " + gateway + " dev " + iface);
            commands.add("ip route replace default via " + gateway + " dev " + iface + " table legacy_network");
            commands.add("ip route replace default via " + gateway + " dev " + iface + " table " + iface);
            commands.add("setprop net." + iface + ".gw " + gateway);
        }

        // 7) DNS properties
        if (dns1 != null && !dns1.trim().isEmpty()) {
            commands.add("setprop net.dns1 " + dns1);
            commands.add("setprop net." + iface + ".dns1 " + dns1);
        }
        if (dns2 != null && !dns2.trim().isEmpty()) {
            commands.add("setprop net.dns2 " + dns2);
            commands.add("setprop net." + iface + ".dns2 " + dns2);
        }

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

    }

    private static boolean isIpAlreadyConfigured(String interfaceName, String targetIp) {
        try {
            java.net.NetworkInterface netIf = java.net.NetworkInterface.getByName(interfaceName);
            if (netIf != null) {
                java.util.Enumeration<java.net.InetAddress> addresses = netIf.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    java.net.InetAddress addr = addresses.nextElement();
                    if (!addr.isLoopbackAddress() && Objects.equals(addr.getHostAddress(), targetIp)) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Exception while checking IP", e);
        }
        return false;
    }

    private static String buildSubnet(String ip, int prefixLength) {
        String[] parts = ip.split("\\.");
        if (parts.length != 4) {
            throw new IllegalArgumentException("IP không hợp lệ: " + ip);
        }

        int a, b, c, d;
        try {
            // Nếu bất kỳ phần tử nào chứa chữ cái, khoảng trắng, hoặc rỗng,
            // parseInt sẽ ném ra NumberFormatException ngay lập tức.
            a = Integer.parseInt(parts[0]);
            b = Integer.parseInt(parts[1]);
            c = Integer.parseInt(parts[2]);
            d = Integer.parseInt(parts[3]);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("IP không hợp lệ: " + ip);
        }

        // Kiểm tra giới hạn 0-255 cho từng Octet
        if (a < 0 || a > 255 || b < 0 || b > 255 || c < 0 || c > 255 || d < 0 || d > 255) {
            throw new IllegalArgumentException("IP không hợp lệ: " + ip);
        }

        // Kiểm tra Prefix hợp lệ (0-32)
        if (prefixLength < 0 || prefixLength > 32) {
            throw new IllegalArgumentException("Prefix không hợp lệ: " + prefixLength);
        }

        // Tính toán Network IP bằng Bitwise
        int ipInt = (a << 24) | (b << 16) | (c << 8) | d;
        int mask = prefixLength == 0 ? 0 : (int) (0xFFFFFFFFL << (32 - prefixLength));
        int network = ipInt & mask;

        // Trả về chuỗi định dạng CIDR
        return ((network >> 24) & 0xFF) + "."
                + ((network >> 16) & 0xFF) + "."
                + ((network >> 8) & 0xFF) + "."
                + (network & 0xFF)
                + "/" + prefixLength;
    }

    public static int parsePrefixLength(String subnetInput) {
        if (subnetInput == null || subnetInput.trim().isEmpty()) {
            throw new IllegalArgumentException("Subnet is empty");
        }

        String value = subnetInput.trim();

        // Cho phép nhập "/24"
        if (value.startsWith("/")) {
            value = value.substring(1);
        }

        // Cho phép nhập "24"
        if (value.matches("\\d+")) {
            int prefix = Integer.parseInt(value);
            if (prefix < 0 || prefix > 32) {
                throw new IllegalArgumentException("Invalid prefix length: " + subnetInput);
            }
            return prefix;
        }

        // Còn lại coi là subnet mask, ví dụ "255.255.255.0"
        return subnetMaskToPrefixLength(value);
    }

    public static int subnetMaskToPrefixLength(String subnetMask) {
        String[] parts = subnetMask.trim().split("\\.");

        if (parts.length != 4) {
            throw new IllegalArgumentException("Invalid subnet mask: " + subnetMask);
        }

        int mask = 0;

        for (String part : parts) {
            int value;

            try {
                value = Integer.parseInt(part);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid subnet mask: " + subnetMask);
            }

            if (value < 0 || value > 255) {
                throw new IllegalArgumentException("Invalid subnet mask: " + subnetMask);
            }

            mask = (mask << 8) | value;
        }

        int prefix = 0;
        boolean zeroFound = false;

        for (int i = 31; i >= 0; i--) {
            boolean bitIsOne = ((mask >> i) & 1) == 1;

            if (bitIsOne) {
                if (zeroFound) {
                    throw new IllegalArgumentException("Subnet mask is not contiguous: " + subnetMask);
                }
                prefix++;
            } else {
                zeroFound = true;
            }
        }

        return prefix;
    }

    private static String prefixToNetmask(int prefixLength) {
        if (prefixLength < 0 || prefixLength > 32) return "255.255.255.0"; // Fallback an toàn
        int mask = prefixLength == 0 ? 0 : (int) (0xFFFFFFFFL << (32 - prefixLength));
        return ((mask >> 24) & 0xFF) + "." +
                ((mask >> 16) & 0xFF) + "." +
                ((mask >> 8) & 0xFF) + "." +
                (mask & 0xFF);
    }


    public static void setDhcp() {
        String interfaceName = "eth0";

        String[] commands = {
                // 1. Xóa toàn bộ IP tĩnh đã cài đặt thủ công
                "ip addr flush dev " + interfaceName,

                // 2. Tắt interface
                "ip link set " + interfaceName + " down",

                // 3. Bật lại interface. Việc này thường sẽ kích hoạt (trigger) Android
                // framework yêu cầu cấp lại IP từ DHCP server.
                "ip link set " + interfaceName + " up",

                // (Tùy chọn bổ sung) Nếu box của bạn sử dụng dịch vụ dhcpcd riêng biệt,
                // bạn có thể khởi động lại dịch vụ này bằng lệnh:
                // "setprop ctl.restart dhcpcd_" + interfaceName
        };

        boolean success = NetworkShellUtils.executeSuCommands(commands);
        if (success) {
            System.out.println("Chuyển về DHCP thành công (Đang chờ cấp IP...)!");
        } else {
            System.out.println("Lỗi khi chuyển về DHCP.");
        }
    }


}