/*******************************************************************************
 * Copyright (c) 2020, 2024 Eurotech and/or its affiliates and others
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
package org.eclipse.kura.web.client.ui.users;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.eclipse.kura.web.client.configuration.HasConfiguration;
import org.eclipse.kura.web.client.messages.Messages;
import org.eclipse.kura.web.client.ui.ConfigurableComponentUi;
import org.eclipse.kura.web.client.ui.EntryClassUi;
import org.eclipse.kura.web.client.ui.Picker;
import org.eclipse.kura.web.client.ui.drivers.assets.BooleanInputCell;
import org.eclipse.kura.web.client.ui.validator.GwtValidators;
import org.eclipse.kura.web.shared.model.GwtConfigComponent;
import org.eclipse.kura.web.shared.model.GwtUserConfig;
import org.eclipse.kura.web.shared.model.GwtUserData;
import org.gwtbootstrap3.client.ui.Button;
import org.gwtbootstrap3.client.ui.Heading;
import org.gwtbootstrap3.client.ui.InlineRadio;
import org.gwtbootstrap3.client.ui.PanelBody;
import org.gwtbootstrap3.client.ui.PanelFooter;
import org.gwtbootstrap3.client.ui.PanelHeader;
import org.gwtbootstrap3.client.ui.constants.HeadingSize;
import org.gwtbootstrap3.client.ui.constants.InputType;
import org.gwtbootstrap3.client.ui.form.validator.Validator;

import com.google.gwt.core.client.GWT;
import com.google.gwt.editor.client.EditorError;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.cellview.client.CellTable;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.cellview.client.ColumnSortEvent;
import com.google.gwt.user.cellview.client.ColumnSortEvent.ListHandler;
import com.google.gwt.user.cellview.client.SimplePager;
import com.google.gwt.user.cellview.client.SimplePager.TextLocation;
import com.google.gwt.user.cellview.client.TextColumn;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.view.client.HasRows;
import com.google.gwt.view.client.ListDataProvider;

public class UserConfigUi extends Composite {

    protected static final Messages MSGS = GWT.create(Messages.class);

    private static UserConfigUiUiBinder uiBinder = GWT.create(UserConfigUiUiBinder.class);

    interface UserConfigUiUiBinder extends UiBinder<Widget, UserConfigUi> {
    }

    @UiField
    InlineRadio passwordChangeRequired;
    @UiField
    InlineRadio passwordPasswordChangeNotRequired;
    @UiField
    InlineRadio passwordEnabled;
    @UiField
    InlineRadio passwordDisabled;
    @UiField
    Button changePassword;
    @UiField
    Picker picker;
    @UiField
    CellTable<AssignedPermission> permissionTable;
    @UiField
    PanelFooter tablePanelFooter;
    @UiField
    PanelBody additionalConfigurationsPanel;

    private final Listener listener;
    private final GwtUserConfig userData;

    private final ListDataProvider<AssignedPermission> dataProvider = new ListDataProvider<>();
    private final boolean hasPassword;
    private final List<Validator<String>> passwordStrengthValidators;

    private SimplePager pager;

    public UserConfigUi(final GwtUserConfig userData, final Set<String> definedPermissions, final Listener listener) {
        this.listener = listener;
        this.userData = userData;
        this.hasPassword = userData.isPasswordAuthEnabled();
        this.passwordStrengthValidators = GwtValidators.passwordStrength(EntryClassUi.getUserOptions());
        initWidget(uiBinder.createAndBindUi(this));
        initTable(userData, definedPermissions);
        initPasswordWidgets();
        renderAdditionalConfigurations(userData);
    }

    private void initTable(final GwtUserData userData, final Set<String> definedPermissions) {

        this.pager = new SimplePager(TextLocation.CENTER, false, 0, true) {

            @Override
            public void nextPage() {
                setPage(getPage() + 1);
            }

            @Override
            public void setPageStart(int index) {
                final HasRows display = getDisplay();
                if (display != null) {
                    display.setVisibleRange(index, getPageSize());
                }
            }
        };
        this.pager.setPageSize(15);
        this.pager.setDisplay(this.permissionTable);
        this.tablePanelFooter.add(this.pager);

        final Column<AssignedPermission, String> assignedColumn = new Column<AssignedPermission, String>(
                new BooleanInputCell()) {

            @Override
            public String getValue(final AssignedPermission object) {
                return Boolean.toString(object.isAssigned());
            }
        };

        assignedColumn.setFieldUpdater((index, object, value) -> {
            final boolean assigned = Boolean.parseBoolean(value);
            final String permissionName = object.getName();

            object.setAssigned(assigned);

            if (assigned) {
                if (userData.getPermissions().add(permissionName)) {
                    this.listener.onUserDataChanged(userData);
                }
            } else {
                if (userData.getPermissions().remove(permissionName)) {
                    this.listener.onUserDataChanged(userData);
                }
            }
        });

        final TextColumn<AssignedPermission> nameColumn = new TextColumn<AssignedPermission>() {

            @Override
            public String getValue(AssignedPermission object) {
                return object.getName();
            }
        };

        nameColumn.setSortable(true);

        this.dataProvider.addDataDisplay(this.permissionTable);

        this.permissionTable.addColumn(assignedColumn, MSGS.usersPermissionAssigned());
        this.permissionTable.addColumn(nameColumn, MSGS.usersPermissionName());
        this.permissionTable.getColumnSortList().push(nameColumn);
        this.permissionTable.addColumnSortHandler(permissionNameSortHandler(nameColumn));

        final List<AssignedPermission> tableContent = this.dataProvider.getList();

        for (final String permission : definedPermissions) {
            tableContent.add(new AssignedPermission(permission, userData.getPermissions().contains(permission)));
        }

        ColumnSortEvent.fire(this.permissionTable, this.permissionTable.getColumnSortList());
    }

    private void renderAdditionalConfigurations(final GwtUserConfig gwtUserConfig) {
        for (final GwtConfigComponent additionalConfiguration : gwtUserConfig.getAdditionalConfigurations().values()) {
            final ConfigurableComponentUi configurableComponentUi = new ConfigurableComponentUi(
                    additionalConfiguration);

            configurableComponentUi.setListener(new HasConfiguration.Listener() {

                @Override
                public void onConfigurationChanged(HasConfiguration hasConfiguration) {
                    addAdditionalConfiguration(hasConfiguration);
                }

                @Override
                public void onDirtyStateChanged(HasConfiguration hasConfiguration) {
                    addAdditionalConfiguration(hasConfiguration);
                }

                private void addAdditionalConfiguration(HasConfiguration hasConfiguration) {
                    if (!hasConfiguration.isDirty()) {
                        return;
                    }
                    
                    if (hasConfiguration.isValid()) {
                        final GwtConfigComponent updatedConfiguration = hasConfiguration.getConfiguration();

                        userData.getAdditionalConfigurations().put(hasConfiguration.getComponentId(),
                                updatedConfiguration);
                        listener.onUserDataChanged(userData);
                    }
                }
                
            });

            final PanelHeader header = new PanelHeader();
            final Heading heading = new Heading(HeadingSize.H3);
            heading.setText(additionalConfiguration.getComponentName());
            header.add(heading);

            additionalConfigurationsPanel.add(header);
            additionalConfigurationsPanel.add(configurableComponentUi);
        }
    }

    public void initPasswordWidgets() {
        updatePasswordWidgetState();

        this.passwordEnabled.addChangeHandler(e -> {
            this.userData.setPasswordAuthEnabled(true);
            updatePasswordWidgetState();
            if (!this.hasPassword) {
                pickPassword();
            }
        });

        this.passwordDisabled.addChangeHandler(e -> {
            this.userData.setPasswordAuthEnabled(false);
            this.userData.setNewPassword(Optional.empty());
            if (this.hasPassword) {
                this.listener.onUserDataChanged(this.userData);
            }
            updatePasswordWidgetState();
        });

        this.passwordChangeRequired.addChangeHandler(e -> {
            if (!this.userData.isPasswordChangeNeeded()) {
                this.userData.setPasswordChangeNeeded(true);
                this.listener.onUserDataChanged(this.userData);
            }
        });

        this.passwordPasswordChangeNotRequired.addChangeHandler(e -> {
            if (this.userData.isPasswordChangeNeeded()) {
                this.userData.setPasswordChangeNeeded(false);
                this.listener.onUserDataChanged(this.userData);
            }
        });

        this.changePassword.addClickHandler(e -> pickPassword());
    }

    private ListHandler<AssignedPermission> permissionNameSortHandler(TextColumn<AssignedPermission> nameColumn) {
        ListHandler<AssignedPermission> handler = new ListHandler<>(this.dataProvider.getList());
        handler.setComparator(nameColumn, Comparator.comparing(AssignedPermission::getName));

        return handler;
    }

    private void pickPassword() {
        final Runnable onDismiss = () -> {
            if (!this.hasPassword) {
                this.passwordEnabled.setValue(false);
                this.passwordDisabled.setValue(true);
                this.userData.setPasswordAuthEnabled(false);
                updatePasswordWidgetState();
            }
        };

        this.picker.builder(String.class) //
                .setTitle(MSGS.usersSetPassword()) //
                .setMessage(MSGS.usersDefineNewPassword()) //
                .setInputCustomizer(input -> input.setType(InputType.PASSWORD)) //
                .setOnCancel(onDismiss) //
                .setValidator((editor, password) -> {
                    if (password == null || password.trim().isEmpty()) {
                        throw new IllegalArgumentException(MSGS.usersPasswordEmpty());
                    }
                    for (final Validator<String> validator : this.passwordStrengthValidators) {
                        final List<EditorError> errors = validator.validate(editor, password);
                        if (!errors.isEmpty()) {
                            throw new IllegalArgumentException(errors.get(0).getMessage());
                        }
                    }

                    return password;
                }).setOnPick(newPassword -> this.picker.builder(String.class) //
                        .setTitle(MSGS.usersConfirmPassword()) //
                        .setMessage(MSGS.usersRepeatPassword()) //
                        .setInputCustomizer(input -> input.setType(InputType.PASSWORD)) //
                        .setOnCancel(onDismiss) //
                        .setValidator((e, confirm) -> {
                            if (!newPassword.equals(confirm)) {
                                throw new IllegalArgumentException(MSGS.usersPasswordMismatch());
                            }

                            return confirm;
                        }).setOnPick(p -> {
                            this.userData.setNewPassword(Optional.of(p));
                            this.listener.onUserDataChanged(this.userData);
                        }).pick())
                .pick();
    }

    public void updatePasswordWidgetState() {
        final boolean isPasswordEnabled = this.userData.isPasswordAuthEnabled();
        final boolean isPasswordChangeNeeded = this.userData.isPasswordChangeNeeded();

        this.passwordEnabled.setValue(isPasswordEnabled);
        this.passwordDisabled.setValue(!isPasswordEnabled);
        this.passwordChangeRequired.setValue(isPasswordChangeNeeded);
        this.passwordPasswordChangeNotRequired.setValue(!isPasswordChangeNeeded);

        this.changePassword.setEnabled(isPasswordEnabled);
        this.passwordChangeRequired.setEnabled(isPasswordEnabled);
        this.passwordPasswordChangeNotRequired.setEnabled(isPasswordEnabled);
    }

    private class AssignedPermission {

        private final String name;
        private boolean assigned;

        public AssignedPermission(String name, boolean assigned) {
            this.name = name;
            this.assigned = assigned;
        }

        public boolean isAssigned() {
            return this.assigned;
        }

        public void setAssigned(boolean assigned) {
            this.assigned = assigned;
        }

        public String getName() {
            return this.name;
        }
    }

    public interface Listener {

        public void onUserDataChanged(final GwtUserData userData);
    }
}
