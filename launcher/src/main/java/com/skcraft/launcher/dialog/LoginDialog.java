/*
 * SK's Minecraft Launcher
 * Copyright (C) 2010-2014 Albert Pham <http://www.sk89q.com> and contributors
 * Please see LICENSE.txt for license information.
 */

package com.skcraft.launcher.dialog;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.net.URI;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Callable;

import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JPopupMenu;
import javax.swing.WindowConstants;

import com.google.common.base.Strings;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.saferize.sdk.Approval;
import com.saferize.sdk.Approval.Status;
import com.saferize.sdk.SaferizeClient;
import com.skcraft.concurrency.ObservableFuture;
import com.skcraft.concurrency.ProgressObservable;
import com.skcraft.launcher.Configuration;
import com.skcraft.launcher.Launcher;
import com.skcraft.launcher.auth.Account;
import com.skcraft.launcher.auth.AccountList;
import com.skcraft.launcher.auth.AuthenticationException;
import com.skcraft.launcher.auth.LoginService;
import com.skcraft.launcher.auth.OfflineSession;
import com.skcraft.launcher.auth.SaferizeToken;
import com.skcraft.launcher.auth.Session;
import com.skcraft.launcher.persistence.Persistence;
import com.skcraft.launcher.swing.ActionListeners;
import com.skcraft.launcher.swing.FormPanel;
import com.skcraft.launcher.swing.LinedBoxPanel;
import com.skcraft.launcher.swing.LinkButton;
import com.skcraft.launcher.swing.PopupMouseAdapter;
import com.skcraft.launcher.swing.SwingHelper;
import com.skcraft.launcher.swing.TextFieldPopupMenu;
import com.skcraft.launcher.util.SharedLocale;
import com.skcraft.launcher.util.SwingExecutor;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

/**
 * The login dialog.
 */
public class LoginDialog extends JDialog {

    private final Launcher launcher;
    @Getter private final AccountList accounts;
    @Getter private Session session;
    @Getter @Setter private String parentEmail;
    

    private final JComboBox idCombo = new JComboBox();
    private final JPasswordField passwordText = new JPasswordField();
    private final JCheckBox rememberIdCheck = new JCheckBox(SharedLocale.tr("login.rememberId"));
    private final JCheckBox rememberPassCheck = new JCheckBox(SharedLocale.tr("login.rememberPassword"));
    private final JButton loginButton = new JButton(SharedLocale.tr("login.login"));
    private final LinkButton recoverButton = new LinkButton(SharedLocale.tr("login.recoverAccount"));
    private final LinkButton createAccountButton = new LinkButton(SharedLocale.tr("login.createAccount"));
    private final JButton offlineButton = new JButton(SharedLocale.tr("login.playOffline"));
    private final JButton cancelButton = new JButton(SharedLocale.tr("button.cancel"));
    private final FormPanel formPanel = new FormPanel();
    private final LinedBoxPanel buttonsPanel = new LinedBoxPanel(true);
    private final JPanel outerPanel = new JPanel();

    /**
     * Create a new login dialog.
     *
     * @param owner the owner
     * @param launcher the launcher
     */
    public LoginDialog(Window owner, @NonNull Launcher launcher) {
        super(owner, ModalityType.DOCUMENT_MODAL);

        this.launcher = launcher;
        this.accounts = launcher.getAccounts();

        setTitle(SharedLocale.tr("login.title"));
        initComponents();
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(420, 0));
        setResizable(false);
        pack();
        setLocationRelativeTo(owner);

        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                removeListeners();
                dispose();
            }
        });
    }

    @SuppressWarnings("unchecked")
    private void removeListeners() {
        idCombo.setModel(new DefaultComboBoxModel());
    }

    @SuppressWarnings("unchecked")
    private void initComponents() {
        idCombo.setModel(getAccounts());
        updateSelection();

        rememberIdCheck.setBorder(BorderFactory.createEmptyBorder());
        rememberPassCheck.setBorder(BorderFactory.createEmptyBorder());
        idCombo.setEditable(true);
        idCombo.getEditor().selectAll();
        

        loginButton.setFont(loginButton.getFont().deriveFont(Font.BOLD));

        
        formPanel.addRow(new JLabel(SharedLocale.tr("login.idEmail")), idCombo);
        formPanel.addRow(new JLabel(SharedLocale.tr("login.password")), passwordText);
        formPanel.addRow(recoverButton);
        formPanel.addRow(new JLabel(), rememberIdCheck);
        formPanel.addRow(new JLabel(), rememberPassCheck);
        
        buttonsPanel.setBorder(BorderFactory.createEmptyBorder(26, 13, 13, 13));

        //buttonsPanel.addElement(recoverButton);
        buttonsPanel.addElement(createAccountButton);
        buttonsPanel.addGlue();
        buttonsPanel.addElement(loginButton);
        buttonsPanel.addElement(cancelButton);
        
        
        outerPanel.setLayout(new GridBagLayout());
        
        JLabel stepsLabel = new JLabel("Step 2 of 2");
        stepsLabel.setFont(stepsLabel.getFont().deriveFont(Font.BOLD));       
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(10, 2, 10, 2);        
        outerPanel.add(stepsLabel, c);
        
        JLabel loginWithMinecraftLabel = new JLabel("Login with your Minecraft/Mojang Account");
        loginWithMinecraftLabel.setFont(loginWithMinecraftLabel.getFont().deriveFont(Font.BOLD));
        c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 1;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(10, 12, 10, 2);
        
        outerPanel.add(loginWithMinecraftLabel, c);
        
        c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 2;
        c.weightx = 1.0;
        c.fill = GridBagConstraints.HORIZONTAL;        
        outerPanel.add(formPanel, c);      
        add(outerPanel, BorderLayout.CENTER);
        add(buttonsPanel, BorderLayout.SOUTH);

        getRootPane().setDefaultButton(loginButton);

        passwordText.setComponentPopupMenu(TextFieldPopupMenu.INSTANCE);

        idCombo.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                updateSelection();
            }
        });

        idCombo.getEditor().getEditorComponent().addMouseListener(new PopupMouseAdapter() {
            @Override
            protected void showPopup(MouseEvent e) {
                popupManageMenu(e.getComponent(), e.getX(), e.getY());
            }
        });

        recoverButton.addActionListener(
                ActionListeners.openURL(recoverButton, launcher.getProperties().getProperty("resetPasswordUrl")));

        createAccountButton.addActionListener(
                ActionListeners.openURL(recoverButton, launcher.getProperties().getProperty("createAccountUrl")));
        

        loginButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                prepareLogin();
            }
        });

        offlineButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                setResult(new OfflineSession(launcher.getProperties().getProperty("offlinePlayerName")));
                removeListeners();
                dispose();
            }
        });

        cancelButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                removeListeners();
                dispose();
            }
        });

        rememberPassCheck.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (rememberPassCheck.isSelected()) {
                    rememberIdCheck.setSelected(true);
                }
            }
        });

        rememberIdCheck.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (!rememberIdCheck.isSelected()) {
                    rememberPassCheck.setSelected(false);
                }
            }
        });
    }

    private void popupManageMenu(Component component, int x, int y) {
        Object selected = idCombo.getSelectedItem();
        JPopupMenu popup = new JPopupMenu();
        JMenuItem menuItem;

        if (selected != null && selected instanceof Account) {
            final Account account = (Account) selected;

            menuItem = new JMenuItem(SharedLocale.tr("login.forgetUser"));
            menuItem.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    accounts.remove(account);
                    Persistence.commitAndForget(accounts);
                }
            });
            popup.add(menuItem);

            if (!Strings.isNullOrEmpty(account.getPassword())) {
                menuItem = new JMenuItem(SharedLocale.tr("login.forgetPassword"));
                menuItem.addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        account.setPassword(null);
                        Persistence.commitAndForget(accounts);
                    }
                });
                popup.add(menuItem);
            }
        }

        menuItem = new JMenuItem(SharedLocale.tr("login.forgetAllPasswords"));
        menuItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (SwingHelper.confirmDialog(LoginDialog.this,
                        SharedLocale.tr("login.confirmForgetAllPasswords"),
                        SharedLocale.tr("login.forgetAllPasswordsTitle"))) {
                    accounts.forgetPasswords();
                    Persistence.commitAndForget(accounts);
                }
            }
        });
        popup.add(menuItem);

        popup.show(component, x, y);
    }

    private void updateSelection() {
        Object selected = idCombo.getSelectedItem();

        if (selected != null && selected instanceof Account) {
            Account account = (Account) selected;
            String password = account.getPassword();

            rememberIdCheck.setSelected(true);
            if (!Strings.isNullOrEmpty(password)) {
                rememberPassCheck.setSelected(true);
                passwordText.setText(password);
            } else {
                rememberPassCheck.setSelected(false);
            }
        } else {
            passwordText.setText("");
            rememberIdCheck.setSelected(true);
            rememberPassCheck.setSelected(false);
        }
    }

    @SuppressWarnings("deprecation")
    private void prepareLogin() {
        Object selected = idCombo.getSelectedItem();

        if (selected != null && selected instanceof Account) {
            Account account = (Account) selected;
            String password = passwordText.getText();

            if (password == null || password.isEmpty()) {
                SwingHelper.showErrorDialog(this, SharedLocale.tr("login.noPasswordError"), SharedLocale.tr("login.noPasswordTitle"));
            } else {
                if (rememberPassCheck.isSelected()) {
                    account.setPassword(password);
                } else {
                    account.setPassword(null);
                }

                if (rememberIdCheck.isSelected()) {
                    accounts.add(account);
                } else {
                    accounts.remove(account);
                }

                account.setLastUsed(new Date());

                Persistence.commitAndForget(accounts);

                attemptLogin(this.parentEmail, account, password);
                //attemptSaferize(parentEmail, "test", new OfflineSession(launcher.getProperties().getProperty("offlinePlayerName")));
            }
        } else {
            SwingHelper.showErrorDialog(this, SharedLocale.tr("login.noLoginError"), SharedLocale.tr("login.noLoginTitle"));
        }
    }

    private void attemptLogin(String parentEmail, Account account, String password) {
        LoginCallable callable = new LoginCallable(account, password);        
        
        ObservableFuture<Session> future = new ObservableFuture<Session>(launcher.getExecutor().submit(callable), callable);
        
        Futures.addCallback(future, new FutureCallback<Session>() {
            @Override
            public void onSuccess(Session result) {            	
               // setResult(result);
            	attemptSaferize(parentEmail, result.getUuid(), result);
            }

            @Override
            public void onFailure(Throwable t) {
            }
        }, SwingExecutor.INSTANCE);

        ProgressDialog.showProgress(this, future, SharedLocale.tr("login.loggingInTitle"), SharedLocale.tr("login.loggingInStatus"));
        SwingHelper.addErrorDialogCallback(this, future);
    }
    
    private void attemptSaferize(String parentEmail, String userToken, Session session) {
    	
        SaferizeCallable callable = new SaferizeCallable(parentEmail, userToken);        
        
        ObservableFuture<SaferizeToken> future = new ObservableFuture<SaferizeToken>(launcher.getExecutor().submit(callable), callable);
        
        Futures.addCallback(future, new FutureCallback<SaferizeToken>() {
            @Override
            public void onSuccess(SaferizeToken result) {            	
               setResult(session);            	
            }

            @Override
            public void onFailure(Throwable t) {
            }
        }, SwingExecutor.INSTANCE);        
        ProgressDialog.showProgress(this, future, SharedLocale.tr("login.saferizeCreating"), SharedLocale.tr("login.saferizeCreatingStatus"));
        SwingHelper.addErrorDialogCallback(this, future);
    }

    private void setResult(Session session) {
        this.session = session;
        removeListeners();
        dispose();
    }

    public static Session showLoginRequest(Window owner, Launcher launcher) {
    	SaferizeDialog saferizeDialog = new SaferizeDialog(owner, launcher);
    	saferizeDialog.setVisible(true);
    	if (saferizeDialog.getParentEmail() == null) {
    		return null;
    	}
    	
        LoginDialog dialog = new LoginDialog(owner, launcher);
        dialog.setParentEmail(saferizeDialog.getParentEmail());
        dialog.setVisible(true);
        return dialog.getSession();
    }

    private class LoginCallable implements Callable<Session>,ProgressObservable {
        private final Account account;
        private final String password;

        private LoginCallable(Account account, String password) {
            this.account = account;
            this.password = password;
        }

        @Override
        public Session call() throws AuthenticationException, IOException, InterruptedException {
            LoginService service = launcher.getLoginService();
            List<? extends Session> identities = service.login(launcher.getProperties().getProperty("agentName"), account.getId(), password);
            
            // The list of identities (profiles in Mojang terms) corresponds to whether the account
            // owns the game, so we need to check that
            if (identities.size() > 0) {
                // Set offline enabled flag to true
                Configuration config = launcher.getConfig();
                if (!config.isOfflineEnabled()) {
                    config.setOfflineEnabled(true);
                    Persistence.commitAndForget(config);
                }

                Persistence.commitAndForget(getAccounts());
                return identities.get(0);
            } else {
                throw new AuthenticationException("Minecraft not owned", SharedLocale.tr("login.minecraftNotOwnedError"));
            }
        }

        @Override
        public double getProgress() {
            return -1;
        }

        @Override
        public String getStatus() {
            return SharedLocale.tr("login.loggingInStatus");
        }
    }
    
    private class SaferizeCallable implements Callable<SaferizeToken>,ProgressObservable {

    	private String parentEmail;
    	private String userToken;
    	
    	public SaferizeCallable(String parentEmail, String userToken) {
			this.parentEmail = parentEmail;
			this.userToken = userToken;
		}
    	
		@Override
		public double getProgress() {
			return -1;
		}

		@Override
		public String getStatus() {
			return SharedLocale.tr("login.loggingInStatus");
		}

		@Override
		public SaferizeToken call() throws Exception {
			Properties prop = launcher.getProperties();
			com.saferize.sdk.Configuration saferizeConfig = new com.saferize.sdk.Configuration(new URI(prop.getProperty("saferizeUrl")), new URI(prop.getProperty("saferizeWebsocketUrl")), prop.getProperty("saferizeAccessKey"), prop.getProperty("saferizePrivateKey"));
			SaferizeClient saferizeClient = new SaferizeClient(saferizeConfig);
			Approval approval = saferizeClient.signUp(parentEmail, userToken);
			if (approval.getStatus() == Status.REJECTED) {
				throw new AuthenticationException("Parental Control is Rejected", SharedLocale.tr("login.parentalControlRejectedError"));
			}
			SaferizeToken token = launcher.getSaferizeToken();
			token.setParentEmail(parentEmail);
			token.setUserToken(userToken);
			Persistence.commitAndForget(token);
			return token;
		}
    	
    }

}
