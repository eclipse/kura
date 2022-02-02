/*******************************************************************************
 * Copyright (c) 2011, 2022 Eurotech and/or its affiliates and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Eurotech
 *******************************************************************************/
package org.eclipse.kura.web.client.ui.network;

import org.eclipse.kura.web.client.messages.Messages;
import org.eclipse.kura.web.client.ui.AlertDialog;
import org.eclipse.kura.web.client.ui.AlertDialog.ConfirmListener;
import org.eclipse.kura.web.client.ui.AlertDialog.Severity;
import org.eclipse.kura.web.client.util.HelpButton;
import org.eclipse.kura.web.client.util.MessageUtils;
import org.eclipse.kura.web.client.util.TextFieldValidator.FieldType;
import org.eclipse.kura.web.shared.model.GwtNetIfType;
import org.eclipse.kura.web.shared.model.GwtNetInterfaceConfig;
import org.eclipse.kura.web.shared.model.GwtNetRouterMode;
import org.eclipse.kura.web.shared.model.GwtSession;
import org.eclipse.kura.web.shared.model.GwtWifiConfig;
import org.eclipse.kura.web.shared.model.GwtWifiWirelessMode;
import org.gwtbootstrap3.client.ui.Form;
import org.gwtbootstrap3.client.ui.FormGroup;
import org.gwtbootstrap3.client.ui.FormLabel;
import org.gwtbootstrap3.client.ui.HelpBlock;
import org.gwtbootstrap3.client.ui.InlineRadio;
import org.gwtbootstrap3.client.ui.ListBox;
import org.gwtbootstrap3.client.ui.PanelHeader;
import org.gwtbootstrap3.client.ui.TextBox;
import org.gwtbootstrap3.client.ui.constants.ValidationState;
import org.gwtbootstrap3.client.ui.html.Span;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.ScrollPanel;
import com.google.gwt.user.client.ui.Widget;

public class TabDhcpNatUi extends Composite implements NetworkTab {

    private static final String ROUTER_OFF_MESSAGE = MessageUtils.get(GwtNetRouterMode.netRouterOff.name());
    private static final String ROUTER_NAT_MESSAGE = MessageUtils.get(GwtNetRouterMode.netRouterNat.name());
    private static final String WIFI_DISABLED = GwtWifiWirelessMode.netWifiWirelessModeDisabled.name();
    private static final String WIFI_STATION_MODE = GwtWifiWirelessMode.netWifiWirelessModeStation.name();
    private static TabDhcpNatUiUiBinder uiBinder = GWT.create(TabDhcpNatUiUiBinder.class);

    interface TabDhcpNatUiUiBinder extends UiBinder<Widget, TabDhcpNatUi> {
    }

    private static final String REGEX_IPV4 = "\\b(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\b";
    private static final Messages MSGS = GWT.create(Messages.class);
    private final GwtSession session;
    private final TabTcpIpUi tcpTab;
    private final TabWirelessUi wirelessTab;
    private Boolean dirty;
    private GwtNetInterfaceConfig selectedNetIfConfig;
    private final NetworkTabsUi tabs;

    @UiField
    Form form;

    @UiField
    FormLabel labelRouter;
    @UiField
    FormLabel labelBegin;
    @UiField
    FormLabel labelEnd;
    @UiField
    FormLabel labelSubnet;
    @UiField
    FormLabel labelDefaultLease;
    @UiField
    FormLabel labelMaxLease;
    @UiField
    FormLabel labelPass;

    @UiField
    ListBox router;

    @UiField
    TextBox begin;
    @UiField
    TextBox end;
    @UiField
    TextBox subnet;
    @UiField
    TextBox defaultLease;
    @UiField
    TextBox maxLease;

    @UiField
    InlineRadio radio1;
    @UiField
    InlineRadio radio2;

    @UiField
    FormGroup groupRouter;
    @UiField
    FormGroup groupBegin;
    @UiField
    FormGroup groupEnd;
    @UiField
    FormGroup groupSubnet;
    @UiField
    FormGroup groupDefaultLease;
    @UiField
    FormGroup groupMaxLease;

    @UiField
    HelpBlock helpRouter;

    @UiField
    PanelHeader helpTitle;

    @UiField
    ScrollPanel helpText;

    @UiField
    HelpButton routerHelp;
    @UiField
    HelpButton beginHelp;
    @UiField
    HelpButton endHelp;
    @UiField
    HelpButton subnetHelp;
    @UiField
    HelpButton defaultLeaseHelp;
    @UiField
    HelpButton maxLeaseHelp;
    @UiField
    HelpButton passHelp;

    @UiField
    AlertDialog alertDialog;

    public TabDhcpNatUi(GwtSession currentSession, TabTcpIpUi tcp, TabWirelessUi wireless, NetworkTabsUi netTabs) {
        initWidget(uiBinder.createAndBindUi(this));
        this.tabs = netTabs;
        this.tcpTab = tcp;
        this.wirelessTab = wireless;
        this.session = currentSession;
        setDirty(false);
        initForm();

        initHelpButtons();

        this.tcpTab.status.addChangeHandler(event -> update());

        this.wirelessTab.wireless.addChangeHandler(event -> update());
    }

    @Override
    public void setDirty(boolean flag) {
        this.dirty = flag;
        if (this.tabs.getButtons() != null) {
            this.tabs.getButtons().setApplyButtonDirty(flag);
        }
    }

    @Override
    public boolean isDirty() {
        return this.dirty;
    }

    @Override
    public boolean isValid() {
        return !(this.groupRouter.getValidationState().equals(ValidationState.ERROR)
                || this.groupBegin.getValidationState().equals(ValidationState.ERROR)
                || this.groupEnd.getValidationState().equals(ValidationState.ERROR)
                || this.groupSubnet.getValidationState().equals(ValidationState.ERROR)
                || this.groupDefaultLease.getValidationState().equals(ValidationState.ERROR)
                || this.groupMaxLease.getValidationState().equals(ValidationState.ERROR));
    }

    @Override
    public void setNetInterface(GwtNetInterfaceConfig config) {
        setDirty(true);
        this.selectedNetIfConfig = config;
    }

    @Override
    public void refresh() {
        if (isDirty()) {
            setDirty(false);
            resetValidations();
            if (this.selectedNetIfConfig == null) {
                reset();
            } else {
                update();
            }
        }
    }

    @Override
    public void getUpdatedNetInterface(GwtNetInterfaceConfig updatedNetIf) {
        if (this.session != null) {

            for (GwtNetRouterMode mode : GwtNetRouterMode.values()) {
                if (MessageUtils.get(mode.name()).equals(this.router.getSelectedItemText())) {
                    updatedNetIf.setRouterMode(mode.name());
                }
            }
            updatedNetIf.setRouterDhcpBeginAddress(this.begin.getText());
            updatedNetIf.setRouterDhcpEndAddress(this.end.getText());
            updatedNetIf.setRouterDhcpSubnetMask(this.subnet.getText());
            if (this.defaultLease.getText() != null && !"".equals(this.defaultLease.getText().trim())) {
                updatedNetIf.setRouterDhcpDefaultLease(Integer.parseInt(this.defaultLease.getText()));
            }
            if (this.maxLease.getText() != null && !"".equals(this.maxLease.getText().trim())) {
                updatedNetIf.setRouterDhcpMaxLease(Integer.parseInt(this.maxLease.getText()));
            }
            updatedNetIf.setRouterDnsPass(this.radio1.getValue());

        }
    }

    @Override
    public void clear() {
        // Not needed
    }

    // ----------PRIVATE METHODS-------------

    private void update() {
        if (this.selectedNetIfConfig != null) {
            for (int i = 0; i < this.router.getItemCount(); i++) {
                if (this.router.getItemText(i).equals(MessageUtils.get(this.selectedNetIfConfig.getRouterMode()))) {
                    this.router.setSelectedIndex(i);
                    break;
                }
            }
            this.begin.setText(this.selectedNetIfConfig.getRouterDhcpBeginAddress());
            this.end.setText(this.selectedNetIfConfig.getRouterDhcpEndAddress());
            this.subnet.setText(this.selectedNetIfConfig.getRouterDhcpSubnetMask());
            this.defaultLease.setText(String.valueOf(this.selectedNetIfConfig.getRouterDhcpDefaultLease()));
            this.maxLease.setText(String.valueOf(this.selectedNetIfConfig.getRouterDhcpMaxLease()));
            this.radio1.setValue(this.selectedNetIfConfig.getRouterDnsPass());
            this.radio2.setValue(!this.selectedNetIfConfig.getRouterDnsPass());

        }
        refreshForm();
    }

    // enable/disable fields depending on values in other tabs
    private void refreshForm() {
        GwtWifiConfig wifiConfig = this.wirelessTab.activeConfig;
        String wifiMode = null;
        if (wifiConfig != null) {
            wifiMode = wifiConfig.getWirelessMode();
        }
        if (this.selectedNetIfConfig.getHwTypeEnum() == GwtNetIfType.WIFI && wifiMode != null
                && (wifiMode.equals(WIFI_STATION_MODE) || wifiMode.equals(WIFI_DISABLED))) {
            this.router.setEnabled(false);
            this.begin.setEnabled(false);
            this.end.setEnabled(false);
            this.subnet.setEnabled(false);
            this.defaultLease.setEnabled(false);
            this.maxLease.setEnabled(false);
            this.radio1.setEnabled(false);
            this.radio2.setEnabled(false);
        } else {
            this.router.setEnabled(true);
            this.begin.setEnabled(true);
            this.end.setEnabled(true);
            this.subnet.setEnabled(true);
            this.defaultLease.setEnabled(true);
            this.maxLease.setEnabled(true);
            this.radio1.setEnabled(true);
            this.radio2.setEnabled(true);

            String modeValue = this.router.getSelectedItemText();
            if (modeValue.equals(ROUTER_NAT_MESSAGE) || modeValue.equals(ROUTER_OFF_MESSAGE)) {
                this.router.setEnabled(true);
                this.begin.setEnabled(false);
                this.end.setEnabled(false);
                this.subnet.setEnabled(false);
                this.defaultLease.setEnabled(false);
                this.maxLease.setEnabled(false);
                this.radio1.setEnabled(false);
                this.radio2.setEnabled(false);
            } else {
                this.router.setEnabled(true);
                this.begin.setEnabled(true);
                this.end.setEnabled(true);
                this.subnet.setEnabled(true);
                this.defaultLease.setEnabled(true);
                this.maxLease.setEnabled(true);
                this.radio1.setEnabled(true);
                this.radio2.setEnabled(true);
            }
        }
    }

    private void reset() {
        this.router.setSelectedIndex(0);
        this.begin.setText("");
        this.end.setText("");
        this.subnet.setText("");
        this.defaultLease.setText("");
        this.maxLease.setText("");
        this.radio1.setValue(true);
        this.radio2.setValue(false);
        update();
    }

    private void resetValidations() {
        this.groupRouter.setValidationState(ValidationState.NONE);
        this.groupBegin.setValidationState(ValidationState.NONE);
        this.groupEnd.setValidationState(ValidationState.NONE);
        this.groupSubnet.setValidationState(ValidationState.NONE);
        this.groupDefaultLease.setValidationState(ValidationState.NONE);
        this.groupMaxLease.setValidationState(ValidationState.NONE);

        this.helpRouter.setText("");
    }

    private void initHelpButtons() {
        this.routerHelp.setHelpText(MSGS.netRouterToolTipMode());
        this.beginHelp.setHelpText(MSGS.netRouterToolTipDhcpBeginAddr());
        this.endHelp.setHelpText(MSGS.netRouterToolTipDhcpEndAddr());
        this.subnetHelp.setHelpText(MSGS.netRouterToolTipDhcpSubnet());
        this.defaultLeaseHelp.setHelpText(MSGS.netRouterToolTipDhcpDefaultLeaseTime());
        this.maxLeaseHelp.setHelpText(MSGS.netRouterToolTipDhcpMaxLeaseTime());
        this.passHelp.setHelpText(MSGS.netRouterToolTipPassDns());
    }

    private void initForm() {
        initRouterMode();
        initDHCPBeginAddress();
        initDHCPEndAddress();
        initDHCPSubnetMask();
        initDefaultLeaseTime();
        initMaxLeaseTime();
        initPassDNS();
    }

    private void initPassDNS() {
        // Pass DNS
        this.labelPass.setText(MSGS.netRouterPassDns());
        this.radio1.setText(MSGS.trueLabel());
        this.radio2.setText(MSGS.falseLabel());
        this.radio1.addMouseOverHandler(event -> {
            if (TabDhcpNatUi.this.router.isEnabled()) {
                TabDhcpNatUi.this.helpText.clear();
                TabDhcpNatUi.this.helpText.add(new Span(MSGS.netRouterToolTipPassDns()));
            }
        });
        this.radio1.addMouseOutHandler(event -> resetHelp());
        this.radio2.addMouseOverHandler(event -> {
            if (TabDhcpNatUi.this.router.isEnabled()) {
                TabDhcpNatUi.this.helpText.clear();
                TabDhcpNatUi.this.helpText.add(new Span(MSGS.netRouterToolTipPassDns()));
            }
        });
        this.radio2.addMouseOutHandler(event -> resetHelp());

        this.radio1.addValueChangeHandler(event -> setDirty(true));
        this.radio2.addValueChangeHandler(event -> setDirty(true));
        this.helpTitle.setText(MSGS.netHelpTitle());
    }

    private void initMaxLeaseTime() {
        // DHCP Max Lease
        this.labelMaxLease.setText(MSGS.netRouterDhcpMaxLease());
        this.maxLease.addMouseOverHandler(event -> {
            if (TabDhcpNatUi.this.router.isEnabled()) {
                TabDhcpNatUi.this.helpText.clear();
                TabDhcpNatUi.this.helpText.add(new Span(MSGS.netRouterToolTipDhcpMaxLeaseTime()));
            }
        });
        this.maxLease.addMouseOutHandler(event -> resetHelp());
        this.maxLease.addValueChangeHandler(event -> {
            setDirty(true);
            if (!TabDhcpNatUi.this.maxLease.getText().trim().matches(FieldType.NUMERIC.getRegex())
                    || !isPositiveInteger(TabDhcpNatUi.this.maxLease.getText().trim())) {
                TabDhcpNatUi.this.groupMaxLease.setValidationState(ValidationState.ERROR);
            } else {
                TabDhcpNatUi.this.groupMaxLease.setValidationState(ValidationState.NONE);
            }
        });
    }

    private void initDefaultLeaseTime() {
        // DHCP Default Lease
        this.labelDefaultLease.setText(MSGS.netRouterDhcpDefaultLease());
        this.defaultLease.addMouseOverHandler(event -> {
            if (TabDhcpNatUi.this.router.isEnabled()) {
                TabDhcpNatUi.this.helpText.clear();
                TabDhcpNatUi.this.helpText.add(new Span(MSGS.netRouterToolTipDhcpDefaultLeaseTime()));
            }
        });
        this.defaultLease.addMouseOutHandler(event -> resetHelp());
        this.defaultLease.addValueChangeHandler(event -> {
            setDirty(true);
            if (!TabDhcpNatUi.this.defaultLease.getText().trim().matches(FieldType.NUMERIC.getRegex())
                    || !isPositiveInteger(TabDhcpNatUi.this.defaultLease.getText().trim())) {
                TabDhcpNatUi.this.groupDefaultLease.setValidationState(ValidationState.ERROR);
            } else {
                TabDhcpNatUi.this.groupDefaultLease.setValidationState(ValidationState.NONE);
            }
        });
    }

    private void initDHCPSubnetMask() {
        // DHCP Subnet Mask
        this.labelSubnet.setText(MSGS.netRouterDhcpSubnetMask());
        this.subnet.addMouseOverHandler(event -> {
            if (TabDhcpNatUi.this.router.isEnabled()) {
                TabDhcpNatUi.this.helpText.clear();
                TabDhcpNatUi.this.helpText.add(new Span(MSGS.netRouterToolTipDhcpSubnet()));
            }
        });
        this.subnet.addMouseOutHandler(event -> resetHelp());
        this.subnet.addValueChangeHandler(event -> {
            setDirty(true);
            if (!TabDhcpNatUi.this.subnet.getText().matches(REGEX_IPV4)) {
                TabDhcpNatUi.this.groupSubnet.setValidationState(ValidationState.ERROR);
            } else {
                TabDhcpNatUi.this.groupSubnet.setValidationState(ValidationState.NONE);
            }
        });
    }

    private void initDHCPEndAddress() {
        // DHCP Ending Address
        this.labelEnd.setText(MSGS.netRouterDhcpEndingAddress());
        this.end.addMouseOverHandler(event -> {
            if (TabDhcpNatUi.this.router.isEnabled()) {
                TabDhcpNatUi.this.helpText.clear();
                TabDhcpNatUi.this.helpText.add(new Span(MSGS.netRouterToolTipDhcpEndAddr()));
            }
        });
        this.end.addMouseOutHandler(event -> resetHelp());
        this.end.addValueChangeHandler(event -> {
            setDirty(true);
            if (!TabDhcpNatUi.this.end.getText().matches(REGEX_IPV4)) {
                TabDhcpNatUi.this.groupEnd.setValidationState(ValidationState.ERROR);
            } else {
                checkDhcpRangeValidity();
            }
        });
    }

    private void initDHCPBeginAddress() {
        // DHCP Beginning Address
        this.labelBegin.setText(MSGS.netRouterDhcpBeginningAddress());
        this.begin.addMouseOverHandler(event -> {
            if (TabDhcpNatUi.this.router.isEnabled()) {
                TabDhcpNatUi.this.helpText.clear();
                TabDhcpNatUi.this.helpText.add(new Span(MSGS.netRouterToolTipDhcpBeginAddr()));
            }
        });
        this.begin.addMouseOutHandler(event -> resetHelp());
        this.begin.addValueChangeHandler(event -> {
            setDirty(true);
            if (!TabDhcpNatUi.this.begin.getText().matches(REGEX_IPV4)) {
                TabDhcpNatUi.this.groupBegin.setValidationState(ValidationState.ERROR);
            } else {
                checkDhcpRangeValidity();
            }
        });
    }

    private void initRouterMode() {
        // Router Mode
        this.labelRouter.setText(MSGS.netRouterMode());
        int i = 0;
        for (GwtNetRouterMode mode : GwtNetRouterMode.values()) {
            this.router.addItem(MessageUtils.get(mode.name()));

            if (this.tcpTab.isDhcp() && mode.equals(GwtNetRouterMode.netRouterOff)) {
                this.router.setSelectedIndex(i);
            }
            i++;
        }
        this.router.addMouseOverHandler(event -> {
            if (TabDhcpNatUi.this.router.isEnabled()) {
                TabDhcpNatUi.this.helpText.clear();
                TabDhcpNatUi.this.helpText.add(new Span(MSGS.netRouterToolTipMode()));
            }
        });
        this.router.addMouseOutHandler(event -> resetHelp());
        this.router.addChangeHandler(event -> {
            setDirty(true);
            ListBox box = (ListBox) event.getSource();
            if (TabDhcpNatUi.this.tcpTab.isDhcp() && !box.getSelectedItemText().equals(ROUTER_OFF_MESSAGE)) {
                TabDhcpNatUi.this.groupRouter.setValidationState(ValidationState.ERROR);
                TabDhcpNatUi.this.helpRouter.setText(MSGS.netRouterConfiguredForDhcpError());
                TabDhcpNatUi.this.helpRouter.setColor("red");
            } else {
                TabDhcpNatUi.this.groupRouter.setValidationState(ValidationState.NONE);
                TabDhcpNatUi.this.helpRouter.setText("");
            }
            if (this.router.getSelectedIndex() == 0 || this.router.getSelectedIndex() == 2) {
                this.alertDialog.show(MSGS.netRouterDhcpNATWarning(), Severity.ALERT, (ConfirmListener) null);
            }
            refreshForm();
        });
    }

    private void resetHelp() {
        this.helpText.clear();
        this.helpText.add(new Span(MSGS.netHelpDefaultHint()));
    }

    private boolean isPositiveInteger(String value) {
        try {
            if (Integer.parseInt(value) > 0) {
                return true;
            }
        } catch (NumberFormatException e) {
            return false;
        }
        return false;
    }

    private void checkDhcpRangeValidity() {
        if (!isDhcpRangeValid()) {
            TabDhcpNatUi.this.groupBegin.setValidationState(ValidationState.ERROR);
            TabDhcpNatUi.this.groupEnd.setValidationState(ValidationState.ERROR);
        } else {
            TabDhcpNatUi.this.groupBegin.setValidationState(ValidationState.NONE);
            TabDhcpNatUi.this.groupEnd.setValidationState(ValidationState.NONE);
        }
    }

    private boolean isDhcpRangeValid() {
        try {
            long beginAddress = addressToLong(TabDhcpNatUi.this.begin.getText().trim());
            long endAddress = addressToLong(TabDhcpNatUi.this.end.getText().trim());
            return endAddress > beginAddress;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private long addressToLong(String address) {
        String[] addressArray = address.split("\\.");
        long addressLong = 0;
        for (String addressfield : addressArray) {
            addressLong <<= 8;
            addressLong |= Long.parseLong(addressfield) & 0x000000ff;
        }
        return addressLong;
    }
}
