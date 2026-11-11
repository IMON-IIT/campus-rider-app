package ui;

import model.DriverAccount;
import model.PassengerAccount;
import model.Rickshaw;
import service.AccountService;
import service.AccountService.AccountIdentity;
import service.AccountService.AccountRole;
import service.RideService;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JToggleButton;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.CardLayout;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Login and registration for local passenger and driver accounts. */
public class LoginWindow extends JFrame {
    private static final Color PAGE = DashboardStyle.PAGE;
    private static final Color INK = DashboardStyle.INK;
    private static final Color MUTED = DashboardStyle.MUTED;

    private final RideService rideService;
    private final AccountService accountService;
    private final JPasswordField passwordField = new JPasswordField(20);
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
        setMinimumSize(new Dimension(500, 650));
        selectedAccount = findInitialAccount();

        JPanel content = new JPanel(new BorderLayout(0, 20));
        content.setBackground(PAGE);
        content.setBorder(BorderFactory.createEmptyBorder(34, 42, 24, 42));
        content.add(buildWelcomeHeader(), BorderLayout.NORTH);
        content.add(buildLoginCard(), BorderLayout.CENTER);

        JLabel footer = new JLabel("A simpler way to move around campus", SwingConstants.CENTER);
        footer.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        footer.setForeground(MUTED);
        content.add(footer, BorderLayout.SOUTH);
        setContentPane(content);

        updateSelectedAccountDisplay();
        getRootPane().setDefaultButton(loginButton);
        setSize(540, 700);
        setLocationRelativeTo(null);
    }

    private JPanel buildWelcomeHeader() {
        JPanel header = new JPanel(new BorderLayout(14, 14));
        header.setOpaque(false);

        BrandMark mark = new BrandMark();
        JPanel brand = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        brand.setOpaque(false);
        brand.add(mark);
        JLabel name = new JLabel("Campus Rider");
        name.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 20));
        name.setForeground(INK);
        brand.add(name);

        JPanel welcome = new JPanel(new GridLayout(0, 1, 0, 5));
        welcome.setOpaque(false);
        JLabel title = new JLabel("Welcome back", SwingConstants.CENTER);
        title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 28));
        title.setForeground(INK);
        JLabel subtitle = new JLabel("Sign in to continue your campus journey.", SwingConstants.CENTER);
        subtitle.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        subtitle.setForeground(MUTED);
        welcome.add(title);
        welcome.add(subtitle);

        JPanel stacked = new JPanel(new BorderLayout(0, 22));
        stacked.setOpaque(false);
        stacked.add(brand, BorderLayout.NORTH);
        stacked.add(welcome, BorderLayout.CENTER);
        header.add(stacked, BorderLayout.CENTER);
        return header;
    }

    private JPanel buildLoginCard() {
        JPanel card = cardPanel();
        card.setLayout(new BorderLayout(0, 20));

        JPanel account = new JPanel(new BorderLayout(0, 8));
        account.setOpaque(false);
        JLabel accountTitle = sectionLabel("YOUR ACCOUNT");
        accountNameLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
        accountNameLabel.setForeground(INK);
        account.add(accountTitle, BorderLayout.NORTH);
        account.add(accountNameLabel, BorderLayout.CENTER);

        JButton registerButton = createButton("Create Account", false);
        loginButton.addActionListener(event -> logIn());
        switchAccountButton.addActionListener(event -> switchAccount());
        registerButton.addActionListener(event -> showRegistration());

        JPanel accountActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        accountActions.setOpaque(false);
        accountActions.add(switchAccountButton);
        JPanel accountBlock = new JPanel(new BorderLayout(0, 8));
        accountBlock.setOpaque(false);
        accountBlock.add(account, BorderLayout.CENTER);
        accountBlock.add(accountActions, BorderLayout.SOUTH);

        JPanel passwordBlock = new JPanel(new BorderLayout(0, 8));
        passwordBlock.setOpaque(false);
        JLabel passwordTitle = sectionLabel("PASSWORD");
        DashboardStyle.styleInput(passwordField);
        JCheckBox showPassword = new JCheckBox("Show password");
        showPassword.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        showPassword.setForeground(MUTED);
        showPassword.setOpaque(false);
        bindShowPassword(showPassword, passwordField);
        JButton forgotPassword = createLinkButton("Forgot password?");
        forgotPassword.addActionListener(event -> showPasswordReset());
        JPanel passwordOptions = new JPanel(new BorderLayout());
        passwordOptions.setOpaque(false);
        passwordOptions.add(showPassword, BorderLayout.WEST);
        passwordOptions.add(forgotPassword, BorderLayout.EAST);
        passwordBlock.add(passwordTitle, BorderLayout.NORTH);
        passwordBlock.add(passwordField, BorderLayout.CENTER);
        passwordBlock.add(passwordOptions, BorderLayout.SOUTH);

        JPanel actions = new JPanel(new GridLayout(0, 1, 0, 9));
        actions.setOpaque(false);
        actions.add(loginButton);
        actions.add(registerButton);

        JPanel form = new JPanel(new GridLayout(0, 1, 0, 16));
        form.setOpaque(false);
        form.add(accountBlock);
        form.add(passwordBlock);
        form.add(actions);

        message.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        message.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        card.add(form, BorderLayout.CENTER);
        card.add(message, BorderLayout.SOUTH);
        return card;
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
        JDialog dialog = new JDialog(this, "Create your account", true);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setMinimumSize(new Dimension(470, 650));

        JPanel content = new JPanel(new BorderLayout(0, 16));
        content.setBackground(PAGE);
        content.setBorder(BorderFactory.createEmptyBorder(28, 32, 24, 32));
        JPanel heading = new JPanel(new GridLayout(0, 1, 0, 5));
        heading.setOpaque(false);
        JLabel title = new JLabel("Create your account", SwingConstants.CENTER);
        title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 24));
        title.setForeground(INK);
        JLabel subtitle = new JLabel("Join your campus ride community.", SwingConstants.CENTER);
        subtitle.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        subtitle.setForeground(MUTED);
        heading.add(title);
        heading.add(subtitle);
        content.add(heading, BorderLayout.NORTH);

        JPanel card = cardPanel();
        card.setLayout(new BorderLayout(0, 14));
        JLabel roleTitle = sectionLabel("I'M HERE TO");
        JToggleButton passengerRole = new JToggleButton(
                "<html><center><b>Passenger</b><br>Book a shared campus ride</center></html>");
        JToggleButton driverRole = new JToggleButton(
                "<html><center><b>Driver</b><br>Offer seats on a campus trip</center></html>");
        ButtonGroup roleGroup = new ButtonGroup();
        roleGroup.add(passengerRole);
        roleGroup.add(driverRole);
        passengerRole.setSelected(true);
        styleRoleButton(passengerRole, true);
        styleRoleButton(driverRole, false);
        passengerRole.addActionListener(event -> {
            styleRoleButton(passengerRole, true);
            styleRoleButton(driverRole, false);
        });
        driverRole.addActionListener(event -> {
            styleRoleButton(passengerRole, false);
            styleRoleButton(driverRole, true);
        });
        JPanel roles = new JPanel(new GridLayout(1, 2, 10, 0));
        roles.setOpaque(false);
        roles.add(passengerRole);
        roles.add(driverRole);

        JTextField fullName = new JTextField(20);
        JPasswordField password = new JPasswordField(20);
        JPasswordField confirmPassword = new JPasswordField(20);
        DashboardStyle.styleInput(fullName);
        DashboardStyle.styleInput(password);
        DashboardStyle.styleInput(confirmPassword);
        JCheckBox showPassword = new JCheckBox("Show passwords");
        showPassword.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        showPassword.setForeground(MUTED);
        showPassword.setOpaque(false);
        bindShowPasswords(showPassword, password, confirmPassword);

        JPanel fields = new JPanel(new GridLayout(0, 1, 0, 6));
        fields.setOpaque(false);
        fields.add(sectionLabel("FULL NAME"));
        fields.add(fullName);
        fields.add(sectionLabel("PASSWORD"));
        fields.add(password);
        fields.add(sectionLabel("CONFIRM PASSWORD"));
        fields.add(confirmPassword);
        fields.add(showPassword);

        MessageLabel registrationMessage = new MessageLabel();
        registrationMessage.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        registrationMessage.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        JButton create = createButton("Create Account", true);
        JButton back = createButton("Back to Login", false);
        create.addActionListener(event -> {
            char[] secret = password.getPassword();
            char[] confirmation = confirmPassword.getPassword();
            try {
                if (!Arrays.equals(secret, confirmation)) {
                    throw new IllegalArgumentException("Passwords do not match. Please try again.");
                }
                AccountRole role = driverRole.isSelected() ? AccountRole.DRIVER : AccountRole.PASSENGER;
                AccountIdentity account = accountService.createSimpleAccount(role, fullName.getText(), secret);
                selectedAccount = account;
                dialog.dispose();
                openDashboard(account);
            } catch (IllegalArgumentException | IllegalStateException | java.io.IOException e) {
                registrationMessage.showError(e.getMessage());
            } finally {
                Arrays.fill(secret, '\0');
                Arrays.fill(confirmation, '\0');
                password.setText("");
                confirmPassword.setText("");
            }
        });
        back.addActionListener(event -> dialog.dispose());
        JPanel buttons = new JPanel(new GridLayout(0, 1, 0, 8));
        buttons.setOpaque(false);
        buttons.add(create);
        buttons.add(back);

        JPanel inner = new JPanel(new BorderLayout(0, 14));
        inner.setOpaque(false);
        inner.add(roleTitle, BorderLayout.NORTH);
        inner.add(roles, BorderLayout.CENTER);
        JPanel accountFields = new JPanel(new BorderLayout(0, 12));
        accountFields.setOpaque(false);
        accountFields.add(fields, BorderLayout.CENTER);
        accountFields.add(registrationMessage, BorderLayout.SOUTH);
        inner.add(accountFields, BorderLayout.SOUTH);
        card.add(inner, BorderLayout.CENTER);
        card.add(buttons, BorderLayout.SOUTH);
        content.add(card, BorderLayout.CENTER);
        dialog.setContentPane(content);
        dialog.getRootPane().setDefaultButton(create);
        dialog.setSize(520, 760);
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private void showPasswordReset() {
        JDialog dialog = new JDialog(this, "Reset your password", true);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        JTextField fullName = new JTextField(22);
        JTextField accountIdentifier = new JTextField(22);
        JPasswordField newPassword = new JPasswordField(22);
        JPasswordField confirmPassword = new JPasswordField(22);
        DashboardStyle.styleInput(fullName);
        DashboardStyle.styleInput(accountIdentifier);
        DashboardStyle.styleInput(newPassword);
        DashboardStyle.styleInput(confirmPassword);

        MessageLabel verificationMessage = new MessageLabel();
        verificationMessage.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        MessageLabel resetMessage = new MessageLabel();
        resetMessage.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        AccountIdentity[] verifiedAccount = new AccountIdentity[1];
        JLabel verifiedAccountLabel = new JLabel(" ");

        JPanel steps = new JPanel(new CardLayout());
        steps.add(buildResetIdentityStep(dialog, fullName, accountIdentifier,
                verificationMessage, verifiedAccount, verifiedAccountLabel, steps), "identity");
        steps.add(buildNewPasswordStep(dialog, fullName, accountIdentifier, newPassword,
                confirmPassword, resetMessage, verifiedAccount, verifiedAccountLabel, steps), "password");

        dialog.setContentPane(steps);
        dialog.getRootPane().setDefaultButton(findButton(steps, "Verify Account"));
        dialog.setMinimumSize(new Dimension(480, 520));
        dialog.setSize(520, 600);
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private JPanel buildResetIdentityStep(JDialog dialog, JTextField fullName,
                                          JTextField accountIdentifier, MessageLabel stepMessage,
                                          AccountIdentity[] verifiedAccount, JLabel verifiedAccountLabel,
                                          JPanel steps) {
        JPanel content = new JPanel(new BorderLayout(0, 18));
        content.setBackground(PAGE);
        content.setBorder(BorderFactory.createEmptyBorder(28, 30, 24, 30));

        JPanel heading = new JPanel(new GridLayout(0, 1, 0, 6));
        heading.setOpaque(false);
        JLabel title = new JLabel("Find your account", SwingConstants.CENTER);
        title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 24));
        title.setForeground(INK);
        JLabel subtitle = new JLabel("Enter the name and mobile number saved on your account.",
                SwingConstants.CENTER);
        subtitle.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        subtitle.setForeground(MUTED);
        heading.add(title);
        heading.add(subtitle);
        content.add(heading, BorderLayout.NORTH);

        JPanel card = cardPanel();
        card.setLayout(new BorderLayout(0, 14));
        JPanel fields = new JPanel(new GridLayout(0, 1, 0, 7));
        fields.setOpaque(false);
        fields.add(sectionLabel("REGISTERED FULL NAME"));
        fields.add(fullName);
        fields.add(sectionLabel("REGISTERED MOBILE NUMBER / ACCOUNT ID"));
        fields.add(accountIdentifier);

        JLabel warning = new JLabel("<html>This local recovery checks the name and account identifier only. "
                + "It does not send an OTP or verify ownership of the mobile number.</html>");
        warning.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        warning.setForeground(new Color(123, 86, 24));
        warning.setBorder(BorderFactory.createEmptyBorder(6, 0, 4, 0));
        stepMessage.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        JPanel form = new JPanel(new BorderLayout(0, 6));
        form.setOpaque(false);
        JPanel details = new JPanel(new BorderLayout(0, 8));
        details.setOpaque(false);
        details.add(fields, BorderLayout.NORTH);
        details.add(warning, BorderLayout.CENTER);
        details.add(stepMessage, BorderLayout.SOUTH);
        form.add(details, BorderLayout.CENTER);

        JButton continueButton = createButton("Verify Account", true);
        JButton backToLogin = createButton("Back to Login", false);
        continueButton.addActionListener(event -> {
            try {
                verifiedAccount[0] = accountService.verifyPasswordResetAccount(
                        fullName.getText(), accountIdentifier.getText());
                stepMessage.setText(" ");
                verifiedAccountLabel.setText(
                        "Verified account · " + verifiedAccount[0].name() + " · " + verifiedAccount[0].role());
                ((CardLayout) steps.getLayout()).show(steps, "password");
                dialog.getRootPane().setDefaultButton(findButton(steps, "Reset Password"));
            } catch (IllegalArgumentException e) {
                verifiedAccount[0] = null;
                stepMessage.showError(e.getMessage());
            }
        });
        backToLogin.addActionListener(event -> dialog.dispose());
        JPanel actions = new JPanel(new GridLayout(0, 1, 0, 8));
        actions.setOpaque(false);
        actions.add(continueButton);
        actions.add(backToLogin);
        card.add(form, BorderLayout.CENTER);
        card.add(actions, BorderLayout.SOUTH);
        dialog.getRootPane().setDefaultButton(continueButton);
        content.add(card, BorderLayout.CENTER);
        return content;
    }

    private JPanel buildNewPasswordStep(JDialog dialog, JTextField fullName, JTextField accountIdentifier,
                                        JPasswordField newPassword, JPasswordField confirmPassword,
                                        MessageLabel stepMessage, AccountIdentity[] verifiedAccount,
                                        JLabel verifiedAccountLabel, JPanel steps) {
        JPanel content = new JPanel(new BorderLayout(0, 18));
        content.setBackground(PAGE);
        content.setBorder(BorderFactory.createEmptyBorder(28, 30, 24, 30));

        JPanel heading = new JPanel(new GridLayout(0, 1, 0, 6));
        heading.setOpaque(false);
        JLabel title = new JLabel("Choose a new password", SwingConstants.CENTER);
        title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 24));
        title.setForeground(INK);
        JLabel subtitle = new JLabel("Create a strong password for your Campus Rider account.",
                SwingConstants.CENTER);
        subtitle.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        subtitle.setForeground(MUTED);
        heading.add(title);
        heading.add(subtitle);
        content.add(heading, BorderLayout.NORTH);

        JPanel card = cardPanel();
        card.setLayout(new BorderLayout(0, 14));
        JPanel fields = new JPanel(new GridLayout(0, 1, 0, 7));
        fields.setOpaque(false);
        verifiedAccountLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        verifiedAccountLabel.setForeground(INK);
        fields.add(verifiedAccountLabel);
        fields.add(sectionLabel("NEW PASSWORD"));
        fields.add(newPassword);
        fields.add(sectionLabel("CONFIRM NEW PASSWORD"));
        fields.add(confirmPassword);
        JLabel strength = new JLabel("<html>Use 10–128 characters, including uppercase and lowercase "
                + "letters, a number, and a symbol.</html>");
        strength.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        strength.setForeground(MUTED);
        JCheckBox showPassword = new JCheckBox("Show passwords");
        showPassword.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        showPassword.setForeground(MUTED);
        showPassword.setOpaque(false);
        bindShowPasswords(showPassword, newPassword, confirmPassword);
        JPanel options = new JPanel(new BorderLayout(0, 4));
        options.setOpaque(false);
        options.add(strength, BorderLayout.NORTH);
        options.add(showPassword, BorderLayout.SOUTH);
        fields.add(options);
        stepMessage.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        JPanel form = new JPanel(new BorderLayout(0, 8));
        form.setOpaque(false);
        form.add(fields, BorderLayout.CENTER);
        form.add(stepMessage, BorderLayout.SOUTH);

        JButton reset = createButton("Reset Password", true);
        JButton backToIdentity = createButton("Use Different Account", false);
        JButton backToLogin = createButton("Back to Login", false);
        reset.addActionListener(event -> {
            char[] password = newPassword.getPassword();
            char[] confirmation = confirmPassword.getPassword();
            try {
                if (password.length == 0 || confirmation.length == 0) {
                    throw new IllegalArgumentException("Enter and confirm your new password.");
                }
                if (!passwordsMatch(password, confirmation)) {
                    throw new IllegalArgumentException("The new passwords do not match.");
                }
                accountService.resetPassword(fullName.getText(), accountIdentifier.getText(), password);
                selectedAccount = verifiedAccount[0];
                updateSelectedAccountDisplay();
                passwordField.setText("");
                message.showSuccess("Password reset. Sign in with your new password.");
                dialog.dispose();
            } catch (IllegalArgumentException | IllegalStateException | java.io.IOException e) {
                stepMessage.showError(e.getMessage());
            } finally {
                Arrays.fill(password, '\0');
                Arrays.fill(confirmation, '\0');
                newPassword.setText("");
                confirmPassword.setText("");
            }
        });
        backToIdentity.addActionListener(event -> {
            stepMessage.setText(" ");
            verifiedAccount[0] = null;
            dialog.getRootPane().setDefaultButton(
                    findButton(steps, "Verify Account"));
            newPassword.setText("");
            confirmPassword.setText("");
            ((CardLayout) steps.getLayout()).show(steps, "identity");
        });
        backToLogin.addActionListener(event -> dialog.dispose());
        JPanel actions = new JPanel(new GridLayout(0, 1, 0, 8));
        actions.setOpaque(false);
        actions.add(reset);
        actions.add(backToIdentity);
        actions.add(backToLogin);
        card.add(form, BorderLayout.CENTER);
        card.add(actions, BorderLayout.SOUTH);
        content.add(card, BorderLayout.CENTER);
        return content;
    }

    private static JButton findButton(java.awt.Container container, String text) {
        for (java.awt.Component component : container.getComponents()) {
            if (component instanceof JButton button && text.equals(button.getText())) {
                return button;
            }
            if (component instanceof java.awt.Container nested) {
                JButton found = findButton(nested, text);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private void switchAccount() {
        List<AccountIdentity> accounts = allAccounts();
        if (accounts.size() < 2) {
            message.showError("There are no other saved accounts to switch to.");
            return;
        }
        javax.swing.JComboBox<AccountIdentity> chooser =
                new javax.swing.JComboBox<>(accounts.toArray(new AccountIdentity[0]));
        chooser.setSelectedItem(selectedAccount);
        DashboardStyle.styleInput(chooser);
        int result = javax.swing.JOptionPane.showConfirmDialog(this, chooser, "Switch Account",
                javax.swing.JOptionPane.OK_CANCEL_OPTION, javax.swing.JOptionPane.PLAIN_MESSAGE);
        if (result == javax.swing.JOptionPane.OK_OPTION
                && chooser.getSelectedItem() instanceof AccountIdentity account) {
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
        message.setText(" ");
        updateSelectedAccountDisplay();
    }

    private void updateSelectedAccountDisplay() {
        if (selectedAccount == null) {
            accountNameLabel.setText("No saved account — create one to continue");
            loginButton.setEnabled(false);
            switchAccountButton.setEnabled(false);
            return;
        }
        accountNameLabel.setText("<html>" + escapeHtml(selectedAccount.name())
                + " <font color='#707070'>· " + selectedAccount.role() + "</font></html>");
        loginButton.setEnabled(true);
        switchAccountButton.setEnabled(allAccounts().size() > 1);
    }

    private JButton createButton(String text, boolean primary) {
        JButton button = new JButton(text);
        DashboardStyle.styleButton(button, primary
                ? DashboardStyle.ButtonType.PRIMARY : DashboardStyle.ButtonType.SECONDARY);
        return button;
    }

    private static JPanel cardPanel() {
        JPanel card = new JPanel();
        card.setBackground(DashboardStyle.SURFACE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(DashboardStyle.BORDER),
                BorderFactory.createEmptyBorder(24, 26, 22, 26)));
        return card;
    }

    private static JLabel sectionLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
        label.setForeground(MUTED);
        return label;
    }

    private static void bindShowPassword(JCheckBox checkbox, JPasswordField field) {
        bindShowPasswords(checkbox, field);
    }

    private static void bindShowPasswords(JCheckBox checkbox, JPasswordField... fields) {
        char[] echoCharacters = new char[fields.length];
        for (int i = 0; i < fields.length; i++) {
            echoCharacters[i] = fields[i].getEchoChar();
        }
        checkbox.addActionListener(event -> {
            for (int i = 0; i < fields.length; i++) {
                fields[i].setEchoChar(checkbox.isSelected() ? (char) 0 : echoCharacters[i]);
            }
        });
    }

    static boolean passwordsMatch(char[] password, char[] confirmation) {
        return password != null && confirmation != null && Arrays.equals(password, confirmation);
    }

    private static JButton createLinkButton(String text) {
        JButton button = new JButton("<html><u>" + text + "</u></html>");
        button.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        button.setForeground(new Color(62, 92, 130));
        button.setBorderPainted(false);
        button.setContentAreaFilled(false);
        button.setFocusPainted(false);
        button.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        return button;
    }

    private static void styleRoleButton(JToggleButton button, boolean selected) {
        DashboardStyle.styleButton(button, selected
                ? DashboardStyle.ButtonType.PRIMARY : DashboardStyle.ButtonType.SECONDARY);
        button.setVerticalTextPosition(SwingConstants.CENTER);
        button.setHorizontalTextPosition(SwingConstants.CENTER);
        button.setPreferredSize(new Dimension(170, 72));
    }

    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
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
            javax.swing.JOptionPane.showMessageDialog(this, e.getMessage(),
                    "Logout error", javax.swing.JOptionPane.ERROR_MESSAGE);
            return;
        }
        openAdditionalSession();
    }

    private void openAdditionalSession() {
        new LoginWindow(rideService, accountService, true).setVisible(true);
    }

    private static final class BrandMark extends JPanel {
        private BrandMark() {
            setOpaque(false);
            setPreferredSize(new Dimension(42, 42));
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(INK);
                g.fillRoundRect(0, 0, getWidth(), getHeight(), 13, 13);
                g.setColor(Color.WHITE);
                g.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                Path2D road = new Path2D.Double();
                road.moveTo(14, 30);
                road.lineTo(18, 12);
                road.lineTo(24, 12);
                road.lineTo(28, 30);
                g.draw(road);
                g.drawLine(20, 17, 22, 17);
                g.drawLine(19, 23, 23, 23);
            } finally {
                g.dispose();
            }
        }
    }
}
