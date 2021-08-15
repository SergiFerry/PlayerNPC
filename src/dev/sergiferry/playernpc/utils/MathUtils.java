package dev.sergiferry.playernpc.utils;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Creado por SergiFerry el 26/06/2021
 */
public class MathUtils {

    public static String getFormat(double d) {
        return getFormat(d, 2);
    }

    public static String getFormat(double d, int decimals) {
        return getDecimalFormat(decimals).format(d);
    }

    public static String getFormat(int i) {
        return getFormat(i, 2);
    }

    public static String getFormat(int i, int decimals) {
        return getDecimalFormat(decimals).format(i);
    }

    public static DecimalFormat getDecimalFormat(int decimals) {
        DecimalFormat formatea = new DecimalFormat("#,###,##0.00");
        DecimalFormatSymbols otherSymbols = new DecimalFormatSymbols(Locale.ENGLISH);
        otherSymbols.setDecimalSeparator('.');
        otherSymbols.setGroupingSeparator(',');
        formatea.setDecimalFormatSymbols(otherSymbols);
        formatea.setRoundingMode(RoundingMode.FLOOR);
        formatea.setMinimumFractionDigits(0);
        formatea.setMaximumFractionDigits(decimals);
        return formatea;
    }

    public static boolean isInteger(Object object) {
        try {
            Integer.parseInt(object.toString());
            return true;
        } catch (Exception exc) {
            return false;
        }
    }

    public static boolean isDouble(Object object) {
        try {
            Double.parseDouble(object.toString());
            return true;
        } catch (Exception exc) {
            return false;
        }
    }

}
