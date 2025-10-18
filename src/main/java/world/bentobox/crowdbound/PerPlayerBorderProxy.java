package world.bentobox.crowdbound;

import java.util.Optional;

import org.bukkit.entity.Player;

import world.bentobox.bentobox.api.metadata.MetaDataValue;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.crowdbound.listeners.BorderShower;

public final class PerPlayerBorderProxy implements BorderShower {

    public static final String BORDER_BORDERTYPE_META_DATA = "Border_bordertype";

    private final CrowdBound addon;
    private final BorderShower customBorder;
    private final BorderShower vanillaBorder;

    public PerPlayerBorderProxy(CrowdBound addon, BorderShower customBorder, BorderShower vanillaBorder) {
        this.addon = addon;
        this.customBorder = customBorder;
        this.vanillaBorder = vanillaBorder;
    }

    @Override
    public void showBorder(Player player) {
        var user = User.getInstance(player);
        var border = getBorder(user);
        border.showBorder(player);
    }

    @Override
    public void hideBorder(User user) {
        var border = getBorder(user);
        border.hideBorder(user);
    }

    @Override
    public void clearUser(User user) {
        var border = getBorder(user);
        border.clearUser(user);
    }

    @Override
    public void refreshView(User user) {
        var border = getBorder(user);
        border.refreshView(user);
    }

    private BorderShower getBorder(User user) {
        BorderType borderType = getBorderType(user);
        return switch (borderType) {
            case BARRIER -> customBorder;
            case VANILLA -> vanillaBorder;
        };
    }

    private BorderType getBorderType(User user) {
        Optional<Byte> userTypeId = user.getMetaData(BORDER_BORDERTYPE_META_DATA)
                .map(MetaDataValue::asByte);

        if (userTypeId.isEmpty()) {
            return getDefaultBorderType();
        }

        Optional<BorderType> borderType = BorderType.fromId(userTypeId.get());
        if (borderType.isEmpty() || !addon.getAvailableBorderTypesView().contains(borderType.get())) {
            return getDefaultBorderType();
        }

        return borderType.get();
    }

    private BorderType getDefaultBorderType() {
        return addon.getSettings().getType();
    }

    @Override
    public void teleportPlayer(Player player) {
        if (getBorderType(User.getInstance(player)) == BorderType.BARRIER) {
            customBorder.teleportPlayer(player);
        } else {
            vanillaBorder.teleportPlayer(player);
        }

    }
}
