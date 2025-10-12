package controller;


import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import network.MailClient;

public class MainController {
    @FXML private TextField usernameField;
    @FXML private ListView<String> emailList;
    @FXML private TextArea emailContent;
    @FXML private TextField targetField;
    @FXML private TextArea sendContent;

    private String currentUser;

    @FXML
    private void handleRegister() {
        try {
            String username = usernameField.getText().trim();
            if (username.isEmpty()) {
                showAlert("Lỗi", "Vui lòng nhập tên tài khoản!");
                return;
            }
            String response = MailClient.sendCommand("REGISTER:" + username);
            showAlert("Server", response);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void handleLogin() {
        try {
            String username = usernameField.getText().trim();
            if (username.isEmpty()) {
                showAlert("Lỗi", "Vui lòng nhập tên tài khoản!");
                return;
            }

            // Ensure listener is running to receive push notifications
            MailClient.startListener(this::onServerPush);
            int listenPort = MailClient.getListenerPort();

            currentUser = username;
            // Send LOGIN with listener port so server can push to the correct port
            String response = MailClient.sendCommand("LOGIN:" + username + ":" + listenPort);
            if (response.startsWith("EMAIL_LIST:")) {
                String[] files = response.substring(11).split(",");
                emailList.getItems().setAll(files);
            } else {
                showAlert("Server", response);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void handleSendEmail() {
        try {
            String target = targetField.getText().trim();
            String content = sendContent.getText().trim();

            if (target.isEmpty() || content.isEmpty()) {
                showAlert("Lỗi", "Vui lòng nhập người nhận và nội dung email!");
                return;
            }

            if (currentUser == null || currentUser.isEmpty()) {
                showAlert("Lỗi", "Vui lòng đăng nhập trước khi gửi email!");
                return;
            }

            // 🟢 Gửi kèm người gửi
            String response = MailClient.sendCommand("SEND:" + target + ":" + currentUser + ":" + content);
            showAlert("Server", response);
            sendContent.clear();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void initialize() {
        emailList.setOnMouseClicked(this::showEmailContent);
        try {
            // Start listener early so we are ready even before login; handler will no-op if not logged in.
            MailClient.startListener(this::onServerPush);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void showEmailContent(MouseEvent e) {
        String selected = emailList.getSelectionModel().getSelectedItem();
        if (selected == null || selected.isBlank()) return;
        try {
            // Gửi lệnh GET_EMAIL:<username>:<filename>
            String response = MailClient.sendCommand("GET_EMAIL:" + currentUser + ":" + selected);
            emailContent.setText(response);
        } catch (Exception ex) {
            emailContent.setText("Không thể đọc nội dung email.");
        }
    }

    // Handle server push messages (runs on background thread)
    private void onServerPush(String msg) {
        if (msg == null || msg.isBlank()) return;
        if (msg.startsWith("NEW_EMAIL")) {
            Platform.runLater(() -> {
                if (currentUser != null && !currentUser.isEmpty()) refreshEmailList();
            });
        }
        // You can handle other push types here later
    }

    private void refreshEmailList() {
        try {
            String response = MailClient.sendCommand("LIST:" + currentUser);
            if (response.startsWith("EMAIL_LIST:")) {
                String[] files = response.substring(11).split(",");
                emailList.getItems().setAll(files);
            }
        } catch (Exception e) {
            // Optional: show a brief message or ignore
        }
    }

    private void showAlert(String title, String msg) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.showAndWait();
    }
}