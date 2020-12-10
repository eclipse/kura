package org.eclipse.kura.web.client.ui.users;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.kura.web.client.messages.Messages;
import org.eclipse.kura.web.client.ui.AlertDialog;
import org.eclipse.kura.web.client.ui.Picker;
import org.eclipse.kura.web.client.ui.Tab;
import org.eclipse.kura.web.client.util.request.RequestQueue;
import org.eclipse.kura.web.shared.KuraPermission;
import org.eclipse.kura.web.shared.model.GwtUserConfig;
import org.eclipse.kura.web.shared.model.GwtUserData;
import org.eclipse.kura.web.shared.service.GwtSecurityTokenService;
import org.eclipse.kura.web.shared.service.GwtSecurityTokenServiceAsync;
import org.eclipse.kura.web.shared.service.GwtUserService;
import org.eclipse.kura.web.shared.service.GwtUserServiceAsync;
import org.gwtbootstrap3.client.ui.Button;
import org.gwtbootstrap3.client.ui.Row;
import org.gwtbootstrap3.client.ui.html.Paragraph;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.cellview.client.CellTable;
import com.google.gwt.user.cellview.client.TextColumn;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.view.client.ListDataProvider;
import com.google.gwt.view.client.SingleSelectionModel;

public class UsersPanelUi extends Composite implements Tab, UserConfigUi.Listener {

    protected static final Messages MSGS = GWT.create(Messages.class);

    private static UsersPanelUiUiBinder uiBinder = GWT.create(UsersPanelUiUiBinder.class);

    interface UsersPanelUiUiBinder extends UiBinder<Widget, UsersPanelUi> {
    }

    @UiField
    Button apply;
    @UiField
    Button reset;
    @UiField
    Button newIdentity;
    @UiField
    Button delete;
    @UiField
    Row config;
    @UiField
    Paragraph emptyLabel;
    @UiField
    Picker picker;
    @UiField
    AlertDialog alertDialog;
    @UiField
    CellTable<GwtUserConfig> userTable;

    private final GwtSecurityTokenServiceAsync gwtXsrfService = GWT.create(GwtSecurityTokenService.class);
    private final GwtUserServiceAsync gwtUserService = GWT.create(GwtUserService.class);

    private final SingleSelectionModel<GwtUserConfig> selectionModel = new SingleSelectionModel<>();
    private final ListDataProvider<GwtUserConfig> dataProvider = new ListDataProvider<>();

    private Set<String> definedPermissions;

    private boolean isDirty = false;

    public UsersPanelUi() {
        initWidget(uiBinder.createAndBindUi(this));
        initTable();
    }

    private void initTable() {
        TextColumn<GwtUserConfig> userColumn = new TextColumn<GwtUserConfig>() {

            @Override
            public String getValue(GwtUserConfig object) {
                final String userName = object.getUserName();

                if (userName.startsWith(" ") || userName.endsWith(" ")) {
                    return '"' + userName + '"';
                } else {
                    return userName;
                }
            }
        };

        this.userTable.addColumn(userColumn, MSGS.usersIdentities());

        this.dataProvider.addDataDisplay(this.userTable);

        this.userTable.setSelectionModel(selectionModel);

        this.selectionModel.addSelectionChangeHandler(e -> {
            this.config.clear();

            final GwtUserConfig userData = selectionModel.getSelectedObject();

            if (userData != null) {
                showConfigUi(new UserConfigUi(userData, definedPermissions, this));
            }

            updateButtonState();
        });

        this.newIdentity.addClickHandler(e -> {
            picker.builder(GwtUserConfig.class) //
                    .setTitle(MSGS.usersCreateIdentity()) //
                    .setMessage(MSGS.usersIdentityName()) //
                    .setValidator((editor, userName) -> {
                        if (userName == null || userName.trim().isEmpty()) {
                            throw new IllegalArgumentException(MSGS.usersIdentityNameEmpty());
                        }

                        if (userName.startsWith(" ") || userName.endsWith(" ")) {
                            throw new IllegalArgumentException(MSGS.usersIdentityLeadingOrTrailingSpaces());
                        }

                        if (dataProvider.getList().stream().anyMatch(d -> d.getUserName().equals(userName))) {
                            throw new IllegalArgumentException(MSGS.usersIdentityAlreadyExists());
                        }

                        return new GwtUserConfig(userName, new HashSet<>(), false);
                    }).setOnPick(user -> {
                        dataProvider.getList().add(user);
                        setDirty(true);
                    }).pick();
        });

        this.delete.addClickHandler(e -> alertDialog.show(MSGS.usersConfirmDeleteIdentity(), () -> {
            dataProvider.getList().remove(selectionModel.getSelectedObject());
            clearConfigUi();
            setDirty(true);
        }));

        this.reset.addClickHandler(e -> alertDialog.show(MSGS.deviceConfigDirty(), this::refresh));
        this.apply.addClickHandler(e -> {
            final List<String> warnings = getWarnings();

            alertDialog.show(MSGS.confirm(), MSGS.usersConfirmApply(), AlertDialog.Severity.INFO, ok -> {
                if (ok) {
                    this.apply();
                }
            }, warnings.toArray(new String[warnings.size()]));
        });

    }

    private List<String> getWarnings() {
        final List<String> result = new ArrayList<>();

        if (dataProvider.getList().isEmpty()) {
            result.add(MSGS.usersEmpty());
            return result;
        }

        if (!dataProvider.getList().stream().anyMatch(u -> u.getPermissions().contains(KuraPermission.ADMIN))) {
            result.add(MSGS.usersNoAdmin());
        }

        dataProvider.getList().stream().filter(u -> u.getPermissions().isEmpty())
                .forEach(u -> result.add(MSGS.usersNoPermissions(u.getUserName())));

        return result;
    }

    private void updateButtonState() {
        this.delete.setEnabled(this.selectionModel.getSelectedObject() != null);
        this.apply.setEnabled(isDirty());
        this.reset.setEnabled(isDirty());
    }

    private void apply() {
        final Set<GwtUserConfig> asSet = new HashSet<>(dataProvider.getList());
        RequestQueue.submit(c -> gwtXsrfService.generateSecurityToken(
                c.callback(token -> gwtUserService.setUserConfig(token, asSet, c.callback(result -> {
                    refresh();
                })))));
    }

    private void showConfigUi(final UserConfigUi ui) {
        this.config.add(ui);
        this.emptyLabel.setVisible(false);
    }

    private void clearConfigUi() {
        this.config.clear();
        this.selectionModel.clear();
        this.emptyLabel.setVisible(true);
        updateButtonState();
    }

    @Override
    public void setDirty(boolean flag) {
        this.isDirty = flag;
        updateButtonState();
    }

    @Override
    public boolean isDirty() {
        return isDirty;
    }

    @Override
    public boolean isValid() {
        return true;
    }

    @Override
    public void refresh() {
        clearConfigUi();

        RequestQueue.submit(c -> gwtXsrfService
                .generateSecurityToken(c.callback(token -> gwtUserService.getUserConfig(token, c.callback(result -> {
                    final List<GwtUserConfig> list = dataProvider.getList();
                    list.clear();
                    list.addAll(result);
                })))));
        RequestQueue.submit(c -> gwtXsrfService.generateSecurityToken(
                c.callback(token -> gwtUserService.getDefinedPermissions(token, c.callback(result -> {
                    definedPermissions = result;
                })))));

        setDirty(false);
    }

    @Override
    public void clear() {
    }

    @Override
    public void onUserDataChanged(final GwtUserData userData) {
        setDirty(true);
    }
}
