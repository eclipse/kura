/*******************************************************************************
 * Copyright (c) 2023 Eurotech and/or its affiliates and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Eurotech
 *  Sterwen-Technology
 *******************************************************************************/
package org.eclipse.kura.web.client.ui.network;

import java.util.logging.Logger;

import org.eclipse.kura.web.client.messages.Messages;
import org.eclipse.kura.web.client.ui.NewPasswordInput;
import org.eclipse.kura.web.client.util.HelpButton;
import org.eclipse.kura.web.client.util.MessageUtils;
import org.eclipse.kura.web.shared.GwtSafeHtmlUtils;
import org.eclipse.kura.web.shared.model.Gwt8021xConfig;
import org.eclipse.kura.web.shared.model.Gwt8021xEap;
import org.eclipse.kura.web.shared.model.Gwt8021xInnerAuth;
import org.eclipse.kura.web.shared.model.GwtNetInterfaceConfig;
import org.eclipse.kura.web.shared.model.GwtSession;
import org.gwtbootstrap3.client.ui.Button;
import org.gwtbootstrap3.client.ui.Form;
import org.gwtbootstrap3.client.ui.FormLabel;
import org.gwtbootstrap3.client.ui.ListBox;
import org.gwtbootstrap3.client.ui.TextBox;
import org.gwtbootstrap3.client.ui.constants.ValidationState;
import org.gwtbootstrap3.client.ui.html.Span;
import org.gwtbootstrap3.client.ui.html.Text;
import org.gwtbootstrap3.client.ui.PanelHeader;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.user.client.ui.ScrollPanel;

public class Tab8021xUi extends Composite implements NetworkTab {

    private static Tab8021xUiUiBinder uiBinder = GWT.create(Tab8021xUiUiBinder.class);

    private static final Messages MSGS = GWT.create(Messages.class);

    interface Tab8021xUiUiBinder extends UiBinder<Widget, Tab8021xUi> {
    }

    private final NetworkTabsUi netTabs;

    Gwt8021xConfig activeConfig;
    GwtSession currentSession;

    private boolean dirty;

    // Labels
    @UiField
    FormLabel labelEap;

    @UiField
    FormLabel labelInnerAuth;

    @UiField
    FormLabel labelUsername;

    @UiField
    FormLabel labelPassword;

    @UiField
    FormLabel labelKeystorePid;

    @UiField
    FormLabel labelCaCertName;

    @UiField
    FormLabel labelPublicPrivateKeyPairName;

    @UiField
    Form form;

    // Fields
    @UiField
    Button buttonTestPassword;

    @UiField
    ListBox eap;

    @UiField
    ListBox innerAuth;

    @UiField
    TextBox username;

    @UiField
    NewPasswordInput password;

    @UiField
    TextBox keystorePid;

    @UiField
    TextBox caCertName;

    @UiField
    TextBox publicPrivateKeyPairName;

    // Help
    @UiField
    HelpButton helpEap;

    @UiField
    HelpButton helpInnerAuth;

    @UiField
    HelpButton helpUsername;

    @UiField
    HelpButton helpPassword;

    @UiField
    HelpButton helpKeystorePid;

    @UiField
    HelpButton helpCaCertName;

    @UiField
    HelpButton helpPublicPrivateKeyPairName;

    @UiField
    PanelHeader helpTitle;

    @UiField
    ScrollPanel helpText;

    public Tab8021xUi(GwtSession currentSession, NetworkTabsUi tabs) {
        initWidget(uiBinder.createAndBindUi(this));

        this.currentSession = currentSession;
        this.netTabs = tabs;

        initLabels();
        initHelpButtons();
        initListBoxes();
        initTextBoxes();

        this.buttonTestPassword.setVisible(false);
        updateFormEap(Gwt8021xEap.valueOf(this.eap.getSelectedValue()));
    }

    private void initLabels() {
        labelEap.setText(MSGS.net8021xEap());
        labelInnerAuth.setText(MSGS.net8021xInnerAuth());
        labelUsername.setText(MSGS.net8021xUsername());
        labelPassword.setText(MSGS.net8021xPassword());
        labelKeystorePid.setText(MSGS.net8021xKeystorePid());
        labelCaCertName.setText(MSGS.net8021xCaCert());
        labelPublicPrivateKeyPairName.setText(MSGS.net8021xPublicPrivateKeyPair());
    }

    private void initHelpButtons() {
        this.helpEap.setHelpText(MSGS.net8021xEapHelp());
        this.helpInnerAuth.setHelpText(MSGS.net8021xInnerAuthHelp());
        this.helpUsername.setHelpText(MSGS.net8021xUsernameHelp());
        this.helpPassword.setHelpText(MSGS.net8021xPasswordHelp());
        this.helpKeystorePid.setHelpText(MSGS.net8021xKeystorePidHelp());
        this.helpCaCertName.setHelpText(MSGS.net8021xCaCertHelp());
        this.helpPublicPrivateKeyPairName.setHelpText(MSGS.net8021xPublicPrivateKeyPairHelp());
    }

    private void initListBoxes() {
        initEapListBox();
        initInnerAuthListBox();
    }

    private void initTextBoxes() {
        initUsernameTextBox();
        initPasswordTextBox();
        initKeystorePidTextBox();
        initCaCertNameTextBox();
        initPrivateKeyNameTextBox();
    }

    private void initEapListBox() {
        for (Gwt8021xEap eap : Gwt8021xEap.values()) {
            this.eap.addItem(eap.name());
        }

        this.eap.addMouseOverHandler(event -> {
            if (this.eap.isEnabled()) {
                setHelpText(MSGS.net8021xEapHelp());
            }
        });

        this.eap.addMouseOutHandler(event -> resetHelpText());

        this.eap.addChangeHandler(event -> {
            setDirty(true);
            this.netTabs.updateTabs();
            updateFormEap(Gwt8021xEap.valueOf(this.eap.getSelectedValue()));
        });
    }

    private void initInnerAuthListBox() {
        for (Gwt8021xInnerAuth auth : Gwt8021xInnerAuth.values()) {
            this.innerAuth.addItem(auth.name());
        }

        this.innerAuth.addMouseOverHandler(event -> {
            if (this.innerAuth.isEnabled()) {
                setHelpText(MSGS.net8021xInnerAuthHelp());
            }
        });

        this.innerAuth.addMouseOutHandler(event -> resetHelpText());

        this.innerAuth.addChangeHandler(event -> {
            setDirty(true);
            this.netTabs.updateTabs();
        });
    }

    private void initUsernameTextBox() {
        this.username.addMouseOverHandler(event -> {
            if (this.username.isEnabled()) {
                setHelpText(MSGS.net8021xUsernameHelp());
            }
        });

        this.username.addBlurHandler(e -> this.username.validate());
        this.username.setAllowBlank(true);
        this.username.addMouseOutHandler(event -> resetHelpText());

        this.username.addChangeHandler(event -> {
            setDirty(true);
        });
    }

    private void initPasswordTextBox() {
        this.password.addMouseOverHandler(event -> {
            if (this.password.isEnabled()) {
                setHelpText(MSGS.net8021xPasswordHelp());
            }
        });

        this.password.addBlurHandler(e -> this.password.validate());
        this.password.setAllowBlank(false);
        this.password.setAllowPlaceholder(true);
        this.password.addMouseOutHandler(event -> resetHelpText());

        this.password.addChangeHandler(event -> {
            setDirty(true);
        });
    }

    private void initKeystorePidTextBox() {
        this.keystorePid.addMouseOverHandler(event -> {
            if (this.keystorePid.isEnabled()) {
                setHelpText(MSGS.net8021xKeystorePidHelp());
            }
        });

        this.keystorePid.addBlurHandler(e -> this.keystorePid.validate());
        this.keystorePid.setAllowBlank(false);
        this.keystorePid.addMouseOutHandler(event -> resetHelpText());

        this.keystorePid.addChangeHandler(event -> {
            setDirty(true);
        });
    }

    private void initCaCertNameTextBox() {
        this.caCertName.addMouseOverHandler(event -> {
            if (this.caCertName.isEnabled()) {
                setHelpText(MSGS.net8021xCaCertHelp());
            }
        });

        this.caCertName.addBlurHandler(e -> this.caCertName.validate());
        this.caCertName.setAllowBlank(true);
        this.caCertName.addMouseOutHandler(event -> resetHelpText());

        this.caCertName.addChangeHandler(event -> {
            setDirty(true);
        });
    }

    private void initPrivateKeyNameTextBox() {
        this.publicPrivateKeyPairName.addMouseOverHandler(event -> {
            if (this.publicPrivateKeyPairName.isEnabled()) {
                setHelpText(MSGS.net8021xPublicPrivateKeyPairHelp());
            }
        });

        this.publicPrivateKeyPairName.addBlurHandler(e -> this.publicPrivateKeyPairName.validate());
        this.publicPrivateKeyPairName.setAllowBlank(false);
        this.publicPrivateKeyPairName.addMouseOutHandler(event -> resetHelpText());

        this.publicPrivateKeyPairName.addChangeHandler(event -> {
            setDirty(true);
        });
    }

    private void reset() {
        for (int i = 0; i < this.eap.getItemCount(); i++) {
            if (this.eap.getSelectedItemText().equals(Gwt8021xEap.TTLS.name())) {
                this.eap.setSelectedIndex(i);
                break;
            }
        }

        for (int i = 0; i < this.innerAuth.getItemCount(); i++) {
            if (this.innerAuth.getSelectedItemText().equals(Gwt8021xInnerAuth.NONE.name())) {
                this.innerAuth.setSelectedIndex(i);
                break;
            }
        }

        update();
    }

    private void setValues() {

        for (int i = 0; i < this.eap.getItemCount(); i++) {
            if (this.eap.getItemText(i).equals(this.activeConfig.getEapEnum().name())) {
                this.eap.setSelectedIndex(i);
                break;
            }
        }

        for (int i = 0; i < this.innerAuth.getItemCount(); i++) {
            if (this.innerAuth.getItemText(i).equals(this.activeConfig.getInnerAuthEnum().name())) {
                this.innerAuth.setSelectedIndex(i);
                break;
            }
        }
    }

    private boolean checkPassword() {

        if (!this.password.isEnabled()) {
            return true;
        }

        return !this.password.getText().isEmpty();
    }

    private void update() {
        setValues();
    }

    @Override
    public void clear() {
        // Not needed
    }

    @Override
    public void refresh() {
        if (isDirty()) {
            setDirty(false);
            if (this.activeConfig == null) {
                reset();
            } else {
                update();
            }
        }
    }

    @Override
    public boolean isDirty() {
        return this.dirty;
    }

    @Override
    public boolean isValid() {
        boolean result = this.form.validate();

        result &= checkPassword();

        return result;
    }

    @Override
    public void setDirty(boolean dirty) {
        this.dirty = dirty;
        if (this.netTabs.getButtons() != null) {
            this.netTabs.getButtons().setButtonsDirty(dirty);
        }
    }

    @Override
    public void getUpdatedNetInterface(GwtNetInterfaceConfig updatedNetIf) {
        Gwt8021xConfig updated8021xConfig = new Gwt8021xConfig();

        updated8021xConfig.setIdentity(this.username.getText());

        updated8021xConfig.setPassword(this.password.getText());

        if (!this.eap.getSelectedValue().isEmpty() && this.eap.getSelectedValue() != null) {
            updated8021xConfig.setEap(Gwt8021xEap.valueOf(this.eap.getSelectedValue()));
        }

        if (!this.innerAuth.getSelectedValue().isEmpty() && this.innerAuth.getSelectedValue() != null) {
            updated8021xConfig.setInnerAuthEnum(Gwt8021xInnerAuth.valueOf(this.innerAuth.getSelectedValue()));
        }

        updated8021xConfig.setKeystorePid(this.keystorePid.getText());
        updated8021xConfig.setCaCertName(this.caCertName.getText());
        updated8021xConfig.setPublicPrivateKeyPairName(this.publicPrivateKeyPairName.getText());

        updatedNetIf.setEnterpriseConfig(updated8021xConfig);
    }

    @Override
    public void setNetInterface(GwtNetInterfaceConfig config) {

        this.activeConfig = config.get8021xConfig();

        for (int i = 0; i < this.eap.getItemCount(); i++) {
            if (this.eap.getValue(i).equals(config.get8021xConfig().getEapEnum().name())) {
                this.eap.setSelectedIndex(i);
                break;
            }
        }

        for (int i = 0; i < this.innerAuth.getItemCount(); i++) {
            if (this.innerAuth.getValue(i).equals(config.get8021xConfig().getInnerAuthEnum().name())) {
                this.innerAuth.setSelectedIndex(i);
                break;
            }
        }

        this.username.setValue(config.get8021xConfig().getUsername());
        this.password.setValue(config.get8021xConfig().getPassword());

        if (!this.activeConfig.getPassword().isEmpty()) {
            this.password.setValue(this.activeConfig.getPassword());
        }

        this.keystorePid.setValue(config.get8021xConfig().getKeystorePid());
        this.caCertName.setValue(config.get8021xConfig().getCaCertName());
        this.publicPrivateKeyPairName.setValue(config.get8021xConfig().getPublicPrivateKeyPairName());
    }

    private void updateFormEap(Gwt8021xEap eap) {
        switch (eap) {
        case PEAP:
        case TTLS:
            this.innerAuth.setEnabled(true);
            this.username.setEnabled(true);
            this.password.setEnabled(true);
            this.keystorePid.setEnabled(false);
            this.caCertName.setEnabled(false);
            this.publicPrivateKeyPairName.setEnabled(false);
            break;
        case TLS:
            this.innerAuth.setEnabled(false);
            setInnerAuthTo(Gwt8021xInnerAuth.NONE);
            this.username.setEnabled(true);
            this.password.setEnabled(false);
            this.keystorePid.setEnabled(true);
            this.caCertName.setEnabled(true);
            this.publicPrivateKeyPairName.setEnabled(true);
            break;
        default:
            break;
        }
    }

    private void setInnerAuthTo(Gwt8021xInnerAuth auth) {
        for (int i = 0; i < this.innerAuth.getItemCount(); i++) {
            if (this.innerAuth.getItemText(i).equals(auth.name())) {
                this.innerAuth.setSelectedIndex(i);
                break;
            }
        }
    }

    private void setHelpText(String message) {
        this.helpText.clear();
        this.helpText.add(new Span(message));
    }

    private void resetHelpText() {
        this.helpText.clear();
        this.helpText.add(new Span(MSGS.netHelpDefaultHint()));
    }

}
