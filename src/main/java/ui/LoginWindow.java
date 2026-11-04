package ui;

import model.DriverAccount;
import model.PassengerAccount;
import model.Rickshaw;
import service.AccountService;
import service.AccountService.AccountIdentity;
import service.AccountService.AccountRole;
import service.RideService;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Login and registration for local passenger and driver accounts. */
public class LoginWindow extends JFrame {
    private static final Color NAVY = new Color(28, 48, 74);
    private static final Color MUTED = new Color(96, 111, 130);
    private static final Color ACCENT = new Color(31, 119, 89);

    private final RideService rideService;
    private final AccountService accountService;
    private final JPasswordField passwordField = new JPasswordField(18);
    private final JLabel accountNameLabel = new JLabel();
    private final JButton loginButton = createButton("Log In", true);
    private final JButton switchAccountButton = createButton("Switch Account", false);
    private final MessageLabel message = new MessageLabel();
    private AccountIdentity selectedAccount;

    public LoginWindow(RideService rideService, AccountService accountService) {
        this(rideService, accountService, false);
    }

    private LoginWindow(RideService rideService, AccountService accountService, boolean additionalSession) {
        super("Campus Rider");
        this.rideService = rideService;
        this.accountService = accountService;
        setDefaultCloseOperation(additionalSession ? JFrame.DISPOSE_ON_CLOSE : JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(430, 410));
        selectedAccount = findInitialAccount();

        JPanel content = new JPanel(new BorderLayout(18, 18));
        content.setBackground(Color.WHITE);
        content.setBorder(BorderFactory.createEmptyBorder(30, 36, 24, 36));

        JLabel title = new JLabel("Campus Rider", SwingConstants.CENTER);
        title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 29));
        title.setForeground(NAVY);
        JLabel subtitle = new JLabel("Sign in to your campus ride account", SwingConstants.CENTER);
        subtitle.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        subtitle.setForeground(MUTED);
        JPanel heading = new JPanel(new GridLayout(2, 1, 0, 7));
        heading.setOpaque(false);
        heading.add(title);
        heading.add(subtitle);

        JPanel fields = new JPanel(new GridLayout(0, 1, 0, 7));
        fields.setOpaque(false);
        fields.add(new JLabel("Account"));
        accountNameLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
        accountNameLabel.setForeground(NAVY);
        fields.add(accountNameLabel);
        fields.add(new JLabel("Password"));
        fields.add(passwordField);

        JButton registerButton = createButton("Create Account", false);
        loginButton.addActionListener(event -> logIn());
        switchAccountButton.addActionListener(event -> switchAccount());
        registerButton.addActionListener(event -> showRegistration());
        JPanel actions = new JPanel(new GridLayout(0, 1, 8, 8));
        actions.setOpaque(false);
        actions.add(loginButton);
        actions.add(switchAccountButton);
        actions.add(registerButton);

        JPanel form = new JPanel(new BorderLayout(12, 12));
        form.setOpaque(false);
        form.add(fields, BorderLayout.CENTER);
        form.add(actions, BorderLayout.SOUTH);

        content.add(heading, BorderLayout.NORTH);
        content.add(form, BorderLayout.CENTER);
        content.add(message, BorderLayout.SOUTH);
        setContentPane(content);
        updateSelectedAccountDisplay();
        getRootPane().setDefaultButton(loginButton);
        setSize(480, 430);
        setLocationRelativeTo(null);
    }

    private void logIn() {
        char[] password = passwordField.getPassword();
        try {
            if (selectedAccount == null) {
                throw new IllegalArgumentException("Create an account before logging in.");
            }
            AccountIdentity authenticated = accountService.authenticateAccount(selectedAccount, password);
            openDashboard(authenticated);
        } catch (IllegalArgumentException | IllegalStateException e) {
            message.showError(e.getMessage());
            passwordField.setText("");
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    private void showRegistration() {
        Object selectedRole = JOptionPane.showInputDialog(this,
                "Choose the type of account to create.", "Create Account",
                JOptionPane.PLAIN_MESSAGE, null,
                new String[]{"Passenger", "Driver"}, "Passenger");
        if (selectedRole == null) {
            return;
        }
        AccountRole role = "Driver".equals(selectedRole) ? AccountRole.DRIVER : AccountRole.PASSENGER;
        JTextField name = new JTextField(20);
        JPasswordField password = new JPasswordField(20);
        JPanel form = new JPanel(new GridLayout(0, 2, 8, 8));
        form.add(new JLabel("Name"));
        form.add(name);
        form.add(new JLabel("Password"));
        form.add(password);
        int result = JOptionPane.showConfirmDialog(this, form, role + " Account",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) {
            return;
        }
        char[] secret = password.getPassword();
        try {
            AccountIdentity account = accountService.createSimpleAccount(role, name.getText(), secret);
            selectedAccount = account;
            openDashboard(account);
        } catch (IllegalArgumentException | IllegalStateException | java.io.IOException e) {
            message.showError(e.getMessage());
        } finally {
            Arrays.fill(secret, '\0');
            password.setText("");
        }
    }

    private void switchAccount() {
        List<AccountIdentity> accounts = allAccounts();
        if (accounts.size() < 2) {
            message.showError("There are no other saved accounts to switch to.");
            return;
        }
        JComboBox<AccountIdentity> chooser = new JComboBox<>(accounts.toArray(new AccountIdentity[0]));
        chooser.setSelectedItem(selectedAccount);
        int result = JOptionPane.showConfirmDialog(this, chooser, "Switch Account",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result == JOptionPane.OK_OPTION && chooser.getSelectedItem() instanceof AccountIdentity account) {
            selectAccountForLogin(account);
        }
    }

    private List<AccountIdentity> allAccounts() {
        List<AccountIdentity> accounts = new ArrayList<>(
                accountService.getAccounts(AccountRole.PASSENGER));
        accounts.addAll(accountService.getAccounts(AccountRole.DRIVER));
        return accounts;
    }

    private AccountIdentity findInitialAccount() {
        AccountIdentity lastAccount = accountService.getLastAccount();
        if (lastAccount != null) {
            return lastAccount;
        }
        List<AccountIdentity> accounts = allAccounts();
        return accounts.isEmpty() ? null : accounts.get(0);
    }

    void selectAccountForLogin(AccountIdentity account) {
        if (account == null || !allAccounts().contains(account)) {
            throw new IllegalArgumentException("Select a saved account.");
        }
        selectedAccount = account;
        passwordField.setText("");
        updateSelectedAccountDisplay();
    }

    private void updateSelectedAccountDisplay() {
        if (selectedAccount == null) {
            accountNameLabel.setText("No saved account — create one to continue");
            loginButton.setEnabled(false);
            switchAccountButton.setEnabled(false);
            return;
        }
        accountNameLabel.setText(selectedAccount.name() + "  ·  " + selectedAccount.role());
        loginButton.setEnabled(true);
        switchAccountButton.setEnabled(allAccounts().size() > 1);
    }

    private JButton createButton(String text, boolean primary) {
        JButton button = new JButton(text);
        button.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        button.setFocusPainted(false);
        if (primary) {
            button.setForeground(Color.WHITE);
            button.setBackground(ACCENT);
        }
        return button;
    }

    private void openDashboard(JFrame dashboard) {
        dashboard.setVisible(true);
        dispose();
    }

    private void openDashboard(AccountIdentity identity) {
        if (identity == null || identity.role() == null) {
            throw new IllegalStateException("The selected account has no valid role.");
        }
        if (identity.role() == AccountRole.DRIVER) {
            DriverAccount account = accountService.getDriverByMobile(identity.id());
            Rickshaw rickshaw = account == null ? null : rideService.getRickshaw(account.getRickshawId());
            if (account == null || !Objects.equals(identity.id(), account.getMobileNumber())
                    || rickshaw == null || !Objects.equals(account.getRickshawId(), rickshaw.getId())) {
                throw new IllegalStateException("The rickshaw linked to this driver account is unavailable.");
            }
            openDashboard(new DriverDashboard(rideService, rickshaw, account, accountService,
                    this::returnToLogin, this::openAdditionalSession));
        } else if (identity.role() == AccountRole.PASSENGER) {
            PassengerAccount account = accountService.getPassengerByMobile(identity.id());
            if (account == null || !Objects.equals(identity.id(), account.getMobileNumber())) {
                throw new IllegalStateException("The selected passenger account is unavailable.");
            }
            openDashboard(new PassengerDashboard(rideService, account,
                    this::returnToLogin, this::openAdditionalSession));
        } else {
            throw new IllegalStateException("The selected account role is not supported.");
        }
    }

    private void returnToLogin() {
        try {
            accountService.clearActiveSession();
        } catch (IllegalStateException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Logout error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        openAdditionalSession();
    }

    private void openAdditionalSession() {
        new LoginWindow(rideService, accountService, true).setVisible(true);
    }
}
