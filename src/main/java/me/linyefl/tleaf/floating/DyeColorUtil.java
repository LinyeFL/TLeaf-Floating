package me.linyefl.tleaf.floating;

import org.bukkit.DyeColor;

public final class DyeColorUtil {

    private DyeColorUtil() {}

    // 官方染料颜色表（Minecraft Wiki dye color）
    public static String toHex(DyeColor color) {
        switch (color) {
            case WHITE:       return "#F9FFFE";
            case ORANGE:      return "#F9801D";
            case MAGENTA:     return "#C74EBD";
            case LIGHT_BLUE:  return "#3AB3DA";
            case YELLOW:      return "#FED83D";
            case LIME:        return "#80C71F";
            case PINK:        return "#F38BAA";
            case GRAY:        return "#787878";
            case LIGHT_GRAY:  return "#9D9D97";
            case CYAN:        return "#169C9C";
            case PURPLE:      return "#8932B8";
            case BLUE:        return "#3C44AA";
            case BROWN:       return "#835432";
            case GREEN:       return "#5E7C16";
            case RED:         return "#B02E26";
            case BLACK:       return "#1D1D21";
            default:          return "#FFFFFF";
        }
    }
}