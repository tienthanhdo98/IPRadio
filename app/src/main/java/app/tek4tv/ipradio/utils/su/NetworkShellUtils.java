package app.tek4tv.ipradio.utils.su;

import android.util.Log;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

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


    public static boolean forceRockchipStaticIp(String ip, String gateway, String dns1, String dns2) {
        String file = "/data/misc/ethernet/ipconfig.txt";

        String[] commands = {
                // 1. Tắt mạng và XÓA SẠCH IP/Đường đi cũ (Tránh kẹt dải 11.x)
                "ifconfig eth0 down",
                "ip addr flush dev eth0",
                "ip route flush dev eth0",

                // 2. Ghi file cấu hình cho Rockchip (Để nó không "phản bội" đè lại IP cũ)
                "mkdir -p /data/misc/ethernet",
                "echo \"3\" > " + file,
                "echo \"eth0\" >> " + file,
                "echo \"static\" >> " + file,
                "echo \"ipAddress " + ip + " 24\" >> " + file,
                "echo \"gateway " + gateway + "\" >> " + file,
                "echo \"dns " + dns1 + "\" >> " + file,
                "echo \"dns " + dns2 + "\" >> " + file,
                "echo \"id eth0\" >> " + file,
                "echo \"eos\" >> " + file,
                "chmod 600 " + file,
                "chown system:system " + file,

                // 3. Khóa các property của Rockchip
                "setprop persist.net.ethernet.mode static",
                "setprop persist.net.ethernet.ip " + ip,
                "setprop persist.net.ethernet.mask 255.255.255.0",
                "setprop persist.net.ethernet.gw " + gateway,
                "setprop persist.net.ethernet.dns1 " + dns1,
                "setprop persist.net.ethernet.dns2 " + dns2,

                // 4. BƯỚC ĐỘT PHÁ: Tự tay ép Kernel nhận IP mới ngay lập tức
                "ifconfig eth0 " + ip + " netmask 255.255.255.0 up",

                // Chờ 2 giây cho card mạng nhận điện và sẵn sàng
                "sleep 2",

                // 5. CHỈ ĐƯỜNG RA INTERNET VÀ BẮT APP ĐI THEO
                "ip route add default via " + gateway + " dev eth0",
                "ip rule add from all lookup main pref 1",

                // 6. Cấu hình DNS
                "ndc resolver setnetdns eth0 \"\" " + dns1 + " " + dns2,
                "am broadcast -a android.net.conn.CONNECTIVITY_CHANGE"
        };

        return NetworkShellUtils.executeSuCommands(commands);
    }

    //doi thnah cong nhưng chưa có mạng
    public static void forceStaticIp(String ip, String gateway, String dns1, String dns2) {
        String[] commands = {
                // 1. Chuyển chế độ Ethernet sang Static (Sử dụng 1 cho Static, 0 cho DHCP)
                "settings put global ethernet_static_ip " + ip,
                "settings put global ethernet_static_netmask 255.255.255.0",
                "settings put global ethernet_static_gateway " + gateway,
                "settings put global ethernet_static_dns1 " + dns1,
                // Lệnh quan trọng để báo cho hệ thống biết đang dùng chế độ manual
                "settings put global ethernet_use_static_ip 1",

                // 2. Thực hiện các lệnh hạ tầng để áp dụng ngay lập tức
                "ifconfig eth0 " + ip + " netmask 255.255.255.0 up",
                "route add default gw " + gateway + " dev eth0",

                // 3. DNS "cứng" qua ndc
                "ndc resolver setnetdns eth0 \"\" " + dns1 + " 8.8.4.4",

                // 4. "Đóng băng" dịch vụ mạng của Android để nó không tự scan lại
                "svc network disable",
                "svc network enable"
        };
        NetworkShellUtils.executeSuCommands(commands);
    }

    public static void setStaticIp(
            String ip,
            int prefixLength,
            String gateway,
            String dns1,
            String dns2
    ) {
        String interfaceName = "eth0";
        String subnet = buildSubnet(ip, prefixLength);

        List<String> commands = new ArrayList<>();

        // 1) Bring interface up và xóa IP cũ
        commands.add("ip link set " + interfaceName + " up");
        commands.add("ip addr flush dev " + interfaceName);

        // 2) Set IP mới
        commands.add("ip addr add " + ip + "/" + prefixLength + " dev " + interfaceName);

        // 3) Main table
        commands.add("ip route replace " + subnet + " dev " + interfaceName + " src " + ip);
        commands.add("ip route replace default via " + gateway + " dev " + interfaceName);

        // 4) Android tables quan trọng trên box này
        commands.add("ip route replace " + subnet + " dev " + interfaceName + " src " + ip + " table legacy_network");
        commands.add("ip route replace default via " + gateway + " dev " + interfaceName + " table legacy_network");

        commands.add("ip route replace " + subnet + " dev " + interfaceName + " src " + ip + " table eth0");
        commands.add("ip route replace default via " + gateway + " dev " + interfaceName + " table eth0");

        commands.add("ip route replace " + subnet + " dev " + interfaceName + " src " + ip + " table local_network");

        // 5) DNS / gateway properties
        commands.add("setprop net." + interfaceName + ".gw " + gateway);

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

    private static String buildSubnet(String ip, int prefixLength) {
        String[] parts = ip.split("\\.");
        if (parts.length != 4) {
            throw new IllegalArgumentException("IP không hợp lệ: " + ip);
        }

        int p1 = Integer.parseInt(parts[0]);
        int p2 = Integer.parseInt(parts[1]);
        int p3 = Integer.parseInt(parts[2]);
        int p4 = Integer.parseInt(parts[3]);

        if (p1 < 0 || p1 > 255 || p2 < 0 || p2 > 255 || p3 < 0 || p3 > 255 || p4 < 0 || p4 > 255) {
            throw new IllegalArgumentException("IP không hợp lệ: " + ip);
        }

        if (prefixLength < 0 || prefixLength > 32) {
            throw new IllegalArgumentException("Prefix không hợp lệ: " + prefixLength);
        }

        int ipInt = (p1 << 24) | (p2 << 16) | (p3 << 8) | p4;
        int mask = prefixLength == 0 ? 0 : (int) (0xFFFFFFFFL << (32 - prefixLength));
        int network = ipInt & mask;

        String networkIp = ((network >> 24) & 0xFF) + "."
                + ((network >> 16) & 0xFF) + "."
                + ((network >> 8) & 0xFF) + "."
                + (network & 0xFF);

        return networkIp + "/" + prefixLength;
    }

//    //work trên android 7
//    public static void setStaticIp(String ip, int prefixLength, String gateway, String dns1, String dns2) {
//        String interfaceName = "eth0";
//
//        String subnet = buildSubnet(ip, prefixLength);
//
//        String[] commands = {
//                "ip link set " + interfaceName + " up",
//                "ip addr flush dev " + interfaceName,
//                "ip addr add " + ip + "/" + prefixLength + " dev " + interfaceName,
//
//                // main table
//                "ip route replace " + subnet + " dev " + interfaceName + " src " + ip,
//                "ip route replace default via " + gateway + " dev " + interfaceName,
//
//                // Android legacy_network table
//                "ip route replace " + subnet + " dev " + interfaceName + " src " + ip + " table legacy_network",
//                "ip route replace default via " + gateway + " dev " + interfaceName + " table legacy_network",
//
//                // DNS properties
//                "setprop net.dns1 " + dns1,
//                "setprop net.dns2 " + dns2,
//                "setprop net." + interfaceName + ".gw " + gateway,
//                "setprop net." + interfaceName + ".dns1 " + dns1,
//                "setprop net." + interfaceName + ".dns2 " + dns2
//        };
//
//        boolean success = NetworkShellUtils.executeSuCommands(commands);
//        if (success) {
//            System.out.println("Set Static IP thành công!");
//        } else {
//            System.out.println("Set Static IP thất bại.");
//        }
//    }
//
//    private static String buildSubnet(String ip, int prefixLength) {
//        String[] parts = ip.split("\\.");
//        if (parts.length != 4) {
//            throw new IllegalArgumentException("IP không hợp lệ: " + ip);
//        }
//
//        int ipInt = (Integer.parseInt(parts[0]) << 24)
//                | (Integer.parseInt(parts[1]) << 16)
//                | (Integer.parseInt(parts[2]) << 8)
//                | Integer.parseInt(parts[3]);
//
//        int mask = prefixLength == 0 ? 0 : (int) (0xFFFFFFFFL << (32 - prefixLength));
//        int network = ipInt & mask;
//
//        String networkIp = ((network >> 24) & 0xFF) + "."
//                + ((network >> 16) & 0xFF) + "."
//                + ((network >> 8) & 0xFF) + "."
//                + (network & 0xFF);
//
//        return networkIp + "/" + prefixLength;
//    }


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