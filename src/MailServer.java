import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class MailServer {
    private static final int PORT = 9876;
    private static final String BASE_DIR = "Server/accounts/";

    // 🔵 Danh sách người dùng đang online: username -> (địa chỉ, cổng)
    private static final Map<String, ClientInfo> onlineUsers = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        try {
            InetAddress bindAddr = findWifiIPv4Address();
            DatagramSocket socket = new DatagramSocket(new InetSocketAddress(bindAddr, PORT));
            System.out.println("Mail Server đang chạy (IPv4)");
            byte[] receiveData = new byte[4096];

            System.out.println("ip: " + bindAddr.getHostAddress() + ", port: " + PORT);

            while (true) {
                DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                socket.receive(receivePacket);

                String request = new String(receivePacket.getData(), 0, receivePacket.getLength());
                InetAddress clientAddress = receivePacket.getAddress();
                int clientPort = receivePacket.getPort();
                System.out.println("[Yêu cầu] " + clientAddress + ":" + clientPort + " -> " + request);

                String response = handleRequest(request, clientAddress, clientPort, socket);
                if (response != null && !response.isEmpty()) {
                    byte[] sendData = response.getBytes();
                    DatagramPacket sendPacket =
                            new DatagramPacket(sendData, sendData.length, clientAddress, clientPort);
                    socket.send(sendPacket);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // 🧩 Hàm xử lý yêu cầu
    private static String handleRequest(String request, InetAddress clientAddress, int clientPort, DatagramSocket socket) {
        try {
            String[] parts = request.split(":", 5);
            String command = parts[0];

            switch (command) {
                case "REGISTER":
                    return registerAccount(parts[1], parts[2]);

                case "LOGIN": {
                    String username = parts[1];
                    String password = parts[2];
                    int portToUse = clientPort;
                    if (parts.length >= 4) {
                        try { portToUse = Integer.parseInt(parts[3]); } catch (Exception ignore) { portToUse = clientPort; }
                    }
                    if (validateLogin(username, password)) {
                        onlineUsers.put(username, new ClientInfo(clientAddress, portToUse));
                        return listEmails(username);
                    }
                    return "Đăng nhập thất bại. Sai tên tài khoản hoặc mật khẩu.";
                }

                case "SEND":
                    // Cấu trúc: SEND:<target>:<sender>:<content>
                    return sendEmail(parts[1], parts[2], clientAddress.getHostAddress(), parts[3], parts[4], socket);

                case "GET_EMAIL":
                    return getEmailContent(parts[1], parts[2]);

                case "LIST":
                    // LIST:<username>
                    return listEmails(parts[1]);

                case "LOGOUT":
                    onlineUsers.remove(parts[1]);
                    return "Đăng xuất thành công.";

                case "LIST_ACCOUNTS":
                    return listAccounts();

                default:
                    return "UNKNOWN_COMMAND";
            }
        } catch (Exception e) {
            return "ERROR:" + e.getMessage();
        }
    }

    // 🟢 Đăng ký tài khoản
    private static String registerAccount(String username, String password) throws IOException {
        Path userDir = Paths.get(BASE_DIR, username);
        if (!Files.exists(userDir)) {
            Files.createDirectories(userDir);
            Path accountFile = userDir.resolve("account.txt");
            String creationTime = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss").format(new Date());
            Files.writeString(accountFile, "Username: " + username + "\nPassword: " + password + "\nCreated: " + creationTime);
            return "Đăng ký thành công!";
        }
        return "USER_EXISTS";
    }

    private static boolean validateLogin(String username, String password) throws IOException {
        Path accountFile = Paths.get(BASE_DIR, username, "account.txt");
        if (!Files.exists(accountFile)) return false;
        List<String> lines = Files.readAllLines(accountFile);
        for (String line : lines) {
            if (line.startsWith("Password: ") && line.substring(10).equals(password)) {
                return true;
            }
        }
        return false;
    }

    // 🟢 Gửi email — có gửi realtime nếu người nhận đang online
    private static String sendEmail(String targetUser, String sender, String senderIP, String subject, String content, DatagramSocket socket) throws IOException {
        Path userDir = Paths.get(BASE_DIR, targetUser);
        if (!Files.exists(userDir)) return "Người dùng không tồn tại!";

        // ✅ Thời gian hiển thị đẹp và an toàn cho tên file
        Date now = new Date();
        String displayTime = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss").format(now);
        String safeTimestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(now);
        String fileName = String.format("from_%s_%s.txt", sender, safeTimestamp);

        // ✅ Ghi nội dung email có định dạng đẹp
        String fullContent = "💌 Từ: " + sender +
                "\n🌐 IP: " + senderIP +
                "\n🕒 Thời gian: " + displayTime +
                "\n📜 Tiêu đề: " + subject +
                "\n\n" + content;
        Files.writeString(userDir.resolve(fileName), fullContent);

        // 🟡 Nếu người nhận đang online, gửi realtime thông báo
        if (onlineUsers.containsKey(targetUser)) {
            ClientInfo info = onlineUsers.get(targetUser);
            String notifyMsg = "NEW_EMAIL:Từ " + sender + " (" + displayTime + ")";
            byte[] sendData = notifyMsg.getBytes();
            DatagramPacket packet = new DatagramPacket(sendData, sendData.length, info.address, info.port);
            socket.send(packet);
            System.out.println("📨 Đã gửi thông báo realtime đến " + targetUser);
        }

        return "Gửi email thành công đến " + targetUser;
    }

    private static String listEmails(String username) throws IOException {
        Path userDir = Paths.get(BASE_DIR, username);
        if (!Files.exists(userDir)) return "Nguoi Dung Khong Ton Tai";

        StringBuilder sb = new StringBuilder("EMAIL_LIST:");
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(userDir)) {
            for (Path file : stream) {
                sb.append(file.getFileName().toString()).append(",");
            }
        }
        return sb.toString();
    }

    private static String getEmailContent(String username, String filename) throws IOException {
        Path filePath = Paths.get(BASE_DIR, username, filename);
        if (!Files.exists(filePath)) return "Email Không Tồn Tại";
        return Files.readString(filePath);
    }

    // 🟢 Trả về danh sách tài khoản đã đăng ký
    private static String listAccounts() throws IOException {
        Path baseDir = Paths.get(BASE_DIR);
        if (!Files.exists(baseDir)) return "ACCOUNTS:";

        StringBuilder sb = new StringBuilder("ACCOUNTS:");
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(baseDir)) {
            for (Path dir : stream) {
                if (Files.isDirectory(dir)) {
                    sb.append(dir.getFileName().toString()).append(",");
                }
            }
        }
        return sb.toString();
    }

    // 🧭 Tìm IPv4 của adapter Wi-Fi; ưu tiên site-local. Fallback site-local bất kỳ, rồi 0.0.0.0
    private static InetAddress findWifiIPv4Address() throws Exception {
        InetAddress wifiCandidate = null;
        InetAddress anySiteLocal = null;

        Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
        while (ifaces.hasMoreElements()) {
            NetworkInterface nif = ifaces.nextElement();
            if (!nif.isUp() || nif.isLoopback() || nif.isVirtual()) continue;

            String id = (nif.getName() + " " + nif.getDisplayName()).toLowerCase(Locale.ROOT);
            boolean isWifi = id.contains("wi-fi") || id.contains("wifi") || id.contains("wlan");

            Enumeration<InetAddress> addrs = nif.getInetAddresses();
            while (addrs.hasMoreElements()) {
                InetAddress addr = addrs.nextElement();
                if (!(addr instanceof Inet4Address)) continue;

                if (addr.isSiteLocalAddress() && anySiteLocal == null) {
                    anySiteLocal = addr; // fallback
                }
                if (isWifi) {
                    if (addr.isSiteLocalAddress()) return addr; // Wi-Fi + private IPv4
                    if (wifiCandidate == null) wifiCandidate = addr; // Wi-Fi + IPv4 bất kỳ
                }
            }
        }
        if (wifiCandidate != null) return wifiCandidate;
        if (anySiteLocal != null) return anySiteLocal;
        return InetAddress.getByName("0.0.0.0"); // bind mọi IPv4 nếu không tìm thấy
    }

    // 🧩 Lưu thông tin client
    private static class ClientInfo {
        InetAddress address;
        int port;

        ClientInfo(InetAddress address, int port) {
            this.address = address;
            this.port = port;
        }
    }
}