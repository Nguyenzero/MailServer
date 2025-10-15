package controller;


import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import network.MailClient;

public class MainController {
    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField; // Add password field for registration/login
    @FXML private ListView<String> emailList;
    @FXML private TextArea emailContent;
    @FXML private ChoiceBox<String> recipientChoiceBox; // Keep only the recipient dropdown
    @FXML private TextField subjectField; // Add subject field for email sending
    @FXML private TextArea sendContent;

    private String currentUser;

    @FXML
    private void handleRegister() {
        try {
            String username = usernameField.getText().trim();
            String password = passwordField.getText().trim();
            if (username.isEmpty() || password.isEmpty()) {
                showAlert("Lỗi", "Vui lòng nhập tên tài khoản và mật khẩu!");
                return;
            }
            String response = MailClient.sendCommand("REGISTER:" + username + ":" + password);
            showAlert("Server", response);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void handleLogin() {
        try {
            String username = usernameField.getText().trim();
            String password = passwordField.getText().trim();
            if (username.isEmpty() || password.isEmpty()) {
                showAlert("Lỗi", "Vui lòng nhập tên tài khoản và mật khẩu!");
                return;
            }

            MailClient.startListener(this::onServerPush);
            int listenPort = MailClient.getListenerPort();

            String response = MailClient.sendCommand("LOGIN:" + username + ":" + password + ":" + listenPort);
            if (response.startsWith("EMAIL_LIST:")) {
                currentUser = username; // Set the current user after successful login
                String[] files = response.substring(11).split(",");
                emailList.getItems().setAll(files);

                // Fetch the list of accounts and populate the recipientChoiceBox
                String accountsResponse = MailClient.sendCommand("LIST_ACCOUNTS");
                if (accountsResponse.startsWith("ACCOUNTS:")) {
                    String[] accounts = accountsResponse.substring(9).split(",");
                    recipientChoiceBox.getItems().setAll(accounts);
                    recipientChoiceBox.getItems().remove(currentUser); // Remove the current user from the recipient list
                    if (!recipientChoiceBox.getItems().isEmpty()) {
                        recipientChoiceBox.setValue(recipientChoiceBox.getItems().get(0)); // Set the first account as default
                    }
                    recipientChoiceBox.setDisable(false); // Enable the recipientChoiceBox
                }
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
            String recipient = recipientChoiceBox.getValue(); // Get the selected recipient
            String subject = subjectField.getText().trim();
            String content = sendContent.getText().trim();

            if (recipient == null || recipient.isEmpty() || subject.isEmpty() || content.isEmpty()) {
                showAlert("Lỗi", "Vui lòng chọn người nhận, nhập tiêu đề và nội dung email!");
                return;
            }

            String response = MailClient.sendCommand("SEND:" + recipient + ":" + currentUser + ":" + subject + ":" + content);
            showAlert("Server", response);
            sendContent.clear();
            subjectField.clear();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void initialize() {
        emailList.setOnMouseClicked(this::showEmailContent);

        // Disable recipientChoiceBox by default
        recipientChoiceBox.setDisable(true);

        try {
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